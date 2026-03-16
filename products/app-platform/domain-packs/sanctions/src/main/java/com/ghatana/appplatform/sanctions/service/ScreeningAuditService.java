package com.ghatana.appplatform.sanctions.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @doc.type      Service
 * @doc.purpose   Immutable audit trail for all sanctions screening events. Records every
 *                screen, match, review decision, and list change. Provides PDF evidence export
 *                for regulatory submissions. Audit entries are append-only (never updated/deleted).
 * @doc.layer     Application
 * @doc.pattern   Append-only audit log with evidence export
 *
 * Story: D14-008
 */
public class ScreeningAuditService {

    private static final Logger log = LoggerFactory.getLogger(ScreeningAuditService.class);

    private final DataSource       dataSource;
    private final PdfRendererPort  pdfRendererPort;
    private final Counter          auditEntriesWritten;

    public ScreeningAuditService(DataSource dataSource,
                                  PdfRendererPort pdfRendererPort,
                                  MeterRegistry meterRegistry) {
        this.dataSource          = dataSource;
        this.pdfRendererPort     = pdfRendererPort;
        this.auditEntriesWritten = meterRegistry.counter("sanctions.audit.entries_written");
    }

    /**
     * Records a screening event (screen_started, match_found, clear, etc.).
     *
     * @param eventType   e.g. SCREEN_STARTED, MATCH_FOUND, CLEAR, REVIEW_CREATED
     * @param clientId    affected client
     * @param detail      free-text detail (match score, entity ref, algorithm, etc.)
     * @param actorId     system or user identifier triggering the event
     */
    public void record(String eventType, String clientId, String detail, String actorId) {
        String sql = "INSERT INTO sanctions_audit_log(event_type, client_id, detail, actor_id, occurred_at) "
                   + "VALUES(?,?,?,?,?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, eventType);
            ps.setString(2, clientId);
            ps.setString(3, detail);
            ps.setString(4, actorId);
            ps.setTimestamp(5, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        } catch (SQLException e) {
            // Audit must not fail silently — log and rethrow so caller can alert
            log.error("ScreeningAudit: failed to write event={} client={}", eventType, clientId, e);
            throw new RuntimeException("Audit write failed", e);
        }
        auditEntriesWritten.increment();
    }

    /**
     * Returns the full audit trail for a client, ordered chronologically.
     *
     * @param clientId  client to query
     * @param from      start of window (inclusive)
     * @param to        end of window (inclusive)
     */
    public List<AuditEntry> getTrail(String clientId, Instant from, Instant to) {
        String sql = "SELECT event_type, client_id, detail, actor_id, occurred_at "
                   + "FROM sanctions_audit_log "
                   + "WHERE client_id=? AND occurred_at BETWEEN ? AND ? "
                   + "ORDER BY occurred_at ASC";
        List<AuditEntry> entries = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, clientId);
            ps.setTimestamp(2, Timestamp.from(from));
            ps.setTimestamp(3, Timestamp.from(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new AuditEntry(rs.getString("event_type"), rs.getString("client_id"),
                            rs.getString("detail"), rs.getString("actor_id"),
                            rs.getTimestamp("occurred_at").toInstant()));
                }
            }
        } catch (SQLException e) {
            log.error("getTrail DB error client={}", clientId, e);
        }
        return entries;
    }

    /**
     * Exports the audit trail for a client as a signed PDF evidence package.
     *
     * @param clientId  client for the evidence package
     * @param from      start date
     * @param to        end date
     * @return PDF bytes
     */
    public byte[] exportEvidencePdf(String clientId, Instant from, Instant to) {
        List<AuditEntry> entries = getTrail(clientId, from, to);
        log.info("Exporting PDF evidence for client={} entries={}", clientId, entries.size());
        return pdfRendererPort.render(clientId, entries, from, to);
    }

    // ─── Port ─────────────────────────────────────────────────────────────────

    public interface PdfRendererPort {
        byte[] render(String clientId, List<AuditEntry> entries, Instant from, Instant to);
    }

    // ─── Domain records ───────────────────────────────────────────────────────

    public record AuditEntry(String eventType, String clientId, String detail,
                              String actorId, Instant occurredAt) {}
}
