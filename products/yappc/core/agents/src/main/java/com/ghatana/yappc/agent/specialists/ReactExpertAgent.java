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
 * Expert React/TypeScript engineer for frontend architecture and patterns.
 *
 * @doc.type class
 * @doc.purpose Expert React/TypeScript engineer for frontend architecture and patterns
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class ReactExpertAgent extends YAPPCAgentBase<ReactExpertInput, ReactExpertOutput> {

  private static final Logger log = LoggerFactory.getLogger(ReactExpertAgent.class);

  private final MemoryStore memoryStore;

  public ReactExpertAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ReactExpertInput>, StepResult<ReactExpertOutput>> generator) {
    super(
        "ReactExpertAgent",
        "expert.react",
        new StepContract(
            "expert.react",
            "#/definitions/ReactExpertInput",
            "#/definitions/ReactExpertOutput",
            List.of("react", "typescript", "frontend"),
            Map.of("description", "Expert React/TypeScript engineer for frontend architecture and patterns", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ReactExpertInput input) {
    if (input.componentContext() == null || input.componentContext().isEmpty()) {
      return ValidationResult.fail("componentContext cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ReactExpertInput> perceive(
      @NotNull StepRequest<ReactExpertInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.react request: {}", request.input().componentContext().substring(0, Math.min(50, request.input().componentContext().length())));
    return request;
  }

  /** Rule-based generator for expert.react. */
  public static class ReactExpertGenerator
      implements OutputGenerator<StepRequest<ReactExpertInput>, StepResult<ReactExpertOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ReactExpertGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ReactExpertOutput>> generate(
        @NotNull StepRequest<ReactExpertInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ReactExpertInput stepInput = input.input();

      log.info("Executing expert.react for: {}", stepInput.componentContext());

      ReactExpertOutput output =
          new ReactExpertOutput(
              "expert.react-" + UUID.randomUUID(),
              "Generated recommendation for " + stepInput.componentContext(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.react", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ReactExpertInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ReactExpertGenerator")
          .type("rule-based")
          .description("Expert React/TypeScript engineer for frontend architecture and patterns")
          .version("1.0.0")
          .build();
    }
  }
}
