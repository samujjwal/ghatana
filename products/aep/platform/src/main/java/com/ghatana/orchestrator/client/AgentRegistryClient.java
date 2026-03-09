package com.ghatana.orchestrator.client;

import java.util.List;
import java.util.Optional;

import com.ghatana.aep.domain.models.agent.AgentInfo;

import io.activej.promise.Promise;

/**
 * Client interface for communicating with the Agent Registry service.
 * 
 * Day 24 Implementation: Client for agent discovery and metadata
 */
public interface AgentRegistryClient {

    /**
     * List all active agents in the registry.
     */
    Promise<List<AgentInfo>> listAllAgents();

    /**
     * Get a specific agent by ID.
     */
    Promise<Optional<AgentInfo>> getAgent(String agentId);

    /**
     * List agents by type or capability.
     */
    Promise<List<AgentInfo>> listAgentsByType(String agentType);

    /**
     * Check if the client connection is healthy.
     */
    Promise<Boolean> isHealthy();
}