/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle;

import com.ghatana.yappc.agent.AepEventPublisher;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bridges YAPPC lifecycle phase transitions to the
 * Agentic Event Processor (AEP) event bus.
 *
 * <p>After every successful or blocked {@link AdvancePhaseUseCase} execution,
 * callers should invoke {@link #publishTransitionEvent} so that AEP-registered
 * agents (e.g., monitoring, remediation) receive real-time lifecycle signals.
 *
 * <h2>Event Types</h2>
 * <ul>
 *   <li>{@code lifecycle.phase.advanced} — transition was accepted</li>
 *   <li>{@code lifecycle.phase.blocked} — transition was rejected with a reason</li>
 * </ul>
 *
 * <h2>Resilience</h2>
 * <p>AEP publication failures are <em>logged but never propagated</em> — the
 * lifecycle transition outcome is not affected by AEP availability.
 *
 * @doc.type class
 * @doc.purpose Publishes lifecycle transition events to the AEP event bus
 * @doc.layer product
 * @doc.pattern Adapter
 * @doc.gaa.lifecycle act
 */
public class AepEventBridge {

    private static final Logger log = LoggerFactory.getLogger(AepEventBridge.class);

    static final String EVENT_PHASE_ADVANCED = "lifecycle.phase.advanced";
    static final String EVENT_PHASE_BLOCKED  = "lifecycle.phase.blocked";

    private final AepEventPublisher publisher;

    /**
     * @param publisher the AEP publisher to use for event emission
     */
    public AepEventBridge(AepEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Publishes a lifecycle transition event to AEP, derived from the given
     * {@link TransitionRequest} and {@link TransitionResult}.
     *
     * <p>Publishes:
     * <ul>
     *   <li>{@code lifecycle.phase.advanced} when {@code result.isSuccess()}</li>
     *   <li>{@code lifecycle.phase.blocked} when the transition was rejected</li>
     * </ul>
     *
     * <p>AEP failures are swallowed — the promise always completes successfully.
     *
     * @param request the original transition request (provides project/tenant context)
     * @param result  the outcome of the phase advance use case
     * @return a Promise that completes once the event has been dispatched (or failed silently)
     */
    public Promise<Void> publishTransitionEvent(TransitionRequest request, TransitionResult result) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(result, "result must not be null");

        String eventType = result.isSuccess() ? EVENT_PHASE_ADVANCED : EVENT_PHASE_BLOCKED;
        String tenantId  = request.tenantId() != null ? request.tenantId() : "default";

        Map<String, Object> payload = buildPayload(request, result);

        log.debug("AepEventBridge: publishing '{}' for project={} tenant={}",
                eventType, request.projectId(), tenantId);

        return publisher.publish(eventType, tenantId, payload)
                .then(
                    ignored -> Promise.complete(),
                    ex -> {
                        log.warn("AepEventBridge: failed to publish '{}' (continuing): {}",
                                eventType, ex.getMessage());
                        return Promise.complete();
                    });
    }

    /**
     * Publishes an arbitrary event to AEP with the provided payload.
     * Failures are swallowed.
     *
     * @param eventType AEP event type string
     * @param tenantId  tenant scope
     * @param payload   event payload
     * @return a Promise that always completes successfully
     */
    public Promise<Void> publishRawEvent(String eventType, String tenantId, Map<String, Object> payload) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(tenantId,  "tenantId must not be null");
        Objects.requireNonNull(payload,   "payload must not be null");

        log.debug("AepEventBridge: publishing '{}' for tenant={}", eventType, tenantId);

        return publisher.publish(eventType, tenantId, payload)
                .then(
                    ignored -> Promise.complete(),
                    ex -> {
                        log.warn("AepEventBridge: failed to publish '{}' (continuing): {}",
                                eventType, ex.getMessage());
                        return Promise.complete();
                    });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private Map<String, Object> buildPayload(TransitionRequest request, TransitionResult result) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("projectId",  request.projectId());
        payload.put("tenantId",   request.tenantId());
        payload.put("fromPhase",  request.fromPhase());
        payload.put("toPhase",    result.isSuccess() ? result.toPhase() : request.toPhase());
        payload.put("timestamp",  Instant.now().toString());
        payload.put("status",     result.status());

        if (!result.isSuccess()) {
            payload.put("blockCode",        result.blockCode());
            payload.put("blockReason",      result.blockReason());
            payload.put("missingArtifacts", result.missingArtifacts());
        }

        return payload;
    }
}
