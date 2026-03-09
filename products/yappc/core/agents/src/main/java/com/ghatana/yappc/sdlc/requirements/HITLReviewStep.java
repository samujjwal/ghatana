package com.ghatana.yappc.sdlc.requirements;

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
 * AEP Step: REQUIREMENTS / HITLReview.
 *
 * <p>Human-in-the-loop review step for requirements that need manual approval. Manages review
 * lifecycle: create, submit, approve/reject/request-changes.
 *
 * <p>✅ Implements WorkflowStep from libs:workflow-api (EXISTING) ✅ Uses DatabaseClient from
 * libs:database (EXISTING) ✅ Uses EventCloud from libs:event-cloud (EXISTING)
 *
 * <h3>Review States:</h3>
 *
 * <ul>
 *   <li>PENDING_REVIEW - Waiting for reviewer assignment
 *   <li>IN_REVIEW - Reviewer actively reviewing
 *   <li>APPROVED - Requirement approved, can proceed
 *   <li>REJECTED - Requirement rejected, needs rework
 *   <li>CHANGES_REQUESTED - Minor changes needed before approval
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Requirements phase HITL review step - human approval for requirements
 * @doc.layer product
 * @doc.pattern Service
 */
public final class HITLReviewStep implements WorkflowStep {

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  // Review state constants
  public static final String STATE_PENDING_REVIEW = "PENDING_REVIEW";
  public static final String STATE_IN_REVIEW = "IN_REVIEW";
  public static final String STATE_APPROVED = "APPROVED";
  public static final String STATE_REJECTED = "REJECTED";
  public static final String STATE_CHANGES_REQUESTED = "CHANGES_REQUESTED";

  // Review action constants
  public static final String ACTION_CREATE = "create";
  public static final String ACTION_SUBMIT = "submit";
  public static final String ACTION_APPROVE = "approve";
  public static final String ACTION_REJECT = "reject";
  public static final String ACTION_REQUEST_CHANGES = "request_changes";

  public HITLReviewStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "requirements.hitlreview";
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

    if (data == null || data.isEmpty()) {
      return Promise.ofException(
          new IllegalArgumentException("Input data required for HITL review"));
    }

    if (!data.containsKey("requirementId")) {
      return Promise.ofException(new IllegalArgumentException("Field 'requirementId' required"));
    }

    return Promise.of(context);
  }

  /**
   * Determines what review action to take based on input. Default is 'create' if no action
   * specified.
   */
  private Promise<Map<String, Object>> determineReviewAction(WorkflowContext context) {
    Map<String, Object> data = context.getData();
    String action = (String) data.getOrDefault("reviewAction", ACTION_CREATE);
    String requirementId = (String) data.get("requirementId");

    Map<String, Object> actionResult = new HashMap<>();
    actionResult.put("action", action);
    actionResult.put("requirementId", requirementId);
    actionResult.put("tenantId", context.getTenantId());
    actionResult.put("workflowId", context.getWorkflowId());

    // Copy relevant data forward
    actionResult.put(
        "functionalRequirements", data.getOrDefault("functionalRequirements", List.of()));
    actionResult.put(
        "nonFunctionalRequirements", data.getOrDefault("nonFunctionalRequirements", List.of()));
    actionResult.put("acceptanceCriteria", data.getOrDefault("acceptanceCriteria", List.of()));
    actionResult.put("policyFindings", data.getOrDefault("policyFindings", List.of()));

    // Review-specific fields
    if (data.containsKey("reviewerId")) {
      actionResult.put("reviewerId", data.get("reviewerId"));
    }
    if (data.containsKey("reviewComments")) {
      actionResult.put("reviewComments", data.get("reviewComments"));
    }
    if (data.containsKey("requestedChanges")) {
      actionResult.put("requestedChanges", data.get("requestedChanges"));
    }

    return Promise.of(actionResult);
  }

  private Promise<Map<String, Object>> processReviewAction(
      WorkflowContext context, Map<String, Object> actionResult) {
    String action = (String) actionResult.get("action");

    return switch (action) {
      case ACTION_CREATE -> createReview(actionResult);
      case ACTION_SUBMIT -> submitForReview(actionResult);
      case ACTION_APPROVE -> approveReview(actionResult);
      case ACTION_REJECT -> rejectReview(actionResult);
      case ACTION_REQUEST_CHANGES -> requestChanges(actionResult);
      default -> Promise.ofException(
          new IllegalArgumentException("Unknown review action: " + action));
    };
  }

  private Promise<Map<String, Object>> createReview(Map<String, Object> data) {
    String reviewId = UUID.randomUUID().toString();
    Instant now = Instant.now();

    Map<String, Object> review = new HashMap<>(data);
    review.put("reviewId", reviewId);
    review.put("reviewState", STATE_PENDING_REVIEW);
    review.put("createdAt", now.toString());
    review.put("updatedAt", now.toString());
    review.put(
        "stateHistory",
        List.of(
            Map.of("state", STATE_PENDING_REVIEW, "timestamp", now.toString(), "actor", "system")));
    review.put("reviewType", "requirement_approval");
    review.put("priority", determinePriority(data));
    review.put("dueDate", calculateDueDate(now, 3)); // 3 business days default

    return Promise.of(review);
  }

  private Promise<Map<String, Object>> submitForReview(Map<String, Object> data) {
    String reviewerId = (String) data.get("reviewerId");
    if (reviewerId == null || reviewerId.isBlank()) {
      return Promise.ofException(
          new IllegalArgumentException("reviewerId required for submit action"));
    }

    Instant now = Instant.now();
    data.put("reviewState", STATE_IN_REVIEW);
    data.put("assignedReviewerId", reviewerId);
    data.put("assignedAt", now.toString());
    data.put("updatedAt", now.toString());

    addStateHistory(data, STATE_IN_REVIEW, reviewerId);

    return Promise.of(data);
  }

  private Promise<Map<String, Object>> approveReview(Map<String, Object> data) {
    String reviewerId = (String) data.get("reviewerId");
    String comments = (String) data.getOrDefault("reviewComments", "");

    Instant now = Instant.now();
    data.put("reviewState", STATE_APPROVED);
    data.put("approvedBy", reviewerId);
    data.put("approvedAt", now.toString());
    data.put("updatedAt", now.toString());
    data.put("approvalComments", comments);
    data.put("canProceedToPublish", true);

    addStateHistory(data, STATE_APPROVED, reviewerId);

    return Promise.of(data);
  }

  private Promise<Map<String, Object>> rejectReview(Map<String, Object> data) {
    String reviewerId = (String) data.get("reviewerId");
    String comments = (String) data.getOrDefault("reviewComments", "Requirement rejected");

    Instant now = Instant.now();
    data.put("reviewState", STATE_REJECTED);
    data.put("rejectedBy", reviewerId);
    data.put("rejectedAt", now.toString());
    data.put("updatedAt", now.toString());
    data.put("rejectionReason", comments);
    data.put("canProceedToPublish", false);
    data.put("requiresRework", true);

    addStateHistory(data, STATE_REJECTED, reviewerId);

    return Promise.of(data);
  }

  private Promise<Map<String, Object>> requestChanges(Map<String, Object> data) {
    String reviewerId = (String) data.get("reviewerId");
    List<String> requestedChanges = getListOrEmpty(data, "requestedChanges");

    if (requestedChanges.isEmpty()) {
      return Promise.ofException(new IllegalArgumentException("requestedChanges list required"));
    }

    Instant now = Instant.now();
    data.put("reviewState", STATE_CHANGES_REQUESTED);
    data.put("changesRequestedBy", reviewerId);
    data.put("changesRequestedAt", now.toString());
    data.put("updatedAt", now.toString());
    data.put("pendingChanges", requestedChanges);
    data.put("canProceedToPublish", false);
    data.put("awaitingChanges", true);

    addStateHistory(data, STATE_CHANGES_REQUESTED, reviewerId);

    return Promise.of(data);
  }

  private Promise<Map<String, Object>> persistReviewState(Map<String, Object> review) {
    return dbClient
        .insert("requirement_reviews", review)
        .map(
            dbResult -> {
              review.put("persisted", true);
              review.put("collection", "requirement_reviews");
              return review;
            });
  }

  private Promise<Map<String, Object>> publishEvents(Map<String, Object> data) {
    String reviewState = (String) data.get("reviewState");
    String action = (String) data.get("action");

    String eventType =
        switch (reviewState) {
          case STATE_PENDING_REVIEW -> "requirements.review.created";
          case STATE_IN_REVIEW -> "requirements.review.assigned";
          case STATE_APPROVED -> "requirements.review.approved";
          case STATE_REJECTED -> "requirements.review.rejected";
          case STATE_CHANGES_REQUESTED -> "requirements.review.changes_requested";
          default -> "requirements.review.state_changed";
        };

    Map<String, Object> event =
        Map.of(
            "eventType",
            eventType,
            "requirementId",
            data.get("requirementId"),
            "reviewId",
            data.getOrDefault("reviewId", ""),
            "reviewState",
            reviewState,
            "action",
            action,
            "timestamp",
            Instant.now().toString());

    return eventClient.publish("requirements.hitl.review", event).map($ -> data);
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

  private void handleError(Throwable error, WorkflowContext context) {
    Map<String, Object> errorEvent =
        Map.of(
            "eventType", "requirements.review.error",
            "requirementId", context.getData().getOrDefault("requirementId", "unknown"),
            "error", error.getMessage(),
            "timestamp", Instant.now().toString());

    eventClient.publish("requirements.errors", errorEvent);
  }

  // --- Helper Methods ---

  private String determinePriority(Map<String, Object> data) {
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> findings =
        (List<Map<String, Object>>) data.getOrDefault("policyFindings", List.of());

    boolean hasBlock = findings.stream().anyMatch(f -> "BLOCK".equals(f.get("action")));
    boolean hasReviewRequired =
        findings.stream().anyMatch(f -> "REQUIRE_REVIEW".equals(f.get("action")));

    if (hasBlock) return "CRITICAL";
    if (hasReviewRequired) return "HIGH";
    return "NORMAL";
  }

  private String calculateDueDate(Instant from, int businessDays) {
    // Simple calculation - add days (ignoring weekends for simplicity)
    return from.plusSeconds((long) businessDays * 24 * 60 * 60).toString();
  }

  @SuppressWarnings("unchecked")
  private void addStateHistory(Map<String, Object> data, String newState, String actor) {
    List<Map<String, Object>> history =
        new ArrayList<>((List<Map<String, Object>>) data.getOrDefault("stateHistory", List.of()));
    history.add(
        Map.of(
            "state",
            newState,
            "timestamp",
            Instant.now().toString(),
            "actor",
            actor != null ? actor : "system"));
    data.put("stateHistory", history);
  }

  @SuppressWarnings("unchecked")
  private List<String> getListOrEmpty(Map<String, Object> data, String key) {
    Object value = data.get(key);
    if (value instanceof List) {
      return (List<String>) value;
    }
    return List.of();
  }
}
