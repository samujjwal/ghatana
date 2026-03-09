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
 * Worker agent that generates API handlers from OpenAPI/GraphQL specs.
 *
 * @doc.type class
 * @doc.purpose Worker agent that generates API handlers from OpenAPI/GraphQL specs
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class ApiHandlerGeneratorAgent extends YAPPCAgentBase<ApiHandlerGeneratorInput, ApiHandlerGeneratorOutput> {

  private static final Logger log = LoggerFactory.getLogger(ApiHandlerGeneratorAgent.class);

  private final MemoryStore memoryStore;

  public ApiHandlerGeneratorAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ApiHandlerGeneratorInput>, StepResult<ApiHandlerGeneratorOutput>> generator) {
    super(
        "ApiHandlerGeneratorAgent",
        "worker.api-handler-generator",
        new StepContract(
            "worker.api-handler-generator",
            "#/definitions/ApiHandlerGeneratorInput",
            "#/definitions/ApiHandlerGeneratorOutput",
            List.of("code-generation", "api", "handler"),
            Map.of("description", "Worker agent that generates API handlers from OpenAPI/GraphQL specs", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ApiHandlerGeneratorInput input) {
    if (input.apiSpec() == null || input.apiSpec().isEmpty()) {
      return ValidationResult.fail("apiSpec cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ApiHandlerGeneratorInput> perceive(
      @NotNull StepRequest<ApiHandlerGeneratorInput> request, @NotNull AgentContext context) {
    log.info("Perceiving worker.api-handler-generator request: {}", input.input().apiSpec().substring(0, Math.min(50, input.input().apiSpec().length())));
    return request;
  }

  /** Rule-based generator for worker.api-handler-generator. */
  public static class ApiHandlerGeneratorGenerator
      implements OutputGenerator<StepRequest<ApiHandlerGeneratorInput>, StepResult<ApiHandlerGeneratorOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ApiHandlerGeneratorGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ApiHandlerGeneratorOutput>> generate(
        @NotNull StepRequest<ApiHandlerGeneratorInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ApiHandlerGeneratorInput stepInput = input.input();

      log.info("Executing worker.api-handler-generator for: {}", stepInput.apiSpec());

      ApiHandlerGeneratorOutput output =
          new ApiHandlerGeneratorOutput(
              "Generated generatedCode for " + stepInput.apiSpec(),
              List.of(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "worker.api-handler-generator", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ApiHandlerGeneratorInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ApiHandlerGeneratorGenerator")
          .type("rule-based")
          .description("Worker agent that generates API handlers from OpenAPI/GraphQL specs")
          .version("1.0.0")
          .build();
    }
  }
}
