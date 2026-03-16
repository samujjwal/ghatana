package com.ghatana.appplatform.incident;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Schedule and run game-day chaos engineering drills.
 *              Drills are executed within an existing operator-approved maintenance window.
 *              Each drill defines target services, chaos scenarios (KILL_POD, NETWORK_PARTITION,
 *              DISK_FULL, LATENCY_INJECTION, CPU_STRESS), and expected recovery SLAs.
 *              Measures: detection time, response time, recovery time.
 *              Results stored per drill for reliability regression tracking.
 * @doc.layer   Incident Management (R-02)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-R02-010: Game-day chaos drill scheduling and outcome measurement
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS chaos_drills (
 *   drill_id              TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   maintenance_window_id TEXT NOT NULL,
 *   name                  TEXT NOT NULL,
 *   target_services       JSONB NOT NULL,
 *   scenarios             JSONB NOT NULL,
 *   expected_recovery_min INT NOT NULL DEFAULT 30,
 *   status                TEXT NOT NULL DEFAULT 'SCHEDULED',  -- SCHEDULED | RUNNING | COMPLETED | ABORTED
 *   started_at            TIMESTAMPTZ,
 *   ended_at              TIMESTAMPTZ,
 *   detection_ms          BIGINT,
 *   response_ms           BIGINT,
 *   recovery_ms           BIGINT,
 *   passed                BOOLEAN,
 *   notes                 TEXT
 * );
 * </pre>
 */
public class GameDayChaosManagementService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface ChaosRunnerPort {
        void injectChaos(String drillId, String service, String scenario) throws Exception;
        void stopChaos(String drillId) throws Exception;
        boolean isServiceHealthy(String service) throws Exception;
    }

    public interface MaintenanceWindowPort {
        boolean isActive(String maintenanceWindowId) throws Exception;
    }

    public interface AlertMonitorPort {
        /** Returns timestamp (epoch ms) when alert was fired, or -1 if not yet fired. */
        long getAlertFiredTime(String drillId) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final ChaosRunnerPort chaosRunner;
    private final MaintenanceWindowPort maintenanceWindow;
    private final AlertMonitorPort alertMonitor;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter drillsPassed;
    private final Counter drillsFailed;

    public GameDayChaosManagementService(
        javax.sql.DataSource ds,
        ChaosRunnerPort chaosRunner,
        MaintenanceWindowPort maintenanceWindow,
        AlertMonitorPort alertMonitor,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds               = ds;
        this.chaosRunner      = chaosRunner;
        this.maintenanceWindow = maintenanceWindow;
        this.alertMonitor     = alertMonitor;
        this.audit            = audit;
        this.executor         = executor;
        this.drillsPassed     = Counter.builder("incident.chaos.drills_passed").register(registry);
        this.drillsFailed     = Counter.builder("incident.chaos.drills_failed").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Schedule a drill for a maintenance window. Returns drillId. */
    public Promise<String> schedule(String maintenanceWindowId, String name,
                                     String targetServicesJson, String scenariosJson,
                                     int expectedRecoveryMin, String scheduledBy) {
        return Promise.ofBlocking(executor, () -> {
            String drillId;
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO chaos_drills (maintenance_window_id, name, target_services, scenarios, expected_recovery_min) " +
                     "VALUES (?,?,?::jsonb,?::jsonb,?) RETURNING drill_id"
                 )) {
                ps.setString(1, maintenanceWindowId); ps.setString(2, name);
                ps.setString(3, targetServicesJson);  ps.setString(4, scenariosJson);
                ps.setInt(5, expectedRecoveryMin);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); drillId = rs.getString(1); }
            }
            audit.record(scheduledBy, "CHAOS_DRILL_SCHEDULED", "drillId=" + drillId + " window=" + maintenanceWindowId);
            return drillId;
        });
    }

    /**
     * Execute a drill. Requires the maintenance window to be active.
     * Measures detection_ms, response_ms, recovery_ms. Passes if recovery < expected_recovery_min.
     */
    public Promise<Map<String, Object>> run(String drillId, String executedBy) {
        return Promise.ofBlocking(executor, () -> {
            DrillRecord drill = loadDrill(drillId);
            if (!maintenanceWindow.isActive(drill.maintenanceWindowId()))
                throw new IllegalStateException("Maintenance window is not active");

            long runStart = System.currentTimeMillis();
            markRunning(drillId);
            String outcome = "COMPLETED";
            long detectionMs = -1, responseMs = -1, recoveryMs = -1;
            boolean passed = false;

            try {
                for (String scenario : parseJsonStringArray(drill.scenariosJson())) {
                    for (String svc : parseJsonStringArray(drill.targetServicesJson())) {
                        chaosRunner.injectChaos(drillId, svc, scenario);
                    }
                }

                // Poll for alert detection (max 10 minutes)
                long alertFired = pollForAlert(drillId, 600_000L);
                if (alertFired > 0) detectionMs = alertFired - runStart;

                // Stop chaos — response time
                chaosRunner.stopChaos(drillId);
                responseMs = System.currentTimeMillis() - runStart;

                // Wait for all services to recover (max expectedRecoveryMin * 2 minutes)
                long recoveryDeadline = runStart + (drill.expectedRecoveryMin() * 2L * 60_000L);
                recoveryMs = awaitRecovery(drill.targetServicesJson(), recoveryDeadline, runStart);
                passed = recoveryMs >= 0 && recoveryMs <= drill.expectedRecoveryMin() * 60_000L;

            } catch (Exception ex) {
                outcome = "ABORTED";
                try { chaosRunner.stopChaos(drillId); } catch (Exception ignored) {}
            }

            markCompleted(drillId, outcome, detectionMs, responseMs, recoveryMs, passed);
            audit.record(executedBy, "CHAOS_DRILL_EXECUTED", "drillId=" + drillId + " passed=" + passed);
            if (passed) drillsPassed.increment(); else drillsFailed.increment();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("drillId",       drillId);
            result.put("outcome",       outcome);
            result.put("passed",        passed);
            result.put("detectionMs",   detectionMs);
            result.put("responseMs",    responseMs);
            result.put("recoveryMs",    recoveryMs);
            return result;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private record DrillRecord(String maintenanceWindowId, String targetServicesJson,
                                String scenariosJson, int expectedRecoveryMin) {}

    private DrillRecord loadDrill(String drillId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT maintenance_window_id, target_services::text, scenarios::text, expected_recovery_min " +
                 "FROM chaos_drills WHERE drill_id=?"
             )) {
            ps.setString(1, drillId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new NoSuchElementException("Drill not found: " + drillId);
                return new DrillRecord(rs.getString("maintenance_window_id"),
                    rs.getString("target_services"), rs.getString("scenarios"),
                    rs.getInt("expected_recovery_min"));
            }
        }
    }

    private void markRunning(String drillId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE chaos_drills SET status='RUNNING', started_at=NOW() WHERE drill_id=?"
             )) { ps.setString(1, drillId); ps.executeUpdate(); }
    }

    private void markCompleted(String drillId, String outcome, long detectionMs, long responseMs,
                                long recoveryMs, boolean passed) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE chaos_drills SET status=?, ended_at=NOW(), detection_ms=?, response_ms=?, recovery_ms=?, passed=? WHERE drill_id=?"
             )) {
            ps.setString(1, outcome);
            ps.setObject(2, detectionMs < 0 ? null : detectionMs);
            ps.setObject(3, responseMs < 0 ? null : responseMs);
            ps.setObject(4, recoveryMs < 0 ? null : recoveryMs);
            ps.setBoolean(5, passed); ps.setString(6, drillId);
            ps.executeUpdate();
        }
    }

    private long pollForAlert(String drillId, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            long fired = alertMonitor.getAlertFiredTime(drillId);
            if (fired > 0) return fired;
            Thread.sleep(5_000L);
        }
        return -1;
    }

    private long awaitRecovery(String targetServicesJson, long recoveryDeadline, long runStart) throws Exception {
        List<String> services = parseJsonStringArray(targetServicesJson);
        while (System.currentTimeMillis() < recoveryDeadline) {
            boolean allHealthy = true;
            for (String svc : services) {
                if (!chaosRunner.isServiceHealthy(svc)) { allHealthy = false; break; }
            }
            if (allHealthy) return System.currentTimeMillis() - runStart;
            Thread.sleep(5_000L);
        }
        return -1; // Recovery timeout
    }

    /** Minimal JSON string array parser — extracts quoted strings from ["a","b",...]. */
    private List<String> parseJsonStringArray(String json) {
        List<String> items = new ArrayList<>();
        if (json == null) return items;
        int i = json.indexOf('"');
        while (i >= 0 && i < json.length() - 1) {
            int end = json.indexOf('"', i + 1);
            if (end < 0) break;
            items.add(json.substring(i + 1, end));
            i = json.indexOf('"', end + 1);
        }
        return items;
    }
}
