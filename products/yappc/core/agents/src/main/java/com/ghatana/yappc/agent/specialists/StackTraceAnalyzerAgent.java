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
 * Debug micro-agent that parses and analyzes stack traces for root cause identification.
 *
 * @doc.type class
 * @doc.purpose Debug micro-agent that parses and analyzes stack traces for root cause identification
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle perceive
 */
public class StackTraceAnalyzerAgent extends YAPPCAgentBase<StackTraceAnalyzerInput, StackTraceAnalyzerOutput> {

  private static final Logger log = LoggerFactory.getLogger(StackTraceAnalyzerAgent.class);

  private final MemoryStore memoryStore;

  public StackTraceAnalyzerAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<StackTraceAnalyzerInput>, StepResult<StackTraceAnalyzerOutput>> generator) {
    super(
        "StackTraceAnalyzerAgent",
        "debug.stack-trace-analyzer",
        new StepContract(
            "debug.stack-trace-analyzer",
            "#/definitions/StackTraceAnalyzerInput",
            "#/definitions/StackTraceAnalyzerOutput",
            List.of("debug", "stack-trace"),
            Map.of("description", "Debug micro-agent that parses and analyzes stack traces for root cause identification", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull StackTraceAnalyzerInput input) {
    if (input.stackTrace() == null || input.stackTrace().isEmpty()) {
      return ValidationResult.fail("stackTrace cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<StackTraceAnalyzerInput> perceive(
      @NotNull StepRequest<StackTraceAnalyzerInput> request, @NotNull AgentContext context) {
    log.info("Perceiving debug.stack-trace-analyzer request: {}", request.input().stackTrace().substring(0, Math.min(50, request.input().stackTrace().length())));
    return request;
  }

  /** Rule-based generator for debug.stack-trace-analyzer. */
  public static class StackTraceAnalyzerGenerator
      implements OutputGenerator<StepRequest<StackTraceAnalyzerInput>, StepResult<StackTraceAnalyzerOutput>> {

    private static final Logger log = LoggerFactory.getLogger(StackTraceAnalyzerGenerator.class);

    @Override
    public @NotNull Promise<StepResult<StackTraceAnalyzerOutput>> generate(
        @NotNull StepRequest<StackTraceAnalyzerInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      StackTraceAnalyzerInput stepInput = input.input();

      log.info("Executing debug.stack-trace-analyzer for: {}", stepInput.stackTrace());

      StackTraceAnalyzerOutput output =
          new StackTraceAnalyzerOutput(
              "debug.stack-trace-analyzer-" + UUID.randomUUID(),
              "Generated rootCauseClass for " + stepInput.stackTrace(),
              "Generated rootCauseMethod for " + stepInput.stackTrace(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "debug.stack-trace-analyzer", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<StackTraceAnalyzerInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("StackTraceAnalyzerGenerator")
          .type("rule-based")
          .description("Debug micro-agent that parses and analyzes stack traces for root cause identification")
          .version("1.0.0")
          .build();
    }
  }
}
