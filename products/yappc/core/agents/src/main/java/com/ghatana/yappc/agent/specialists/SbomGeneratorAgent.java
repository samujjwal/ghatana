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
 * Release governance agent that generates Software Bill of Materials.
 *
 * @doc.type class
 * @doc.purpose Release governance agent that generates Software Bill of Materials
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class SbomGeneratorAgent extends YAPPCAgentBase<SbomGeneratorInput, SbomGeneratorOutput> {

  private static final Logger log = LoggerFactory.getLogger(SbomGeneratorAgent.class);

  private final MemoryStore memoryStore;

  public SbomGeneratorAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<SbomGeneratorInput>, StepResult<SbomGeneratorOutput>> generator) {
    super(
        "SbomGeneratorAgent",
        "release.sbom-generator",
        new StepContract(
            "release.sbom-generator",
            "#/definitions/SbomGeneratorInput",
            "#/definitions/SbomGeneratorOutput",
            List.of("release", "sbom", "supply-chain"),
            Map.of("description", "Release governance agent that generates Software Bill of Materials", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull SbomGeneratorInput input) {
    if (input.projectId() == null || input.projectId().isEmpty()) {
      return ValidationResult.fail("projectId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<SbomGeneratorInput> perceive(
      @NotNull StepRequest<SbomGeneratorInput> request, @NotNull AgentContext context) {
    log.info("Perceiving release.sbom-generator request: {}", request.input().projectId().substring(0, Math.min(50, request.input().projectId().length())));
    return request;
  }

  /** Rule-based generator for release.sbom-generator. */
  public static class SbomGeneratorGenerator
      implements OutputGenerator<StepRequest<SbomGeneratorInput>, StepResult<SbomGeneratorOutput>> {

    private static final Logger log = LoggerFactory.getLogger(SbomGeneratorGenerator.class);

    @Override
    public @NotNull Promise<StepResult<SbomGeneratorOutput>> generate(
        @NotNull StepRequest<SbomGeneratorInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      SbomGeneratorInput stepInput = input.input();

      log.info("Executing release.sbom-generator for: {}", stepInput.projectId());

      SbomGeneratorOutput output =
          new SbomGeneratorOutput(
              "release.sbom-generator-" + UUID.randomUUID(),
              "Generated sbomContent for " + stepInput.projectId(),
              "Generated format for " + stepInput.projectId(),
              0,
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "release.sbom-generator", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<SbomGeneratorInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("SbomGeneratorGenerator")
          .type("rule-based")
          .description("Release governance agent that generates Software Bill of Materials")
          .version("1.0.0")
          .build();
    }
  }
}
