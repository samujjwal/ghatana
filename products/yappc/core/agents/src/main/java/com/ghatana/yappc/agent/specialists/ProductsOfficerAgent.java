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
 * Strategic products officer managing portfolio vision and priorities.
 *
 * @doc.type class
 * @doc.purpose Strategic products officer managing portfolio vision and priorities
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class ProductsOfficerAgent extends YAPPCAgentBase<ProductsOfficerInput, ProductsOfficerOutput> {

  private static final Logger log = LoggerFactory.getLogger(ProductsOfficerAgent.class);

  private final MemoryStore memoryStore;

  public ProductsOfficerAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ProductsOfficerInput>, StepResult<ProductsOfficerOutput>> generator) {
    super(
        "ProductsOfficerAgent",
        "strategic.products-officer",
        new StepContract(
            "strategic.products-officer",
            "#/definitions/ProductsOfficerInput",
            "#/definitions/ProductsOfficerOutput",
            List.of("strategic", "product-management"),
            Map.of("description", "Strategic products officer managing portfolio vision and priorities", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ProductsOfficerInput input) {
    if (input.portfolioId() == null || input.portfolioId().isEmpty()) {
      return ValidationResult.fail("portfolioId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ProductsOfficerInput> perceive(
      @NotNull StepRequest<ProductsOfficerInput> request, @NotNull AgentContext context) {
    log.info("Perceiving strategic.products-officer request: {}", request.input().portfolioId().substring(0, Math.min(50, request.input().portfolioId().length())));
    return request;
  }

  /** Rule-based generator for strategic.products-officer. */
  public static class ProductsOfficerGenerator
      implements OutputGenerator<StepRequest<ProductsOfficerInput>, StepResult<ProductsOfficerOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ProductsOfficerGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ProductsOfficerOutput>> generate(
        @NotNull StepRequest<ProductsOfficerInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ProductsOfficerInput stepInput = input.input();

      log.info("Executing strategic.products-officer for: {}", stepInput.portfolioId());

      ProductsOfficerOutput output =
          new ProductsOfficerOutput(
              "strategic.products-officer-" + UUID.randomUUID(),
              List.of(),
              Map.of("generatedAt", start.toString()),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "strategic.products-officer", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ProductsOfficerInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ProductsOfficerGenerator")
          .type("rule-based")
          .description("Strategic products officer managing portfolio vision and priorities")
          .version("1.0.0")
          .build();
    }
  }
}
