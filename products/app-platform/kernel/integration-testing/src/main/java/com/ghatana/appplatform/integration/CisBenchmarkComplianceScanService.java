package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose CIS benchmark compliance scan service (GA-002).
 *              CIS K8s Level 1 compliance ≥ 80%; kube-bench pass; Trivy config scan;
 *              CIS Docker benchmark; failing controls documented with exception or
 *              remediated; scan results in compliance dashboard; auto-rescan on K8s
 *              config change.
 * @doc.layer   Integration Testing (GA Readiness)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; security-compliance
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS cis_compliance_results (
 *   scan_id           TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   tool              TEXT NOT NULL,
 *   benchmark         TEXT NOT NULL,
 *   total_controls    INT  NOT NULL,
 *   passed_controls   INT  NOT NULL,
 *   compliance_pct    DOUBLE PRECISION NOT NULL,
 *   failing_controls  TEXT,
 *   scanned_at        TIMESTAMPTZ DEFAULT now()
 * );
 * </pre>
 *
 * STORY-GA-002: Implement CIS benchmark compliance scan
 */
public class CisBenchmarkComplianceScanService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface ComplianceScanPort {
        /** Run kube-bench CIS K8s Level 1 scan. Returns result. */
        ScanResult runKubeBench() throws Exception;
        /** Run Trivy config scan for K8s manifests. Returns result. */
        ScanResult runTrivyConfigScan() throws Exception;
        /** Run CIS Docker Benchmark. Returns result. */
        ScanResult runCisDockerBenchmark() throws Exception;
        /** Check if compliance dashboard is updated with latest scan results. */
        boolean isComplianceDashboardUpdated(String scanId) throws Exception;
        /** Check if auto-rescan is configured to trigger on K8s config changes. */
        boolean isAutoRescanConfigured() throws Exception;
    }

    public interface ExceptionDocumentationPort {
        /** Return documented exceptions for failing controls. */
        List<ControlException> getDocumentedExceptions() throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    public record ScanResult(
        String tool,
        String benchmark,
        int totalControls,
        int passedControls,
        List<String> failingControls
    ) {
        public double compliancePct() {
            return totalControls == 0 ? 0.0 : (passedControls * 100.0 / totalControls);
        }
    }

    public record ControlException(String controlId, String justification) {}

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final double CIS_LEVEL1_THRESHOLD = 80.0;

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final ComplianceScanPort scanner;
    private final ExceptionDocumentationPort exceptions;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;
    private final Gauge k8sComplianceGauge;
    private volatile double lastK8sCompliancePct = 0.0;

    public CisBenchmarkComplianceScanService(
        javax.sql.DataSource ds,
        ComplianceScanPort scanner,
        ExceptionDocumentationPort exceptions,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds         = ds;
        this.scanner    = scanner;
        this.exceptions = exceptions;
        this.audit      = audit;
        this.executor   = executor;
        this.suitesPassed = Counter.builder("integration.ga.cis.suites_passed").register(registry);
        this.suitesFailed = Counter.builder("integration.ga.cis.suites_failed").register(registry);
        this.k8sComplianceGauge = Gauge.builder("integration.ga.cis.k8s_compliance_pct",
            this, s -> s.lastK8sCompliancePct).register(registry);
    }

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("cis_k8s_benchmark_80pct",        this::cisK8sBenchmark80pct));
            results.add(runScenario("cis_docker_benchmark",            this::cisDockerBenchmark));
            results.add(runScenario("kube_bench_pass",                 this::kubeBenchPass));
            results.add(runScenario("failing_exceptions_documented",   this::failingExceptionsDocumented));
            results.add(runScenario("auto_rescan_on_change",           this::autoRescanOnChange));
            results.add(runScenario("trivy_scan",                      this::trivyScan));
            results.add(runScenario("compliance_dashboard",            this::complianceDashboard));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("GA_CIS_SUITE", "passed=" + passed + " k8s_compliance=" + lastK8sCompliancePct + "%");
            return new SuiteResult("CisBenchmarkComplianceScan", results, passed, failed);
        });
    }

    /** CIS K8s Level 1 compliance must be ≥ 80%. */
    private void cisK8sBenchmark80pct(String runId) throws Exception {
        ScanResult result = scanner.runKubeBench();
        lastK8sCompliancePct = result.compliancePct();
        persistScanResult(result);
        assertStep(runId, "k8s_compliance_80pct", "K8s CIS Level 1 compliance >= 80%",
            ">= " + CIS_LEVEL1_THRESHOLD + "%", result.compliancePct() >= CIS_LEVEL1_THRESHOLD,
            result.compliancePct() + "% (" + result.passedControls() + "/" + result.totalControls() + ")");
    }

    private void cisDockerBenchmark(String runId) throws Exception {
        ScanResult result = scanner.runCisDockerBenchmark();
        persistScanResult(result);
        assertStep(runId, "docker_cis_compliance", "Docker CIS benchmark compliance >= 80%",
            ">= " + CIS_LEVEL1_THRESHOLD + "%", result.compliancePct() >= CIS_LEVEL1_THRESHOLD,
            result.compliancePct() + "% (" + result.passedControls() + "/" + result.totalControls() + ")");
    }

    private void kubeBenchPass(String runId) throws Exception {
        ScanResult result = scanner.runKubeBench();
        double pct = result.compliancePct();
        assertStep(runId, "kuebench_pass", "kube-bench returns >= 80% compliance",
            ">= 80%", pct >= CIS_LEVEL1_THRESHOLD, pct + "%");
    }

    /**
     * All failing CIS controls must have a documented exception or remediation plan.
     * A failing control without documentation is a GA blocker.
     */
    private void failingExceptionsDocumented(String runId) throws Exception {
        ScanResult result = scanner.runKubeBench();
        List<ControlException> documented = exceptions.getDocumentedExceptions();
        Set<String> documentedIds = new HashSet<>();
        for (ControlException e : documented) documentedIds.add(e.controlId());

        List<String> undocumented = new ArrayList<>();
        for (String failingControl : result.failingControls()) {
            if (!documentedIds.contains(failingControl)) undocumented.add(failingControl);
        }
        assertStep(runId, "all_failing_documented", "all failing controls have exception or remediation",
            "0 undocumented", undocumented.isEmpty(), undocumented.size() + " undocumented: " + undocumented);
    }

    private void autoRescanOnChange(String runId) throws Exception {
        boolean autoRescan = scanner.isAutoRescanConfigured();
        assertStep(runId, "auto_rescan_configured", "auto-rescan configured for K8s config changes",
            "true", autoRescan, autoRescan);
    }

    private void trivyScan(String runId) throws Exception {
        ScanResult result = scanner.runTrivyConfigScan();
        persistScanResult(result);
        assertStep(runId, "trivy_scan_complete", "Trivy config scan completed",
            "no-throw", true, result.compliancePct() + "% (" + result.passedControls() + "/" + result.totalControls() + ")");
    }

    private void complianceDashboard(String runId) throws Exception {
        ScanResult result = scanner.runKubeBench();
        String scanId = persistScanResult(result);
        boolean dashboardUpdated = scanner.isComplianceDashboardUpdated(scanId);
        assertStep(runId, "dashboard_updated", "compliance dashboard updated with latest scan",
            "true", dashboardUpdated, dashboardUpdated);
    }

    private String persistScanResult(ScanResult r) {
        String scanId = "SCAN-" + System.nanoTime();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO cis_compliance_results (scan_id,tool,benchmark,total_controls,passed_controls,compliance_pct,failing_controls) VALUES (?,?,?,?,?,?,?)")) {
            ps.setString(1, scanId); ps.setString(2, r.tool()); ps.setString(3, r.benchmark());
            ps.setInt(4, r.totalControls()); ps.setInt(5, r.passedControls());
            ps.setDouble(6, r.compliancePct()); ps.setString(7, String.join("|", r.failingControls()));
            ps.executeUpdate();
        } catch (SQLException ignored) {}
        return scanId;
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
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('CisBenchmarkComplianceScan',?) RETURNING run_id")) {
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
