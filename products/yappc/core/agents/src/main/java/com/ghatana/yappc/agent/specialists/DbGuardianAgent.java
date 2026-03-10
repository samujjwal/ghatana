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
 * Expert database guardian for schema design, migration and query optimization.
 *
 * @doc.type class
 * @doc.purpose Expert database guardian for schema design, migration and query optimization
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class DbGuardianAgent extends YAPPCAgentBase<DbGuardianInput, DbGuardianOutput> {

  private static final Logger log = LoggerFactory.getLogger(DbGuardianAgent.class);

  private final MemoryStore memoryStore;

  public DbGuardianAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<DbGuardianInput>, StepResult<DbGuardianOutput>> generator) {
    super(
        "DbGuardianAgent",
        "expert.db-guardian",
        new StepContract(
            "expert.db-guardian",
            "#/definitions/DbGuardianInput",
            "#/definitions/DbGuardianOutput",
            List.of("database", "schema", "migration", "optimization"),
            Map.of("description", "Expert database guardian for schema design, migration and query optimization", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull DbGuardianInput input) {
    if (input.databaseId() == null || input.databaseId().isEmpty()) {
      return ValidationResult.fail("databaseId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<DbGuardianInput> perceive(
      @NotNull StepRequest<DbGuardianInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.db-guardian request: {}", request.input().databaseId().substring(0, Math.min(50, request.input().databaseId().length())));
    return request;
  }

  /** Rule-based generator for expert.db-guardian. */
  public static class DbGuardianGenerator
      implements OutputGenerator<StepRequest<DbGuardianInput>, StepResult<DbGuardianOutput>> {

    private static final Logger log = LoggerFactory.getLogger(DbGuardianGenerator.class);

    @Override
    public @NotNull Promise<StepResult<DbGuardianOutput>> generate(
        @NotNull StepRequest<DbGuardianInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      DbGuardianInput stepInput = input.input();

      log.info("Executing expert.db-guardian for: {}", stepInput.databaseId());

      DbGuardianOutput output =
          new DbGuardianOutput(
              "expert.db-guardian-" + UUID.randomUUID(),
              "Generated assessment for " + stepInput.databaseId(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.db-guardian", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<DbGuardianInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("DbGuardianGenerator")
          .type("rule-based")
          .description("Expert database guardian for schema design, migration and query optimization")
          .version("1.0.0")
          .build();
    }
  }
}
