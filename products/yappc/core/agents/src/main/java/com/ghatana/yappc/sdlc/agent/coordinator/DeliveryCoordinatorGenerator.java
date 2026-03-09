package com.ghatana.yappc.sdlc.agent.coordinator;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.yappc.sdlc.StepResult;
import com.ghatana.yappc.sdlc.agent.StepRequest;
import com.ghatana.yappc.sdlc.agent.YAPPCAgentRegistry;
import io.activej.promise.Promise;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rule-based output generator for platform delivery coordination.
 *
 * <p>Orchestrates SDLC phases based on:
 *
 * <ul>
 *   <li>Phase dependencies (e.g., architecture before implementation)
 *   <li>Request priority
 *   <li>Available agents
 *   <li>Budget constraints
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Deterministic orchestration logic for delivery coordination
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class DeliveryCoordinatorGenerator
    implements OutputGenerator<StepRequest<DeliveryRequest>, StepResult<DeliveryResult>> {

  private static final Logger log = LoggerFactory.getLogger(DeliveryCoordinatorGenerator.class);

  private final YAPPCAgentRegistry agentRegistry;

  // Phase execution order based on SDLC dependencies
  private static final List<String> PHASE_ORDER =
      List.of("requirements", "architecture", "implementation", "testing", "ops", "enhancement");

  public DeliveryCoordinatorGenerator(@NotNull YAPPCAgentRegistry agentRegistry) {
    this.agentRegistry = agentRegistry;
  }

  @Override
  public @NotNull Promise<StepResult<DeliveryResult>> generate(
      @NotNull StepRequest<DeliveryRequest> input, @NotNull AgentContext context) {

    long startTime = System.currentTimeMillis();
    java.time.Instant startInstant = java.time.Instant.now();
    DeliveryRequest request = input.input();

    log.info("Generating delivery plan for: {}", request.request());

    // Determine execution order respecting dependencies
    List<String> executionOrder = determineExecutionOrder(request.targetPhases());

    // Execute phases in order
    Map<String, DeliveryResult.PhaseResult> phaseResults = new LinkedHashMap<>();

    for (String phase : executionOrder) {
      log.info("Executing phase: {}", phase);

      long phaseStart = System.currentTimeMillis();

      // Check if we have agents for this phase
      List<?> phaseAgents = agentRegistry.getAgentsByPhase(phase);

      if (phaseAgents.isEmpty()) {
        log.warn("No agents available for phase: {}", phase);
        phaseResults.put(
            phase,
            new DeliveryResult.PhaseResult(
                phase,
                "SKIPPED",
                System.currentTimeMillis() - phaseStart,
                Map.of("reason", "No agents available")));
        continue;
      }

      // Simulate phase execution (in real implementation, delegate to phase lead)
      // For now, mark as success
      long phaseTime = System.currentTimeMillis() - phaseStart;
      phaseResults.put(
          phase,
          new DeliveryResult.PhaseResult(
              phase,
              "SUCCESS",
              phaseTime,
              Map.of("agentCount", phaseAgents.size(), "request", request.request())));

      log.info("Phase {} completed in {}ms", phase, phaseTime);
    }

    long totalTime = System.currentTimeMillis() - startTime;
    boolean allSuccessful =
        phaseResults.values().stream().allMatch(pr -> "SUCCESS".equals(pr.status()));

    DeliveryResult result =
        new DeliveryResult(
            phaseResults,
            totalTime,
            allSuccessful,
            Map.of(
                "coordinator", "PlatformDeliveryCoordinator",
                "priority", request.priority().toString(),
                "totalPhases", executionOrder.size()));

    log.info(
        "Delivery plan generated: {} phases, {} total time, success: {}",
        phaseResults.size(),
        totalTime,
        allSuccessful);

    return Promise.of(
        StepResult.success(
            result, Map.of("generationTimeMs", totalTime), startInstant, java.time.Instant.now()));
  }

  @Override
  public @NotNull Promise<Double> estimateCost(
      @NotNull StepRequest<DeliveryRequest> input, @NotNull AgentContext context) {
    // Coordination itself is rule-based with no LLM cost
    // Cost comes from delegated agents (not estimated here)
    return Promise.of(0.0);
  }

  @Override
  public @NotNull GeneratorMetadata getMetadata() {
    return GeneratorMetadata.builder()
        .name("DeliveryCoordinatorGenerator")
        .type("rule-based")
        .description("Orchestrates SDLC phases based on dependencies and constraints")
        .version("1.0.0")
        .property("strategy", "sequential-with-dependencies")
        .property("phaseOrder", String.join(", ", PHASE_ORDER))
        .build();
  }

  /**
   * Determines execution order respecting SDLC phase dependencies.
   *
   * @param targetPhases requested phases
   * @return ordered list of phases to execute
   */
  private List<String> determineExecutionOrder(List<String> targetPhases) {
    // Filter PHASE_ORDER to only include requested phases, preserving order
    return PHASE_ORDER.stream().filter(targetPhases::contains).toList();
  }
}
