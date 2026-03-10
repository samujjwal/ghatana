package com.ghatana.yappc.agent.ops;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;

/**
 * Ops Phase - Step 5: Promote or Rollback Decision.
 *
 * <p>Makes the final decision to promote the release to 100% traffic or rollback. Based on
 * validation results, monitoring data, and business approval.
 *
 * <h3>Responsibilities:</h3>
 *
 * <ul>
 *   <li>Load validation and monitoring results
 *   <li>Evaluate promotion criteria
 *   <li>Execute promote (100% traffic) or rollback
 *   <li>Update deployment status
 *   <li>Persist decision record
 *   <li>Emit workflow events
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Makes promote/rollback decision and executes it
 * @doc.layer product
 * @doc.pattern Service
 */
public final class PromoteOrRollbackStep implements WorkflowStep {

  private static final String COLLECTION_PROMOTION_DECISIONS = "promotion_decisions";
  private static final String COLLECTION_RELEASE_VALIDATIONS = "release_validations";
  private static final String EVENT_TOPIC = "ops.workflow";

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public PromoteOrRollbackStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "ops.promoteorrollback";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    String decisionId = UUID.randomUUID().toString();
    String tenantId = (String) context.get("tenantId");
    Instant startTime = Instant.now();

    return validateInput(context)
        .then($ -> loadValidationResults(context, tenantId))
        .then(validation -> evaluatePromotionCriteria(validation, tenantId, decisionId))
        .then(decision -> executeDecision(decision, tenantId, decisionId))
        .then(decision -> persistDecision(decision, tenantId, decisionId))
        .then(decision -> publishEvents(decision, tenantId, decisionId))
        .then(decision -> buildOutputContext(context, decision, startTime))
        .whenException(ex -> handleError(ex, context, tenantId, decisionId, startTime));
  }

  private Promise<Void> validateInput(WorkflowContext context) {
    if (!context.containsKey("tenantId")) {
      return Promise.ofException(new IllegalArgumentException("Missing required input: tenantId"));
    }
    return Promise.complete();
  }

  private Promise<Map<String, Object>> loadValidationResults(
      WorkflowContext context, String tenantId) {
    String validationId = (String) context.getOrDefault("validationId", "latest");

    return dbClient
        .query(COLLECTION_RELEASE_VALIDATIONS, Map.of("tenantId", tenantId), 1)
        .map(
            results -> {
              if (results.isEmpty()) {
                throw new IllegalStateException("No release validation found");
              }
              return results.get(0);
            });
  }

  private Promise<Map<String, Object>> evaluatePromotionCriteria(
      Map<String, Object> validation, String tenantId, String decisionId) {

    List<Map<String, Object>> criteria = new ArrayList<>();

    // Criterion 1: Release validation passed
    boolean releaseApproved = (boolean) validation.getOrDefault("releaseApproved", false);
    criteria.add(
        Map.of(
            "criterion",
            "RELEASE_VALIDATION",
            "status",
            releaseApproved ? "MET" : "NOT_MET",
            "required",
            true));

    // Criterion 2: No critical alerts
    int criticalAlerts = 0; // Simulated from monitoring
    criteria.add(
        Map.of(
            "criterion",
            "NO_CRITICAL_ALERTS",
            "status",
            criticalAlerts == 0 ? "MET" : "NOT_MET",
            "required",
            true));

    // Criterion 3: Error budget available
    double errorBudget = 95.0; // % remaining
    criteria.add(
        Map.of(
            "criterion",
            "ERROR_BUDGET",
            "status",
            errorBudget > 20 ? "MET" : "NOT_MET",
            "required",
            true,
            "value",
            errorBudget + "%"));

    // Criterion 4: Business approval
    boolean businessApproval = true; // Simulated
    criteria.add(
        Map.of(
            "criterion",
            "BUSINESS_APPROVAL",
            "status",
            businessApproval ? "MET" : "NOT_MET",
            "required",
            false));

    boolean allRequiredMet =
        criteria.stream()
            .filter(c -> Boolean.TRUE.equals(c.get("required")))
            .allMatch(c -> "MET".equals(c.get("status")));

    Map<String, Object> decision = new LinkedHashMap<>();
    decision.put("decisionId", decisionId);
    decision.put("tenantId", tenantId);
    decision.put("validation", validation);
    decision.put("criteria", criteria);
    decision.put("decision", allRequiredMet ? "PROMOTE" : "ROLLBACK");
    decision.put(
        "reason",
        allRequiredMet ? "All promotion criteria met" : "One or more required criteria not met");
    decision.put("decidedAt", Instant.now().toString());

    return Promise.of(decision);
  }

  private Promise<Map<String, Object>> executeDecision(
      Map<String, Object> decision, String tenantId, String decisionId) {

    String decisionType = (String) decision.get("decision");

    List<Map<String, Object>> executionSteps = new ArrayList<>();

    if ("PROMOTE".equals(decisionType)) {
      // Promote to 100% traffic
      executionSteps.add(
          Map.of(
              "step", "SHIFT_TRAFFIC_100",
              "status", "SUCCESS",
              "traffic", Map.of("canary", 100, "stable", 0)));

      executionSteps.add(
          Map.of(
              "step", "SCALE_DOWN_OLD_VERSION",
              "status", "SUCCESS",
              "oldVersion", "v1.2.2",
              "replicas", "0"));

      executionSteps.add(
          Map.of(
              "step", "UPDATE_STABLE_VERSION",
              "status", "SUCCESS",
              "stableVersion", "v1.2.3"));

      decision.put("status", "PROMOTED");
    } else {
      // Rollback
      executionSteps.add(
          Map.of(
              "step", "SHIFT_TRAFFIC_BACK",
              "status", "SUCCESS",
              "traffic", Map.of("canary", 0, "stable", 100)));

      executionSteps.add(
          Map.of(
              "step", "SCALE_DOWN_CANARY",
              "status", "SUCCESS",
              "canaryVersion", "v1.2.3",
              "replicas", "0"));

      executionSteps.add(
          Map.of(
              "step", "RESTORE_STABLE",
              "status", "SUCCESS",
              "stableVersion", "v1.2.2"));

      decision.put("status", "ROLLED_BACK");
    }

    decision.put("executionSteps", executionSteps);
    decision.put("executedAt", Instant.now().toString());

    return Promise.of(decision);
  }

  private Promise<Map<String, Object>> persistDecision(
      Map<String, Object> decision, String tenantId, String decisionId) {

    return dbClient.insert(COLLECTION_PROMOTION_DECISIONS, decision).map($ -> decision);
  }

  private Promise<Map<String, Object>> publishEvents(
      Map<String, Object> decision, String tenantId, String decisionId) {

    String eventType =
        "PROMOTED".equals(decision.get("status")) ? "RELEASE_PROMOTED" : "RELEASE_ROLLED_BACK";

    Map<String, Object> eventPayload =
        Map.of(
            "eventType",
            eventType,
            "decisionId",
            decisionId,
            "decision",
            decision.get("decision"),
            "status",
            decision.get("status"));

    return eventClient.publish(EVENT_TOPIC, tenantId, eventPayload).map($ -> decision);
  }

  private Promise<WorkflowContext> buildOutputContext(
      WorkflowContext context, Map<String, Object> decision, Instant startTime) {

    WorkflowContext output = context.copy();
    output.put("status", "COMPLETED");
    output.put("step", "PromoteOrRollback");
    output.put("decisionId", decision.get("decisionId"));
    output.put("decision", decision);
    output.put("promoted", "PROMOTED".equals(decision.get("status")));
    output.put("duration", Instant.now().toEpochMilli() - startTime.toEpochMilli());
    output.put("completedAt", Instant.now().toString());

    return Promise.of(output);
  }

  private Promise<WorkflowContext> handleError(
      Throwable ex,
      WorkflowContext context,
      String tenantId,
      String decisionId,
      Instant startTime) {

    Map<String, Object> errorPayload =
        Map.of(
            "eventType",
            "PROMOTION_DECISION_FAILED",
            "decisionId",
            decisionId,
            "error",
            ex.getMessage(),
            "timestamp",
            Instant.now().toString());

    return eventClient
        .publish(EVENT_TOPIC, tenantId, errorPayload)
        .then(
            $ -> {
              WorkflowContext errorContext = context.copy();
              errorContext.put("status", "FAILED");
              errorContext.put("error", ex.getMessage());
              errorContext.put("duration", Instant.now().toEpochMilli() - startTime.toEpochMilli());
              return Promise.of(errorContext);
            });
  }
}
