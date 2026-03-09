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
 * Specialist agent for API contract derivation.
 *
 * <p>Generates API contracts, event schemas, and protocol definitions from architecture.
 *
 * @doc.type class
 * @doc.purpose Specialist agent for contract derivation
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class DeriveContractsSpecialistAgent
    extends YAPPCAgentBase<DeriveContractsInput, DeriveContractsOutput> {

  private static final Logger log = LoggerFactory.getLogger(DeriveContractsSpecialistAgent.class);

  private final MemoryStore memoryStore;

  public DeriveContractsSpecialistAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull
          OutputGenerator<StepRequest<DeriveContractsInput>, StepResult<DeriveContractsOutput>>
              generator) {
    super(
        "DeriveContractsSpecialistAgent",
        "architecture.deriveContracts",
        new StepContract(
            "architecture.deriveContracts",
            "#/definitions/DeriveContractsInput",
            "#/definitions/DeriveContractsOutput",
            List.of("architecture", "contracts", "api"),
            Map.of("description", "Derives API contracts from architecture", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull DeriveContractsInput input) {
    if (input.architectureId() == null || input.architectureId().isEmpty()) {
      return ValidationResult.fail("Architecture ID cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<DeriveContractsInput> perceive(
      @NotNull StepRequest<DeriveContractsInput> request, @NotNull AgentContext context) {
    log.info(
        "Perceiving contract derivation request for architecture: {}",
        request.input().architectureId());
    return request;
  }

  /** Rule-based generator for contract derivation. */
  public static class DeriveContractsGenerator
      implements OutputGenerator<
          StepRequest<DeriveContractsInput>, StepResult<DeriveContractsOutput>> {

    private static final Logger log = LoggerFactory.getLogger(DeriveContractsGenerator.class);

    @Override
    public @NotNull Promise<StepResult<DeriveContractsOutput>> generate(
        @NotNull StepRequest<DeriveContractsInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      DeriveContractsInput contractInput = input.input();

      log.info("Generating API contracts for architecture: {}", contractInput.architectureId());

      // Generate API endpoints
      List<String> apiEndpoints =
          List.of(
              "POST /api/v1/users",
              "GET /api/v1/users/{id}",
              "PUT /api/v1/users/{id}",
              "DELETE /api/v1/users/{id}",
              "GET /api/v1/users",
              "POST /api/v1/orders",
              "GET /api/v1/orders/{id}",
              "GET /api/v1/orders");

      // Generate event schemas
      List<String> eventSchemas =
          List.of(
              "UserCreated.avsc",
              "UserUpdated.avsc",
              "UserDeleted.avsc",
              "OrderCreated.avsc",
              "OrderConfirmed.avsc",
              "OrderShipped.avsc");

      Map<String, String> protocols =
          Map.of(
              "rest", "OpenAPI 3.0",
              "events", "CloudEvents 1.0",
              "serialization", "JSON/Avro",
              "authentication", "OAuth 2.0 + JWT");

      String contractId = "contract-" + UUID.randomUUID();

      DeriveContractsOutput output =
          new DeriveContractsOutput(
              contractId,
              apiEndpoints,
              eventSchemas,
              protocols,
              Map.of(
                  "architectureId",
                  contractInput.architectureId(),
                  "generatedAt",
                  start.toString(),
                  "endpointCount",
                  apiEndpoints.size(),
                  "schemaCount",
                  eventSchemas.size()));

      return Promise.of(
          StepResult.success(output, Map.of("contractId", contractId), start, Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<DeriveContractsInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0); // Rule-based
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("DeriveContractsGenerator")
          .type("rule-based")
          .description("Derives API contracts and event schemas from architecture")
          .version("1.0.0")
          .build();
    }
  }
}
