/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.stage;

import java.util.Map;

/**
 * Base interface for agent dispatch stages.
 *
 * <p>A stage is a pre-dispatch preparation step that can prepare data,
 * retrieve information, or perform other setup before agent execution.
 * Stages are composed in a pipeline to provide comprehensive preparation.
 *
 * @doc.type interface
 * @doc.purpose Base interface for agent dispatch stages in the safety pipeline
 * @doc.layer product
 * @doc.pattern Stage
 */
public interface AgentDispatchStage {

    /**
     * Result of a stage execution.
     *
     * @param succeeded whether the stage succeeded
     * @param errorMessage error message (if failed)
     * @param output stage output data
     */
    record StageResult(boolean succeeded, String errorMessage, Map<String, Object> output) {
        public static StageResult success(Map<String, Object> output) {
            return new StageResult(true, null, output);
        }

        public static StageResult success() {
            return new StageResult(true, null, Map.of());
        }

        public static StageResult failure(String errorMessage) {
            return new StageResult(false, errorMessage, Map.of());
        }
    }

    /**
     * Executes the stage for a given dispatch context.
     *
     * @param context the dispatch context
     * @return the stage result
     */
    StageResult execute(StageContext context);

    /**
     * Context for dispatch stage execution.
     *
     * @param agentId the agent ID
     * @param agentVersion the agent version
     * @param executionGrant the execution grant
     * @param metadata additional metadata
     */
    record StageContext(
            String agentId,
            String agentVersion,
            String executionGrant,
            Map<String, Object> metadata) {}
}
