package com.ghatana.yappc.sdlc.agent.specialists;

import com.ghatana.agent.framework.api.AgentContext;
import com.ghatana.agent.framework.api.GeneratorMetadata;
import com.ghatana.agent.framework.api.OutputGenerator;
import com.ghatana.agent.framework.memory.MemoryStore;
import com.ghatana.yappc.sdlc.*;
import com.ghatana.yappc.sdlc.agent.StepRequest;
import com.ghatana.yappc.sdlc.agent.YAPPCAgentBase;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Release governance agent that analyzes canary deployment metrics.
 *
 * @doc.type class
 * @doc.purpose Release governance agent that analyzes canary deployment metrics
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle perceive
 */
public class CanaryAnalysisAgent extends YAPPCAgentBase<CanaryAnalysisInput, CanaryAnalysisOutput> {

  private static final Logger log = LoggerFactory.getLogger(CanaryAnalysisAgent.class);

  private final MemoryStore memoryStore;

  public CanaryAnalysisAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<CanaryAnalysisInput>, StepResult<CanaryAnalysisOutput>> generator) {
    super(
        "CanaryAnalysisAgent",
        "release.canary-analysis",
        new StepContract(
            "release.canary-analysis",
            "#/definitions/CanaryAnalysisInput",
            "#/definitions/CanaryAnalysisOutput",
            List.of("release", "canary", "analysis"),
            Map.of("description", "Release governance agent that analyzes canary deployment metrics", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull CanaryAnalysisInput input) {
    if (input.canaryId() == null || input.canaryId().isEmpty()) {
      return ValidationResult.fail("canaryId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<CanaryAnalysisInput> perceive(
      @NotNull StepRequest<CanaryAnalysisInput> request, @NotNull AgentContext context) {
    log.info("Perceiving release.canary-analysis request: {}", input.input().canaryId().substring(0, Math.min(50, input.input().canaryId().length())));
    return request;
  }

  /** Rule-based generator for release.canary-analysis. */
  public static class CanaryAnalysisGenerator
      implements OutputGenerator<StepRequest<CanaryAnalysisInput>, StepResult<CanaryAnalysisOutput>> {

    private static final Logger log = LoggerFactory.getLogger(CanaryAnalysisGenerator.class);

    @Override
    public @NotNull Promise<StepResult<CanaryAnalysisOutput>> generate(
        @NotNull StepRequest<CanaryAnalysisInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      CanaryAnalysisInput stepInput = input.input();

      log.info("Executing release.canary-analysis for: {}", stepInput.canaryId());

      CanaryAnalysisOutput output =
          new CanaryAnalysisOutput(
              "release.canary-analysis-" + UUID.randomUUID(),
              "Generated verdict for " + stepInput.canaryId(),
              0.0,
              Map.of("generatedAt", start.toString()),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "release.canary-analysis", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<CanaryAnalysisInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("CanaryAnalysisGenerator")
          .type("rule-based")
          .description("Release governance agent that analyzes canary deployment metrics")
          .version("1.0.0")
          .build();
    }
  }
}
