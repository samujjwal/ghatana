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
 * Orchestrates deployment pipelines and release workflows.
 *
 * @doc.type class
 * @doc.purpose Orchestrates deployment pipelines and release workflows
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class DeployOrchestratorAgent extends YAPPCAgentBase<DeployOrchestratorInput, DeployOrchestratorOutput> {

  private static final Logger log = LoggerFactory.getLogger(DeployOrchestratorAgent.class);

  private final MemoryStore memoryStore;

  public DeployOrchestratorAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<DeployOrchestratorInput>, StepResult<DeployOrchestratorOutput>> generator) {
    super(
        "DeployOrchestratorAgent",
        "expert.deploy-orchestrator",
        new StepContract(
            "expert.deploy-orchestrator",
            "#/definitions/DeployOrchestratorInput",
            "#/definitions/DeployOrchestratorOutput",
            List.of("deployment", "orchestration", "release"),
            Map.of("description", "Orchestrates deployment pipelines and release workflows", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull DeployOrchestratorInput input) {
    if (input.releaseId() == null || input.releaseId().isEmpty()) {
      return ValidationResult.fail("releaseId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<DeployOrchestratorInput> perceive(
      @NotNull StepRequest<DeployOrchestratorInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.deploy-orchestrator request: {}", request.input().releaseId().substring(0, Math.min(50, request.input().releaseId().length())));
    return request;
  }

  /** Rule-based generator for expert.deploy-orchestrator. */
  public static class DeployOrchestratorGenerator
      implements OutputGenerator<StepRequest<DeployOrchestratorInput>, StepResult<DeployOrchestratorOutput>> {

    private static final Logger log = LoggerFactory.getLogger(DeployOrchestratorGenerator.class);

    @Override
    public @NotNull Promise<StepResult<DeployOrchestratorOutput>> generate(
        @NotNull StepRequest<DeployOrchestratorInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      DeployOrchestratorInput stepInput = input.input();

      log.info("Executing expert.deploy-orchestrator for: {}", stepInput.releaseId());

      DeployOrchestratorOutput output =
          new DeployOrchestratorOutput(
              "expert.deploy-orchestrator-" + UUID.randomUUID(),
              "Generated status for " + stepInput.releaseId(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.deploy-orchestrator", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<DeployOrchestratorInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("DeployOrchestratorGenerator")
          .type("rule-based")
          .description("Orchestrates deployment pipelines and release workflows")
          .version("1.0.0")
          .build();
    }
  }
}
