package com.ghatana.agent.workflow;

import com.ghatana.agent.Agent;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registry for managing workflow agents.
 *
 * <p>Provides centralized management of workflow agents, including registration,
 * lookup, and lifecycle management. The registry maintains the mapping between
 * {@link WorkflowAgentRole} and available agent implementations.
 *
 * <p><b>Thread Safety:</b> Implementations must be thread-safe as the registry
 * may be accessed concurrently from multiple event loop threads.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * // Register an agent
 * registry.register(agentId, WorkflowAgentRole.CODE_REVIEWER, agent);
 *
 * // Look up agents by role
 * List<String> reviewers = registry.getAgentsByRole(WorkflowAgentRole.CODE_REVIEWER).getResult();
 *
 * // Get specific agent
 * Optional<Agent> agent = registry.getAgent(agentId).getResult();
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Workflow agent registry
 * @doc.layer core
 * @doc.pattern Registry
 */
public interface WorkflowAgentRegistry {

    /**
     * Registers a new agent with the given role.
     *
     * @param agentId Unique agent identifier
     * @param role The workflow role this agent fulfills
     * @param agent The agent implementation
     * @return A Promise resolving when registration is complete
     */
    @NotNull
    Promise<Void> register(
            @NotNull String agentId,
            @NotNull WorkflowAgentRole role,
            @NotNull Agent agent
    );

    /**
     * Unregisters an agent.
     *
     * @param agentId The agent ID to unregister
     * @return A Promise resolving to true if the agent was removed
     */
    @NotNull
    Promise<Boolean> unregister(@NotNull String agentId);

    /**
     * Gets an agent by ID.
     *
     * @param agentId The agent ID
     * @return A Promise resolving to the agent if found
     */
    @NotNull
    Promise<Optional<Agent>> getAgent(@NotNull String agentId);

    /**
     * Gets all agent IDs for a specific role.
     *
     * @param role The workflow role
     * @return A Promise resolving to the list of agent IDs
     */
    @NotNull
    Promise<List<String>> getAgentsByRole(@NotNull WorkflowAgentRole role);

    /**
     * Gets the role assigned to an agent.
     *
     * @param agentId The agent ID
     * @return A Promise resolving to the role if the agent exists
     */
    @NotNull
    Promise<Optional<WorkflowAgentRole>> getAgentRole(@NotNull String agentId);

    /**
     * Gets metadata for an agent.
     *
     * @param agentId The agent ID
     * @return A Promise resolving to the metadata if the agent exists
     */
    @NotNull
    Promise<Optional<AgentMetadata>> getAgentMetadata(@NotNull String agentId);

    /**
     * Lists all registered agent IDs.
     *
     * @return A Promise resolving to all agent IDs
     */
    @NotNull
    Promise<Set<String>> listAgentIds();

    /**
     * Lists all roles that have at least one registered agent.
     *
     * @return A Promise resolving to the set of active roles
     */
    @NotNull
    Promise<Set<WorkflowAgentRole>> listActiveRoles();

    /**
     * Checks if an agent is registered and enabled.
     *
     * @param agentId The agent ID
     * @return A Promise resolving to true if the agent is available
     */
    @NotNull
    Promise<Boolean> isAvailable(@NotNull String agentId);

    /**
     * Updates the enabled state of an agent.
     *
     * @param agentId The agent ID
     * @param enabled Whether the agent should be enabled
     * @return A Promise resolving when the update is complete
     */
    @NotNull
    Promise<Void> setEnabled(@NotNull String agentId, boolean enabled);

    /**
     * Metadata for a registered agent.
     *
     * @param agentId The agent ID
     * @param role The assigned role
     * @param name Display name
     * @param description Description
     * @param enabled Whether the agent is enabled
     * @param registeredAt Registration timestamp (epoch millis)
     * @param capabilities Map of capability names to descriptions
     */
    record AgentMetadata(
            @NotNull String agentId,
            @NotNull WorkflowAgentRole role,
            @NotNull String name,
            @Nullable String description,
            boolean enabled,
            long registeredAt,
            @NotNull Map<String, String> capabilities
    ) {}
}
