package com.ghatana.digitalmarketing.persistence.migration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * P1-050: Migration Audit and Deployment Metric Service.
 *
 * <p>Provides comprehensive migration tracking and deployment metrics:
 * <ul>
 *   <li>Migration execution tracking with timestamps</li>
 *   <li>Deployment success/failure metrics</li>
 *   <li>Migration duration monitoring</li>
 *   <li>Schema version tracking</li>
 *   <li>Failed migration detection</li>
 *   <li>Migration drift detection</li>
 *   <li>Health check integration</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Migration audit and deployment metrics tracking (P1-050)
 * @doc.layer product
 * @doc.pattern Observability, Migration, Metrics
 */
public class MigrationAuditService {

    private static final Logger LOG = LoggerFactory.getLogger(MigrationAuditService.class);

    private final DataSource dataSource;
    private final MeterRegistry meterRegistry;
    private final Flyway flyway;

    // Metrics
    private Counter migrationSuccessCounter;
    private Counter migrationFailureCounter;
    private Timer migrationDurationTimer;
    private AtomicInteger pendingMigrationsGauge;
    private AtomicInteger schemaVersionGauge;

    public MigrationAuditService(DataSource dataSource, MeterRegistry meterRegistry, Flyway flyway) {
        this.dataSource = dataSource;
        this.meterRegistry = meterRegistry;
        this.flyway = flyway;
        initializeMetrics();
    }

    private void initializeMetrics() {
        LOG.info("[DMOS-MIGRATION] Initializing migration metrics");

        // Migration success counter
        this.migrationSuccessCounter = Counter.builder("dmos.migration.success")
            .description("Number of successful migrations")
            .register(meterRegistry);

        // Migration failure counter
        this.migrationFailureCounter = Counter.builder("dmos.migration.failed")
            .description("Number of failed migrations")
            .register(meterRegistry);

        // Migration duration timer
        this.migrationDurationTimer = Timer.builder("dmos.migration.duration")
            .description("Migration execution duration")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);

        // Pending migrations gauge
        this.pendingMigrationsGauge = new AtomicInteger(0);
        Gauge.builder("dmos.migration.pending", pendingMigrationsGauge, AtomicInteger::get)
            .description("Number of pending migrations")
            .register(meterRegistry);

        // Schema version gauge
        this.schemaVersionGauge = new AtomicInteger(0);
        Gauge.builder("dmos.migration.current_version", schemaVersionGauge, AtomicInteger::get)
            .description("Current schema version")
            .register(meterRegistry);

        LOG.info("[DMOS-MIGRATION] Migration metrics initialized");
    }

    /**
     * P1-050: Records migration execution status.
     */
    public void recordMigrationStatus() {
        LOG.info("[DMOS-MIGRATION] Recording migration status on startup");

        try {
            MigrationInfo current = flyway.info().current();
            MigrationInfo[] pending = flyway.info().pending();

            // Update gauges
            if (current != null) {
                schemaVersionGauge.set(Integer.parseInt(current.getVersion().getVersion()));
                LOG.info("[DMOS-MIGRATION] Current schema version: {}", current.getVersion());
            }

            pendingMigrationsGauge.set(pending != null ? pending.length : 0);

            if (pending != null && pending.length > 0) {
                LOG.warn("[DMOS-MIGRATION] {} pending migrations detected", pending.length);
            } else {
                LOG.info("[DMOS-MIGRATION] No pending migrations");
            }

            // Record migration history
            recordMigrationAudit(current, pending);

        } catch (Exception e) {
            LOG.error("[DMOS-MIGRATION] Failed to record migration status", e);
        }
    }

    /**
     * P1-050: Tracks a migration execution.
     *
     * @param version migration version
     * @param description migration description
     * @param success whether migration succeeded
     * @param duration execution duration
     */
    public void trackMigration(String version, String description, boolean success, Duration duration) {
        LOG.info("[DMOS-MIGRATION] Tracking migration: version={}, success={}, duration={}ms",
            version, success, duration.toMillis());

        // Record metric
        if (success) {
            migrationSuccessCounter.increment();
            migrationDurationTimer.record(duration);
        } else {
            migrationFailureCounter.increment();
        }

        // Store audit record
        storeMigrationRecord(version, description, success, duration, null);
    }

    /**
     * P1-050: Detects migration drift between expected and actual state.
     *
     * @return list of drift issues
     */
    public List<MigrationDrift> detectMigrationDrift() {
        LOG.info("[DMOS-MIGRATION] Detecting migration drift");

        List<MigrationDrift> drifts = new ArrayList<>();

        try {
            // Get applied migrations
            Set<String> appliedMigrations = getAppliedMigrations();

            // Get available migrations
            MigrationInfo[] available = flyway.info().all();

            if (available == null) {
                return drifts;
            }

            for (MigrationInfo migration : available) {
                String version = migration.getVersion().getVersion();

                // Check if migration should be applied but isn't
                if (migration.getState() == org.flywaydb.core.api.MigrationState.PENDING
                    && !migration.getType().isUndo()) {
                    drifts.add(new MigrationDrift(
                        version,
                        migration.getDescription(),
                        DriftType.PENDING,
                        "Migration is pending but should be applied"
                    ));
                }

                // Check for missing migrations
                if (migration.getState().isApplied() && !appliedMigrations.contains(version)) {
                    drifts.add(new MigrationDrift(
                        version,
                        migration.getDescription(),
                        DriftType.MISSING,
                        "Migration marked applied but not found in audit"
                    ));
                }
            }

            LOG.info("[DMOS-MIGRATION] Drift detection complete: {} issues found", drifts.size());

        } catch (Exception e) {
            LOG.error("[DMOS-MIGRATION] Drift detection failed", e);
        }

        return drifts;
    }

    /**
     * P1-050: Gets migration health status.
     *
     * @return health status
     */
    public MigrationHealth getMigrationHealth() {
        try {
            MigrationInfo[] pending = flyway.info().pending();
            MigrationInfo current = flyway.info().current();
            int failedMigrations = getFailedMigrationCount();

            boolean healthy = (pending == null || pending.length == 0) && failedMigrations == 0;

            return new MigrationHealth(
                healthy,
                current != null ? current.getVersion().getVersion() : "0",
                pending != null ? pending.length : 0,
                failedMigrations,
                healthy ? "OK" : (failedMigrations > 0 ? "FAILED_MIGRATIONS" : "PENDING_MIGRATIONS")
            );

        } catch (Exception e) {
            LOG.error("[DMOS-MIGRATION] Health check failed", e);
            return new MigrationHealth(false, "UNKNOWN", 0, 0, "ERROR: " + e.getMessage());
        }
    }

    /**
     * P1-050: Generates migration report.
     *
     * @return report data
     */
    public MigrationReport generateReport() {
        LOG.info("[DMOS-MIGRATION] Generating migration report");

        try {
            MigrationInfo[] all = flyway.info().all();
            MigrationInfo current = flyway.info().current();
            MigrationInfo[] pending = flyway.info().pending();

            List<MigrationRecord> history = getMigrationHistory();

            return new MigrationReport(
                current != null ? current.getVersion().getVersion() : "0",
                all != null ? all.length : 0,
                history.size(),
                pending != null ? pending.length : 0,
                getFailedMigrationCount(),
                history,
                Instant.now()
            );

        } catch (Exception e) {
            LOG.error("[DMOS-MIGRATION] Report generation failed", e);
            throw new RuntimeException("Failed to generate migration report", e);
        }
    }

    // Helper methods

    private void recordMigrationAudit(MigrationInfo current, MigrationInfo[] pending) {
        try (Connection conn = dataSource.getConnection()) {
            String sql =
                "INSERT INTO dmos_migration_audit (" +
                "  event_type, current_version, pending_count, recorded_at" +
                ") VALUES (?, ?, ?, ?) " +
                "ON CONFLICT (recorded_at) DO NOTHING";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, "STARTUP_CHECK");
                stmt.setString(2, current != null ? current.getVersion().getVersion() : "0");
                stmt.setInt(3, pending != null ? pending.length : 0);
                stmt.setTimestamp(4, java.sql.Timestamp.from(Instant.now()));
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOG.error("[DMOS-MIGRATION] Failed to record migration audit", e);
        }
    }

    private void storeMigrationRecord(String version, String description, boolean success,
                                       Duration duration, String errorMessage) {
        try (Connection conn = dataSource.getConnection()) {
            String sql =
                "INSERT INTO dmos_migration_audit (" +
                "  event_type, version, description, success, duration_ms, error_message, recorded_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?)";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, "MIGRATION_EXECUTED");
                stmt.setString(2, version);
                stmt.setString(3, description);
                stmt.setBoolean(4, success);
                stmt.setLong(5, duration.toMillis());
                stmt.setString(6, errorMessage);
                stmt.setTimestamp(7, java.sql.Timestamp.from(Instant.now()));
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            LOG.error("[DMOS-MIGRATION] Failed to store migration record", e);
        }
    }

    private Set<String> getAppliedMigrations() throws SQLException {
        Set<String> applied = new HashSet<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT version FROM flyway_schema_history WHERE success = true");
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                applied.add(rs.getString("version"));
            }
        }

        return applied;
    }

    private int getFailedMigrationCount() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT COUNT(*) FROM flyway_schema_history WHERE success = false");
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    private List<MigrationRecord> getMigrationHistory() throws SQLException {
        List<MigrationRecord> history = new ArrayList<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC");
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                history.add(new MigrationRecord(
                    rs.getString("version"),
                    rs.getString("description"),
                    rs.getString("type"),
                    rs.getBoolean("success"),
                    rs.getTimestamp("installed_on") != null
                        ? rs.getTimestamp("installed_on").toInstant()
                        : null,
                    rs.getLong("execution_time")
                ));
            }
        }

        return history;
    }

    // Records
    public record MigrationDrift(String version, String description, DriftType type, String message) {}
    public record MigrationHealth(boolean healthy, String currentVersion, int pendingCount,
                                 int failedCount, String message) {}
    public record MigrationReport(String currentVersion, int totalMigrations, int appliedMigrations,
                                   int pendingMigrations, int failedMigrations,
                                   List<MigrationRecord> history, Instant generatedAt) {}
    public record MigrationRecord(String version, String description, String type,
                                   boolean success, Instant executedAt, long executionTimeMs) {}

    public enum DriftType {
        PENDING, MISSING, FAILED, UNEXPECTED
    }
}
