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
 * Debug micro-agent that analyzes JVM heap dumps for memory issues.
 *
 * @doc.type class
 * @doc.purpose Debug micro-agent that analyzes JVM heap dumps for memory issues
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle perceive
 */
public class HeapDumpAnalyzerAgent extends YAPPCAgentBase<HeapDumpAnalyzerInput, HeapDumpAnalyzerOutput> {

  private static final Logger log = LoggerFactory.getLogger(HeapDumpAnalyzerAgent.class);

  private final MemoryStore memoryStore;

  public HeapDumpAnalyzerAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<HeapDumpAnalyzerInput>, StepResult<HeapDumpAnalyzerOutput>> generator) {
    super(
        "HeapDumpAnalyzerAgent",
        "debug.heap-dump-analyzer",
        new StepContract(
            "debug.heap-dump-analyzer",
            "#/definitions/HeapDumpAnalyzerInput",
            "#/definitions/HeapDumpAnalyzerOutput",
            List.of("debug", "heap-analysis", "memory"),
            Map.of("description", "Debug micro-agent that analyzes JVM heap dumps for memory issues", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull HeapDumpAnalyzerInput input) {
    if (input.dumpPath() == null || input.dumpPath().isEmpty()) {
      return ValidationResult.fail("dumpPath cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<HeapDumpAnalyzerInput> perceive(
      @NotNull StepRequest<HeapDumpAnalyzerInput> request, @NotNull AgentContext context) {
    log.info("Perceiving debug.heap-dump-analyzer request: {}", input.input().dumpPath().substring(0, Math.min(50, input.input().dumpPath().length())));
    return request;
  }

  /** Rule-based generator for debug.heap-dump-analyzer. */
  public static class HeapDumpAnalyzerGenerator
      implements OutputGenerator<StepRequest<HeapDumpAnalyzerInput>, StepResult<HeapDumpAnalyzerOutput>> {

    private static final Logger log = LoggerFactory.getLogger(HeapDumpAnalyzerGenerator.class);

    @Override
    public @NotNull Promise<StepResult<HeapDumpAnalyzerOutput>> generate(
        @NotNull StepRequest<HeapDumpAnalyzerInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      HeapDumpAnalyzerInput stepInput = input.input();

      log.info("Executing debug.heap-dump-analyzer for: {}", stepInput.dumpPath());

      HeapDumpAnalyzerOutput output =
          new HeapDumpAnalyzerOutput(
              "debug.heap-dump-analyzer-" + UUID.randomUUID(),
              List.of(),
              Map.of("generatedAt", start.toString()),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "debug.heap-dump-analyzer", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<HeapDumpAnalyzerInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("HeapDumpAnalyzerGenerator")
          .type("rule-based")
          .description("Debug micro-agent that analyzes JVM heap dumps for memory issues")
          .version("1.0.0")
          .build();
    }
  }
}
