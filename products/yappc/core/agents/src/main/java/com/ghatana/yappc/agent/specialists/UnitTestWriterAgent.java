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
 * Worker agent that generates unit tests for source code.
 *
 * @doc.type class
 * @doc.purpose Worker agent that generates unit tests for source code
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class UnitTestWriterAgent extends YAPPCAgentBase<UnitTestWriterInput, UnitTestWriterOutput> {

  private static final Logger log = LoggerFactory.getLogger(UnitTestWriterAgent.class);

  private final MemoryStore memoryStore;

  public UnitTestWriterAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<UnitTestWriterInput>, StepResult<UnitTestWriterOutput>> generator) {
    super(
        "UnitTestWriterAgent",
        "worker.unit-test-writer",
        new StepContract(
            "worker.unit-test-writer",
            "#/definitions/UnitTestWriterInput",
            "#/definitions/UnitTestWriterOutput",
            List.of("testing", "unit-test", "code-generation"),
            Map.of("description", "Worker agent that generates unit tests for source code", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull UnitTestWriterInput input) {
    if (input.sourceCode() == null || input.sourceCode().isEmpty()) {
      return ValidationResult.fail("sourceCode cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<UnitTestWriterInput> perceive(
      @NotNull StepRequest<UnitTestWriterInput> request, @NotNull AgentContext context) {
    log.info("Perceiving worker.unit-test-writer request: {}", request.input().sourceCode().substring(0, Math.min(50, request.input().sourceCode().length())));
    return request;
  }

  /** Rule-based generator for worker.unit-test-writer. */
  public static class UnitTestWriterGenerator
      implements OutputGenerator<StepRequest<UnitTestWriterInput>, StepResult<UnitTestWriterOutput>> {

    private static final Logger log = LoggerFactory.getLogger(UnitTestWriterGenerator.class);

    @Override
    public @NotNull Promise<StepResult<UnitTestWriterOutput>> generate(
        @NotNull StepRequest<UnitTestWriterInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      UnitTestWriterInput stepInput = input.input();

      log.info("Executing worker.unit-test-writer for: {}", stepInput.sourceCode());

      UnitTestWriterOutput output =
          new UnitTestWriterOutput(
              "Generated testCode for " + stepInput.sourceCode(),
              0,
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "worker.unit-test-writer", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<UnitTestWriterInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("UnitTestWriterGenerator")
          .type("rule-based")
          .description("Worker agent that generates unit tests for source code")
          .version("1.0.0")
          .build();
    }
  }
}
