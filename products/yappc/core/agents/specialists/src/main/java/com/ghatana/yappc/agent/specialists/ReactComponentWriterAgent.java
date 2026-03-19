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
 * Worker agent that generates React/TypeScript components from specifications.
 *
 * @doc.type class
 * @doc.purpose Worker agent that generates React/TypeScript components from specifications
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class ReactComponentWriterAgent extends YAPPCAgentBase<ReactComponentWriterInput, ReactComponentWriterOutput> {

  private static final Logger log = LoggerFactory.getLogger(ReactComponentWriterAgent.class);

  private final MemoryStore memoryStore;

  public ReactComponentWriterAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ReactComponentWriterInput>, StepResult<ReactComponentWriterOutput>> generator) {
    super(
        "ReactComponentWriterAgent",
        "worker.react-component-writer",
        new StepContract(
            "worker.react-component-writer",
            "#/definitions/ReactComponentWriterInput",
            "#/definitions/ReactComponentWriterOutput",
            List.of("code-generation", "react", "typescript"),
            Map.of("description", "Worker agent that generates React/TypeScript components from specifications", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ReactComponentWriterInput input) {
    if (input.componentName() == null || input.componentName().isEmpty()) {
      return ValidationResult.fail("componentName cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ReactComponentWriterInput> perceive(
      @NotNull StepRequest<ReactComponentWriterInput> request, @NotNull AgentContext context) {
    log.info("Perceiving worker.react-component-writer request: {}", request.input().componentName().substring(0, Math.min(50, request.input().componentName().length())));
    return request;
  }

  /** Rule-based generator for worker.react-component-writer. */
  public static class ReactComponentWriterGenerator
      implements OutputGenerator<StepRequest<ReactComponentWriterInput>, StepResult<ReactComponentWriterOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ReactComponentWriterGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ReactComponentWriterOutput>> generate(
        @NotNull StepRequest<ReactComponentWriterInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ReactComponentWriterInput stepInput = input.input();

      log.info("Executing worker.react-component-writer for: {}", stepInput.componentName());

      ReactComponentWriterOutput output =
          new ReactComponentWriterOutput(
              "Generated generatedCode for " + stepInput.componentName(),
              "Generated filePath for " + stepInput.componentName(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "worker.react-component-writer", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ReactComponentWriterInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ReactComponentWriterGenerator")
          .type("rule-based")
          .description("Worker agent that generates React/TypeScript components from specifications")
          .version("1.0.0")
          .build();
    }
  }
}
