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
 * Worker agent that generates database migration scripts.
 *
 * @doc.type class
 * @doc.purpose Worker agent that generates database migration scripts
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class MigrationWriterAgent extends YAPPCAgentBase<MigrationWriterInput, MigrationWriterOutput> {

  private static final Logger log = LoggerFactory.getLogger(MigrationWriterAgent.class);

  private final MemoryStore memoryStore;

  public MigrationWriterAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<MigrationWriterInput>, StepResult<MigrationWriterOutput>> generator) {
    super(
        "MigrationWriterAgent",
        "worker.migration-writer",
        new StepContract(
            "worker.migration-writer",
            "#/definitions/MigrationWriterInput",
            "#/definitions/MigrationWriterOutput",
            List.of("database", "migration", "schema"),
            Map.of("description", "Worker agent that generates database migration scripts", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull MigrationWriterInput input) {
    if (input.sourceSchema() == null || input.sourceSchema().isEmpty()) {
      return ValidationResult.fail("sourceSchema cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<MigrationWriterInput> perceive(
      @NotNull StepRequest<MigrationWriterInput> request, @NotNull AgentContext context) {
    log.info("Perceiving worker.migration-writer request: {}", request.input().sourceSchema().substring(0, Math.min(50, request.input().sourceSchema().length())));
    return request;
  }

  /** Rule-based generator for worker.migration-writer. */
  public static class MigrationWriterGenerator
      implements OutputGenerator<StepRequest<MigrationWriterInput>, StepResult<MigrationWriterOutput>> {

    private static final Logger log = LoggerFactory.getLogger(MigrationWriterGenerator.class);

    @Override
    public @NotNull Promise<StepResult<MigrationWriterOutput>> generate(
        @NotNull StepRequest<MigrationWriterInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      MigrationWriterInput stepInput = input.input();

      log.info("Executing worker.migration-writer for: {}", stepInput.sourceSchema());

      MigrationWriterOutput output =
          new MigrationWriterOutput(
              "worker.migration-writer-" + UUID.randomUUID(),
              "Generated upScript for " + stepInput.sourceSchema(),
              "Generated downScript for " + stepInput.sourceSchema(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "worker.migration-writer", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<MigrationWriterInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("MigrationWriterGenerator")
          .type("rule-based")
          .description("Worker agent that generates database migration scripts")
          .version("1.0.0")
          .build();
    }
  }
}
