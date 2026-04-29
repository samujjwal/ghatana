/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.aep.domain.models.agent;

import com.ghatana.agent.registry.domain.AgentInfo;

/**
 * Domain model for agent information.
 *
 * <p>Wraps {@link AgentInfo} for use in registry endpoints and pipeline domain logic.</p>
 *
 * @doc.type class
 * @doc.purpose Provides agent domain model as a registry-compatible wrapper around AgentInfo.
 * @doc.layer product
 * @doc.pattern Domain Model
 */
public class AgentDomainModel {
    private final AgentInfo agentInfo;

    public AgentDomainModel(AgentInfo agentInfo) {
        this.agentInfo = agentInfo;
    }

    public AgentInfo getAgentInfo() {
        return agentInfo;
    }

    // Delegate methods for backward compatibility
    public String getAgentId() {
        return agentInfo.getAgentId();
    }

    public String getName() {
        return agentInfo.getName();
    }

    public String getType() {
        return agentInfo.getType();
    }

    public String getStatus() {
        return agentInfo.getStatus();
    }
}
