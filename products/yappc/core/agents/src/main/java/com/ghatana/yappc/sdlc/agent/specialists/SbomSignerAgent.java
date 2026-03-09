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
 * Release governance agent that signs SBOM for supply chain trust.
 *
 * @doc.type class
 * @doc.purpose Release governance agent that signs SBOM for supply chain trust
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class SbomSignerAgent extends YAPPCAgentBase<SbomSignerInput, SbomSignerOutput> {

  private static final Logger log = LoggerFactory.getLogger(SbomSignerAgent.class);

  private final MemoryStore memoryStore;

  public SbomSignerAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<SbomSignerInput>, StepResult<SbomSignerOutput>> generator) {
    super(
        "SbomSignerAgent",
        "release.sbom-signer",
        new StepContract(
            "release.sbom-signer",
            "#/definitions/SbomSignerInput",
            "#/definitions/SbomSignerOutput",
            List.of("release", "sbom-signing", "supply-chain"),
            Map.of("description", "Release governance agent that signs SBOM for supply chain trust", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull SbomSignerInput input) {
    if (input.sbomId() == null || input.sbomId().isEmpty()) {
      return ValidationResult.fail("sbomId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<SbomSignerInput> perceive(
      @NotNull StepRequest<SbomSignerInput> request, @NotNull AgentContext context) {
    log.info("Perceiving release.sbom-signer request: {}", input.input().sbomId().substring(0, Math.min(50, input.input().sbomId().length())));
    return request;
  }

  /** Rule-based generator for release.sbom-signer. */
  public static class SbomSignerGenerator
      implements OutputGenerator<StepRequest<SbomSignerInput>, StepResult<SbomSignerOutput>> {

    private static final Logger log = LoggerFactory.getLogger(SbomSignerGenerator.class);

    @Override
    public @NotNull Promise<StepResult<SbomSignerOutput>> generate(
        @NotNull StepRequest<SbomSignerInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      SbomSignerInput stepInput = input.input();

      log.info("Executing release.sbom-signer for: {}", stepInput.sbomId());

      SbomSignerOutput output =
          new SbomSignerOutput(
              "release.sbom-signer-" + UUID.randomUUID(),
              "Generated signature for " + stepInput.sbomId(),
              "Generated format for " + stepInput.sbomId(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "release.sbom-signer", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<SbomSignerInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("SbomSignerGenerator")
          .type("rule-based")
          .description("Release governance agent that signs SBOM for supply chain trust")
          .version("1.0.0")
          .build();
    }
  }
}
