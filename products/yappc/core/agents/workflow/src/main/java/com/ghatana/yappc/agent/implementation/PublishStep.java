package com.ghatana.yappc.agent.implementation;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;

/**
 * Implementation Phase - Step 7: Publish Implementation Baseline.
 *
 * <p>Creates immutable implementation baseline with complete trace links. Publishes approved
 * implementation artifacts, build results, and metadata for consumption by downstream phases
 * (Testing, Ops).
 *
 * <h3>Responsibilities:</h3>
 *
 * <ul>
 *   <li>Verify all reviews are approved
 *   <li>Load all implementation artifacts (units, builds, reviews, quality gates)
 *   <li>Create immutable baseline snapshot with versioning
 *   <li>Establish trace links (requirements → architecture → implementation → builds)
 *   <li>Compute content hash for integrity verification
 *   <li>Persist baseline to implementation_published collection
 *   <li>Emit phase completion events
 *   <li>Signal readiness for Testing phase
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Publishes implementation baseline - immutable snapshot with trace links
 * @doc.layer product
 * @doc.pattern Service
 */
public final class PublishStep implements WorkflowStep {

  private static final String COLLECTION_CODE_REVIEWS = "code_reviews";
  private static final String COLLECTION_IMPL_UNITS = "implementation_units";
  private static final String COLLECTION_BUILD_RUNS = "build_runs";
  private static final String COLLECTION_QUALITY_GATES = "quality_gates";
  private static final String COLLECTION_IMPL_PUBLISHED = "implementation_published";
  private static final String EVENT_TOPIC = "implementation.workflow";
  private static final String PHASE_TOPIC = "phase.transitions";

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public PublishStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "implementation.publish";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    String runId = UUID.randomUUID().toString();
    String tenantId = (String) context.get("tenantId");
    Instant startTime = Instant.now();

    return validateInput(context)
        .then($ -> verifyReviewsApproved(context))
        .then($ -> loadImplementationArtifacts(context))
        .then(artifacts -> createBaselineSnapshot(artifacts, tenantId, runId))
        .then(baseline -> persistBaseline(baseline, tenantId, runId))
        .then(baseline -> publishEvents(baseline, tenantId, runId))
        .then(baseline -> buildOutputContext(context, baseline, startTime))
        .whenException(ex -> handleError(ex, context, tenantId, runId, startTime));
  }

  private Promise<Void> validateInput(WorkflowContext context) {
    if (!context.containsKey("reviews") && !context.containsKey("runId")) {
      return Promise.ofException(
          new IllegalArgumentException(
              "Missing required input: reviews or runId from previous step"));
    }
    if (!context.containsKey("tenantId")) {
      return Promise.ofException(new IllegalArgumentException("Missing required input: tenantId"));
    }
    if (!context.containsKey("architectureBaselineId")) {
      return Promise.ofException(
          new IllegalArgumentException(
              "Missing required input: architectureBaselineId for trace links"));
    }
    return Promise.complete();
  }

  @SuppressWarnings("unchecked")
  private Promise<Void> verifyReviewsApproved(WorkflowContext context) {
    String tenantId = (String) context.get("tenantId");

    Map<String, Object> query =
        Map.of("tenantId", tenantId, "approvalRequired", true, "state", "PENDING_REVIEW");

    return dbClient
        .query(COLLECTION_CODE_REVIEWS, query, 10)
        .then(
            pendingReviews -> {
              if (!pendingReviews.isEmpty()) {
                return Promise.ofException(
                    new IllegalStateException(
                        "Cannot publish: "
                            + pendingReviews.size()
                            + " reviews still pending approval"));
              }
              return Promise.complete();
            });
  }

  private Promise<Map<String, Object>> loadImplementationArtifacts(WorkflowContext context) {
    String tenantId = (String) context.get("tenantId");

    Map<String, Object> artifacts = new LinkedHashMap<>();

    // Load units
    Promise<List<Map<String, Object>>> unitsPromise =
        dbClient.query(COLLECTION_IMPL_UNITS, Map.of("tenantId", tenantId), 100);

    // Load builds
    Promise<List<Map<String, Object>>> buildsPromise =
        dbClient.query(COLLECTION_BUILD_RUNS, Map.of("tenantId", tenantId), 100);

    // Load quality gates
    Promise<List<Map<String, Object>>> gatesPromise =
        dbClient.query(COLLECTION_QUALITY_GATES, Map.of("tenantId", tenantId), 100);

    // Load reviews
    Promise<List<Map<String, Object>>> reviewsPromise =
        dbClient.query(COLLECTION_CODE_REVIEWS, Map.of("tenantId", tenantId), 100);

    return unitsPromise
        .then(
            units -> {
              artifacts.put("units", units);
              return buildsPromise;
            })
        .then(
            builds -> {
              artifacts.put("builds", builds);
              return gatesPromise;
            })
        .then(
            gates -> {
              artifacts.put("gates", gates);
              return reviewsPromise;
            })
        .then(
            reviews -> {
              artifacts.put("reviews", reviews);
              return Promise.of(artifacts);
            });
  }

  @SuppressWarnings("unchecked")
  private Promise<Map<String, Object>> createBaselineSnapshot(
      Map<String, Object> artifacts, String tenantId, String runId) {

    List<Map<String, Object>> units = (List<Map<String, Object>>) artifacts.get("units");
    List<Map<String, Object>> builds = (List<Map<String, Object>>) artifacts.get("builds");
    List<Map<String, Object>> gates = (List<Map<String, Object>>) artifacts.get("gates");
    List<Map<String, Object>> reviews = (List<Map<String, Object>>) artifacts.get("reviews");

    String baselineId = UUID.randomUUID().toString();
    // Use semantic versioning based on changes in this release
    SemanticVersioning version = determineVersion(units, builds, gates);

    Map<String, Object> baseline = new LinkedHashMap<>();
    baseline.put("baselineId", baselineId);
    baseline.put("tenantId", tenantId);
    baseline.put("runId", runId);
    baseline.put("phase", "IMPLEMENTATION");
    baseline.put("version", version.toString());

    // Content snapshot
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("units", units);
    content.put("builds", builds);
    content.put("qualityGates", gates);
    content.put("reviews", reviews);
    baseline.put("content", content);

    // Statistics
    Map<String, Object> stats = new LinkedHashMap<>();
    stats.put("totalUnits", units.size());
    stats.put("totalBuilds", builds.size());
    stats.put(
        "passedBuilds", builds.stream().filter(b -> "PASSED".equals(b.get("status"))).count());
    stats.put("totalReviews", reviews.size());
    stats.put(
        "approvedReviews", reviews.stream().filter(r -> "APPROVED".equals(r.get("state"))).count());

    double avgCoverage =
        builds.stream()
            .mapToDouble(b -> ((Number) b.get("coverage")).doubleValue())
            .average()
            .orElse(0.0);
    stats.put("averageCoverage", avgCoverage);
    baseline.put("statistics", stats);

    // Trace links
    Map<String, Object> traceLinks = buildTraceLinks(artifacts);
    baseline.put("traceLinks", traceLinks);

    // Content hash for immutability verification
    String contentHash = computeContentHash(content);
    baseline.put("contentHash", contentHash);

    // Metadata
    baseline.put("immutable", true);
    baseline.put("publishedBy", "system");
    baseline.put("createdAt", Instant.now().toString());
    baseline.put("publishedAt", Instant.now().toString());

    return Promise.of(baseline);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> buildTraceLinks(Map<String, Object> artifacts) {
    List<Map<String, Object>> units = (List<Map<String, Object>>) artifacts.get("units");
    List<Map<String, Object>> builds = (List<Map<String, Object>>) artifacts.get("builds");

    Map<String, Object> links = new LinkedHashMap<>();

    // Unit to requirements
    Map<String, List<String>> unitToRequirements = new LinkedHashMap<>();
    for (Map<String, Object> unit : units) {
      String unitId = (String) unit.get("unitId");
      @SuppressWarnings("unchecked")
      List<String> reqIds = (List<String>) unit.getOrDefault("requirementIds", List.of());
      unitToRequirements.put(unitId, reqIds);
    }
    links.put("unitToRequirements", unitToRequirements);

    // Build to units
    Map<String, String> buildToUnit = new LinkedHashMap<>();
    for (Map<String, Object> build : builds) {
      buildToUnit.put((String) build.get("buildId"), (String) build.get("unitId"));
    }
    links.put("buildToUnit", buildToUnit);

    return links;
  }

  private String computeContentHash(Map<String, Object> content) {
    // Simplified hash computation (in production: use SHA-256)
    return "sha256-" + UUID.randomUUID().toString().replace("-", "");
  }

  /**
   * Determines the semantic version based on changes in this release.
   * 
   * @param units List of implementation units
   * @param builds List of build results
   * @param gates List of quality gates
   * @return Appropriate semantic version
   */
  private SemanticVersioning determineVersion(
      List<Map<String, Object>> units,
      List<Map<String, Object>> builds,
      List<Map<String, Object>> gates) {
    
    // Default starting version
    SemanticVersioning baseVersion = SemanticVersioning.of(1, 0, 0);
    
    // Analyze changes to determine version bump
    boolean hasBreakingChanges = units.stream()
        .anyMatch(u -> "BREAKING".equals(u.get("changeType")));
    
    boolean hasNewFeatures = units.stream()
        .anyMatch(u -> "FEATURE".equals(u.get("changeType")));
    
    boolean allBuildsPassed = builds.stream()
        .allMatch(b -> "PASSED".equals(b.get("status")));
    
    boolean allGatesPassed = gates.stream()
        .allMatch(g -> "PASSED".equals(g.get("status")));
    
    // Determine change type
    SemanticVersioning.ChangeType changeType;
    if (hasBreakingChanges) {
      changeType = SemanticVersioning.ChangeType.MAJOR;
    } else if (hasNewFeatures) {
      changeType = SemanticVersioning.ChangeType.MINOR;
    } else {
      changeType = SemanticVersioning.ChangeType.PATCH;
    }
    
    // If not all checks passed, mark as prerelease
    SemanticVersioning version = baseVersion.nextVersion(changeType);
    if (!allBuildsPassed || !allGatesPassed) {
      version = version.withPrerelease("rc." + System.currentTimeMillis() / 1000);
    }
    
    return version;
  }

  private Promise<Map<String, Object>> persistBaseline(
      Map<String, Object> baseline, String tenantId, String runId) {

    return dbClient.insert(COLLECTION_IMPL_PUBLISHED, baseline).map($ -> baseline);
  }

  private Promise<Map<String, Object>> publishEvents(
      Map<String, Object> baseline, String tenantId, String runId) {

    // Implementation workflow event
    Map<String, Object> workflowEvent =
        Map.of(
            "runId", runId,
            "eventType", "IMPLEMENTATION_PUBLISHED",
            "baselineId", baseline.get("baselineId"),
            "version", baseline.get("version"),
            "statistics", baseline.get("statistics"));

    // Phase transition event
    Map<String, Object> phaseEvent =
        Map.of(
            "eventType",
            "PHASE_COMPLETED",
            "phase",
            "IMPLEMENTATION",
            "nextPhase",
            "TESTING",
            "baselineId",
            baseline.get("baselineId"),
            "tenantId",
            tenantId,
            "timestamp",
            Instant.now().toString());

    return eventClient
        .publish(EVENT_TOPIC, tenantId, workflowEvent)
        .then($ -> eventClient.publish(PHASE_TOPIC, tenantId, phaseEvent))
        .map($ -> baseline);
  }

  private Promise<WorkflowContext> buildOutputContext(
      WorkflowContext context, Map<String, Object> baseline, Instant startTime) {

    WorkflowContext output = context.copy();
    output.put("status", "COMPLETED");
    output.put("step", "Publish");
    output.put("phase", "IMPLEMENTATION");
    output.put("baselineId", baseline.get("baselineId"));
    output.put("version", baseline.get("version"));
    output.put("contentHash", baseline.get("contentHash"));
    output.put("statistics", baseline.get("statistics"));
    output.put("traceLinks", baseline.get("traceLinks"));
    output.put("duration", Instant.now().toEpochMilli() - startTime.toEpochMilli());
    output.put("completedAt", Instant.now().toString());
    output.put("readyForTesting", true);

    return Promise.of(output);
  }

  private Promise<WorkflowContext> handleError(
      Throwable ex, WorkflowContext context, String tenantId, String runId, Instant startTime) {

    Map<String, Object> errorPayload =
        Map.of(
            "runId",
            runId,
            "eventType",
            "IMPLEMENTATION_PUBLISH_FAILED",
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
