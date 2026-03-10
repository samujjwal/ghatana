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
 * L1 orchestrator for runtime operations (Phase 9).
 *
 * <p>Coordinates monitoring, incident response, SLO enforcement, and capacity
 * management. Downstream agents ({@code MonitorSpecialistAgent},
 * {@code IncidentResponseSpecialistAgent}, {@code NotificationAgent}) report
 * to this orchestrator.
 *
 * @doc.type class
 * @doc.purpose Orchestrates runtime operations including monitoring, incidents, and SLOs
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class OperationsOrchestratorAgent
    extends YAPPCAgentBase<OperationsOrchestratorInput, OperationsOrchestratorOutput> {

  private static final Logger log = LoggerFactory.getLogger(OperationsOrchestratorAgent.class);

  private final MemoryStore memoryStore;

  /**
   * Constructs the operations orchestrator.
   *
   * @param memoryStore memory store for operational event tracking
   * @param generator   output generator (rule-based or LLM-powered)
   */
  public OperationsOrchestratorAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull OutputGenerator<StepRequest<OperationsOrchestratorInput>,
          StepResult<OperationsOrchestratorOutput>> generator) {
    super(
        "OperationsOrchestratorAgent",
        "orchestrator.operations",
        new StepContract(
            "orchestrator.operations",
            "#/definitions/OperationsOrchestratorInput",
            "#/definitions/OperationsOrchestratorOutput",
            List.of("operations-orchestration", "monitoring", "incident-response", "slo-enforcement"),
            Map.of(
                "description", "Orchestrates runtime operations",
                "version", "1.0.0",
                "level", "L1",
                "reports_to", "head-of-devops")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull OperationsOrchestratorInput input) {
    if (input.operationId() == null || input.operationId().isEmpty()) {
      return ValidationResult.fail("operationId cannot be empty");
    }
    if (input.operationType() == null || input.operationType().isEmpty()) {
      return ValidationResult.fail("operationType cannot be empty");
    }
    String type = input.operationType().toLowerCase(Locale.ROOT);
    if (!Set.of("monitoring", "incident", "slo_check", "capacity").contains(type)) {
      return ValidationResult.fail(
          "operationType must be one of: monitoring, incident, slo_check, capacity");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<OperationsOrchestratorInput> perceive(
      @NotNull StepRequest<OperationsOrchestratorInput> request,
      @NotNull AgentContext context) {
    OperationsOrchestratorInput input = request.input();
    log.info("Perceiving operations request [{}] type={} severity={} services={}",
        input.operationId(), input.operationType(), input.severity(),
        input.affectedServices().size());
    return request;
  }

  /**
   * Rule-based operations orchestration generator.
   *
   * <p>Routes operational requests to the appropriate action path
   * (monitoring, incident response, SLO check, capacity management).
   */
  public static class OperationsOrchestratorGenerator
      implements OutputGenerator<StepRequest<OperationsOrchestratorInput>,
          StepResult<OperationsOrchestratorOutput>> {

    private static final Logger log =
        LoggerFactory.getLogger(OperationsOrchestratorGenerator.class);

    @Override
    public @NotNull Promise<StepResult<OperationsOrchestratorOutput>> generate(
        @NotNull StepRequest<OperationsOrchestratorInput> input,
        @NotNull AgentContext context) {

      Instant start = Instant.now();
      OperationsOrchestratorInput opsInput = input.input();

      log.info("Orchestrating operations [{}] type={}",
          opsInput.operationId(), opsInput.operationType());

      return switch (opsInput.operationType().toLowerCase(Locale.ROOT)) {
        case "incident" -> handleIncident(opsInput, start);
        case "slo_check" -> handleSloCheck(opsInput, start);
        case "capacity" -> handleCapacity(opsInput, start);
        default -> handleMonitoring(opsInput, start);
      };
    }

    private Promise<StepResult<OperationsOrchestratorOutput>> handleIncident(
        OperationsOrchestratorInput input, Instant start) {

      String incidentId = "INC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      List<String> actions = new ArrayList<>();
      List<String> notifications = new ArrayList<>();

      actions.add("incident-detected: " + input.severity());
      actions.add("affected-services-identified: " + input.affectedServices().size());
      notifications.add("oncall-paged: severity=" + input.severity());

      if ("CRITICAL".equalsIgnoreCase(input.severity())
          || "HIGH".equalsIgnoreCase(input.severity())) {
        actions.add("escalated-to-incident-commander");
        notifications.add("management-notified");
      }

      actions.add("delegated-to-incident-response-agent");

      return buildResult(input.operationId(), OperationsOrchestratorOutput.STATUS_INCIDENT,
          actions, notifications, incidentId, start, input);
    }

    private Promise<StepResult<OperationsOrchestratorOutput>> handleSloCheck(
        OperationsOrchestratorInput input, Instant start) {

      List<String> actions = List.of(
          "slo-targets-retrieved",
          "current-metrics-collected",
          "burn-rate-calculated");

      boolean healthy = !input.context().containsKey("sloBreached");
      String status = healthy
          ? OperationsOrchestratorOutput.STATUS_HEALTHY
          : OperationsOrchestratorOutput.STATUS_DEGRADED;

      List<String> notifications = healthy
          ? List.of()
          : List.of("slo-breach-alert: " + input.affectedServices());

      return buildResult(input.operationId(), status,
          actions, notifications, "", start, input);
    }

    private Promise<StepResult<OperationsOrchestratorOutput>> handleCapacity(
        OperationsOrchestratorInput input, Instant start) {

      List<String> actions = List.of(
          "capacity-metrics-collected",
          "utilization-analyzed",
          "scaling-recommendation-generated");

      return buildResult(input.operationId(), OperationsOrchestratorOutput.STATUS_HEALTHY,
          actions, List.of(), "", start, input);
    }

    private Promise<StepResult<OperationsOrchestratorOutput>> handleMonitoring(
        OperationsOrchestratorInput input, Instant start) {

      List<String> actions = List.of(
          "health-checks-executed",
          "metrics-collected",
          "dashboards-updated");

      return buildResult(input.operationId(), OperationsOrchestratorOutput.STATUS_HEALTHY,
          actions, List.of(), "", start, input);
    }

    private Promise<StepResult<OperationsOrchestratorOutput>> buildResult(
        String operationId, String status, List<String> actions,
        List<String> notifications, String incidentId,
        Instant start, OperationsOrchestratorInput input) {

      OperationsOrchestratorOutput output = new OperationsOrchestratorOutput(
          operationId,
          status,
          actions,
          notifications,
          incidentId,
          Map.of(
              "generatedAt", start.toString(),
              "operationType", input.operationType(),
              "severity", input.severity(),
              "affectedServiceCount", input.affectedServices().size()));

      return Promise.of(
          StepResult.success(
              output,
              Map.of(
                  "stepId", "orchestrator.operations",
                  "status", status,
                  "executedAt", start.toString()),
              start,
              Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<OperationsOrchestratorInput> input,
        @NotNull AgentContext context) {
      return Promise.of(0.0);
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("OperationsOrchestratorGenerator")
          .type("rule-based")
          .description("Orchestrates runtime operations with monitoring, incident, and SLO paths")
          .version("1.0.0")
          .build();
    }
  }
}
