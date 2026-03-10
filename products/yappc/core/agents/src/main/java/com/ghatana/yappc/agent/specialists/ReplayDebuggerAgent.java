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
 * Debug micro-agent that replays request flows for debugging.
 *
 * @doc.type class
 * @doc.purpose Debug micro-agent that replays request flows for debugging
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class ReplayDebuggerAgent extends YAPPCAgentBase<ReplayDebuggerInput, ReplayDebuggerOutput> {

  private static final Logger log = LoggerFactory.getLogger(ReplayDebuggerAgent.class);

  private final MemoryStore memoryStore;

  public ReplayDebuggerAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ReplayDebuggerInput>, StepResult<ReplayDebuggerOutput>> generator) {
    super(
        "ReplayDebuggerAgent",
        "debug.replay-debugger",
        new StepContract(
            "debug.replay-debugger",
            "#/definitions/ReplayDebuggerInput",
            "#/definitions/ReplayDebuggerOutput",
            List.of("debug", "replay", "request-tracing"),
            Map.of("description", "Debug micro-agent that replays request flows for debugging", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ReplayDebuggerInput input) {
    if (input.requestId() == null || input.requestId().isEmpty()) {
      return ValidationResult.fail("requestId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ReplayDebuggerInput> perceive(
      @NotNull StepRequest<ReplayDebuggerInput> request, @NotNull AgentContext context) {
    log.info("Perceiving debug.replay-debugger request: {}", request.input().requestId().substring(0, Math.min(50, request.input().requestId().length())));
    return request;
  }

  /** Rule-based generator for debug.replay-debugger. */
  public static class ReplayDebuggerGenerator
      implements OutputGenerator<StepRequest<ReplayDebuggerInput>, StepResult<ReplayDebuggerOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ReplayDebuggerGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ReplayDebuggerOutput>> generate(
        @NotNull StepRequest<ReplayDebuggerInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ReplayDebuggerInput stepInput = input.input();

      log.info("Executing debug.replay-debugger for: {}", stepInput.requestId());

      ReplayDebuggerOutput output =
          new ReplayDebuggerOutput(
              "debug.replay-debugger-" + UUID.randomUUID(),
              List.of(),
              Map.of("generatedAt", start.toString()),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "debug.replay-debugger", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ReplayDebuggerInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ReplayDebuggerGenerator")
          .type("rule-based")
          .description("Debug micro-agent that replays request flows for debugging")
          .version("1.0.0")
          .build();
    }
  }
}
