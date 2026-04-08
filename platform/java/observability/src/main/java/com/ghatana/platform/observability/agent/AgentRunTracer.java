/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.platform.observability.agent;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.time.Duration;
import java.util.Objects;

/**
 * Fluent, lifecycle-aware OpenTelemetry span emitter for the 11 standard agent run phases.
 *
 * <p>Provides a typed, safe API for instrumenting agent runs without scattering
 * raw OTel span builder calls across runtime code. All span attribute keys are sourced
 * from {@link AgentTelemetryContract} to ensure consistency.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * AgentRunTracer tracer = new AgentRunTracer(otelTracer);
 * AgentRunSpan run = tracer.startRun(agentId, releaseId, tenantId, correlationId);
 * try {
 *     tracer.traceContextRetrieval(run, 12, Duration.ofMillis(42));
 *     tracer.tracePolicyEval(run, policyPackId, "ALLOW");
 *     tracer.traceToolExecution(run, toolId, "READ", "ALLOW");
 *     run.setStatus(StatusCode.OK, "success");
 * } catch (Exception e) {
 *     run.recordException(e);
 *     run.setStatus(StatusCode.ERROR, e.getMessage());
 * } finally {
 *     run.close();
 * }
 * }</pre>
 *
 * @see AgentTelemetryContract
 * @doc.type class
 * @doc.purpose Fluent OpenTelemetry span emitter for the 11 standard agent run lifecycle phases.
 * @doc.layer platform
 * @doc.pattern Facade
 */
public final class AgentRunTracer {

    private final Tracer tracer;

    /**
     * Constructs an {@code AgentRunTracer} using the given OpenTelemetry {@link Tracer}.
     *
     * @param tracer the OTel tracer; must not be null
     */
    public AgentRunTracer(Tracer tracer) {
        this.tracer = Objects.requireNonNull(tracer, "tracer");
    }

    // ── Phase 1: Root run span ────────────────────────────────────────────────

    /**
     * Starts a root span for a new agent run.
     *
     * @param agentId       logical agent identifier
     * @param agentReleaseId agent release record identifier
     * @param tenantId      tenant scoping this run
     * @param correlationId cross-service correlation identifier; may be null
     * @return a closeable {@link AgentRunSpan} handle
     */
    public AgentRunSpan startRun(String agentId, String agentReleaseId, String tenantId, String correlationId) {
        Span span = tracer.spanBuilder(AgentTelemetryContract.SPAN_RUN_START)
                .startSpan();
        span.setAttribute(AgentTelemetryContract.ATTR_AGENT_ID, agentId);
        span.setAttribute(AgentTelemetryContract.ATTR_AGENT_RELEASE_ID, agentReleaseId);
        span.setAttribute(AgentTelemetryContract.ATTR_TENANT_ID, tenantId);
        span.setAttribute(AgentTelemetryContract.ATTR_TELEMETRY_VERSION, AgentTelemetryContract.VERSION);
        if (correlationId != null) {
            span.setAttribute(AgentTelemetryContract.ATTR_CORRELATION_ID, correlationId);
        }
        return new DefaultAgentRunSpan(span);
    }

    // ── Phase 2: Context retrieval ────────────────────────────────────────────

    /**
     * Records a context retrieval child span under the given run span.
     *
     * @param runSpan   the parent run span
     * @param itemCount number of context items retrieved
     * @param latency   retrieval latency
     */
    public void traceContextRetrieval(AgentRunSpan runSpan, int itemCount, Duration latency) {
        Span parent = toSpan(runSpan);
        try (Scope ignored = parent.makeCurrent()) {
            Span child = tracer.spanBuilder(AgentTelemetryContract.SPAN_CONTEXT_RETRIEVAL)
                    .setParent(Context.current())
                    .startSpan();
            child.setAttribute(AgentTelemetryContract.ATTR_CONTEXT_ITEM_COUNT, itemCount);
            child.end();
        }
    }

    // ── Phase 3: Planner invocation ───────────────────────────────────────────

    /**
     * Records a planner invocation child span.
     *
     * @param runSpan the parent run span
     */
    public void tracePlannerInvoke(AgentRunSpan runSpan) {
        Span parent = toSpan(runSpan);
        try (Scope ignored = parent.makeCurrent()) {
            tracer.spanBuilder(AgentTelemetryContract.SPAN_PLANNER_INVOKE)
                    .setParent(Context.current())
                    .startSpan()
                    .end();
        }
    }

    // ── Phase 4: Policy evaluation ────────────────────────────────────────────

    /**
     * Records a policy evaluation child span.
     *
     * @param runSpan      the parent run span
     * @param policyPackId policy pack identifier applied
     * @param decision     policy decision string (e.g. {@code "ALLOW"} or {@code "DENY"})
     */
    public void tracePolicyEval(AgentRunSpan runSpan, String policyPackId, String decision) {
        Span parent = toSpan(runSpan);
        try (Scope ignored = parent.makeCurrent()) {
            Span child = tracer.spanBuilder(AgentTelemetryContract.SPAN_POLICY_EVAL)
                    .setParent(Context.current())
                    .startSpan();
            child.setAttribute(AgentTelemetryContract.ATTR_POLICY_PACK_ID, policyPackId);
            child.setAttribute(AgentTelemetryContract.ATTR_POLICY_DECISION, decision);
            child.end();
        }
    }

    // ── Phase 5: Tool execution ───────────────────────────────────────────────

    /**
     * Records a tool execution child span.
     *
     * @param runSpan     the parent run span
     * @param toolId      tool identifier
     * @param actionClass action class (e.g. {@code "READ"}, {@code "WRITE"})
     * @param decision    access decision (e.g. {@code "ALLOW"} or {@code "DENY"})
     */
    public void traceToolExecution(AgentRunSpan runSpan, String toolId, String actionClass, String decision) {
        Span parent = toSpan(runSpan);
        try (Scope ignored = parent.makeCurrent()) {
            Span child = tracer.spanBuilder(AgentTelemetryContract.SPAN_TOOL_EXECUTE)
                    .setParent(Context.current())
                    .startSpan();
            child.setAttribute(AgentTelemetryContract.ATTR_TOOL_ID, toolId);
            child.setAttribute(AgentTelemetryContract.ATTR_ACTION_CLASS, actionClass);
            child.setAttribute(AgentTelemetryContract.ATTR_DATA_ACCESS_DECISION, decision);
            child.end();
        }
    }

    // ── Phase 6: Sub-agent delegation ─────────────────────────────────────────

    /**
     * Records a sub-agent delegation child span.
     *
     * @param runSpan       the parent run span
     * @param subAgentId    the delegated agent identifier
     */
    public void traceSubAgentDelegate(AgentRunSpan runSpan, String subAgentId) {
        Span parent = toSpan(runSpan);
        try (Scope ignored = parent.makeCurrent()) {
            Span child = tracer.spanBuilder(AgentTelemetryContract.SPAN_SUB_AGENT_DELEGATE)
                    .setParent(Context.current())
                    .startSpan();
            child.setAttribute(AgentTelemetryContract.ATTR_AGENT_ID, subAgentId);
            child.end();
        }
    }

    // ── Phase 7: Approval request ─────────────────────────────────────────────

    /**
     * Records an approval request child span (human-in-the-loop gate).
     *
     * @param runSpan  the parent run span
     * @param toolId   tool requesting approval
     * @param decision approval decision (e.g. {@code "APPROVED"} or {@code "REJECTED"})
     */
    public void traceApprovalRequest(AgentRunSpan runSpan, String toolId, String decision) {
        Span parent = toSpan(runSpan);
        try (Scope ignored = parent.makeCurrent()) {
            Span child = tracer.spanBuilder(AgentTelemetryContract.SPAN_APPROVAL_REQUEST)
                    .setParent(Context.current())
                    .startSpan();
            child.setAttribute(AgentTelemetryContract.ATTR_TOOL_ID, toolId);
            child.setAttribute(AgentTelemetryContract.ATTR_POLICY_DECISION, decision);
            child.end();
        }
    }

    // ── Phase 8: Memory write ─────────────────────────────────────────────────

    /**
     * Records a memory write child span.
     *
     * @param runSpan      the parent run span
     * @param memoryClass  memory class/namespace (e.g. {@code "episodic"}, {@code "fact"})
     * @param namespaceId  namespace identifier for this write
     */
    public void traceMemoryWrite(AgentRunSpan runSpan, String memoryClass, String namespaceId) {
        Span parent = toSpan(runSpan);
        try (Scope ignored = parent.makeCurrent()) {
            Span child = tracer.spanBuilder(AgentTelemetryContract.SPAN_MEMORY_WRITE)
                    .setParent(Context.current())
                    .startSpan();
            child.setAttribute(AgentTelemetryContract.ATTR_MEMORY_CLASS, memoryClass);
            child.setAttribute(AgentTelemetryContract.ATTR_TENANT_ID, namespaceId);
            child.end();
        }
    }

    // ── Phase 9: Evaluation gate ──────────────────────────────────────────────

    /**
     * Records an evaluation gate child span (quality/safety check).
     *
     * @param runSpan  the parent run span
     * @param decision gate decision
     */
    public void traceEvalGate(AgentRunSpan runSpan, String decision) {
        Span parent = toSpan(runSpan);
        try (Scope ignored = parent.makeCurrent()) {
            Span child = tracer.spanBuilder(AgentTelemetryContract.SPAN_EVAL_GATE)
                    .setParent(Context.current())
                    .startSpan();
            child.setAttribute(AgentTelemetryContract.ATTR_POLICY_DECISION, decision);
            child.end();
        }
    }

    // ── Phase 10: External commit ─────────────────────────────────────────────

    /**
     * Records an external system commit child span.
     *
     * @param runSpan       the parent run span
     * @param actionClass   action class of the external commit
     */
    public void traceExternalCommit(AgentRunSpan runSpan, String actionClass) {
        Span parent = toSpan(runSpan);
        try (Scope ignored = parent.makeCurrent()) {
            Span child = tracer.spanBuilder(AgentTelemetryContract.SPAN_EXTERNAL_COMMIT)
                    .setParent(Context.current())
                    .startSpan();
            child.setAttribute(AgentTelemetryContract.ATTR_ACTION_CLASS, actionClass);
            child.end();
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private static Span toSpan(AgentRunSpan runSpan) {
        if (runSpan instanceof DefaultAgentRunSpan d) {
            return d.span();
        }
        throw new IllegalArgumentException("Unsupported AgentRunSpan implementation: " + runSpan.getClass());
    }

    // ── AgentRunSpan ─────────────────────────────────────────────────────────

    /**
     * A closeable handle to the root run span returned by {@link #startRun}.
     *
     * <p>Callers must {@link #close()} this handle (via try-with-resources) to ensure
     * the underlying span is ended regardless of outcome.
     */
    public sealed interface AgentRunSpan extends AutoCloseable permits DefaultAgentRunSpan {
        /** Sets the span status. */
        void setStatus(StatusCode status, String description);

        /** Records an exception on the span and transitions status to ERROR. */
        void recordException(Throwable t);

        /** Ends the span. Safe to call multiple times — subsequent calls are no-ops. */
        @Override
        void close();
    }

    // ── DefaultAgentRunSpan ───────────────────────────────────────────────────

    record DefaultAgentRunSpan(Span span) implements AgentRunSpan {

        @Override
        public void setStatus(StatusCode status, String description) {
            span.setStatus(status, description);
        }

        @Override
        public void recordException(Throwable t) {
            span.recordException(t);
            span.setStatus(StatusCode.ERROR, t.getMessage());
        }

        @Override
        public void close() {
            span.end();
        }
    }
}
