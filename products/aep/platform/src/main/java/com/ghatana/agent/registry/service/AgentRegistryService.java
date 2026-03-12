package com.ghatana.agent.registry.service;

import com.ghatana.aep.domain.agent.registry.AgentExecutionContext;
import com.ghatana.platform.domain.agent.registry.AgentMetrics;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.agent.Agent;
import com.ghatana.contracts.agent.v1.AgentManifestProto;
import com.ghatana.contracts.agent.v1.AgentInputProto;
import com.ghatana.contracts.agent.v1.AgentResultProto;
import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Set;

/**
 * Enhanced Agent Registry service that combines manifest management with agent execution capabilities.
 *
 * <p><b>Purpose</b><br>
 * This service consolidates functionality from event-core agent management while maintaining
 * the existing Promise-based API architecture. It provides a unified interface for:
 * <ul>
 *   <li>Agent manifest registration, update, and deletion</li>
 *   <li>Agent discovery by event type and capabilities</li>
 *   <li>Agent runtime instantiation and execution</li>
 *   <li>Batch processing for high-throughput scenarios</li>
 *   <li>Agent metrics collection and monitoring</li>
 * </ul>
 *
 * <p><b>Architecture Role</b><br>
 * Central service interface in the Agent Registry bounded context. Implementations
 * must use ActiveJ Promise for all async operations per platform standards.
 *
 * <p><b>Usage Example</b>
 * <pre>{@code
 * // Register an agent
 * Promise<AgentManifestProto> registration = registryService.register(manifest);
 *
 * // Execute agent with event
 * Promise<List<Event>> results = registryService.executeAgent(
 *     "agent-123",
 *     incomingEvent,
 *     executionContext
 * );
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Core service interface for agent manifest management and execution
 * @doc.layer product
 * @doc.pattern Service, Repository, Registry
 *
 * @since 2.0.0
 */
public interface AgentRegistryService {
    
    // ==================== MANIFEST MANAGEMENT ====================

    /**
     * Register a new agent manifest scoped to the given tenant.
     *
     * @param tenantId tenant that owns this agent manifest
     * @param manifest the agent manifest to register
     */
    Promise<AgentManifestProto> register(TenantId tenantId, AgentManifestProto manifest);

    /**
     * Get an agent manifest by ID within a tenant.
     *
     * @param tenantId tenant scope
     * @param id       agent identifier
     */
    Promise<AgentManifestProto> getById(TenantId tenantId, String id);

    /**
     * List all agent manifests visible to a tenant.
     *
     * @param tenantId tenant scope
     */
    Promise<List<AgentManifestProto>> listAll(TenantId tenantId);

    /**
     * Update an existing agent manifest.
     *
     * @param tenantId tenant scope
     * @param id       agent identifier
     * @param manifest updated manifest
     */
    Promise<AgentManifestProto> update(TenantId tenantId, String id, AgentManifestProto manifest);

    /**
     * Delete an agent manifest.
     *
     * @param tenantId   tenant scope
     * @param id         agent identifier
     * @param hardDelete if {@code true}, permanently delete; otherwise soft-delete
     * @return {@code true} if deleted, {@code false} if not found
     */
    Promise<Boolean> delete(TenantId tenantId, String id, boolean hardDelete);

    // ==================== AGENT DISCOVERY ====================

    /**
     * Find agent manifests that handle a specific event type.
     *
     * @param tenantId    tenant scope
     * @param eventTypeId the event type to match
     */
    Promise<List<AgentManifestProto>> findByEventType(TenantId tenantId, String eventTypeId);

    /**
     * Find agent manifests that declare all of the given capabilities.
     *
     * @param tenantId     tenant scope
     * @param capabilities required capability set
     */
    Promise<List<AgentManifestProto>> findByCapabilities(TenantId tenantId, Set<String> capabilities);

    // ==================== AGENT EXECUTION ====================

    /**
     * Retrieve a runtime agent instance for execution.
     *
     * @param tenantId tenant scope
     * @param agentId  agent identifier
     */
    Promise<Agent> getAgentInstance(TenantId tenantId, String agentId);

    /**
     * Execute an agent with a single event input.
     *
     * @param tenantId agent's owning tenant
     * @param agentId  agent identifier
     * @param event    event to process
     * @param context  execution context (also carries {@link AgentExecutionContext#tenantId()})
     * @return output events produced by the agent
     */
    Promise<List<Event>> executeAgent(TenantId tenantId, String agentId, Event event, AgentExecutionContext context);

    /**
     * Execute an agent using protocol-buffer I/O.
     *
     * @param tenantId tenant scope
     * @param agentId  agent identifier
     * @param input    protobuf input
     */
    Promise<AgentResultProto> executeAgentProto(TenantId tenantId, String agentId, AgentInputProto input);

    /**
     * Execute an agent against a batch of events.
     *
     * @param tenantId tenant scope
     * @param agentId  agent identifier
     * @param events   events to process
     * @param context  execution context
     * @return all output events produced during batch processing
     */
    Promise<List<Event>> executeBatch(TenantId tenantId, String agentId, List<Event> events, AgentExecutionContext context);

    // ==================== MONITORING & METRICS ====================

    /**
     * Get runtime metrics for a specific agent.
     *
     * @param tenantId tenant scope
     * @param agentId  agent identifier
     */
    Promise<AgentMetrics> getAgentMetrics(TenantId tenantId, String agentId);

    /**
     * Check the health of a specific agent.
     *
     * @param tenantId tenant scope
     * @param agentId  agent identifier
     * @return {@code true} if the agent is healthy
     */
    Promise<Boolean> isAgentHealthy(TenantId tenantId, String agentId);

    /**
     * Get runtime metrics for all agents visible to this tenant.
     *
     * @param tenantId tenant scope
     */
    Promise<List<AgentMetrics>> getAllAgentMetrics(TenantId tenantId);
}
