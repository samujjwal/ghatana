package com.ghatana.yappc.agent.leads;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.yappc.agent.StepResult;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.YAPPCAgentRegistry;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generator for architecture phase lead orchestration.
 *
 * <p>Orchestrates architecture steps based on:
 *
 * <ul>
 *   <li>Step dependencies (intake → design → validation)
 *   <li>Available specialist agents
 *   <li>Quality gates
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Orchestration logic for architecture phase
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class ArchitecturePhaseGenerator
    implements OutputGenerator<StepRequest<ArchitectureRequest>, StepResult<ArchitectureResult>> {

  private static final Logger log = LoggerFactory.getLogger(ArchitecturePhaseGenerator.class);

  private final YAPPCAgentRegistry agentRegistry;

  // Architecture step execution order
  private static final List<String> STEP_ORDER =
      List.of(
          "architecture.intake",
          "architecture.design",
          "architecture.deriveContracts",
          "architecture.deriveDataModels",
          "architecture.validate",
          "architecture.hitlReview");

  public ArchitecturePhaseGenerator(@NotNull YAPPCAgentRegistry agentRegistry) {
    this.agentRegistry = agentRegistry;
  }

  @Override
  public @NotNull Promise<StepResult<ArchitectureResult>> generate(
      @NotNull StepRequest<ArchitectureRequest> input, @NotNull AgentContext context) {

    Instant startTime = Instant.now();
    ArchitectureRequest request = input.input();

    log.info("Generating architecture plan for: {}", request.requirements());

    // Determine execution order
    List<String> executionOrder = determineExecutionOrder(request.targetSteps());

    // Execute steps in order
    Map<String, ArchitectureResult.StepResult> stepResults = new LinkedHashMap<>();

    for (String step : executionOrder) {
      log.info("Executing architecture step: {}", step);

      long stepStart = System.currentTimeMillis();

      // Check for agent availability
      var agent = agentRegistry.getAgent(step);
      if (agent == null) {
        log.warn("No agent registered for step: {}", step);
        stepResults.put(
            step,
            new ArchitectureResult.StepResult(
                step,
                "SKIPPED",
                System.currentTimeMillis() - stepStart,
                false,
                Map.of("reason", "No agent registered")));
        continue;
      }

      // Simulate step execution (delegate to specialist)
      long stepDuration = System.currentTimeMillis() - stepStart;
      boolean needsReview = step.contains("hitl") || step.contains("review");

      stepResults.put(
          step,
          new ArchitectureResult.StepResult(
              step,
              "SUCCESS",
              stepDuration,
              needsReview,
              Map.of("agent", agent.stepName(), "requirements", request.requirements())));

      log.info("Step {} completed in {}ms", step, stepDuration);
    }

    long totalDuration = System.currentTimeMillis() - startTime.toEpochMilli();
    boolean allSuccessful =
        stepResults.values().stream().allMatch(sr -> "SUCCESS".equals(sr.status()));

    ArchitectureResult result =
        new ArchitectureResult(
            stepResults,
            totalDuration,
            allSuccessful,
            Map.of(
                "phase", "architecture",
                "totalSteps", executionOrder.size(),
                "needsReview",
                    stepResults.values().stream()
                        .anyMatch(ArchitectureResult.StepResult::needsReview)));

    log.info(
        "Architecture plan generated: {} steps, {}ms, success: {}",
        stepResults.size(),
        totalDuration,
        allSuccessful);

    return Promise.of(
        StepResult.success(
            result, Map.of("generationTimeMs", totalDuration), startTime, Instant.now()));
  }

  @Override
  public @NotNull Promise<Double> estimateCost(
      @NotNull StepRequest<ArchitectureRequest> input, @NotNull AgentContext context) {
    // Architecture coordination is rule-based
    return Promise.of(0.0);
  }

  @Override
  public @NotNull GeneratorMetadata getMetadata() {
    return GeneratorMetadata.builder()
        .name("ArchitecturePhaseGenerator")
        .type("rule-based")
        .description("Orchestrates architecture workflow steps")
        .version("1.0.0")
        .property("phase", "architecture")
        .property("stepOrder", String.join(", ", STEP_ORDER))
        .build();
  }

  private List<String> determineExecutionOrder(List<String> targetSteps) {
    return STEP_ORDER.stream().filter(targetSteps::contains).toList();
  }
}
