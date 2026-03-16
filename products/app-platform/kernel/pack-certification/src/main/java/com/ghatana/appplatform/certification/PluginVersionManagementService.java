package com.ghatana.appplatform.certification;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Manage multiple versions of a plugin within the marketplace.
 *              Latest stable version is the default; tenants can pin to a specific version.
 *              Auto-upgrade: pinned=FALSE tenants receive the latest certified version
 *              automatically within the operator-defined upgrade window.
 *              Changelog per version; breaking changes require explicit tenant approval.
 *              Rollback: tenant or operator can revert to prior version.
 * @doc.layer   Pack Certification (P-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-P01-008: Plugin version management in marketplace
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS plugin_versions (
 *   version_id       TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   plugin_id        TEXT NOT NULL,
 *   version          TEXT NOT NULL,
 *   changelog        TEXT NOT NULL,
 *   has_breaking_changes BOOLEAN NOT NULL DEFAULT FALSE,
 *   is_stable        BOOLEAN NOT NULL DEFAULT FALSE,
 *   is_latest_stable BOOLEAN NOT NULL DEFAULT FALSE,
 *   cert_status      TEXT NOT NULL DEFAULT 'PENDING',  -- PENDING | CERTIFIED | REVOKED
 *   published_at     TIMESTAMPTZ,
 *   UNIQUE (plugin_id, version)
 * );
 * CREATE TABLE IF NOT EXISTS plugin_tenant_installs (
 *   install_id     TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   plugin_id      TEXT NOT NULL,
 *   tenant_id      TEXT NOT NULL,
 *   pinned_version TEXT,          -- NULL = follow latest stable
 *   active_version TEXT NOT NULL,
 *   auto_upgrade   BOOLEAN NOT NULL DEFAULT TRUE,
 *   installed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   UNIQUE (plugin_id, tenant_id)
 * );
 * </pre>
 */
public class PluginVersionManagementService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface SandboxUpgradePort {
        void upgradePlugin(String tenantId, String pluginId, String fromVersion, String toVersion) throws Exception;
        void rollbackPlugin(String tenantId, String pluginId, String toVersion) throws Exception;
    }

    public interface NotificationPort {
        void notifyTenant(String tenantId, String subject, String body) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final SandboxUpgradePort upgradePort;
    private final NotificationPort notify;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter autoUpgradesCounter;

    public PluginVersionManagementService(
        javax.sql.DataSource ds,
        SandboxUpgradePort upgradePort,
        NotificationPort notify,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                 = ds;
        this.upgradePort        = upgradePort;
        this.notify             = notify;
        this.audit              = audit;
        this.executor           = executor;
        this.autoUpgradesCounter = Counter.builder("certification.plugin.auto_upgrades").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Publish a new certified version, marking it as the latest stable. */
    public Promise<Void> publishVersion(String pluginId, String version, String changelog,
                                         boolean hasBreakingChanges, String publishedBy) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection()) {
                c.setAutoCommit(false);
                // Demote previous latest stable
                try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE plugin_versions SET is_latest_stable=FALSE WHERE plugin_id=? AND is_latest_stable=TRUE"
                )) { ps.setString(1, pluginId); ps.executeUpdate(); }

                // Insert or promote new version
                try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO plugin_versions (plugin_id, version, changelog, has_breaking_changes, is_stable, is_latest_stable, cert_status, published_at) " +
                    "VALUES (?,?,?,?,TRUE,TRUE,'CERTIFIED',NOW()) " +
                    "ON CONFLICT (plugin_id, version) DO UPDATE SET is_stable=TRUE, is_latest_stable=TRUE, cert_status='CERTIFIED', published_at=NOW()"
                )) {
                    ps.setString(1, pluginId); ps.setString(2, version);
                    ps.setString(3, changelog); ps.setBoolean(4, hasBreakingChanges);
                    ps.executeUpdate();
                }
                c.commit();
            }
            audit.record(publishedBy, "PLUGIN_VERSION_PUBLISHED",
                "pluginId=" + pluginId + " version=" + version + " breaking=" + hasBreakingChanges);
            return null;
        });
    }

    /**
     * Run auto-upgrades for all tenants following latest.
     * Breaking-change versions require explicit approval first and are skipped here.
     */
    public Promise<Integer> runAutoUpgrades(String pluginId) {
        return Promise.ofBlocking(executor, () -> {
            VersionInfo latest = getLatestStable(pluginId);
            if (latest == null) return 0;

            List<InstallRecord> toUpgrade = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT install_id, tenant_id, active_version FROM plugin_tenant_installs " +
                     "WHERE plugin_id=? AND auto_upgrade=TRUE AND pinned_version IS NULL AND active_version <> ?"
                 )) {
                ps.setString(1, pluginId); ps.setString(2, latest.version());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) toUpgrade.add(new InstallRecord(
                        rs.getString("install_id"), rs.getString("tenant_id"), rs.getString("active_version")));
                }
            }

            int upgraded = 0;
            for (InstallRecord install : toUpgrade) {
                if (latest.hasBreakingChanges()) continue; // skip — needs explicit approval
                upgradePort.upgradePlugin(install.tenantId(), pluginId, install.activeVersion(), latest.version());
                updateActiveVersion(install.installId(), latest.version());
                notify.notifyTenant(install.tenantId(), "Plugin " + pluginId + " upgraded to " + latest.version(), latest.changelog());
                autoUpgradesCounter.increment();
                upgraded++;
            }
            return upgraded;
        });
    }

    /** Pin a tenant to a specific version. */
    public Promise<Void> pin(String pluginId, String tenantId, String version, String requestedBy) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE plugin_tenant_installs SET pinned_version=?, auto_upgrade=FALSE WHERE plugin_id=? AND tenant_id=?"
                 )) {
                ps.setString(1, version); ps.setString(2, pluginId); ps.setString(3, tenantId);
                ps.executeUpdate();
            }
            audit.record(requestedBy, "PLUGIN_VERSION_PINNED", "pluginId=" + pluginId + " tenantId=" + tenantId + " version=" + version);
            return null;
        });
    }

    /** Rollback a tenant to a previous version. */
    public Promise<Void> rollback(String pluginId, String tenantId, String targetVersion, String requestedBy) {
        return Promise.ofBlocking(executor, () -> {
            String current = getActiveVersion(pluginId, tenantId);
            upgradePort.rollbackPlugin(tenantId, pluginId, targetVersion);
            updateActiveVersionByTenant(pluginId, tenantId, targetVersion);
            audit.record(requestedBy, "PLUGIN_VERSION_ROLLBACK",
                "pluginId=" + pluginId + " tenantId=" + tenantId + " from=" + current + " to=" + targetVersion);
            return null;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private record VersionInfo(String version, boolean hasBreakingChanges, String changelog) {}
    private record InstallRecord(String installId, String tenantId, String activeVersion) {}

    private VersionInfo getLatestStable(String pluginId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT version, has_breaking_changes, changelog FROM plugin_versions " +
                 "WHERE plugin_id=? AND is_latest_stable=TRUE AND cert_status='CERTIFIED'"
             )) {
            ps.setString(1, pluginId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new VersionInfo(rs.getString("version"), rs.getBoolean("has_breaking_changes"), rs.getString("changelog"));
            }
        }
    }

    private void updateActiveVersion(String installId, String version) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE plugin_tenant_installs SET active_version=?, updated_at=NOW() WHERE install_id=?"
             )) {
            ps.setString(1, version); ps.setString(2, installId); ps.executeUpdate();
        }
    }

    private void updateActiveVersionByTenant(String pluginId, String tenantId, String version) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE plugin_tenant_installs SET active_version=?, updated_at=NOW() WHERE plugin_id=? AND tenant_id=?"
             )) {
            ps.setString(1, version); ps.setString(2, pluginId); ps.setString(3, tenantId); ps.executeUpdate();
        }
    }

    private String getActiveVersion(String pluginId, String tenantId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT active_version FROM plugin_tenant_installs WHERE plugin_id=? AND tenant_id=?"
             )) {
            ps.setString(1, pluginId); ps.setString(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString("active_version") : "unknown"; }
        }
    }
}
