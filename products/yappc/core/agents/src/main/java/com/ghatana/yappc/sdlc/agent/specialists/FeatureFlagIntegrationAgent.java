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
 * Integration bridge agent for feature flag management systems.
 *
 * @doc.type class
 * @doc.purpose Integration bridge agent for feature flag management systems
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class FeatureFlagIntegrationAgent extends YAPPCAgentBase<FeatureFlagIntegrationInput, FeatureFlagIntegrationOutput> {

  private static final Logger log = LoggerFactory.getLogger(FeatureFlagIntegrationAgent.class);

  private final MemoryStore memoryStore;

  public FeatureFlagIntegrationAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<FeatureFlagIntegrationInput>, StepResult<FeatureFlagIntegrationOutput>> generator) {
    super(
        "FeatureFlagIntegrationAgent",
        "integration.feature-flag",
        new StepContract(
            "integration.feature-flag",
            "#/definitions/FeatureFlagIntegrationInput",
            "#/definitions/FeatureFlagIntegrationOutput",
            List.of("integration", "feature-flags"),
            Map.of("description", "Integration bridge agent for feature flag management systems", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull FeatureFlagIntegrationInput input) {
    if (input.flagServiceId() == null || input.flagServiceId().isEmpty()) {
      return ValidationResult.fail("flagServiceId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<FeatureFlagIntegrationInput> perceive(
      @NotNull StepRequest<FeatureFlagIntegrationInput> request, @NotNull AgentContext context) {
    log.info("Perceiving integration.feature-flag request: {}", input.input().flagServiceId().substring(0, Math.min(50, input.input().flagServiceId().length())));
    return request;
  }

  /** Rule-based generator for integration.feature-flag. */
  public static class FeatureFlagIntegrationGenerator
      implements OutputGenerator<StepRequest<FeatureFlagIntegrationInput>, StepResult<FeatureFlagIntegrationOutput>> {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagIntegrationGenerator.class);

    @Override
    public @NotNull Promise<StepResult<FeatureFlagIntegrationOutput>> generate(
        @NotNull StepRequest<FeatureFlagIntegrationInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      FeatureFlagIntegrationInput stepInput = input.input();

      log.info("Executing integration.feature-flag for: {}", stepInput.flagServiceId());

      FeatureFlagIntegrationOutput output =
          new FeatureFlagIntegrationOutput(
              "integration.feature-flag-" + UUID.randomUUID(),
              "Generated result for " + stepInput.flagServiceId(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "integration.feature-flag", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<FeatureFlagIntegrationInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("FeatureFlagIntegrationGenerator")
          .type("rule-based")
          .description("Integration bridge agent for feature flag management systems")
          .version("1.0.0")
          .build();
    }
  }
}
