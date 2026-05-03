package com.ghatana.digitalmarketing.application.ai;

import io.activej.promise.Promise;

import java.time.Duration;
import java.util.Map;

/**
 * Port for agent orchestration integration (DMOS-P1-018).
 *
 * <p>Defines the contract for invoking AI agents through the Kernel or a fallback.
 * Supports timeout policies, failure handling, and observability.</p>
 *
 * @doc.type interface
 * @doc.purpose Port for agent orchestration with Kernel integration (DMOS-P1-018)
 * @doc.layer application
 * @doc.pattern Port
 */
public interface DmAgentOrchestrationPort {

    /**
     * Invokes an agent with the specified parameters.
     *
     * @param agentType the type of agent to invoke
     * @param prompt the prompt to send to the agent
     * @param model the model to use (optional, uses default if null)
     * @param parameters additional parameters for the agent
     * @param timeout the timeout for the agent invocation
     * @return the agent response
     */
    Promise<AgentResponse> invokeAgent(
        AgentType agentType,
        String prompt,
        String model,
        Map<String, Object> parameters,
        Duration timeout
    );

    /**
     * Checks if the agent orchestration service is available.
     */
    Promise<Boolean> isAvailable();

    /**
     * Gets the health status of the agent orchestration service.
     */
    Promise<AgentHealthStatus> getHealthStatus();

    /**
     * Types of agents that can be invoked (DMOS-P1-018).
     */
    enum AgentType {
        STRATEGY_GENERATOR,
        AD_COPY_GENERATOR,
        LANDING_PAGE_GENERATOR,
        EMAIL_FOLLOW_UP_GENERATOR,
        PROPOSAL_SOW_GENERATOR,
        REPORT_NARRATIVE_GENERATOR,
        RECOMMENDATION_ENGINE
    }

    /**
     * Response from an agent invocation (DMOS-P1-018).
     */
    record AgentResponse(
        String output,
        String model,
        double confidence,
        String evidenceLocation,
        Duration duration,
        boolean success,
        String errorMessage
    ) {
        public AgentResponse {
            if (success && errorMessage != null) {
                throw new IllegalArgumentException("errorMessage must be null when success is true");
            }
            if (!success && errorMessage == null) {
                throw new IllegalArgumentException("errorMessage must be non-null when success is false");
            }
        }
    }

    /**
     * Health status of the agent orchestration service (DMOS-P1-018).
     */
    enum AgentHealthStatus {
        HEALTHY,
        DEGRADED,
        UNAVAILABLE
    }
}
