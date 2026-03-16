package com.ghatana.appplatform.workflow;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Manage multiple coexisting workflow definition versions.
 *              New instances use the active version; older instances complete on their started version.
 *              Supports version migration at safe migration points and deprecation with grace period.
 * @doc.layer   Workflow Orchestration (W-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking
 *
 * STORY-W01-015: Workflow version management
 *
 * DDL (idempotent create):
 * <pre>
 * CREATE TABLE IF NOT EXISTS workflow_definition_versions (
 *   version_id      TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   workflow_name   TEXT NOT NULL,
 *   version_number  TEXT NOT NULL,
 *   definition_json JSONB NOT NULL,
 *   is_active       BOOLEAN NOT NULL DEFAULT false,
 *   deprecated      BOOLEAN NOT NULL DEFAULT false,
 *   deprecation_date TIMESTAMPTZ,
 *   migration_point TEXT,           -- step_id at which in-flight instances can migrate
 *   created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   UNIQUE (workflow_name, version_number)
 * );
 * </pre>
 */
public class WorkflowVersionManagementService {

    // ── Inner port interfaces ────────────────────────────────────────────────

    public interface WorkflowDefinitionStorePort {
        String upsertDefinition(String workflowName, String version, String definitionJson) throws Exception;
    }

    // ── Value types ──────────────────────────────────────────────────────────

    public record WorkflowVersion(
        String versionId,
        String workflowName,
        String versionNumber,
        String definitionJson,
        boolean isActive,
        boolean deprecated,
        String deprecationDate,
        String migrationPoint,
        String createdAt
    ) {}

    public record MigrationResult(
        int instancesMigrated,
        int instancesSkipped,
        String fromVersion,
        String toVersion
    ) {}

    // ── Fields ───────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final WorkflowDefinitionStorePort defStore;
    private final Executor executor;
    private final Counter versionPublishedCounter;
    private final Counter migrationCounter;
    private final Counter deprecationCounter;

    public WorkflowVersionManagementService(
        javax.sql.DataSource ds,
        WorkflowDefinitionStorePort defStore,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds        = ds;
        this.defStore  = defStore;
        this.executor  = executor;
        this.versionPublishedCounter = Counter.builder("workflow.version.published").register(registry);
        this.migrationCounter        = Counter.builder("workflow.version.migrated").register(registry);
        this.deprecationCounter      = Counter.builder("workflow.version.deprecated").register(registry);

        Gauge.builder("workflow.version.deprecated_count", ds, d -> {
            try (Connection c = d.getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM workflow_definition_versions WHERE deprecated=true");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            } catch (Exception e) { return 0; }
        }).register(registry);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /** Publish a new workflow version and optionally activate it. */
    public Promise<WorkflowVersion> publishVersion(
        String workflowName,
        String versionNumber,
        String definitionJson,
        boolean activate,
        String migrationPoint
    ) {
        return Promise.ofBlocking(executor, () -> {
            defStore.upsertDefinition(workflowName, versionNumber, definitionJson);

            try (Connection c = ds.getConnection()) {
                c.setAutoCommit(false);
                try {
                    if (activate) {
                        // Deactivate current active version
                        try (PreparedStatement ps = c.prepareStatement(
                                "UPDATE workflow_definition_versions SET is_active=false WHERE workflow_name=? AND is_active=true")) {
                            ps.setString(1, workflowName);
                            ps.executeUpdate();
                        }
                    }

                    String versionId;
                    try (PreparedStatement ps = c.prepareStatement(
                             "INSERT INTO workflow_definition_versions " +
                             "(workflow_name, version_number, definition_json, is_active, migration_point) " +
                             "VALUES (?,?,?::jsonb,?,?) " +
                             "ON CONFLICT (workflow_name, version_number) DO UPDATE SET definition_json=EXCLUDED.definition_json, is_active=EXCLUDED.is_active " +
                             "RETURNING version_id, created_at"
                         )) {
                        ps.setString(1, workflowName);
                        ps.setString(2, versionNumber);
                        ps.setString(3, definitionJson);
                        ps.setBoolean(4, activate);
                        ps.setString(5, migrationPoint);
                        try (ResultSet rs = ps.executeQuery()) {
                            rs.next();
                            versionId = rs.getString("version_id");
                        }
                    }

                    c.commit();
                    versionPublishedCounter.increment();
                    return new WorkflowVersion(versionId, workflowName, versionNumber, definitionJson,
                        activate, false, null, migrationPoint, now());
                } catch (Exception e) {
                    c.rollback();
                    throw e;
                }
            }
        });
    }

    /** Get the currently active version for a workflow. */
    public Promise<Optional<WorkflowVersion>> getActiveVersion(String workflowName) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM workflow_definition_versions WHERE workflow_name=? AND is_active=true"
                 )) {
                ps.setString(1, workflowName);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(mapVersion(rs));
                    return Optional.empty();
                }
            }
        });
    }

    /** Get the version that a specific running instance is using. */
    public Promise<Optional<WorkflowVersion>> getVersionForInstance(String workflowName, String instanceId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT wdv.* FROM workflow_definition_versions wdv " +
                     "JOIN workflow_instances wi ON wi.workflow_version = wdv.version_number " +
                     "WHERE wdv.workflow_name=? AND wi.instance_id=?"
                 )) {
                ps.setString(1, workflowName);
                ps.setString(2, instanceId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return Optional.of(mapVersion(rs));
                    return Optional.empty();
                }
            }
        });
    }

    /** List all published versions for a workflow (newest first). */
    public Promise<List<WorkflowVersion>> listVersions(String workflowName) {
        return Promise.ofBlocking(executor, () -> {
            List<WorkflowVersion> result = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT * FROM workflow_definition_versions WHERE workflow_name=? ORDER BY created_at DESC"
                 )) {
                ps.setString(1, workflowName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) result.add(mapVersion(rs));
                }
            }
            return result;
        });
    }

    /**
     * Deprecate a version with a grace period end date.
     * In-flight instances will complete, but no new instances start on this version.
     */
    public Promise<Void> deprecateVersion(String workflowName, String versionNumber, String deprecationDate) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE workflow_definition_versions SET deprecated=true, deprecation_date=?::timestamptz " +
                     "WHERE workflow_name=? AND version_number=?"
                 )) {
                ps.setString(1, deprecationDate);
                ps.setString(2, workflowName);
                ps.setString(3, versionNumber);
                ps.executeUpdate();
                deprecationCounter.increment();
                return null;
            }
        });
    }

    /**
     * Migrate in-flight instances from one version to another at the designated migration point.
     * Skips instances that are not at or past the migration point.
     */
    public Promise<MigrationResult> migrateInstances(
        String workflowName,
        String fromVersion,
        String toVersion
    ) {
        return Promise.ofBlocking(executor, () -> {
            // Find instances at the migration point for fromVersion
            List<String> migratable = new ArrayList<>();
            List<String> skipped = new ArrayList<>();

            // Get migration point for fromVersion
            String migrationPoint = null;
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT migration_point FROM workflow_definition_versions WHERE workflow_name=? AND version_number=?"
                 )) {
                ps.setString(1, workflowName);
                ps.setString(2, fromVersion);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) migrationPoint = rs.getString("migration_point");
                }
            }

            // Find running instances on fromVersion at or past the migration point
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT instance_id, current_step_id FROM workflow_instances " +
                     "WHERE workflow_name=? AND workflow_version=? AND status IN ('RUNNING','WAITING')"
                 )) {
                ps.setString(1, workflowName);
                ps.setString(2, fromVersion);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String instanceId = rs.getString("instance_id");
                        String currentStep = rs.getString("current_step_id");
                        if (migrationPoint == null || isAtOrPastMigrationPoint(currentStep, migrationPoint)) {
                            migratable.add(instanceId);
                        } else {
                            skipped.add(instanceId);
                        }
                    }
                }
            }

            // Update version for migratable instances
            if (!migratable.isEmpty()) {
                try (Connection c = ds.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                         "UPDATE workflow_instances SET workflow_version=? WHERE instance_id=ANY(?::text[])"
                     )) {
                    ps.setString(1, toVersion);
                    ps.setArray(2, c.createArrayOf("text", migratable.toArray()));
                    ps.executeUpdate();
                }
            }

            migrationCounter.increment(migratable.size());
            return new MigrationResult(migratable.size(), skipped.size(), fromVersion, toVersion);
        });
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private WorkflowVersion mapVersion(ResultSet rs) throws SQLException {
        return new WorkflowVersion(
            rs.getString("version_id"),
            rs.getString("workflow_name"),
            rs.getString("version_number"),
            rs.getString("definition_json"),
            rs.getBoolean("is_active"),
            rs.getBoolean("deprecated"),
            rs.getString("deprecation_date"),
            rs.getString("migration_point"),
            rs.getTimestamp("created_at").toString()
        );
    }

    private boolean isAtOrPastMigrationPoint(String currentStep, String migrationPoint) {
        // Simplified: treat as "at migration point" if currentStep equals or comes after the point
        return currentStep != null && (currentStep.equals(migrationPoint) || currentStep.compareTo(migrationPoint) > 0);
    }

    private String now() { return java.time.Instant.now().toString(); }
}
