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
 * Debug micro-agent that analyzes code diffs to identify regression risks.
 *
 * @doc.type class
 * @doc.purpose Debug micro-agent that analyzes code diffs to identify regression risks
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle perceive
 */
public class DiffAnalyzerAgent extends YAPPCAgentBase<DiffAnalyzerInput, DiffAnalyzerOutput> {

  private static final Logger log = LoggerFactory.getLogger(DiffAnalyzerAgent.class);

  private final MemoryStore memoryStore;

  public DiffAnalyzerAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<DiffAnalyzerInput>, StepResult<DiffAnalyzerOutput>> generator) {
    super(
        "DiffAnalyzerAgent",
        "debug.diff-analyzer",
        new StepContract(
            "debug.diff-analyzer",
            "#/definitions/DiffAnalyzerInput",
            "#/definitions/DiffAnalyzerOutput",
            List.of("debug", "diff-analysis", "regression"),
            Map.of("description", "Debug micro-agent that analyzes code diffs to identify regression risks", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull DiffAnalyzerInput input) {
    if (input.commitId() == null || input.commitId().isEmpty()) {
      return ValidationResult.fail("commitId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<DiffAnalyzerInput> perceive(
      @NotNull StepRequest<DiffAnalyzerInput> request, @NotNull AgentContext context) {
    log.info("Perceiving debug.diff-analyzer request: {}", input.input().commitId().substring(0, Math.min(50, input.input().commitId().length())));
    return request;
  }

  /** Rule-based generator for debug.diff-analyzer. */
  public static class DiffAnalyzerGenerator
      implements OutputGenerator<StepRequest<DiffAnalyzerInput>, StepResult<DiffAnalyzerOutput>> {

    private static final Logger log = LoggerFactory.getLogger(DiffAnalyzerGenerator.class);

    @Override
    public @NotNull Promise<StepResult<DiffAnalyzerOutput>> generate(
        @NotNull StepRequest<DiffAnalyzerInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      DiffAnalyzerInput stepInput = input.input();

      log.info("Executing debug.diff-analyzer for: {}", stepInput.commitId());

      DiffAnalyzerOutput output =
          new DiffAnalyzerOutput(
              "debug.diff-analyzer-" + UUID.randomUUID(),
              List.of(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "debug.diff-analyzer", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<DiffAnalyzerInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("DiffAnalyzerGenerator")
          .type("rule-based")
          .description("Debug micro-agent that analyzes code diffs to identify regression risks")
          .version("1.0.0")
          .build();
    }
  }
}
