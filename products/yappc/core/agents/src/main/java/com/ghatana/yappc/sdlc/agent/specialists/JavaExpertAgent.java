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
 * Expert Java engineer for architecture and implementation guidance.
 *
 * @doc.type class
 * @doc.purpose Expert Java engineer for architecture and implementation guidance
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class JavaExpertAgent extends YAPPCAgentBase<JavaExpertInput, JavaExpertOutput> {

  private static final Logger log = LoggerFactory.getLogger(JavaExpertAgent.class);

  private final MemoryStore memoryStore;

  public JavaExpertAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<JavaExpertInput>, StepResult<JavaExpertOutput>> generator) {
    super(
        "JavaExpertAgent",
        "expert.java",
        new StepContract(
            "expert.java",
            "#/definitions/JavaExpertInput",
            "#/definitions/JavaExpertOutput",
            List.of("java", "architecture", "code-review"),
            Map.of("description", "Expert Java engineer for architecture and implementation guidance", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull JavaExpertInput input) {
    if (input.codeContext() == null || input.codeContext().isEmpty()) {
      return ValidationResult.fail("codeContext cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<JavaExpertInput> perceive(
      @NotNull StepRequest<JavaExpertInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.java request: {}", input.input().codeContext().substring(0, Math.min(50, input.input().codeContext().length())));
    return request;
  }

  /** Rule-based generator for expert.java. */
  public static class JavaExpertGenerator
      implements OutputGenerator<StepRequest<JavaExpertInput>, StepResult<JavaExpertOutput>> {

    private static final Logger log = LoggerFactory.getLogger(JavaExpertGenerator.class);

    @Override
    public @NotNull Promise<StepResult<JavaExpertOutput>> generate(
        @NotNull StepRequest<JavaExpertInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      JavaExpertInput stepInput = input.input();

      log.info("Executing expert.java for: {}", stepInput.codeContext());

      JavaExpertOutput output =
          new JavaExpertOutput(
              "expert.java-" + UUID.randomUUID(),
              "Generated recommendation for " + stepInput.codeContext(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.java", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<JavaExpertInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("JavaExpertGenerator")
          .type("rule-based")
          .description("Expert Java engineer for architecture and implementation guidance")
          .version("1.0.0")
          .build();
    }
  }
}
