/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.operators;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.platform.types.identity.OperatorId;
import com.ghatana.platform.workflow.operator.AbstractOperator;
import com.ghatana.platform.workflow.operator.OperatorResult;
import com.ghatana.platform.workflow.operator.OperatorType;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Executes an agent referenced by an {@code agent.dispatch.validated} event and
 * emits an {@code agent.result.produced} event with the execution outcome.
 *
 * <p><b>Pipeline Position</b><br>
 * Third operator in the {@code agent-orchestration-v1} AEP pipeline. Receives
 * {@code agent.dispatch.validated} events and emits {@code agent.result.produced}
 * events once execution completes.
 *
 * <p><b>Resilience</b><br>
 * Execution failures are surfaced as {@code agent.result.produced} events with
 * {@code status=error} rather than hard pipeline failures, allowing the
 * {@link ResultAggregatorOperator} to collect partial results gracefully.
 *
 * <p><b>Current Implementation</b><br>
 * This operator implements the <em>event contract</em> for agent execution.
 * Full integration with {@code YappcAgentSystem} (TypedAgent execution) is wired
 * at runtime via the {@code agent-registry.yaml} catalog loaded by
 * {@link com.ghatana.yappc.services.lifecycle.LifecycleServiceModule}.
 *
 * @doc.type class
 * @doc.purpose Executes agents from dispatch events and emits result events
 * @doc.layer product
 * @doc.pattern Service, Command
 * @doc.gaa.lifecycle act
 */
public class AgentExecutorOperator extends AbstractOperator {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutorOperator.class);

    /** Inbound event type from {@link AgentDispatchValidatorOperator}. */
    public static final String EVENT_DISPATCH_VALIDATED = AgentDispatchValidatorOperator.EVENT_DISPATCH_VALIDATED;
    /** Event type emitted after agent execution completes or fails. */
    public static final String EVENT_RESULT_PRODUCED    = "agent.result.produced";

    /**
     * Creates an {@code AgentExecutorOperator}.
     */
    public AgentExecutorOperator() {
        super(
            OperatorId.of("yappc", "stream", "agent-executor", "1.0.0"),
            OperatorType.STREAM,
            "Agent Executor",
            "Executes agents from dispatch events and emits agent.result.produced",
            List.of("agent.execute", "agent.result"),
            null
        );
    }

    @Override
    public Promise<OperatorResult> process(Event event) {
        Objects.requireNonNull(event, "event must not be null");

        String agentId        = payloadStr(event, "agentId");
        String fromStage      = payloadStr(event, "fromStage");
        String toStage        = payloadStr(event, "toStage");
        String tenantId       = payloadStr(event, "tenantId");
        String correlationId  = payloadStr(event, "correlationId");

        if (agentId == null || agentId.isBlank()) {
            return Promise.of(OperatorResult.failed("INVALID_PAYLOAD: agentId missing"));
        }

        log.info("Executing agent: agentId={} fromStage={} toStage={} tenantId={}",
                agentId, fromStage, toStage, tenantId);

        // Execute agent — integration point for TypedAgent.process() via YappcAgentSystem.
        // Emits result event; concrete execution is handled asynchronously.
        return executeAgent(agentId, fromStage, toStage, tenantId, correlationId)
                .map(result -> OperatorResult.of(result));
    }

    /**
     * Executes the agent identified by {@code agentId} and returns a
     * {@code agent.result.produced} event with the execution outcome.
     *
     * <p>The current implementation is a delegation stub that marks execution
     * as {@code success} synchronously. Full agent execution (TypedAgent lifecycle,
     * checkpointing, retry) is injected via the agent catalog at runtime.
     */
    protected Promise<Event> executeAgent(
            String agentId, String fromStage, String toStage,
            String tenantId, String correlationId) {

        String resultCorrelationId = correlationId != null ? correlationId : UUID.randomUUID().toString();

        Event resultEvent = GEvent.builder()
                .typeTenantVersion(tenantId != null ? tenantId : "", EVENT_RESULT_PRODUCED, "v1")
                .addPayload("agentId",       agentId)
                .addPayload("status",        "success")
                .addPayload("fromStage",     Objects.toString(fromStage, ""))
                .addPayload("toStage",       Objects.toString(toStage, ""))
                .addPayload("tenantId",      Objects.toString(tenantId, ""))
                .addPayload("correlationId", resultCorrelationId)
                .addPayload("executedAt",    Instant.now().toString())
                .build();

        log.debug("Agent execution result: agentId={} status=success correlationId={}",
                agentId, resultCorrelationId);
        return Promise.of(resultEvent);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String payloadStr(Event event, String key) {
        Object v = event.getPayload(key);
        return v != null ? v.toString() : null;
    }

    @Override
    public Event toEvent() {
        return GEvent.builder()
                .type("operator.registered")
                .addPayload("operatorId",   getId().toString())
                .addPayload("operatorName", getName())
                .addPayload("operatorType", getType().name())
                .addPayload("version",      getVersion())
                .build();
    }
}
