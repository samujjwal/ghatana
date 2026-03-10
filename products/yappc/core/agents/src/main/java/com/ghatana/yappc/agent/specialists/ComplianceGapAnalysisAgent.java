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
 * Worker agent that performs gap analysis against compliance frameworks.
 *
 * @doc.type class
 * @doc.purpose Worker agent that performs gap analysis against compliance frameworks
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class ComplianceGapAnalysisAgent extends YAPPCAgentBase<ComplianceGapAnalysisInput, ComplianceGapAnalysisOutput> {

  private static final Logger log = LoggerFactory.getLogger(ComplianceGapAnalysisAgent.class);

  private final MemoryStore memoryStore;

  public ComplianceGapAnalysisAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ComplianceGapAnalysisInput>, StepResult<ComplianceGapAnalysisOutput>> generator) {
    super(
        "ComplianceGapAnalysisAgent",
        "worker.compliance-gap-analysis",
        new StepContract(
            "worker.compliance-gap-analysis",
            "#/definitions/ComplianceGapAnalysisInput",
            "#/definitions/ComplianceGapAnalysisOutput",
            List.of("compliance", "gap-analysis"),
            Map.of("description", "Worker agent that performs gap analysis against compliance frameworks", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ComplianceGapAnalysisInput input) {
    if (input.frameworkId() == null || input.frameworkId().isEmpty()) {
      return ValidationResult.fail("frameworkId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ComplianceGapAnalysisInput> perceive(
      @NotNull StepRequest<ComplianceGapAnalysisInput> request, @NotNull AgentContext context) {
    log.info("Perceiving worker.compliance-gap-analysis request: {}", request.input().frameworkId().substring(0, Math.min(50, request.input().frameworkId().length())));
    return request;
  }

  /** Rule-based generator for worker.compliance-gap-analysis. */
  public static class ComplianceGapAnalysisGenerator
      implements OutputGenerator<StepRequest<ComplianceGapAnalysisInput>, StepResult<ComplianceGapAnalysisOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ComplianceGapAnalysisGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ComplianceGapAnalysisOutput>> generate(
        @NotNull StepRequest<ComplianceGapAnalysisInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ComplianceGapAnalysisInput stepInput = input.input();

      log.info("Executing worker.compliance-gap-analysis for: {}", stepInput.frameworkId());

      ComplianceGapAnalysisOutput output =
          new ComplianceGapAnalysisOutput(
              "worker.compliance-gap-analysis-" + UUID.randomUUID(),
              List.of(),
              "Generated complianceScore for " + stepInput.frameworkId(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "worker.compliance-gap-analysis", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ComplianceGapAnalysisInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ComplianceGapAnalysisGenerator")
          .type("rule-based")
          .description("Worker agent that performs gap analysis against compliance frameworks")
          .version("1.0.0")
          .build();
    }
  }
}
