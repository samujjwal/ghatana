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
 * Worker agent that gathers contextual information for decision making.
 *
 * @doc.type class
 * @doc.purpose Worker agent that gathers contextual information for decision making
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle perceive
 */
public class ContextGatheringAgent extends YAPPCAgentBase<ContextGatheringInput, ContextGatheringOutput> {

  private static final Logger log = LoggerFactory.getLogger(ContextGatheringAgent.class);

  private final MemoryStore memoryStore;

  public ContextGatheringAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ContextGatheringInput>, StepResult<ContextGatheringOutput>> generator) {
    super(
        "ContextGatheringAgent",
        "worker.context-gathering",
        new StepContract(
            "worker.context-gathering",
            "#/definitions/ContextGatheringInput",
            "#/definitions/ContextGatheringOutput",
            List.of("context", "information-gathering"),
            Map.of("description", "Worker agent that gathers contextual information for decision making", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ContextGatheringInput input) {
    if (input.queryId() == null || input.queryId().isEmpty()) {
      return ValidationResult.fail("queryId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ContextGatheringInput> perceive(
      @NotNull StepRequest<ContextGatheringInput> request, @NotNull AgentContext context) {
    log.info("Perceiving worker.context-gathering request: {}", request.input().queryId().substring(0, Math.min(50, request.input().queryId().length())));
    return request;
  }

  /** Rule-based generator for worker.context-gathering. */
  public static class ContextGatheringGenerator
      implements OutputGenerator<StepRequest<ContextGatheringInput>, StepResult<ContextGatheringOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ContextGatheringGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ContextGatheringOutput>> generate(
        @NotNull StepRequest<ContextGatheringInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ContextGatheringInput stepInput = input.input();

      log.info("Executing worker.context-gathering for: {}", stepInput.queryId());

      ContextGatheringOutput output =
          new ContextGatheringOutput(
              "worker.context-gathering-" + UUID.randomUUID(),
              Map.of("generatedAt", start.toString()),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "worker.context-gathering", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ContextGatheringInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ContextGatheringGenerator")
          .type("rule-based")
          .description("Worker agent that gathers contextual information for decision making")
          .version("1.0.0")
          .build();
    }
  }
}
