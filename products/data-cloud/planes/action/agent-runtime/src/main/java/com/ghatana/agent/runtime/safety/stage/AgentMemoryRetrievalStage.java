/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.stage;

import java.util.Map;
import java.util.Objects;

/**
 * Stage that retrieves memory for agent execution.
 *
 * <p>This stage retrieves relevant memory (context, history, etc.)
 * for the agent before execution, ensuring the agent has access
 * to necessary information.
 *
 * @doc.type class
 * @doc.purpose Retrieves memory for agent execution
 * @doc.layer product
 * @doc.pattern Stage
 */
public final class AgentMemoryRetrievalStage implements AgentDispatchStage {

    private final MemoryRetriever memoryRetriever;

    public AgentMemoryRetrievalStage(MemoryRetriever memoryRetriever) {
        this.memoryRetriever = Objects.requireNonNull(memoryRetriever, "memoryRetriever must not be null");
    }

    @Override
    public StageResult execute(StageContext context) {
        Objects.requireNonNull(context, "context must not be null");

        String agentId = context.agentId();
        String agentVersion = context.agentVersion();

        try {
            Map<String, Object> memory = memoryRetriever.retrieveMemory(agentId, agentVersion);

            if (memory == null) {
                return StageResult.success(Map.of("memory", Map.of()));
            }

            return StageResult.success(Map.of("memory", memory));
        } catch (Exception e) {
            return StageResult.failure("Memory retrieval failed: " + e.getMessage());
        }
    }

    /**
     * Interface for retrieving agent memory.
     *
     * <p>This is a placeholder for the actual memory retrieval logic.
     * A production implementation would query the MemoryRetriever service.
     */
    public interface MemoryRetriever {
        /**
         * Retrieves memory for an agent.
         *
         * @param agentId the agent ID
         * @param agentVersion the agent version
         * @return memory map, or null if no memory exists
         */
        Map<String, Object> retrieveMemory(String agentId, String agentVersion);
    }
}
