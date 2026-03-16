package com.ghatana.appplatform.manifest;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Orchestrates the 6-step platform upgrade pipeline:
 *              1 DOWNLOAD_VERIFY → 2 HEALTH_CHECK → 3 RUN_MIGRATIONS → 4 ROLLING_UPGRADE
 *              → 5 SMOKE_TEST → 6 CUT_OVER.
 *              Any step failure triggers automatic rollback.
 *              Each step transition is written to the upgrade_audit table.
 * @doc.layer   Platform Manifest (PU-004)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer; sequential step machine
 *
 * STORY-PU004-005: Platform upgrade orchestration with rollback
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS platform_upgrades (
 *   upgrade_id       TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   from_version     TEXT NOT NULL,
 *   to_version       TEXT NOT NULL,
 *   initiated_by     TEXT NOT NULL,
 *   current_step     TEXT NOT NULL DEFAULT 'DOWNLOAD_VERIFY',
 *   status           TEXT NOT NULL DEFAULT 'RUNNING',  -- RUNNING | COMPLETED | ROLLED_BACK | FAILED
 *   failure_step     TEXT,
 *   failure_reason   TEXT,
 *   rollback_step    TEXT,
 *   started_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   completed_at     TIMESTAMPTZ
 * );
 *
 * CREATE TABLE IF NOT EXISTS upgrade_step_log (
 *   log_id     TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   upgrade_id TEXT NOT NULL,
 *   step       TEXT NOT NULL,
 *   outcome    TEXT NOT NULL,  -- STARTED | SUCCESS | FAILED
 *   detail     TEXT,
 *   logged_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class PlatformUpgradeOrchestratorService {

    // ── Step names ────────────────────────────────────────────────────────────

    private static final String STEP_DOWNLOAD  = "DOWNLOAD_VERIFY";
    private static final String STEP_HEALTH    = "HEALTH_CHECK";
    private static final String STEP_MIGRATE   = "RUN_MIGRATIONS";
    private static final String STEP_ROLLING   = "ROLLING_UPGRADE";
    private static final String STEP_SMOKE     = "SMOKE_TEST";
    private static final String STEP_CUTOVER   = "CUT_OVER";

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface ArtifactPort {
        /** Download artifact for version and verify its SHA-256 checksum. Returns artifact path. */
        String downloadAndVerify(String version) throws Exception;
    }

    public interface HealthCheckPort {
        /** Returns true if all platform services are healthy before upgrade. */
        boolean allServicesHealthy() throws Exception;
    }

    public interface MigrationRunnerPort {
        /** Runs DB/config migrations; throws on failure. */
        void runMigrations(String toVersion) throws Exception;
        /** Rolls back migrations to fromVersion. */
        void rollbackMigrations(String fromVersion) throws Exception;
    }

    public interface RollingUpgradePort {
        /** Performs rolling pod-by-pod upgrade. */
        void rollingUpgrade(String artifactPath, String toVersion) throws Exception;
        /** Rolls back to fromVersion binaries. */
        void rollback(String fromVersion) throws Exception;
    }

    public interface SmokeTestRunnerPort {
        /** Runs the post-upgrade smoke suite; throws on any failure. */
        void run(String toVersion) throws Exception;
    }

    public interface CutOverPort {
        /** Switches production traffic to the new version. */
        void cutOver(String toVersion) throws Exception;
        /** Reverts traffic back to fromVersion. */
        void revertCutOver(String fromVersion) throws Exception;
    }

    public interface UpgradeAuditPort {
        /** Writes audit entry (used by UpgradeHistoryAuditService). */
        void record(String upgradeId, String fromVersion, String toVersion,
                    String status, String detail) throws Exception;
    }

    public interface NotificationPort {
        void notifyOperators(String message) throws Exception;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final ArtifactPort artifacts;
    private final HealthCheckPort healthCheck;
    private final MigrationRunnerPort migrations;
    private final RollingUpgradePort rollingUpgrade;
    private final SmokeTestRunnerPort smokeTests;
    private final CutOverPort cutOver;
    private final UpgradeAuditPort auditPort;
    private final NotificationPort notifier;
    private final Executor executor;
    private final Counter upgradesCompleted;
    private final Counter upgradesRolledBack;
    private final Counter upgradesFailed;

    public PlatformUpgradeOrchestratorService(
        javax.sql.DataSource ds,
        ArtifactPort artifacts,
        HealthCheckPort healthCheck,
        MigrationRunnerPort migrations,
        RollingUpgradePort rollingUpgrade,
        SmokeTestRunnerPort smokeTests,
        CutOverPort cutOver,
        UpgradeAuditPort auditPort,
        NotificationPort notifier,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds              = ds;
        this.artifacts       = artifacts;
        this.healthCheck     = healthCheck;
        this.migrations      = migrations;
        this.rollingUpgrade  = rollingUpgrade;
        this.smokeTests      = smokeTests;
        this.cutOver         = cutOver;
        this.auditPort       = auditPort;
        this.notifier        = notifier;
        this.executor        = executor;
        this.upgradesCompleted  = Counter.builder("manifest.upgrade.completed").register(registry);
        this.upgradesRolledBack = Counter.builder("manifest.upgrade.rolled_back").register(registry);
        this.upgradesFailed     = Counter.builder("manifest.upgrade.failed").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Start the full 6-step upgrade pipeline. Returns upgradeId.
     * Blocks until the upgrade completes or rolls back.
     */
    public Promise<String> runUpgrade(String fromVersion, String toVersion, String initiatedBy) {
        return Promise.ofBlocking(executor, () -> {
            String upgradeId = createUpgradeRecord(fromVersion, toVersion, initiatedBy);
            notifier.notifyOperators("Platform upgrade started: " + fromVersion + " → " + toVersion);

            String artifactPath = null;
            boolean migrationsDone = false;
            boolean rollingDone   = false;
            boolean cutOverDone   = false;

            try {
                // Step 1: Download & verify
                logStep(upgradeId, STEP_DOWNLOAD, "STARTED", null);
                setStep(upgradeId, STEP_DOWNLOAD);
                artifactPath = artifacts.downloadAndVerify(toVersion);
                logStep(upgradeId, STEP_DOWNLOAD, "SUCCESS", null);

                // Step 2: Health check
                logStep(upgradeId, STEP_HEALTH, "STARTED", null);
                setStep(upgradeId, STEP_HEALTH);
                if (!healthCheck.allServicesHealthy()) throw new IllegalStateException("Pre-upgrade health check failed");
                logStep(upgradeId, STEP_HEALTH, "SUCCESS", null);

                // Step 3: Migrations
                logStep(upgradeId, STEP_MIGRATE, "STARTED", null);
                setStep(upgradeId, STEP_MIGRATE);
                migrations.runMigrations(toVersion);
                migrationsDone = true;
                logStep(upgradeId, STEP_MIGRATE, "SUCCESS", null);

                // Step 4: Rolling upgrade
                logStep(upgradeId, STEP_ROLLING, "STARTED", null);
                setStep(upgradeId, STEP_ROLLING);
                rollingUpgrade.rollingUpgrade(artifactPath, toVersion);
                rollingDone = true;
                logStep(upgradeId, STEP_ROLLING, "SUCCESS", null);

                // Step 5: Smoke tests
                logStep(upgradeId, STEP_SMOKE, "STARTED", null);
                setStep(upgradeId, STEP_SMOKE);
                smokeTests.run(toVersion);
                logStep(upgradeId, STEP_SMOKE, "SUCCESS", null);

                // Step 6: Cut-over
                logStep(upgradeId, STEP_CUTOVER, "STARTED", null);
                setStep(upgradeId, STEP_CUTOVER);
                cutOver.cutOver(toVersion);
                cutOverDone = true;
                logStep(upgradeId, STEP_CUTOVER, "SUCCESS", null);

                markCompleted(upgradeId);
                upgradesCompleted.increment();
                auditPort.record(upgradeId, fromVersion, toVersion, "COMPLETED", "All 6 steps succeeded");
                notifier.notifyOperators("Platform upgrade COMPLETED: " + fromVersion + " → " + toVersion);

            } catch (Exception ex) {
                String failingStep = getCurrentStep(upgradeId);
                logStep(upgradeId, failingStep, "FAILED", ex.getMessage());
                String rollbackResult = rollback(upgradeId, fromVersion, toVersion,
                    cutOverDone, rollingDone, migrationsDone, ex.getMessage());
                auditPort.record(upgradeId, fromVersion, toVersion, rollbackResult, ex.getMessage());
                notifier.notifyOperators("Platform upgrade FAILED at " + failingStep + ". " + rollbackResult);
            }
            return upgradeId;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String rollback(String upgradeId, String fromVersion, String toVersion,
                             boolean cutOverDone, boolean rollingDone, boolean migrationsDone,
                             String reason) {
        try {
            if (cutOverDone) cutOver.revertCutOver(fromVersion);
            if (rollingDone) rollingUpgrade.rollback(fromVersion);
            if (migrationsDone) migrations.rollbackMigrations(fromVersion);
            markRolledBack(upgradeId, reason);
            upgradesRolledBack.increment();
            return "ROLLED_BACK";
        } catch (Exception re) {
            markFailed(upgradeId, reason + " | rollback failed: " + re.getMessage());
            upgradesFailed.increment();
            return "FAILED";
        }
    }

    private String createUpgradeRecord(String from, String to, String by) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO platform_upgrades (from_version,to_version,initiated_by) VALUES (?,?,?) RETURNING upgrade_id"
             )) {
            ps.setString(1, from); ps.setString(2, to); ps.setString(3, by);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private void setStep(String upgradeId, String step) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE platform_upgrades SET current_step=? WHERE upgrade_id=?"
             )) { ps.setString(1, step); ps.setString(2, upgradeId); ps.executeUpdate(); }
    }

    private String getCurrentStep(String upgradeId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT current_step FROM platform_upgrades WHERE upgrade_id=?"
             )) {
            ps.setString(1, upgradeId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : "UNKNOWN"; }
        }
    }

    private void logStep(String upgradeId, String step, String outcome, String detail) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO upgrade_step_log (upgrade_id,step,outcome,detail) VALUES (?,?,?,?)"
             )) {
            ps.setString(1, upgradeId); ps.setString(2, step);
            ps.setString(3, outcome); ps.setString(4, detail);
            ps.executeUpdate();
        }
    }

    private void markCompleted(String upgradeId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE platform_upgrades SET status='COMPLETED', completed_at=NOW() WHERE upgrade_id=?"
             )) { ps.setString(1, upgradeId); ps.executeUpdate(); }
    }

    private void markRolledBack(String upgradeId, String reason) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE platform_upgrades SET status='ROLLED_BACK', failure_reason=?, completed_at=NOW() WHERE upgrade_id=?"
             )) { ps.setString(1, reason); ps.setString(2, upgradeId); ps.executeUpdate(); }
        catch (SQLException ignored) {}
    }

    private void markFailed(String upgradeId, String reason) {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE platform_upgrades SET status='FAILED', failure_reason=?, completed_at=NOW() WHERE upgrade_id=?"
             )) { ps.setString(1, reason); ps.setString(2, upgradeId); ps.executeUpdate(); }
        catch (SQLException ignored) {}
    }
}
