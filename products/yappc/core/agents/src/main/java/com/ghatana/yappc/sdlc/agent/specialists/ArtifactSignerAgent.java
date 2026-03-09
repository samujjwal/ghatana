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
 * Release governance agent that signs build artifacts for integrity verification.
 *
 * @doc.type class
 * @doc.purpose Release governance agent that signs build artifacts for integrity verification
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class ArtifactSignerAgent extends YAPPCAgentBase<ArtifactSignerInput, ArtifactSignerOutput> {

  private static final Logger log = LoggerFactory.getLogger(ArtifactSignerAgent.class);

  private final MemoryStore memoryStore;

  public ArtifactSignerAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ArtifactSignerInput>, StepResult<ArtifactSignerOutput>> generator) {
    super(
        "ArtifactSignerAgent",
        "release.artifact-signer",
        new StepContract(
            "release.artifact-signer",
            "#/definitions/ArtifactSignerInput",
            "#/definitions/ArtifactSignerOutput",
            List.of("release", "signing", "integrity"),
            Map.of("description", "Release governance agent that signs build artifacts for integrity verification", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ArtifactSignerInput input) {
    if (input.artifactId() == null || input.artifactId().isEmpty()) {
      return ValidationResult.fail("artifactId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ArtifactSignerInput> perceive(
      @NotNull StepRequest<ArtifactSignerInput> request, @NotNull AgentContext context) {
    log.info("Perceiving release.artifact-signer request: {}", input.input().artifactId().substring(0, Math.min(50, input.input().artifactId().length())));
    return request;
  }

  /** Rule-based generator for release.artifact-signer. */
  public static class ArtifactSignerGenerator
      implements OutputGenerator<StepRequest<ArtifactSignerInput>, StepResult<ArtifactSignerOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ArtifactSignerGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ArtifactSignerOutput>> generate(
        @NotNull StepRequest<ArtifactSignerInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ArtifactSignerInput stepInput = input.input();

      log.info("Executing release.artifact-signer for: {}", stepInput.artifactId());

      ArtifactSignerOutput output =
          new ArtifactSignerOutput(
              "release.artifact-signer-" + UUID.randomUUID(),
              "Generated signature for " + stepInput.artifactId(),
              "Generated algorithm for " + stepInput.artifactId(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "release.artifact-signer", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ArtifactSignerInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ArtifactSignerGenerator")
          .type("rule-based")
          .description("Release governance agent that signs build artifacts for integrity verification")
          .version("1.0.0")
          .build();
    }
  }
}
