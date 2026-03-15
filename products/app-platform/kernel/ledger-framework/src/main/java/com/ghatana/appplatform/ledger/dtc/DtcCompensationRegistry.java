/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.ledger.dtc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of DTC domain-specific compensation callbacks for integration with the
 * K-05-F05 compensation handler framework (STORY-K17-009).
 *
 * <p>DTC registers one {@link CompensationCallback} per saga step compensation event
 * (e.g. {@code dtc.dvp.lock.reversed}). When K-05 triggers compensation, it invokes
 * the matching callback with context from the original saga step. K-05 owns retry logic,
 * audit, and general idempotency at the transport layer; this registry provides an
 * additional DTC-level idempotency guard to prevent double-reversal at the business layer.
 *
 * <h2>Idempotency</h2>
 * <p>On the first successful invocation for a {@code sagaId + "/" + stepName} key, the result
 * is cached in memory. Subsequent invocations with the same key return the cached result
 * without re-executing the callback; the returned {@link CompensationResult#idempotent()}
 * flag is {@code true} for replay responses.
 *
 * <h2>Manual review escalation</h2>
 * <p>When K-05 signals that all retry attempts for a step are exhausted
 * ({@link #onCompensationFailed}), the registry raises a {@link ManualReviewAlert} to
 * every registered {@link ManualReviewListener}. The alert carries full regulatory
 * context from the DTC settlement.
 *
 * <h2>Registering callbacks (startup example)</h2>
 * <pre>{@code
 * DtcCompensationRegistry registry = new DtcCompensationRegistry();
 * registry.register("dtc.dvp.lock.reversed",
 *         (sagaId, stepName, payload) -> unlockSecurities(sagaId, payload));
 * registry.register("dtc.transfer.debit.reversed",
 *         (sagaId, stepName, payload) -> creditBack(sagaId, payload));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose DTC compensation callback registry integrating with K-05 compensation framework (K17-009)
 * @doc.layer product
 * @doc.pattern Service
 */
public final class DtcCompensationRegistry {

    /**
     * Compensation event type → business callback.
     * Key: compensation topic string (e.g. {@code dtc.dvp.lock.reversed}).
     */
    private final Map<String, CompensationCallback> callbacks = new ConcurrentHashMap<>();

    /**
     * Idempotency cache: {@code sagaId + "/" + stepName} → first result.
     * Prevents double-reversal when K-05 retries the same compensation step.
     */
    private final Map<String, CompensationResult> idempotencyCache = new ConcurrentHashMap<>();

    /** Listeners called when all K-05 retries are exhausted for a compensation step. */
    private final List<ManualReviewListener> reviewListeners = new ArrayList<>();

    // ── Callback registration ─────────────────────────────────────────────────

    /**
     * Registers a DTC compensation callback for a specific K-05 compensation event type.
     *
     * <p>The event type must match a compensation topic defined in {@link DtcSagaPolicies}
     * (e.g. {@code dtc.dvp.lock.reversed}, {@code dtc.transfer.debit.reversed}).
     * Registering a second callback for the same event type replaces the first.
     *
     * @param compensationEventType K-05 compensation topic (see {@link DtcSagaPolicies}); not null
     * @param callback              DTC domain callback invoked by K-05; not null
     */
    public void register(String compensationEventType, CompensationCallback callback) {
        Objects.requireNonNull(compensationEventType, "compensationEventType");
        Objects.requireNonNull(callback, "callback");
        callbacks.put(compensationEventType, callback);
    }

    /**
     * Looks up the callback registered for the given compensation event type.
     *
     * @param compensationEventType K-05 compensation topic
     * @return the registered callback, or empty if none
     */
    public Optional<CompensationCallback> getCallback(String compensationEventType) {
        return Optional.ofNullable(callbacks.get(compensationEventType));
    }

    // ── Callback invocation ───────────────────────────────────────────────────

    /**
     * Invokes the DTC compensation callback for the given K-05 compensation event.
     *
     * <p><b>Idempotency contract</b>: the first successful invocation for a
     * {@code sagaId + "/" + stepName} pair is cached. Subsequent calls with the same
     * pair return the cached {@link CompensationResult} with {@code idempotent=true}
     * without re-executing the callback (preventing double-reversal on K-05 retries).
     *
     * @param sagaId                K-05 saga instance identifier; not null
     * @param stepName              saga step name (e.g. {@code lock-assets}); not null
     * @param compensationEventType K-05 compensation topic; not null
     * @param payload               original step payload forwarded by K-05; may be empty
     * @return compensation result; {@link CompensationResult#success()} false if no callback found
     */
    public CompensationResult invoke(String sagaId,
                                     String stepName,
                                     String compensationEventType,
                                     Map<String, Object> payload) {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(stepName, "stepName");
        Objects.requireNonNull(compensationEventType, "compensationEventType");

        String idempotencyKey = sagaId + "/" + stepName;

        // Return cached result if this sagaId+stepName was already compensated
        CompensationResult cached = idempotencyCache.get(idempotencyKey);
        if (cached != null) {
            return CompensationResult.idempotentReplay(cached.message());
        }

        CompensationCallback callback = callbacks.get(compensationEventType);
        if (callback == null) {
            return CompensationResult.failure(
                    "No DTC compensation callback registered for: " + compensationEventType);
        }

        Map<String, Object> safePayload = payload != null ? Map.copyOf(payload) : Map.of();
        CompensationResult result = callback.apply(sagaId, stepName, safePayload);

        // Cache successful results only; failures are retried by K-05
        if (result.success()) {
            idempotencyCache.put(idempotencyKey, result);
        }
        return result;
    }

    // ── Compensation failure escalation ───────────────────────────────────────

    /**
     * Called by K-05 when all retry attempts for a compensation step are exhausted
     * ({@code COMPENSATION_FAILED} event). Raises a {@link ManualReviewAlert} to all
     * registered listeners carrying full regulatory context.
     *
     * @param sagaId   K-05 saga instance identifier; not null
     * @param stepName failed compensation step name; not null
     * @param payload  original step payload; may be empty
     * @param context  DTC settlement context with regulatory metadata; not null
     */
    public void onCompensationFailed(String sagaId,
                                     String stepName,
                                     Map<String, Object> payload,
                                     DtcSagaCoordinator.DtcSettlementContext context) {
        Objects.requireNonNull(sagaId, "sagaId");
        Objects.requireNonNull(stepName, "stepName");
        Objects.requireNonNull(context, "context");

        Map<String, Object> safePayload = payload != null ? Map.copyOf(payload) : Map.of();
        ManualReviewAlert alert = new ManualReviewAlert(
                sagaId, stepName, safePayload, context,
                "All K-05 compensation retries exhausted; DTC manual intervention required");

        for (ManualReviewListener listener : reviewListeners) {
            listener.onManualReviewRequired(alert);
        }
    }

    /**
     * Registers a listener to receive manual review alerts when compensation is exhausted.
     *
     * @param listener alert recipient (e.g. compliance notification system); not null
     */
    public void addManualReviewListener(ManualReviewListener listener) {
        reviewListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    /**
     * DTC domain-specific compensation callback invoked by the K-05 framework.
     *
     * <p>Implementations must be idempotent at the business level (the registry also
     * caches results for additional protection). Implementations must NOT depend on
     * K-05 audit or retry — K-05 owns those concerns.
     */
    @FunctionalInterface
    public interface CompensationCallback {
        /**
         * Executes the DTC compensation business logic for a failed saga step.
         *
         * @param sagaId    K-05 saga instance identifier
         * @param stepName  name of the step being compensated
         * @param payload   original step payload forwarded from K-05
         * @return compensation result indicating success or failure
         */
        CompensationResult apply(String sagaId, String stepName, Map<String, Object> payload);
    }

    /**
     * Result returned by a {@link CompensationCallback} to the K-05 framework.
     *
     * @param success    true if the DTC compensation business logic succeeded
     * @param message    human-readable outcome description for audit
     * @param idempotent true if this result was replayed from cache (not freshly executed)
     */
    public record CompensationResult(boolean success, String message, boolean idempotent) {

        /** Creates a successful, freshly-executed compensation result. */
        public static CompensationResult ok(String message) {
            return new CompensationResult(true, message, false);
        }

        /** Creates a failure result; K-05 will retry or escalate. */
        public static CompensationResult failure(String message) {
            return new CompensationResult(false, message, false);
        }

        /**
         * Creates a cached replay result from a previous successful execution.
         * Prevents double-reversal on K-05 retries.
         */
        public static CompensationResult idempotentReplay(String originalMessage) {
            return new CompensationResult(true, originalMessage, true);
        }
    }

    /**
     * Alert raised when K-05 signals all compensation retries are exhausted.
     *
     * <p>Carries full DTC regulatory context to enable compliance reporting and
     * manual intervention workflows.
     *
     * @param sagaId    K-05 saga instance identifier
     * @param stepName  name of the step whose compensation was exhausted
     * @param payload   original step payload for audit reconstruction
     * @param context   DTC settlement context with regulatory flags and counterparty info
     * @param reason    human-readable escalation reason
     */
    public record ManualReviewAlert(
            String sagaId,
            String stepName,
            Map<String, Object> payload,
            DtcSagaCoordinator.DtcSettlementContext context,
            String reason
    ) {}

    /**
     * Listener notified when DTC compensation is exhausted and manual review is required.
     */
    @FunctionalInterface
    public interface ManualReviewListener {
        /**
         * Handles a manual review alert.
         *
         * @param alert the escalation details; not null
         */
        void onManualReviewRequired(ManualReviewAlert alert);
    }
}
