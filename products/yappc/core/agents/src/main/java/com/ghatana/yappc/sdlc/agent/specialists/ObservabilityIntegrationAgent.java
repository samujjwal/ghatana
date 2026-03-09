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
 * Integration bridge agent for observability platform data.
 *
 * @doc.type class
 * @doc.purpose Integration bridge agent for observability platform data
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle perceive
 */
public class ObservabilityIntegrationAgent extends YAPPCAgentBase<ObservabilityIntegrationInput, ObservabilityIntegrationOutput> {

  private static final Logger log = LoggerFactory.getLogger(ObservabilityIntegrationAgent.class);

  private final MemoryStore memoryStore;

  public ObservabilityIntegrationAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ObservabilityIntegrationInput>, StepResult<ObservabilityIntegrationOutput>> generator) {
    super(
        "ObservabilityIntegrationAgent",
        "integration.observability",
        new StepContract(
            "integration.observability",
            "#/definitions/ObservabilityIntegrationInput",
            "#/definitions/ObservabilityIntegrationOutput",
            List.of("integration", "observability", "metrics"),
            Map.of("description", "Integration bridge agent for observability platform data", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ObservabilityIntegrationInput input) {
    if (input.platformId() == null || input.platformId().isEmpty()) {
      return ValidationResult.fail("platformId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ObservabilityIntegrationInput> perceive(
      @NotNull StepRequest<ObservabilityIntegrationInput> request, @NotNull AgentContext context) {
    log.info("Perceiving integration.observability request: {}", input.input().platformId().substring(0, Math.min(50, input.input().platformId().length())));
    return request;
  }

  /** Rule-based generator for integration.observability. */
  public static class ObservabilityIntegrationGenerator
      implements OutputGenerator<StepRequest<ObservabilityIntegrationInput>, StepResult<ObservabilityIntegrationOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityIntegrationGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ObservabilityIntegrationOutput>> generate(
        @NotNull StepRequest<ObservabilityIntegrationInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ObservabilityIntegrationInput stepInput = input.input();

      log.info("Executing integration.observability for: {}", stepInput.platformId());

      ObservabilityIntegrationOutput output =
          new ObservabilityIntegrationOutput(
              "integration.observability-" + UUID.randomUUID(),
              Map.of("generatedAt", start.toString()),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "integration.observability", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ObservabilityIntegrationInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ObservabilityIntegrationGenerator")
          .type("rule-based")
          .description("Integration bridge agent for observability platform data")
          .version("1.0.0")
          .build();
    }
  }
}
