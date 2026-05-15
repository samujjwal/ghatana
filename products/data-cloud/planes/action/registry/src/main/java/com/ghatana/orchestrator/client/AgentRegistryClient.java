/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.client;

import io.activej.promise.Promise;

import java.util.Optional;

/**
 * Client interface for querying the agent registry.
 *
 * <p>Used by pipeline validation to verify agent references exist
 * before accepting pipeline configurations.
 *
 * @doc.type interface
 * @doc.purpose Lightweight client for agent registry lookups
 * @doc.layer product
 * @doc.pattern Client Interface
 */
public interface AgentRegistryClient {

    /**
     * Looks up an agent by its reference identifier.
     *
     * @param agentRef the agent reference (e.g., agent ID or qualified name)
     * @return a Promise resolving to the agent info if found, or empty if not
     */
    Promise<Optional<AgentInfo>> getAgent(String agentRef);

    /**
     * Check if the agent registry is healthy/reachable.
     *
     * @return a Promise resolving to true if healthy
     */
    default Promise<Boolean> isHealthy() {
        return Promise.of(Boolean.TRUE);
    }

    /**
     * Minimal agent info returned from registry lookups.
     */
    class AgentInfo {
        private final String agentId;
        private final String name;
        private final String status;

        public AgentInfo(String agentId, String name, String status) {
            this.agentId = agentId;
            this.name = name;
            this.status = status;
        }

        public String getAgentId() { return agentId; }
        public String getName() { return name; }
        public String getStatus() { return status; }
    }
}
