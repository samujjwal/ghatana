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
 * Debug micro-agent that generates regression tests for fixed bugs.
 *
 * @doc.type class
 * @doc.purpose Debug micro-agent that generates regression tests for fixed bugs
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class RegressionTestWriterAgent extends YAPPCAgentBase<RegressionTestWriterInput, RegressionTestWriterOutput> {

  private static final Logger log = LoggerFactory.getLogger(RegressionTestWriterAgent.class);

  private final MemoryStore memoryStore;

  public RegressionTestWriterAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<RegressionTestWriterInput>, StepResult<RegressionTestWriterOutput>> generator) {
    super(
        "RegressionTestWriterAgent",
        "debug.regression-test-writer",
        new StepContract(
            "debug.regression-test-writer",
            "#/definitions/RegressionTestWriterInput",
            "#/definitions/RegressionTestWriterOutput",
            List.of("debug", "regression-testing", "code-generation"),
            Map.of("description", "Debug micro-agent that generates regression tests for fixed bugs", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull RegressionTestWriterInput input) {
    if (input.bugId() == null || input.bugId().isEmpty()) {
      return ValidationResult.fail("bugId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<RegressionTestWriterInput> perceive(
      @NotNull StepRequest<RegressionTestWriterInput> request, @NotNull AgentContext context) {
    log.info("Perceiving debug.regression-test-writer request: {}", request.input().bugId().substring(0, Math.min(50, request.input().bugId().length())));
    return request;
  }

  /** Rule-based generator for debug.regression-test-writer. */
  public static class RegressionTestWriterGenerator
      implements OutputGenerator<StepRequest<RegressionTestWriterInput>, StepResult<RegressionTestWriterOutput>> {

    private static final Logger log = LoggerFactory.getLogger(RegressionTestWriterGenerator.class);

    @Override
    public @NotNull Promise<StepResult<RegressionTestWriterOutput>> generate(
        @NotNull StepRequest<RegressionTestWriterInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      RegressionTestWriterInput stepInput = input.input();

      log.info("Executing debug.regression-test-writer for: {}", stepInput.bugId());

      RegressionTestWriterOutput output =
          new RegressionTestWriterOutput(
              "debug.regression-test-writer-" + UUID.randomUUID(),
              "Generated testCode for " + stepInput.bugId(),
              0,
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "debug.regression-test-writer", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<RegressionTestWriterInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("RegressionTestWriterGenerator")
          .type("rule-based")
          .description("Debug micro-agent that generates regression tests for fixed bugs")
          .version("1.0.0")
          .build();
    }
  }
}
