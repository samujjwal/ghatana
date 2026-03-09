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
 * Orchestrates the complete SDLC lifecycle from intake to production.
 *
 * @doc.type class
 * @doc.purpose Orchestrates the complete SDLC lifecycle from intake to production
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class FullLifecycleOrchestratorAgent extends YAPPCAgentBase<FullLifecycleOrchestratorInput, FullLifecycleOrchestratorOutput> {

  private static final Logger log = LoggerFactory.getLogger(FullLifecycleOrchestratorAgent.class);

  private final MemoryStore memoryStore;

  public FullLifecycleOrchestratorAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<FullLifecycleOrchestratorInput>, StepResult<FullLifecycleOrchestratorOutput>> generator) {
    super(
        "FullLifecycleOrchestratorAgent",
        "strategic.full-lifecycle",
        new StepContract(
            "strategic.full-lifecycle",
            "#/definitions/FullLifecycleOrchestratorInput",
            "#/definitions/FullLifecycleOrchestratorOutput",
            List.of("strategic", "orchestration", "lifecycle"),
            Map.of("description", "Orchestrates the complete SDLC lifecycle from intake to production", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull FullLifecycleOrchestratorInput input) {
    if (input.projectId() == null || input.projectId().isEmpty()) {
      return ValidationResult.fail("projectId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<FullLifecycleOrchestratorInput> perceive(
      @NotNull StepRequest<FullLifecycleOrchestratorInput> request, @NotNull AgentContext context) {
    log.info("Perceiving strategic.full-lifecycle request: {}", input.input().projectId().substring(0, Math.min(50, input.input().projectId().length())));
    return request;
  }

  /** Rule-based generator for strategic.full-lifecycle. */
  public static class FullLifecycleOrchestratorGenerator
      implements OutputGenerator<StepRequest<FullLifecycleOrchestratorInput>, StepResult<FullLifecycleOrchestratorOutput>> {

    private static final Logger log = LoggerFactory.getLogger(FullLifecycleOrchestratorGenerator.class);

    @Override
    public @NotNull Promise<StepResult<FullLifecycleOrchestratorOutput>> generate(
        @NotNull StepRequest<FullLifecycleOrchestratorInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      FullLifecycleOrchestratorInput stepInput = input.input();

      log.info("Executing strategic.full-lifecycle for: {}", stepInput.projectId());

      FullLifecycleOrchestratorOutput output =
          new FullLifecycleOrchestratorOutput(
              "strategic.full-lifecycle-" + UUID.randomUUID(),
              "Generated nextPhase for " + stepInput.projectId(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "strategic.full-lifecycle", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<FullLifecycleOrchestratorInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("FullLifecycleOrchestratorGenerator")
          .type("rule-based")
          .description("Orchestrates the complete SDLC lifecycle from intake to production")
          .version("1.0.0")
          .build();
    }
  }
}
