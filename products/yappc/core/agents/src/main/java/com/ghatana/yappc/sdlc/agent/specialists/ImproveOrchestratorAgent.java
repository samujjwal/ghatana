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
 * Orchestrates continuous improvement across codebase and processes.
 *
 * @doc.type class
 * @doc.purpose Orchestrates continuous improvement across codebase and processes
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class ImproveOrchestratorAgent extends YAPPCAgentBase<ImproveOrchestratorInput, ImproveOrchestratorOutput> {

  private static final Logger log = LoggerFactory.getLogger(ImproveOrchestratorAgent.class);

  private final MemoryStore memoryStore;

  public ImproveOrchestratorAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ImproveOrchestratorInput>, StepResult<ImproveOrchestratorOutput>> generator) {
    super(
        "ImproveOrchestratorAgent",
        "expert.improve-orchestrator",
        new StepContract(
            "expert.improve-orchestrator",
            "#/definitions/ImproveOrchestratorInput",
            "#/definitions/ImproveOrchestratorOutput",
            List.of("improvement", "orchestration", "refactoring"),
            Map.of("description", "Orchestrates continuous improvement across codebase and processes", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ImproveOrchestratorInput input) {
    if (input.projectId() == null || input.projectId().isEmpty()) {
      return ValidationResult.fail("projectId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ImproveOrchestratorInput> perceive(
      @NotNull StepRequest<ImproveOrchestratorInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.improve-orchestrator request: {}", input.input().projectId().substring(0, Math.min(50, input.input().projectId().length())));
    return request;
  }

  /** Rule-based generator for expert.improve-orchestrator. */
  public static class ImproveOrchestratorGenerator
      implements OutputGenerator<StepRequest<ImproveOrchestratorInput>, StepResult<ImproveOrchestratorOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ImproveOrchestratorGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ImproveOrchestratorOutput>> generate(
        @NotNull StepRequest<ImproveOrchestratorInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ImproveOrchestratorInput stepInput = input.input();

      log.info("Executing expert.improve-orchestrator for: {}", stepInput.projectId());

      ImproveOrchestratorOutput output =
          new ImproveOrchestratorOutput(
              "expert.improve-orchestrator-" + UUID.randomUUID(),
              List.of(),
              Map.of("generatedAt", start.toString()),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.improve-orchestrator", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ImproveOrchestratorInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ImproveOrchestratorGenerator")
          .type("rule-based")
          .description("Orchestrates continuous improvement across codebase and processes")
          .version("1.0.0")
          .build();
    }
  }
}
