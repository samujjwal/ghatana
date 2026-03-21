/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.aep.domain.models.agent;

import com.ghatana.agent.registry.domain.AgentInfo;

/**
 * Domain model for agent information.
 * This is a wrapper around the AgentInfo domain class for compatibility.
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
