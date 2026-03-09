/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.repository;

import com.ghatana.yappc.api.domain.AgentRegistryEntry;
import com.ghatana.yappc.api.domain.AgentRegistryEntry.AgentStatus;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the persistent agent registry.
 *
 * @doc.type interface
 * @doc.purpose Agent registry persistence port
 * @doc.layer domain
 * @doc.pattern Repository
 */
public interface AgentRegistryRepository {

    /**
     * Upserts an agent entry (insert or update by primary key).
     *
     * @param entry the agent entry to persist
     * @return Promise with the saved entry
     */
    Promise<AgentRegistryEntry> save(AgentRegistryEntry entry);

    /**
     * Finds an agent by ID within a tenant.
     *
     * @param tenantId tenant scope
     * @param agentId  agent identifier
     * @return Promise with optional entry
     */
    Promise<Optional<AgentRegistryEntry>> findById(String tenantId, String agentId);

    /**
     * Returns all active agents for a tenant.
     *
     * @param tenantId tenant scope
     * @return Promise with list of active agents
     */
    Promise<List<AgentRegistryEntry>> findActiveByTenant(String tenantId);

    /**
     * Returns agents with a specific capability for a tenant.
     *
     * @param tenantId   tenant scope
     * @param capability capability name to match
     * @return Promise with matching agents
     */
    Promise<List<AgentRegistryEntry>> findByCapability(String tenantId, String capability);

    /**
     * Returns agents of a specific type for a tenant.
     *
     * @param tenantId  tenant scope
     * @param agentType agent type to match
     * @return Promise with matching agents
     */
    Promise<List<AgentRegistryEntry>> findByType(String tenantId, String agentType);

    /**
     * Updates status field for a single agent.
     *
     * @param tenantId tenant scope
     * @param agentId  agent identifier
     * @param status   new status
     * @return Promise completing when the update is done
     */
    Promise<Void> updateStatus(String tenantId, String agentId, AgentStatus status);

    /**
     * Updates the heartbeat timestamp and marks the agent healthy.
     *
     * @param tenantId tenant scope
     * @param agentId  agent identifier
     * @return Promise completing when the update is done
     */
    Promise<Void> updateHeartbeat(String tenantId, String agentId);

    /**
     * Records a metric data point for an agent.
     *
     * @param tenantId   tenant scope
     * @param agentId    agent identifier
     * @param metricName metric name
     * @param value      metric value
     * @return Promise completing when the record is inserted
     */
    Promise<Void> recordMetric(String tenantId, String agentId, String metricName, double value);

    /**
     * Deletes an agent entry permanently.
     *
     * @param tenantId tenant scope
     * @param agentId  agent identifier
     * @return Promise completing with true if a row was deleted
     */
    Promise<Boolean> delete(String tenantId, String agentId);
}
