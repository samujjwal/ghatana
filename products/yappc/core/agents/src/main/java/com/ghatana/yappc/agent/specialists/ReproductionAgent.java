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
 * Debug micro-agent that generates minimal reproduction steps for bugs.
 *
 * @doc.type class
 * @doc.purpose Debug micro-agent that generates minimal reproduction steps for bugs
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class ReproductionAgent extends YAPPCAgentBase<ReproductionInput, ReproductionOutput> {

  private static final Logger log = LoggerFactory.getLogger(ReproductionAgent.class);

  private final MemoryStore memoryStore;

  public ReproductionAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ReproductionInput>, StepResult<ReproductionOutput>> generator) {
    super(
        "ReproductionAgent",
        "debug.reproduction",
        new StepContract(
            "debug.reproduction",
            "#/definitions/ReproductionInput",
            "#/definitions/ReproductionOutput",
            List.of("debug", "reproduction"),
            Map.of("description", "Debug micro-agent that generates minimal reproduction steps for bugs", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ReproductionInput input) {
    if (input.bugId() == null || input.bugId().isEmpty()) {
      return ValidationResult.fail("bugId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ReproductionInput> perceive(
      @NotNull StepRequest<ReproductionInput> request, @NotNull AgentContext context) {
    log.info("Perceiving debug.reproduction request: {}", request.input().bugId().substring(0, Math.min(50, request.input().bugId().length())));
    return request;
  }

  /** Rule-based generator for debug.reproduction. */
  public static class ReproductionGenerator
      implements OutputGenerator<StepRequest<ReproductionInput>, StepResult<ReproductionOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ReproductionGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ReproductionOutput>> generate(
        @NotNull StepRequest<ReproductionInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ReproductionInput stepInput = input.input();

      log.info("Executing debug.reproduction for: {}", stepInput.bugId());

      ReproductionOutput output =
          new ReproductionOutput(
              "debug.reproduction-" + UUID.randomUUID(),
              List.of(),
              "Generated reproCode for " + stepInput.bugId(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "debug.reproduction", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ReproductionInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ReproductionGenerator")
          .type("rule-based")
          .description("Debug micro-agent that generates minimal reproduction steps for bugs")
          .version("1.0.0")
          .build();
    }
  }
}
