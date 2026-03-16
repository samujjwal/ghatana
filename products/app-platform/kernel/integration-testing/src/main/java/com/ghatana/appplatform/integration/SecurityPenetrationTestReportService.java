package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Security penetration test report service (GA-001).
 *              Validates that an external penetration test has been conducted, findings are
 *              tracked, CRITICAL + HIGH vulnerabilities are remediated, and the report is
 *              archived in a secure vault with signed approval.
 * @doc.layer   Integration Testing (GA Readiness)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; security-validation
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS pentest_findings (
 *   finding_id     TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   severity       TEXT NOT NULL,
 *   title          TEXT NOT NULL,
 *   status         TEXT NOT NULL DEFAULT 'OPEN',  -- OPEN, REMEDIATED, ACCEPTED
 *   report_archive TEXT,
 *   signed_off_by  TEXT,
 *   recorded_at    TIMESTAMPTZ DEFAULT now()
 * );
 * </pre>
 *
 * STORY-GA-001: Implement security penetration test and remediation
 */
public class SecurityPenetrationTestReportService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface PentestResultsPort {
        /** Load all findings from the pentest engagement report. */
        List<PentestFinding> loadFindings() throws Exception;
        /** Get archive location for the sealed pentest report. */
        String getReportArchiveLocation() throws Exception;
        /** Check if the pentest sign-off has been recorded. */
        boolean isReportSignedOff() throws Exception;
        /** Store remediation evidence for a finding. */
        void markRemediated(String findingId, String evidence) throws Exception;
    }

    public interface SecurityScanPort {
        /** Run API security checks (header validation, auth bypass probes). */
        List<String> runApiSecurityProbe() throws Exception;
        /** Run auth flow checks. */
        boolean isAuthFlowSecure() throws Exception;
        /** Run secrets management probe (no secrets in logs/env). */
        boolean isSecretsManagementSecure() throws Exception;
        /** Run audit tamper resistance probe (K-07). */
        boolean isAuditTamperResistant() throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    public record PentestFinding(String findingId, String severity, String title, String status) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final PentestResultsPort pentestResults;
    private final SecurityScanPort securityScan;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;

    public SecurityPenetrationTestReportService(
        javax.sql.DataSource ds,
        PentestResultsPort pentestResults,
        SecurityScanPort securityScan,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds             = ds;
        this.pentestResults = pentestResults;
        this.securityScan   = securityScan;
        this.audit          = audit;
        this.executor       = executor;
        this.suitesPassed   = Counter.builder("integration.ga.pentest.suites_passed").register(registry);
        this.suitesFailed   = Counter.builder("integration.ga.pentest.suites_failed").register(registry);
    }

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("zero_critical_vulnerabilities", this::zeroCriticalVulnerabilities));
            results.add(runScenario("high_severity_remediated",      this::highSeverityRemediated));
            results.add(runScenario("auth_pentest",                  this::authPentest));
            results.add(runScenario("secrets_management_test",       this::secretsManagementTest));
            results.add(runScenario("audit_tamper_test",             this::auditTamperTest));
            results.add(runScenario("api_security",                  this::apiSecurity));
            results.add(runScenario("report_archival",               this::reportArchival));
            results.add(runScenario("sign_off",                      this::signOff));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("GA_PENTEST_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("SecurityPenetrationTestReport", results, passed, failed);
        });
    }

    /** Zero CRITICAL findings in open state — all must be REMEDIATED. */
    private void zeroCriticalVulnerabilities(String runId) throws Exception {
        List<PentestFinding> findings = pentestResults.loadFindings();
        long openCritical = findings.stream()
            .filter(f -> "CRITICAL".equals(f.severity()) && "OPEN".equals(f.status()))
            .count();
        // Persist all findings to local DB for evidence
        for (PentestFinding f : findings) persistFinding(f);
        assertStep(runId, "zero_open_critical", "zero CRITICAL findings in OPEN state",
            "0", openCritical == 0, openCritical + " open CRITICAL findings");
    }

    /** All HIGH severity findings must be REMEDIATED or ACCEPTED with plan. */
    private void highSeverityRemediated(String runId) throws Exception {
        List<PentestFinding> findings = pentestResults.loadFindings();
        long openHigh = findings.stream()
            .filter(f -> "HIGH".equals(f.severity()) && "OPEN".equals(f.status()))
            .count();
        assertStep(runId, "high_severity_closed", "all HIGH findings remediated or accepted",
            "0", openHigh == 0, openHigh + " open HIGH findings");
    }

    private void authPentest(String runId) throws Exception {
        boolean authSecure = securityScan.isAuthFlowSecure();
        assertStep(runId, "auth_flow_secure", "auth flows pass security probe", "true", authSecure, authSecure);
    }

    private void secretsManagementTest(String runId) throws Exception {
        boolean secretsSecure = securityScan.isSecretsManagementSecure();
        assertStep(runId, "secrets_secure", "secrets not exposed in logs or env vars", "true",
            secretsSecure, secretsSecure);
    }

    private void auditTamperTest(String runId) throws Exception {
        boolean tamperResistant = securityScan.isAuditTamperResistant();
        assertStep(runId, "audit_tamper_resistant", "audit log tamper resistance verified", "true",
            tamperResistant, tamperResistant);
    }

    private void apiSecurity(String runId) throws Exception {
        List<String> vulnerabilities = securityScan.runApiSecurityProbe();
        // Critical API vulnerabilities must be absent
        boolean noVulns = vulnerabilities.isEmpty();
        assertStep(runId, "no_api_vulnerabilities", "API security probe finds no vulnerabilities",
            "empty", noVulns, vulnerabilities.size() + " findings: " + vulnerabilities);
    }

    private void reportArchival(String runId) throws Exception {
        String archiveLocation = pentestResults.getReportArchiveLocation();
        assertStep(runId, "report_archived", "pentest report archived in secure vault",
            "non-null", archiveLocation != null && !archiveLocation.isBlank(), archiveLocation);
    }

    private void signOff(String runId) throws Exception {
        boolean signedOff = pentestResults.isReportSignedOff();
        assertStep(runId, "report_signed_off", "pentest report has security sign-off",
            "true", signedOff, signedOff);
        audit.audit("GA_PENTEST_SIGN_OFF", "Penetration test report signed off for GA");
    }

    private void persistFinding(PentestFinding f) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO pentest_findings (finding_id,severity,title,status) VALUES (?,?,?,?) ON CONFLICT DO NOTHING")) {
            ps.setString(1, f.findingId()); ps.setString(2, f.severity());
            ps.setString(3, f.title()); ps.setString(4, f.status());
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private ScenarioResult runScenario(String name, ThrowingConsumer<String> fn) {
        long start = System.currentTimeMillis();
        try {
            String runId = insertRun(name); fn.accept(runId); markRunStatus(runId, "PASSED");
            return new ScenarioResult(name, true, null, System.currentTimeMillis() - start);
        } catch (AssertionError ae) { return new ScenarioResult(name, false, ae.getMessage(), System.currentTimeMillis() - start);
        } catch (Exception ex)      { return new ScenarioResult(name, false, ex.getMessage(),  System.currentTimeMillis() - start); }
    }

    private void assertStep(String runId, String step, String assertion, String expected, boolean passed, Object actual) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_step_assertions (run_id,step_name,assertion,expected,actual,passed) VALUES (?,?,?,?,?,?)")) {
            ps.setString(1, runId); ps.setString(2, step); ps.setString(3, assertion);
            ps.setString(4, expected); ps.setString(5, String.valueOf(actual)); ps.setBoolean(6, passed);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
        if (!passed) throw new AssertionError("FAIL [" + step + "] " + assertion + " expected=" + expected + " actual=" + actual);
    }

    private String insertRun(String scenario) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('SecurityPenetrationTestReport',?) RETURNING run_id")) {
            ps.setString(1, scenario);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private void markRunStatus(String runId, String status) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE e2e_test_runs SET status=? WHERE run_id=?")) {
            ps.setString(1, status); ps.setString(2, runId); ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    @FunctionalInterface interface ThrowingConsumer<T> { void accept(T t) throws Exception; }
    public record ScenarioResult(String scenario, boolean passed, String failureMessage, long durationMs) {}
    public record SuiteResult(String suite, List<ScenarioResult> results, long passedCount, long failedCount) {}
}
