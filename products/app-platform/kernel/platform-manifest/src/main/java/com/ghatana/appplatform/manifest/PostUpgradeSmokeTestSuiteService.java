package com.ghatana.appplatform.manifest;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Post-upgrade smoke test suite.
 *              5 smoke tests run after each platform upgrade; target completion &lt;2 minutes.
 *              3 retries per test; any final failure triggers rollback via orchestrator.
 *              Tests: API_GATEWAY, EVENT_BUS, CONFIG_SERVICE, AUTH_SERVICE, ORDER_PROCESSING.
 * @doc.layer   Platform Manifest (PU-004)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-PU004-007: Post-upgrade smoke test suite with rollback trigger
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS upgrade_smoke_runs (
 *   run_id      TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   upgrade_id  TEXT NOT NULL,
 *   version     TEXT NOT NULL,
 *   overall     TEXT NOT NULL DEFAULT 'RUNNING',  -- RUNNING | PASSED | FAILED
 *   started_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   duration_ms BIGINT,
 *   triggered_rollback BOOLEAN NOT NULL DEFAULT FALSE
 * );
 *
 * CREATE TABLE IF NOT EXISTS upgrade_smoke_results (
 *   result_id   TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   run_id      TEXT NOT NULL,
 *   test_name   TEXT NOT NULL,   -- API_GATEWAY | EVENT_BUS | CONFIG_SERVICE | AUTH_SERVICE | ORDER_PROCESSING
 *   attempts    INT  NOT NULL DEFAULT 0,
 *   passed      BOOLEAN,
 *   error_msg   TEXT,
 *   duration_ms BIGINT
 * );
 * </pre>
 */
public class PostUpgradeSmokeTestSuiteService {

    // ── Test names ────────────────────────────────────────────────────────────

    private static final List<String> SMOKE_TESTS = List.of(
        "API_GATEWAY", "EVENT_BUS", "CONFIG_SERVICE", "AUTH_SERVICE", "ORDER_PROCESSING"
    );
    private static final int MAX_RETRIES = 3;
    private static final long TARGET_DURATION_MS = 120_000L; // 2 minutes

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface SmokeTestProbePort {
        /** Execute a single named smoke test. Returns a human-readable result detail. Throws on failure. */
        String probe(String testName) throws Exception;
    }

    public interface RollbackTriggerPort {
        /** Trigger rollback via the upgrade orchestrator. */
        void triggerRollback(String upgradeId, String reason) throws Exception;
    }

    public interface NotificationPort {
        void notifyOperators(String message) throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final SmokeTestProbePort probe;
    private final RollbackTriggerPort rollback;
    private final NotificationPort notifier;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter suitesPassed;
    private final Counter suitesFailed;
    private final Counter rollbacksTriggered;
    private final Timer suiteDuration;

    public PostUpgradeSmokeTestSuiteService(
        javax.sql.DataSource ds,
        SmokeTestProbePort probe,
        RollbackTriggerPort rollback,
        NotificationPort notifier,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                  = ds;
        this.probe               = probe;
        this.rollback            = rollback;
        this.notifier            = notifier;
        this.audit               = audit;
        this.executor            = executor;
        this.suitesPassed        = Counter.builder("manifest.smoke.suites_passed").register(registry);
        this.suitesFailed        = Counter.builder("manifest.smoke.suites_failed").register(registry);
        this.rollbacksTriggered  = Counter.builder("manifest.smoke.rollbacks_triggered").register(registry);
        this.suiteDuration       = Timer.builder("manifest.smoke.suite_duration").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Run all 5 smoke tests for the given upgrade.
     * Rolls back automatically if any test fails after retries.
     * Returns runId.
     */
    public Promise<String> run(String upgradeId, String version) {
        return Promise.ofBlocking(executor, () -> {
            long start = System.currentTimeMillis();
            String runId = createRun(upgradeId, version);

            List<String> failed = new ArrayList<>();
            for (String test : SMOKE_TESTS) {
                boolean passed = false;
                String error = null;
                long testStart = System.currentTimeMillis();
                for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                    try {
                        probe.probe(test);
                        passed = true;
                        break;
                    } catch (Exception ex) {
                        error = ex.getMessage();
                    }
                }
                long testDuration = System.currentTimeMillis() - testStart;
                persistResult(runId, test, passed, error, testDuration, passed ? MAX_RETRIES : MAX_RETRIES);
                if (!passed) failed.add(test);
            }

            long elapsed = System.currentTimeMillis() - start;
            suiteDuration.record(elapsed, java.util.concurrent.TimeUnit.MILLISECONDS);

            if (elapsed > TARGET_DURATION_MS) {
                notifier.notifyOperators("Smoke suite exceeded 2-minute target: " + elapsed + "ms");
            }

            if (failed.isEmpty()) {
                markOverall(runId, "PASSED", false, elapsed);
                suitesPassed.increment();
                audit.audit("SMOKE_SUITE_PASSED", "upgradeId=" + upgradeId + " version=" + version);
            } else {
                markOverall(runId, "FAILED", true, elapsed);
                suitesFailed.increment();
                String reason = "Smoke tests failed: " + failed;
                rollbacksTriggered.increment();
                rollback.triggerRollback(upgradeId, reason);
                notifier.notifyOperators("Post-upgrade smoke FAILED for version " + version + ". Rollback triggered. Failed: " + failed);
                audit.audit("SMOKE_SUITE_FAILED_ROLLBACK", "upgradeId=" + upgradeId + " failed=" + failed);
            }
            return runId;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String createRun(String upgradeId, String version) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO upgrade_smoke_runs (upgrade_id, version) VALUES (?,?) RETURNING run_id"
             )) {
            ps.setString(1, upgradeId); ps.setString(2, version);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private void persistResult(String runId, String testName, boolean passed,
                                String errorMsg, long durationMs, int attempts) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO upgrade_smoke_results (run_id,test_name,attempts,passed,error_msg,duration_ms) VALUES (?,?,?,?,?,?)"
             )) {
            ps.setString(1, runId); ps.setString(2, testName); ps.setInt(3, attempts);
            ps.setBoolean(4, passed); ps.setString(5, errorMsg); ps.setLong(6, durationMs);
            ps.executeUpdate();
        }
    }

    private void markOverall(String runId, String overall, boolean triggeredRollback, long durationMs) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE upgrade_smoke_runs SET overall=?, triggered_rollback=?, duration_ms=? WHERE run_id=?"
             )) {
            ps.setString(1, overall); ps.setBoolean(2, triggeredRollback);
            ps.setLong(3, durationMs); ps.setString(4, runId);
            ps.executeUpdate();
        }
    }
}
