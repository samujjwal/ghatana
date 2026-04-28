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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Bridges YAPPC lifecycle domain events to AEP event publishing with resilient,
 * fire-and-forget semantics.
 *
 * <p>All publish methods on this class are <em>best-effort</em>: AEP publish failures
 * are logged as warnings and swallowed so that lifecycle state-machine processing is
 * never blocked by downstream event-publishing issues.
 *
 * <p>The two primary event types published through this bridge are:
 * <ul>
 *   <li>{@code lifecycle.phase.advanced} — emitted when a phase transition succeeds</li>
 *   <li>{@code lifecycle.phase.blocked}  — emitted when a transition is rejected or blocked</li>
 * </ul>
 *
 * <p>This class is the successor to the deleted {@code AepEventBridge} from the Ph1c
 * refactoring. It replaces the direct {@link AepEventPublisher} usage in
 * {@link com.ghatana.yappc.services.lifecycle.operators.LifecycleStatePublisherOperator}
 * to provide a richer, domain-aware API.
 *
 * @doc.type class
 * @doc.purpose Bridges YAPPC lifecycle domain events to AEP with resilient fire-and-forget semantics (YAPPC-Ph5)
 * @doc.layer product
 * @doc.pattern Facade
 */
public class AepEventBridge {

    private static final Logger log = LoggerFactory.getLogger(AepEventBridge.class);

    /** Canonical event type signalling a successful phase transition. */
    public static final String EVENT_PHASE_ADVANCED = "lifecycle.phase.advanced";

    /** Canonical event type signalling a blocked / rejected phase transition. */
    public static final String EVENT_PHASE_BLOCKED  = "lifecycle.phase.blocked";

    private final AepEventPublisher publisher;

    /**
     * Creates an {@code AepEventBridge} wrapping the given publisher.
     *
     * @param publisher underlying AEP event publisher (must not be null)
     */
    public AepEventBridge(AepEventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Publishes a raw event to AEP with the given type, tenant, and payload.
     *
     * <p>Failures are caught and logged as warnings; the returned Promise always
     * completes successfully ({@code null} value).
     *
     * @param eventType canonical event type string (e.g., {@code "lifecycle.phase.advanced"})
     * @param tenantId  tenant identifier
     * @param payload   event payload fields
     * @return a Promise that always completes with {@code null} (best-effort)
     */
    public Promise<Void> publishRawEvent(String eventType, String tenantId, Map<String, Object> payload) {
        return publisher.publish(eventType, tenantId, payload)
                .then(
                        v -> Promise.of((Void) null),
                        e -> {
                            log.warn("Failed to publish event type={} tenant={}: {}",
                                    eventType, tenantId, e.getMessage());
                            return Promise.of((Void) null);
                        }
                );
    }

    /**
     * Derives and publishes the appropriate lifecycle event based on the given
     * {@link TransitionRequest} and {@link TransitionResult}.
     *
     * <ul>
     *   <li>If the result is a success → publishes {@value #EVENT_PHASE_ADVANCED}</li>
     *   <li>If the result is blocked    → publishes {@value #EVENT_PHASE_BLOCKED}</li>
     * </ul>
     *
     * <p>Failures are swallowed (see {@link #publishRawEvent}).
     *
     * @param request the original transition request
     * @param result  the transition outcome
     * @return a Promise that always completes with {@code null} (best-effort)
     */
    public Promise<Void> publishTransitionEvent(TransitionRequest request, TransitionResult result) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(result,  "result must not be null");

        if (result.isSuccess()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("projectId",   request.projectId());
            payload.put("fromPhase",   request.fromPhase());
            payload.put("toPhase",     result.toPhase());
            payload.put("requestedBy", request.requestedBy());
            payload.put("advancedAt",  Instant.now().toString());
            payload.put("source",      "lifecycle-bridge");
            return publishRawEvent(EVENT_PHASE_ADVANCED, request.tenantId(), payload);
        } else {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("projectId",        request.projectId());
            payload.put("fromPhase",         request.fromPhase());
            payload.put("intendedToPhase",   request.toPhase());
            payload.put("requestedBy",       request.requestedBy());
            payload.put("blockCode",         result.blockCode());
            payload.put("blockReason",       result.blockReason());
            payload.put("missingArtifacts",  result.missingArtifacts());
            payload.put("blockedAt",         Instant.now().toString());
            payload.put("source",            "lifecycle-bridge");
            return publishRawEvent(EVENT_PHASE_BLOCKED, request.tenantId(), payload);
        }
    }
}
