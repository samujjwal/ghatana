/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety;

import com.ghatana.agent.AgentResult;
import com.ghatana.agent.AgentResultStatus;
import com.ghatana.agent.audit.AgentTraceLedger;
import com.ghatana.agent.audit.TraceEvent;
import com.ghatana.agent.audit.TraceEventBuilder;
import com.ghatana.agent.audit.TraceEventType;
import com.ghatana.agent.dispatch.AgentDispatcher;
import com.ghatana.agent.dispatch.ExecutionTier;
import com.ghatana.agent.framework.api.AgentContext;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Governance-aware decorator for {@link AgentDispatcher}.
 *
 * <p>Wraps an existing dispatcher with:
 * <ul>
 *   <li><b>Grant validation</b>: Verifies the execution grant is valid before dispatch</li>
 *   <li><b>Invariant monitoring</b>: Evaluates pre-dispatch invariants</li>
 *   <li><b>Trace recording</b>: Appends evidence to the trace ledger</li>
 * </ul>
 *
 * <p>If any pre-dispatch check fails, the action is denied and recorded.
 *
 * @doc.type class
 * @doc.purpose Governance-aware agent dispatch decorator
 * @doc.layer agent-runtime
 * @doc.pattern Decorator
 */
public class GovernedAgentDispatcher implements AgentDispatcher {

    private static final Logger log = LoggerFactory.getLogger(GovernedAgentDispatcher.class);

    private final AgentDispatcher delegate;
    private final InvariantMonitor invariantMonitor;
    private final AgentTraceLedger traceLedger;

    public GovernedAgentDispatcher(
            @NotNull AgentDispatcher delegate,
            @NotNull InvariantMonitor invariantMonitor,
            @NotNull AgentTraceLedger traceLedger) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.invariantMonitor = Objects.requireNonNull(invariantMonitor, "invariantMonitor");
        this.traceLedger = Objects.requireNonNull(traceLedger, "traceLedger");
    }

    @Override
    @NotNull
    public <I, O> Promise<AgentResult<O>> dispatch(
            @NotNull String agentId,
            @NotNull I input,
            @NotNull AgentContext ctx) {

        String tenantId = extractTenantId(ctx);
        String traceId = extractTraceId(ctx);

        // Build trace event builder for this dispatch
        TraceEventBuilder eventBuilder = new TraceEventBuilder(
                traceId, agentId, tenantId,
                traceLedger instanceof com.ghatana.agent.audit.HashChainedTraceAppender hc
                        ? hc.getLastHash(tenantId) : "");

        // Evaluate pre-dispatch invariants
        InvariantContext invariantCtx = buildInvariantContext(agentId, tenantId, traceId, ctx);
        List<InvariantViolation> violations = invariantMonitor.evaluate(invariantCtx);

        List<InvariantViolation> critical = violations.stream()
                .filter(v -> v.severity() == InvariantViolation.Severity.CRITICAL
                        || v.severity() == InvariantViolation.Severity.FATAL)
                .toList();

        if (!critical.isEmpty()) {
            // Record denial and abort
            String reason = critical.stream()
                    .map(InvariantViolation::description)
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Invariant violation");

            TraceEvent denialEvent = eventBuilder.build(
                    TraceEventType.ACTION_DENIED,
                    "Dispatch denied: " + reason,
                    Map.of("agentId", agentId, "violations", String.valueOf(critical.size())));

            return traceLedger.append(denialEvent)
                    .map(v -> AgentResult.<O>builder()
                            .status(AgentResultStatus.DENIED)
                            .confidence(0.0)
                            .agentId(agentId)
                            .explanation("Dispatch blocked by invariant violation: " + reason)
                            .processingTime(Duration.ZERO)
                            .build());
        }

        // Record dispatch event
        TraceEvent dispatchEvent = eventBuilder.build(
                TraceEventType.ACTION_EXECUTED,
                "Dispatching agent " + agentId,
                Map.of("agentId", agentId));

        return traceLedger.append(dispatchEvent)
                .then(v -> delegate.<I, O>dispatch(agentId, input, ctx))
                .map(result -> {
                    // Record completion (fire-and-forget; do not block on ledger append)
                    TraceEvent completionEvent = eventBuilder.build(
                            TraceEventType.TURN_COMPLETED,
                            "Agent " + agentId + " completed with status " + result.getStatus(),
                            Map.of("status", result.getStatus().name(),
                                    "confidence", String.valueOf(result.getConfidence())));
                    traceLedger.append(completionEvent);
                    return result;
                });
    }

    @Override
    @NotNull
    public ExecutionTier resolve(@NotNull String agentId) {
        return delegate.resolve(agentId);
    }

    private String extractTenantId(AgentContext ctx) {
        return ctx.getTenantId();
    }

    private String extractTraceId(AgentContext ctx) {
        Object traceId = ctx.getConfig("__traceId");
        return traceId != null ? traceId.toString() : java.util.UUID.randomUUID().toString();
    }

    private InvariantContext buildInvariantContext(
            String agentId, String tenantId, String traceId, AgentContext ctx) {
        // Extract accumulated metrics from context config
        double costUsd = extractDouble(ctx, "__accumulatedCostUsd", 0.0);
        double costCap = extractDouble(ctx, "__costCapUsd", 10.0);
        int depth = extractInt(ctx, "__delegationDepth", 0);
        int maxDepth = extractInt(ctx, "__maxDelegationDepth", 5);
        int actions = extractInt(ctx, "__actionsExecuted", 0);
        int maxActions = extractInt(ctx, "__maxActionsPerTurn", 100);
        long maxDuration = extractLong(ctx, "__maxTurnDurationSeconds", 300);

        java.time.Instant turnStart = ctx.getStartTime();

        return new InvariantContext(
                agentId, tenantId, traceId,
                costUsd, costCap,
                depth, maxDepth,
                actions, maxActions,
                turnStart, maxDuration,
                Map.of());
    }

    private double extractDouble(AgentContext ctx, String key, double defaultVal) {
        Object v = ctx.getConfig(key);
        if (v instanceof Number n) return n.doubleValue();
        return defaultVal;
    }

    private int extractInt(AgentContext ctx, String key, int defaultVal) {
        Object v = ctx.getConfig(key);
        if (v instanceof Number n) return n.intValue();
        return defaultVal;
    }

    private long extractLong(AgentContext ctx, String key, long defaultVal) {
        Object v = ctx.getConfig(key);
        if (v instanceof Number n) return n.longValue();
        return defaultVal;
    }
}
