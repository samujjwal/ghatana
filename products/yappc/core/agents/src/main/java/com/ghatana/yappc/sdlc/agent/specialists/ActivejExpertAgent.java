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
 * Stack expert agent for ActiveJ framework patterns and best practices.
 *
 * @doc.type class
 * @doc.purpose Stack expert agent for ActiveJ framework patterns and best practices
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class ActivejExpertAgent extends YAPPCAgentBase<ActivejExpertInput, ActivejExpertOutput> {

  private static final Logger log = LoggerFactory.getLogger(ActivejExpertAgent.class);

  private final MemoryStore memoryStore;

  public ActivejExpertAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ActivejExpertInput>, StepResult<ActivejExpertOutput>> generator) {
    super(
        "ActivejExpertAgent",
        "expert.activej",
        new StepContract(
            "expert.activej",
            "#/definitions/ActivejExpertInput",
            "#/definitions/ActivejExpertOutput",
            List.of("activej", "async", "eventloop"),
            Map.of("description", "Stack expert agent for ActiveJ framework patterns and best practices", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ActivejExpertInput input) {
    if (input.codeContext() == null || input.codeContext().isEmpty()) {
      return ValidationResult.fail("codeContext cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ActivejExpertInput> perceive(
      @NotNull StepRequest<ActivejExpertInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.activej request: {}", input.input().codeContext().substring(0, Math.min(50, input.input().codeContext().length())));
    return request;
  }

  /** Rule-based generator for expert.activej. */
  public static class ActivejExpertGenerator
      implements OutputGenerator<StepRequest<ActivejExpertInput>, StepResult<ActivejExpertOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ActivejExpertGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ActivejExpertOutput>> generate(
        @NotNull StepRequest<ActivejExpertInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ActivejExpertInput stepInput = input.input();

      log.info("Executing expert.activej for: {}", stepInput.codeContext());

      ActivejExpertOutput output =
          new ActivejExpertOutput(
              "expert.activej-" + UUID.randomUUID(),
              "Generated recommendation for " + stepInput.codeContext(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.activej", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ActivejExpertInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ActivejExpertGenerator")
          .type("rule-based")
          .description("Stack expert agent for ActiveJ framework patterns and best practices")
          .version("1.0.0")
          .build();
    }
  }
}
