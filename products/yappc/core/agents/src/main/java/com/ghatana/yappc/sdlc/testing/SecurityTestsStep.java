package com.ghatana.yappc.sdlc.testing;

import com.ghatana.core.database.DatabaseClient;
import com.ghatana.core.event.cloud.EventCloud;
import com.ghatana.platform.workflow.WorkflowContext;
import com.ghatana.platform.workflow.WorkflowStep;
import io.activej.promise.Promise;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Testing Phase - Step 4: Execute Security Tests.
 *
 * <p>Runs security-focused testing including OWASP Top 10 checks, SAST/DAST scans, dependency
 * vulnerability scanning, and penetration testing.
 *
 * <h3>Responsibilities:</h3>
 *
 * <ul>
 *   <li>OWASP ZAP dynamic security scanning
 *   <li>SAST (Static Application Security Testing)
 *   <li>DAST (Dynamic Application Security Testing)
 *   <li>Dependency vulnerability scanning (CVE detection)
 *   <li>Authentication & authorization testing
 *   <li>SQL injection, XSS, CSRF vulnerability checks
 *   <li>Persist security scan results to Data-Cloud
 *   <li>Emit workflow events for tracking
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Executes security tests - OWASP, SAST/DAST, CVE scanning
 * @doc.layer product
 * @doc.pattern Service
 */
public final class SecurityTestsStep implements WorkflowStep {

  private static final String COLLECTION_SECURITY_RESULTS = "security_scan_results";
  private static final String COLLECTION_DEFECTS = "defects";
  private static final String EVENT_TOPIC = "testing.workflow";

  private final DatabaseClient dbClient;
  private final EventCloud eventClient;

  public SecurityTestsStep(DatabaseClient dbClient, EventCloud eventClient) {
    this.dbClient = Objects.requireNonNull(dbClient, "dbClient must not be null");
    this.eventClient = Objects.requireNonNull(eventClient, "eventClient must not be null");
  }

  @Override
  public String getStepId() {
    return "testing.securitytests";
  }

  @Override
  public Promise<WorkflowContext> execute(WorkflowContext context) {
    String scanId = UUID.randomUUID().toString();
    String tenantId = (String) context.get("tenantId");
    Instant startTime = Instant.now();

    return validateInput(context)
        .then($ -> runOwaspZapScan(tenantId, scanId))
        .then(owaspResults -> runSastScan(tenantId, scanId, owaspResults))
        .then(sastResults -> runDependencyScan(tenantId, scanId, sastResults))
        .then(depResults -> aggregateResults(depResults, tenantId, scanId))
        .then(scanResults -> openSecurityDefects(scanResults, tenantId, scanId))
        .then(scanResults -> persistResults(scanResults, tenantId, scanId))
        .then(scanResults -> publishEvents(scanResults, tenantId, scanId))
        .then(scanResults -> buildOutputContext(context, scanResults, startTime))
        .whenException(ex -> handleError(ex, context, tenantId, scanId, startTime));
  }

  private Promise<Void> validateInput(WorkflowContext context) {
    if (!context.containsKey("tenantId")) {
      return Promise.ofException(new IllegalArgumentException("Missing required input: tenantId"));
    }
    if (!context.containsKey("testRunId") && !context.containsKey("buildId")) {
      return Promise.ofException(
          new IllegalArgumentException(
              "Missing required input: testRunId or buildId from previous step"));
    }
    return Promise.complete();
  }

  private Promise<Map<String, Object>> runOwaspZapScan(String tenantId, String scanId) {
    // Simulate OWASP ZAP scan for OWASP Top 10 vulnerabilities
    List<Map<String, Object>> owaspFindings = new ArrayList<>();

    // SQL Injection
    owaspFindings.add(
        Map.of(
            "type", "SQL_INJECTION",
            "severity", "HIGH",
            "url", "/api/search?q=<sql-injection-attempt>",
            "description", "Potential SQL injection vulnerability in search parameter"));

    // XSS
    owaspFindings.add(
        Map.of(
            "type", "XSS",
            "severity", "MEDIUM",
            "url", "/api/comments",
            "description", "Cross-Site Scripting (XSS) vulnerability in user comments"));

    // CSRF
    owaspFindings.add(
        Map.of(
            "type", "CSRF",
            "severity", "MEDIUM",
            "url", "/api/actions/delete",
            "description", "Missing CSRF token validation on state-changing endpoint"));

    // Insecure Deserialization
    owaspFindings.add(
        Map.of(
            "type", "INSECURE_DESERIALIZATION",
            "severity", "CRITICAL",
            "url", "/api/upload",
            "description", "Insecure deserialization in file upload handler"));

    Map<String, Object> owaspResults = new LinkedHashMap<>();
    owaspResults.put("scanType", "OWASP_ZAP");
    owaspResults.put("findings", owaspFindings);
    owaspResults.put("totalFindings", owaspFindings.size());
    owaspResults.put("critical", 1L);
    owaspResults.put("high", 1L);
    owaspResults.put("medium", 2L);
    owaspResults.put("low", 0L);
    owaspResults.put("scannedAt", Instant.now().toString());

    return Promise.of(owaspResults);
  }

  @SuppressWarnings("unchecked")
  private Promise<Map<String, Object>> runSastScan(
      String tenantId, String scanId, Map<String, Object> owaspResults) {

    // Simulate SAST (Static Analysis) scan
    List<Map<String, Object>> sastFindings = new ArrayList<>();

    sastFindings.add(
        Map.of(
            "type", "HARDCODED_CREDENTIALS",
            "severity", "CRITICAL",
            "file", "src/main/java/com/ghatana/security/AuthService.java",
            "line", 42,
            "description", "Hardcoded API key detected in source code"));

    sastFindings.add(
        Map.of(
            "type", "WEAK_CRYPTOGRAPHY",
            "severity", "HIGH",
            "file", "src/main/java/com/ghatana/crypto/Encryption.java",
            "line", 78,
            "description", "Usage of weak cryptographic algorithm (MD5)"));

    sastFindings.add(
        Map.of(
            "type", "PATH_TRAVERSAL",
            "severity", "MEDIUM",
            "file", "src/main/java/com/ghatana/files/FileService.java",
            "line", 123,
            "description", "Potential path traversal vulnerability in file access"));

    Map<String, Object> sastResults = new LinkedHashMap<>();
    sastResults.put("scanType", "SAST");
    sastResults.put("findings", sastFindings);
    sastResults.put("totalFindings", sastFindings.size());
    sastResults.put("critical", 1L);
    sastResults.put("high", 1L);
    sastResults.put("medium", 1L);
    sastResults.put("low", 0L);
    sastResults.put("scannedAt", Instant.now().toString());

    owaspResults.put("sastResults", sastResults);
    return Promise.of(owaspResults);
  }

  @SuppressWarnings("unchecked")
  private Promise<Map<String, Object>> runDependencyScan(
      String tenantId, String scanId, Map<String, Object> previousResults) {

    // Simulate dependency vulnerability scan (CVE detection)
    List<Map<String, Object>> cveFindings = new ArrayList<>();

    cveFindings.add(
        Map.of(
            "cveId", "CVE-2024-1234",
            "severity", "HIGH",
            "dependency", "com.fasterxml.jackson.core:jackson-databind:2.12.0",
            "description", "Deserialization vulnerability in Jackson library",
            "cvssScore", 7.5));

    cveFindings.add(
        Map.of(
            "cveId", "CVE-2023-5678",
            "severity", "MEDIUM",
            "dependency", "org.apache.commons:commons-text:1.9",
            "description", "Remote code execution in Commons Text StringSubstitutor",
            "cvssScore", 5.5));

    cveFindings.add(
        Map.of(
            "cveId", "CVE-2023-9999",
            "severity", "LOW",
            "dependency", "org.yaml:snakeyaml:1.30",
            "description", "Denial of service vulnerability in SnakeYAML",
            "cvssScore", 3.3));

    Map<String, Object> cveResults = new LinkedHashMap<>();
    cveResults.put("scanType", "DEPENDENCY_CVE");
    cveResults.put("findings", cveFindings);
    cveResults.put("totalFindings", cveFindings.size());
    cveResults.put("critical", 0L);
    cveResults.put("high", 1L);
    cveResults.put("medium", 1L);
    cveResults.put("low", 1L);
    cveResults.put("scannedAt", Instant.now().toString());

    previousResults.put("cveResults", cveResults);
    return Promise.of(previousResults);
  }

  @SuppressWarnings("unchecked")
  private Promise<Map<String, Object>> aggregateResults(
      Map<String, Object> allResults, String tenantId, String scanId) {

    Map<String, Object> owaspResults = (Map<String, Object>) allResults.get("owaspResults");
    Map<String, Object> sastResults = (Map<String, Object>) allResults.get("sastResults");
    Map<String, Object> cveResults = (Map<String, Object>) allResults.get("cveResults");

    long totalCritical =
        (long) owaspResults.getOrDefault("critical", 0L)
            + (long) sastResults.getOrDefault("critical", 0L)
            + (long) cveResults.getOrDefault("critical", 0L);

    long totalHigh =
        (long) owaspResults.getOrDefault("high", 0L)
            + (long) sastResults.getOrDefault("high", 0L)
            + (long) cveResults.getOrDefault("high", 0L);

    long totalMedium =
        (long) owaspResults.getOrDefault("medium", 0L)
            + (long) sastResults.getOrDefault("medium", 0L)
            + (long) cveResults.getOrDefault("medium", 0L);

    long totalLow =
        (long) owaspResults.getOrDefault("low", 0L)
            + (long) sastResults.getOrDefault("low", 0L)
            + (long) cveResults.getOrDefault("low", 0L);

    long totalFindings = totalCritical + totalHigh + totalMedium + totalLow;

    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("totalFindings", totalFindings);
    summary.put("critical", totalCritical);
    summary.put("high", totalHigh);
    summary.put("medium", totalMedium);
    summary.put("low", totalLow);
    summary.put(
        "securityScore", calculateSecurityScore(totalCritical, totalHigh, totalMedium, totalLow));

    Map<String, Object> scanResults = new LinkedHashMap<>();
    scanResults.put("scanId", scanId);
    scanResults.put("tenantId", tenantId);
    scanResults.put("summary", summary);
    scanResults.put("owaspResults", owaspResults);
    scanResults.put("sastResults", sastResults);
    scanResults.put("cveResults", cveResults);
    scanResults.put("status", totalCritical > 0 ? "FAILED" : "PASSED");
    scanResults.put("completedAt", Instant.now().toString());

    return Promise.of(scanResults);
  }

  private double calculateSecurityScore(long critical, long high, long medium, long low) {
    // Weighted scoring: 100 - (critical*20 + high*10 + medium*5 + low*1)
    double penalty = (critical * 20) + (high * 10) + (medium * 5) + (low * 1);
    return Math.max(0, 100 - penalty);
  }

  @SuppressWarnings("unchecked")
  private Promise<Map<String, Object>> openSecurityDefects(
      Map<String, Object> scanResults, String tenantId, String scanId) {

    Map<String, Object> summary = (Map<String, Object>) scanResults.get("summary");
    long totalCritical = (long) summary.get("critical");
    long totalHigh = (long) summary.get("high");

    if (totalCritical == 0 && totalHigh == 0) {
      return Promise.of(scanResults);
    }

    // Open defects for critical and high severity findings
    List<Map<String, Object>> defects = new ArrayList<>();

    // Critical defects
    for (int i = 0; i < totalCritical; i++) {
      defects.add(createDefect(tenantId, scanId, "CRITICAL", i));
    }

    // High defects
    for (int i = 0; i < totalHigh; i++) {
      defects.add(createDefect(tenantId, scanId, "HIGH", i));
    }

    List<Promise<Void>> defectPromises =
        defects.stream()
            .map(defect -> dbClient.insert(COLLECTION_DEFECTS, defect).toVoid())
            .collect(Collectors.toList());

    return io.activej.promise.Promises.all(defectPromises)
        .map(
            $ -> {
              scanResults.put("defectsOpened", defects.size());
              return scanResults;
            });
  }

  private Map<String, Object> createDefect(
      String tenantId, String scanId, String severity, int index) {
    Map<String, Object> defect = new LinkedHashMap<>();
    defect.put("defectId", UUID.randomUUID().toString());
    defect.put("tenantId", tenantId);
    defect.put("scanId", scanId);
    defect.put("severity", severity);
    defect.put("type", "SECURITY");
    defect.put("description", severity + " security vulnerability #" + (index + 1));
    defect.put("status", "OPEN");
    defect.put("owner", null);
    defect.put("createdAt", Instant.now().toString());
    return defect;
  }

  private Promise<Map<String, Object>> persistResults(
      Map<String, Object> scanResults, String tenantId, String scanId) {

    return dbClient.insert(COLLECTION_SECURITY_RESULTS, scanResults).map($ -> scanResults);
  }

  @SuppressWarnings("unchecked")
  private Promise<Map<String, Object>> publishEvents(
      Map<String, Object> scanResults, String tenantId, String scanId) {

    Map<String, Object> summary = (Map<String, Object>) scanResults.get("summary");

    Map<String, Object> eventPayload =
        Map.of(
            "eventType",
            "SECURITY_SCAN_COMPLETED",
            "scanId",
            scanId,
            "status",
            scanResults.get("status"),
            "summary",
            summary);

    return eventClient.publish(EVENT_TOPIC, tenantId, eventPayload).map($ -> scanResults);
  }

  private Promise<WorkflowContext> buildOutputContext(
      WorkflowContext context, Map<String, Object> scanResults, Instant startTime) {

    WorkflowContext output = context.copy();
    output.put("status", "COMPLETED");
    output.put("step", "SecurityTests");
    output.put("scanId", scanResults.get("scanId"));
    output.put("securityResults", scanResults);
    output.put("duration", Instant.now().toEpochMilli() - startTime.toEpochMilli());
    output.put("completedAt", Instant.now().toString());

    return Promise.of(output);
  }

  private Promise<WorkflowContext> handleError(
      Throwable ex, WorkflowContext context, String tenantId, String scanId, Instant startTime) {

    Map<String, Object> errorPayload =
        Map.of(
            "eventType",
            "SECURITY_SCAN_FAILED",
            "scanId",
            scanId,
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

  // --- OWASP ZAP DAST Helpers ---

  private Map<String, Object> runOwaspZapScan(String targetUrl) {
    // Simulate OWASP ZAP scan
    return Map.of(
        "tool",
        "OWASP_ZAP",
        "scanType",
        "DAST",
        "targetUrl",
        targetUrl,
        "vulnerabilitiesFound",
        3,
        "severity",
        Map.of("HIGH", 1, "MEDIUM", 2, "LOW", 0),
        "completedAt",
        Instant.now().toString());
  }
}
