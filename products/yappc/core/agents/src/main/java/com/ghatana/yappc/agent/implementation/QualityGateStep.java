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
 * Implementation Phase - Step 5: Quality Gate Validation.
 *
 * <p>Enforces quality gates on build artifacts. Validates coverage thresholds, static analysis
 * results, security scans, and other quality metrics.
 *
 * <h3>Responsibilities:</h3>
 *
 * <ul>
 *   <li>Load build runs from previous step
 *   <li>Validate coverage >= 80% threshold
 *   <li>Check static analysis violations (linting, complexity, duplicates)
 *   <li>Run security scans (dependency vulnerabilities)
 *   <li>Enforce quality policies (PASS/WARN/BLOCK)
 *   <li>Persist quality gate results to Data-Cloud
 *   <li>Emit workflow events for tracking
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Quality gate enforcement - validates build quality against thresholds
 * @doc.layer product
 * @doc.pattern Service
 */
public final class QualityGateStep implements WorkflowStep {

  private static final String COLLECTION_BUILD_RUNS = "build_runs";
  private static final String COLLECTION_QUALITY_GATES = "quality_gates";
  private static final String COLLECTION_IMPL_UNITS = "implementation_units";
  private static final String EVENT_TOPIC = "implementation.workflow";

  private static final double COVERAGE_THRESHOLD = 80.0;
  private static final int MAX_COMPLEXITY = 10;
  private static final int MAX_VIOLATIONS = 5;

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public QualityGateStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "implementation.qualitygate";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    String runId = UUID.randomUUID().toString();
    String tenantId = (String) context.get("tenantId");
    Instant startTime = Instant.now();

    return validateInput(context)
        .then($ -> loadBuildRuns(context))
        .then(buildRuns -> runQualityGates(buildRuns, tenantId, runId))
        .then(gates -> persistQualityGates(gates, tenantId, runId))
        .then(gates -> updateUnitStatus(gates))
        .then(gates -> publishEvents(gates, tenantId, runId))
        .then(gates -> buildOutputContext(context, gates, startTime))
        .whenException(ex -> handleError(ex, context, tenantId, runId, startTime));
  }

  private Promise<Void> validateInput(WorkflowContext context) {
    if (!context.containsKey("builds") && !context.containsKey("runId")) {
      return Promise.ofException(
          new IllegalArgumentException(
              "Missing required input: builds or runId from previous step"));
    }
    if (!context.containsKey("tenantId")) {
      return Promise.ofException(new IllegalArgumentException("Missing required input: tenantId"));
    }
    return Promise.complete();
  }

  @SuppressWarnings("unchecked")
  private Promise<List<Map<String, Object>>> loadBuildRuns(WorkflowContext context) {
    if (context.containsKey("builds")) {
      return Promise.of((List<Map<String, Object>>) context.get("builds"));
    }

    String previousRunId = (String) context.get("runId");
    String tenantId = (String) context.get("tenantId");

    Map<String, Object> query =
        Map.of(
            "runId", previousRunId,
            "tenantId", tenantId,
            "status", "PASSED");

    return dbClient.query(COLLECTION_BUILD_RUNS, query, 100);
  }

  private Promise<List<Map<String, Object>>> runQualityGates(
      List<Map<String, Object>> buildRuns, String tenantId, String runId) {

    List<Map<String, Object>> gates = new ArrayList<>();

    for (Map<String, Object> buildRun : buildRuns) {
      String buildId = (String) buildRun.get("buildId");
      String unitId = (String) buildRun.get("unitId");
      String name = (String) buildRun.get("name");
      double coverage = ((Number) buildRun.get("coverage")).doubleValue();

      Map<String, Object> gate = new LinkedHashMap<>();
      gate.put("gateId", UUID.randomUUID().toString());
      gate.put("buildId", buildId);
      gate.put("unitId", unitId);
      gate.put("tenantId", tenantId);
      gate.put("runId", runId);
      gate.put("name", name);

      // Coverage check
      Map<String, Object> coverageCheck = checkCoverage(coverage);
      gate.put("coverageCheck", coverageCheck);

      // Static analysis check (driven by buildRun metadata when available)
      Map<String, Object> staticAnalysis = runStaticAnalysis(name, buildRun);
      gate.put("staticAnalysis", staticAnalysis);

      // Security scan (driven by buildRun metadata when available)
      Map<String, Object> securityScan = runSecurityScan(name, buildRun);
      gate.put("securityScan", securityScan);

      // Overall status
      String overallStatus =
          determineOverallStatus(
              (String) coverageCheck.get("status"),
              (String) staticAnalysis.get("status"),
              (String) securityScan.get("status"));
      gate.put("status", overallStatus);
      gate.put("passed", "PASS".equals(overallStatus));

      // Violations
      List<Map<String, String>> violations =
          collectViolations(coverageCheck, staticAnalysis, securityScan);
      gate.put("violations", violations);
      gate.put("violationCount", violations.size());

      gate.put("createdAt", Instant.now().toString());

      gates.add(gate);
    }

    return Promise.of(gates);
  }

  private Map<String, Object> checkCoverage(double coverage) {
    Map<String, Object> check = new LinkedHashMap<>();
    check.put("coverage", coverage);
    check.put("threshold", COVERAGE_THRESHOLD);
    check.put("met", coverage >= COVERAGE_THRESHOLD);
    check.put("status", coverage >= COVERAGE_THRESHOLD ? "PASS" : "BLOCK");
    if (coverage < COVERAGE_THRESHOLD) {
      check.put(
          "message",
          String.format("Coverage %.1f%% is below threshold %.1f%%", coverage, COVERAGE_THRESHOLD));
    }
    return check;
  }

  private Map<String, Object> runStaticAnalysis(String name) {
    String unitKey = name != null ? name : "";
    return runStaticAnalysis(unitKey, Collections.emptyMap());
  }

  private Map<String, Object> runStaticAnalysis(String name, Map<String, Object> buildRun) {
    // Use actual static-analysis results stored in the build run when available.
    // The build step is expected to persist violation and complexity counts in the build record.
    int violations = extractInt(buildRun, "staticViolations", 0);
    int complexity = extractInt(buildRun, "cyclomaticComplexity", 0);

    Map<String, Object> analysis = new LinkedHashMap<>();
    analysis.put("violations", violations);
    analysis.put("maxViolations", MAX_VIOLATIONS);
    analysis.put("complexity", complexity);
    analysis.put("maxComplexity", MAX_COMPLEXITY);
    analysis.put("duplicates", extractInt(buildRun, "duplicateBlocks", 0));

    boolean passed = violations <= MAX_VIOLATIONS && complexity <= MAX_COMPLEXITY;
    analysis.put("status", passed ? "PASS" : "WARN");
    if (!passed) {
      analysis.put("message", "Static analysis issues detected");
    }
    return analysis;
  }

  private Map<String, Object> runSecurityScan(String name) {
    String unitKey = name != null ? name : "";
    return runSecurityScan(unitKey, Collections.emptyMap());
  }

  private Map<String, Object> runSecurityScan(String name, Map<String, Object> buildRun) {
    // Use actual vulnerability counts stored in the build run when available.
    // The CI pipeline is expected to run a dependency-check or SAST tool and persist
    // the results (criticalVulns, highVulns, mediumVulns) in the build record.
    int criticalVulns = extractInt(buildRun, "criticalVulns", 0);
    int highVulns = extractInt(buildRun, "highVulns", 0);
    int mediumVulns = extractInt(buildRun, "mediumVulns", 0);

    Map<String, Object> scan = new LinkedHashMap<>();
    scan.put("critical", criticalVulns);
    scan.put("high", highVulns);
    scan.put("medium", mediumVulns);
    scan.put("low", extractInt(buildRun, "lowVulns", 0));

    String status;
    if (criticalVulns > 0) {
      status = "BLOCK";
      scan.put("message", "Critical security vulnerabilities found");
    } else if (highVulns > 0) {
      status = "WARN";
      scan.put("message", "High severity vulnerabilities found");
    } else {
      status = "PASS";
    }
    scan.put("status", status);
    return scan;
  }

  /** Safely extracts an integer value from a map, returning the default when absent or wrong type. */
  private int extractInt(Map<String, Object> map, String key, int defaultValue) {
    Object val = map.get(key);
    if (val instanceof Number n) {
      return n.intValue();
    }
    return defaultValue;
  }

  private String determineOverallStatus(
      String coverageStatus, String analysisStatus, String securityStatus) {
    if ("BLOCK".equals(coverageStatus) || "BLOCK".equals(securityStatus)) {
      return "BLOCK";
    }
    if ("WARN".equals(analysisStatus) || "WARN".equals(securityStatus)) {
      return "WARN";
    }
    return "PASS";
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, String>> collectViolations(
      Map<String, Object> coverageCheck,
      Map<String, Object> staticAnalysis,
      Map<String, Object> securityScan) {

    List<Map<String, String>> violations = new ArrayList<>();

    if (!"PASS".equals(coverageCheck.get("status"))) {
      violations.add(
          Map.of(
              "type", "COVERAGE",
              "severity", "HIGH",
              "message", (String) coverageCheck.get("message")));
    }

    if (!"PASS".equals(staticAnalysis.get("status"))) {
      violations.add(
          Map.of(
              "type", "STATIC_ANALYSIS",
              "severity", "MEDIUM",
              "message",
                  (String) staticAnalysis.getOrDefault("message", "Static analysis warnings")));
    }

    if ("BLOCK".equals(securityScan.get("status"))) {
      violations.add(
          Map.of(
              "type", "SECURITY",
              "severity", "CRITICAL",
              "message", (String) securityScan.get("message")));
    } else if ("WARN".equals(securityScan.get("status"))) {
      violations.add(
          Map.of(
              "type", "SECURITY",
              "severity", "HIGH",
              "message", (String) securityScan.get("message")));
    }

    return violations;
  }

  private Promise<List<Map<String, Object>>> persistQualityGates(
      List<Map<String, Object>> gates, String tenantId, String runId) {

    List<Promise<Void>> persistPromises =
        gates.stream()
            .map(gate -> dbClient.insert(COLLECTION_QUALITY_GATES, gate))
            .collect(Collectors.toList());

    return io.activej.promise.Promises.all(persistPromises).map($ -> gates);
  }

  private Promise<List<Map<String, Object>>> updateUnitStatus(List<Map<String, Object>> gates) {

    List<Promise<Void>> updatePromises =
        gates.stream()
            .map(
                gate -> {
                  String unitId = (String) gate.get("unitId");
                  String gateStatus = (String) gate.get("status");

                  String unitStatus;
                  if ("PASS".equals(gateStatus)) {
                    unitStatus = "QUALITY_GATE_PASSED";
                  } else if ("WARN".equals(gateStatus)) {
                    unitStatus = "QUALITY_GATE_WARNING";
                  } else {
                    unitStatus = "QUALITY_GATE_BLOCKED";
                  }

                  Map<String, Object> update =
                      Map.of("status", unitStatus, "updatedAt", Instant.now().toString());
                  return dbClient.update(COLLECTION_IMPL_UNITS, Map.of("unitId", unitId), update);
                })
            .collect(Collectors.toList());

    return io.activej.promise.Promises.all(updatePromises).map($ -> gates);
  }

  private Promise<List<Map<String, Object>>> publishEvents(
      List<Map<String, Object>> gates, String tenantId, String runId) {

    long passed = gates.stream().filter(g -> "PASS".equals(g.get("status"))).count();
    long warned = gates.stream().filter(g -> "WARN".equals(g.get("status"))).count();
    long blocked = gates.stream().filter(g -> "BLOCK".equals(g.get("status"))).count();

    Map<String, Object> eventPayload =
        Map.of(
            "runId", runId,
            "eventType", "QUALITY_GATE_COMPLETED",
            "gateCount", gates.size(),
            "passed", passed,
            "warned", warned,
            "blocked", blocked,
            "gates",
                gates.stream()
                    .map(
                        g ->
                            Map.of(
                                "gateId", g.get("gateId"),
                                "unitId", g.get("unitId"),
                                "name", g.get("name"),
                                "status", g.get("status"),
                                "violationCount", g.get("violationCount")))
                    .collect(Collectors.toList()));

    return eventClient.publish(EVENT_TOPIC, tenantId, eventPayload).map($ -> gates);
  }

  private Promise<WorkflowContext> buildOutputContext(
      WorkflowContext context, List<Map<String, Object>> gates, Instant startTime) {

    long passed = gates.stream().filter(g -> "PASS".equals(g.get("status"))).count();

    WorkflowContext output = context.copy();
    output.put("status", "COMPLETED");
    output.put("step", "QualityGate");
    output.put("gateCount", gates.size());
    output.put("passed", passed);
    output.put("gates", gates);
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
            "QUALITY_GATE_FAILED",
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
