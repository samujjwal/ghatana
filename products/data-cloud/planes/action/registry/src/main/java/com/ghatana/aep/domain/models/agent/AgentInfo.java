/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.aep.domain.models.agent;

/**
 * AgentInfo class in domain models package.
 * This is a type alias for the AgentInfo from the agent registry domain.
  * @doc.type class
 * @doc.purpose Provides agent info functionality.
 * @doc.layer product
 * @doc.pattern Agent
*/
public class AgentInfo extends com.ghatana.agent.registry.domain.AgentInfo {
    public AgentInfo() {
        super();
    }

    public AgentInfo(String agentId, String name, String type) {
        super(agentId, name, type);
    }
}
