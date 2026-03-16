package com.ghatana.appplatform.regulator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Packages regulatory data exports requested by licensed regulators.
 *              Each export is generated asynchronously: raw data is extracted,
 *              the bundle is compressed, a SHA-256 manifest is computed, and a
 *              time-limited secure download link is emailed to the regulator.
 *              Supports TRANSACTION, AUDIT_LOG, COMPLIANCE_REPORT, and FULL_DUMP types.
 * @doc.layer   Regulator Portal (R-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-R01-005: Regulatory data export packaging with SHA-256 manifest
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS regulatory_export_requests (
 *   export_id      TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   regulator_id   TEXT NOT NULL,
 *   export_type    TEXT NOT NULL,    -- TRANSACTION | AUDIT_LOG | COMPLIANCE_REPORT | FULL_DUMP
 *   date_from      DATE NOT NULL,
 *   date_to        DATE NOT NULL,
 *   jurisdiction   TEXT,
 *   status         TEXT NOT NULL DEFAULT 'PENDING',  -- PENDING | PROCESSING | READY | FAILED
 *   sha256         TEXT,
 *   download_url   TEXT,
 *   url_expires_at TIMESTAMPTZ,
 *   requested_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   ready_at       TIMESTAMPTZ,
 *   row_count      INT
 * );
 * </pre>
 */
public class RegulatoryDataExportPackagingService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface DataExtractPort {
        /** Extract rows for the given type and date range into a byte buffer (CSV). */
        byte[] extractCsv(String exportType, String dateFrom, String dateTo, String jurisdiction) throws Exception;
    }

    public interface SecureStoragePort {
        /** Upload bundle and return a pre-signed URL valid for the specified hours. */
        String uploadAndPresign(String exportId, byte[] data, int ttlHours) throws Exception;
    }

    public interface EmailPort {
        void sendDownloadLink(String regulatorId, String exportId, String downloadUrl, String sha256) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private static final int DOWNLOAD_TTL_HOURS = 72;

    private final javax.sql.DataSource ds;
    private final DataExtractPort extractor;
    private final SecureStoragePort storage;
    private final EmailPort email;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter exportsReady;
    private final Counter exportsFailed;

    public RegulatoryDataExportPackagingService(
        javax.sql.DataSource ds,
        DataExtractPort extractor,
        SecureStoragePort storage,
        EmailPort email,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds           = ds;
        this.extractor    = extractor;
        this.storage      = storage;
        this.email        = email;
        this.audit        = audit;
        this.executor     = executor;
        this.exportsReady  = Counter.builder("regulator.export.ready").register(registry);
        this.exportsFailed = Counter.builder("regulator.export.failed").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Submit an export request and trigger async processing. Returns the exportId. */
    public Promise<String> requestExport(String regulatorId, String exportType,
                                          String dateFrom, String dateTo, String jurisdiction) {
        return Promise.ofBlocking(executor, () -> {
            String exportId = insertRequest(regulatorId, exportType, dateFrom, dateTo, jurisdiction);
            audit.record(regulatorId, "EXPORT_REQUESTED",
                "exportId=" + exportId + " type=" + exportType + " from=" + dateFrom + " to=" + dateTo);
            processAsync(exportId, regulatorId, exportType, dateFrom, dateTo, jurisdiction);
            return exportId;
        });
    }

    /** Poll export status. */
    public Promise<Map<String, Object>> getStatus(String exportId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT status, sha256, download_url, url_expires_at, row_count, ready_at " +
                     "FROM regulatory_export_requests WHERE export_id=?"
                 )) {
                ps.setString(1, exportId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new NoSuchElementException("Export not found: " + exportId);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("exportId",    exportId);
                    m.put("status",      rs.getString("status"));
                    m.put("sha256",      rs.getString("sha256"));
                    m.put("downloadUrl", rs.getString("download_url"));
                    m.put("rowCount",    rs.getObject("row_count"));
                    Timestamp ready = rs.getTimestamp("ready_at");
                    if (ready != null) m.put("readyAt", ready.toInstant().toString());
                    return m;
                }
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String insertRequest(String regulatorId, String exportType, String dateFrom,
                                  String dateTo, String jurisdiction) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO regulatory_export_requests (regulator_id, export_type, date_from, date_to, jurisdiction) " +
                 "VALUES (?,?,?::date,?::date,?) RETURNING export_id"
             )) {
            ps.setString(1, regulatorId); ps.setString(2, exportType);
            ps.setString(3, dateFrom);    ps.setString(4, dateTo); ps.setString(5, jurisdiction);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    /**
     * Runs on the blocking executor — extract → compute SHA-256 → upload → email.
     * Updates status to READY or FAILED.
     */
    private void processAsync(String exportId, String regulatorId, String exportType,
                               String dateFrom, String dateTo, String jurisdiction) {
        executor.execute(() -> {
            try {
                updateStatus(exportId, "PROCESSING", null, null, null);
                byte[] csv = extractor.extractCsv(exportType, dateFrom, dateTo, jurisdiction);
                String sha256 = sha256Hex(csv);
                String url    = storage.uploadAndPresign(exportId, csv, DOWNLOAD_TTL_HOURS);
                int rows = countLines(csv) - 1; // minus header
                try (Connection c = ds.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                         "UPDATE regulatory_export_requests SET status='READY', sha256=?, download_url=?, " +
                         "url_expires_at=NOW()+INTERVAL '72 hours', row_count=?, ready_at=NOW() WHERE export_id=?"
                     )) {
                    ps.setString(1, sha256); ps.setString(2, url);
                    ps.setInt(3, rows);      ps.setString(4, exportId);
                    ps.executeUpdate();
                }
                email.sendDownloadLink(regulatorId, exportId, url, sha256);
                exportsReady.increment();
            } catch (Exception ex) {
                try { updateStatus(exportId, "FAILED", null, null, null); } catch (Exception ignored) {}
                exportsFailed.increment();
            }
        });
    }

    private void updateStatus(String exportId, String status, String sha256, String url, Integer rows) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE regulatory_export_requests SET status=? WHERE export_id=?"
             )) {
            ps.setString(1, status); ps.setString(2, exportId); ps.executeUpdate();
        }
    }

    private String sha256Hex(byte[] data) throws Exception {
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] bytes = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private int countLines(byte[] data) {
        int lines = 0;
        for (byte b : data) if (b == '\n') lines++;
        return lines;
    }
}
