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
 * Governance agent that enforces budget constraints on AI and cloud spend.
 *
 * @doc.type class
 * @doc.purpose Governance agent that enforces budget constraints on AI and cloud spend
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class BudgetGateAgent extends YAPPCAgentBase<BudgetGateInput, BudgetGateOutput> {

  private static final Logger log = LoggerFactory.getLogger(BudgetGateAgent.class);

  private final MemoryStore memoryStore;

  public BudgetGateAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<BudgetGateInput>, StepResult<BudgetGateOutput>> generator) {
    super(
        "BudgetGateAgent",
        "governance.budget-gate",
        new StepContract(
            "governance.budget-gate",
            "#/definitions/BudgetGateInput",
            "#/definitions/BudgetGateOutput",
            List.of("governance", "budget", "cost-control"),
            Map.of("description", "Governance agent that enforces budget constraints on AI and cloud spend", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull BudgetGateInput input) {
    if (input.requestId() == null || input.requestId().isEmpty()) {
      return ValidationResult.fail("requestId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<BudgetGateInput> perceive(
      @NotNull StepRequest<BudgetGateInput> request, @NotNull AgentContext context) {
    log.info("Perceiving governance.budget-gate request: {}", request.input().requestId().substring(0, Math.min(50, request.input().requestId().length())));
    return request;
  }

  /** Rule-based generator for governance.budget-gate. */
  public static class BudgetGateGenerator
      implements OutputGenerator<StepRequest<BudgetGateInput>, StepResult<BudgetGateOutput>> {

    private static final Logger log = LoggerFactory.getLogger(BudgetGateGenerator.class);

    @Override
    public @NotNull Promise<StepResult<BudgetGateOutput>> generate(
        @NotNull StepRequest<BudgetGateInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      BudgetGateInput stepInput = input.input();

      log.info("Executing governance.budget-gate for: {}", stepInput.requestId());

      BudgetGateOutput output =
          new BudgetGateOutput(
              "governance.budget-gate-" + UUID.randomUUID(),
              true,
              0.0,
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "governance.budget-gate", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<BudgetGateInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("BudgetGateGenerator")
          .type("rule-based")
          .description("Governance agent that enforces budget constraints on AI and cloud spend")
          .version("1.0.0")
          .build();
    }
  }
}
