package com.ghatana.appplatform.manifest;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose YAML-based release manifest registry for platform releases.
 *              Each manifest captures: releaseId (SemVer), list of services with imageTag,
 *              list of included plugins, config changes, migration scripts, and breaking changes.
 *              Schema validation enforced (semver format + required fields).
 *              Manifest lifecycle: DRAFT → SIGNED → PUBLISHED.
 *              Only SIGNED manifests may be deployed (ManifestSigningVerificationService gate).
 * @doc.layer   Platform Manifest (PU-004)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-PU004-001: Release manifest definition format
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS release_manifests (
 *   manifest_id      TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   release_id       TEXT NOT NULL UNIQUE,         -- e.g. "2025.1.0"
 *   semver           TEXT NOT NULL,                -- strict semver
 *   description      TEXT NOT NULL,
 *   services         JSONB NOT NULL DEFAULT '[]',  -- [{name, imageTag, minReplicas}]
 *   plugins          JSONB NOT NULL DEFAULT '[]',  -- [{pluginId, version, tier}]
 *   config_changes   JSONB NOT NULL DEFAULT '[]',  -- [{key, oldValue, newValue}]
 *   migration_scripts JSONB NOT NULL DEFAULT '[]', -- [{scriptId, targetDb, description}]
 *   breaking_changes JSONB NOT NULL DEFAULT '[]',  -- [{component, description, migrationGuide}]
 *   status           TEXT NOT NULL DEFAULT 'DRAFT', -- DRAFT | SIGNED | PUBLISHED | DEPRECATED
 *   created_by       TEXT NOT NULL,
 *   created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * CREATE TABLE IF NOT EXISTS release_manifest_audit (
 *   audit_id     TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   manifest_id  TEXT NOT NULL,
 *   action       TEXT NOT NULL,
 *   actor_id     TEXT NOT NULL,
 *   detail       TEXT,
 *   recorded_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class ReleaseManifestService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface YamlParserPort {
        /** Parse manifest YAML into a structured map. */
        Map<String, Object> parse(String yaml) throws Exception;
        /** Serialize structured map back to canonical YAML. */
        String serialize(Map<String, Object> data) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public record ServiceEntry(String name, String imageTag, int minReplicas) {}
    public record PluginEntry(String pluginId, String version, String tier) {}
    public record ConfigChange(String key, String oldValue, String newValue) {}
    public record MigrationScript(String scriptId, String targetDb, String description) {}
    public record BreakingChange(String component, String description, String migrationGuide) {}

    public record ReleaseManifest(
        String manifestId, String releaseId, String semver, String description,
        List<ServiceEntry> services, List<PluginEntry> plugins,
        List<ConfigChange> configChanges, List<MigrationScript> migrationScripts,
        List<BreakingChange> breakingChanges, String status, String createdBy
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private static final java.util.regex.Pattern SEMVER_RE =
        java.util.regex.Pattern.compile("^\\d{4}\\.\\d+\\.\\d+(-[a-zA-Z0-9.]+)?$");

    private final javax.sql.DataSource ds;
    private final YamlParserPort yaml;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter manifestsCreatedCounter;
    private final Counter manifestsPublishedCounter;

    public ReleaseManifestService(
        javax.sql.DataSource ds,
        YamlParserPort yaml,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds                       = ds;
        this.yaml                     = yaml;
        this.audit                    = audit;
        this.executor                 = executor;
        this.manifestsCreatedCounter  = Counter.builder("manifest.created").register(registry);
        this.manifestsPublishedCounter = Counter.builder("manifest.published").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Create a new DRAFT release manifest. Validates semver and required fields.
     */
    public Promise<ReleaseManifest> create(
        String releaseId, String semver, String description,
        List<ServiceEntry> services, List<PluginEntry> plugins,
        List<ConfigChange> configChanges, List<MigrationScript> migrationScripts,
        List<BreakingChange> breakingChanges, String createdBy
    ) {
        return Promise.ofBlocking(executor, () -> {
            validateSemver(semver);
            if (services.isEmpty()) throw new IllegalArgumentException("Manifest must include at least one service");

            String servicesJson     = toJsonArray(services);
            String pluginsJson      = toJsonArray(plugins);
            String configJson       = toJsonArray(configChanges);
            String migrationsJson   = toJsonArray(migrationScripts);
            String breakingJson     = toJsonArray(breakingChanges);

            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO release_manifests (release_id, semver, description, services, plugins, " +
                     "config_changes, migration_scripts, breaking_changes, created_by) " +
                     "VALUES (?,?,?,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?::jsonb,?) RETURNING manifest_id"
                 )) {
                ps.setString(1, releaseId); ps.setString(2, semver); ps.setString(3, description);
                ps.setString(4, servicesJson); ps.setString(5, pluginsJson);
                ps.setString(6, configJson); ps.setString(7, migrationsJson);
                ps.setString(8, breakingJson); ps.setString(9, createdBy);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    String manifestId = rs.getString("manifest_id");
                    manifestsCreatedCounter.increment();
                    auditAction(c, manifestId, "MANIFEST_CREATED", createdBy, "releaseId=" + releaseId);
                    audit.record(createdBy, "MANIFEST_CREATED", "manifestId=" + manifestId + " releaseId=" + releaseId);
                    return new ReleaseManifest(manifestId, releaseId, semver, description,
                        services, plugins, configChanges, migrationScripts, breakingChanges,
                        "DRAFT", createdBy);
                }
            }
        });
    }

    /**
     * Transition a SIGNED manifest to PUBLISHED — makes it available for deployment.
     */
    public Promise<Void> publish(String manifestId, String publishedBy) {
        return Promise.ofBlocking(executor, () -> {
            transition(manifestId, "SIGNED", "PUBLISHED");
            try (Connection c = ds.getConnection()) {
                auditAction(c, manifestId, "MANIFEST_PUBLISHED", publishedBy, null);
            }
            audit.record(publishedBy, "MANIFEST_PUBLISHED", "manifestId=" + manifestId);
            manifestsPublishedCounter.increment();
            return null;
        });
    }

    /** Deprecate an older manifest — prevents future deployments. */
    public Promise<Void> deprecate(String manifestId, String operatorId) {
        return Promise.ofBlocking(executor, () -> {
            transition(manifestId, "PUBLISHED", "DEPRECATED");
            audit.record(operatorId, "MANIFEST_DEPRECATED", "manifestId=" + manifestId);
            return null;
        });
    }

    /** Retrieve a manifest by its releaseId (e.g. "2025.1.0"). */
    public Promise<ReleaseManifest> getByReleaseId(String releaseId) {
        return Promise.ofBlocking(executor, () -> loadByReleaseId(releaseId));
    }

    /** List all manifests in a given status. */
    public Promise<List<Map<String, String>>> listByStatus(String status) {
        return Promise.ofBlocking(executor, () -> {
            List<Map<String, String>> rows = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT manifest_id, release_id, semver, status, created_by, created_at::text " +
                     "FROM release_manifests WHERE status=? ORDER BY created_at DESC"
                 )) {
                ps.setString(1, status);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> row = new LinkedHashMap<>();
                        row.put("manifestId", rs.getString("manifest_id"));
                        row.put("releaseId",  rs.getString("release_id"));
                        row.put("semver",     rs.getString("semver"));
                        row.put("status",     rs.getString("status"));
                        row.put("createdBy",  rs.getString("created_by"));
                        row.put("createdAt",  rs.getString("created_at"));
                        rows.add(row);
                    }
                }
            }
            return rows;
        });
    }

    // ── Package-visible helper (used by ManifestSigningVerificationService) ──

    /** Mark a manifest as SIGNED. Called by ManifestSigningVerificationService. */
    Promise<Void> markSigned(String manifestId) {
        return Promise.ofBlocking(executor, () -> {
            transition(manifestId, "DRAFT", "SIGNED");
            return null;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void validateSemver(String semver) {
        if (!SEMVER_RE.matcher(semver).matches()) {
            throw new IllegalArgumentException("Invalid semver: " + semver +
                " — expected format YYYY.MINOR.PATCH (e.g. 2025.1.0)");
        }
    }

    private void transition(String manifestId, String expectedFrom, String to) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE release_manifests SET status=?, updated_at=NOW() WHERE manifest_id=? AND status=?"
             )) {
            ps.setString(1, to); ps.setString(2, manifestId); ps.setString(3, expectedFrom);
            int rows = ps.executeUpdate();
            if (rows == 0) throw new IllegalStateException(
                "Manifest " + manifestId + " not in expected status " + expectedFrom);
        }
    }

    private ReleaseManifest loadByReleaseId(String releaseId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT manifest_id, release_id, semver, description, status, created_by FROM release_manifests WHERE release_id=?"
             )) {
            ps.setString(1, releaseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Manifest not found: " + releaseId);
                // Plugin/service/change lists are JSONB; return lightweight view here
                return new ReleaseManifest(
                    rs.getString("manifest_id"), rs.getString("release_id"), rs.getString("semver"),
                    rs.getString("description"), List.of(), List.of(), List.of(), List.of(), List.of(),
                    rs.getString("status"), rs.getString("created_by")
                );
            }
        }
    }

    private void auditAction(Connection c, String manifestId, String action,
                              String actorId, String detail) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "INSERT INTO release_manifest_audit (manifest_id, action, actor_id, detail) VALUES (?,?,?,?)"
        )) {
            ps.setString(1, manifestId); ps.setString(2, action);
            ps.setString(3, actorId); ps.setString(4, detail);
            ps.executeUpdate();
        }
    }

    // ── Minimal JSONB serialization (no external JSON library) ────────────────

    private String toJsonArray(List<?> items) {
        if (items.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (Object item : items) {
            sb.append(objectToJson(item)).append(",");
        }
        sb.setCharAt(sb.length() - 1, ']');
        return sb.toString();
    }

    private String objectToJson(Object obj) {
        return switch (obj) {
            case ServiceEntry s  -> "{\"name\":\"" + s.name() + "\",\"imageTag\":\"" + s.imageTag() + "\",\"minReplicas\":" + s.minReplicas() + "}";
            case PluginEntry p   -> "{\"pluginId\":\"" + p.pluginId() + "\",\"version\":\"" + p.version() + "\",\"tier\":\"" + p.tier() + "\"}";
            case ConfigChange cc -> "{\"key\":\"" + cc.key() + "\",\"oldValue\":\"" + cc.oldValue() + "\",\"newValue\":\"" + cc.newValue() + "\"}";
            case MigrationScript m -> "{\"scriptId\":\"" + m.scriptId() + "\",\"targetDb\":\"" + m.targetDb() + "\",\"description\":\"" + m.description() + "\"}";
            case BreakingChange b -> "{\"component\":\"" + b.component() + "\",\"description\":\"" + b.description() + "\",\"migrationGuide\":\"" + b.migrationGuide() + "\"}";
            default -> "{}";
        };
    }
}
