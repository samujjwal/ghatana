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
 * Debug micro-agent that performs systematic root cause analysis.
 *
 * @doc.type class
 * @doc.purpose Debug micro-agent that performs systematic root cause analysis
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class RootCauseAnalysisAgent extends YAPPCAgentBase<RootCauseAnalysisInput, RootCauseAnalysisOutput> {

  private static final Logger log = LoggerFactory.getLogger(RootCauseAnalysisAgent.class);

  private final MemoryStore memoryStore;

  public RootCauseAnalysisAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<RootCauseAnalysisInput>, StepResult<RootCauseAnalysisOutput>> generator) {
    super(
        "RootCauseAnalysisAgent",
        "debug.root-cause-analysis",
        new StepContract(
            "debug.root-cause-analysis",
            "#/definitions/RootCauseAnalysisInput",
            "#/definitions/RootCauseAnalysisOutput",
            List.of("debug", "root-cause-analysis"),
            Map.of("description", "Debug micro-agent that performs systematic root cause analysis", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull RootCauseAnalysisInput input) {
    if (input.incidentId() == null || input.incidentId().isEmpty()) {
      return ValidationResult.fail("incidentId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<RootCauseAnalysisInput> perceive(
      @NotNull StepRequest<RootCauseAnalysisInput> request, @NotNull AgentContext context) {
    log.info("Perceiving debug.root-cause-analysis request: {}", request.input().incidentId().substring(0, Math.min(50, request.input().incidentId().length())));
    return request;
  }

  /** Rule-based generator for debug.root-cause-analysis. */
  public static class RootCauseAnalysisGenerator
      implements OutputGenerator<StepRequest<RootCauseAnalysisInput>, StepResult<RootCauseAnalysisOutput>> {

    private static final Logger log = LoggerFactory.getLogger(RootCauseAnalysisGenerator.class);

    @Override
    public @NotNull Promise<StepResult<RootCauseAnalysisOutput>> generate(
        @NotNull StepRequest<RootCauseAnalysisInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      RootCauseAnalysisInput stepInput = input.input();

      log.info("Executing debug.root-cause-analysis for: {}", stepInput.incidentId());

      RootCauseAnalysisOutput output =
          new RootCauseAnalysisOutput(
              "debug.root-cause-analysis-" + UUID.randomUUID(),
              "Generated rootCause for " + stepInput.incidentId(),
              List.of(),
              "Generated confidence for " + stepInput.incidentId(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "debug.root-cause-analysis", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<RootCauseAnalysisInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("RootCauseAnalysisGenerator")
          .type("rule-based")
          .description("Debug micro-agent that performs systematic root cause analysis")
          .version("1.0.0")
          .build();
    }
  }
}
