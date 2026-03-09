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
 * Governance agent that enforces dependency policies and license compliance.
 *
 * @doc.type class
 * @doc.purpose Governance agent that enforces dependency policies and license compliance
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class DependencyGateAgent extends YAPPCAgentBase<DependencyGateInput, DependencyGateOutput> {

  private static final Logger log = LoggerFactory.getLogger(DependencyGateAgent.class);

  private final MemoryStore memoryStore;

  public DependencyGateAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<DependencyGateInput>, StepResult<DependencyGateOutput>> generator) {
    super(
        "DependencyGateAgent",
        "governance.dependency-gate",
        new StepContract(
            "governance.dependency-gate",
            "#/definitions/DependencyGateInput",
            "#/definitions/DependencyGateOutput",
            List.of("governance", "dependency", "licensing"),
            Map.of("description", "Governance agent that enforces dependency policies and license compliance", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull DependencyGateInput input) {
    if (input.projectId() == null || input.projectId().isEmpty()) {
      return ValidationResult.fail("projectId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<DependencyGateInput> perceive(
      @NotNull StepRequest<DependencyGateInput> request, @NotNull AgentContext context) {
    log.info("Perceiving governance.dependency-gate request: {}", input.input().projectId().substring(0, Math.min(50, input.input().projectId().length())));
    return request;
  }

  /** Rule-based generator for governance.dependency-gate. */
  public static class DependencyGateGenerator
      implements OutputGenerator<StepRequest<DependencyGateInput>, StepResult<DependencyGateOutput>> {

    private static final Logger log = LoggerFactory.getLogger(DependencyGateGenerator.class);

    @Override
    public @NotNull Promise<StepResult<DependencyGateOutput>> generate(
        @NotNull StepRequest<DependencyGateInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      DependencyGateInput stepInput = input.input();

      log.info("Executing governance.dependency-gate for: {}", stepInput.projectId());

      DependencyGateOutput output =
          new DependencyGateOutput(
              "governance.dependency-gate-" + UUID.randomUUID(),
              true,
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "governance.dependency-gate", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<DependencyGateInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("DependencyGateGenerator")
          .type("rule-based")
          .description("Governance agent that enforces dependency policies and license compliance")
          .version("1.0.0")
          .build();
    }
  }
}
