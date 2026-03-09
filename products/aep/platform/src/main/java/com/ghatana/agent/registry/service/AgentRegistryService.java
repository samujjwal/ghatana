package com.ghatana.agent.registry.service;

import com.ghatana.aep.domain.agent.registry.AgentExecutionContext;
import com.ghatana.platform.domain.agent.registry.AgentMetrics;
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
     * Register a new agent manifest
     */
    Promise<AgentManifestProto> register(AgentManifestProto manifest);

    /**
     * Get an agent by ID
     */
    Promise<AgentManifestProto> getById(String id);

    /**
     * List all agents
     */
    Promise<List<AgentManifestProto>> listAll();

    /**
     * Update an existing agent
     */
    Promise<AgentManifestProto> update(String id, AgentManifestProto manifest);

    /**
     * Delete an agent
     * @param id The ID of the agent to delete
     * @param hardDelete If true, permanently delete the agent
     * @return A Promise that completes with true if the agent was deleted, false if not found
     */
    Promise<Boolean> delete(String id, boolean hardDelete);
    
    // ==================== AGENT DISCOVERY ====================
    
    /**
     * Find agents that can process a specific event type
     * 
     * @param eventTypeId The event type to find processors for
     * @return Promise containing list of agent manifests that support the event type
     */
    Promise<List<AgentManifestProto>> findByEventType(String eventTypeId);
    
    /**
     * Find agents with specific capabilities
     * 
     * @param capabilities Set of required capabilities
     * @return Promise containing list of agent manifests with matching capabilities
     */
    Promise<List<AgentManifestProto>> findByCapabilities(Set<String> capabilities);
    
    // ==================== AGENT EXECUTION ====================
    
    /**
     * Get runtime agent instance for execution
     * 
     * @param agentId The ID of the agent to retrieve
     * @return Promise containing the runtime agent instance
     */
    Promise<Agent> getAgentInstance(String agentId);
    
    /**
     * Execute an agent with event input
     * 
     * @param agentId The ID of the agent to execute
     * @param event The event to process
     * @param context Execution context with security and tenant information
     * @return Promise containing the list of output events
     */
    Promise<List<Event>> executeAgent(String agentId, Event event, AgentExecutionContext context);
    
    /**
     * Execute an agent with protocol buffer input/output
     * 
     * @param agentId The ID of the agent to execute
     * @param input Protocol buffer input containing event and context
     * @return Promise containing protocol buffer output with results
     */
    Promise<AgentResultProto> executeAgentProto(String agentId, AgentInputProto input);
    
    /**
     * Execute batch processing for multiple events
     * 
     * @param agentId The ID of the agent to execute
     * @param events List of events to process
     * @param context Execution context
     * @return Promise containing all output events from batch processing
     */
    Promise<List<Event>> executeBatch(String agentId, List<Event> events, AgentExecutionContext context);
    
    // ==================== MONITORING & METRICS ====================
    
    /**
     * Get current metrics for an agent
     * 
     * @param agentId The ID of the agent
     * @return Promise containing current agent metrics
     */
    Promise<AgentMetrics> getAgentMetrics(String agentId);
    
    /**
     * Check agent health status
     * 
     * @param agentId The ID of the agent to check
     * @return Promise containing true if agent is healthy
     */
    Promise<Boolean> isAgentHealthy(String agentId);
    
    /**
     * Get metrics for all registered agents
     * 
     * @return Promise containing metrics for all agents
     */
    Promise<List<AgentMetrics>> getAllAgentMetrics();
}
