/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Central registry of the 11 required agent runtime metrics.
 *
 * <p>All metrics use the {@code ghatana.agent} prefix and carry {@code agentId} /
 * {@code tenantId} tags to enable per-agent and per-tenant observability.
 *
 * <h2>Metric inventory</h2>
 * <ol>
 *   <li>{@code ghatana.agent.dispatch.total} — number of dispatch attempts</li>
 *   <li>{@code ghatana.agent.dispatch.denied} — dispatches denied by governance</li>
 *   <li>{@code ghatana.agent.dispatch.duration} — wall-clock dispatch latency</li>
 *   <li>{@code ghatana.agent.policy.eval.total} — number of policy evaluations</li>
 *   <li>{@code ghatana.agent.policy.eval.denied} — policy evaluations that returned DENY</li>
 *   <li>{@code ghatana.agent.invariant.violation} — invariant violations (critical + fatal)</li>
 *   <li>{@code ghatana.agent.tool.execution.total} — total tool calls</li>
 *   <li>{@code ghatana.agent.tool.execution.denied} — tool calls denied by guard or policy</li>
 *   <li>{@code ghatana.agent.tool.execution.duration} — tool execution latency</li>
 *   <li>{@code ghatana.agent.memory.access.total} — memory reads/writes</li>
 *   <li>{@code ghatana.agent.turn.completed.total} — completed agent turns</li>
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Centralised Micrometer metric definitions for the AEP agent runtime (11 metrics)
 * @doc.layer product
 * @doc.pattern Metrics, Registry
 */
public final class AgentRuntimeMetrics {

    // ── Metric name constants ──────────────────────────────────────────────

    public static final String METRIC_DISPATCH_TOTAL      = "ghatana.agent.dispatch.total";
    public static final String METRIC_DISPATCH_DENIED     = "ghatana.agent.dispatch.denied";
    public static final String METRIC_DISPATCH_DURATION   = "ghatana.agent.dispatch.duration";
    public static final String METRIC_POLICY_EVAL_TOTAL   = "ghatana.agent.policy.eval.total";
    public static final String METRIC_POLICY_EVAL_DENIED  = "ghatana.agent.policy.eval.denied";
    public static final String METRIC_INVARIANT_VIOLATION = "ghatana.agent.invariant.violation";
    public static final String METRIC_TOOL_EXEC_TOTAL     = "ghatana.agent.tool.execution.total";
    public static final String METRIC_TOOL_EXEC_DENIED    = "ghatana.agent.tool.execution.denied";
    public static final String METRIC_TOOL_EXEC_DURATION  = "ghatana.agent.tool.execution.duration";
    public static final String METRIC_MEMORY_ACCESS_TOTAL = "ghatana.agent.memory.access.total";
    public static final String METRIC_TURN_COMPLETED      = "ghatana.agent.turn.completed.total";

    // ── Tag constants ──────────────────────────────────────────────────────

    public static final String TAG_AGENT_ID  = "agentId";
    public static final String TAG_TENANT_ID = "tenantId";
    public static final String TAG_STATUS    = "status";
    public static final String TAG_TOOL_ID   = "toolId";

    private final MeterRegistry registry;

    /**
     * Constructs the metrics registry and pre-registers all 11 timers/counters/summaries.
     *
     * @param registry the Micrometer {@link MeterRegistry} to register metrics with
     */
    public AgentRuntimeMetrics(@NotNull MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    // ── Record helpers (preferred over raw registry access) ─────────────────

    /** Records a completed dispatch attempt. */
    public void recordDispatch(@NotNull String agentId, @NotNull String tenantId,
                               @NotNull String status, @NotNull Duration duration) {
        Counter.builder(METRIC_DISPATCH_TOTAL)
                .tag(TAG_AGENT_ID, agentId).tag(TAG_TENANT_ID, tenantId).tag(TAG_STATUS, status)
                .register(registry).increment();
        Timer.builder(METRIC_DISPATCH_DURATION)
                .tag(TAG_AGENT_ID, agentId).tag(TAG_TENANT_ID, tenantId)
                .register(registry)
                .record(duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    /** Records a denied dispatch. */
    public void recordDispatchDenied(@NotNull String agentId, @NotNull String tenantId) {
        Counter.builder(METRIC_DISPATCH_DENIED)
                .tag(TAG_AGENT_ID, agentId).tag(TAG_TENANT_ID, tenantId)
                .register(registry).increment();
    }

    /** Records a policy evaluation result. */
    public void recordPolicyEval(@NotNull String agentId, @NotNull String tenantId,
                                 boolean allowed) {
        Counter.builder(METRIC_POLICY_EVAL_TOTAL)
                .tag(TAG_AGENT_ID, agentId).tag(TAG_TENANT_ID, tenantId)
                .register(registry).increment();
        if (!allowed) {
            Counter.builder(METRIC_POLICY_EVAL_DENIED)
                    .tag(TAG_AGENT_ID, agentId).tag(TAG_TENANT_ID, tenantId)
                    .register(registry).increment();
        }
    }

    /** Records an invariant violation (critical or fatal). */
    public void recordInvariantViolation(@NotNull String agentId, @NotNull String tenantId,
                                         int violationCount) {
        Counter.builder(METRIC_INVARIANT_VIOLATION)
                .tag(TAG_AGENT_ID, agentId).tag(TAG_TENANT_ID, tenantId)
                .register(registry).increment(violationCount);
    }

    /** Records a tool execution attempt. */
    public void recordToolExecution(@NotNull String agentId, @NotNull String tenantId,
                                    @NotNull String toolId, @NotNull String status,
                                    @NotNull Duration duration) {
        Counter.builder(METRIC_TOOL_EXEC_TOTAL)
                .tag(TAG_AGENT_ID, agentId).tag(TAG_TENANT_ID, tenantId)
                .tag(TAG_TOOL_ID, toolId).tag(TAG_STATUS, status)
                .register(registry).increment();
        Timer.builder(METRIC_TOOL_EXEC_DURATION)
                .tag(TAG_AGENT_ID, agentId).tag(TAG_TENANT_ID, tenantId).tag(TAG_TOOL_ID, toolId)
                .register(registry)
                .record(duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    /** Records a denied tool execution. */
    public void recordToolExecutionDenied(@NotNull String agentId, @NotNull String tenantId,
                                          @NotNull String toolId) {
        Counter.builder(METRIC_TOOL_EXEC_DENIED)
                .tag(TAG_AGENT_ID, agentId).tag(TAG_TENANT_ID, tenantId).tag(TAG_TOOL_ID, toolId)
                .register(registry).increment();
    }

    /** Records a memory access (read or write). */
    public void recordMemoryAccess(@NotNull String agentId, @NotNull String tenantId,
                                   @NotNull String operation) {
        Counter.builder(METRIC_MEMORY_ACCESS_TOTAL)
                .tag(TAG_AGENT_ID, agentId).tag(TAG_TENANT_ID, tenantId).tag("operation", operation)
                .register(registry).increment();
    }

    /** Records a completed agent turn. */
    public void recordTurnCompleted(@NotNull String agentId, @NotNull String tenantId,
                                    @NotNull String status) {
        Counter.builder(METRIC_TURN_COMPLETED)
                .tag(TAG_AGENT_ID, agentId).tag(TAG_TENANT_ID, tenantId).tag(TAG_STATUS, status)
                .register(registry).increment();
    }

    /** Exposes the underlying registry for advanced use (e.g. gauge registration). */
    @NotNull
    public MeterRegistry registry() {
        return registry;
    }
}
