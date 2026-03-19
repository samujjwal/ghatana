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
 * AEP Step: ARCHITECTURE / Publish.
 *
 * <p>Publishes approved architecture artifacts to downstream consumers.
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
 * @doc.purpose Architecture phase publish step - publishes architecture artifacts
 * @doc.layer product
 * @doc.pattern Service
 */
public final class PublishStep implements WorkflowStep {

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public PublishStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  public static final String STATUS_PUBLISHED = "PUBLISHED";

  @Override
  public String getStepId() {
    return "architecture.publish";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    return validateInput(context)
        .then(this::verifyApprovalStatus)
        .then(this::createArchitectureBaseline)
        .then(this::createTraceLinks)
        .then(this::persistPublishedArchitecture)
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

  private Promise<Map<String, Object>> verifyApprovalStatus(WorkflowContext context) {
    Map<String, Object> data = context.getData();
    String reviewState = (String) data.getOrDefault("reviewState", "");
    boolean canProceed = Boolean.TRUE.equals(data.get("canProceedToPublish"));

    if (!canProceed && !"APPROVED".equals(reviewState)) {
      return Promise.ofException(
          new IllegalStateException(
              "Architecture must be approved before publishing. Current state: " + reviewState));
    }

    Map<String, Object> result = new HashMap<>(data);
    result.put("approvalVerified", true);
    return Promise.of(result);
  }

  private Promise<Map<String, Object>> createArchitectureBaseline(Map<String, Object> data) {
    String baselineId = UUID.randomUUID().toString();
    Instant now = Instant.now();

    Map<String, Object> baseline = new HashMap<>(data);
    baseline.put("baselineId", baselineId);
    baseline.put("status", STATUS_PUBLISHED);
    baseline.put("publishedAt", now.toString());
    baseline.put("version", "v1.0");
    baseline.put("immutable", true);

    return Promise.of(baseline);
  }

  private Promise<Map<String, Object>> createTraceLinks(Map<String, Object> baseline) {
    String architectureId = baseline.get("architectureId").toString();
    String baselineId = baseline.get("baselineId").toString();
    String reqBaselineId = (String) baseline.get("baselineId");

    List<Map<String, Object>> traceLinks = new ArrayList<>();
    traceLinks.add(
        Map.of(
            "sourceId", baselineId,
            "sourceType", "architecture_baseline",
            "targetId", reqBaselineId,
            "targetType", "requirements_baseline",
            "linkType", "realizes"));

    baseline.put("traceLinks", traceLinks);
    return Promise.of(baseline);
  }

  private Promise<Map<String, Object>> persistPublishedArchitecture(Map<String, Object> baseline) {
    return dbClient
        .insert("architecture_published", baseline)
        .map(
            $ -> {
              baseline.put("persisted", true);
              return baseline;
            });
  }

  private Promise<Map<String, Object>> publishEvents(Map<String, Object> data) {
    return eventClient
        .publish(
            "architecture.published",
            Map.of(
                "eventType", "architecture.published",
                "architectureId", data.get("architectureId"),
                "baselineId", data.get("baselineId"),
                "version", data.get("version"),
                "timestamp", Instant.now().toString()))
        .then(
            $ ->
                eventClient.publish(
                    "phase.transitions",
                    Map.of(
                        "eventType", "architecture.ready_for_implementation",
                        "baselineId", data.get("baselineId"),
                        "timestamp", Instant.now().toString())))
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
            "eventType", "architecture.publish.error",
            "error", error.getMessage(),
            "timestamp", Instant.now().toString()));
  }
}
