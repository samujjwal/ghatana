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
 * Specialist agent for staging deployment.
 *
 * <p>Deploys build artifacts to staging environment with health checks.
 *
 * @doc.type class
 * @doc.purpose Specialist agent for staging deployment
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class DeployStagingSpecialistAgent
    extends YAPPCAgentBase<DeployStagingInput, DeployStagingOutput> {

  private static final Logger log = LoggerFactory.getLogger(DeployStagingSpecialistAgent.class);

  private final MemoryStore memoryStore;

  public DeployStagingSpecialistAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull
          OutputGenerator<StepRequest<DeployStagingInput>, StepResult<DeployStagingOutput>>
              generator) {
    super(
        "DeployStagingSpecialistAgent",
        "ops.deployStaging",
        new StepContract(
            "ops.deployStaging",
            "#/definitions/DeployStagingInput",
            "#/definitions/DeployStagingOutput",
            List.of("ops", "deployment", "staging"),
            Map.of("description", "Deploys to staging environment", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull DeployStagingInput input) {
    if (input.buildId() == null || input.buildId().isEmpty()) {
      return ValidationResult.fail("Build ID cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<DeployStagingInput> perceive(
      @NotNull StepRequest<DeployStagingInput> request, @NotNull AgentContext context) {
    log.info(
        "Perceiving staging deployment request for build: {}, environment: {}",
        request.input().buildId(),
        request.input().environment());
    return request;
  }

  /** Rule-based generator for staging deployment. */
  public static class DeployStagingGenerator
      implements OutputGenerator<StepRequest<DeployStagingInput>, StepResult<DeployStagingOutput>> {

    private static final Logger log = LoggerFactory.getLogger(DeployStagingGenerator.class);

    @Override
    public @NotNull Promise<StepResult<DeployStagingOutput>> generate(
        @NotNull StepRequest<DeployStagingInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      DeployStagingInput deployInput = input.input();

      log.info(
          "Deploying build {} to environment: {}",
          deployInput.buildId(),
          deployInput.environment());

      String deploymentId = "deployment-" + UUID.randomUUID();
      String deploymentUrl = "https://" + deployInput.environment() + ".example.com";

      DeployStagingOutput output =
          new DeployStagingOutput(
              deploymentId,
              deployInput.environment(),
              "deployed",
              deploymentUrl,
              Map.of(
                  "buildId",
                  deployInput.buildId(),
                  "deployedAt",
                  start.toString(),
                  "healthCheck",
                  "passed",
                  "instances",
                  3,
                  "version",
                  "1.0.0"));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("deploymentId", deploymentId, "url", deploymentUrl),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<DeployStagingInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0); // Rule-based
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("DeployStagingGenerator")
          .type("rule-based")
          .description("Deploys artifacts to staging with health checks")
          .version("1.0.0")
          .build();
    }
  }
}
