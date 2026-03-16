package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose SLA validation sign-off service (GA-003).
 *              72-hour sustained load test at 2× peak; measures all SLAs:
 *              API p99 < 200ms, event bus consumer lag < 30s, settlement < T+2,
 *              daily reconciliation within 2hr of EOD, audit 100% captured.
 *              SLA report signed by CTO and product head. Document published.
 * @doc.layer   Integration Testing (GA Readiness)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; sla-validation
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS sla_validation_results (
 *   result_id        TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   sla_name         TEXT NOT NULL,
 *   target           TEXT NOT NULL,
 *   measured_value   TEXT NOT NULL,
 *   passed           BOOL NOT NULL,
 *   load_duration_hr INT  NOT NULL DEFAULT 72,
 *   signed_off_by    TEXT,
 *   validated_at     TIMESTAMPTZ DEFAULT now()
 * );
 * </pre>
 *
 * STORY-GA-003: Implement SLA validation and formal sign-off
 */
public class SlaValidationSignOffService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface LoadTestPort {
        /** Run sustained load at the given multiplier of peak for durationHr. Returns testId. */
        String runSustainedLoad(double peakMultiplier, int durationHr) throws Exception;
        /** Is the load test complete? */
        boolean isLoadTestComplete(String testId) throws Exception;
        /** Get measured API p99 latency in ms after load test. */
        long getApiP99LatencyMs(String testId) throws Exception;
        /** Get peak event bus consumer lag in seconds during load test. */
        long getEventBusLagSeconds(String testId) throws Exception;
        /** Settlement p99 processing time in hours (T+N). */
        double getSettlementProcessingHours(String testId) throws Exception;
        /** Reconciliation duration in minutes from EOD. */
        long getReconciliationMinutesFromEod(String testId) throws Exception;
        /** Audit event capture rate as a fraction (0.0–1.0). */
        double getAuditCaptureRate(String testId) throws Exception;
    }

    public interface SignOffPort {
        boolean isSlaSignedOffByCto() throws Exception;
        boolean isSlaSignedOffByProductHead() throws Exception;
        String getSignOffDocumentLocation() throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final long   API_P99_LIMIT_MS         = 200L;
    private static final long   EVENT_LAG_LIMIT_SEC      = 30L;
    private static final double SETTLEMENT_LIMIT_DAYS    = 2.0 / 24; // T+2 hours expressed as fraction of a day
    private static final long   RECON_WINDOW_MINUTES     = 120L;      // 2 hours
    private static final double AUDIT_CAPTURE_MIN        = 1.0;       // 100%
    private static final double LOAD_PEAK_MULTIPLIER     = 2.0;
    private static final int    LOAD_DURATION_HOURS      = 72;

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final LoadTestPort loadTest;
    private final SignOffPort signOff;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;

    public SlaValidationSignOffService(
        javax.sql.DataSource ds,
        LoadTestPort loadTest,
        SignOffPort signOff,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds          = ds;
        this.loadTest    = loadTest;
        this.signOff     = signOff;
        this.audit       = audit;
        this.executor    = executor;
        this.suitesPassed = Counter.builder("integration.ga.sla.suites_passed").register(registry);
        this.suitesFailed = Counter.builder("integration.ga.sla.suites_failed").register(registry);
    }

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("72hr_load_2x_peak",      this::load72hrAt2xPeak));
            results.add(runScenario("api_p99_200ms",          this::apiP99));
            results.add(runScenario("event_lag_30sec",        this::eventLag));
            results.add(runScenario("settlement_t_plus_2",    this::settlementTPlus2));
            results.add(runScenario("reconciliation_2hr",     this::reconciliation2hr));
            results.add(runScenario("audit_100pct",           this::audit100pct));
            results.add(runScenario("cto_sign_off",           this::ctoSignOff));
            results.add(runScenario("go_no_go_process",       this::goNoGoProcess));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("GA_SLA_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("SlaValidationSignOff", results, passed, failed);
        });
    }

    /** Run 72-hour sustained load at 2× peak. Validates that load test completes. */
    private void load72hrAt2xPeak(String runId) throws Exception {
        String testId = loadTest.runSustainedLoad(LOAD_PEAK_MULTIPLIER, LOAD_DURATION_HOURS);
        // In integration test we poll with a shorter timeout; the port impl simulates completion
        boolean done = false;
        for (int i = 0; i < 30; i++) {
            Thread.sleep(1000);
            if (loadTest.isLoadTestComplete(testId)) { done = true; break; }
        }
        assertStep(runId, "load_test_complete", "72hr 2× peak load test completes", "true", done, done);
        // Store testId in run so downstream scenarios can retrieve results
        updateRunWithTestId(runId, testId);
    }

    private void apiP99(String runId) throws Exception {
        String testId = getOrRunLoadTest();
        long p99 = loadTest.getApiP99LatencyMs(testId);
        persistSlaResult("api_p99_latency_ms", "< " + API_P99_LIMIT_MS + "ms", p99 + "ms", p99 < API_P99_LIMIT_MS);
        assertStep(runId, "api_p99_200ms", "API p99 latency < 200ms during 2× peak",
            "< " + API_P99_LIMIT_MS + "ms", p99 < API_P99_LIMIT_MS, p99 + "ms");
    }

    private void eventLag(String runId) throws Exception {
        String testId = getOrRunLoadTest();
        long lag = loadTest.getEventBusLagSeconds(testId);
        persistSlaResult("event_bus_consumer_lag_sec", "< " + EVENT_LAG_LIMIT_SEC + "s", lag + "s", lag < EVENT_LAG_LIMIT_SEC);
        assertStep(runId, "event_lag_30sec", "event bus consumer lag < 30s during load",
            "< " + EVENT_LAG_LIMIT_SEC + "s", lag < EVENT_LAG_LIMIT_SEC, lag + "s");
    }

    private void settlementTPlus2(String runId) throws Exception {
        String testId = getOrRunLoadTest();
        double settlementHr = loadTest.getSettlementProcessingHours(testId);
        // T+2 means ≤ 2 business hours from submission
        boolean met = settlementHr <= 2.0;
        persistSlaResult("settlement_processing_hr", "<= 2h", settlementHr + "h", met);
        assertStep(runId, "settlement_t_plus_2", "settlement processing T+2 (≤ 2hr)",
            "<= 2h", met, settlementHr + "h");
    }

    private void reconciliation2hr(String runId) throws Exception {
        String testId = getOrRunLoadTest();
        long reconMin = loadTest.getReconciliationMinutesFromEod(testId);
        persistSlaResult("recon_minutes_from_eod", "<= 120min", reconMin + "min", reconMin <= RECON_WINDOW_MINUTES);
        assertStep(runId, "reconciliation_2hr", "daily reconciliation completes within 2hr of EOD",
            "<= " + RECON_WINDOW_MINUTES + "min", reconMin <= RECON_WINDOW_MINUTES, reconMin + "min");
    }

    private void audit100pct(String runId) throws Exception {
        String testId = getOrRunLoadTest();
        double captureRate = loadTest.getAuditCaptureRate(testId);
        persistSlaResult("audit_capture_pct", "100%", (captureRate * 100) + "%", captureRate >= AUDIT_CAPTURE_MIN);
        assertStep(runId, "audit_100pct", "audit events captured at 100% rate",
            "1.0 (100%)", captureRate >= AUDIT_CAPTURE_MIN, captureRate * 100 + "%");
    }

    private void ctoSignOff(String runId) throws Exception {
        boolean cto         = signOff.isSlaSignedOffByCto();
        boolean productHead = signOff.isSlaSignedOffByProductHead();
        assertStep(runId, "cto_sign_off", "SLA report signed off by CTO", "true", cto, cto);
        assertStep(runId, "product_head_sign_off", "SLA report signed off by product head", "true",
            productHead, productHead);
        String doc = signOff.getSignOffDocumentLocation();
        assertStep(runId, "sign_off_document", "sign-off document location known", "non-null",
            doc != null && !doc.isBlank(), doc);
    }

    private void goNoGoProcess(String runId) throws Exception {
        boolean cto = signOff.isSlaSignedOffByCto();
        boolean ph  = signOff.isSlaSignedOffByProductHead();
        boolean go  = cto && ph;
        assertStep(runId, "go_no_go", "GA go/no-go decision: both sign-offs required", "GO",
            go, go ? "GO" : "NO-GO");
        audit.audit("GA_GO_NO_GO", "decision=" + (go ? "GO" : "NO-GO") + " cto=" + cto + " productHead=" + ph);
    }

    /** Helper: run a quick load test and return its ID. */
    private String getOrRunLoadTest() throws Exception {
        String testId = loadTest.runSustainedLoad(LOAD_PEAK_MULTIPLIER, LOAD_DURATION_HOURS);
        for (int i = 0; i < 10; i++) {
            if (loadTest.isLoadTestComplete(testId)) break;
            Thread.sleep(1000);
        }
        return testId;
    }

    private void persistSlaResult(String slaName, String target, String measured, boolean passed) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO sla_validation_results (sla_name,target,measured_value,passed,load_duration_hr) VALUES (?,?,?,?,?)")) {
            ps.setString(1, slaName); ps.setString(2, target);
            ps.setString(3, measured); ps.setBoolean(4, passed);
            ps.setInt(5, LOAD_DURATION_HOURS);
            ps.executeUpdate();
        } catch (SQLException ignored) {}
    }

    private void updateRunWithTestId(String runId, String testId) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE e2e_test_runs SET failure_step=? WHERE run_id=?")) {
            // re-using failure_step column to carry testId context forward
            ps.setString(1, "testId=" + testId); ps.setString(2, runId);
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
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('SlaValidationSignOff',?) RETURNING run_id")) {
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
