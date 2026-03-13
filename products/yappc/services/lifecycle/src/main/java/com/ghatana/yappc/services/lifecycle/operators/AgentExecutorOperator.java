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
import com.ghatana.yappc.agent.StepBudget;
import com.ghatana.yappc.agent.StepContext;
import com.ghatana.yappc.agent.StepResult;
import com.ghatana.yappc.agent.WorkflowStep;
import com.ghatana.yappc.agent.YappcAgentSystem;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
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

    @NotNull
    private final YappcAgentSystem yappcAgentSystem;

    /**
     * Creates an {@code AgentExecutorOperator} wired to a {@link YappcAgentSystem}.
     *
     * @param yappcAgentSystem fully initialised YAPPC agent system (required)
     */
    public AgentExecutorOperator(@NotNull YappcAgentSystem yappcAgentSystem) {
        super(
            OperatorId.of("yappc", "stream", "agent-executor", "1.0.0"),
            OperatorType.STREAM,
            "Agent Executor",
            "Executes agents from dispatch events and emits agent.result.produced",
            List.of("agent.execute", "agent.result"),
            null
        );
        this.yappcAgentSystem = Objects.requireNonNull(yappcAgentSystem, "yappcAgentSystem");
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
     * Executes the agent identified by {@code agentId} by dispatching to the
     * {@link YappcAgentSystem} SDLC registry.
     *
     * <p>Looks up the agent by step name. When a {@link YappcAgentSystem} is injected and
     * the agent is found, the full {@link WorkflowStep} GAA lifecycle is executed. If the
     * system is absent or the agent is unknown, a descriptive error result is emitted so
     * downstream operators (e.g., {@link ResultAggregatorOperator}) can handle it gracefully.
     */
    @SuppressWarnings("unchecked")
    protected Promise<Event> executeAgent(
            String agentId, String fromStage, String toStage,
            String tenantId, String correlationId) {

        String resultCorrelationId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        String effectiveTenant     = tenantId != null ? tenantId : "";

        if (!yappcAgentSystem.isInitialized()) {
            log.error("[AgentExecutor] YappcAgentSystem is not yet initialized. "
                    + "Dropping dispatch for agentId={} — ensure yappcAgentSystem.initialize() completes before events arrive.",
                    agentId);
            return Promise.of(buildResultEvent(effectiveTenant, agentId, "error",
                    fromStage, toStage, resultCorrelationId,
                    Map.of("_error", "agent_system_not_initialized")));
        }

        WorkflowStep<Object, Object> agent =
                yappcAgentSystem.getSdlcRegistry().getAgent(agentId);

        if (agent == null) {
            log.error("[AgentExecutor] No SDLC agent registered for stepName='{}'. "
                    + "Known agents: {}", agentId, yappcAgentSystem.getAllAgentIds());
            return Promise.of(buildResultEvent(effectiveTenant, agentId, "error",
                    fromStage, toStage, resultCorrelationId,
                    Map.of("_error", "agent_not_found", "agentId", agentId)));
        }

        StepContext context = new StepContext(
                resultCorrelationId,   // runId = correlationId
                effectiveTenant,
                extractPhase(agentId), // e.g., "architecture.intake" → "architecture"
                null,                  // configSnapshotId — not yet in dispatch event
                new StepBudget(30.0, 60_000L));

        // Input: pass the dispatch metadata as a Map; agents that need richer input
        // should evolve the dispatch event schema to include a typed "input" payload field.
        Map<String, Object> input = Map.of(
                "agentId",       agentId,
                "fromStage",     Objects.toString(fromStage, ""),
                "toStage",       Objects.toString(toStage, ""),
                "tenantId",      effectiveTenant,
                "correlationId", resultCorrelationId);

        log.info("[AgentExecutor] Dispatching to agent: agentId={} fromStage={} toStage={}",
                agentId, fromStage, toStage);

        return agent.execute(input, context)
                .map(stepResult -> {
                    String status = stepResult != null && stepResult.isSuccess() ? "success" : "error";
                    Map<String, Object> extra = stepResult != null && stepResult.output() != null
                            ? Map.of("output", stepResult.output().toString())
                            : Map.of();
                    log.info("[AgentExecutor] Agent completed: agentId={} status={}", agentId, status);
                    return buildResultEvent(effectiveTenant, agentId, status,
                            fromStage, toStage, resultCorrelationId, extra);
                })
                .mapException(ex -> {
                    log.error("[AgentExecutor] Agent execution failed: agentId={}", agentId, ex);
                    return ex;
                })
                .then(
                    event -> Promise.of(event),
                    ex -> Promise.of(buildResultEvent(effectiveTenant, agentId, "error",
                            fromStage, toStage, resultCorrelationId,
                            Map.of("_error", ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName()))));
    }

    private static Event buildResultEvent(
            String tenantId, String agentId, String status,
            String fromStage, String toStage, String correlationId,
            Map<String, Object> extra) {
        GEvent.GEventBuilder builder = GEvent.builder()
                .typeTenantVersion(tenantId, EVENT_RESULT_PRODUCED, "v1")
                .addPayload("agentId",       agentId)
                .addPayload("status",        status)
                .addPayload("fromStage",     Objects.toString(fromStage, ""))
                .addPayload("toStage",       Objects.toString(toStage, ""))
                .addPayload("tenantId",      tenantId)
                .addPayload("correlationId", correlationId)
                .addPayload("executedAt",    Instant.now().toString());
        extra.forEach(builder::addPayload);
        return builder.build();
    }

    private static String extractPhase(String agentId) {
        if (agentId == null) return "unknown";
        int dot = agentId.indexOf('.');
        return dot > 0 ? agentId.substring(0, dot) : agentId;
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
