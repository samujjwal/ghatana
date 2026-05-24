/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.safety.gate;

import java.util.Map;
import java.util.Objects;

/**
 * Gate that checks if the agent release is in a response-serving state.
 *
 * <p>This gate rejects dispatch if the AgentRelease is not response-serving,
 * ensuring that only approved and stable agent versions are executed.
 *
 * @doc.type class
 * @doc.purpose Validates agent release state before dispatch
 * @doc.layer product
 * @doc.pattern Gate
 */
public final class AgentReleaseGate implements AgentDispatchGate {

    private final ReleaseStateChecker releaseStateChecker;

    public AgentReleaseGate(ReleaseStateChecker releaseStateChecker) {
        this.releaseStateChecker = Objects.requireNonNull(releaseStateChecker, "releaseStateChecker must not be null");
    }

    @Override
    public GateResult evaluate(DispatchContext context) {
        Objects.requireNonNull(context, "context must not be null");

        boolean isResponseServing = releaseStateChecker.isResponseServing(
            context.agentId(),
            context.agentVersion());

        if (!isResponseServing) {
            return GateResult.failure(
                "Agent release " + context.agentId() + "@" + context.agentVersion() +
                " is not in response-serving state");
        }

        return GateResult.success();
    }

    /**
     * Interface for checking agent release state.
     *
     * <p>This is a placeholder for the actual release state checking logic.
     * A production implementation would query the AgentReleaseRepository.
     */
    public interface ReleaseStateChecker {
        /**
         * Checks if an agent release is in response-serving state.
         *
         * @param agentId the agent ID
         * @param agentVersion the agent version
         * @return true if response-serving
         */
        boolean isResponseServing(String agentId, String agentVersion);
    }
}
