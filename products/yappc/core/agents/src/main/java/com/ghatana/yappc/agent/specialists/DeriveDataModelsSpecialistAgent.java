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
 * Specialist agent for data model derivation.
 *
 * <p>Generates entity models, relationships, and storage strategies from architecture.
 *
 * @doc.type class
 * @doc.purpose Specialist agent for data model derivation
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class DeriveDataModelsSpecialistAgent
    extends YAPPCAgentBase<DeriveDataModelsInput, DeriveDataModelsOutput> {

  private static final Logger log = LoggerFactory.getLogger(DeriveDataModelsSpecialistAgent.class);

  private final MemoryStore memoryStore;

  public DeriveDataModelsSpecialistAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull
          OutputGenerator<StepRequest<DeriveDataModelsInput>, StepResult<DeriveDataModelsOutput>>
              generator) {
    super(
        "DeriveDataModelsSpecialistAgent",
        "architecture.deriveDataModels",
        new StepContract(
            "architecture.deriveDataModels",
            "#/definitions/DeriveDataModelsInput",
            "#/definitions/DeriveDataModelsOutput",
            List.of("architecture", "data", "models"),
            Map.of("description", "Derives data models from architecture", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull DeriveDataModelsInput input) {
    if (input.architectureId() == null || input.architectureId().isEmpty()) {
      return ValidationResult.fail("Architecture ID cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<DeriveDataModelsInput> perceive(
      @NotNull StepRequest<DeriveDataModelsInput> request, @NotNull AgentContext context) {
    log.info(
        "Perceiving data model derivation request for architecture: {}",
        request.input().architectureId());
    return request;
  }

  /** Rule-based generator for data model derivation. */
  public static class DeriveDataModelsGenerator
      implements OutputGenerator<
          StepRequest<DeriveDataModelsInput>, StepResult<DeriveDataModelsOutput>> {

    private static final Logger log = LoggerFactory.getLogger(DeriveDataModelsGenerator.class);

    @Override
    public @NotNull Promise<StepResult<DeriveDataModelsOutput>> generate(
        @NotNull StepRequest<DeriveDataModelsInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      DeriveDataModelsInput modelInput = input.input();

      log.info("Generating data models for architecture: {}", modelInput.architectureId());

      // Generate entities
      List<String> entities =
          List.of(
              "User", "Order", "Product", "Payment", "Shipment", "Inventory", "Category", "Review");

      // Generate relationships
      Map<String, List<String>> relationships =
          Map.of(
              "User", List.of("hasMany:Order", "hasMany:Review"),
              "Order",
                  List.of("belongsTo:User", "hasMany:Product", "hasOne:Payment", "hasOne:Shipment"),
              "Product", List.of("belongsTo:Category", "hasMany:Review"),
              "Category", List.of("hasMany:Product"),
              "Review", List.of("belongsTo:User", "belongsTo:Product"));

      // Generate storage strategies
      Map<String, String> storageStrategies =
          Map.of(
              "User", "PostgreSQL (ACID required)",
              "Order", "PostgreSQL (transactions)",
              "Product", "PostgreSQL + Redis cache",
              "Payment", "PostgreSQL (audit log)",
              "Shipment", "PostgreSQL",
              "Inventory", "Redis (real-time)",
              "Category", "PostgreSQL + cache",
              "Review", "MongoDB (flexible schema)");

      String modelId = "model-" + UUID.randomUUID();

      DeriveDataModelsOutput output =
          new DeriveDataModelsOutput(
              modelId,
              entities,
              relationships,
              storageStrategies,
              Map.of(
                  "architectureId",
                  modelInput.architectureId(),
                  "generatedAt",
                  start.toString(),
                  "entityCount",
                  entities.size(),
                  "relationshipCount",
                  relationships.values().stream().mapToInt(List::size).sum()));

      return Promise.of(
          StepResult.success(output, Map.of("modelId", modelId), start, Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<DeriveDataModelsInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0); // Rule-based
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("DeriveDataModelsGenerator")
          .type("rule-based")
          .description("Derives entity models and storage strategies from architecture")
          .version("1.0.0")
          .build();
    }
  }
}
