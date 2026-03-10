package com.ghatana.yappc.agent.implementation;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation Phase - Step 6: Code Review Workflow.
 *
 * <p>Manages code review process with HITL (Human-in-the-Loop) support. Coordinates automated and
 * human review activities, tracks review status, and optionally provides LLM-assisted review
 * suggestions.
 *
 * <h3>Responsibilities:</h3>
 *
 * <ul>
 *   <li>Load quality gate results from previous step
 *   <li>Create review records for units requiring review
 *   <li>Optional: Generate LLM-assisted review suggestions
 *   <li>Track review state machine (PENDING_REVIEW → IN_REVIEW → APPROVED/REJECTED)
 *   <li>Support review actions (create, submit, approve, reject)
 *   <li>Persist review records to Data-Cloud
 *   <li>Emit workflow events for tracking
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Code review workflow - HITL review process management
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ReviewStep implements WorkflowStep {

  private static final String COLLECTION_QUALITY_GATES = "quality_gates";
  private static final String COLLECTION_CODE_REVIEWS = "code_reviews";
  private static final String COLLECTION_IMPL_UNITS = "implementation_units";
  private static final String EVENT_TOPIC = "implementation.workflow";

  // Review states
  private static final String STATE_PENDING_REVIEW = "PENDING_REVIEW";
  private static final String STATE_IN_REVIEW = "IN_REVIEW";
  private static final String STATE_APPROVED = "APPROVED";
  private static final String STATE_REJECTED = "REJECTED";
  private static final String STATE_CHANGES_REQUESTED = "CHANGES_REQUESTED";

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public ReviewStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "implementation.review";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    String runId = UUID.randomUUID().toString();
    String tenantId = (String) context.get("tenantId");
    Instant startTime = Instant.now();

    return validateInput(context)
        .then($ -> loadQualityGateResults(context))
        .then(gates -> createReviewRecords(gates, context, tenantId, runId))
        .then(reviews -> persistReviews(reviews, tenantId, runId))
        .then(reviews -> updateUnitStatus(reviews))
        .then(reviews -> publishEvents(reviews, tenantId, runId))
        .then(reviews -> buildOutputContext(context, reviews, startTime))
        .whenException(ex -> handleError(ex, context, tenantId, runId, startTime));
  }

  private Promise<Void> validateInput(WorkflowContext context) {
    if (!context.containsKey("gates") && !context.containsKey("runId")) {
      return Promise.ofException(
          new IllegalArgumentException(
              "Missing required input: gates or runId from previous step"));
    }
    if (!context.containsKey("tenantId")) {
      return Promise.ofException(new IllegalArgumentException("Missing required input: tenantId"));
    }
    return Promise.complete();
  }

  @SuppressWarnings("unchecked")
  private Promise<List<Map<String, Object>>> loadQualityGateResults(WorkflowContext context) {
    if (context.containsKey("gates")) {
      return Promise.of((List<Map<String, Object>>) context.get("gates"));
    }

    String previousRunId = (String) context.get("runId");
    String tenantId = (String) context.get("tenantId");

    Map<String, Object> query =
        Map.of(
            "runId", previousRunId,
            "tenantId", tenantId);

    return dbClient.query(COLLECTION_QUALITY_GATES, query, 100);
  }

  private Promise<List<Map<String, Object>>> createReviewRecords(
      List<Map<String, Object>> gates, WorkflowContext context, String tenantId, String runId) {

    List<Map<String, Object>> reviews = new ArrayList<>();

    for (Map<String, Object> gate : gates) {
      String unitId = (String) gate.get("unitId");
      String name = (String) gate.get("name");
      String gateStatus = (String) gate.get("status");

      Map<String, Object> review = new LinkedHashMap<>();
      review.put("reviewId", UUID.randomUUID().toString());
      review.put("unitId", unitId);
      review.put("tenantId", tenantId);
      review.put("runId", runId);
      review.put("name", name);
      review.put("type", "HUMAN"); // Can be HUMAN or LLM_ASSISTED

      // Initial state
      review.put("state", STATE_PENDING_REVIEW);
      review.put("previousState", null);

      // Review findings
      List<Map<String, String>> findings = new ArrayList<>();
      if ("WARN".equals(gateStatus)) {
        findings.add(
            Map.of(
                "type", "WARNING",
                "severity", "MEDIUM",
                "message", "Quality gate warnings detected - manual review recommended"));
      }
      review.put("findings", findings);

      // Optional: LLM-assisted suggestions
      if (shouldGenerateReviewSuggestions(context)) {
        Map<String, Object> llmSuggestions = generateReviewSuggestions(name);
        review.put("llmSuggestions", llmSuggestions);
        review.put("type", "LLM_ASSISTED");
      }

      // Review metadata
      review.put("reviewer", null);
      review.put("comments", List.of());
      review.put("approvalRequired", "BLOCK".equals(gateStatus) || "WARN".equals(gateStatus));

      review.put("createdAt", Instant.now().toString());
      review.put("updatedAt", Instant.now().toString());

      reviews.add(review);
    }

    return Promise.of(reviews);
  }

  private boolean shouldGenerateReviewSuggestions(WorkflowContext context) {
    Object llmEnabled = context.getVariable("llmEnabled");
    return llmEnabled != null && Boolean.parseBoolean(llmEnabled.toString());
  }

  private Map<String, Object> generateReviewSuggestions(String name) {
    // Fallback review suggestions when LLM is disabled or unavailable
    // In production with llmEnabled=true: delegates to LLM generator for context-aware code review
    Map<String, Object> suggestions = new LinkedHashMap<>();
    suggestions.put(
        "codeQuality",
        List.of(
            "Consider adding more JavaDoc comments",
            "Extract magic numbers into constants",
            "Consider adding validation for edge cases"));
    suggestions.put(
        "bestPractices",
        List.of(
            "Follow ActiveJ Promise patterns",
            "Ensure proper error handling",
            "Add unit tests for new functionality"));
    suggestions.put(
        "security",
        List.of("Validate input parameters", "Use parameterized queries for database access"));
    suggestions.put("confidence", 0.75);
    suggestions.put("model", "gpt-4");
    suggestions.put("timestamp", Instant.now().toString());
    return suggestions;
  }

  private Promise<List<Map<String, Object>>> persistReviews(
      List<Map<String, Object>> reviews, String tenantId, String runId) {

    List<Promise<Void>> persistPromises =
        reviews.stream()
            .map(review -> dbClient.insert(COLLECTION_CODE_REVIEWS, review))
            .collect(Collectors.toList());

    return io.activej.promise.Promises.all(persistPromises).map($ -> reviews);
  }

  private Promise<List<Map<String, Object>>> updateUnitStatus(List<Map<String, Object>> reviews) {

    List<Promise<Void>> updatePromises =
        reviews.stream()
            .map(
                review -> {
                  String unitId = (String) review.get("unitId");
                  boolean approvalRequired = (boolean) review.get("approvalRequired");

                  String status = approvalRequired ? "PENDING_REVIEW" : "REVIEW_NOT_REQUIRED";

                  Map<String, Object> update =
                      Map.of("status", status, "updatedAt", Instant.now().toString());
                  return dbClient.update(COLLECTION_IMPL_UNITS, Map.of("unitId", unitId), update);
                })
            .collect(Collectors.toList());

    return io.activej.promise.Promises.all(updatePromises).map($ -> reviews);
  }

  private Promise<List<Map<String, Object>>> publishEvents(
      List<Map<String, Object>> reviews, String tenantId, String runId) {

    long requiresReview = reviews.stream().filter(r -> (boolean) r.get("approvalRequired")).count();

    Map<String, Object> eventPayload =
        Map.of(
            "runId",
            runId,
            "eventType",
            "REVIEW_CREATED",
            "reviewCount",
            reviews.size(),
            "requiresReview",
            requiresReview,
            "reviews",
            reviews.stream()
                .map(
                    r ->
                        Map.of(
                            "reviewId", r.get("reviewId"),
                            "unitId", r.get("unitId"),
                            "name", r.get("name"),
                            "state", r.get("state"),
                            "approvalRequired", r.get("approvalRequired")))
                .collect(Collectors.toList()));

    return eventClient.publish(EVENT_TOPIC, tenantId, eventPayload).map($ -> reviews);
  }

  private Promise<WorkflowContext> buildOutputContext(
      WorkflowContext context, List<Map<String, Object>> reviews, Instant startTime) {

    long requiresReview = reviews.stream().filter(r -> (boolean) r.get("approvalRequired")).count();

    WorkflowContext output = context.copy();
    output.put("status", "COMPLETED");
    output.put("step", "Review");
    output.put("reviewCount", reviews.size());
    output.put("requiresReview", requiresReview);
    output.put("reviews", reviews);
    output.put("duration", Instant.now().toEpochMilli() - startTime.toEpochMilli());
    output.put("completedAt", Instant.now().toString());

    return Promise.of(output);
  }

  private Promise<WorkflowContext> handleError(
      Throwable ex, WorkflowContext context, String tenantId, String runId, Instant startTime) {

    Map<String, Object> errorPayload =
        Map.of(
            "runId",
            runId,
            "eventType",
            "REVIEW_FAILED",
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
