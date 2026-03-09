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
 * Stack expert worker agent for React component patterns and hooks.
 *
 * @doc.type class
 * @doc.purpose Stack expert worker agent for React component patterns and hooks
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class ReactExpertWorkerAgent extends YAPPCAgentBase<ReactExpertWorkerInput, ReactExpertWorkerOutput> {

  private static final Logger log = LoggerFactory.getLogger(ReactExpertWorkerAgent.class);

  private final MemoryStore memoryStore;

  public ReactExpertWorkerAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ReactExpertWorkerInput>, StepResult<ReactExpertWorkerOutput>> generator) {
    super(
        "ReactExpertWorkerAgent",
        "expert.react-worker",
        new StepContract(
            "expert.react-worker",
            "#/definitions/ReactExpertWorkerInput",
            "#/definitions/ReactExpertWorkerOutput",
            List.of("react", "hooks", "components"),
            Map.of("description", "Stack expert worker agent for React component patterns and hooks", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ReactExpertWorkerInput input) {
    if (input.codeContext() == null || input.codeContext().isEmpty()) {
      return ValidationResult.fail("codeContext cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ReactExpertWorkerInput> perceive(
      @NotNull StepRequest<ReactExpertWorkerInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.react-worker request: {}", input.input().codeContext().substring(0, Math.min(50, input.input().codeContext().length())));
    return request;
  }

  /** Rule-based generator for expert.react-worker. */
  public static class ReactExpertWorkerGenerator
      implements OutputGenerator<StepRequest<ReactExpertWorkerInput>, StepResult<ReactExpertWorkerOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ReactExpertWorkerGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ReactExpertWorkerOutput>> generate(
        @NotNull StepRequest<ReactExpertWorkerInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ReactExpertWorkerInput stepInput = input.input();

      log.info("Executing expert.react-worker for: {}", stepInput.codeContext());

      ReactExpertWorkerOutput output =
          new ReactExpertWorkerOutput(
              "expert.react-worker-" + UUID.randomUUID(),
              "Generated recommendation for " + stepInput.codeContext(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.react-worker", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ReactExpertWorkerInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ReactExpertWorkerGenerator")
          .type("rule-based")
          .description("Stack expert worker agent for React component patterns and hooks")
          .version("1.0.0")
          .build();
    }
  }
}
