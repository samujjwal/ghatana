package com.ghatana.products.yappc.domain.agent;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * YAPPC runtime registry for typed {@link AIAgent} instances.
 *
 * <p>Provides agent discovery, lifecycle management, health monitoring,
 * and capability-based routing for the YAPPC AI agent ecosystem.
 *
 * <p><b>Relationship to platform AgentRegistry</b><br>
 * This class is intentionally distinct from
 * {@code com.ghatana.agent.registry.AgentRegistry} (platform agent-registry module).
 * The platform SPI is a persistent, distributed registry backed by Data-Cloud,
 * designed for agent-framework {@code TypedAgent} instances at the platform level.
 * This class is a lightweight, in-memory runtime registry scoped to YAPPC's
 * domain-typed {@code AIAgent<TIn,TOut>} instances, providing YAPPC-specific
 * features like capability scoring, health-check aggregation, and listener callbacks.
 *
 * <p>The two registries may coexist: YAPPC can register agents here for local
 * routing while also publishing to the platform registry for cross-product discovery.
 *
 * @doc.type class
 * @doc.purpose YAPPC in-memory typed agent registry (distinct from platform AgentRegistry SPI)
 * @doc.layer product
 * @doc.pattern Registry
 */
public class AgentRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(AgentRegistry.class);

    private final Map<AgentName, RegisteredAgent<?>> agents;
    private final MetricsCollector metricsCollector;
    private final List<RegistryListener> listeners;

    /**
     * Creates a new AgentRegistry.
     *
     * @param metricsCollector The metrics collector
     */
    public AgentRegistry(@NotNull MetricsCollector metricsCollector) {
        this.agents = new ConcurrentHashMap<>();
        this.metricsCollector = metricsCollector;
        this.listeners = new ArrayList<>();
    }

    /**
     * Registers an AI agent.
     *
     * @param agent The agent to register
     * @param <TIn> Input type
     * @param <TOut> Output type
     * @return Registration result
     */
    public <TIn, TOut> RegistrationResult register(@NotNull AIAgent<TIn, TOut> agent) {
        AgentMetadata metadata = agent.getMetadata();
        AgentName name = metadata.name();

        if (agents.containsKey(name)) {
            LOG.warn("Agent {} already registered, replacing", name.getDisplayName());
        }

        RegisteredAgent<AIAgent<TIn, TOut>> registered = new RegisteredAgent<>(
                agent,
                metadata,
                Instant.now(),
                AgentState.INITIALIZING
        );

        agents.put(name, registered);

        LOG.info("Registered agent: {} v{}", name.getDisplayName(), metadata.version());
        metricsCollector.incrementCounter("registry.agents.registered", "agent", name.name());

        // Notify listeners
        for (RegistryListener listener : listeners) {
            try {
                listener.onAgentRegistered(name, metadata);
            } catch (Exception e) {
                LOG.error("Listener error on registration: {}", e.getMessage());
            }
        }

        // Update state to READY
        updateState(name, AgentState.READY);

        return new RegistrationResult(true, name, metadata.version(), null);
    }

    /**
     * Unregisters an AI agent.
     *
     * @param name The agent name
     * @return true if unregistered
     */
    public boolean unregister(@NotNull AgentName name) {
        RegisteredAgent<?> removed = agents.remove(name);

        if (removed == null) {
            return false;
        }

        LOG.info("Unregistered agent: {}", name.getDisplayName());
        metricsCollector.incrementCounter("registry.agents.unregistered", "agent", name.name());

        // Notify listeners
        for (RegistryListener listener : listeners) {
            try {
                listener.onAgentUnregistered(name);
            } catch (Exception e) {
                LOG.error("Listener error on unregistration: {}", e.getMessage());
            }
        }

        return true;
    }

    /**
     * Gets an agent by name.
     *
     * @param name The agent name
     * @param <TIn> Input type
     * @param <TOut> Output type
     * @return The agent, or null if not found
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <TIn, TOut> AIAgent<TIn, TOut> get(@NotNull AgentName name) {
        RegisteredAgent<?> registered = agents.get(name);
        return registered != null ? (AIAgent<TIn, TOut>) registered.agent() : null;
    }

    /**
     * Checks if an agent is registered.
     *
     * @param name The agent name
     * @return true if registered
     */
    public boolean isRegistered(@NotNull AgentName name) {
        return agents.containsKey(name);
    }

    /**
     * Gets all registered agent names.
     *
     * @return Set of registered agent names
     */
    public Set<AgentName> getRegisteredNames() {
        return Collections.unmodifiableSet(agents.keySet());
    }

    /**
     * Gets all registered agents.
     *
     * @return List of registered agent info
     */
    public List<AgentInfo> getAll() {
        return agents.values().stream()
                .map(r -> new AgentInfo(
                        r.metadata().name(),
                        r.metadata().version(),
                        r.metadata().description(),
                        r.state(),
                        r.registeredAt(),
                        r.metadata().capabilities()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Gets all registered agent metadata.
     *
     * <p>Unlike {@link #getAll()}, this returns full metadata (including SLA/cost) which is
     * needed for capability-based scoring and selection.
     */
    public List<AgentMetadata> getAllMetadata() {
        return agents.values().stream()
                .map(RegisteredAgent::metadata)
                .toList();
    }

    /**
     * Gets metadata for a registered agent.
     */
    public Optional<AgentMetadata> getMetadata(@NotNull AgentName name) {
        RegisteredAgent<?> registered = agents.get(name);
        return registered == null ? Optional.empty() : Optional.of(registered.metadata());
    }

    /**
     * Finds agents by capability.
     *
     * @param capability The required capability
     * @return List of agents with the capability
     */
    public List<AgentInfo> findByCapability(@NotNull String capability) {
        return agents.values().stream()
                .filter(r -> r.metadata().capabilities().contains(capability))
                .map(r -> new AgentInfo(
                        r.metadata().name(),
                        r.metadata().version(),
                        r.metadata().description(),
                        r.state(),
                        r.registeredAt(),
                        r.metadata().capabilities()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Updates the state of an agent.
     *
     * @param name The agent name
     * @param state The new state
     */
    public void updateState(@NotNull AgentName name, @NotNull AgentState state) {
        RegisteredAgent<?> registered = agents.get(name);

        if (registered != null) {
            agents.put(name, new RegisteredAgent<>(
                    registered.agent(),
                    registered.metadata(),
                    registered.registeredAt(),
                    state
            ));

            metricsCollector.incrementCounter("registry.agents.state_change",
                    "agent", name.name(),
                    "state", state.name());

            // Notify listeners
            for (RegistryListener listener : listeners) {
                try {
                    listener.onAgentStateChanged(name, state);
                } catch (Exception e) {
                    LOG.error("Listener error on state change: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * Performs health checks on all agents.
     *
     * @return Promise with health status map
     */
    public Promise<Map<AgentName, AgentHealth>> healthCheckAll() {
        List<Promise<Map.Entry<AgentName, AgentHealth>>> healthPromises = new ArrayList<>();

        for (Map.Entry<AgentName, RegisteredAgent<?>> entry : agents.entrySet()) {
            Promise<Map.Entry<AgentName, AgentHealth>> healthPromise = entry.getValue()
                    .agent()
                    .healthCheck()
                    .map(health -> Map.entry(entry.getKey(), health))
                    .mapException(e -> {
                        LOG.error("Health check failed for {}: {}", entry.getKey(), e.getMessage());
                        return e;
                    });

            healthPromises.add(healthPromise);
        }

        return Promises.toList(healthPromises)
                .map(entries -> {
                    Map<AgentName, AgentHealth> result = new HashMap<>();
                    for (Map.Entry<AgentName, AgentHealth> entry : entries) {
                        result.put(entry.getKey(), entry.getValue());

                        // Update state based on health
                        AgentState newState = entry.getValue().healthy()
                                ? AgentState.READY
                                : AgentState.UNHEALTHY;
                        updateState(entry.getKey(), newState);
                    }
                    return result;
                });
    }

    /**
     * Gets registry statistics.
     *
     * @return Registry statistics
     */
    public RegistryStats getStats() {
        int total = agents.size();
        int ready = (int) agents.values().stream()
                .filter(r -> r.state() == AgentState.READY)
                .count();
        int unhealthy = (int) agents.values().stream()
                .filter(r -> r.state() == AgentState.UNHEALTHY)
                .count();

        Map<String, Integer> byCapability = new HashMap<>();
        for (RegisteredAgent<?> agent : agents.values()) {
            for (String capability : agent.metadata().capabilities()) {
                byCapability.merge(capability, 1, Integer::sum);
            }
        }

        return new RegistryStats(total, ready, unhealthy, byCapability);
    }

    /**
     * Adds a registry listener.
     *
     * @param listener The listener to add
     */
    public void addListener(@NotNull RegistryListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a registry listener.
     *
     * @param listener The listener to remove
     */
    public void removeListener(@NotNull RegistryListener listener) {
        listeners.remove(listener);
    }

    /**
     * Shuts down all agents.
     *
     * @return Promise that completes when all agents are shut down
     */
    public Promise<Void> shutdownAll() {
        LOG.info("Shutting down all agents...");

        for (AgentName name : agents.keySet()) {
            updateState(name, AgentState.SHUTTING_DOWN);
        }

        // In a real implementation, would call shutdown on each agent
        agents.clear();

        LOG.info("All agents shut down");
        return Promise.complete();
    }

    // Data structures

    /**
     * Wrapper for a registered agent.
     */
    private record RegisteredAgent<T extends AIAgent<?, ?>>(
            @NotNull T agent,
            @NotNull AgentMetadata metadata,
            @NotNull Instant registeredAt,
            @NotNull AgentState state
    ) {}

    /**
     * Agent states.
     */
    public enum AgentState {
        INITIALIZING,
        READY,
        BUSY,
        UNHEALTHY,
        SHUTTING_DOWN
    }

    /**
     * Public agent info.
     */
    public record AgentInfo(
            @NotNull AgentName name,
            @NotNull String version,
            @NotNull String description,
            @NotNull AgentState state,
            @NotNull Instant registeredAt,
            @NotNull List<String> capabilities
    ) {}

    /**
     * Registration result.
     */
    public record RegistrationResult(
            boolean success,
            @NotNull AgentName name,
            @NotNull String version,
            @Nullable String errorMessage
    ) {}

    /**
     * Registry statistics.
     */
    public record RegistryStats(
            int totalAgents,
            int readyAgents,
            int unhealthyAgents,
            @NotNull Map<String, Integer> agentsByCapability
    ) {}

    /**
     * Registry event listener.
     */
    public interface RegistryListener {
        void onAgentRegistered(AgentName name, AgentMetadata metadata);
        void onAgentUnregistered(AgentName name);
        void onAgentStateChanged(AgentName name, AgentState state);
    }
}
