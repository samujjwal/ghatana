/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.gate;

import java.util.Map;
import java.util.Objects;

/**
 * Gate that selects the appropriate execution mode for the agent.
 *
 * <p>This gate classifies the task and selects the execution mode
 * based on task risk, novelty, and agent mastery state.
 *
 * @doc.type class
 * @doc.purpose Selects appropriate execution mode for agent dispatch
 * @doc.layer product
 * @doc.pattern Gate
 */
public final class AgentModeSelectionGate implements AgentDispatchGate {

    private final TaskClassifier taskClassifier;
    private final MasteryAwareModeSelector modeSelector;

    public AgentModeSelectionGate(TaskClassifier taskClassifier, MasteryAwareModeSelector modeSelector) {
        this.taskClassifier = Objects.requireNonNull(taskClassifier, "taskClassifier must not be null");
        this.modeSelector = Objects.requireNonNull(modeSelector, "modeSelector must not be null");
    }

    @Override
    public GateResult evaluate(DispatchContext context) {
        Objects.requireNonNull(context, "context must not be null");

        String agentId = context.agentId();
        Map<String, Object> metadata = context.metadata();

        // Classify the task
        Map<String, Object> taskClassification = taskClassifier.classifyTask(metadata);

        if (taskClassification == null) {
            return GateResult.failure("Task classification failed for agent: " + agentId);
        }

        // Select mode based on classification and mastery
        String selectedMode = modeSelector.selectMode(agentId, taskClassification);

        if (selectedMode == null) {
            return GateResult.failure("Mode selection failed for agent: " + agentId);
        }

        // Update context with selected mode
        Map<String, Object> updatedMetadata = new java.util.LinkedHashMap<>(metadata);
        updatedMetadata.put("selectedMode", selectedMode);

        // Return success with updated context (in production, this would update the context)
        return GateResult.success();
    }

    /**
     * Interface for classifying tasks.
     *
     * <p>This is a placeholder for the actual task classification logic.
     * A production implementation would query the TaskClassifier service.
     */
    public interface TaskClassifier {
        /**
         * Classifies a task based on its metadata.
         *
         * @param taskMetadata the task metadata
         * @return classification map, or null if classification fails
         */
        Map<String, Object> classifyTask(Map<String, Object> taskMetadata);
    }

    /**
     * Interface for selecting execution mode based on mastery.
     *
     * <p>This is a placeholder for the actual mode selection logic.
     * A production implementation would query the MasteryAwareModeSelector service.
     */
    public interface MasteryAwareModeSelector {
        /**
         * Selects the execution mode for an agent.
         *
         * @param agentId the agent ID
         * @param taskClassification the task classification
         * @return selected mode, or null if selection fails
         */
        String selectMode(String agentId, Map<String, Object> taskClassification);
    }
}
