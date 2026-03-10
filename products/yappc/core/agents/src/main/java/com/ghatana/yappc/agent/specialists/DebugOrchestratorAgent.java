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
 * Orchestrates debugging workflows using specialized debug micro-agents.
 *
 * @doc.type class
 * @doc.purpose Orchestrates debugging workflows using specialized debug micro-agents
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class DebugOrchestratorAgent extends YAPPCAgentBase<DebugOrchestratorInput, DebugOrchestratorOutput> {

  private static final Logger log = LoggerFactory.getLogger(DebugOrchestratorAgent.class);

  private final MemoryStore memoryStore;

  public DebugOrchestratorAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<DebugOrchestratorInput>, StepResult<DebugOrchestratorOutput>> generator) {
    super(
        "DebugOrchestratorAgent",
        "expert.debug-orchestrator",
        new StepContract(
            "expert.debug-orchestrator",
            "#/definitions/DebugOrchestratorInput",
            "#/definitions/DebugOrchestratorOutput",
            List.of("debug", "orchestration", "incident-response"),
            Map.of("description", "Orchestrates debugging workflows using specialized debug micro-agents", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull DebugOrchestratorInput input) {
    if (input.incidentId() == null || input.incidentId().isEmpty()) {
      return ValidationResult.fail("incidentId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<DebugOrchestratorInput> perceive(
      @NotNull StepRequest<DebugOrchestratorInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.debug-orchestrator request: {}", request.input().incidentId().substring(0, Math.min(50, request.input().incidentId().length())));
    return request;
  }

  /** Rule-based generator for expert.debug-orchestrator. */
  public static class DebugOrchestratorGenerator
      implements OutputGenerator<StepRequest<DebugOrchestratorInput>, StepResult<DebugOrchestratorOutput>> {

    private static final Logger log = LoggerFactory.getLogger(DebugOrchestratorGenerator.class);

    @Override
    public @NotNull Promise<StepResult<DebugOrchestratorOutput>> generate(
        @NotNull StepRequest<DebugOrchestratorInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      DebugOrchestratorInput stepInput = input.input();

      log.info("Executing expert.debug-orchestrator for: {}", stepInput.incidentId());

      DebugOrchestratorOutput output =
          new DebugOrchestratorOutput(
              "expert.debug-orchestrator-" + UUID.randomUUID(),
              "Generated rootCause for " + stepInput.incidentId(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.debug-orchestrator", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<DebugOrchestratorInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("DebugOrchestratorGenerator")
          .type("rule-based")
          .description("Orchestrates debugging workflows using specialized debug micro-agents")
          .version("1.0.0")
          .build();
    }
  }
}
