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
 * Worker agent that generates Java classes from specifications.
 *
 * @doc.type class
 * @doc.purpose Worker agent that generates Java classes from specifications
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class JavaClassWriterAgent extends YAPPCAgentBase<JavaClassWriterInput, JavaClassWriterOutput> {

  private static final Logger log = LoggerFactory.getLogger(JavaClassWriterAgent.class);

  private final MemoryStore memoryStore;

  public JavaClassWriterAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<JavaClassWriterInput>, StepResult<JavaClassWriterOutput>> generator) {
    super(
        "JavaClassWriterAgent",
        "worker.java-class-writer",
        new StepContract(
            "worker.java-class-writer",
            "#/definitions/JavaClassWriterInput",
            "#/definitions/JavaClassWriterOutput",
            List.of("code-generation", "java"),
            Map.of("description", "Worker agent that generates Java classes from specifications", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull JavaClassWriterInput input) {
    if (input.className() == null || input.className().isEmpty()) {
      return ValidationResult.fail("className cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<JavaClassWriterInput> perceive(
      @NotNull StepRequest<JavaClassWriterInput> request, @NotNull AgentContext context) {
    log.info("Perceiving worker.java-class-writer request: {}", input.input().className().substring(0, Math.min(50, input.input().className().length())));
    return request;
  }

  /** Rule-based generator for worker.java-class-writer. */
  public static class JavaClassWriterGenerator
      implements OutputGenerator<StepRequest<JavaClassWriterInput>, StepResult<JavaClassWriterOutput>> {

    private static final Logger log = LoggerFactory.getLogger(JavaClassWriterGenerator.class);

    @Override
    public @NotNull Promise<StepResult<JavaClassWriterOutput>> generate(
        @NotNull StepRequest<JavaClassWriterInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      JavaClassWriterInput stepInput = input.input();

      log.info("Executing worker.java-class-writer for: {}", stepInput.className());

      JavaClassWriterOutput output =
          new JavaClassWriterOutput(
              "Generated generatedCode for " + stepInput.className(),
              "Generated filePath for " + stepInput.className(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "worker.java-class-writer", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<JavaClassWriterInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("JavaClassWriterGenerator")
          .type("rule-based")
          .description("Worker agent that generates Java classes from specifications")
          .version("1.0.0")
          .build();
    }
  }
}
