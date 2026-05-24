/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.gate;

import java.util.Map;
import java.util.Objects;

/**
 * Gate that validates mastery state compatibility before dispatch.
 *
 * <p>This gate checks that the agent's mastery state is compatible
 * with the requested operation, ensuring that the agent has sufficient
 * mastery for the task.
 *
 * @doc.type class
 * @doc.purpose Validates mastery state compatibility before agent dispatch
 * @doc.layer product
 * @doc.pattern Gate
 */
public final class AgentMasteryGate implements AgentDispatchGate {

    private final MasteryRegistry masteryRegistry;

    public AgentMasteryGate(MasteryRegistry masteryRegistry) {
        this.masteryRegistry = Objects.requireNonNull(masteryRegistry, "masteryRegistry must not be null");
    }

    @Override
    public GateResult evaluate(DispatchContext context) {
        Objects.requireNonNull(context, "context must not be null");

        String agentId = context.agentId();
        String agentVersion = context.agentVersion();

        Map<String, Object> masteryState = masteryRegistry.getMasteryState(agentId, agentVersion);

        if (masteryState == null) {
            return GateResult.failure(
                "Mastery state not found for agent: " + agentId + "@" + agentVersion);
        }

        if (Boolean.FALSE.equals(masteryState.get("isReady"))) {
            return GateResult.failure(
                "Agent mastery state not ready: " + agentId + "@" + agentVersion);
        }

        if (masteryState.containsKey("requiredMasteryLevel")) {
            int currentLevel = (int) masteryState.getOrDefault("currentMasteryLevel", 0);
            int requiredLevel = (int) masteryState.get("requiredMasteryLevel");
            
            if (currentLevel < requiredLevel) {
                return GateResult.failure(
                    "Insufficient mastery level: " + currentLevel + " < " + requiredLevel);
            }
        }

        return GateResult.success();
    }

    /**
     * Interface for querying agent mastery state.
     *
     * <p>This is a placeholder for the actual mastery registry logic.
     * A production implementation would query the MasteryRegistry service.
     */
    public interface MasteryRegistry {
        /**
         * Gets the mastery state for an agent.
         *
         * @param agentId the agent ID
         * @param agentVersion the agent version
         * @return mastery state map, or null if not found
         */
        Map<String, Object> getMasteryState(String agentId, String agentVersion);
    }
}
