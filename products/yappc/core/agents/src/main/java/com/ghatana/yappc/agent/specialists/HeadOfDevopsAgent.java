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
 * Strategic head of DevOps overseeing infrastructure and delivery pipelines.
 *
 * @doc.type class
 * @doc.purpose Strategic head of DevOps overseeing infrastructure and delivery pipelines
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class HeadOfDevopsAgent extends YAPPCAgentBase<HeadOfDevopsInput, HeadOfDevopsOutput> {

  private static final Logger log = LoggerFactory.getLogger(HeadOfDevopsAgent.class);

  private final MemoryStore memoryStore;

  public HeadOfDevopsAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<HeadOfDevopsInput>, StepResult<HeadOfDevopsOutput>> generator) {
    super(
        "HeadOfDevopsAgent",
        "strategic.head-of-devops",
        new StepContract(
            "strategic.head-of-devops",
            "#/definitions/HeadOfDevopsInput",
            "#/definitions/HeadOfDevopsOutput",
            List.of("strategic", "devops", "infrastructure"),
            Map.of("description", "Strategic head of DevOps overseeing infrastructure and delivery pipelines", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull HeadOfDevopsInput input) {
    if (input.infrastructureId() == null || input.infrastructureId().isEmpty()) {
      return ValidationResult.fail("infrastructureId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<HeadOfDevopsInput> perceive(
      @NotNull StepRequest<HeadOfDevopsInput> request, @NotNull AgentContext context) {
    log.info("Perceiving strategic.head-of-devops request: {}", request.input().infrastructureId().substring(0, Math.min(50, request.input().infrastructureId().length())));
    return request;
  }

  /** Rule-based generator for strategic.head-of-devops. */
  public static class HeadOfDevopsGenerator
      implements OutputGenerator<StepRequest<HeadOfDevopsInput>, StepResult<HeadOfDevopsOutput>> {

    private static final Logger log = LoggerFactory.getLogger(HeadOfDevopsGenerator.class);

    @Override
    public @NotNull Promise<StepResult<HeadOfDevopsOutput>> generate(
        @NotNull StepRequest<HeadOfDevopsInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      HeadOfDevopsInput stepInput = input.input();

      log.info("Executing strategic.head-of-devops for: {}", stepInput.infrastructureId());

      HeadOfDevopsOutput output =
          new HeadOfDevopsOutput(
              "strategic.head-of-devops-" + UUID.randomUUID(),
              "Generated recommendation for " + stepInput.infrastructureId(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "strategic.head-of-devops", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<HeadOfDevopsInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("HeadOfDevopsGenerator")
          .type("rule-based")
          .description("Strategic head of DevOps overseeing infrastructure and delivery pipelines")
          .version("1.0.0")
          .build();
    }
  }
}
