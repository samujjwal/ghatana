/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.gate;

import java.util.Map;

/**
 * Base interface for agent dispatch gates.
 *
 * <p>A gate is a pre-dispatch check that can allow or block agent execution.
 * Gates are composed in a pipeline to provide comprehensive safety checks.
 *
 * @doc.type interface
 * @doc.purpose Base interface for agent dispatch gates in the safety pipeline
 * @doc.layer product
 * @doc.pattern Gate
 */
public interface AgentDispatchGate {

    /**
     * Result of a gate evaluation.
     *
     * @param passed whether the gate passed
     * @param reason human-readable reason for failure (if failed)
     */
    record GateResult(boolean passed, String reason) {
        public static GateResult success() {
            return new GateResult(true, null);
        }

        public static GateResult failure(String reason) {
            return new GateResult(false, reason);
        }
    }

    /**
     * Evaluates the gate for a given dispatch context.
     *
     * @param context the dispatch context
     * @return the gate result
     */
    GateResult evaluate(DispatchContext context);

    /**
     * Context for dispatch gate evaluation.
     *
     * @param agentId the agent ID
     * @param agentVersion the agent version
     * @param executionGrant the execution grant
     * @param metadata additional metadata
     */
    record DispatchContext(
            String agentId,
            String agentVersion,
            String executionGrant,
            Map<String, Object> metadata) {}
}
