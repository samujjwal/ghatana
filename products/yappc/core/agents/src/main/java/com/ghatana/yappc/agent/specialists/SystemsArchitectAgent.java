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
 * Strategic systems architect for cross-cutting technical decisions.
 *
 * @doc.type class
 * @doc.purpose Strategic systems architect for cross-cutting technical decisions
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class SystemsArchitectAgent extends YAPPCAgentBase<SystemsArchitectInput, SystemsArchitectOutput> {

  private static final Logger log = LoggerFactory.getLogger(SystemsArchitectAgent.class);

  private final MemoryStore memoryStore;

  public SystemsArchitectAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<SystemsArchitectInput>, StepResult<SystemsArchitectOutput>> generator) {
    super(
        "SystemsArchitectAgent",
        "strategic.systems-architect",
        new StepContract(
            "strategic.systems-architect",
            "#/definitions/SystemsArchitectInput",
            "#/definitions/SystemsArchitectOutput",
            List.of("strategic", "architecture"),
            Map.of("description", "Strategic systems architect for cross-cutting technical decisions", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull SystemsArchitectInput input) {
    if (input.systemId() == null || input.systemId().isEmpty()) {
      return ValidationResult.fail("systemId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<SystemsArchitectInput> perceive(
      @NotNull StepRequest<SystemsArchitectInput> request, @NotNull AgentContext context) {
    log.info("Perceiving strategic.systems-architect request: {}", request.input().systemId().substring(0, Math.min(50, request.input().systemId().length())));
    return request;
  }

  /** Rule-based generator for strategic.systems-architect. */
  public static class SystemsArchitectGenerator
      implements OutputGenerator<StepRequest<SystemsArchitectInput>, StepResult<SystemsArchitectOutput>> {

    private static final Logger log = LoggerFactory.getLogger(SystemsArchitectGenerator.class);

    @Override
    public @NotNull Promise<StepResult<SystemsArchitectOutput>> generate(
        @NotNull StepRequest<SystemsArchitectInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      SystemsArchitectInput stepInput = input.input();

      log.info("Executing strategic.systems-architect for: {}", stepInput.systemId());

      SystemsArchitectOutput output =
          new SystemsArchitectOutput(
              "strategic.systems-architect-" + UUID.randomUUID(),
              "Generated architectureDecision for " + stepInput.systemId(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "strategic.systems-architect", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<SystemsArchitectInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("SystemsArchitectGenerator")
          .type("rule-based")
          .description("Strategic systems architect for cross-cutting technical decisions")
          .version("1.0.0")
          .build();
    }
  }
}
