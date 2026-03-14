package com.ghatana.appplatform.audit.export;

import com.ghatana.appplatform.audit.domain.AuditEntry;
import com.ghatana.appplatform.audit.port.AuditTrailStore;

import javax.sql.DataSource;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.util.logging.Logger;

/**
 * Streams audit log records as CSV or newline-delimited JSON for compliance exports.
 *
 * <p>Outputs directly to an {@link OutputStream} to avoid loading entire result sets
 * into memory. Suitable for large tenant audit exports.
 *
 * @doc.type class
 * @doc.purpose Streaming audit export to CSV / NDJSON (STORY-K07-008/009)
 * @doc.layer product
 * @doc.pattern Service
 */
public class AuditExportService {

    private static final Logger LOG = Logger.getLogger(AuditExportService.class.getName());

    private static final String[] CSV_HEADERS = {
        "audit_id", "tenant_id", "event_type", "actor_id", "actor_type",
        "resource_id", "resource_type", "outcome", "occurred_at", "sequence_number"
    };

    private final DataSource dataSource;

    public AuditExportService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Export audit entries as RFC 4180 CSV.
     *
     * @param tenantId  Tenant scope
     * @param from      Start timestamp (inclusive)
     * @param to        End timestamp (inclusive)
     * @param output    Stream to write CSV rows to
     */
    public void exportCsv(String tenantId, Instant from, Instant to, OutputStream output) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = buildQuery(conn, tenantId, from, to);
             ResultSet rs = ps.executeQuery();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {

            writer.println(String.join(",", CSV_HEADERS));

            long count = 0;
            while (rs.next()) {
                writer.println(toCsvRow(rs));
                count++;
            }
            writer.flush();
            LOG.info("[AuditExportService] CSV export completed tenant=" + tenantId + " rows=" + count);
        } catch (SQLException e) {
            throw new RuntimeException("Audit CSV export failed for tenant=" + tenantId, e);
        }
    }

    /**
     * Export audit entries as newline-delimited JSON (NDJSON).
     *
     * @param tenantId  Tenant scope
     * @param from      Start timestamp (inclusive)
     * @param to        End timestamp (inclusive)
     * @param output    Stream to write NDJSON lines to
     */
    public void exportNdjson(String tenantId, Instant from, Instant to, OutputStream output) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = buildQuery(conn, tenantId, from, to);
             ResultSet rs = ps.executeQuery();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8))) {

            long count = 0;
            while (rs.next()) {
                writer.println(toJsonLine(rs));
                count++;
            }
            writer.flush();
            LOG.info("[AuditExportService] NDJSON export completed tenant=" + tenantId + " rows=" + count);
        } catch (SQLException e) {
            throw new RuntimeException("Audit NDJSON export failed for tenant=" + tenantId, e);
        }
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private PreparedStatement buildQuery(Connection conn, String tenantId,
                                         Instant from, Instant to) throws SQLException {
        String sql = """
            SELECT audit_id, tenant_id, event_type, actor_id, actor_type,
                   resource_id, resource_type, outcome, occurred_at, sequence_number
              FROM audit_logs
             WHERE tenant_id = ?
               AND occurred_at >= ?
               AND occurred_at <= ?
             ORDER BY sequence_number ASC
            """;
        PreparedStatement ps = conn.prepareStatement(sql);
        ps.setString(1, tenantId);
        ps.setTimestamp(2, Timestamp.from(from));
        ps.setTimestamp(3, Timestamp.from(to));
        ps.setFetchSize(1000); // stream rows
        return ps;
    }

    private String toCsvRow(ResultSet rs) throws SQLException {
        return String.join(",",
            escape(rs.getString("audit_id")),
            escape(rs.getString("tenant_id")),
            escape(rs.getString("event_type")),
            escape(rs.getString("actor_id")),
            escape(rs.getString("actor_type")),
            escape(rs.getString("resource_id")),
            escape(rs.getString("resource_type")),
            escape(rs.getString("outcome")),
            rs.getString("occurred_at"),
            String.valueOf(rs.getLong("sequence_number"))
        );
    }

    private String toJsonLine(ResultSet rs) throws SQLException {
        return "{\"audit_id\":\"" + rs.getString("audit_id") + "\""
            + ",\"tenant_id\":\"" + rs.getString("tenant_id") + "\""
            + ",\"event_type\":\"" + rs.getString("event_type") + "\""
            + ",\"actor_id\":\"" + rs.getString("actor_id") + "\""
            + ",\"actor_type\":\"" + rs.getString("actor_type") + "\""
            + ",\"resource_id\":\"" + rs.getString("resource_id") + "\""
            + ",\"resource_type\":\"" + rs.getString("resource_type") + "\""
            + ",\"outcome\":\"" + rs.getString("outcome") + "\""
            + ",\"occurred_at\":\"" + rs.getString("occurred_at") + "\""
            + ",\"sequence_number\":" + rs.getLong("sequence_number")
            + "}";
    }

    /** CSV-escape a field: wrap in quotes if contains comma/quote/newline. */
    private String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
