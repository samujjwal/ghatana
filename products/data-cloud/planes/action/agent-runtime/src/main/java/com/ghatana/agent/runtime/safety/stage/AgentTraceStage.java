/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.stage;

import java.util.Map;
import java.util.Objects;

/**
 * Stage that records trace events and OTel spans.
 *
 * <p>This stage records trace events and emits OpenTelemetry spans
 * for observability and debugging of agent execution.
 *
 * @doc.type class
 * @doc.purpose Records trace events and OTel spans for agent execution
 * @doc.layer product
 * @doc.pattern Stage
 */
public final class AgentTraceStage implements AgentDispatchStage {

    private final AgentTraceLedger traceLedger;
    private final AgentRunTracer runTracer;

    public AgentTraceStage(AgentTraceLedger traceLedger, AgentRunTracer runTracer) {
        this.traceLedger = Objects.requireNonNull(traceLedger, "traceLedger must not be null");
        this.runTracer = Objects.requireNonNull(runTracer, "runTracer must not be null");
    }

    @Override
    public StageResult execute(StageContext context) {
        Objects.requireNonNull(context, "context must not be null");

        String agentId = context.agentId();
        String agentVersion = context.agentVersion();

        try {
            // Append trace event to ledger
            traceLedger.appendEvent(agentId, agentVersion, "dispatch_start", Map.of(
                "timestamp", System.currentTimeMillis(),
                "grant", context.executionGrant()
            ));

            // Start OTel span
            String spanId = runTracer.startSpan(agentId, agentVersion, "agent_dispatch");

            return StageResult.success(Map.of("spanId", spanId));
        } catch (Exception e) {
            return StageResult.failure("Trace recording failed: " + e.getMessage());
        }
    }

    /**
     * Interface for appending trace events to the ledger.
     *
     * <p>This is a placeholder for the actual trace ledger logic.
     * A production implementation would query the AgentTraceLedger service.
     */
    public interface AgentTraceLedger {
        /**
         * Appends a trace event to the ledger.
         *
         * @param agentId the agent ID
         * @param agentVersion the agent version
         * @param eventType the event type
         * @param eventData the event data
         */
        void appendEvent(String agentId, String agentVersion, String eventType, Map<String, Object> eventData);
    }

    /**
     * Interface for tracing agent runs with OTel.
     *
     * <p>This is a placeholder for the actual run tracing logic.
     * A production implementation would query the AgentRunTracer service.
     */
    public interface AgentRunTracer {
        /**
         * Starts an OTel span for the agent run.
         *
         * @param agentId the agent ID
         * @param agentVersion the agent version
         * @param spanName the span name
         * @return the span ID
         */
        String startSpan(String agentId, String agentVersion, String spanName);
    }
}
