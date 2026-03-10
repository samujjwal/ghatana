package com.ghatana.yappc.agent.testing;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import io.activej.promise.Promise;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/**
 * Testing Phase - Step 7: Publish Test Results Baseline.
 *
 * <p>Creates an immutable test results baseline with trace links to requirements, architecture, and
 * implementation. This baseline is used by downstream phases (Ops, Enhancement) to verify
 * production readiness.
 *
 * <h3>Responsibilities:</h3>
 *
 * <ul>
 *   <li>Aggregate all test results (functional, security, performance, gate decision)
 *   <li>Create trace links (requirements → architecture → implementation → tests → results)
 *   <li>Generate baseline hash (SHA-256) for immutability
 *   <li>Persist test baseline to Data-Cloud
 *   <li>Emit TESTING_PHASE_COMPLETED event
 *   <li>Mark baseline as production-ready if gate PASSED
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Publishes immutable test results baseline with trace links
 * @doc.layer product
 * @doc.pattern Service
 */
public final class PublishStep implements WorkflowStep {

  private static final String COLLECTION_TEST_BASELINES = "test_baselines";
  private static final String COLLECTION_TEST_RUNS = "test_runs";
  private static final String COLLECTION_SECURITY_RESULTS = "security_scan_results";
  private static final String COLLECTION_PERFORMANCE_RESULTS = "performance_results";
  private static final String COLLECTION_RELEASE_GATES = "release_gates";
  private static final String EVENT_TOPIC = "testing.workflow";

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public PublishStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    String baselineId = UUID.randomUUID().toString();
    String tenantId = (String) context.get("tenantId");
    Instant startTime = Instant.now();

    return validateInput(context)
        .then($ -> loadAllResults(context, tenantId))
        .then(results -> buildBaseline(results, tenantId, baselineId, context))
        .then(baseline -> computeHash(baseline))
        .then(baseline -> persistBaseline(baseline, tenantId, baselineId))
        .then(baseline -> publishEvents(baseline, tenantId, baselineId))
        .then(baseline -> buildOutputContext(context, baseline, startTime))
        .whenException(ex -> handleError(ex, context, tenantId, baselineId, startTime));
  }

  private Promise<Void> validateInput(WorkflowContext context) {
    if (!context.containsKey("tenantId")) {
      return Promise.ofException(new IllegalArgumentException("Missing required input: tenantId"));
    }
    return Promise.complete();
  }

  private Promise<Map<String, Object>> loadAllResults(WorkflowContext context, String tenantId) {
    Promise<List<Map<String, Object>>> testRunPromise =
        dbClient.query(COLLECTION_TEST_RUNS, Map.of("tenantId", tenantId), 1);
    Promise<List<Map<String, Object>>> securityPromise =
        dbClient.query(COLLECTION_SECURITY_RESULTS, Map.of("tenantId", tenantId), 1);
    Promise<List<Map<String, Object>>> performancePromise =
        dbClient.query(COLLECTION_PERFORMANCE_RESULTS, Map.of("tenantId", tenantId), 1);
    Promise<List<Map<String, Object>>> gatePromise =
        dbClient.query(COLLECTION_RELEASE_GATES, Map.of("tenantId", tenantId), 1);

    return io.activej.promise.Promises.toList(
            testRunPromise, securityPromise, performancePromise, gatePromise)
        .map(
            results -> {
              Map<String, Object> allResults = new LinkedHashMap<>();
              allResults.put("testRun", results.get(0).isEmpty() ? null : results.get(0).get(0));
              allResults.put("security", results.get(1).isEmpty() ? null : results.get(1).get(0));
              allResults.put(
                  "performance", results.get(2).isEmpty() ? null : results.get(2).get(0));
              allResults.put("gate", results.get(3).isEmpty() ? null : results.get(3).get(0));
              return allResults;
            });
  }

  @SuppressWarnings("unchecked")
  private Promise<Map<String, Object>> buildBaseline(
      Map<String, Object> allResults, String tenantId, String baselineId, WorkflowContext context) {

    Map<String, Object> testRun = (Map<String, Object>) allResults.get("testRun");
    Map<String, Object> security = (Map<String, Object>) allResults.get("security");
    Map<String, Object> performance = (Map<String, Object>) allResults.get("performance");
    Map<String, Object> gate = (Map<String, Object>) allResults.get("gate");

    // Extract key metrics
    Map<String, Object> testSummary =
        testRun != null ? (Map<String, Object>) testRun.get("summary") : Map.of();
    Map<String, Object> securitySummary =
        security != null ? (Map<String, Object>) security.get("summary") : Map.of();
    Map<String, Object> perfValidation =
        performance != null ? (Map<String, Object>) performance.get("nfrValidation") : Map.of();

    // Build trace links
    Map<String, Object> traceLinks = new LinkedHashMap<>();
    traceLinks.put("requirementsBaseline", context.getOrDefault("requirementsBaseline", "unknown"));
    traceLinks.put("architectureBaseline", context.getOrDefault("architectureBaseline", "unknown"));
    traceLinks.put(
        "implementationBaseline", context.getOrDefault("implementationBaseline", "unknown"));
    traceLinks.put("testPlanId", context.getOrDefault("testPlanId", "unknown"));
    traceLinks.put("testRunId", testRun != null ? testRun.get("testRunId") : "unknown");
    traceLinks.put("securityScanId", security != null ? security.get("scanId") : "unknown");
    traceLinks.put(
        "performanceRunId", performance != null ? performance.get("perfRunId") : "unknown");
    traceLinks.put("gateId", gate != null ? gate.get("gateId") : "unknown");

    // Build baseline
    Map<String, Object> baseline = new LinkedHashMap<>();
    baseline.put("baselineId", baselineId);
    baseline.put("tenantId", tenantId);
    baseline.put("type", "TEST_RESULTS");
    baseline.put("version", "1.0.0");
    baseline.put("phase", "TESTING");

    // Test results summary
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("testPassRate", testSummary.getOrDefault("passRate", 0.0));
    summary.put("testCoverage", testSummary.getOrDefault("coverage", 0.0));
    summary.put("securityScore", securitySummary.getOrDefault("securityScore", 0.0));
    summary.put("securityCritical", securitySummary.getOrDefault("critical", 0L));
    summary.put("performanceStatus", perfValidation.getOrDefault("status", "UNKNOWN"));
    summary.put("gateDecision", gate != null ? gate.get("decision") : "UNKNOWN");
    baseline.put("summary", summary);

    // Detailed results
    baseline.put("testResults", testRun);
    baseline.put("securityResults", security);
    baseline.put("performanceResults", performance);
    baseline.put("gateDecision", gate);

    // Trace links
    baseline.put("traceLinks", traceLinks);

    // Metadata
    baseline.put("publishedAt", Instant.now().toString());
    baseline.put("publishedBy", "sdlc-agents");
    baseline.put("immutable", true);

    // Production readiness
    boolean productionReady = gate != null && "PASS".equals(gate.get("decision"));
    baseline.put("productionReady", productionReady);
    baseline.put("status", productionReady ? "APPROVED" : "BLOCKED");

    return Promise.of(baseline);
  }

  private Promise<Map<String, Object>> computeHash(Map<String, Object> baseline) {
    try {
      // Compute SHA-256 hash of baseline for immutability
      String baselineJson =
          baseline.toString(); // Simplified - use proper JSON serialization in prod
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(baselineJson.getBytes(StandardCharsets.UTF_8));

      StringBuilder hexString = new StringBuilder();
      for (byte b : hashBytes) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) hexString.append('0');
        hexString.append(hex);
      }

      baseline.put("hash", hexString.toString());
      baseline.put("hashAlgorithm", "SHA-256");

      return Promise.of(baseline);
    } catch (NoSuchAlgorithmException e) {
      return Promise.ofException(new RuntimeException("Failed to compute baseline hash", e));
    }
  }

  private Promise<Map<String, Object>> persistBaseline(
      Map<String, Object> baseline, String tenantId, String baselineId) {

    return dbClient.insert(COLLECTION_TEST_BASELINES, baseline).map($ -> baseline);
  }

  private Promise<Map<String, Object>> publishEvents(
      Map<String, Object> baseline, String tenantId, String baselineId) {

    Map<String, Object> eventPayload =
        Map.of(
            "eventType", "TESTING_PHASE_COMPLETED",
            "baselineId", baselineId,
            "status", baseline.get("status"),
            "productionReady", baseline.get("productionReady"),
            "summary", baseline.get("summary"));

    return eventClient.publish(EVENT_TOPIC, tenantId, eventPayload).map($ -> baseline);
  }

  private Promise<WorkflowContext> buildOutputContext(
      WorkflowContext context, Map<String, Object> baseline, Instant startTime) {

    WorkflowContext output = context.copy();
    output.put("status", "COMPLETED");
    output.put("step", "Publish");
    output.put("phase", "TESTING");
    output.put("baselineId", baseline.get("baselineId"));
    output.put("baseline", baseline);
    output.put("productionReady", baseline.get("productionReady"));
    output.put("testingPhaseComplete", true);
    output.put("duration", Instant.now().toEpochMilli() - startTime.toEpochMilli());
    output.put("completedAt", Instant.now().toString());

    return Promise.of(output);
  }

  private Promise<WorkflowContext> handleError(
      Throwable ex,
      WorkflowContext context,
      String tenantId,
      String baselineId,
      Instant startTime) {

    Map<String, Object> errorPayload =
        Map.of(
            "eventType",
            "TESTING_PUBLISH_FAILED",
            "baselineId",
            baselineId,
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
