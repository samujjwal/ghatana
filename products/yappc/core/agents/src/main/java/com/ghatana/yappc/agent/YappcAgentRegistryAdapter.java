package com.ghatana.yappc.agent;

import com.ghatana.agent.AgentConfig;
import com.ghatana.agent.AgentType;
import com.ghatana.agent.migration.BaseAgentAdapter;
import com.ghatana.agent.registry.AgentRegistry;
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
 * YAPPC Agent Registry backed by the platform {@link AgentRegistry}.
 *
 * <p>Delegates persistent storage and cross-product discovery to the platform
 * registry while maintaining YAPPC-specific indexes for fast lookup by step
 * name and SDLC phase.
 *
 * @doc.type class
 * @doc.purpose YAPPC adapter over platform AgentRegistry for persistent agent storage
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class YappcAgentRegistryAdapter {

  private static final Logger log = LoggerFactory.getLogger(YappcAgentRegistryAdapter.class);

  private final AgentRegistry platformRegistry;

  /** YAPPC-specific local index: stepName → agent. */
  private final Map<String, YAPPCAgentBase<?, ?>> agentsByStepName = new ConcurrentHashMap<>();

  /** YAPPC-specific local index: phase → agents. */
  private final Map<String, List<YAPPCAgentBase<?, ?>>> agentsByPhase = new ConcurrentHashMap<>();

  /** YAPPC-specific local index: agentId → agent. */
  private final Map<String, YAPPCAgentBase<?, ?>> agentsById = new ConcurrentHashMap<>();

  /**
   * Creates the adapter.
   *
   * @param platformRegistry the platform agent registry for persistent storage
   */
  public YappcAgentRegistryAdapter(@NotNull AgentRegistry platformRegistry) {
    this.platformRegistry = Objects.requireNonNull(platformRegistry, "platformRegistry");
  }

  /**
   * Registers a workflow agent in both the platform registry and local YAPPC indexes.
   *
   * @param agent the agent to register
   * @param <I> input type
   * @param <O> output type
   * @return Promise completing when registration is persisted
   */
  public <I, O> Promise<Void> register(@NotNull YAPPCAgentBase<I, O> agent) {
    String stepName = agent.stepName();
    String agentId = agent.getAgentId();

    log.info("Registering agent: {} for step: {}", agentId, stepName);

    // Update local YAPPC indexes
    agentsByStepName.put(stepName, agent);
    agentsById.put(agentId, agent);

    String phase = extractPhase(stepName);
    agentsByPhase.computeIfAbsent(phase, k -> Collections.synchronizedList(new ArrayList<>()))
        .add(agent);

    // Delegate to platform registry for persistent storage (wrap as TypedAgent)
    return platformRegistry.register(new BaseAgentAdapter<>(agent), buildAgentConfig(agent))
        .whenResult(v -> log.info("Agent registered: {} (step: {}, phase: {})", agentId, stepName, phase))
        .whenException(e -> log.error("Platform registry failed for agent {}: {}", agentId, e.getMessage()));
  }

  /**
   * Gets an agent by step name.
   *
   * @param stepName the step name
   * @param <I> input type
   * @param <O> output type
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
   * @param <I> input type
   * @param <O> output type
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
   * @return list of agents, empty if none found
   */
  @NotNull
  public List<YAPPCAgentBase<?, ?>> getAgentsByPhase(@NotNull String phase) {
    return List.copyOf(agentsByPhase.getOrDefault(phase, List.of()));
  }

  /** Returns all registered step names. */
  @NotNull
  public Set<String> getAllStepNames() {
    return Set.copyOf(agentsByStepName.keySet());
  }

  /** Returns all registered SDLC phases. */
  @NotNull
  public Set<String> getAllPhases() {
    return Set.copyOf(agentsByPhase.keySet());
  }

  /** Checks if an agent is registered for the given step. */
  public boolean hasAgent(@NotNull String stepName) {
    return agentsByStepName.containsKey(stepName);
  }

  /** Returns the count of registered agents. */
  public int getAgentCount() {
    return agentsByStepName.size();
  }

  /**
   * Initializes all registered agents.
   *
   * @return Promise that completes when all agents are initialized
   */
  @NotNull
  public Promise<Void> initializeAll() {
    log.info("Initializing {} agents", agentsById.size());
    List<Promise<Void>> promises = agentsById.values().stream()
        .map(agent -> Promise.<Void>complete()
            .whenResult(v -> log.info("Agent initialized: {}", agent.stepName()))
            .whenException(e -> log.error("Agent initialization failed: {}", agent.stepName(), e)))
        .collect(Collectors.toList());
    return Promises.all(promises).toVoid();
  }

  /**
   * Shuts down all registered agents and deregisters from platform.
   *
   * @return Promise that completes when all agents are shut down
   */
  @NotNull
  public Promise<Void> shutdownAll() {
    log.info("Shutting down {} agents", agentsById.size());
    List<Promise<Void>> promises = agentsById.entrySet().stream()
        .map(entry -> platformRegistry.deregister(entry.getKey())
            .whenResult(v -> log.info("Agent stopped: {}", entry.getValue().stepName()))
            .whenException(e -> log.error("Agent shutdown failed: {}", entry.getValue().stepName(), e)))
        .collect(Collectors.toList());
    return Promises.all(promises).toVoid()
        .whenResult(v -> {
          agentsByStepName.clear();
          agentsByPhase.clear();
          agentsById.clear();
        });
  }

  /** Returns the underlying platform registry for advanced queries. */
  @NotNull
  public AgentRegistry getPlatformRegistry() {
    return platformRegistry;
  }

  private String extractPhase(@NotNull String stepName) {
    int dotIndex = stepName.indexOf('.');
    return dotIndex > 0 ? stepName.substring(0, dotIndex) : stepName;
  }

  private AgentConfig buildAgentConfig(YAPPCAgentBase<?, ?> agent) {
    return AgentConfig.builder()
        .agentId(agent.getAgentId())
        .type(AgentType.DETERMINISTIC)
        .build();
  }
}
