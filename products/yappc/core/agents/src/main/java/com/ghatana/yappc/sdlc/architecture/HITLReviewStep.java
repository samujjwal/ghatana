package com.ghatana.yappc.sdlc.architecture;

// ✅ Use EXISTING interfaces from libs/java
import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import com.ghatana.yappc.sdlc.WorkflowContextAdapter;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;

/**
 * AEP Step: ARCHITECTURE / HITLReview.
 *
 * <p>Human-in-the-loop review step for architecture approval.
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
 * @doc.purpose Architecture phase HITL review step - human approval for architecture
 * @doc.layer product
 * @doc.pattern Service
 */
public final class HITLReviewStep implements WorkflowStep {

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public HITLReviewStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  public static final String STATE_PENDING_REVIEW = "PENDING_REVIEW";
  public static final String STATE_IN_REVIEW = "IN_REVIEW";
  public static final String STATE_APPROVED = "APPROVED";
  public static final String STATE_REJECTED = "REJECTED";
  public static final String STATE_CHANGES_REQUESTED = "CHANGES_REQUESTED";

  public static final String ACTION_CREATE = "create";
  public static final String ACTION_APPROVE = "approve";
  public static final String ACTION_REJECT = "reject";

  @Override
  public String getStepId() {
    return "architecture.hitlreview";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    return validateInput(context)
        .then(this::determineReviewAction)
        .then(actionResult -> processReviewAction(context, actionResult))
        .then(this::persistReviewState)
        .then(this::publishEvents)
        .then(result -> buildOutputContext(context, result))
        .whenException(error -> handleError(error, context));
  }

  private Promise<WorkflowContext> validateInput(WorkflowContext context) {
    Map<String, Object> data = context.getData();
    if (data == null || !data.containsKey("architectureId")) {
      return Promise.ofException(new IllegalArgumentException("architectureId required"));
    }
    return Promise.of(context);
  }

  private Promise<Map<String, Object>> determineReviewAction(WorkflowContext context) {
    Map<String, Object> data = context.getData();
    String action = (String) data.getOrDefault("reviewAction", ACTION_CREATE);

    Map<String, Object> actionResult = new HashMap<>(data);
    actionResult.put("action", action);
    actionResult.put("reviewId", data.getOrDefault("reviewId", UUID.randomUUID().toString()));
    return Promise.of(actionResult);
  }

  private Promise<Map<String, Object>> processReviewAction(
      WorkflowContext context, Map<String, Object> actionResult) {
    String action = (String) actionResult.get("action");
    return switch (action) {
      case ACTION_CREATE -> createReview(actionResult);
      case ACTION_APPROVE -> approveReview(actionResult);
      case ACTION_REJECT -> rejectReview(actionResult);
      default -> Promise.ofException(new IllegalArgumentException("Unknown action: " + action));
    };
  }

  private Promise<Map<String, Object>> createReview(Map<String, Object> data) {
    data.put("reviewState", STATE_PENDING_REVIEW);
    data.put("createdAt", Instant.now().toString());
    data.put("reviewType", "architecture_approval");
    return Promise.of(data);
  }

  private Promise<Map<String, Object>> approveReview(Map<String, Object> data) {
    data.put("reviewState", STATE_APPROVED);
    data.put("approvedAt", Instant.now().toString());
    data.put("approvedBy", data.getOrDefault("reviewerId", "system"));
    data.put("canProceedToPublish", true);
    return Promise.of(data);
  }

  private Promise<Map<String, Object>> rejectReview(Map<String, Object> data) {
    data.put("reviewState", STATE_REJECTED);
    data.put("rejectedAt", Instant.now().toString());
    data.put("rejectedBy", data.getOrDefault("reviewerId", "system"));
    data.put("canProceedToPublish", false);
    return Promise.of(data);
  }

  private Promise<Map<String, Object>> persistReviewState(Map<String, Object> data) {
    return dbClient
        .insert("architecture_reviews", data)
        .map(
            $ -> {
              data.put("reviewPersisted", true);
              return data;
            });
  }

  private Promise<Map<String, Object>> publishEvents(Map<String, Object> data) {
    String reviewState = (String) data.get("reviewState");
    String eventType = "architecture.review." + reviewState.toLowerCase();
    return eventClient
        .publish(
            "architecture.hitl.review",
            Map.of(
                "eventType",
                eventType,
                "architectureId",
                data.get("architectureId"),
                "reviewState",
                reviewState,
                "timestamp",
                Instant.now().toString()))
        .map($ -> data);
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
    eventClient.publish(
        "architecture.errors",
        Map.of(
            "eventType", "architecture.review.error",
            "error", error.getMessage(),
            "timestamp", Instant.now().toString()));
  }
}
