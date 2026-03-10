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
 * Debug micro-agent that generates code fixes for identified bugs.
 *
 * @doc.type class
 * @doc.purpose Debug micro-agent that generates code fixes for identified bugs
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class FixGeneratorAgent extends YAPPCAgentBase<FixGeneratorInput, FixGeneratorOutput> {

  private static final Logger log = LoggerFactory.getLogger(FixGeneratorAgent.class);

  private final MemoryStore memoryStore;

  public FixGeneratorAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<FixGeneratorInput>, StepResult<FixGeneratorOutput>> generator) {
    super(
        "FixGeneratorAgent",
        "debug.fix-generator",
        new StepContract(
            "debug.fix-generator",
            "#/definitions/FixGeneratorInput",
            "#/definitions/FixGeneratorOutput",
            List.of("debug", "fix-generation", "code-generation"),
            Map.of("description", "Debug micro-agent that generates code fixes for identified bugs", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull FixGeneratorInput input) {
    if (input.bugId() == null || input.bugId().isEmpty()) {
      return ValidationResult.fail("bugId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<FixGeneratorInput> perceive(
      @NotNull StepRequest<FixGeneratorInput> request, @NotNull AgentContext context) {
    log.info("Perceiving debug.fix-generator request: {}", request.input().bugId().substring(0, Math.min(50, request.input().bugId().length())));
    return request;
  }

  /** Rule-based generator for debug.fix-generator. */
  public static class FixGeneratorGenerator
      implements OutputGenerator<StepRequest<FixGeneratorInput>, StepResult<FixGeneratorOutput>> {

    private static final Logger log = LoggerFactory.getLogger(FixGeneratorGenerator.class);

    @Override
    public @NotNull Promise<StepResult<FixGeneratorOutput>> generate(
        @NotNull StepRequest<FixGeneratorInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      FixGeneratorInput stepInput = input.input();

      log.info("Executing debug.fix-generator for: {}", stepInput.bugId());

      FixGeneratorOutput output =
          new FixGeneratorOutput(
              "debug.fix-generator-" + UUID.randomUUID(),
              "Generated patchCode for " + stepInput.bugId(),
              "Generated explanation for " + stepInput.bugId(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "debug.fix-generator", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<FixGeneratorInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("FixGeneratorGenerator")
          .type("rule-based")
          .description("Debug micro-agent that generates code fixes for identified bugs")
          .version("1.0.0")
          .build();
    }
  }
}
