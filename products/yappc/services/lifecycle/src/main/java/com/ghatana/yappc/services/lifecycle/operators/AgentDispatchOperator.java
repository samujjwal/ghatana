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
import com.ghatana.yappc.services.lifecycle.StageConfigLoader;
import com.ghatana.yappc.services.lifecycle.StageSpec;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reads agent assignments from the target stage spec and emits a dispatch event
 * per assigned agent so the YAPPC agent runtime can invoke them.
 *
 * <p><b>Pipeline Position</b><br>
 * Third operator in the {@code lifecycle-management-v1} AEP pipeline. Receives
 * {@code lifecycle.gate.passed} events (emitted by {@link GateOrchestratorOperator})
 * and emits one {@code lifecycle.agent.dispatched} event per agent that is assigned
 * to the target stage. If no agents are assigned the event is forwarded unchanged.
 *
 * <p><b>Agent Assignment Resolution</b><br>
 * Agent IDs are read from {@link StageSpec#getAgentAssignments()}. These IDs map
 * 1:1 to entries in the YAPPC Agent Catalog ({@code config/agents/*.yaml}). Actual
 * execution is handled asynchronously by the {@code YappcAgentSystem}; this operator
 * only emits the triggering events.
 *
 * @doc.type class
 * @doc.purpose Reads stage agent assignments and emits dispatch events
 * @doc.layer product
 * @doc.pattern Service
 * @doc.gaa.lifecycle act
 */
public class AgentDispatchOperator extends AbstractOperator {

    private static final Logger log = LoggerFactory.getLogger(AgentDispatchOperator.class);

    /** Event type emitted for each dispatched agent. */
    public static final String EVENT_AGENT_DISPATCHED   = "lifecycle.agent.dispatched";
    /** Inbound event type from GateOrchestratorOperator (gate passed). */
    public static final String EVENT_GATE_PASSED        = GateOrchestratorOperator.EVENT_GATE_PASSED;

    private final StageConfigLoader stageConfig;

    /**
     * Creates an {@code AgentDispatchOperator}.
     *
     * @param stageConfig loader that provides {@link StageSpec} instances with agent assignments
     */
    public AgentDispatchOperator(StageConfigLoader stageConfig) {
        super(
            OperatorId.of("yappc", "stream", "agent-dispatch", "1.0.0"),
            OperatorType.STREAM,
            "Agent Dispatch",
            "Reads stage agent assignments and emits dispatch events",
            List.of("lifecycle.dispatch", "lifecycle.agent"),
            null
        );
        this.stageConfig = Objects.requireNonNull(stageConfig, "stageConfig");
    }

    @Override
    public Promise<OperatorResult> process(Event event) {
        Objects.requireNonNull(event, "event must not be null");

        String projectId   = payloadStr(event, "projectId");
        String fromPhase   = payloadStr(event, "fromPhase");
        String toPhase     = payloadStr(event, "toPhase");
        String tenantId    = payloadStr(event, "tenantId");
        String requestedBy = payloadStr(event, "requestedBy");

        if (toPhase == null) {
            return Promise.of(OperatorResult.failed("INVALID_PAYLOAD: toPhase missing in gate.passed event"));
        }

        Optional<StageSpec> stageOpt = stageConfig.findById(toPhase);
        if (stageOpt.isEmpty()) {
            log.warn("Stage '{}' not found — skipping agent dispatch for project={}", toPhase, projectId);
            return Promise.of(OperatorResult.empty());
        }

        StageSpec stage = stageOpt.get();
        List<String> agentIds = stage.getAgentAssignments();

        if (agentIds.isEmpty()) {
            log.info("No agents assigned to stage '{}' — forwarding without dispatch", toPhase);
            // Re-emit the gate.passed event so downstream operators still fire
            return Promise.of(OperatorResult.of(event));
        }

        // Emit one dispatch event per agent
        String dispatchedAt = Instant.now().toString();
        List<Event> dispatchEvents = new ArrayList<>(agentIds.size());
        for (String agentId : agentIds) {
            Event dispatchEvent = GEvent.builder()
                .typeTenantVersion(tenantId, EVENT_AGENT_DISPATCHED, "v1")
                .addPayload("projectId",    projectId)
                .addPayload("fromPhase",    fromPhase)
                .addPayload("toPhase",      toPhase)
                .addPayload("tenantId",     tenantId)
                .addPayload("requestedBy",  requestedBy)
                .addPayload("agentId",      agentId)
                .addPayload("stageName",    stage.getName())
                .addPayload("dispatchedAt", dispatchedAt)
                .build();
            dispatchEvents.add(dispatchEvent);
            log.info("Dispatching agent '{}' for stage '{}' project={}", agentId, toPhase, projectId);
        }

        return Promise.of(OperatorResult.of(dispatchEvents));
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
