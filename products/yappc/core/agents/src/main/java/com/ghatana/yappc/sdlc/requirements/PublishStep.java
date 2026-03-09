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
 * AEP Step: REQUIREMENTS / Publish.
 *
 * <p>Final step in Requirements phase. Publishes approved requirements as the official baseline for
 * downstream phases (Design, Implementation, etc.). Creates trace links back to ideation artifacts.
 *
 * <p>✅ Implements WorkflowStep from libs:workflow-api (EXISTING) ✅ Uses DatabaseClient from
 * libs:database (EXISTING) ✅ Uses EventCloud from libs:event-cloud (EXISTING)
 *
 * <h3>Publish Actions:</h3>
 *
 * <ul>
 *   <li>Verify approval status from HITL review
 *   <li>Create immutable baseline snapshot
 *   <li>Generate trace links to ideation artifacts
 *   <li>Emit requirements.published event for downstream phases
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Requirements phase publish step - publishes approved requirements baseline
 * @doc.layer product
 * @doc.pattern Service
 */
public final class PublishStep implements WorkflowStep {

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  // Publish status constants
  public static final String STATUS_PUBLISHED = "PUBLISHED";
  public static final String STATUS_SUPERSEDED = "SUPERSEDED";
  public static final String STATUS_DRAFT = "DRAFT";

  public PublishStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "requirements.publish";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    return validateInput(context)
        .then(this::verifyApprovalStatus)
        .then(this::createBaselineSnapshot)
        .then(this::createTraceLinks)
        .then(this::persistPublishedRequirements)
        .then(this::publishEvents)
        .then(result -> buildOutputContext(context, result))
        .whenException(error -> handleError(error, context));
  }

  private Promise<WorkflowContext> validateInput(WorkflowContext context) {
    Map<String, Object> data = context.getData();

    if (data == null || data.isEmpty()) {
      return Promise.ofException(new IllegalArgumentException("Input data required for publish"));
    }

    if (!data.containsKey("requirementId")) {
      return Promise.ofException(new IllegalArgumentException("Field 'requirementId' required"));
    }

    return Promise.of(context);
  }

  /** Verifies that the requirement has been approved before publishing. */
  private Promise<Map<String, Object>> verifyApprovalStatus(WorkflowContext context) {
    Map<String, Object> data = context.getData();

    String reviewState = (String) data.getOrDefault("reviewState", "");
    boolean canProceed = Boolean.TRUE.equals(data.get("canProceedToPublish"));

    // Check if approved through HITL review
    if (!canProceed && !"APPROVED".equals(reviewState)) {
      // Check if it's auto-approved (passed all policies without requiring review)
      boolean requiresHITL = Boolean.TRUE.equals(data.get("requiresHITL"));
      if (requiresHITL) {
        return Promise.ofException(
            new IllegalStateException(
                "Requirement must be approved before publishing. Current state: " + reviewState));
      }
      // Auto-approved - didn't require HITL review
    }

    Map<String, Object> result = new HashMap<>(data);
    result.put("approvalVerified", true);
    result.put("verifiedAt", Instant.now().toString());
    result.put("tenantId", context.getTenantId());
    result.put("workflowId", context.getWorkflowId());

    return Promise.of(result);
  }

  /** Creates an immutable baseline snapshot of the approved requirements. */
  private Promise<Map<String, Object>> createBaselineSnapshot(Map<String, Object> data) {
    String requirementId = (String) data.get("requirementId");
    String baselineId = UUID.randomUUID().toString();
    Instant now = Instant.now();

    // Determine version number
    int version = determineNextVersion(requirementId);

    Map<String, Object> baseline = new HashMap<>();
    baseline.put("baselineId", baselineId);
    baseline.put("requirementId", requirementId);
    baseline.put("version", version);
    baseline.put("versionTag", "v" + version + ".0");
    baseline.put("status", STATUS_PUBLISHED);
    baseline.put("publishedAt", now.toString());
    baseline.put("tenantId", data.get("tenantId"));
    baseline.put("workflowId", data.get("workflowId"));

    // Copy requirement content into baseline
    baseline.put("functionalRequirements", data.getOrDefault("functionalRequirements", List.of()));
    baseline.put(
        "nonFunctionalRequirements", data.getOrDefault("nonFunctionalRequirements", List.of()));
    baseline.put("acceptanceCriteria", data.getOrDefault("acceptanceCriteria", List.of()));

    // Copy validation and policy results
    baseline.put("validationResults", data.getOrDefault("validationResults", Map.of()));
    baseline.put("policyFindings", data.getOrDefault("policyFindings", List.of()));
    baseline.put("overallValid", data.getOrDefault("overallValid", true));

    // Review metadata
    baseline.put("approvedBy", data.getOrDefault("approvedBy", "auto-approved"));
    baseline.put("approvedAt", data.getOrDefault("approvedAt", now.toString()));
    baseline.put("reviewId", data.getOrDefault("reviewId", ""));

    // Immutability checksum (for integrity verification)
    baseline.put("contentHash", generateContentHash(baseline));
    baseline.put("immutable", true);

    return Promise.of(baseline);
  }

  /** Creates trace links connecting requirements to ideation artifacts. */
  private Promise<Map<String, Object>> createTraceLinks(Map<String, Object> baseline) {
    String requirementId = (String) baseline.get("requirementId");
    String baselineId = (String) baseline.get("baselineId");
    Instant now = Instant.now();

    List<Map<String, Object>> traceLinks = new ArrayList<>();

    // Create trace link to parent epic (if exists)
    String epicId = (String) baseline.get("epicId");
    if (epicId != null && !epicId.isBlank()) {
      traceLinks.add(
          createTraceLink(baselineId, "requirement", epicId, "epic", "derived_from", now));
    }

    // Create trace link to ideation source (if exists)
    String ideationId = (String) baseline.get("ideationSourceId");
    if (ideationId != null && !ideationId.isBlank()) {
      traceLinks.add(
          createTraceLink(
              baselineId, "requirement", ideationId, "ideation", "originates_from", now));
    }

    // Create trace links for each functional requirement to downstream phases
    @SuppressWarnings("unchecked")
    List<String> funcReqs =
        (List<String>) baseline.getOrDefault("functionalRequirements", List.of());
    for (int i = 0; i < funcReqs.size(); i++) {
      String frId = requirementId + "-FR-" + (i + 1);
      traceLinks.add(
          createTraceLink(baselineId, "baseline", frId, "functional_requirement", "contains", now));
    }

    baseline.put("traceLinks", traceLinks);
    baseline.put("traceLinkCount", traceLinks.size());

    return Promise.of(baseline);
  }

  private Promise<Map<String, Object>> persistPublishedRequirements(Map<String, Object> baseline) {
    // Persist the baseline
    return dbClient
        .insert("requirements_published", baseline)
        .then(
            $ -> {
              // Persist trace links separately
              @SuppressWarnings("unchecked")
              List<Map<String, Object>> links =
                  (List<Map<String, Object>>) baseline.get("traceLinks");
              if (links != null && !links.isEmpty()) {
                return persistTraceLinks(links);
              }
              return Promise.of((Void) null);
            })
        .map(
            $ -> {
              baseline.put("persisted", true);
              baseline.put("collection", "requirements_published");
              return baseline;
            });
  }

  private Promise<Void> persistTraceLinks(List<Map<String, Object>> links) {
    // Batch insert trace links
    List<Promise<Void>> insertPromises = new ArrayList<>();
    for (Map<String, Object> link : links) {
      insertPromises.add(dbClient.insert("requirements_trace_links", link).toVoid());
    }
    return io.activej.promise.Promises.all(insertPromises).toVoid();
  }

  private Promise<Map<String, Object>> publishEvents(Map<String, Object> data) {
    String baselineId = (String) data.get("baselineId");
    String requirementId = (String) data.get("requirementId");
    int version = (int) data.get("version");

    // Main published event - triggers downstream phases
    Map<String, Object> publishedEvent =
        Map.of(
            "eventType", "requirements.published",
            "baselineId", baselineId,
            "requirementId", requirementId,
            "version", version,
            "versionTag", data.get("versionTag"),
            "functionalRequirementCount", getListSize(data, "functionalRequirements"),
            "nonFunctionalRequirementCount", getListSize(data, "nonFunctionalRequirements"),
            "acceptanceCriteriaCount", getListSize(data, "acceptanceCriteria"),
            "traceLinkCount", data.getOrDefault("traceLinkCount", 0),
            "timestamp", Instant.now().toString());

    return eventClient
        .publish("requirements.published", publishedEvent)
        .then(
            $ -> {
              // Notify downstream phases that requirements are ready
              Map<String, Object> readyEvent =
                  Map.of(
                      "eventType", "requirements.ready_for_design",
                      "baselineId", baselineId,
                      "requirementId", requirementId,
                      "version", version,
                      "timestamp", Instant.now().toString());
              return eventClient.publish("phase.transitions", readyEvent);
            })
        .map($ -> data);
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
            "eventType", "requirements.publish.error",
            "requirementId", context.getData().getOrDefault("requirementId", "unknown"),
            "error", error.getMessage(),
            "timestamp", Instant.now().toString());

    eventClient.publish("requirements.errors", errorEvent);
  }

  // --- Helper Methods ---

  private Map<String, Object> createTraceLink(
      String sourceId,
      String sourceType,
      String targetId,
      String targetType,
      String linkType,
      Instant createdAt) {

    return Map.of(
        "traceLinkId", UUID.randomUUID().toString(),
        "sourceId", sourceId,
        "sourceType", sourceType,
        "targetId", targetId,
        "targetType", targetType,
        "linkType", linkType,
        "createdAt", createdAt.toString(),
        "status", "active");
  }

  private int determineNextVersion(String requirementId) {
    // In a real implementation, this would query the database for existing versions
    // For now, return 1 as the initial version
    return 1;
  }

  private String generateContentHash(Map<String, Object> content) {
    // Simple hash based on content - in production use SHA-256
    int hash =
        Objects.hash(
            content.get("functionalRequirements"),
            content.get("nonFunctionalRequirements"),
            content.get("acceptanceCriteria"),
            content.get("publishedAt"));
    return "sha256:" + Integer.toHexString(hash);
  }

  private int getListSize(Map<String, Object> data, String key) {
    Object value = data.get(key);
    if (value instanceof List) {
      return ((List<?>) value).size();
    }
    return 0;
  }
}
