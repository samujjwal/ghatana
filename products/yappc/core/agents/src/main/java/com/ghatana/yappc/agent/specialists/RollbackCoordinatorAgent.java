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
 * Release governance agent that coordinates rollback procedures.
 *
 * @doc.type class
 * @doc.purpose Release governance agent that coordinates rollback procedures
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class RollbackCoordinatorAgent extends YAPPCAgentBase<RollbackCoordinatorInput, RollbackCoordinatorOutput> {

  private static final Logger log = LoggerFactory.getLogger(RollbackCoordinatorAgent.class);

  private final MemoryStore memoryStore;

  public RollbackCoordinatorAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<RollbackCoordinatorInput>, StepResult<RollbackCoordinatorOutput>> generator) {
    super(
        "RollbackCoordinatorAgent",
        "release.rollback-coordinator",
        new StepContract(
            "release.rollback-coordinator",
            "#/definitions/RollbackCoordinatorInput",
            "#/definitions/RollbackCoordinatorOutput",
            List.of("release", "rollback", "coordination"),
            Map.of("description", "Release governance agent that coordinates rollback procedures", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull RollbackCoordinatorInput input) {
    if (input.deploymentId() == null || input.deploymentId().isEmpty()) {
      return ValidationResult.fail("deploymentId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<RollbackCoordinatorInput> perceive(
      @NotNull StepRequest<RollbackCoordinatorInput> request, @NotNull AgentContext context) {
    log.info("Perceiving release.rollback-coordinator request: {}", request.input().deploymentId().substring(0, Math.min(50, request.input().deploymentId().length())));
    return request;
  }

  /** Rule-based generator for release.rollback-coordinator. */
  public static class RollbackCoordinatorGenerator
      implements OutputGenerator<StepRequest<RollbackCoordinatorInput>, StepResult<RollbackCoordinatorOutput>> {

    private static final Logger log = LoggerFactory.getLogger(RollbackCoordinatorGenerator.class);

    @Override
    public @NotNull Promise<StepResult<RollbackCoordinatorOutput>> generate(
        @NotNull StepRequest<RollbackCoordinatorInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      RollbackCoordinatorInput stepInput = input.input();

      log.info("Executing release.rollback-coordinator for: {}", stepInput.deploymentId());

      RollbackCoordinatorOutput output =
          new RollbackCoordinatorOutput(
              "release.rollback-coordinator-" + UUID.randomUUID(),
              "Generated status for " + stepInput.deploymentId(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "release.rollback-coordinator", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<RollbackCoordinatorInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("RollbackCoordinatorGenerator")
          .type("rule-based")
          .description("Release governance agent that coordinates rollback procedures")
          .version("1.0.0")
          .build();
    }
  }
}
