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
 * Expert API designer for REST, GraphQL and contract-first design.
 *
 * @doc.type class
 * @doc.purpose Expert API designer for REST, GraphQL and contract-first design
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class ApiDesignerAgent extends YAPPCAgentBase<ApiDesignerInput, ApiDesignerOutput> {

  private static final Logger log = LoggerFactory.getLogger(ApiDesignerAgent.class);

  private final MemoryStore memoryStore;

  public ApiDesignerAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ApiDesignerInput>, StepResult<ApiDesignerOutput>> generator) {
    super(
        "ApiDesignerAgent",
        "expert.api-designer",
        new StepContract(
            "expert.api-designer",
            "#/definitions/ApiDesignerInput",
            "#/definitions/ApiDesignerOutput",
            List.of("api-design", "openapi", "graphql"),
            Map.of("description", "Expert API designer for REST, GraphQL and contract-first design", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ApiDesignerInput input) {
    if (input.serviceId() == null || input.serviceId().isEmpty()) {
      return ValidationResult.fail("serviceId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ApiDesignerInput> perceive(
      @NotNull StepRequest<ApiDesignerInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.api-designer request: {}", request.input().serviceId().substring(0, Math.min(50, request.input().serviceId().length())));
    return request;
  }

  /** Rule-based generator for expert.api-designer. */
  public static class ApiDesignerGenerator
      implements OutputGenerator<StepRequest<ApiDesignerInput>, StepResult<ApiDesignerOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ApiDesignerGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ApiDesignerOutput>> generate(
        @NotNull StepRequest<ApiDesignerInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ApiDesignerInput stepInput = input.input();

      log.info("Executing expert.api-designer for: {}", stepInput.serviceId());

      ApiDesignerOutput output =
          new ApiDesignerOutput(
              "expert.api-designer-" + UUID.randomUUID(),
              "Generated apiContract for " + stepInput.serviceId(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.api-designer", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ApiDesignerInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ApiDesignerGenerator")
          .type("rule-based")
          .description("Expert API designer for REST, GraphQL and contract-first design")
          .version("1.0.0")
          .build();
    }
  }
}
