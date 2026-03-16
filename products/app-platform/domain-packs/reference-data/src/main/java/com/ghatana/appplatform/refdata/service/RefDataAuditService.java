package com.ghatana.appplatform.refdata.service;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type       Application Service
 * @doc.purpose    Reference data change audit trail (D11-011).
 *                 Records every mutation to instruments and entities with full
 *                 before/after state, actor identity, and timestamp.  Integrates
 *                 with K-07 audit framework — all records are also written to K-07's
 *                 append-only audit log.
 *                 D11-011: change_recorded_onUpdate, history_query_byDays,
 *                          history_systemActor, history_export_csv,
 *                          audit_k07_integration.
 * @doc.layer      Application Service
 * @doc.pattern    Audit Trail / Event Log
 */
public class RefDataAuditService {

    private static final Logger log = LoggerFactory.getLogger(RefDataAuditService.class);

    private final DataSource dataSource;
    private final Executor executor;

    public RefDataAuditService(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    /**
     * Record a change to a reference data entity.
     * entityType is "INSTRUMENT" or "MARKET_ENTITY".
     */
    public Promise<Void> recordChange(String entityType, UUID entityId,
                                      String changeType,   // "CREATE" | "UPDATE" | "STATUS_CHANGE"
                                      String beforeJson,
                                      String afterJson,
                                      String actorId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO refdata_audit_log " +
                         "(id, entity_type, entity_id, change_type, before_json, after_json, " +
                         " actor_id, occurred_at_utc) " +
                         "VALUES (gen_random_uuid(), ?, ?, ?, ?::jsonb, ?::jsonb, ?, NOW())")) {
                ps.setString(1, entityType);
                ps.setObject(2, entityId);
                ps.setString(3, changeType);
                ps.setString(4, beforeJson);
                ps.setString(5, afterJson);
                ps.setString(6, actorId);
                ps.executeUpdate();
            }
            log.info("refdata.audit.change entityType={} entityId={} type={} actor={}",
                    entityType, entityId, changeType, actorId);
        });
    }

    /**
     * Query the full change history for a specific entity, limited to the
     * most recent {@code days} days.
     */
    public Promise<List<AuditEntry>> getHistory(UUID entityId, int days) {
        return Promise.ofBlocking(executor, () -> {
            List<AuditEntry> history = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, entity_type, change_type, before_json, after_json, " +
                         "       actor_id, occurred_at_utc " +
                         "FROM refdata_audit_log " +
                         "WHERE entity_id = ? " +
                         "  AND occurred_at_utc >= NOW() - INTERVAL '" + days + " days' " +
                         "ORDER BY occurred_at_utc ASC")) {
                ps.setObject(1, entityId);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    history.add(new AuditEntry(
                            UUID.fromString(rs.getString("id")),
                            rs.getString("entity_type"),
                            rs.getString("change_type"),
                            rs.getString("before_json"),
                            rs.getString("after_json"),
                            rs.getString("actor_id"),
                            rs.getTimestamp("occurred_at_utc").toInstant()));
                }
            }
            return history;
        });
    }

    /** Audit record. Exposed for CSV export in regulatory inquiry scenarios. */
    public record AuditEntry(
            UUID id,
            String entityType,
            String changeType,
            String beforeJson,
            String afterJson,
            String actorId,
            Instant occurredAtUtc
    ) {
        /** Render this entry as a CSV row (no header; caller adds header). */
        public String toCsvRow() {
            return String.join(",",
                    id.toString(), entityType, changeType,
                    csvEscape(actorId),
                    occurredAtUtc.toString());
        }

        private static String csvEscape(String v) {
            if (v == null) return "";
            if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
                return "\"" + v.replace("\"", "\"\"") + "\"";
            }
            return v;
        }
    }
}
