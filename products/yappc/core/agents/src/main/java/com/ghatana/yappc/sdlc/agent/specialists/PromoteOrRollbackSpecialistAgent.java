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
 * Specialist agent for promotion or rollback decision.
 *
 * <p>Makes data-driven decision to promote to 100% or rollback based on canary metrics.
 *
 * @doc.type class
 * @doc.purpose Specialist agent for promotion/rollback
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class PromoteOrRollbackSpecialistAgent
    extends YAPPCAgentBase<PromoteOrRollbackInput, PromoteOrRollbackOutput> {

  private static final Logger log = LoggerFactory.getLogger(PromoteOrRollbackSpecialistAgent.class);

  private final MemoryStore memoryStore;

  public PromoteOrRollbackSpecialistAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull
          OutputGenerator<StepRequest<PromoteOrRollbackInput>, StepResult<PromoteOrRollbackOutput>>
              generator) {
    super(
        "PromoteOrRollbackSpecialistAgent",
        "ops.promoteOrRollback",
        new StepContract(
            "ops.promoteOrRollback",
            "#/definitions/PromoteOrRollbackInput",
            "#/definitions/PromoteOrRollbackOutput",
            List.of("ops", "promotion", "rollback"),
            Map.of("description", "Decides promotion or rollback", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull PromoteOrRollbackInput input) {
    if (input.canaryId() == null || input.canaryId().isEmpty()) {
      return ValidationResult.fail("Canary ID cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<PromoteOrRollbackInput> perceive(
      @NotNull StepRequest<PromoteOrRollbackInput> request, @NotNull AgentContext context) {
    log.info("Perceiving promotion/rollback request for canary: {}", request.input().canaryId());
    return request;
  }

  /** Rule-based generator for promotion/rollback decision. */
  public static class PromoteOrRollbackGenerator
      implements OutputGenerator<
          StepRequest<PromoteOrRollbackInput>, StepResult<PromoteOrRollbackOutput>> {

    private static final Logger log = LoggerFactory.getLogger(PromoteOrRollbackGenerator.class);

    @Override
    public @NotNull Promise<StepResult<PromoteOrRollbackOutput>> generate(
        @NotNull StepRequest<PromoteOrRollbackInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      PromoteOrRollbackInput decisionInput = input.input();

      log.info("Making promotion/rollback decision for canary: {}", decisionInput.canaryId());

      // Simulate canary analysis (in real system, fetch from monitoring)
      double observedErrorRate = 0.001; // 0.1%

      String decision;
      String reason;
      int trafficPercentage;

      if (observedErrorRate <= decisionInput.errorRateThreshold()) {
        decision = "promote";
        reason =
            String.format(
                "Error rate %.3f%% is below threshold %.3f%%",
                observedErrorRate * 100, decisionInput.errorRateThreshold() * 100);
        trafficPercentage = 100;
      } else {
        decision = "rollback";
        reason =
            String.format(
                "Error rate %.3f%% exceeds threshold %.3f%%",
                observedErrorRate * 100, decisionInput.errorRateThreshold() * 100);
        trafficPercentage = 0;
      }

      String decisionId = "decision-" + UUID.randomUUID();

      PromoteOrRollbackOutput output =
          new PromoteOrRollbackOutput(
              decisionId,
              decision,
              reason,
              trafficPercentage,
              Map.of(
                  "canaryId",
                  decisionInput.canaryId(),
                  "observedErrorRate",
                  observedErrorRate,
                  "threshold",
                  decisionInput.errorRateThreshold(),
                  "decidedAt",
                  start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("decisionId", decisionId, "decision", decision),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<PromoteOrRollbackInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0); // Rule-based
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("PromoteOrRollbackGenerator")
          .type("rule-based")
          .description("Makes data-driven promotion or rollback decision")
          .version("1.0.0")
          .build();
    }
  }
}
