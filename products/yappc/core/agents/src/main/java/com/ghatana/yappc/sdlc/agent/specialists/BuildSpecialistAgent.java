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
 * Specialist agent for build execution.
 *
 * <p>Compiles, tests, and packages implementation into deployable artifacts.
 *
 * @doc.type class
 * @doc.purpose Specialist agent for build execution
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class BuildSpecialistAgent extends YAPPCAgentBase<BuildInput, BuildOutput> {

  private static final Logger log = LoggerFactory.getLogger(BuildSpecialistAgent.class);

  private final MemoryStore memoryStore;

  public BuildSpecialistAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<BuildInput>, StepResult<BuildOutput>> generator) {
    super(
        "BuildSpecialistAgent",
        "implementation.build",
        new StepContract(
            "implementation.build",
            "#/definitions/BuildInput",
            "#/definitions/BuildOutput",
            List.of("implementation", "build", "artifacts"),
            Map.of("description", "Builds and packages implementation", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull BuildInput input) {
    if (input.implementationId() == null || input.implementationId().isEmpty()) {
      return ValidationResult.fail("Implementation ID cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<BuildInput> perceive(
      @NotNull StepRequest<BuildInput> request, @NotNull AgentContext context) {
    log.info(
        "Perceiving build request for implementation: {}, target: {}",
        request.input().implementationId(),
        request.input().buildTarget());
    return request;
  }

  /** Rule-based generator for build execution. */
  public static class BuildGenerator
      implements OutputGenerator<StepRequest<BuildInput>, StepResult<BuildOutput>> {

    private static final Logger log = LoggerFactory.getLogger(BuildGenerator.class);

    @Override
    public @NotNull Promise<StepResult<BuildOutput>> generate(
        @NotNull StepRequest<BuildInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      BuildInput buildInput = input.input();

      log.info(
          "Building implementation: {}, target: {}",
          buildInput.implementationId(),
          buildInput.buildTarget());

      // Simulate build process
      List<String> artifacts =
          List.of(
              "target/app-1.0.0.jar",
              "target/app-1.0.0-sources.jar",
              "target/app-1.0.0-javadoc.jar",
              "docker/app:1.0.0");

      Map<String, Object> buildMetrics =
          Map.of(
              "buildDuration", 120,
              "testsPassed", 156,
              "testsFailed", 0,
              "coverage", 87,
              "warnings", 3,
              "errors", 0);

      boolean success =
          (int) buildMetrics.get("errors") == 0 && (int) buildMetrics.get("testsFailed") == 0;

      String buildId = "build-" + UUID.randomUUID();

      BuildOutput output =
          new BuildOutput(
              buildId,
              success,
              artifacts,
              buildMetrics,
              Map.of(
                  "implementationId",
                  buildInput.implementationId(),
                  "buildTarget",
                  buildInput.buildTarget(),
                  "builtAt",
                  start.toString(),
                  "artifactCount",
                  artifacts.size()));

      return Promise.of(
          StepResult.success(
              output, Map.of("buildId", buildId, "success", success), start, Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<BuildInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0); // Rule-based
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("BuildGenerator")
          .type("rule-based")
          .description("Compiles, tests, and packages implementation")
          .version("1.0.0")
          .build();
    }
  }
}
