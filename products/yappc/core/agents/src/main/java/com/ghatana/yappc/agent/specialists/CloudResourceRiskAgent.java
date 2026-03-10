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
 * Worker agent that assesses risk posture of cloud resources.
 *
 * @doc.type class
 * @doc.purpose Worker agent that assesses risk posture of cloud resources
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class CloudResourceRiskAgent extends YAPPCAgentBase<CloudResourceRiskInput, CloudResourceRiskOutput> {

  private static final Logger log = LoggerFactory.getLogger(CloudResourceRiskAgent.class);

  private final MemoryStore memoryStore;

  public CloudResourceRiskAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<CloudResourceRiskInput>, StepResult<CloudResourceRiskOutput>> generator) {
    super(
        "CloudResourceRiskAgent",
        "worker.cloud-resource-risk",
        new StepContract(
            "worker.cloud-resource-risk",
            "#/definitions/CloudResourceRiskInput",
            "#/definitions/CloudResourceRiskOutput",
            List.of("cloud", "risk-assessment", "security"),
            Map.of("description", "Worker agent that assesses risk posture of cloud resources", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull CloudResourceRiskInput input) {
    if (input.resourceId() == null || input.resourceId().isEmpty()) {
      return ValidationResult.fail("resourceId cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<CloudResourceRiskInput> perceive(
      @NotNull StepRequest<CloudResourceRiskInput> request, @NotNull AgentContext context) {
    log.info("Perceiving worker.cloud-resource-risk request: {}", request.input().resourceId().substring(0, Math.min(50, request.input().resourceId().length())));
    return request;
  }

  /** Rule-based generator for worker.cloud-resource-risk. */
  public static class CloudResourceRiskGenerator
      implements OutputGenerator<StepRequest<CloudResourceRiskInput>, StepResult<CloudResourceRiskOutput>> {

    private static final Logger log = LoggerFactory.getLogger(CloudResourceRiskGenerator.class);

    @Override
    public @NotNull Promise<StepResult<CloudResourceRiskOutput>> generate(
        @NotNull StepRequest<CloudResourceRiskInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      CloudResourceRiskInput stepInput = input.input();

      log.info("Executing worker.cloud-resource-risk for: {}", stepInput.resourceId());

      CloudResourceRiskOutput output =
          new CloudResourceRiskOutput(
              "worker.cloud-resource-risk-" + UUID.randomUUID(),
              "Generated riskLevel for " + stepInput.resourceId(),
              List.of(),
              Map.of("generatedAt", start.toString()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("stepId", "worker.cloud-resource-risk", "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<CloudResourceRiskInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("CloudResourceRiskGenerator")
          .type("rule-based")
          .description("Worker agent that assesses risk posture of cloud resources")
          .version("1.0.0")
          .build();
    }
  }
}
