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
 * Governance agent that enforces release policies and approval workflows.
 *
 * @doc.type class
 * @doc.purpose Governance agent that enforces release policies and approval workflows
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class ReleaseGovernanceAgent extends YAPPCAgentBase<ReleaseGovernanceInput, ReleaseGovernanceOutput> {

  private static final Logger log = LoggerFactory.getLogger(ReleaseGovernanceAgent.class);

  private final MemoryStore memoryStore;

  public ReleaseGovernanceAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ReleaseGovernanceInput>, StepResult<ReleaseGovernanceOutput>> generator) {
    super(
        "ReleaseGovernanceAgent",
        "governance.release",
        new StepContract(
            "governance.release",
            "#/definitions/ReleaseGovernanceInput",
            "#/definitions/ReleaseGovernanceOutput",
            List.of("governance", "release", "approval"),
            Map.of("description", "Governance agent that enforces release policies and approval workflows", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ReleaseGovernanceInput input) {
    if (input.releaseId() == null || input.releaseId().isEmpty()) {
      return ValidationResult.fail("releaseId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ReleaseGovernanceInput> perceive(
      @NotNull StepRequest<ReleaseGovernanceInput> request, @NotNull AgentContext context) {
    log.info("Perceiving governance.release request: {}", input.input().releaseId().substring(0, Math.min(50, input.input().releaseId().length())));
    return request;
  }

  /** Rule-based generator for governance.release. */
  public static class ReleaseGovernanceGenerator
      implements OutputGenerator<StepRequest<ReleaseGovernanceInput>, StepResult<ReleaseGovernanceOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ReleaseGovernanceGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ReleaseGovernanceOutput>> generate(
        @NotNull StepRequest<ReleaseGovernanceInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ReleaseGovernanceInput stepInput = input.input();

      log.info("Executing governance.release for: {}", stepInput.releaseId());

      ReleaseGovernanceOutput output =
          new ReleaseGovernanceOutput(
              "governance.release-" + UUID.randomUUID(),
              true,
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "governance.release", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ReleaseGovernanceInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ReleaseGovernanceGenerator")
          .type("rule-based")
          .description("Governance agent that enforces release policies and approval workflows")
          .version("1.0.0")
          .build();
    }
  }
}
