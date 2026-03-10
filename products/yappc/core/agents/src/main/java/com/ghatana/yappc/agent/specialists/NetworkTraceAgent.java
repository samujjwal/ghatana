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
 * Debug micro-agent that analyzes network traces for latency and connectivity issues.
 *
 * @doc.type class
 * @doc.purpose Debug micro-agent that analyzes network traces for latency and connectivity issues
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle perceive
 */
public class NetworkTraceAgent extends YAPPCAgentBase<NetworkTraceInput, NetworkTraceOutput> {

  private static final Logger log = LoggerFactory.getLogger(NetworkTraceAgent.class);

  private final MemoryStore memoryStore;

  public NetworkTraceAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<NetworkTraceInput>, StepResult<NetworkTraceOutput>> generator) {
    super(
        "NetworkTraceAgent",
        "debug.network-trace",
        new StepContract(
            "debug.network-trace",
            "#/definitions/NetworkTraceInput",
            "#/definitions/NetworkTraceOutput",
            List.of("debug", "network-analysis"),
            Map.of("description", "Debug micro-agent that analyzes network traces for latency and connectivity issues", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull NetworkTraceInput input) {
    if (input.traceId() == null || input.traceId().isEmpty()) {
      return ValidationResult.fail("traceId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<NetworkTraceInput> perceive(
      @NotNull StepRequest<NetworkTraceInput> request, @NotNull AgentContext context) {
    log.info("Perceiving debug.network-trace request: {}", request.input().traceId().substring(0, Math.min(50, request.input().traceId().length())));
    return request;
  }

  /** Rule-based generator for debug.network-trace. */
  public static class NetworkTraceGenerator
      implements OutputGenerator<StepRequest<NetworkTraceInput>, StepResult<NetworkTraceOutput>> {

    private static final Logger log = LoggerFactory.getLogger(NetworkTraceGenerator.class);

    @Override
    public @NotNull Promise<StepResult<NetworkTraceOutput>> generate(
        @NotNull StepRequest<NetworkTraceInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      NetworkTraceInput stepInput = input.input();

      log.info("Executing debug.network-trace for: {}", stepInput.traceId());

      NetworkTraceOutput output =
          new NetworkTraceOutput(
              "debug.network-trace-" + UUID.randomUUID(),
              List.of(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "debug.network-trace", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<NetworkTraceInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("NetworkTraceGenerator")
          .type("rule-based")
          .description("Debug micro-agent that analyzes network traces for latency and connectivity issues")
          .version("1.0.0")
          .build();
    }
  }
}
