package com.ghatana.appplatform.integration;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Disaster recovery drill service (T-02).
 *              Scenarios: RTO < 30 min; RPO < 5 min data loss; all core functions
 *              operational post-failover; data consistency; no duplicate processing;
 *              failback with no disruption; DNS failover; certificate continuity.
 * @doc.layer   Integration Testing (T-02 Chaos)
 * @doc.pattern Port-Adapter; JDBC; Promise.ofBlocking; chaos-engineering
 *
 * STORY-T02-007: Implement disaster recovery drill
 */
public class DisasterRecoveryDrillService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface RegionFailoverPort {
        /** Trigger primary region failure simulation. Returns drillId. */
        String initiateFailover() throws Exception;
        /** Check if replica region is serving traffic. */
        boolean isReplicaServingTraffic() throws Exception;
        /** Elapsed ms since failover initiation. */
        long getFailoverElapsedMs(String drillId) throws Exception;
        /** Measured data loss in minutes (RPO). 0 if none. */
        double getDataLossMinutes(String drillId) throws Exception;
        /** Restore primary and initiate failback. */
        void initiateFailback(String drillId) throws Exception;
        /** Is failback complete and replica handed back? */
        boolean isFailbackComplete(String drillId) throws Exception;
        /** Is DNS failover updated to replica region? */
        boolean isDnsUpdated() throws Exception;
        /** Are TLS certificates valid in replica region? */
        boolean areCertificatesValid() throws Exception;
    }

    public interface CoreFunctionPort {
        boolean isOrderSubmissionOperational() throws Exception;
        boolean isReportingOperational() throws Exception;
        boolean isComplianceScreeningOperational() throws Exception;
        boolean isSettlementOperational() throws Exception;
    }

    public interface DataConsistencyPort {
        boolean isDataConsistentAcrossRegions() throws Exception;
        boolean hasDuplicateProcessing(String drillId) throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final long RTO_LIMIT_MS      = 30L * 60 * 1000; // 30 min
    private static final double RPO_LIMIT_MIN   = 5.0;             // 5 min data loss max

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final RegionFailoverPort failover;
    private final CoreFunctionPort coreFunction;
    private final DataConsistencyPort dataConsistency;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;
    private final Timer rtoTimer;

    public DisasterRecoveryDrillService(
        javax.sql.DataSource ds,
        RegionFailoverPort failover,
        CoreFunctionPort coreFunction,
        DataConsistencyPort dataConsistency,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds              = ds;
        this.failover        = failover;
        this.coreFunction    = coreFunction;
        this.dataConsistency = dataConsistency;
        this.audit           = audit;
        this.executor        = executor;
        this.suitesPassed    = Counter.builder("integration.chaos.dr.suites_passed").register(registry);
        this.suitesFailed    = Counter.builder("integration.chaos.dr.suites_failed").register(registry);
        this.rtoTimer        = Timer.builder("integration.chaos.dr.rto_ms").register(registry);
    }

    public Promise<SuiteResult> runAll() {
        return Promise.ofBlocking(executor, () -> {
            List<ScenarioResult> results = new ArrayList<>();
            results.add(runScenario("rto_30min",                    this::rto30min));
            results.add(runScenario("rpo_5min",                     this::rpo5min));
            results.add(runScenario("failover_all_functions",       this::failoverAllFunctions));
            results.add(runScenario("data_consistency_post_failover", this::dataConsistencyPostFailover));
            results.add(runScenario("no_duplicate_processing",      this::noDuplicateProcessing));
            results.add(runScenario("failback_no_disruption",       this::failbackNoDisruption));
            results.add(runScenario("dns_failover",                 this::dnsFailover));
            results.add(runScenario("certificate_continuity",       this::certificateContinuity));

            long passed = results.stream().filter(r -> r.passed).count();
            long failed = results.size() - passed;
            if (failed == 0) suitesPassed.increment(); else suitesFailed.increment();
            audit.audit("DR_DRILL_SUITE", "passed=" + passed + " failed=" + failed);
            return new SuiteResult("DisasterRecoveryDrill", results, passed, failed);
        });
    }

    /** RTO: replica must serve traffic within 30 minutes of primary failure. */
    private void rto30min(String runId) throws Exception {
        String drillId = failover.initiateFailover();
        try {
            // Poll until replica serving or RTO exceeded
            boolean serving = false;
            long elapsed = 0;
            while (elapsed < RTO_LIMIT_MS) {
                Thread.sleep(10_000);
                elapsed = failover.getFailoverElapsedMs(drillId);
                if (failover.isReplicaServingTraffic()) { serving = true; break; }
            }
            long finalElapsed = failover.getFailoverElapsedMs(drillId);
            rtoTimer.record(finalElapsed, java.util.concurrent.TimeUnit.MILLISECONDS);
            assertStep(runId, "replica_serving_within_rto", "replica serving within RTO",
                "< " + RTO_LIMIT_MS + "ms", serving, finalElapsed + "ms");
        } finally { failover.initiateFailback(drillId); }
    }

    /** RPO: data loss must not exceed 5 minutes. */
    private void rpo5min(String runId) throws Exception {
        String drillId = failover.initiateFailover();
        try {
            Thread.sleep(5000); // simulate time passing
            while (!failover.isReplicaServingTraffic()) { Thread.sleep(5000); }
            double dataLossMin = failover.getDataLossMinutes(drillId);
            assertStep(runId, "data_loss_within_rpo", "data loss within RPO limit",
                "<= " + RPO_LIMIT_MIN + " min", dataLossMin <= RPO_LIMIT_MIN, dataLossMin + " min");
        } finally { failover.initiateFailback(drillId); }
    }

    /** All 4 core functions operational after failover. */
    private void failoverAllFunctions(String runId) throws Exception {
        String drillId = failover.initiateFailover();
        try {
            awaitReplicaReady();
            assertStep(runId, "order_submission_up",     "order submission operational post-failover",  "true", coreFunction.isOrderSubmissionOperational(),    true);
            assertStep(runId, "reporting_up",            "reporting operational post-failover",          "true", coreFunction.isReportingOperational(),           true);
            assertStep(runId, "compliance_screening_up", "compliance screening operational post-failover","true", coreFunction.isComplianceScreeningOperational(),"true");
            assertStep(runId, "settlement_up",           "settlement operational post-failover",         "true", coreFunction.isSettlementOperational(),          true);
        } finally { failover.initiateFailback(drillId); }
    }

    private void dataConsistencyPostFailover(String runId) throws Exception {
        String drillId = failover.initiateFailover();
        try {
            awaitReplicaReady();
            boolean consistent = dataConsistency.isDataConsistentAcrossRegions();
            assertStep(runId, "data_consistent", "data consistent across regions post-failover",
                "true", consistent, consistent);
        } finally { failover.initiateFailback(drillId); }
    }

    private void noDuplicateProcessing(String runId) throws Exception {
        String drillId = failover.initiateFailover();
        try {
            awaitReplicaReady();
            boolean hasDups = dataConsistency.hasDuplicateProcessing(drillId);
            assertStep(runId, "no_duplicate_processing", "no duplicate processing after failover",
                "false", !hasDups, hasDups);
        } finally { failover.initiateFailback(drillId); }
    }

    /** Failback: primary restored, replica hands back without disruption. */
    private void failbackNoDisruption(String runId) throws Exception {
        String drillId = failover.initiateFailover();
        awaitReplicaReady();
        failover.initiateFailback(drillId);
        // Poll until failback complete
        boolean done = false;
        for (int i = 0; i < 60; i++) {
            Thread.sleep(5000);
            if (failover.isFailbackComplete(drillId)) { done = true; break; }
        }
        assertStep(runId, "failback_complete", "failback completed within 5 min",
            "true", done, done);
    }

    private void dnsFailover(String runId) throws Exception {
        String drillId = failover.initiateFailover();
        try {
            awaitReplicaReady();
            boolean dns = failover.isDnsUpdated();
            assertStep(runId, "dns_updated_post_failover", "DNS updated to replica region", "true", dns, dns);
        } finally { failover.initiateFailback(drillId); }
    }

    private void certificateContinuity(String runId) throws Exception {
        String drillId = failover.initiateFailover();
        try {
            awaitReplicaReady();
            boolean certs = failover.areCertificatesValid();
            assertStep(runId, "certs_valid_post_failover", "TLS certificates valid in replica region",
                "true", certs, certs);
        } finally { failover.initiateFailback(drillId); }
    }

    private void awaitReplicaReady() throws Exception {
        for (int i = 0; i < 30; i++) {
            if (failover.isReplicaServingTraffic()) return;
            Thread.sleep(5000);
        }
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
                 "INSERT INTO e2e_test_runs (suite_name,scenario) VALUES ('DisasterRecoveryDrill',?) RETURNING run_id")) {
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
