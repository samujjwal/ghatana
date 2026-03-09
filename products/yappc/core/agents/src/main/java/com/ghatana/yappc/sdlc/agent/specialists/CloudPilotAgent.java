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
 * Expert cloud pilot for cloud architecture and resource management.
 *
 * @doc.type class
 * @doc.purpose Expert cloud pilot for cloud architecture and resource management
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle reason
 */
public class CloudPilotAgent extends YAPPCAgentBase<CloudPilotInput, CloudPilotOutput> {

  private static final Logger log = LoggerFactory.getLogger(CloudPilotAgent.class);

  private final MemoryStore memoryStore;

  public CloudPilotAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<CloudPilotInput>, StepResult<CloudPilotOutput>> generator) {
    super(
        "CloudPilotAgent",
        "expert.cloud-pilot",
        new StepContract(
            "expert.cloud-pilot",
            "#/definitions/CloudPilotInput",
            "#/definitions/CloudPilotOutput",
            List.of("cloud", "infrastructure", "cost"),
            Map.of("description", "Expert cloud pilot for cloud architecture and resource management", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull CloudPilotInput input) {
    if (input.environmentId() == null || input.environmentId().isEmpty()) {
      return ValidationResult.fail("environmentId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<CloudPilotInput> perceive(
      @NotNull StepRequest<CloudPilotInput> request, @NotNull AgentContext context) {
    log.info("Perceiving expert.cloud-pilot request: {}", input.input().environmentId().substring(0, Math.min(50, input.input().environmentId().length())));
    return request;
  }

  /** Rule-based generator for expert.cloud-pilot. */
  public static class CloudPilotGenerator
      implements OutputGenerator<StepRequest<CloudPilotInput>, StepResult<CloudPilotOutput>> {

    private static final Logger log = LoggerFactory.getLogger(CloudPilotGenerator.class);

    @Override
    public @NotNull Promise<StepResult<CloudPilotOutput>> generate(
        @NotNull StepRequest<CloudPilotInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      CloudPilotInput stepInput = input.input();

      log.info("Executing expert.cloud-pilot for: {}", stepInput.environmentId());

      CloudPilotOutput output =
          new CloudPilotOutput(
              "expert.cloud-pilot-" + UUID.randomUUID(),
              "Generated recommendation for " + stepInput.environmentId(),
              Map.of("generatedAt", start.toString()),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "expert.cloud-pilot", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<CloudPilotInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("CloudPilotGenerator")
          .type("rule-based")
          .description("Expert cloud pilot for cloud architecture and resource management")
          .version("1.0.0")
          .build();
    }
  }
}
