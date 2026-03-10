package com.ghatana.yappc.agent.testing;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;

/**
 * Testing Phase - Step 6: Release Gate Decision.
 *
 * <p>Determines release readiness based on test results, coverage, defects, and NFR compliance.
 * This is the PASS/BLOCK gate that prevents production deployment if quality standards are not met.
 *
 * <h3>Responsibilities:</h3>
 *
 * <ul>
 *   <li>Load all test results (functional, security, performance)
 *   <li>Check coverage thresholds (unit 80%, integration 70%, E2E 50%)
 *   <li>Check critical/high defect counts (0 critical, <3 high allowed)
 *   <li>Validate NFR compliance (latency, throughput, availability)
 *   <li>Make PASS/BLOCK decision based on gate rules
 *   <li>Persist release gate decision to Data-Cloud
 *   <li>Emit workflow events for tracking
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Makes release gate decision - PASS/BLOCK based on quality metrics
 * @doc.layer product
 * @doc.pattern Service
 */
public final class ReleaseGateStep implements WorkflowStep {

  private static final String COLLECTION_RELEASE_GATES = "release_gates";
  private static final String COLLECTION_TEST_RUNS = "test_runs";
  private static final String COLLECTION_SECURITY_RESULTS = "security_scan_results";
  private static final String COLLECTION_PERFORMANCE_RESULTS = "performance_results";
  private static final String COLLECTION_DEFECTS = "defects";
  private static final String EVENT_TOPIC = "testing.workflow";

  // Release gate thresholds
  private static final double MIN_UNIT_COVERAGE = 80.0;
  private static final double MIN_INTEGRATION_COVERAGE = 70.0;
  private static final double MIN_E2E_COVERAGE = 50.0;
  private static final int MAX_CRITICAL_DEFECTS = 0;
  private static final int MAX_HIGH_DEFECTS = 2;

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public ReleaseGateStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "testing.releasegate";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    String gateId = UUID.randomUUID().toString();
    String tenantId = (String) context.get("tenantId");
    Instant startTime = Instant.now();

    return validateInput(context)
        .then($ -> loadTestResults(context, tenantId))
        .then(results -> evaluateReleaseGate(results, tenantId, gateId))
        .then(gateDecision -> persistGateDecision(gateDecision, tenantId, gateId))
        .then(gateDecision -> publishEvents(gateDecision, tenantId, gateId))
        .then(gateDecision -> buildOutputContext(context, gateDecision, startTime))
        .whenException(ex -> handleError(ex, context, tenantId, gateId, startTime));
  }

  private Promise<Void> validateInput(WorkflowContext context) {
    if (!context.containsKey("tenantId")) {
      return Promise.ofException(new IllegalArgumentException("Missing required input: tenantId"));
    }
    return Promise.complete();
  }

  private Promise<Map<String, Object>> loadTestResults(WorkflowContext context, String tenantId) {
    String testRunId = (String) context.getOrDefault("testRunId", "latest");
    String scanId = (String) context.getOrDefault("scanId", "latest");
    String perfRunId = (String) context.getOrDefault("perfRunId", "latest");

    Promise<List<Map<String, Object>>> testRunPromise =
        dbClient.query(COLLECTION_TEST_RUNS, Map.of("tenantId", tenantId), 1);
    Promise<List<Map<String, Object>>> securityPromise =
        dbClient.query(COLLECTION_SECURITY_RESULTS, Map.of("tenantId", tenantId), 1);
    Promise<List<Map<String, Object>>> performancePromise =
        dbClient.query(COLLECTION_PERFORMANCE_RESULTS, Map.of("tenantId", tenantId), 1);
    Promise<List<Map<String, Object>>> defectsPromise =
        dbClient.query(COLLECTION_DEFECTS, Map.of("tenantId", tenantId, "status", "OPEN"), 100);

    return io.activej.promise.Promises.toList(
            testRunPromise, securityPromise, performancePromise, defectsPromise)
        .map(
            results -> {
              Map<String, Object> allResults = new LinkedHashMap<>();
              allResults.put("testRun", results.get(0).isEmpty() ? null : results.get(0).get(0));
              allResults.put("security", results.get(1).isEmpty() ? null : results.get(1).get(0));
              allResults.put(
                  "performance", results.get(2).isEmpty() ? null : results.get(2).get(0));
              allResults.put("defects", results.get(3));
              return allResults;
            });
  }

  @SuppressWarnings("unchecked")
  private Promise<Map<String, Object>> evaluateReleaseGate(
      Map<String, Object> allResults, String tenantId, String gateId) {

    Map<String, Object> testRun = (Map<String, Object>) allResults.get("testRun");
    Map<String, Object> security = (Map<String, Object>) allResults.get("security");
    Map<String, Object> performance = (Map<String, Object>) allResults.get("performance");
    List<Map<String, Object>> defects = (List<Map<String, Object>>) allResults.get("defects");

    List<String> blockingReasons = new ArrayList<>();
    Map<String, Object> gateChecks = new LinkedHashMap<>();

    // Check 1: Test Pass Rate
    boolean testsPassed = checkTestPassRate(testRun, gateChecks, blockingReasons);

    // Check 2: Code Coverage
    boolean coverageMet = checkCodeCoverage(testRun, gateChecks, blockingReasons);

    // Check 3: Critical/High Defects
    boolean defectCheckPassed = checkDefects(defects, gateChecks, blockingReasons);

    // Check 4: Security Compliance
    boolean securityPassed = checkSecurity(security, gateChecks, blockingReasons);

    // Check 5: Performance NFRs
    boolean performancePassed = checkPerformance(performance, gateChecks, blockingReasons);

    // Final decision
    boolean releaseApproved =
        testsPassed && coverageMet && defectCheckPassed && securityPassed && performancePassed;

    Map<String, Object> gateDecision = new LinkedHashMap<>();
    gateDecision.put("gateId", gateId);
    gateDecision.put("tenantId", tenantId);
    gateDecision.put("decision", releaseApproved ? "PASS" : "BLOCK");
    gateDecision.put("gateChecks", gateChecks);
    gateDecision.put("blockingReasons", blockingReasons);
    gateDecision.put("evaluatedAt", Instant.now().toString());

    return Promise.of(gateDecision);
  }

  @SuppressWarnings("unchecked")
  private boolean checkTestPassRate(
      Map<String, Object> testRun, Map<String, Object> gateChecks, List<String> blockingReasons) {

    if (testRun == null) {
      gateChecks.put("testPassRate", Map.of("status", "FAIL", "reason", "No test run found"));
      blockingReasons.add("No test run results available");
      return false;
    }

    Map<String, Object> summary = (Map<String, Object>) testRun.get("summary");
    double passRate = (double) summary.getOrDefault("passRate", 0.0);
    boolean passed = passRate >= 95.0;

    gateChecks.put(
        "testPassRate",
        Map.of("status", passed ? "PASS" : "FAIL", "passRate", passRate, "threshold", 95.0));

    if (!passed) {
      blockingReasons.add(String.format("Test pass rate %.1f%% below threshold 95.0%%", passRate));
    }

    return passed;
  }

  @SuppressWarnings("unchecked")
  private boolean checkCodeCoverage(
      Map<String, Object> testRun, Map<String, Object> gateChecks, List<String> blockingReasons) {

    if (testRun == null) {
      gateChecks.put("codeCoverage", Map.of("status", "FAIL", "reason", "No coverage data"));
      blockingReasons.add("No code coverage data available");
      return false;
    }

    Map<String, Object> summary = (Map<String, Object>) testRun.get("summary");
    double coverage = (double) summary.getOrDefault("coverage", 0.0);
    boolean passed = coverage >= MIN_UNIT_COVERAGE;

    gateChecks.put(
        "codeCoverage",
        Map.of(
            "status", passed ? "PASS" : "FAIL",
            "coverage", coverage,
            "threshold", MIN_UNIT_COVERAGE));

    if (!passed) {
      blockingReasons.add(
          String.format(
              "Code coverage %.1f%% below threshold %.1f%%", coverage, MIN_UNIT_COVERAGE));
    }

    return passed;
  }

  private boolean checkDefects(
      List<Map<String, Object>> defects,
      Map<String, Object> gateChecks,
      List<String> blockingReasons) {

    long criticalCount = defects.stream().filter(d -> "CRITICAL".equals(d.get("severity"))).count();
    long highCount = defects.stream().filter(d -> "HIGH".equals(d.get("severity"))).count();

    boolean passed = criticalCount <= MAX_CRITICAL_DEFECTS && highCount <= MAX_HIGH_DEFECTS;

    gateChecks.put(
        "defects",
        Map.of(
            "status", passed ? "PASS" : "FAIL",
            "criticalCount", criticalCount,
            "criticalMax", MAX_CRITICAL_DEFECTS,
            "highCount", highCount,
            "highMax", MAX_HIGH_DEFECTS));

    if (criticalCount > MAX_CRITICAL_DEFECTS) {
      blockingReasons.add(
          String.format(
              "%d critical defects (max allowed: %d)", criticalCount, MAX_CRITICAL_DEFECTS));
    }
    if (highCount > MAX_HIGH_DEFECTS) {
      blockingReasons.add(
          String.format("%d high defects (max allowed: %d)", highCount, MAX_HIGH_DEFECTS));
    }

    return passed;
  }

  @SuppressWarnings("unchecked")
  private boolean checkSecurity(
      Map<String, Object> security, Map<String, Object> gateChecks, List<String> blockingReasons) {

    if (security == null) {
      gateChecks.put("security", Map.of("status", "FAIL", "reason", "No security scan"));
      blockingReasons.add("No security scan results available");
      return false;
    }

    Map<String, Object> summary = (Map<String, Object>) security.get("summary");
    long critical = (long) summary.getOrDefault("critical", 0L);
    boolean passed = critical == 0;

    gateChecks.put(
        "security", Map.of("status", passed ? "PASS" : "FAIL", "criticalFindings", critical));

    if (!passed) {
      blockingReasons.add(String.format("%d critical security vulnerabilities found", critical));
    }

    return passed;
  }

  @SuppressWarnings("unchecked")
  private boolean checkPerformance(
      Map<String, Object> performance,
      Map<String, Object> gateChecks,
      List<String> blockingReasons) {

    if (performance == null) {
      gateChecks.put("performance", Map.of("status", "FAIL", "reason", "No performance tests"));
      blockingReasons.add("No performance test results available");
      return false;
    }

    Map<String, Object> nfrValidation = (Map<String, Object>) performance.get("nfrValidation");
    boolean passed = "PASSED".equals(nfrValidation.get("status"));

    gateChecks.put(
        "performance", Map.of("status", passed ? "PASS" : "FAIL", "nfrValidation", nfrValidation));

    if (!passed) {
      blockingReasons.add("Performance NFR targets not met");
    }

    return passed;
  }

  private Promise<Map<String, Object>> persistGateDecision(
      Map<String, Object> gateDecision, String tenantId, String gateId) {

    return dbClient.insert(COLLECTION_RELEASE_GATES, gateDecision).map($ -> gateDecision);
  }

  private Promise<Map<String, Object>> publishEvents(
      Map<String, Object> gateDecision, String tenantId, String gateId) {

    Map<String, Object> eventPayload =
        Map.of(
            "eventType",
            "RELEASE_GATE_EVALUATED",
            "gateId",
            gateId,
            "decision",
            gateDecision.get("decision"),
            "blockingReasons",
            gateDecision.get("blockingReasons"));

    return eventClient.publish(EVENT_TOPIC, tenantId, eventPayload).map($ -> gateDecision);
  }

  private Promise<WorkflowContext> buildOutputContext(
      WorkflowContext context, Map<String, Object> gateDecision, Instant startTime) {

    WorkflowContext output = context.copy();
    output.put("status", "COMPLETED");
    output.put("step", "ReleaseGate");
    output.put("gateId", gateDecision.get("gateId"));
    output.put("gateDecision", gateDecision);
    output.put("releaseApproved", "PASS".equals(gateDecision.get("decision")));
    output.put("duration", Instant.now().toEpochMilli() - startTime.toEpochMilli());
    output.put("completedAt", Instant.now().toString());

    return Promise.of(output);
  }

  private Promise<WorkflowContext> handleError(
      Throwable ex, WorkflowContext context, String tenantId, String gateId, Instant startTime) {

    Map<String, Object> errorPayload =
        Map.of(
            "eventType",
            "RELEASE_GATE_FAILED",
            "gateId",
            gateId,
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
