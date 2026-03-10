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
 * Worker agent that executes end-to-end test suites and reports results.
 *
 * @doc.type class
 * @doc.purpose Worker agent that executes end-to-end test suites and reports results
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class E2eTestRunnerAgent extends YAPPCAgentBase<E2eTestRunnerInput, E2eTestRunnerOutput> {

  private static final Logger log = LoggerFactory.getLogger(E2eTestRunnerAgent.class);

  private final MemoryStore memoryStore;

  public E2eTestRunnerAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<E2eTestRunnerInput>, StepResult<E2eTestRunnerOutput>> generator) {
    super(
        "E2eTestRunnerAgent",
        "worker.e2e-test-runner",
        new StepContract(
            "worker.e2e-test-runner",
            "#/definitions/E2eTestRunnerInput",
            "#/definitions/E2eTestRunnerOutput",
            List.of("testing", "e2e", "execution"),
            Map.of("description", "Worker agent that executes end-to-end test suites and reports results", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull E2eTestRunnerInput input) {
    if (input.testSuiteId() == null || input.testSuiteId().isEmpty()) {
      return ValidationResult.fail("testSuiteId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<E2eTestRunnerInput> perceive(
      @NotNull StepRequest<E2eTestRunnerInput> request, @NotNull AgentContext context) {
    log.info("Perceiving worker.e2e-test-runner request: {}", request.input().testSuiteId().substring(0, Math.min(50, request.input().testSuiteId().length())));
    return request;
  }

  /** Rule-based generator for worker.e2e-test-runner. */
  public static class E2eTestRunnerGenerator
      implements OutputGenerator<StepRequest<E2eTestRunnerInput>, StepResult<E2eTestRunnerOutput>> {

    private static final Logger log = LoggerFactory.getLogger(E2eTestRunnerGenerator.class);

    @Override
    public @NotNull Promise<StepResult<E2eTestRunnerOutput>> generate(
        @NotNull StepRequest<E2eTestRunnerInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      E2eTestRunnerInput stepInput = input.input();

      log.info("Executing worker.e2e-test-runner for: {}", stepInput.testSuiteId());

      E2eTestRunnerOutput output =
          new E2eTestRunnerOutput(
              "worker.e2e-test-runner-" + UUID.randomUUID(),
              0,
              0,
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "worker.e2e-test-runner", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<E2eTestRunnerInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("E2eTestRunnerGenerator")
          .type("rule-based")
          .description("Worker agent that executes end-to-end test suites and reports results")
          .version("1.0.0")
          .build();
    }
  }
}
