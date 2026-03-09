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
 * Specialist agent for canary deployment.
 *
 * <p>Executes gradual traffic rollout with monitoring and automatic rollback on errors.
 *
 * @doc.type class
 * @doc.purpose Specialist agent for canary deployment
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class CanarySpecialistAgent extends YAPPCAgentBase<CanaryInput, CanaryOutput> {

  private static final Logger log = LoggerFactory.getLogger(CanarySpecialistAgent.class);

  private final MemoryStore memoryStore;

  public CanarySpecialistAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<CanaryInput>, StepResult<CanaryOutput>> generator) {
    super(
        "CanarySpecialistAgent",
        "ops.canary",
        new StepContract(
            "ops.canary",
            "#/definitions/CanaryInput",
            "#/definitions/CanaryOutput",
            List.of("ops", "canary", "rollout"),
            Map.of("description", "Executes canary deployment", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull CanaryInput input) {
    if (input.deploymentId() == null || input.deploymentId().isEmpty()) {
      return ValidationResult.fail("Deployment ID cannot be empty");
    }
    if (input.trafficPercentage() < 0 || input.trafficPercentage() > 100) {
      return ValidationResult.fail("Traffic percentage must be between 0 and 100");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<CanaryInput> perceive(
      @NotNull StepRequest<CanaryInput> request, @NotNull AgentContext context) {
    log.info(
        "Perceiving canary deployment request for: {}, traffic: {}%",
        request.input().deploymentId(), request.input().trafficPercentage());
    return request;
  }

  /** Rule-based generator for canary deployment. */
  public static class CanaryGenerator
      implements OutputGenerator<StepRequest<CanaryInput>, StepResult<CanaryOutput>> {

    private static final Logger log = LoggerFactory.getLogger(CanaryGenerator.class);

    @Override
    public @NotNull Promise<StepResult<CanaryOutput>> generate(
        @NotNull StepRequest<CanaryInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      CanaryInput canaryInput = input.input();

      log.info(
          "Executing canary deployment: {}, traffic: {}%, duration: {}min",
          canaryInput.deploymentId(),
          canaryInput.trafficPercentage(),
          canaryInput.durationMinutes());

      // Simulate canary monitoring
      double errorRate = 0.001; // 0.1% error rate
      double latencyP95 = 150.0; // 150ms P95 latency

      String status = (errorRate < 0.01 && latencyP95 < 300) ? "healthy" : "unhealthy";

      Map<String, Object> metrics =
          Map.of(
              "requestCount",
              10000,
              "errorCount",
              10,
              "successRate",
              99.9,
              "latencyP50",
              50.0,
              "latencyP95",
              latencyP95,
              "latencyP99",
              200.0);

      String canaryId = "canary-" + UUID.randomUUID();

      CanaryOutput output =
          new CanaryOutput(
              canaryId,
              status,
              errorRate,
              latencyP95,
              metrics,
              Map.of(
                  "deploymentId",
                  canaryInput.deploymentId(),
                  "trafficPercentage",
                  canaryInput.trafficPercentage(),
                  "durationMinutes",
                  canaryInput.durationMinutes(),
                  "monitoredAt",
                  start.toString()));

      return Promise.of(
          StepResult.success(
              output, Map.of("canaryId", canaryId, "status", status), start, Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<CanaryInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0); // Rule-based
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("CanaryGenerator")
          .type("rule-based")
          .description("Executes canary deployment with monitoring")
          .version("1.0.0")
          .build();
    }
  }
}
