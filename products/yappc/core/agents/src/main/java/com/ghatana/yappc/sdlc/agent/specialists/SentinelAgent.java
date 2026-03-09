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
 * Expert security sentinel monitoring threats and enforcing security posture.
 *
 * @doc.type class
 * @doc.purpose Expert security sentinel monitoring threats and enforcing security posture
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle perceive
 */
public class SentinelAgent extends YAPPCAgentBase<SentinelInput, SentinelOutput> {

  private static final Logger log = LoggerFactory.getLogger(SentinelAgent.class);

  private final MemoryStore memoryStore;

  public SentinelAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<SentinelInput>, StepResult<SentinelOutput>> generator) {
    super(
        "SentinelAgent",
        "expert.sentinel",
        new StepContract(
            "expert.sentinel",
            "#/definitions/SentinelInput",
            "#/definitions/SentinelOutput",
            List.of("security", "threat-detection", "compliance"),
            Map.of("description", "Expert security sentinel monitoring threats and enforcing security posture", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull SentinelInput input) {
    if (input.scanTargetId() == null || input.scanTargetId().isEmpty()) {
      return ValidationResult.fail("scanTargetId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<SentinelInput> perceive(
      @NotNull StepRequest<SentinelInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.sentinel request: {}", input.input().scanTargetId().substring(0, Math.min(50, input.input().scanTargetId().length())));
    return request;
  }

  /** Rule-based generator for expert.sentinel. */
  public static class SentinelGenerator
      implements OutputGenerator<StepRequest<SentinelInput>, StepResult<SentinelOutput>> {

    private static final Logger log = LoggerFactory.getLogger(SentinelGenerator.class);

    @Override
    public @NotNull Promise<StepResult<SentinelOutput>> generate(
        @NotNull StepRequest<SentinelInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      SentinelInput stepInput = input.input();

      log.info("Executing expert.sentinel for: {}", stepInput.scanTargetId());

      SentinelOutput output =
          new SentinelOutput(
              "expert.sentinel-" + UUID.randomUUID(),
              List.of(),
              "Generated riskLevel for " + stepInput.scanTargetId(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.sentinel", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<SentinelInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("SentinelGenerator")
          .type("rule-based")
          .description("Expert security sentinel monitoring threats and enforcing security posture")
          .version("1.0.0")
          .build();
    }
  }
}
