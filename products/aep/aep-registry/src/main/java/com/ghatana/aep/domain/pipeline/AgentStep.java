/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.aep.domain.pipeline;

/**
 * AgentStep class in domain pipeline package.
 * This is a type alias for the AgentStep from the agent registry domain.
  * @doc.type class
 * @doc.purpose Provides agent step functionality.
 * @doc.layer product
 * @doc.pattern Agent
*/
public class AgentStep extends com.ghatana.agent.registry.domain.AgentStep {
    public AgentStep() {
        super();
    }

    public AgentStep(String stepId, String agentId, String stepType) {
        super(stepId, agentId, stepType);
    }
}
