package com.ghatana.yappc.agent.architecture;

// ✅ Use EXISTING interfaces from libs/java
import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import com.ghatana.yappc.agent.WorkflowContextAdapter;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;

/**
 * AEP Step: ARCHITECTURE / Intake.
 *
 * <p>Loads approved requirements baseline + NFRs + constraints to initialize the architecture phase
 * run. Validates that requirements are approved and extracts architectural constraints and
 * non-functional requirements.
 *
 * <p>✅ Implements WorkflowStep from libs:workflow-api (EXISTING) ✅ Uses DatabaseClient from
 * libs:database (EXISTING) ✅ Uses EventCloud from libs:event-cloud (EXISTING)
 *
 * <h3>Key Responsibilities:</h3>
 *
 * <ul>
 *   <li>Verify requirements baseline is approved and published
 *   <li>Load and validate NFR targets (latency, availability, scalability)
 *   <li>Extract architectural constraints and quality attributes
 *   <li>Initialize architecture run context with requirements traceability
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Architecture phase intake step - loads requirements baseline
 * @doc.layer product
 * @doc.pattern Service
 */
public final class IntakeStep implements WorkflowStep {

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public IntakeStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "architecture.intake";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    return validateInput(context)
        .then(this::loadRequirementsBaseline)
        .then(this::extractArchitecturalConstraints)
        .then(this::extractNFRTargets)
        .then(this::persistArchitectureRun)
        .then(this::publishEvents)
        .then(result -> buildOutputContext(context, result))
        .whenException(error -> handleError(error, context));
  }

  private Promise<WorkflowContext> validateInput(WorkflowContext context) {
    Map<String, Object> data = context.getData();

    if (data == null || data.isEmpty()) {
      return Promise.ofException(
          new IllegalArgumentException("Input data required for architecture intake"));
    }

    if (!data.containsKey("baselineId")) {
      return Promise.ofException(
          new IllegalArgumentException(
              "Field 'baselineId' required - must reference published requirements"));
    }

    return Promise.of(context);
  }

  /**
   * Loads the approved and published requirements baseline. Verifies status is PUBLISHED before
   * proceeding.
   */
  private Promise<Map<String, Object>> loadRequirementsBaseline(WorkflowContext context) {
    Map<String, Object> data = context.getData();
    String baselineId = (String) data.get("baselineId");

    // Query Data-Cloud for published baseline
    return dbClient
        .query("requirements_published", Map.of("baselineId", baselineId), 100)
        .then(
            results -> {
              if (results.isEmpty()) {
                return Promise.ofException(
                    new IllegalStateException(
                        "Requirements baseline not found or not published: " + baselineId));
              }

              Map<String, Object> baseline = results.get(0);
              String status = (String) baseline.get("status");

              if (!"PUBLISHED".equals(status)) {
                return Promise.ofException(
                    new IllegalStateException(
                        "Requirements baseline must be PUBLISHED. Current status: " + status));
              }

              // Build intake data
              Map<String, Object> intakeData = new HashMap<>();
              intakeData.put("architectureId", UUID.randomUUID().toString());
              intakeData.put("baselineId", baselineId);
              intakeData.put("requirementId", baseline.get("requirementId"));
              intakeData.put("baselineVersion", baseline.get("version"));
              intakeData.put("tenantId", context.getTenantId());
              intakeData.put("workflowId", context.getWorkflowId());
              intakeData.put("ingestedAt", Instant.now().toString());

              // Copy functional and non-functional requirements
              intakeData.put(
                  "functionalRequirements",
                  baseline.getOrDefault("functionalRequirements", List.of()));
              intakeData.put(
                  "nonFunctionalRequirements",
                  baseline.getOrDefault("nonFunctionalRequirements", List.of()));
              intakeData.put(
                  "acceptanceCriteria", baseline.getOrDefault("acceptanceCriteria", List.of()));

              return Promise.of(intakeData);
            });
  }

  /**
   * Extracts architectural constraints from requirements. Examples: platform restrictions,
   * integration requirements, regulatory constraints.
   */
  private Promise<Map<String, Object>> extractArchitecturalConstraints(Map<String, Object> data) {
    @SuppressWarnings("unchecked")
    List<String> nfrs = (List<String>) data.getOrDefault("nonFunctionalRequirements", List.of());

    List<String> constraints = new ArrayList<>();
    List<String> qualityAttributes = new ArrayList<>();

    for (String nfr : nfrs) {
      String nfrLower = nfr.toLowerCase();

      // Extract constraints (MUST/SHALL statements)
      if (nfrLower.contains("must") || nfrLower.contains("shall")) {
        constraints.add(nfr);
      }

      // Extract quality attributes
      if (nfrLower.contains("performance")
          || nfrLower.contains("latency")
          || nfrLower.contains("throughput")) {
        qualityAttributes.add("performance");
      }
      if (nfrLower.contains("availability")
          || nfrLower.contains("uptime")
          || nfrLower.contains("reliability")) {
        qualityAttributes.add("availability");
      }
      if (nfrLower.contains("security")
          || nfrLower.contains("authentication")
          || nfrLower.contains("authorization")) {
        qualityAttributes.add("security");
      }
      if (nfrLower.contains("scalability")
          || nfrLower.contains("scale")
          || nfrLower.contains("capacity")) {
        qualityAttributes.add("scalability");
      }
      if (nfrLower.contains("maintainability") || nfrLower.contains("testability")) {
        qualityAttributes.add("maintainability");
      }
    }

    // Deduplicate quality attributes
    Set<String> uniqueQA = new LinkedHashSet<>(qualityAttributes);

    data.put("architecturalConstraints", constraints);
    data.put("qualityAttributes", new ArrayList<>(uniqueQA));
    data.put("constraintCount", constraints.size());

    return Promise.of(data);
  }

  /**
   * Extracts specific NFR targets (quantitative) from requirements. Examples: latency < 200ms,
   * availability > 99.9%, throughput > 1000 req/s.
   */
  private Promise<Map<String, Object>> extractNFRTargets(Map<String, Object> data) {
    @SuppressWarnings("unchecked")
    List<String> nfrs = (List<String>) data.getOrDefault("nonFunctionalRequirements", List.of());

    Map<String, Object> nfrTargets = new HashMap<>();

    // Default targets (can be overridden by explicit requirements)
    nfrTargets.put("availability", Map.of("target", 99.9, "unit", "percent"));
    nfrTargets.put("latency", Map.of("target", 500, "unit", "ms", "percentile", "P95"));
    nfrTargets.put("security", Map.of("criticalVulns", 0, "scanningEnabled", true));

    // Parse explicit targets from NFRs
    for (String nfr : nfrs) {
      String nfrLower = nfr.toLowerCase();

      // Parse latency targets (e.g., "latency < 200ms", "response time under 300ms")
      if (nfrLower.matches(".*latency.*<.*\\d+.*ms.*")
          || nfrLower.matches(".*response.*time.*<.*\\d+.*ms.*")) {
        // Extract numeric value
        String numStr = nfr.replaceAll(".*<\\s*(\\d+)\\s*ms.*", "$1");
        try {
          int latency = Integer.parseInt(numStr);
          nfrTargets.put("latency", Map.of("target", latency, "unit", "ms", "percentile", "P95"));
        } catch (NumberFormatException ignored) {
        }
      }

      // Parse availability targets (e.g., "availability > 99.99%", "uptime 99.9%")
      if (nfrLower.matches(".*availability.*>.*\\d+\\.\\d+.*")
          || nfrLower.matches(".*uptime.*\\d+\\.\\d+.*")) {
        String numStr = nfr.replaceAll(".*(\\d+\\.\\d+).*", "$1");
        try {
          double availability = Double.parseDouble(numStr);
          nfrTargets.put("availability", Map.of("target", availability, "unit", "percent"));
        } catch (NumberFormatException ignored) {
        }
      }

      // Parse throughput targets
      if (nfrLower.matches(".*throughput.*>.*\\d+.*")
          || nfrLower.matches(".*requests.*per.*second.*\\d+.*")) {
        String numStr = nfr.replaceAll(".*>(\\d+).*", "$1");
        try {
          int throughput = Integer.parseInt(numStr);
          nfrTargets.put("throughput", Map.of("target", throughput, "unit", "req/s"));
        } catch (NumberFormatException ignored) {
        }
      }
    }

    data.put("nfrTargets", nfrTargets);

    return Promise.of(data);
  }

  private Promise<Map<String, Object>> persistArchitectureRun(Map<String, Object> data) {
    // Persist initial architecture run state
    Map<String, Object> archRun = new HashMap<>(data);
    archRun.put("status", "INTAKE_COMPLETED");
    archRun.put("phase", "architecture");
    archRun.put("currentStep", "intake");

    return dbClient
        .insert("architecture_runs", archRun)
        .map(
            dbResult -> {
              data.put("persisted", true);
              data.put("collection", "architecture_runs");
              return data;
            });
  }

  private Promise<Map<String, Object>> publishEvents(Map<String, Object> data) {
    Map<String, Object> event =
        Map.of(
            "eventType", "architecture.intake.completed",
            "architectureId", data.get("architectureId"),
            "baselineId", data.get("baselineId"),
            "constraintCount", data.get("constraintCount"),
            "qualityAttributeCount", ((List<?>) data.get("qualityAttributes")).size(),
            "timestamp", Instant.now().toString());

    return eventClient.publish("architecture.ingested", event).map($ -> data);
  }

  private Promise<WorkflowContext> buildOutputContext(
      WorkflowContext originalContext, Map<String, Object> results) {
    return Promise.of(
        WorkflowContextAdapter.builder()
            .tenantId(originalContext.getTenantId())
            .workflowId(originalContext.getWorkflowId())
            .putAll(results)
            .build());
  }

  private void handleError(Throwable error, WorkflowContext context) {
    Map<String, Object> errorEvent =
        Map.of(
            "eventType", "architecture.intake.error",
            "baselineId", context.getData().getOrDefault("baselineId", "unknown"),
            "error", error.getMessage(),
            "timestamp", Instant.now().toString());

    eventClient.publish("architecture.errors", errorEvent);
  }
}
