/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.pipeline;

import com.ghatana.agent.runtime.safety.gate.*;
import com.ghatana.agent.runtime.safety.stage.AgentDispatchStage;
import com.ghatana.agent.runtime.safety.stage.AgentMemoryRetrievalStage;
import com.ghatana.agent.runtime.safety.stage.AgentTraceStage;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pipeline that orchestrates all gates and stages for agent dispatch.
 *
 * <p>This pipeline composes all gates and stages in sequence, providing
 * comprehensive safety checks and preparation before agent execution.
 *
 * @doc.type class
 * @doc.purpose Orchestrates all gates and stages for agent dispatch
 * @doc.layer product
 * @doc.pattern Pipeline
 */
public final class AgentDispatchPipeline {

    private final List<AgentDispatchGate> gates;
    private final List<AgentDispatchStage> stages;

    public AgentDispatchPipeline(List<AgentDispatchGate> gates, List<AgentDispatchStage> stages) {
        this.gates = List.copyOf(Objects.requireNonNull(gates, "gates must not be null"));
        this.stages = List.copyOf(Objects.requireNonNull(stages, "stages must not be null"));
    }

    /**
     * Executes the pipeline for a given dispatch context.
     *
     * @param context the dispatch context
     * @return the pipeline result
     */
    public PipelineResult execute(AgentDispatchGate.DispatchContext context) {
        Objects.requireNonNull(context, "context must not be null");

        // Execute all gates
        for (AgentDispatchGate gate : gates) {
            AgentDispatchGate.GateResult gateResult = gate.evaluate(context);
            if (!gateResult.passed()) {
                return PipelineResult.gateFailed(gate.getClass().getSimpleName(), gateResult.reason());
            }
        }

        // Execute all stages
        Map<String, Object> accumulatedOutput = new java.util.LinkedHashMap<>();
        for (AgentDispatchStage stage : stages) {
            AgentDispatchStage.StageContext stageContext = new AgentDispatchStage.StageContext(
                context.agentId(),
                context.agentVersion(),
                context.executionGrant(),
                context.metadata());

            AgentDispatchStage.StageResult stageResult = stage.execute(stageContext);
            if (!stageResult.succeeded()) {
                return PipelineResult.stageFailed(stage.getClass().getSimpleName(), stageResult.errorMessage());
            }

            accumulatedOutput.putAll(stageResult.output());
        }

        return PipelineResult.success(accumulatedOutput);
    }

    /**
     * Result of a pipeline execution.
     *
     * @param succeeded whether the pipeline succeeded
     * @param failedComponent the component that failed (if any)
     * @param errorMessage error message (if failed)
     * @param output accumulated output from stages
     */
    public record PipelineResult(
            boolean succeeded,
            String failedComponent,
            String errorMessage,
            Map<String, Object> output) {

        public static PipelineResult success(Map<String, Object> output) {
            return new PipelineResult(true, null, null, output);
        }

        public static PipelineResult gateFailed(String gateName, String reason) {
            return new PipelineResult(false, gateName, reason, Map.of());
        }

        public static PipelineResult stageFailed(String stageName, String errorMessage) {
            return new PipelineResult(false, stageName, errorMessage, Map.of());
        }
    }

    /**
     * Builder for creating an AgentDispatchPipeline.
     */
    public static class Builder {
        private final java.util.List<AgentDispatchGate> gates = new java.util.ArrayList<>();
        private final java.util.List<AgentDispatchStage> stages = new java.util.ArrayList<>();

        public Builder addGate(AgentDispatchGate gate) {
            Objects.requireNonNull(gate, "gate must not be null");
            gates.add(gate);
            return this;
        }

        public Builder addStage(AgentDispatchStage stage) {
            Objects.requireNonNull(stage, "stage must not be null");
            stages.add(stage);
            return this;
        }

        public AgentDispatchPipeline build() {
            return new AgentDispatchPipeline(gates, stages);
        }
    }
}
