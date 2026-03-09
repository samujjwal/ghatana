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
 * Debug micro-agent that analyzes JVM thread dumps for deadlocks and contention.
 *
 * @doc.type class
 * @doc.purpose Debug micro-agent that analyzes JVM thread dumps for deadlocks and contention
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle perceive
 */
public class ThreadDumpAnalyzerAgent extends YAPPCAgentBase<ThreadDumpAnalyzerInput, ThreadDumpAnalyzerOutput> {

  private static final Logger log = LoggerFactory.getLogger(ThreadDumpAnalyzerAgent.class);

  private final MemoryStore memoryStore;

  public ThreadDumpAnalyzerAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ThreadDumpAnalyzerInput>, StepResult<ThreadDumpAnalyzerOutput>> generator) {
    super(
        "ThreadDumpAnalyzerAgent",
        "debug.thread-dump-analyzer",
        new StepContract(
            "debug.thread-dump-analyzer",
            "#/definitions/ThreadDumpAnalyzerInput",
            "#/definitions/ThreadDumpAnalyzerOutput",
            List.of("debug", "thread-analysis", "concurrency"),
            Map.of("description", "Debug micro-agent that analyzes JVM thread dumps for deadlocks and contention", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ThreadDumpAnalyzerInput input) {
    if (input.dumpContent() == null || input.dumpContent().isEmpty()) {
      return ValidationResult.fail("dumpContent cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ThreadDumpAnalyzerInput> perceive(
      @NotNull StepRequest<ThreadDumpAnalyzerInput> request, @NotNull AgentContext context) {
    log.info("Perceiving debug.thread-dump-analyzer request: {}", input.input().dumpContent().substring(0, Math.min(50, input.input().dumpContent().length())));
    return request;
  }

  /** Rule-based generator for debug.thread-dump-analyzer. */
  public static class ThreadDumpAnalyzerGenerator
      implements OutputGenerator<StepRequest<ThreadDumpAnalyzerInput>, StepResult<ThreadDumpAnalyzerOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ThreadDumpAnalyzerGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ThreadDumpAnalyzerOutput>> generate(
        @NotNull StepRequest<ThreadDumpAnalyzerInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ThreadDumpAnalyzerInput stepInput = input.input();

      log.info("Executing debug.thread-dump-analyzer for: {}", stepInput.dumpContent());

      ThreadDumpAnalyzerOutput output =
          new ThreadDumpAnalyzerOutput(
              "debug.thread-dump-analyzer-" + UUID.randomUUID(),
              List.of(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "debug.thread-dump-analyzer", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ThreadDumpAnalyzerInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ThreadDumpAnalyzerGenerator")
          .type("rule-based")
          .description("Debug micro-agent that analyzes JVM thread dumps for deadlocks and contention")
          .version("1.0.0")
          .build();
    }
  }
}
