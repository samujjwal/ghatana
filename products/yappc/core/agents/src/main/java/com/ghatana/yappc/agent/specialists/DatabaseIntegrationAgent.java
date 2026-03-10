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
 * Integration bridge agent for database connectivity and operations.
 *
 * @doc.type class
 * @doc.purpose Integration bridge agent for database connectivity and operations
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class DatabaseIntegrationAgent extends YAPPCAgentBase<DatabaseIntegrationInput, DatabaseIntegrationOutput> {

  private static final Logger log = LoggerFactory.getLogger(DatabaseIntegrationAgent.class);

  private final MemoryStore memoryStore;

  public DatabaseIntegrationAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<DatabaseIntegrationInput>, StepResult<DatabaseIntegrationOutput>> generator) {
    super(
        "DatabaseIntegrationAgent",
        "integration.database",
        new StepContract(
            "integration.database",
            "#/definitions/DatabaseIntegrationInput",
            "#/definitions/DatabaseIntegrationOutput",
            List.of("integration", "database"),
            Map.of("description", "Integration bridge agent for database connectivity and operations", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull DatabaseIntegrationInput input) {
    if (input.connectionId() == null || input.connectionId().isEmpty()) {
      return ValidationResult.fail("connectionId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<DatabaseIntegrationInput> perceive(
      @NotNull StepRequest<DatabaseIntegrationInput> request, @NotNull AgentContext context) {
    log.info("Perceiving integration.database request: {}", request.input().connectionId().substring(0, Math.min(50, request.input().connectionId().length())));
    return request;
  }

  /** Rule-based generator for integration.database. */
  public static class DatabaseIntegrationGenerator
      implements OutputGenerator<StepRequest<DatabaseIntegrationInput>, StepResult<DatabaseIntegrationOutput>> {

    private static final Logger log = LoggerFactory.getLogger(DatabaseIntegrationGenerator.class);

    @Override
    public @NotNull Promise<StepResult<DatabaseIntegrationOutput>> generate(
        @NotNull StepRequest<DatabaseIntegrationInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      DatabaseIntegrationInput stepInput = input.input();

      log.info("Executing integration.database for: {}", stepInput.connectionId());

      DatabaseIntegrationOutput output =
          new DatabaseIntegrationOutput(
              "integration.database-" + UUID.randomUUID(),
              "Generated result for " + stepInput.connectionId(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "integration.database", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<DatabaseIntegrationInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("DatabaseIntegrationGenerator")
          .type("rule-based")
          .description("Integration bridge agent for database connectivity and operations")
          .version("1.0.0")
          .build();
    }
  }
}
