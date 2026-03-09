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
 * Specialist agent for production monitoring.
 *
 * <p>Monitors production deployment health, metrics, and alerts.
 *
 * @doc.type class
 * @doc.purpose Specialist agent for production monitoring
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle perceive
 */
public class MonitorSpecialistAgent extends YAPPCAgentBase<MonitorInput, MonitorOutput> {

  private static final Logger log = LoggerFactory.getLogger(MonitorSpecialistAgent.class);

  private final MemoryStore memoryStore;

  public MonitorSpecialistAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<MonitorInput>, StepResult<MonitorOutput>> generator) {
    super(
        "MonitorSpecialistAgent",
        "ops.monitor",
        new StepContract(
            "ops.monitor",
            "#/definitions/MonitorInput",
            "#/definitions/MonitorOutput",
            List.of("ops", "monitoring", "observability"),
            Map.of("description", "Monitors production deployment", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull MonitorInput input) {
    if (input.deploymentId() == null || input.deploymentId().isEmpty()) {
      return ValidationResult.fail("Deployment ID cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<MonitorInput> perceive(
      @NotNull StepRequest<MonitorInput> request, @NotNull AgentContext context) {
    log.info(
        "Perceiving monitoring request for deployment: {}, duration: {}min",
        request.input().deploymentId(),
        request.input().durationMinutes());
    return request;
  }

  /** Rule-based generator for monitoring. */
  public static class MonitorGenerator
      implements OutputGenerator<StepRequest<MonitorInput>, StepResult<MonitorOutput>> {

    private static final Logger log = LoggerFactory.getLogger(MonitorGenerator.class);

    @Override
    public @NotNull Promise<StepResult<MonitorOutput>> generate(
        @NotNull StepRequest<MonitorInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      MonitorInput monitorInput = input.input();

      log.info(
          "Monitoring deployment: {}, duration: {}min",
          monitorInput.deploymentId(),
          monitorInput.durationMinutes());

      List<String> alerts = List.of(); // No alerts in healthy state

      Map<String, Object> metrics =
          Map.of(
              "uptime", 99.99,
              "requestsPerSecond", 1500,
              "errorRate", 0.001,
              "latencyP95", 120.0,
              "cpuUsage", 45.0,
              "memoryUsage", 60.0,
              "activeConnections", 350);

      String health = alerts.isEmpty() ? "healthy" : "degraded";
      String monitoringId = "monitoring-" + UUID.randomUUID();

      MonitorOutput output =
          new MonitorOutput(
              monitoringId,
              health,
              alerts,
              metrics,
              Map.of(
                  "deploymentId",
                  monitorInput.deploymentId(),
                  "monitoredAt",
                  start.toString(),
                  "durationMinutes",
                  monitorInput.durationMinutes(),
                  "dashboardUrl",
                  "https://metrics.example.com/dashboard"));

      return Promise.of(
          StepResult.success(
              output,
              Map.of("monitoringId", monitoringId, "health", health),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<MonitorInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0); // Rule-based
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("MonitorGenerator")
          .type("rule-based")
          .description("Monitors production deployment health and metrics")
          .version("1.0.0")
          .build();
    }
  }
}
