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
 * Expert test strategist for test planning and quality assurance strategy.
 *
 * @doc.type class
 * @doc.purpose Expert test strategist for test planning and quality assurance strategy
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class TestStrategistAgent extends YAPPCAgentBase<TestStrategistInput, TestStrategistOutput> {

  private static final Logger log = LoggerFactory.getLogger(TestStrategistAgent.class);

  private final MemoryStore memoryStore;

  public TestStrategistAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<TestStrategistInput>, StepResult<TestStrategistOutput>> generator) {
    super(
        "TestStrategistAgent",
        "expert.test-strategist",
        new StepContract(
            "expert.test-strategist",
            "#/definitions/TestStrategistInput",
            "#/definitions/TestStrategistOutput",
            List.of("testing", "quality", "strategy"),
            Map.of("description", "Expert test strategist for test planning and quality assurance strategy", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull TestStrategistInput input) {
    if (input.projectId() == null || input.projectId().isEmpty()) {
      return ValidationResult.fail("projectId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<TestStrategistInput> perceive(
      @NotNull StepRequest<TestStrategistInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.test-strategist request: {}", input.input().projectId().substring(0, Math.min(50, input.input().projectId().length())));
    return request;
  }

  /** Rule-based generator for expert.test-strategist. */
  public static class TestStrategistGenerator
      implements OutputGenerator<StepRequest<TestStrategistInput>, StepResult<TestStrategistOutput>> {

    private static final Logger log = LoggerFactory.getLogger(TestStrategistGenerator.class);

    @Override
    public @NotNull Promise<StepResult<TestStrategistOutput>> generate(
        @NotNull StepRequest<TestStrategistInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      TestStrategistInput stepInput = input.input();

      log.info("Executing expert.test-strategist for: {}", stepInput.projectId());

      TestStrategistOutput output =
          new TestStrategistOutput(
              "expert.test-strategist-" + UUID.randomUUID(),
              "Generated testPlan for " + stepInput.projectId(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.test-strategist", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<TestStrategistInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("TestStrategistGenerator")
          .type("rule-based")
          .description("Expert test strategist for test planning and quality assurance strategy")
          .version("1.0.0")
          .build();
    }
  }
}
