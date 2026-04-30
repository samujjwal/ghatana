package com.ghatana.plugin.dataretention.impl;

import com.ghatana.plugin.dataretention.DataRetentionPlugin;
import com.ghatana.plugin.observability.PluginObservability;
import com.ghatana.platform.plugin.PluginContext;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * DefaultDataRetentionPlugin — reference plugin implementation.
 *
 * <p>Demonstrates the three canonical plugin patterns against a real domain:
 * data retention policy enforcement.</p>
 *
 * <h2>How to adapt this template</h2>
 * <ol>
 *   <li>Replace {@code DataRetention} with your plugin's domain concept.</li>
 *   <li>Replace the {@code ConcurrentHashMap} state with your repository or
 *       delegate — keep maps only for pure in-memory plugins or tests.</li>
 *   <li>Replace the {@code plugin-human-approval} stub call in
 *       {@link #requestRetentionException} with the real HumanApprovalPlugin
 *       injection when human approval is wired in your product.</li>
 *   <li>Update the plugin ID in {@code super("data-retention")} to your
 *       canonical plugin identifier (used in OTel span and metric names).</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Reference: DefaultDataRetentionPlugin — canonical plugin impl example
 * @doc.layer platform
 * @doc.pattern Plugin
 */
public final class DefaultDataRetentionPlugin
        extends PluginObservability
        implements DataRetentionPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultDataRetentionPlugin.class);

    /** Default retention periods by data classification. */
    private static final Map<String, Duration> DEFAULT_RETENTION = Map.of(
        "PHI",         Duration.ofDays(365 * 7),   // 7 years
        "FINANCIAL",   Duration.ofDays(365 * 7),   // 7 years
        "OPERATIONAL", Duration.ofDays(365 * 3),   // 3 years
        "ANALYTICS",   Duration.ofDays(365),        // 1 year
        "AUDIT",       Duration.ofDays(365 * 10)   // 10 years
    );

    // ── Per-tenant state — keyed by tenantId ─────────────────────────────────
    /** Records indexed: tenantId → recordId → DataRecord */
    private final Map<String, Map<String, DataRecord>> tenantRecords = new ConcurrentHashMap<>();
    /** Per-tenant retention overrides: tenantId → classification → Duration */
    private final Map<String, Map<String, Duration>> tenantRetentionOverrides = new ConcurrentHashMap<>();

    public DefaultDataRetentionPlugin() {
        super("data-retention");   // pluginId used in OTel span/metric naming
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Plugin lifecycle
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public Promise<Void> initialize(PluginContext context) {
        LOG.info("DataRetentionPlugin initializing [pluginId=data-retention]");
        return Promise.complete();
    }

    @Override
    public Promise<Void> start() {
        LOG.info("DataRetentionPlugin started");
        return Promise.complete();
    }

    @Override
    public Promise<Void> stop() {
        LOG.info("DataRetentionPlugin stopping — releasing state");
        tenantRecords.clear();
        tenantRetentionOverrides.clear();
        return Promise.complete();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Pattern 1 — Policy Evaluation
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public Promise<RetentionDecision> evaluateRetentionPolicy(String tenantId, DataRecord record) {
        try (var span = startSpan("evaluate-retention-policy", tenantId, null, record.dataClassification())) {
            LOG.debug("Evaluating retention policy [tenantId={}, recordId={}, classification={}]",
                tenantId, record.recordId(), record.dataClassification());

            if (record.legalHold()) {
                recordMetric();
                return Promise.of(new RetentionDecision(
                    Decision.LEGAL_HOLD,
                    "Record has an active legal hold — expiry suspended",
                    Instant.now(),
                    Optional.empty()
                ));
            }

            Duration period = retentionPeriodFor(tenantId, record.dataClassification());
            Instant expiresAt = record.createdAt().plus(period);

            if (Instant.now().isAfter(expiresAt)) {
                recordMetric();
                return Promise.of(new RetentionDecision(
                    Decision.EXPIRED,
                    "Record exceeded retention period of " + period.toDays() + " days",
                    Instant.now(),
                    Optional.of(expiresAt)
                ));
            }

            return Promise.of(new RetentionDecision(
                Decision.COMPLIANT,
                "Record is within retention policy",
                Instant.now(),
                Optional.of(expiresAt)
            ));
        }
    }

    @Override
    public Promise<Duration> getRetentionPeriod(String tenantId, String dataClassification) {
        return Promise.of(retentionPeriodFor(tenantId, dataClassification));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Pattern 2 — Event-Driven Processing
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public Promise<Void> handleDataCreated(DataCreatedEvent event) {
        try (var span = startSpan("handle-data-created", event.tenantId(), event.actorId(), null)) {
            LOG.info("DataCreated event received [tenantId={}, recordId={}, actor={}]",
                event.tenantId(), event.record().recordId(), event.actorId());

            // Store record in tenant-scoped map (idempotent via put — safe for at-least-once delivery)
            tenantRecords
                .computeIfAbsent(event.tenantId(), k -> new ConcurrentHashMap<>())
                .put(event.record().recordId(), event.record());

            recordMetric();
            return Promise.complete();
        }
    }

    @Override
    public Promise<Void> handleDataAccessed(DataAccessedEvent event) {
        try (var span = startSpan("handle-data-accessed", event.tenantId(), event.actorId(), null)) {
            LOG.debug("DataAccessed event received [tenantId={}, recordId={}, purpose={}]",
                event.tenantId(), event.recordId(), event.accessPurpose());

            // Update last-accessed timestamp on the cached record
            Map<String, DataRecord> records = tenantRecords.get(event.tenantId());
            if (records != null) {
                records.computeIfPresent(event.recordId(), (id, existing) ->
                    new DataRecord(
                        existing.recordId(),
                        existing.tenantId(),
                        existing.dataClassification(),
                        existing.createdAt(),
                        event.occurredAt(),
                        existing.legalHold(),
                        existing.metadata()
                    )
                );
            }

            recordMetric();
            return Promise.complete();
        }
    }

    @Override
    public Promise<List<DataRecord>> findExpiring(String tenantId, Duration lookAhead) {
        try (var span = startSpan("find-expiring", tenantId, null, null)) {
            Instant cutoff = Instant.now().plus(lookAhead);

            Map<String, DataRecord> records = tenantRecords.getOrDefault(tenantId, Map.of());
            List<DataRecord> expiring = records.values().stream()
                .filter(r -> !r.legalHold())
                .filter(r -> {
                    Duration period = retentionPeriodFor(tenantId, r.dataClassification());
                    Instant expiresAt = r.createdAt().plus(period);
                    return expiresAt.isBefore(cutoff);
                })
                .collect(Collectors.toList());

            LOG.info("Found {} expiring records [tenantId={}, lookAhead={}d]",
                expiring.size(), tenantId, lookAhead.toDays());

            recordMetric();
            return Promise.of(expiring);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Pattern 3 — Human Approval Integration
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public Promise<ApprovalOutcome> requestRetentionException(
            String tenantId,
            DataRecord record,
            String reason,
            String requestor) {
        try (var span = startSpan("request-retention-exception", tenantId, requestor,
                record.dataClassification())) {

            String approvalId = UUID.randomUUID().toString();
            LOG.info("Retention exception requested [tenantId={}, recordId={}, approvalId={}, requestor={}]",
                tenantId, record.recordId(), approvalId, requestor);

            /*
             * PATTERN 3 INTEGRATION POINT
             * ───────────────────────────
             * In a real product, inject HumanApprovalPlugin and call:
             *
             *   return humanApprovalPlugin.createApprovalRequest(
             *       ApprovalRequest.builder()
             *           .approvalId(approvalId)
             *           .tenantId(tenantId)
             *           .type("RETENTION_EXCEPTION")
             *           .subject("Retention extension for " + record.recordId())
             *           .reason(reason)
             *           .requestor(requestor)
             *           .metadata(Map.of("recordId", record.recordId(),
             *                            "classification", record.dataClassification()))
             *           .build()
             *   ).map(result -> result.approved() ? ApprovalOutcome.APPROVED : ApprovalOutcome.REJECTED);
             *
             * For this reference template we return APPROVED to allow tests to
             * verify the full call path without a live approval plugin.
             */
            recordMetric();
            return Promise.of(ApprovalOutcome.APPROVED);
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

    private Duration retentionPeriodFor(String tenantId, String dataClassification) {
        Map<String, Duration> overrides = tenantRetentionOverrides.getOrDefault(tenantId, Map.of());
        return overrides.getOrDefault(
            dataClassification,
            DEFAULT_RETENTION.getOrDefault(dataClassification, Duration.ofDays(365))
        );
    }
}
