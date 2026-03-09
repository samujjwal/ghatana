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
 * Integration bridge agent for artifact repository operations.
 *
 * @doc.type class
 * @doc.purpose Integration bridge agent for artifact repository operations
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class ArtifactStoreIntegrationAgent extends YAPPCAgentBase<ArtifactStoreIntegrationInput, ArtifactStoreIntegrationOutput> {

  private static final Logger log = LoggerFactory.getLogger(ArtifactStoreIntegrationAgent.class);

  private final MemoryStore memoryStore;

  public ArtifactStoreIntegrationAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ArtifactStoreIntegrationInput>, StepResult<ArtifactStoreIntegrationOutput>> generator) {
    super(
        "ArtifactStoreIntegrationAgent",
        "integration.artifact-store",
        new StepContract(
            "integration.artifact-store",
            "#/definitions/ArtifactStoreIntegrationInput",
            "#/definitions/ArtifactStoreIntegrationOutput",
            List.of("integration", "artifact-store"),
            Map.of("description", "Integration bridge agent for artifact repository operations", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ArtifactStoreIntegrationInput input) {
    if (input.storeId() == null || input.storeId().isEmpty()) {
      return ValidationResult.fail("storeId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ArtifactStoreIntegrationInput> perceive(
      @NotNull StepRequest<ArtifactStoreIntegrationInput> request, @NotNull AgentContext context) {
    log.info("Perceiving integration.artifact-store request: {}", input.input().storeId().substring(0, Math.min(50, input.input().storeId().length())));
    return request;
  }

  /** Rule-based generator for integration.artifact-store. */
  public static class ArtifactStoreIntegrationGenerator
      implements OutputGenerator<StepRequest<ArtifactStoreIntegrationInput>, StepResult<ArtifactStoreIntegrationOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ArtifactStoreIntegrationGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ArtifactStoreIntegrationOutput>> generate(
        @NotNull StepRequest<ArtifactStoreIntegrationInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ArtifactStoreIntegrationInput stepInput = input.input();

      log.info("Executing integration.artifact-store for: {}", stepInput.storeId());

      ArtifactStoreIntegrationOutput output =
          new ArtifactStoreIntegrationOutput(
              "integration.artifact-store-" + UUID.randomUUID(),
              "Generated result for " + stepInput.storeId(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "integration.artifact-store", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ArtifactStoreIntegrationInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ArtifactStoreIntegrationGenerator")
          .type("rule-based")
          .description("Integration bridge agent for artifact repository operations")
          .version("1.0.0")
          .build();
    }
  }
}
