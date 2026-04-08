/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime;

import com.ghatana.agent.framework.tools.ToolContract;
import com.ghatana.agent.framework.tools.ToolExecutionEnvelope;
import com.ghatana.agent.framework.tools.ToolExecutionResult;
import com.ghatana.agent.framework.tools.ToolExecutionStatus;
import com.ghatana.platform.observability.agent.AgentRunTracer;
import com.ghatana.platform.pac.PolicyAsCodeEngine;
import com.ghatana.platform.pac.PolicyEvalResult;
import com.ghatana.platform.toolruntime.approval.ApprovalGateway;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of {@link ToolExecutor} that applies governance, approval gates,
 * and monitoring before delegating to the registered {@link ToolHandler}.
 *
 * <p>Execution flow for each call (mandatory safety path — TX-6):
 * <ol>
 *   <li>Look up the registered {@link ToolHandler} for the tool. If absent, return {@code DENIED}.</li>
 *   <li>Evaluate the {@link PolicyAsCodeEngine} with policy {@code "tool.execution"}.
 *       If denied by policy, return {@code DENIED} without calling the handler.</li>
 *   <li>If {@code contract.requiresApproval()} is {@code true}, check the {@link ApprovalGateway}.
 *       If approval is required and not yet granted, submit and return {@code APPROVAL_PENDING}.</li>
 *   <li>Delegate to the handler. Catch any unhandled exception and convert to {@code FAILED}.</li>
 *   <li>Record the execution via {@link ToolExecutionMonitor} and emit a structured audit log.</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Default governed implementation of ToolExecutor
 * @doc.layer platform
 * @doc.pattern Facade
 */
public final class DefaultToolExecutor implements ToolExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultToolExecutor.class);

    private static final String POLICY_TOOL_EXECUTION = "tool.execution";

    // Micrometer metric names
    /** Counter: every tool call that completes (success or failure). */
    public static final String METRIC_TOOL_CALLS          = "agent.tool.calls";
    /** Counter: every tool call denied by policy. */
    public static final String METRIC_TOOL_POLICY_DENIALS = "agent.policy.denials";

    private final ApprovalGateway approvalGateway;
    private final ToolExecutionMonitor monitor;
    private final PolicyAsCodeEngine policyEngine;
    @Nullable
    private final AgentRunTracer tracer;
    private final Counter toolCallsCounter;
    private final Counter policyDenialsCounter;
    private final Map<String, ToolHandler> handlers = new ConcurrentHashMap<>();

    /**
     * Construct with all required collaborators including the mandatory policy engine.
     *
     * @param approvalGateway gateway for evaluating and submitting approval requests; must not be null
     * @param monitor         execution monitor for recording metrics; must not be null
     * @param policyEngine    policy engine to enforce tool execution policy; must not be null
     */
    public DefaultToolExecutor(
            ApprovalGateway approvalGateway,
            ToolExecutionMonitor monitor,
            PolicyAsCodeEngine policyEngine) {
        this(approvalGateway, monitor, policyEngine, null, new SimpleMeterRegistry());
    }

    /**
     * Full constructor with tracing and metrics instrumentation.
     *
     * @param approvalGateway gateway for evaluating and submitting approval requests; must not be null
     * @param monitor         execution monitor for recording metrics; must not be null
     * @param policyEngine    policy engine to enforce tool execution policy; must not be null
     * @param tracer          optional OTel tracer for tool execution spans; may be null
     * @param meterRegistry   Micrometer registry to record call and denial counters; must not be null
     */
    public DefaultToolExecutor(
            ApprovalGateway approvalGateway,
            ToolExecutionMonitor monitor,
            PolicyAsCodeEngine policyEngine,
            @Nullable AgentRunTracer tracer,
            MeterRegistry meterRegistry) {
        this.approvalGateway = Objects.requireNonNull(approvalGateway, "approvalGateway must not be null");
        this.monitor         = Objects.requireNonNull(monitor,         "monitor must not be null");
        this.policyEngine    = Objects.requireNonNull(policyEngine,    "policyEngine must not be null");
        this.tracer          = tracer;
        Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.toolCallsCounter = Counter.builder(METRIC_TOOL_CALLS)
                .description("Total tool invocations (success + failure)")
                .register(meterRegistry);
        this.policyDenialsCounter = Counter.builder(METRIC_TOOL_POLICY_DENIALS)
                .description("Tool calls denied by policy")
                .register(meterRegistry);
    }

    /**
     * Convenience constructor for backward compatibility — uses an allow-all policy engine.
     *
     * @param approvalGateway gateway for evaluating and submitting approval requests; must not be null
     * @param monitor         execution monitor for recording metrics; must not be null
     * @deprecated Prefer {@link #DefaultToolExecutor(ApprovalGateway, ToolExecutionMonitor, PolicyAsCodeEngine)}
     *             to enforce policy evaluation on every tool call.
     */
    @Deprecated
    public DefaultToolExecutor(ApprovalGateway approvalGateway, ToolExecutionMonitor monitor) {
        this(approvalGateway, monitor,
                (tenantId, policyName, input) -> Promise.of(PolicyEvalResult.allow(policyName)),
                null, new SimpleMeterRegistry());
    }

    @Override
    public void register(String toolId, ToolHandler handler) {
        Objects.requireNonNull(toolId,   "toolId must not be null");
        Objects.requireNonNull(handler,  "handler must not be null");
        handlers.put(toolId, handler);
        LOG.debug("Registered tool handler for toolId={}", toolId);
    }

    @Override
    public Promise<ToolExecutionResult> execute(ToolExecutionEnvelope envelope, ToolContract contract) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(contract, "contract must not be null");

        String toolId = envelope.toolId();
        ToolHandler handler = handlers.get(toolId);

        if (handler == null) {
            LOG.warn("No handler registered for toolId={} invocationId={}", toolId, envelope.invocationId());
            ToolExecutionResult denied = ToolExecutionResult.denied(
                    envelope.invocationId(),
                    "No handler registered for tool: " + toolId,
                    Instant.now());
            return Promise.of(denied);
        }

        // ── TX-6: mandatory policy gate — must pass before any execution ──
        Map<String, Object> policyInput = Map.of(
                "toolId",       toolId,
                "actionClass",  contract.actionClass().name(),
                "tenantId",     envelope.tenantId(),
                "callerAgentId", envelope.callerAgentId());
        return policyEngine.evaluate(envelope.tenantId(), POLICY_TOOL_EXECUTION, policyInput)
                .then(policyResult -> {
                    if (!policyResult.allowed()) {
                        String reason = policyResult.reasons().isEmpty()
                                ? "Denied by tool.execution policy"
                                : String.join("; ", policyResult.reasons());
                        LOG.warn("Tool call denied by policy: toolId={} invocationId={} reason={}",
                                toolId, envelope.invocationId(), reason);
                        policyDenialsCounter.increment();
                        if (tracer != null) {
                            AgentRunTracer.AgentRunSpan runSpan = tracer.startRun(
                                    envelope.callerAgentId(), "", envelope.tenantId(), null);
                            tracer.traceToolExecution(runSpan, toolId, contract.actionClass().name(), "DENY");
                            runSpan.close();
                        }
                        return recordAndReturn(envelope, contract, ToolExecutionResult.denied(
                                envelope.invocationId(), reason, Instant.now()));
                    }
                    return continueExecution(envelope, contract, handler);
                });
    }

    private Promise<ToolExecutionResult> continueExecution(
            ToolExecutionEnvelope envelope, ToolContract contract, ToolHandler handler) {
        String toolId = envelope.toolId();
        if (contract.requiresApproval() && !contract.actionClass().isLowRisk()) {
            return approvalGateway.requiresApproval(
                            envelope.tenantId(),
                            envelope.callerAgentId(),
                            contract.actionClass().name())
                    .then(needsApproval -> {
                        if (Boolean.TRUE.equals(needsApproval)) {
                            LOG.info("Tool call pending approval: toolId={} invocationId={}",
                                    toolId, envelope.invocationId());
                            ToolExecutionResult pending =
                                    ToolExecutionResult.pendingApproval(envelope.invocationId(), Instant.now());
                            return recordAndReturn(envelope, contract, pending);
                        }
                        return doExecute(envelope, contract, handler);
                    });
        }

        return doExecute(envelope, contract, handler);
    }

    private Promise<ToolExecutionResult> doExecute(
            ToolExecutionEnvelope envelope, ToolContract contract, ToolHandler handler) {
        return handler.handle(envelope, contract)
                .then(
                        result -> {
                            LOG.debug("Tool executed: toolId={} status={} invocationId={}",
                                    envelope.toolId(), result.status(), envelope.invocationId());
                            return recordAndReturn(envelope, contract, result);
                        },
                        ex -> {
                            LOG.error("Unhandled exception from tool handler: toolId={} invocationId={}",
                                    envelope.toolId(), envelope.invocationId(), ex);
                            ToolExecutionResult failed = ToolExecutionResult.failed(
                                    envelope.invocationId(),
                                    ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName(),
                                    envelope.invocationId(),
                                    Instant.now(),
                                    java.time.Duration.ZERO);
                            return recordAndReturn(envelope, contract, failed);
                        });
    }

    private Promise<ToolExecutionResult> recordAndReturn(
            ToolExecutionEnvelope envelope, ToolContract contract, ToolExecutionResult result) {
        long outputBytes = result.output() != null ? result.output().toString().length() : 0L;
        boolean success = result.status() == ToolExecutionStatus.SUCCESS;
        return monitor.record(
                        envelope.tenantId(),
                        envelope.callerAgentId(),
                        contract.name(),
                        result.executionDuration() != null ? result.executionDuration() : java.time.Duration.ZERO,
                        outputBytes,
                        success)
                .map(ignored -> {
                    // Emit OTel span for this tool execution (allow-listed statuses only)
                    if (tracer != null && result.status() != ToolExecutionStatus.DENIED) {
                        AgentRunTracer.AgentRunSpan runSpan = tracer.startRun(
                                envelope.callerAgentId(), "", envelope.tenantId(), null);
                        tracer.traceToolExecution(runSpan, envelope.toolId(),
                                contract.actionClass().name(),
                                result.status().name());
                        runSpan.close();
                    }
                    // Increment call counter for actual executions (success or failure), not pending/denied
                    if (result.status() == ToolExecutionStatus.SUCCESS
                            || result.status() == ToolExecutionStatus.FAILED) {
                        toolCallsCounter.increment();
                    }
                    LOG.info("tool.executed toolId={} status={} invocationId={} tenantId={}",
                            envelope.toolId(), result.status(), envelope.invocationId(), envelope.tenantId());
                    return result;
                });
    }
}
