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
 * Worker agent that automates project onboarding and setup.
 *
 * @doc.type class
 * @doc.purpose Worker agent that automates project onboarding and setup
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class ProjectOnboardingAgent extends YAPPCAgentBase<ProjectOnboardingInput, ProjectOnboardingOutput> {

  private static final Logger log = LoggerFactory.getLogger(ProjectOnboardingAgent.class);

  private final MemoryStore memoryStore;

  public ProjectOnboardingAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<ProjectOnboardingInput>, StepResult<ProjectOnboardingOutput>> generator) {
    super(
        "ProjectOnboardingAgent",
        "worker.project-onboarding",
        new StepContract(
            "worker.project-onboarding",
            "#/definitions/ProjectOnboardingInput",
            "#/definitions/ProjectOnboardingOutput",
            List.of("onboarding", "project-setup"),
            Map.of("description", "Worker agent that automates project onboarding and setup", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull ProjectOnboardingInput input) {
    if (input.projectName() == null || input.projectName().isEmpty()) {
      return ValidationResult.fail("projectName cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<ProjectOnboardingInput> perceive(
      @NotNull StepRequest<ProjectOnboardingInput> request, @NotNull AgentContext context) {
    log.info("Perceiving worker.project-onboarding request: {}", input.input().projectName().substring(0, Math.min(50, input.input().projectName().length())));
    return request;
  }

  /** Rule-based generator for worker.project-onboarding. */
  public static class ProjectOnboardingGenerator
      implements OutputGenerator<StepRequest<ProjectOnboardingInput>, StepResult<ProjectOnboardingOutput>> {

    private static final Logger log = LoggerFactory.getLogger(ProjectOnboardingGenerator.class);

    @Override
    public @NotNull Promise<StepResult<ProjectOnboardingOutput>> generate(
        @NotNull StepRequest<ProjectOnboardingInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      ProjectOnboardingInput stepInput = input.input();

      log.info("Executing worker.project-onboarding for: {}", stepInput.projectName());

      ProjectOnboardingOutput output =
          new ProjectOnboardingOutput(
              "worker.project-onboarding-" + UUID.randomUUID(),
              List.of(),
              "Generated setupStatus for " + stepInput.projectName(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "worker.project-onboarding", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<ProjectOnboardingInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("ProjectOnboardingGenerator")
          .type("rule-based")
          .description("Worker agent that automates project onboarding and setup")
          .version("1.0.0")
          .build();
    }
  }
}
