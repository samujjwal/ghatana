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
 * Worker agent that generates integration tests for services and APIs.
 *
 * @doc.type class
 * @doc.purpose Worker agent that generates integration tests for services and APIs
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class IntegrationTestWriterAgent extends YAPPCAgentBase<IntegrationTestWriterInput, IntegrationTestWriterOutput> {

  private static final Logger log = LoggerFactory.getLogger(IntegrationTestWriterAgent.class);

  private final MemoryStore memoryStore;

  public IntegrationTestWriterAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<IntegrationTestWriterInput>, StepResult<IntegrationTestWriterOutput>> generator) {
    super(
        "IntegrationTestWriterAgent",
        "worker.integration-test-writer",
        new StepContract(
            "worker.integration-test-writer",
            "#/definitions/IntegrationTestWriterInput",
            "#/definitions/IntegrationTestWriterOutput",
            List.of("testing", "integration-test", "code-generation"),
            Map.of("description", "Worker agent that generates integration tests for services and APIs", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull IntegrationTestWriterInput input) {
    if (input.serviceId() == null || input.serviceId().isEmpty()) {
      return ValidationResult.fail("serviceId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<IntegrationTestWriterInput> perceive(
      @NotNull StepRequest<IntegrationTestWriterInput> request, @NotNull AgentContext context) {
    log.info("Perceiving worker.integration-test-writer request: {}", request.input().serviceId().substring(0, Math.min(50, request.input().serviceId().length())));
    return request;
  }

  /** Rule-based generator for worker.integration-test-writer. */
  public static class IntegrationTestWriterGenerator
      implements OutputGenerator<StepRequest<IntegrationTestWriterInput>, StepResult<IntegrationTestWriterOutput>> {

    private static final Logger log = LoggerFactory.getLogger(IntegrationTestWriterGenerator.class);

    @Override
    public @NotNull Promise<StepResult<IntegrationTestWriterOutput>> generate(
        @NotNull StepRequest<IntegrationTestWriterInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      IntegrationTestWriterInput stepInput = input.input();

      log.info("Executing worker.integration-test-writer for: {}", stepInput.serviceId());

      IntegrationTestWriterOutput output =
          new IntegrationTestWriterOutput(
              "Generated testCode for " + stepInput.serviceId(),
              0,
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "worker.integration-test-writer", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<IntegrationTestWriterInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("IntegrationTestWriterGenerator")
          .type("rule-based")
          .description("Worker agent that generates integration tests for services and APIs")
          .version("1.0.0")
          .build();
    }
  }
}
