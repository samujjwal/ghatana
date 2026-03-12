/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.operators;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.platform.types.identity.OperatorId;
import com.ghatana.platform.workflow.operator.AbstractOperator;
import com.ghatana.platform.workflow.operator.OperatorResult;
import com.ghatana.platform.workflow.operator.OperatorType;
import com.ghatana.yappc.agent.AepEventPublisher;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Terminal pipeline operator that publishes the canonical {@code lifecycle.phase.advanced}
 * event after all agents have been dispatched for a phase transition.
 *
 * <p><b>Pipeline Position</b><br>
 * Fourth and final operator in the {@code lifecycle-management-v1} AEP pipeline.
 * Receives {@code lifecycle.agent.dispatched} events (or a forwarded
 * {@code lifecycle.gate.passed} when no agents are assigned) and:
 * <ol>
 *   <li>Publishes a {@code lifecycle.phase.advanced} event via {@link AepEventBridge} so that
 *       external subscribers (CI/CD hooks, dashboards, audit) are notified</li>
 *   <li>Returns the emitted event as the final pipeline output</li>
 * </ol>
 *
 * <p><b>Idempotency</b><br>
 * The operator is effectively idempotent: re-publishing an already-advanced phase
 * produces a duplicate event in the AEP stream but does not modify persisted state
 * (which is managed by {@code AdvancePhaseUseCase}).
 *
 * @doc.type class
 * @doc.purpose Publishes PhaseAdvancedEvent after lifecycle agents are dispatched
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle capture
 */
public class LifecycleStatePublisherOperator extends AbstractOperator {

    private static final Logger log = LoggerFactory.getLogger(LifecycleStatePublisherOperator.class);

    /** Canonical event type published to notify all phase-advance subscribers. */
    public static final String EVENT_PHASE_ADVANCED    = "lifecycle.phase.advanced";

    /** Inbound event type from AgentDispatchOperator. */
    public static final String EVENT_AGENT_DISPATCHED  = AgentDispatchOperator.EVENT_AGENT_DISPATCHED;
    /** Inbound event type when no agents were assigned (forwarded gate.passed). */
    public static final String EVENT_GATE_PASSED       = GateOrchestratorOperator.EVENT_GATE_PASSED;

    private final AepEventPublisher publisher;

    /**
     * Creates a {@code LifecycleStatePublisherOperator}.
     *
     * @param publisher AEP event publisher for {@code lifecycle.phase.advanced} events
     */
    public LifecycleStatePublisherOperator(AepEventPublisher publisher) {
        super(
            OperatorId.of("yappc", "stream", "lifecycle-state-publisher", "1.0.0"),
            OperatorType.STREAM,
            "Lifecycle State Publisher",
            "Publishes lifecycle.phase.advanced event after agent dispatch",
            List.of("lifecycle.publish", "lifecycle.state"),
            null
        );
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    @Override
    public Promise<OperatorResult> process(Event event) {
        Objects.requireNonNull(event, "event must not be null");

        String projectId   = payloadStr(event, "projectId");
        String fromPhase   = payloadStr(event, "fromPhase");
        String toPhase     = payloadStr(event, "toPhase");
        String tenantId    = payloadStr(event, "tenantId");
        String requestedBy = payloadStr(event, "requestedBy");
        String agentId     = payloadStr(event, "agentId");   // non-null only for agent.dispatched

        String advancedAt  = Instant.now().toString();

        // Build and publish lifecycle.phase.advanced event via AepEventBridge
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("projectId",   projectId);
        payload.put("fromPhase",   fromPhase);
        payload.put("toPhase",     toPhase);
        payload.put("requestedBy", requestedBy);
        payload.put("agentId",     agentId);
        payload.put("advancedAt",  advancedAt);
        payload.put("source",      "lifecycle-pipeline");

        // Fire-and-forget publish (best-effort, swallows failures)
        publisher.publish(EVENT_PHASE_ADVANCED, tenantId, payload)
            .whenException(e -> log.warn("Failed to publish {} event for project={}: {}",
                    EVENT_PHASE_ADVANCED, projectId, e.getMessage()));

        // Return the canonical output event for downstream pipeline consumers
        Event advancedEvent = GEvent.builder()
            .typeTenantVersion(tenantId, EVENT_PHASE_ADVANCED, "v1")
            .addPayload("projectId",   projectId)
            .addPayload("fromPhase",   fromPhase)
            .addPayload("toPhase",     toPhase)
            .addPayload("tenantId",    tenantId)
            .addPayload("requestedBy", requestedBy)
            .addPayload("agentId",     agentId)
            .addPayload("advancedAt",  advancedAt)
            .build();

        log.info("Published lifecycle.phase.advanced: project={} {}→{} (agent={})",
                projectId, fromPhase, toPhase, agentId);

        return Promise.of(OperatorResult.of(advancedEvent));
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static String payloadStr(Event event, String key) {
        Object v = event.getPayload(key);
        return v != null ? v.toString() : null;
    }

    @Override
    public Event toEvent() {
        return GEvent.builder()
                .type("operator.registered")
                .addPayload("operatorId", getId().toString())
                .addPayload("operatorName", getName())
                .addPayload("operatorType", getType().name())
                .addPayload("version", getVersion())
                .build();
    }
}
