/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle.operators;

import com.ghatana.platform.domain.event.Event;
import com.ghatana.platform.domain.event.GEvent;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.types.identity.OperatorId;
import com.ghatana.platform.workflow.operator.AbstractOperator;
import com.ghatana.platform.workflow.operator.OperatorResult;
import com.ghatana.platform.workflow.operator.OperatorType;
import com.ghatana.yappc.infrastructure.datacloud.repository.AgentStateRepository;
import com.ghatana.yappc.agent.WorkflowStep;
import com.ghatana.yappc.agent.WorkflowStepOperatorAdapter;
import com.ghatana.yappc.agent.YappcAgentSystem;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
    @Nullable
    private final AgentStateRepository agentStateRepository;

    /**
     * Creates an {@code AgentExecutorOperator} wired to a {@link YappcAgentSystem}.
     *
     * @param yappcAgentSystem fully initialised YAPPC agent system (required)
     */
    public AgentExecutorOperator(@NotNull YappcAgentSystem yappcAgentSystem) {
        this(yappcAgentSystem, null);
    }

    /**
     * Creates an {@code AgentExecutorOperator} wired to a {@link YappcAgentSystem}
     * and durable agent execution state repository.
     *
     * @param yappcAgentSystem fully initialised YAPPC agent system (required)
     * @param agentStateRepository optional durable state repository
     */
    public AgentExecutorOperator(
            @NotNull YappcAgentSystem yappcAgentSystem,
            @Nullable AgentStateRepository agentStateRepository) {
        super(
            OperatorId.of("yappc", "stream", "agent-executor", "1.0.0"),
            OperatorType.STREAM,
            "Agent Executor",
            "Executes agents from dispatch events and emits agent.result.produced",
            List.of("agent.execute", "agent.result"),
            null
        );
        this.yappcAgentSystem = Objects.requireNonNull(yappcAgentSystem, "yappcAgentSystem");
        this.agentStateRepository = agentStateRepository;
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

        // Execute through the governed WorkflowStepOperatorAdapter rather than
        // directly invoking TypedAgent or workflow-agent execution.
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

        return createExecution(agentId, fromStage, toStage, effectiveTenant, resultCorrelationId)
                .then(executionId -> markRunning(effectiveTenant, executionId)
                        .then(() -> executeAgentCore(agentId, fromStage, toStage, effectiveTenant, resultCorrelationId)
                                .then(
                                        event -> markTerminal(effectiveTenant, executionId, event).map(ignored -> event),
                                        ex -> markFailed(effectiveTenant, executionId, ex)
                                                .map(ignored -> buildResultEvent(effectiveTenant, agentId, "error",
                                                        fromStage, toStage, resultCorrelationId,
                                                        Map.of("_error", ex.getMessage() != null
                                                                ? ex.getMessage()
                                                                : ex.getClass().getSimpleName()))))));
    }

    private Promise<Event> executeAgentCore(
            String agentId, String fromStage, String toStage,
            String effectiveTenant, String resultCorrelationId) {

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

        WorkflowStepOperatorAdapter<Object, Object> governedOperator =
                new WorkflowStepOperatorAdapter<>(agent);
        Event governedDispatch = GEvent.builder()
                .typeTenantVersion(effectiveTenant, "agent.capability.requested", "v1")
                .addPayload("input", input)
                .addPayload("tenantId", effectiveTenant)
                .addPayload("runId", resultCorrelationId)
                .addPayload("phase", extractPhase(agentId))
                .addPayload("configSnapshotId", "")
                .addPayload("capabilityRef", agentId)
                .addPayload("operatorContract", "EventOperatorCapability")
                .addPayload("policy", "yappc-agent-runtime-policy")
                .addPayload("approval", "required-for-destructive-actions")
                .addPayload("idempotencyKey", resultCorrelationId + ":" + agentId)
                .addPayload("audit", "agent.dispatch.governed")
                .addPayload("outputValidation", "operator-result-validated")
                .build();

        return governedOperator.process(governedDispatch)
                .map(operatorResult -> {
                    String status = operatorResult != null && operatorResult.isSuccess() ? "success" : "error";
                    Map<String, Object> extra = Map.of(
                            "capabilityRef", agentId,
                            "operatorContract", "EventOperatorCapability",
                            "policy", "yappc-agent-runtime-policy",
                            "approval", "required-for-destructive-actions",
                            "idempotencyKey", resultCorrelationId + ":" + agentId,
                            "audit", "agent.dispatch.governed",
                            "outputValidation", "operator-result-validated");
                    log.info("[AgentExecutor] Governed agent completed: capabilityRef={} status={}", agentId, status);
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

    private Promise<Optional<UUID>> createExecution(
            String agentId,
            String fromStage,
            String toStage,
            String tenantId,
            String correlationId) {
        if (agentStateRepository == null) {
            return Promise.of(Optional.empty());
        }
        Map<String, Object> input = Map.of(
                "agentId", agentId,
                "fromStage", Objects.toString(fromStage, ""),
                "toStage", Objects.toString(toStage, ""),
                "tenantId", tenantId,
                "correlationId", correlationId);
        return withTenant(tenantId, () -> agentStateRepository
                .create(agentId, extractPhase(agentId), null, input.toString())
                .map(Optional::of));
    }

    private Promise<Void> markRunning(String tenantId, Optional<UUID> executionId) {
        if (agentStateRepository == null || executionId.isEmpty()) {
            return Promise.complete();
        }
        return withTenant(tenantId, () -> agentStateRepository.markRunning(executionId.get()));
    }

    private Promise<Void> markTerminal(String tenantId, Optional<UUID> executionId, Event event) {
        if (agentStateRepository == null || executionId.isEmpty()) {
            return Promise.complete();
        }
        String status = payloadStr(event, "status");
        if ("success".equals(status)) {
            return withTenant(tenantId, () -> agentStateRepository.markSucceeded(
                    executionId.get(),
                    Map.of(
                            "status", "success",
                            "agentId", Objects.toString(event.getPayload("agentId"), ""),
                            "correlationId", Objects.toString(event.getPayload("correlationId"), "")
                    ).toString()));
        }
        return withTenant(tenantId, () -> agentStateRepository.markFailed(
                executionId.get(),
                Objects.toString(event.getPayload("_error"), "agent execution failed")));
    }

    private Promise<Void> markFailed(String tenantId, Optional<UUID> executionId, Throwable ex) {
        if (agentStateRepository == null || executionId.isEmpty()) {
            return Promise.complete();
        }
        String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        return withTenant(tenantId, () -> agentStateRepository.markFailed(executionId.get(), message));
    }

    private static <T> Promise<T> withTenant(String tenantId, java.util.function.Supplier<Promise<T>> supplier) {
        String priorTenant = null;
        try {
            priorTenant = TenantContext.getCurrentTenantId();
        } catch (RuntimeException ignored) {
            // The dispatch event tenant is the canonical context for agent execution tracking.
        }
        TenantContext.setCurrentTenantId(tenantId);
        try {
            return supplier.get();
        } finally {
            if (priorTenant == null) {
                TenantContext.clear();
            } else {
                TenantContext.setCurrentTenantId(priorTenant);
            }
        }
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
