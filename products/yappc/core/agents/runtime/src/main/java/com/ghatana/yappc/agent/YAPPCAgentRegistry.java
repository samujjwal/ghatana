package com.ghatana.yappc.agent;

import com.ghatana.yappc.agent.WorkflowStep;
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
 * Registry for all YAPPC workflow agents.
 *
 * <p>Provides:
 *
 * <ul>
 *   <li>Agent registration and discovery
 *   <li>Lifecycle management (initialization, shutdown)
 *   <li>Query by step name, phase, capabilities
 *   <li>Health checking and status monitoring
 * </ul>
 *
 * <p>Thread-safe for concurrent access.
 *
 * @deprecated Use {@link YappcAgentRegistryAdapter} which delegates to the platform
 *     {@link com.ghatana.agent.registry.AgentRegistry} for persistent, cross-product
 *     agent storage. This in-memory implementation loses state on restart.
 * @doc.type class
 * @doc.purpose Central registry for YAPPC workflow agents (deprecated - use YappcAgentRegistryAdapter)
 * @doc.layer product
 * @doc.pattern Registry
 */
@Deprecated(since = "2.4.0", forRemoval = true)
public class YAPPCAgentRegistry {

  private static final Logger log = LoggerFactory.getLogger(YAPPCAgentRegistry.class);

  private final Map<String, AgentRegistration> agentsByStepName = new ConcurrentHashMap<>();
  private final Map<String, List<AgentRegistration>> agentsByPhase = new ConcurrentHashMap<>();
  private final Map<String, AgentRegistration> agentsById = new ConcurrentHashMap<>();

  /**
   * Registers a workflow agent.
   *
   * @param agent the agent to register
   * @param <I> input type
   * @param <O> output type
   * @return this registry for chaining
   */
  public <I, O> YAPPCAgentRegistry register(@NotNull YAPPCAgentBase<I, O> agent) {
    String stepName = agent.stepName();
    String agentId = agent.getAgentId();

    log.info("Registering agent: {} for step: {}", agentId, stepName);

    AgentRegistration registration = new AgentRegistration(agent);

    agentsByStepName.put(stepName, registration);
    agentsById.put(agentId, registration);

    // Extract phase from step name (e.g., "architecture.intake" -> "architecture")
    String phase = extractPhase(stepName);
    agentsByPhase.computeIfAbsent(phase, k -> new ArrayList<>()).add(registration);

    log.info("Agent registered successfully: {} (step: {}, phase: {})", agentId, stepName, phase);
    return this;
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
    AgentRegistration registration = agentsByStepName.get(stepName);
    return registration != null ? (WorkflowStep<I, O>) registration.agent : null;
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
    AgentRegistration registration = agentsById.get(agentId);
    return registration != null ? (WorkflowStep<I, O>) registration.agent : null;
  }

  /**
   * Gets all agents for a given phase.
   *
   * @param phase the SDLC phase (e.g., "architecture", "implementation")
   * @return list of agents, empty if none found
   */
  @NotNull
  public List<YAPPCAgentBase<?, ?>> getAgentsByPhase(@NotNull String phase) {
    return agentsByPhase.getOrDefault(phase, List.of()).stream()
        .map(reg -> reg.agent)
        .collect(Collectors.toList());
  }

  /**
   * Gets all registered step names.
   *
   * @return set of step names
   */
  @NotNull
  public Set<String> getAllStepNames() {
    return new HashSet<>(agentsByStepName.keySet());
  }

  /**
   * Gets all registered phases.
   *
   * @return set of phases
   */
  @NotNull
  public Set<String> getAllPhases() {
    return new HashSet<>(agentsByPhase.keySet());
  }

  /**
   * Checks if an agent is registered for the given step.
   *
   * @param stepName the step name
   * @return true if registered
   */
  public boolean hasAgent(@NotNull String stepName) {
    return agentsByStepName.containsKey(stepName);
  }

  /**
   * Gets the count of registered agents.
   *
   * @return agent count
   */
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
    log.info("Initializing {} agents", agentsByStepName.size());

    List<Promise<Void>> promises =
        agentsById.values().stream()
            .map(
                registration -> {
                  registration.status = AgentStatus.INITIALIZING;
                  return Promise.complete()
                      .whenComplete(
                          (v, e) -> {
                            if (e == null) {
                              registration.status = AgentStatus.READY;
                              log.info("Agent initialized: {}", registration.agent.stepName());
                            } else {
                              registration.status = AgentStatus.FAILED;
                              log.error(
                                  "Agent initialization failed: {}",
                                  registration.agent.stepName(),
                                  e);
                            }
                          });
                })
            .collect(Collectors.toList());

    return Promises.all(promises).toVoid();
  }

  /**
   * Shuts down all registered agents.
   *
   * @return Promise that completes when all agents are shut down
   */
  @NotNull
  public Promise<Void> shutdownAll() {
    log.info("Shutting down {} agents", agentsByStepName.size());

    List<Promise<Void>> promises =
        agentsById.values().stream()
            .map(
                registration -> {
                  registration.status = AgentStatus.STOPPING;
                  return Promise.complete()
                      .whenComplete(
                          (v, e) -> {
                            registration.status = AgentStatus.STOPPED;
                            if (e == null) {
                              log.info("Agent stopped: {}", registration.agent.stepName());
                            } else {
                              log.error(
                                  "Agent shutdown failed: {}", registration.agent.stepName(), e);
                            }
                          });
                })
            .collect(Collectors.toList());

    return Promises.all(promises).toVoid();
  }

  /**
   * Gets health status of all agents.
   *
   * @return map of agent ID to status
   */
  @NotNull
  public Map<String, AgentStatus> getHealthStatus() {
    return agentsById.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().status));
  }

  /** Extracts phase from step name. */
  private String extractPhase(@NotNull String stepName) {
    int dotIndex = stepName.indexOf('.');
    return dotIndex > 0 ? stepName.substring(0, dotIndex) : stepName;
  }

  /** Registration metadata for an agent. */
  private static class AgentRegistration {
    final YAPPCAgentBase<?, ?> agent;
    volatile AgentStatus status;
    final long registeredAt;

    AgentRegistration(YAPPCAgentBase<?, ?> agent) {
      this.agent = agent;
      this.status = AgentStatus.REGISTERED;
      this.registeredAt = System.currentTimeMillis();
    }
  }

  /** Agent lifecycle status. */
  public enum AgentStatus {
    REGISTERED,
    INITIALIZING,
    READY,
    STOPPING,
    STOPPED,
    FAILED
  }
}
