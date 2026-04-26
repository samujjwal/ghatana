/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.server.ingestion;

import com.ghatana.aep.consent.ConsentService;
import com.ghatana.aep.engine.AepEngine;
import com.ghatana.aep.observability.AepSloMetrics;
import com.ghatana.aep.observability.RunLedgerService;
import com.ghatana.aep.security.AepInputValidator;
import com.ghatana.aep.security.PIIScanner;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared event ingestion pipeline for both single-event and batch-event paths.
 *
 * <p>Both {@code POST /api/v1/events} and {@code POST /api/v1/events/batch} route through this
 * service so that security and compliance controls are applied uniformly:
 * <ol>
 *   <li>Tenant ID validation — rejects blank, illegal-character, and "default" values in production.</li>
 *   <li>Event type validation — rejects blank or invalid characters.</li>
 *   <li>Payload validation — enforces depth, key count, and injection-pattern rules.</li>
 *   <li>Idempotency key deduplication — checks an in-memory set; callers may swap a durable store.</li>
 *   <li>Consent evaluation — denies processing when consent is not granted.</li>
 *   <li>PII scanning — logs detected PII types; future extension point for enforcement policy.</li>
 *   <li>Engine processing — delegates to {@link AepEngine#process}.</li>
 *   <li>Run recording — writes to the in-memory run deque and fires SSE via the supplied callback.</li>
 *   <li>SLO metrics — records intake latency, run success/failure counters.</li>
 *   <li>Ledger recording — fire-and-forget to {@link RunLedgerService} for durable audit trail.</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Unified single+batch event ingestion with consistent security and compliance controls
 * @doc.layer product
 * @doc.pattern Service
 */
public final class AepEventIngestionService {

    private static final Logger log = LoggerFactory.getLogger(AepEventIngestionService.class);

    private final AepEngine engine;
    private final ConsentService consentService;
    private final PIIScanner piiScanner;
    /** T-14: Active PII enforcement policy; resolved once at construction time. */
    private final PIIScanner.PiiEnforcementPolicy piiPolicy;
    private final AepSloMetrics sloMetrics;
    private final RunLedgerService runLedgerService;
    /** T-09: Durable idempotency store — backed by Redis in production, in-memory in dev. */
    private final IdempotencyStore idempotencyStore;
    /** Default TTL for idempotency keys — matches the typical event processing window. */
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    /**
     * Callback: (tenantId, eventId, pipelineId, status, startedAt) → side-effect.
     * Used to write into the in-memory run deque and publish SSE.
     */
    private final RunRecorder runRecorder;

    /**
     * Functional interface for recording a completed or failed run.
     */
    @FunctionalInterface
    public interface RunRecorder {
        void record(String runId, String tenantId, @Nullable String pipelineId,
                    String status, Instant startedAt);
    }

    /**
     * Result of ingesting a single event.
     *
     * @param eventId          the engine-assigned event ID
     * @param success          whether the engine processed the event successfully
     * @param detectionCount   number of pattern detections triggered
     * @param skippedDuplicate true when idempotency key was already processed
     * @param consentDenied    true when consent was not granted
     * @param piiDetected      true when PII was found in the payload
     */
    public record IngestionResult(
            String eventId,
            boolean success,
            int detectionCount,
            boolean skippedDuplicate,
            boolean consentDenied,
            boolean piiDetected
    ) {}

    /**
     * Primary constructor — takes a durable {@link IdempotencyStore} implementation.
     */
    public AepEventIngestionService(
            AepEngine engine,
            ConsentService consentService,
            PIIScanner piiScanner,
            AepSloMetrics sloMetrics,
            RunLedgerService runLedgerService,
            IdempotencyStore idempotencyStore,
            RunRecorder runRecorder) {
        this.engine = engine;
        this.consentService = consentService;
        this.piiScanner = piiScanner;
        this.piiPolicy = PIIScanner.PiiEnforcementPolicy.resolve();
        this.sloMetrics = sloMetrics;
        this.runLedgerService = runLedgerService;
        this.idempotencyStore = idempotencyStore;
        this.runRecorder = runRecorder;
    }

    /**
     * Legacy constructor for backward compatibility — wraps a raw {@code Set<String>}
     * with an {@link InMemoryIdempotencyStore} so existing call-sites still compile.
     *
     * @deprecated Prefer the constructor that accepts {@link IdempotencyStore} directly.
     */
    @Deprecated
    public AepEventIngestionService(
            AepEngine engine,
            ConsentService consentService,
            PIIScanner piiScanner,
            AepSloMetrics sloMetrics,
            RunLedgerService runLedgerService,
            Set<String> processedIdempotencyKeys,
            RunRecorder runRecorder) {
        this(engine, consentService, piiScanner, sloMetrics, runLedgerService,
                new InMemoryIdempotencyStore(), runRecorder);
    }

    /**
     * Ingests a single event through the full control pipeline.
     *
     * @param tenantId       validated tenant ID (must not be "default" in production)
     * @param eventType      validated event type string
     * @param payload        validated and sanitised payload map
     * @param idempotencyKey optional idempotency key (null means no deduplication)
     * @param receivedAt     wall-clock instant when the HTTP request was received
     * @return promise resolving to the ingestion result
     */
    public Promise<IngestionResult> ingestOne(
            String tenantId,
            String eventType,
            Map<String, Object> payload,
            @Nullable String idempotencyKey,
            Instant receivedAt) {

        // --- Idempotency check (T-09: async durable store) ---
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyStore.isDuplicate(tenantId, idempotencyKey, IDEMPOTENCY_TTL)
                    .then(isDuplicate -> {
                        if (isDuplicate) {
                            log.info("[ingestion] duplicate event skipped idempotencyKey={} tenantId={} eventType={}",
                                    idempotencyKey, tenantId, eventType);
                            return Promise.of(new IngestionResult(
                                    "dedup-" + idempotencyKey, true, 0, true, false, false));
                        }
                        return ingestAfterIdempotencyCheck(tenantId, eventType, payload, receivedAt);
                    });
        }

        return ingestAfterIdempotencyCheck(tenantId, eventType, payload, receivedAt);
    }

    private Promise<IngestionResult> ingestAfterIdempotencyCheck(
            String tenantId,
            String eventType,
            Map<String, Object> payload,
            Instant receivedAt) {

        AepEngine.Event event = new AepEngine.Event(eventType, payload, Map.of(), Instant.now());
        Instant startedAt = Instant.now();

        // --- Consent check ---
        return consentService.evaluateConsent(tenantId, event)
                .then(consent -> {
                    if (!consent.allowed()) {
                        log.warn("[ingestion] consent denied tenantId={} eventType={} reason={}",
                                tenantId, eventType, consent.reason());
                        return Promise.of(new IngestionResult("consent-denied", false, 0, false, true, false));
                    }

                    // --- PII scan + enforcement (T-14) ---
                    PIIScanner.PIIResult piiResult = piiScanner.scanMap(payload);
                    final Map<String, Object> effectivePayload;
                    if (piiResult.hasPII()) {
                        String detectedTypes = piiResult.items().stream()
                                .map(PIIScanner.PIIItem::type)
                                .distinct()
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("");
                        switch (piiPolicy) {
                            case BLOCK -> {
                                log.warn("[ingestion] PII BLOCK tenantId={} eventType={} types={}",
                                        tenantId, eventType, detectedTypes);
                                return Promise.of(new IngestionResult(
                                        "pii-blocked", false, 0, false, false, true));
                            }
                            case REDACT -> {
                                log.info("[ingestion] PII REDACT tenantId={} eventType={} types={}",
                                        tenantId, eventType, detectedTypes);
                                effectivePayload = piiScanner.redactMap(payload);
                            }
                            default -> {
                                log.warn("[ingestion] PII detected tenantId={} eventType={} types={}",
                                        tenantId, eventType, detectedTypes);
                                effectivePayload = payload;
                            }
                        }
                    } else {
                        effectivePayload = payload;
                    }

                    // --- Engine processing ---
                    AepEngine.Event effectiveEvent = new AepEngine.Event(
                            eventType, effectivePayload, Map.of(), Instant.now());
                    return engine.process(tenantId, effectiveEvent)
                            .map(result -> {
                                String status = result.success() ? "SUCCEEDED" : "FAILED";
                                runRecorder.record(result.eventId(), tenantId, null, status, startedAt);
                                sloMetrics.recordIntakeLatency(receivedAt, startedAt, tenantId);

                                log.debug("[ingestion] processed eventId={} tenantId={} status={} detections={}",
                                        result.eventId(), tenantId, status, result.detections().size());
                                return new IngestionResult(
                                        result.eventId(),
                                        result.success(),
                                        result.detections().size(),
                                        false,
                                        false,
                                        piiResult.hasPII());
                            });
                });
    }

    /**
     * Ingests a batch of events by routing every event through {@link #ingestOne}.
     *
     * <p>Partial failures are tolerated: each event produces an individual {@link IngestionResult}.
     * The returned list preserves input order.
     *
     * @param tenantId   validated tenant ID (must not be "default" in production)
     * @param eventsData list of raw event maps from the request body
     * @param receivedAt wall-clock instant when the HTTP request was received
     * @return promise resolving to a list of per-event results in input order
     */
    public Promise<List<IngestionResult>> ingestBatch(
            String tenantId,
            List<Map<String, Object>> eventsData,
            Instant receivedAt) {

        List<Promise<IngestionResult>> promises = new ArrayList<>(eventsData.size());

        for (Map<String, Object> eventData : eventsData) {
            String eventType;
            try {
                eventType = AepInputValidator.validateEventType(
                        (String) eventData.getOrDefault("type", "unknown"));
            } catch (AepInputValidator.ValidationException ex) {
                log.warn("[ingestion/batch] invalid eventType in batch tenantId={}: {}", tenantId, ex.getMessage());
                // Skip invalid events rather than aborting the whole batch.
                promises.add(Promise.of(new IngestionResult("invalid-type", false, 0, false, false, false)));
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> rawPayload =
                    eventData.get("payload") instanceof Map<?, ?> m
                            ? (Map<String, Object>) m
                            : Map.of();

            Map<String, Object> payload;
            try {
                payload = AepInputValidator.validatePayload(rawPayload);
            } catch (AepInputValidator.ValidationException ex) {
                log.warn("[ingestion/batch] invalid payload tenantId={} eventType={}: {}",
                        tenantId, eventType, ex.getMessage());
                promises.add(Promise.of(new IngestionResult("invalid-payload", false, 0, false, false, false)));
                continue;
            }

            String idempotencyKey = eventData.get("idempotencyKey") instanceof String ik ? ik : null;
            promises.add(ingestOne(tenantId, eventType, payload, idempotencyKey, receivedAt)
                    .then(Promise::of, err -> {
                        log.error("[ingestion/batch] event failed tenantId={} eventType={}: {}",
                                tenantId, eventType, err.getMessage(), err);
                        return Promise.of(new IngestionResult("error", false, 0, false, false, false));
                    }));
        }

        return Promises.toList(promises);
    }
}
