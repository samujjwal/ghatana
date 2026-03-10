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
 * Specialist agent for code scaffolding.
 *
 * <p>Generates initial codebase structure from architecture and plan.
 *
 * @doc.type class
 * @doc.purpose Specialist agent for code scaffolding
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class ScaffoldSpecialistAgent extends YAPPCAgentBase<ScaffoldInput, ScaffoldOutput> {

  private static final Logger log = LoggerFactory.getLogger(ScaffoldSpecialistAgent.class);

  private final MemoryStore memoryStore;

  public ScaffoldSpecialistAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ScaffoldInput>, StepResult<ScaffoldOutput>> generator) {
    super(
        "ScaffoldSpecialistAgent",
        "implementation.scaffold",
        new StepContract(
            "implementation.scaffold",
            "#/definitions/ScaffoldInput",
            "#/definitions/ScaffoldOutput",
            List.of("implementation", "codegen", "scaffolding"),
            Map.of("description", "Generates initial codebase structure", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ScaffoldInput input) {
    if (input.architectureId() == null || input.architectureId().isEmpty()) {
      return ValidationResult.fail("Architecture ID cannot be empty");
    }
    if (input.planId() == null || input.planId().isEmpty()) {
      return ValidationResult.fail("Plan ID cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ScaffoldInput> perceive(
      @NotNull StepRequest<ScaffoldInput> request, @NotNull AgentContext context) {
    log.info(
        "Perceiving scaffold request for architecture: {}, plan: {}",
        request.input().architectureId(),
        request.input().planId());
    return request;
  }

  /** Rule-based generator for scaffolding. */
  public static class ScaffoldGenerator
      implements OutputGenerator<StepRequest<ScaffoldInput>, StepResult<ScaffoldOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ScaffoldGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ScaffoldOutput>> generate(
        @NotNull StepRequest<ScaffoldInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ScaffoldInput scaffoldInput = input.input();

      log.info(
          "Generating scaffold for architecture: {}, plan: {}",
          scaffoldInput.architectureId(),
          scaffoldInput.planId());

      // Generate file structure (simplified)
      List<String> generatedFiles =
          List.of(
              "src/main/java/App.java",
              "src/main/java/config/Config.java",
              "src/main/java/service/ApiService.java",
              "src/main/java/model/Domain.java",
              "src/main/resources/application.properties",
              "src/test/java/AppTest.java",
              "build.gradle.kts",
              "README.md",
              "Dockerfile",
              ".gitignore");

      Map<String, Integer> filesByType =
          Map.of(
              "java", 4,
              "properties", 1,
              "gradle", 1,
              "markdown", 1,
              "docker", 1,
              "config", 1);

      String scaffoldId = "scaffold-" + UUID.randomUUID();

      ScaffoldOutput output =
          new ScaffoldOutput(
              scaffoldId,
              generatedFiles,
              filesByType,
              Map.of(
                  "architectureId",
                  scaffoldInput.architectureId(),
                  "planId",
                  scaffoldInput.planId(),
                  "totalFiles",
                  generatedFiles.size()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("scaffoldId", scaffoldId, "fileCount", generatedFiles.size()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ScaffoldInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0); // Rule-based
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ScaffoldGenerator")
          .type("rule-based")
          .description("Generates initial codebase structure from architecture")
          .version("1.0.0")
          .build();
    }
  }
}
