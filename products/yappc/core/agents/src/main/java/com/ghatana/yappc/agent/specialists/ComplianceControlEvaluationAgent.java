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
 * Worker agent that evaluates specific compliance controls.
 *
 * @doc.type class
 * @doc.purpose Worker agent that evaluates specific compliance controls
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class ComplianceControlEvaluationAgent extends YAPPCAgentBase<ComplianceControlEvaluationInput, ComplianceControlEvaluationOutput> {

  private static final Logger log = LoggerFactory.getLogger(ComplianceControlEvaluationAgent.class);

  private final MemoryStore memoryStore;

  public ComplianceControlEvaluationAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ComplianceControlEvaluationInput>, StepResult<ComplianceControlEvaluationOutput>> generator) {
    super(
        "ComplianceControlEvaluationAgent",
        "worker.compliance-control-eval",
        new StepContract(
            "worker.compliance-control-eval",
            "#/definitions/ComplianceControlEvaluationInput",
            "#/definitions/ComplianceControlEvaluationOutput",
            List.of("compliance", "control-evaluation"),
            Map.of("description", "Worker agent that evaluates specific compliance controls", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ComplianceControlEvaluationInput input) {
    if (input.controlId() == null || input.controlId().isEmpty()) {
      return ValidationResult.fail("controlId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ComplianceControlEvaluationInput> perceive(
      @NotNull StepRequest<ComplianceControlEvaluationInput> request, @NotNull AgentContext context) {
    log.info("Perceiving worker.compliance-control-eval request: {}", request.input().controlId().substring(0, Math.min(50, request.input().controlId().length())));
    return request;
  }

  /** Rule-based generator for worker.compliance-control-eval. */
  public static class ComplianceControlEvaluationGenerator
      implements OutputGenerator<StepRequest<ComplianceControlEvaluationInput>, StepResult<ComplianceControlEvaluationOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ComplianceControlEvaluationGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ComplianceControlEvaluationOutput>> generate(
        @NotNull StepRequest<ComplianceControlEvaluationInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ComplianceControlEvaluationInput stepInput = input.input();

      log.info("Executing worker.compliance-control-eval for: {}", stepInput.controlId());

      ComplianceControlEvaluationOutput output =
          new ComplianceControlEvaluationOutput(
              "worker.compliance-control-eval-" + UUID.randomUUID(),
              "Generated status for " + stepInput.controlId(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "worker.compliance-control-eval", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ComplianceControlEvaluationInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ComplianceControlEvaluationGenerator")
          .type("rule-based")
          .description("Worker agent that evaluates specific compliance controls")
          .version("1.0.0")
          .build();
    }
  }
}
