package com.ghatana.yappc.agent;

import io.activej.promise.Promise;
import io.activej.promise.Promises;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple synchronous in-memory agent registry for YAPPC workflow agents.
 *
 * <p>This registry is intentionally lightweight — it stores all agents locally in
 * memory without delegating to the platform {@link com.ghatana.agent.spi.AgentRegistry}.
 * It is the default implementation for unit/integration tests and single-node
 * deployments that do not require cross-product agent discovery.
 *
 * <p>For production multi-tenant deployments, prefer
 * {@link YappcAgentRegistryAdapter} which persists registrations to the platform
 * registry.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * YAPPCAgentRegistry registry = new YAPPCAgentRegistry();
 * registry.register(agent1).register(agent2);
 * runPromise(registry::initializeAll);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose In-memory synchronous YAPPC agent registry for tests and lightweight deployments
 * @doc.layer product
 * @doc.pattern Registry
 */
public class YAPPCAgentRegistry implements AgentHealthProvider, AgentRegistryView {

    private static final Logger log = LoggerFactory.getLogger(YAPPCAgentRegistry.class);

    /** Agent lifecycle status used by YAPPC heartbeat monitoring. */
    public enum AgentStatus {
        REGISTERED,
        INITIALIZING,
        READY,
        FAILED,
        STOPPING,
        STOPPED
    }

    /** Index: stepName → agent. */
    private final Map<String, YAPPCAgentBase<?, ?>> agentsByStepName = new ConcurrentHashMap<>();

    /** Index: phase → agents. */
    private final Map<String, List<YAPPCAgentBase<?, ?>>> agentsByPhase = new ConcurrentHashMap<>();

    /** Index: agentId → agent. */
    private final Map<String, YAPPCAgentBase<?, ?>> agentsById = new ConcurrentHashMap<>();

    /** Status index for heartbeat monitoring. */
    private final Map<String, AgentStatus> statusById = new ConcurrentHashMap<>();

    /**
     * Registers a workflow agent in the local indexes.
     *
     * @param agent the agent to register
     * @param <I>   input type
     * @param <O>   output type
     * @return this registry for method chaining
     */
    @NotNull
    public <I, O> YAPPCAgentRegistry register(@NotNull YAPPCAgentBase<I, O> agent) {
        String stepName = agent.stepName();
        String agentId = agent.getAgentId();

        agentsByStepName.put(stepName, agent);
        agentsById.put(agentId, agent);
        statusById.put(agentId, AgentStatus.REGISTERED);

        String phase = extractPhase(stepName);
        agentsByPhase.computeIfAbsent(phase, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(agent);

        log.debug("Registered agent: {} for step: {}", agentId, stepName);
        return this;
    }

    /** Returns the count of registered agents. */
    @Override
    public int getAgentCount() {
        return agentsByStepName.size();
    }

    /**
     * Checks if an agent is registered for the given step.
     *
     * @param stepName the step name
     * @return {@code true} if an agent is registered
     */
    public boolean hasAgent(@NotNull String stepName) {
        return agentsByStepName.containsKey(stepName);
    }

    /**
     * Gets an agent by step name.
     *
     * @param stepName the step name
     * @param <I>      input type
     * @param <O>      output type
     * @return the agent, or null if not found
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <I, O> WorkflowStep<I, O> getAgent(@NotNull String stepName) {
        YAPPCAgentBase<?, ?> agent = agentsByStepName.get(stepName);
        return agent != null ? (WorkflowStep<I, O>) agent : null;
    }

    /**
     * Gets an agent by agent ID.
     *
     * @param agentId the agent ID
     * @param <I>     input type
     * @param <O>     output type
     * @return the agent, or null if not found
     */
    @SuppressWarnings("unchecked")
    @Nullable
    public <I, O> WorkflowStep<I, O> getAgentById(@NotNull String agentId) {
        YAPPCAgentBase<?, ?> agent = agentsById.get(agentId);
        return agent != null ? (WorkflowStep<I, O>) agent : null;
    }

    /**
     * Gets all agents for a given SDLC phase.
     *
     * @param phase the SDLC phase (e.g., "architecture", "implementation")
     * @return unmodifiable list of agents, empty if none found
     */
    @Override
    @NotNull
    public List<YAPPCAgentBase<?, ?>> getAgentsByPhase(@NotNull String phase) {
        return List.copyOf(agentsByPhase.getOrDefault(phase, List.of()));
    }

    /**
     * Returns all registered step names.
     *
     * @return unmodifiable copy of all step names
     */
    @NotNull
    public Set<String> getAllStepNames() {
        return Set.copyOf(agentsByStepName.keySet());
    }

    /**
     * Returns all registered SDLC phases.
     *
     * @return unmodifiable copy of all phases
     */
    @NotNull
    public Set<String> getAllPhases() {
        return Set.copyOf(agentsByPhase.keySet());
    }

    /**
     * Initializes all registered agents and transitions them to {@link AgentStatus#READY}.
     *
     * @return Promise that completes when all agents are initialized
     */
    @NotNull
    public Promise<Void> initializeAll() {
        log.debug("Initializing {} agents", agentsById.size());
        List<Promise<Void>> promises = agentsById.values().stream()
                .map(agent -> Promise.<Void>complete()
                        .whenResult(v -> {
                            statusById.put(agent.getAgentId(), AgentStatus.READY);
                            log.debug("Agent initialized: {}", agent.stepName());
                        }))
                .collect(Collectors.toList());
        return Promises.all(promises).toVoid();
    }

    /**
     * Shuts down all registered agents and transitions them to {@link AgentStatus#STOPPED}.
     *
     * @return Promise that completes when all agents are shut down
     */
    @NotNull
    public Promise<Void> shutdownAll() {
        log.debug("Shutting down {} agents", agentsById.size());
        List<Promise<Void>> promises = agentsById.values().stream()
                .map(agent -> Promise.<Void>complete()
                        .whenResult(v -> {
                            statusById.put(agent.getAgentId(), AgentStatus.STOPPED);
                            log.debug("Agent stopped: {}", agent.stepName());
                        }))
                .collect(Collectors.toList());
        return Promises.all(promises).toVoid();
    }

    /**
     * Returns a snapshot of agent health status keyed by agent ID.
     *
     * @return unmodifiable map from agent ID to status
     */
    @Override
    @NotNull
    public Map<String, AgentStatus> getHealthStatus() {
        return Map.copyOf(statusById);
    }

    private String extractPhase(@NotNull String stepName) {
        int dotIndex = stepName.indexOf('.');
        return dotIndex > 0 ? stepName.substring(0, dotIndex) : stepName;
    }
}
