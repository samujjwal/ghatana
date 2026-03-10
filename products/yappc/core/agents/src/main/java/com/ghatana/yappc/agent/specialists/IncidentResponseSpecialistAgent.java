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
 * Specialist agent for incident response.
 *
 * <p>Coordinates incident response including diagnosis, mitigation, and escalation.
 *
 * @doc.type class
 * @doc.purpose Specialist agent for incident response
 * @doc.layer product
 * @doc.pattern Agent
 * @doc.gaa.lifecycle act
 */
public class IncidentResponseSpecialistAgent
    extends YAPPCAgentBase<IncidentResponseInput, IncidentResponseOutput> {

  private static final Logger log = LoggerFactory.getLogger(IncidentResponseSpecialistAgent.class);

  private final MemoryStore memoryStore;

  public IncidentResponseSpecialistAgent(
      @NotNull MemoryStore memoryStore,
      @NotNull
          OutputGenerator<StepRequest<IncidentResponseInput>, StepResult<IncidentResponseOutput>>
              generator) {
    super(
        "IncidentResponseSpecialistAgent",
        "ops.incidentResponse",
        new StepContract(
            "ops.incidentResponse",
            "#/definitions/IncidentResponseInput",
            "#/definitions/IncidentResponseOutput",
            List.of("ops", "incident", "response"),
            Map.of("description", "Coordinates incident response", "version", "1.0.0")),
        generator);
    this.memoryStore = memoryStore;
  }

  @Override
  protected MemoryStore getMemoryStore() {
    return memoryStore;
  }

  @Override
  public ValidationResult validateInput(@NotNull IncidentResponseInput input) {
    if (input.deploymentId() == null || input.deploymentId().isEmpty()) {
      return ValidationResult.fail("Deployment ID cannot be empty");
    }
    if (input.severity() == null || input.severity().isEmpty()) {
      return ValidationResult.fail("Severity cannot be empty");
    }
    return ValidationResult.success();
  }

  @Override
  protected StepRequest<IncidentResponseInput> perceive(
      @NotNull StepRequest<IncidentResponseInput> request, @NotNull AgentContext context) {
    log.info(
        "Perceiving incident response request for deployment: {}, severity: {}",
        request.input().deploymentId(),
        request.input().severity());
    return request;
  }

  /** Rule-based generator for incident response. */
  public static class IncidentResponseGenerator
      implements OutputGenerator<
          StepRequest<IncidentResponseInput>, StepResult<IncidentResponseOutput>> {

    private static final Logger log = LoggerFactory.getLogger(IncidentResponseGenerator.class);

    @Override
    public @NotNull Promise<StepResult<IncidentResponseOutput>> generate(
        @NotNull StepRequest<IncidentResponseInput> input, @NotNull AgentContext context) {

      Instant start = Instant.now();
      IncidentResponseInput incidentInput = input.input();

      log.info(
          "Responding to incident: deployment={}, severity={}, symptoms={}",
          incidentInput.deploymentId(),
          incidentInput.severity(),
          incidentInput.symptoms());

      List<String> actionsTaken = new ArrayList<>();

      // Determine response based on severity
      switch (incidentInput.severity().toLowerCase()) {
        case "critical" -> {
          actionsTaken.add("Paged on-call engineer");
          actionsTaken.add("Initiated emergency rollback");
          actionsTaken.add("Created war room");
        }
        case "high" -> {
          actionsTaken.add("Notified ops team");
          actionsTaken.add("Increased monitoring frequency");
          actionsTaken.add("Prepared rollback plan");
        }
        case "medium" -> {
          actionsTaken.add("Logged incident");
          actionsTaken.add("Scheduled investigation");
        }
        default -> {
          actionsTaken.add("Logged for review");
        }
      }

      String status =
          incidentInput.severity().equalsIgnoreCase("critical") ? "mitigated" : "monitoring";
      String runbookUrl =
          "https://wiki.example.com/runbooks/" + incidentInput.severity().toLowerCase();
      String incidentId = "incident-" + UUID.randomUUID();

      IncidentResponseOutput output =
          new IncidentResponseOutput(
              incidentId,
              status,
              actionsTaken,
              runbookUrl,
              Map.of(
                  "deploymentId",
                  incidentInput.deploymentId(),
                  "severity",
                  incidentInput.severity(),
                  "symptomCount",
                  incidentInput.symptoms().size(),
                  "respondedAt",
                  start.toString()));

      return Promise.of(
          StepResult.success(
              output, Map.of("incidentId", incidentId, "status", status), start, Instant.now()));
    }

    @Override
    public @NotNull Promise<Double> estimateCost(
        @NotNull StepRequest<IncidentResponseInput> input, @NotNull AgentContext context) {
      return Promise.of(0.0); // Rule-based
    }

    @Override
    public @NotNull GeneratorMetadata getMetadata() {
      return GeneratorMetadata.builder()
          .name("IncidentResponseGenerator")
          .type("rule-based")
          .description("Coordinates incident response and mitigation")
          .version("1.0.0")
          .build();
    }
  }
}
