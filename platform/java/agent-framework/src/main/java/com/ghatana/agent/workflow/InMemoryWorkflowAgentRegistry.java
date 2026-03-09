package com.ghatana.agent.workflow;

import com.ghatana.agent.Agent;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of WorkflowAgentRegistry.
 *
 * <p>Provides thread-safe agent registration and lookup using concurrent
 * data structures. Suitable for single-node deployments; for distributed
 * scenarios, consider a Redis-backed implementation.
 *
 * <p><b>Thread Safety:</b> This implementation is thread-safe through
 * use of ConcurrentHashMap and immutable return values.
 *
 * @doc.type class
 * @doc.purpose In-memory workflow agent registry
 * @doc.layer infrastructure
 * @doc.pattern Registry
 */
public class InMemoryWorkflowAgentRegistry implements WorkflowAgentRegistry {

    private final ConcurrentHashMap<String, RegistryEntry> agents = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WorkflowAgentRole, Set<String>> roleIndex = new ConcurrentHashMap<>();

    /**
     * Internal registry entry.
     */
    private record RegistryEntry(
            Agent agent,
            AgentMetadata metadata
    ) {}

    @Override
    @NotNull
    public Promise<Void> register(
            @NotNull String agentId,
            @NotNull WorkflowAgentRole role,
            @NotNull Agent agent
    ) {
        Objects.requireNonNull(agentId, "agentId is required");
        Objects.requireNonNull(role, "role is required");
        Objects.requireNonNull(agent, "agent is required");

        AgentMetadata metadata = new AgentMetadata(
                agentId,
                role,
                agent.getCapabilities().name(),
                agent.getCapabilities().description(),
                true,
                System.currentTimeMillis(),
                Map.of()
        );

        RegistryEntry entry = new RegistryEntry(agent, metadata);
        agents.put(agentId, entry);

        // Update role index
        roleIndex.computeIfAbsent(role, k -> ConcurrentHashMap.newKeySet())
                .add(agentId);

        return Promise.complete();
    }

    @Override
    @NotNull
    public Promise<Boolean> unregister(@NotNull String agentId) {
        Objects.requireNonNull(agentId, "agentId is required");

        RegistryEntry removed = agents.remove(agentId);
        if (removed != null) {
            // Remove from role index
            Set<String> roleAgents = roleIndex.get(removed.metadata().role());
            if (roleAgents != null) {
                roleAgents.remove(agentId);
            }
            return Promise.of(true);
        }
        return Promise.of(false);
    }

    @Override
    @NotNull
    public Promise<Optional<Agent>> getAgent(@NotNull String agentId) {
        Objects.requireNonNull(agentId, "agentId is required");

        RegistryEntry entry = agents.get(agentId);
        if (entry != null && entry.metadata().enabled()) {
            return Promise.of(Optional.of(entry.agent()));
        }
        return Promise.of(Optional.empty());
    }

    @Override
    @NotNull
    public Promise<List<String>> getAgentsByRole(@NotNull WorkflowAgentRole role) {
        Objects.requireNonNull(role, "role is required");

        Set<String> agentIds = roleIndex.get(role);
        if (agentIds == null || agentIds.isEmpty()) {
            return Promise.of(List.of());
        }

        // Filter to only enabled agents
        List<String> enabledAgents = agentIds.stream()
                .filter(id -> {
                    RegistryEntry entry = agents.get(id);
                    return entry != null && entry.metadata().enabled();
                })
                .collect(Collectors.toList());

        return Promise.of(enabledAgents);
    }

    @Override
    @NotNull
    public Promise<Optional<WorkflowAgentRole>> getAgentRole(@NotNull String agentId) {
        Objects.requireNonNull(agentId, "agentId is required");

        RegistryEntry entry = agents.get(agentId);
        if (entry != null) {
            return Promise.of(Optional.of(entry.metadata().role()));
        }
        return Promise.of(Optional.empty());
    }

    @Override
    @NotNull
    public Promise<Optional<AgentMetadata>> getAgentMetadata(@NotNull String agentId) {
        Objects.requireNonNull(agentId, "agentId is required");

        RegistryEntry entry = agents.get(agentId);
        if (entry != null) {
            return Promise.of(Optional.of(entry.metadata()));
        }
        return Promise.of(Optional.empty());
    }

    @Override
    @NotNull
    public Promise<Set<String>> listAgentIds() {
        return Promise.of(Set.copyOf(agents.keySet()));
    }

    @Override
    @NotNull
    public Promise<Set<WorkflowAgentRole>> listActiveRoles() {
        Set<WorkflowAgentRole> activeRoles = roleIndex.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .filter(e -> e.getValue().stream()
                        .anyMatch(id -> {
                            RegistryEntry entry = agents.get(id);
                            return entry != null && entry.metadata().enabled();
                        }))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        return Promise.of(activeRoles);
    }

    @Override
    @NotNull
    public Promise<Boolean> isAvailable(@NotNull String agentId) {
        Objects.requireNonNull(agentId, "agentId is required");

        RegistryEntry entry = agents.get(agentId);
        return Promise.of(entry != null && entry.metadata().enabled());
    }

    @Override
    @NotNull
    public Promise<Void> setEnabled(@NotNull String agentId, boolean enabled) {
        Objects.requireNonNull(agentId, "agentId is required");

        agents.computeIfPresent(agentId, (id, entry) -> {
            AgentMetadata updated = new AgentMetadata(
                    entry.metadata().agentId(),
                    entry.metadata().role(),
                    entry.metadata().name(),
                    entry.metadata().description(),
                    enabled,
                    entry.metadata().registeredAt(),
                    entry.metadata().capabilities()
            );
            return new RegistryEntry(entry.agent(), updated);
        });

        return Promise.complete();
    }

    /**
     * Returns the count of registered agents.
     *
     * @return The agent count
     */
    public int size() {
        return agents.size();
    }

    /**
     * Clears all registered agents.
     * Primarily for testing purposes.
     */
    public void clear() {
        agents.clear();
        roleIndex.clear();
    }
}
