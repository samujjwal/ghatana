package com.ghatana.yappc.agent.specialists;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.yappc.agent.*;
import com.ghatana.yappc.agent.StepRequest;
import com.ghatana.yappc.agent.YAPPCAgentBase;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Expert performance optimizer for profiling and optimization recommendations.
 *
 * @doc.type class
 * @doc.purpose Expert performance optimizer for profiling and optimization recommendations
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class PerformanceOptimizerAgent extends YAPPCAgentBase<PerformanceOptimizerInput, PerformanceOptimizerOutput> {

  private static final Logger log = LoggerFactory.getLogger(PerformanceOptimizerAgent.class);

  private final MemoryStore memoryStore;

  public PerformanceOptimizerAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<PerformanceOptimizerInput>, StepResult<PerformanceOptimizerOutput>> generator) {
    super(
        "PerformanceOptimizerAgent",
        "expert.performance-optimizer",
        new StepContract(
            "expert.performance-optimizer",
            "#/definitions/PerformanceOptimizerInput",
            "#/definitions/PerformanceOptimizerOutput",
            List.of("performance", "optimization", "profiling"),
            Map.of("description", "Expert performance optimizer for profiling and optimization recommendations", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull PerformanceOptimizerInput input) {
    if (input.serviceId() == null || input.serviceId().isEmpty()) {
      return ValidationResult.fail("serviceId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<PerformanceOptimizerInput> perceive(
      @NotNull StepRequest<PerformanceOptimizerInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.performance-optimizer request: {}", request.input().serviceId().substring(0, Math.min(50, request.input().serviceId().length())));
    return request;
  }

  /** Rule-based generator for expert.performance-optimizer. */
  public static class PerformanceOptimizerGenerator
      implements OutputGenerator<StepRequest<PerformanceOptimizerInput>, StepResult<PerformanceOptimizerOutput>> {

    private static final Logger log = LoggerFactory.getLogger(PerformanceOptimizerGenerator.class);

    @Override
    public @NotNull Promise<StepResult<PerformanceOptimizerOutput>> generate(
        @NotNull StepRequest<PerformanceOptimizerInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      PerformanceOptimizerInput stepInput = input.input();

      log.info("Executing expert.performance-optimizer for: {}", stepInput.serviceId());

      PerformanceOptimizerOutput output =
          new PerformanceOptimizerOutput(
              "expert.performance-optimizer-" + UUID.randomUUID(),
              List.of(),
              Map.of("generatedAt", start.toString()),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.performance-optimizer", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<PerformanceOptimizerInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("PerformanceOptimizerGenerator")
          .type("rule-based")
          .description("Expert performance optimizer for profiling and optimization recommendations")
          .version("1.0.0")
          .build();
    }
  }
}
