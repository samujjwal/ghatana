package com.ghatana.appplatform.manifest;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Immutable upgrade history audit.
 *              Records every upgrade attempt with: from/to version, list of migration scripts applied,
 *              smoke test results snapshot, and outcome. All entries written to K-07 audit trail.
 *              Supports annual export of the full history for compliance purposes.
 * @doc.layer   Platform Manifest (PU-004)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer; append-only
 *
 * STORY-PU004-008: Immutable upgrade history + K-07 + annual export
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS upgrade_history (
 *   history_id         TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   upgrade_id         TEXT NOT NULL UNIQUE,
 *   from_version       TEXT NOT NULL,
 *   to_version         TEXT NOT NULL,
 *   outcome            TEXT NOT NULL,  -- COMPLETED | ROLLED_BACK | FAILED
 *   migration_scripts  JSONB,
 *   smoke_results      JSONB,
 *   initiated_by       TEXT NOT NULL,
 *   failure_reason     TEXT,
 *   duration_ms        BIGINT,
 *   recorded_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 *
 * CREATE TABLE IF NOT EXISTS upgrade_history_exports (
 *   export_id   TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   year        INT  NOT NULL,
 *   record_count INT NOT NULL,
 *   sha256      TEXT,
 *   storage_ref TEXT,
 *   requested_by TEXT NOT NULL,
 *   exported_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class UpgradeHistoryAuditService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface K07AuditPort {
        /** Appends an immutable K-07 event. */
        void append(String eventType, String entityId, String detail) throws Exception;
    }

    public interface SecureStoragePort {
        /** Stores export bytes and returns a storage reference (e.g., S3 path). */
        String store(String filename, byte[] content) throws Exception;
    }

    public interface AuditPort {
        void audit(String event, String detail) throws Exception;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final K07AuditPort k07;
    private final SecureStoragePort storage;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter recordsAppended;
    private final Counter exportsGenerated;

    public UpgradeHistoryAuditService(
        javax.sql.DataSource ds,
        K07AuditPort k07,
        SecureStoragePort storage,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds               = ds;
        this.k07              = k07;
        this.storage          = storage;
        this.audit            = audit;
        this.executor         = executor;
        this.recordsAppended  = Counter.builder("manifest.upgrade_audit.records_appended").register(registry);
        this.exportsGenerated = Counter.builder("manifest.upgrade_audit.exports_generated").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Record a completed/rolled-back/failed upgrade in the immutable history.
     * Appends to K-07. Returns historyId.
     */
    public Promise<String> record(String upgradeId, String fromVersion, String toVersion,
                                   String outcome, String initiatedBy,
                                   List<String> migrationScripts, Map<String, String> smokeResults,
                                   String failureReason, long durationMs) {
        return Promise.ofBlocking(executor, () -> {
            String historyId = insert(upgradeId, fromVersion, toVersion, outcome, initiatedBy,
                listToJson(migrationScripts), mapToJson(smokeResults), failureReason, durationMs);
            k07.append("UPGRADE_RECORDED",
                upgradeId,
                "from=" + fromVersion + " to=" + toVersion + " outcome=" + outcome);
            recordsAppended.increment();
            audit.audit("UPGRADE_HISTORY_RECORDED", "upgradeId=" + upgradeId + " outcome=" + outcome);
            return historyId;
        });
    }

    /**
     * Export full upgrade history for a given year to secure storage.
     * Returns exportId. Output is a CSV with SHA-256 hash stored alongside.
     */
    public Promise<String> exportYear(int year, String requestedBy) {
        return Promise.ofBlocking(executor, () -> {
            List<String[]> rows = loadByYear(year);
            byte[] csv = buildCsv(rows);
            String sha256 = sha256Hex(csv);
            String filename = "upgrade-history-" + year + ".csv";
            String ref = storage.store(filename, csv);

            String exportId = insertExport(year, rows.size(), sha256, ref, requestedBy);
            k07.append("UPGRADE_HISTORY_EXPORTED", exportId,
                "year=" + year + " records=" + rows.size() + " sha256=" + sha256);
            exportsGenerated.increment();
            audit.audit("UPGRADE_HISTORY_EXPORTED", "year=" + year + " by=" + requestedBy);
            return exportId;
        });
    }

    /**
     * Get upgrade history record by upgradeId.
     */
    public Promise<Map<String, Object>> getByUpgradeId(String upgradeId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT history_id, upgrade_id, from_version, to_version, outcome, " +
                     "migration_scripts::text, smoke_results::text, initiated_by, " +
                     "failure_reason, duration_ms, recorded_at FROM upgrade_history WHERE upgrade_id=?"
                 )) {
                ps.setString(1, upgradeId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Collections.emptyMap();
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("historyId",        rs.getString("history_id"));
                    row.put("upgradeId",        rs.getString("upgrade_id"));
                    row.put("fromVersion",      rs.getString("from_version"));
                    row.put("toVersion",        rs.getString("to_version"));
                    row.put("outcome",          rs.getString("outcome"));
                    row.put("migrationScripts", rs.getString("migration_scripts"));
                    row.put("smokeResults",     rs.getString("smoke_results"));
                    row.put("initiatedBy",      rs.getString("initiated_by"));
                    row.put("failureReason",    rs.getString("failure_reason"));
                    row.put("durationMs",       rs.getLong("duration_ms"));
                    row.put("recordedAt",       rs.getTimestamp("recorded_at").toInstant().toString());
                    return row;
                }
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String insert(String upgradeId, String from, String to, String outcome,
                           String initiatedBy, String scriptsJson, String smokeJson,
                           String failureReason, long durationMs) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO upgrade_history " +
                 "(upgrade_id,from_version,to_version,outcome,migration_scripts,smoke_results," +
                 "initiated_by,failure_reason,duration_ms) " +
                 "VALUES (?,?,?,?,?::jsonb,?::jsonb,?,?,?) RETURNING history_id"
             )) {
            ps.setString(1, upgradeId); ps.setString(2, from); ps.setString(3, to);
            ps.setString(4, outcome);  ps.setString(5, scriptsJson); ps.setString(6, smokeJson);
            ps.setString(7, initiatedBy); ps.setString(8, failureReason); ps.setLong(9, durationMs);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private List<String[]> loadByYear(int year) throws SQLException {
        List<String[]> rows = new ArrayList<>();
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT upgrade_id, from_version, to_version, outcome, initiated_by, " +
                 "failure_reason, duration_ms, recorded_at FROM upgrade_history " +
                 "WHERE EXTRACT(YEAR FROM recorded_at)=? ORDER BY recorded_at ASC"
             )) {
            ps.setInt(1, year);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(new String[]{
                    rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                    rs.getString(5), rs.getString(6), String.valueOf(rs.getLong(7)),
                    rs.getTimestamp(8).toInstant().toString()
                });
            }
        }
        return rows;
    }

    private byte[] buildCsv(List<String[]> rows) {
        StringBuilder sb = new StringBuilder("upgrade_id,from_version,to_version,outcome,initiated_by,failure_reason,duration_ms,recorded_at\n");
        for (String[] r : rows) sb.append(String.join(",", r)).append("\n");
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String sha256Hex(byte[] data) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private String insertExport(int year, int count, String sha256, String ref, String by) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO upgrade_history_exports (year,record_count,sha256,storage_ref,requested_by) " +
                 "VALUES (?,?,?,?,?) RETURNING export_id"
             )) {
            ps.setInt(1, year); ps.setInt(2, count);
            ps.setString(3, sha256); ps.setString(4, ref); ps.setString(5, by);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    private String listToJson(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (String s : items) sb.append("\"").append(s.replace("\"", "\\\"")).append("\",");
        if (sb.length() > 1 && sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
        return sb.append("]").toString();
    }

    private String mapToJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        map.forEach((k, v) -> sb.append("\"").append(k).append("\":\"").append(v.replace("\"", "\\\"")).append("\","));
        if (sb.length() > 1 && sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
        return sb.append("}").toString();
    }
}
