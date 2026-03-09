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
 * Debug micro-agent that analyzes log files for error patterns and anomalies.
 *
 * @doc.type class
 * @doc.purpose Debug micro-agent that analyzes log files for error patterns and anomalies
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle perceive
 */
public class LogAnalysisAgent extends YAPPCAgentBase<LogAnalysisInput, LogAnalysisOutput> {

  private static final Logger log = LoggerFactory.getLogger(LogAnalysisAgent.class);

  private final MemoryStore memoryStore;

  public LogAnalysisAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<LogAnalysisInput>, StepResult<LogAnalysisOutput>> generator) {
    super(
        "LogAnalysisAgent",
        "debug.log-analysis",
        new StepContract(
            "debug.log-analysis",
            "#/definitions/LogAnalysisInput",
            "#/definitions/LogAnalysisOutput",
            List.of("debug", "log-analysis"),
            Map.of("description", "Debug micro-agent that analyzes log files for error patterns and anomalies", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull LogAnalysisInput input) {
    if (input.logSource() == null || input.logSource().isEmpty()) {
      return ValidationResult.fail("logSource cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<LogAnalysisInput> perceive(
      @NotNull StepRequest<LogAnalysisInput> request, @NotNull AgentContext context) {
    log.info("Perceiving debug.log-analysis request: {}", input.input().logSource().substring(0, Math.min(50, input.input().logSource().length())));
    return request;
  }

  /** Rule-based generator for debug.log-analysis. */
  public static class LogAnalysisGenerator
      implements OutputGenerator<StepRequest<LogAnalysisInput>, StepResult<LogAnalysisOutput>> {

    private static final Logger log = LoggerFactory.getLogger(LogAnalysisGenerator.class);

    @Override
    public @NotNull Promise<StepResult<LogAnalysisOutput>> generate(
        @NotNull StepRequest<LogAnalysisInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      LogAnalysisInput stepInput = input.input();

      log.info("Executing debug.log-analysis for: {}", stepInput.logSource());

      LogAnalysisOutput output =
          new LogAnalysisOutput(
              "debug.log-analysis-" + UUID.randomUUID(),
              List.of(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "debug.log-analysis", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<LogAnalysisInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("LogAnalysisGenerator")
          .type("rule-based")
          .description("Debug micro-agent that analyzes log files for error patterns and anomalies")
          .version("1.0.0")
          .build();
    }
  }
}
