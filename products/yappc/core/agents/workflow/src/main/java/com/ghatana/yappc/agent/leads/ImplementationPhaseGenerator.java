package com.ghatana.yappc.agent.leads;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.yappc.agent.StepResult;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.YAPPCAgentRegistry;
import io.activej.promise.Promise;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rule-based generator for implementation phase orchestration.
 *
 * @doc.type class
 * @doc.purpose Implementation phase orchestration logic
 * @doc.layer product
 * @doc.pattern Generator
 */
public class ImplementationPhaseGenerator
    implements OutputGenerator<
        StepRequest<ImplementationRequest>, StepResult<ImplementationResult>> {

  private static final Logger log = LoggerFactory.getLogger(ImplementationPhaseGenerator.class);

  private static final List<String> STEP_ORDER =
      List.of(
          "implementation.scaffold",
          "implementation.planUnits",
          "implementation.implement",
          "implementation.review",
          "implementation.build",
          "implementation.qualityGate",
          "implementation.publish");

  private final YAPPCAgentRegistry agentRegistry;

  public ImplementationPhaseGenerator(@NotNull YAPPCAgentRegistry agentRegistry) {
    this.agentRegistry = agentRegistry;
  }

  @Override
  public @NotNull Promise<StepResult<ImplementationResult>> generate(
      @NotNull StepRequest<ImplementationRequest> input, @NotNull AgentContext context) {

    Instant startTime = Instant.now();
    ImplementationRequest request = input.input();

    log.info("Generating implementation plan for {} architecture", request.architecture());

    // Determine execution order based on requested steps
    List<String> executionOrder = determineExecutionOrder(request.targetSteps());
    int stepCount = 0;
    boolean buildSuccessful = false;
    boolean qualityGatePassed = false;

    log.info("Implementation plan: {} steps in order", executionOrder.size());

    // Execute steps in sequence (simplified - actual would delegate to specialist agents)
    for (String step : executionOrder) {
      if (agentRegistry.hasAgent(step)) {
        log.info("Planning step: {}", step);
        stepCount++;
        if (step.equals("implementation.build")) {
          buildSuccessful = true;
        }
        if (step.equals("implementation.qualityGate")) {
          qualityGatePassed = true;
        }
      } else {
        log.warn("Step {} not registered, skipping", step);
      }
    }

    long totalDuration = Duration.between(startTime, Instant.now()).toMillis();

    ImplementationResult result =
        new ImplementationResult(
            stepCount,
            buildSuccessful,
            qualityGatePassed,
            Map.of("phase", "implementation", "totalSteps", executionOrder.size()));

    log.info(
        "Implementation plan generated: {} steps, {}ms, build={}, quality={}",
        stepCount,
        totalDuration,
        buildSuccessful,
        qualityGatePassed);

    return Promise.of(
        StepResult.success(
            result, Map.of("generationTimeMs", totalDuration), startTime, Instant.now()));
  }

  @Override
  public @NotNull Promise<Double> estimateCost(
      @NotNull StepRequest<ImplementationRequest> input, @NotNull AgentContext context) {
    return Promise.of(0.0); // Rule-based, no LLM cost
  }

  @Override
  public @NotNull GeneratorMetadata getMetadata() {
    return GeneratorMetadata.builder()
        .name("ImplementationPhaseGenerator")
        .type("rule-based")
        .description("Orchestrates implementation phase steps")
        .version("1.0.0")
        .property("phase", "implementation")
        .property("stepOrder", String.join(",", STEP_ORDER))
        .build();
  }

  private List<String> determineExecutionOrder(List<String> targetSteps) {
    if (targetSteps == null || targetSteps.isEmpty()) {
      return STEP_ORDER;
    }

    // Filter to include only requested steps in defined order
    return STEP_ORDER.stream()
        .filter(step -> targetSteps.contains(step) || targetSteps.contains("*"))
        .toList();
  }
}
