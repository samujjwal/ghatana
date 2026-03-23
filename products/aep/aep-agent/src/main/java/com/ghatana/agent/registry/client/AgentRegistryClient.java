/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.registry.client;

import com.ghatana.agent.registry.domain.AgentInfo;
import com.ghatana.agent.registry.domain.AgentStep;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Client interface for interacting with the agent registry.
 *
 * @doc.type interface
 * @doc.purpose Async client interface for agent registry operations
 * @doc.layer product
 * @doc.pattern Client
 */
public interface AgentRegistryClient {

    /**
     * List all registered agents.
     */
    Promise<List<AgentInfo>> listAllAgents();

    /**
     * Get agent information by ID.
     */
    Promise<Optional<AgentInfo>> getAgent(String agentId);

    /**
     * Get agents by type.
     */
    Promise<List<AgentInfo>> listAgentsByType(String type);

    /**
     * Check if the agent registry is healthy/reachable.
     */
    Promise<Boolean> isHealthy();

    /**
     * Register a new agent in the registry.
     */
    default Promise<Void> registerAgent(AgentInfo agentInfo) {
        return Promise.complete();
    }

    /**
     * Update agent information.
     */
    default Promise<Void> updateAgent(AgentInfo agentInfo) {
        return Promise.complete();
    }

    /**
     * Unregister an agent from the registry.
     */
    default Promise<Void> unregisterAgent(String agentId) {
        return Promise.complete();
    }

    /**
     * Register an agent step.
     */
    default Promise<Void> registerAgentStep(AgentStep agentStep) {
        return Promise.complete();
    }

    /**
     * Get agent steps for an agent.
     */
    default Promise<List<AgentStep>> getAgentSteps(String agentId) {
        return Promise.of(List.of());
    }

    /**
     * Update agent step.
     */
    default Promise<Void> updateAgentStep(AgentStep agentStep) {
        return Promise.complete();
    }

    /**
     * Delete agent step.
     */
    default Promise<Void> deleteAgentStep(String stepId) {
        return Promise.complete();
    }
}
