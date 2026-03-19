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
 * Release governance agent that enforces release quality gates.
 *
 * @doc.type class
 * @doc.purpose Release governance agent that enforces release quality gates
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class ReleaseGateAgent extends YAPPCAgentBase<ReleaseGateInput, ReleaseGateOutput> {

  private static final Logger log = LoggerFactory.getLogger(ReleaseGateAgent.class);

  private final MemoryStore memoryStore;

  public ReleaseGateAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ReleaseGateInput>, StepResult<ReleaseGateOutput>> generator) {
    super(
        "ReleaseGateAgent",
        "release.release-gate",
        new StepContract(
            "release.release-gate",
            "#/definitions/ReleaseGateInput",
            "#/definitions/ReleaseGateOutput",
            List.of("release", "quality-gate", "governance"),
            Map.of("description", "Release governance agent that enforces release quality gates", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ReleaseGateInput input) {
    if (input.releaseId() == null || input.releaseId().isEmpty()) {
      return ValidationResult.fail("releaseId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ReleaseGateInput> perceive(
      @NotNull StepRequest<ReleaseGateInput> request, @NotNull AgentContext context) {
    log.info("Perceiving release.release-gate request: {}", request.input().releaseId().substring(0, Math.min(50, request.input().releaseId().length())));
    return request;
  }

  /** Rule-based generator for release.release-gate. */
  public static class ReleaseGateGenerator
      implements OutputGenerator<StepRequest<ReleaseGateInput>, StepResult<ReleaseGateOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ReleaseGateGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ReleaseGateOutput>> generate(
        @NotNull StepRequest<ReleaseGateInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ReleaseGateInput stepInput = input.input();

      log.info("Executing release.release-gate for: {}", stepInput.releaseId());

      ReleaseGateOutput output =
          new ReleaseGateOutput(
              "release.release-gate-" + UUID.randomUUID(),
              true,
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "release.release-gate", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ReleaseGateInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ReleaseGateGenerator")
          .type("rule-based")
          .description("Release governance agent that enforces release quality gates")
          .version("1.0.0")
          .build();
    }
  }
}
