package com.ghatana.yappc.agent.requirements;

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
 * AEP Step: REQUIREMENTS / Validate.
 *
 * <p>Validates requirements for completeness, consistency and feasibility.
 *
 * <p>✅ Implements WorkflowStep from libs:workflow-api (EXISTING) ✅ Uses DatabaseClient from
 * libs:database (EXISTING) ✅ Uses EventCloud from libs:event-cloud (EXISTING)
 *
 * <h3>Implementation Checklist:</h3>
 *
 * <ol>
 *   <li>Validate input against JSON schema (contract.inputSchemaRef) – fail fast.
 *   <li>Deterministic gates (rules + policies) BEFORE any intelligent call.
 *   <li>Optional intelligent calls (LLM/ML) must:
 *       <ul>
 *         <li>use pinned versions from config snapshot
 *         <li>enforce budget, retries, circuit breakers
 *         <li>record provenance (model/prompt hashes)
 *       </ul>
 *   <li>Persist entity mutations via Data-Cloud (repo).
 *   <li>Emit workflow + entity events + audit events.
 *   <li>Safe degradation: fallback provider / heuristic-only / review-only.
 * </ol>
 *
 * @doc.type class
 * @doc.purpose Requirements phase validate step - validates requirements quality
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ValidateStep implements WorkflowStep {

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public ValidateStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "requirements.validate";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    return validateInput(context)
        .then(this::performValidationChecks)
        .then(this::persistValidationResults)
        .then(this::publishEvents)
        .then(result -> buildOutputContext(context, result))
        .whenException(error -> handleError(error, context));
  }

  private Promise<WorkflowContext> validateInput(WorkflowContext context) {
    Map<String, Object> data = context.getData();

    if (data == null || data.isEmpty()) {
      return Promise.ofException(
          new IllegalArgumentException("Input data required for validation"));
    }

    if (!data.containsKey("requirementId")) {
      return Promise.ofException(new IllegalArgumentException("Field 'requirementId' required"));
    }

    return Promise.of(context);
  }

  /**
   * Performs comprehensive validation checks on derived requirements. Checks: completeness,
   * consistency, feasibility, clarity.
   */
  private Promise<Map<String, Object>> performValidationChecks(WorkflowContext context) {
    Map<String, Object> data = context.getData();
    String requirementId = (String) data.get("requirementId");

    List<String> functionalReqs =
        (List<String>) data.getOrDefault("functionalRequirements", List.of());
    List<String> nonFunctionalReqs =
        (List<String>) data.getOrDefault("nonFunctionalRequirements", List.of());

    Map<String, Object> validationResults = new HashMap<>();
    validationResults.put("requirementId", requirementId);
    validationResults.put(
        "completenessCheck", checkCompleteness(functionalReqs, nonFunctionalReqs));
    validationResults.put("consistencyCheck", checkConsistency(data));
    validationResults.put("feasibilityCheck", checkFeasibility(data));
    validationResults.put("clarityCheck", checkClarity(functionalReqs));
    validationResults.put("overallValid", isOverallValid(validationResults));
    validationResults.put("validationIssues", collectIssues(validationResults));
    validationResults.put("validatedAt", Instant.now().toString());
    validationResults.put("tenantId", context.getTenantId());

    // Copy important fields forward
    validationResults.put("functionalRequirements", functionalReqs);
    validationResults.put("nonFunctionalRequirements", nonFunctionalReqs);

    return Promise.of(validationResults);
  }

  private Promise<Map<String, Object>> persistValidationResults(Map<String, Object> results) {
    return dbClient
        .insert("requirements_validated", results)
        .map(
            dbResult -> {
              results.put("persisted", true);
              results.put("collection", "requirements_validated");
              return results;
            });
  }

  private Promise<Map<String, Object>> publishEvents(Map<String, Object> data) {
    boolean isValid = (boolean) data.get("overallValid");
    List<String> issues = (List<String>) data.get("validationIssues");

    Map<String, Object> event =
        Map.of(
            "eventType",
                isValid ? "requirements.validated.success" : "requirements.validated.failed",
            "requirementId", data.get("requirementId"),
            "isValid", isValid,
            "issueCount", issues.size(),
            "timestamp", Instant.now().toString());

    return eventClient.publish("requirements.validated", event).map($ -> data);
  }

  private Promise<WorkflowContext> buildOutputContext(
      WorkflowContext originalContext, Map<String, Object> results) {
    return Promise.of(
        new WorkflowContextAdapter.Builder()
            .tenantId(originalContext.getTenantId())
            .workflowId(originalContext.getWorkflowId())
            .putAll(results)
            .build());
  }

  private Promise<WorkflowContext> handleError(Throwable error, WorkflowContext context) {
    Map<String, Object> errorEvent =
        Map.of(
            "eventType", "requirements.validation.error",
            "requirementId", context.getData().getOrDefault("requirementId", "unknown"),
            "error", error.getMessage(),
            "timestamp", Instant.now().toString());

    return eventClient
        .publish("requirements.errors", errorEvent)
        .then(
            $ ->
                Promise.ofException(
                    error instanceof Exception ? (Exception) error : new RuntimeException(error)));
  }

  // --- Validation Helper Methods ---

  private Map<String, Object> checkCompleteness(
      List<String> functional, List<String> nonFunctional) {
    boolean hasFunctional = !functional.isEmpty();
    boolean hasNonFunctional = !nonFunctional.isEmpty();
    boolean isComplete = hasFunctional && hasNonFunctional;

    return Map.of(
        "passed", isComplete,
        "hasFunctionalRequirements", hasFunctional,
        "hasNonFunctionalRequirements", hasNonFunctional,
        "message",
            isComplete
                ? "Requirements are complete"
                : "Missing functional or non-functional requirements");
  }

  private Map<String, Object> checkConsistency(Map<String, Object> data) {
    // Check for contradictions or conflicts
    List<String> functional = (List<String>) data.getOrDefault("functionalRequirements", List.of());
    List<String> constraints = (List<String>) data.getOrDefault("constraints", List.of());

    boolean hasConflicts = false;
    String conflictMessage = "";

    // Simple heuristic: check if constraints conflict with requirements
    if (!constraints.isEmpty() && functional.size() > 5) {
      hasConflicts = true;
      conflictMessage = "High complexity with many constraints may indicate conflicts";
    }

    return Map.of(
        "passed", !hasConflicts,
        "hasConflicts", hasConflicts,
        "message", hasConflicts ? conflictMessage : "No obvious consistency issues detected");
  }

  private Map<String, Object> checkFeasibility(Map<String, Object> data) {
    List<String> dependencies = (List<String>) data.getOrDefault("dependencies", List.of());
    List<String> constraints = (List<String>) data.getOrDefault("constraints", List.of());

    boolean isFeasible = true;
    String message = "Requirements appear feasible";

    if (dependencies.size() > 10) {
      isFeasible = false;
      message = "Too many dependencies may indicate infeasibility";
    } else if (constraints.size() > 5) {
      isFeasible = false;
      message = "Too many constraints may make requirements infeasible";
    }

    return Map.of(
        "passed",
        isFeasible,
        "dependencyCount",
        dependencies.size(),
        "constraintCount",
        constraints.size(),
        "message",
        message);
  }

  private Map<String, Object> checkClarity(List<String> requirements) {
    boolean allClear =
        requirements.stream()
            .allMatch(req -> req != null && req.length() > 10 && req.length() < 500);

    return Map.of(
        "passed",
        allClear,
        "averageLength",
        requirements.stream().mapToInt(String::length).average().orElse(0.0),
        "message",
        allClear ? "Requirements are clear and well-defined" : "Some requirements lack clarity");
  }

  private boolean isOverallValid(Map<String, Object> validationResults) {
    Map<String, Object> completeness =
        (Map<String, Object>) validationResults.get("completenessCheck");
    Map<String, Object> consistency =
        (Map<String, Object>) validationResults.get("consistencyCheck");
    Map<String, Object> feasibility =
        (Map<String, Object>) validationResults.get("feasibilityCheck");
    Map<String, Object> clarity = (Map<String, Object>) validationResults.get("clarityCheck");

    return (boolean) completeness.get("passed")
        && (boolean) consistency.get("passed")
        && (boolean) feasibility.get("passed")
        && (boolean) clarity.get("passed");
  }

  private List<String> collectIssues(Map<String, Object> validationResults) {
    List<String> issues = new ArrayList<>();

    Map<String, Object> completeness =
        (Map<String, Object>) validationResults.get("completenessCheck");
    Map<String, Object> consistency =
        (Map<String, Object>) validationResults.get("consistencyCheck");
    Map<String, Object> feasibility =
        (Map<String, Object>) validationResults.get("feasibilityCheck");
    Map<String, Object> clarity = (Map<String, Object>) validationResults.get("clarityCheck");

    if (!(boolean) completeness.get("passed")) {
      issues.add("Completeness: " + completeness.get("message"));
    }
    if (!(boolean) consistency.get("passed")) {
      issues.add("Consistency: " + consistency.get("message"));
    }
    if (!(boolean) feasibility.get("passed")) {
      issues.add("Feasibility: " + feasibility.get("message"));
    }
    if (!(boolean) clarity.get("passed")) {
      issues.add("Clarity: " + clarity.get("message"));
    }

    return issues;
  }
}
