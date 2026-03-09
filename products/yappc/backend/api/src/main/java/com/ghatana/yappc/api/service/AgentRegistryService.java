/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module
 */
package com.ghatana.yappc.api.service;

import com.ghatana.yappc.api.domain.AgentRegistryEntry;
import com.ghatana.yappc.api.domain.AgentRegistryEntry.AgentStatus;
import com.ghatana.yappc.api.domain.AgentRegistryEntry.HealthStatus;
import com.ghatana.yappc.api.repository.AgentRegistryRepository;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Application service for managing the persistent agent registry.
 *
 * <p>Replaces the previous in-memory approach. All state is durable in
 * PostgreSQL, enabling agents to survive process restarts and enabling
 * distributed deployments to share a single source of truth.
 *
 * <p>Usage example:
 * <pre>{@code
 * AgentRegistryEntry entry = new AgentRegistryEntry();
 * entry.setId("my-agent");
 * entry.setName("My Agent");
 * entry.setVersion("1.0.0");
 * entry.setAgentType("WorkerAgent");
 * entry.setStatus(AgentStatus.ACTIVE);
 * entry.setCapabilities(List.of("code-review", "analysis"));
 * entry.setConfig(Map.of("maxConcurrency", 5));
 * entry.setTenantId("tenant-123");
 * entry.setHealthStatus(HealthStatus.HEALTHY);
 * entry.setLastHeartbeat(Instant.now());
 *
 * registryService.register(entry)
 *     .whenResult(saved -> log.info("Registered agent {}", saved.getId()));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Persistent agent registry application service
 * @doc.layer application
 * @doc.pattern Service
 */
public class AgentRegistryService {

    private static final Logger logger = LoggerFactory.getLogger(AgentRegistryService.class);

    private final AgentRegistryRepository repository;

    @Inject
    public AgentRegistryService(AgentRegistryRepository repository) {
        this.repository = repository;
    }

    /**
     * Registers or updates an agent entry.
     *
     * @param entry the agent entry to persist
     * @return Promise with the saved entry
     */
    public Promise<AgentRegistryEntry> register(AgentRegistryEntry entry) {
        if (entry.getStatus() == null)       entry.setStatus(AgentStatus.ACTIVE);
        if (entry.getHealthStatus() == null) entry.setHealthStatus(HealthStatus.UNKNOWN);
        if (entry.getLastHeartbeat() == null) entry.setLastHeartbeat(Instant.now());

        return repository.save(entry)
                .whenResult(saved -> logger.info(
                        "Registered agent id={} type={} tenant={}",
                        saved.getId(), saved.getAgentType(), saved.getTenantId()))
                .whenException(e -> logger.error(
                        "Failed to register agent id={}: {}", entry.getId(), e.getMessage()));
    }

    /**
     * Looks up an agent by ID.
     *
     * @param tenantId tenant scope
     * @param agentId  agent identifier
     * @return Promise with optional entry
     */
    public Promise<Optional<AgentRegistryEntry>> getAgent(String tenantId, String agentId) {
        return repository.findById(tenantId, agentId);
    }

    /**
     * Returns all active agents for a tenant.
     */
    public Promise<List<AgentRegistryEntry>> listActiveAgents(String tenantId) {
        return repository.findActiveByTenant(tenantId);
    }

    /**
     * Returns agents capable of a specific operation.
     *
     * @param tenantId   tenant scope
     * @param capability capability name
     * @return Promise with matching agents
     */
    public Promise<List<AgentRegistryEntry>> findByCapability(String tenantId, String capability) {
        return repository.findByCapability(tenantId, capability);
    }

    /**
     * Returns agents of a specific type.
     */
    public Promise<List<AgentRegistryEntry>> findByType(String tenantId, String agentType) {
        return repository.findByType(tenantId, agentType);
    }

    /**
     * Activates a previously inactive or suspended agent.
     */
    public Promise<Void> activate(String tenantId, String agentId) {
        return repository.updateStatus(tenantId, agentId, AgentStatus.ACTIVE)
                .whenResult(v -> logger.info("Activated agent id={} tenant={}", agentId, tenantId));
    }

    /**
     * Suspends an agent (temporarily unavailable).
     */
    public Promise<Void> suspend(String tenantId, String agentId) {
        return repository.updateStatus(tenantId, agentId, AgentStatus.SUSPENDED)
                .whenResult(v -> logger.info("Suspended agent id={} tenant={}", agentId, tenantId));
    }

    /**
     * Terminates an agent permanently.
     */
    public Promise<Void> terminate(String tenantId, String agentId) {
        return repository.updateStatus(tenantId, agentId, AgentStatus.TERMINATED)
                .whenResult(v -> logger.info("Terminated agent id={} tenant={}", agentId, tenantId));
    }

    /**
     * Records a liveness heartbeat for an agent (marks HEALTHY, updates timestamp).
     *
     * @param tenantId tenant scope
     * @param agentId  agent identifier
     */
    public Promise<Void> heartbeat(String tenantId, String agentId) {
        return repository.updateHeartbeat(tenantId, agentId)
                .whenException(e -> logger.warn(
                        "Heartbeat failed agent={} tenant={}: {}", agentId, tenantId, e.getMessage()));
    }

    /**
     * Records a named metric for an agent (e.g., execution count, latency).
     *
     * @param tenantId   tenant scope
     * @param agentId    agent identifier
     * @param metricName metric name
     * @param value      metric value
     */
    public Promise<Void> recordMetric(String tenantId, String agentId,
                                      String metricName, double value) {
        return repository.recordMetric(tenantId, agentId, metricName, value);
    }

    /**
     * Removes an agent from the registry permanently.
     *
     * @param tenantId tenant scope
     * @param agentId  agent identifier
     * @return Promise with true if the agent was found and deleted
     */
    public Promise<Boolean> deregister(String tenantId, String agentId) {
        return repository.delete(tenantId, agentId)
                .whenResult(deleted -> {
                    if (deleted) {
                        logger.info("Deregistered agent id={} tenant={}", agentId, tenantId);
                    } else {
                        logger.warn("Deregister: agent id={} not found in tenant={}", agentId, tenantId);
                    }
                });
    }
}
