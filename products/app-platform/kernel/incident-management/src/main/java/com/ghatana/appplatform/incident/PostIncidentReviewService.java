package com.ghatana.appplatform.incident;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Post-Incident Review (PIR) lifecycle for P1 and P2 severity incidents.
 *              PIRs are mandatory for P1/P2 and must be completed within 72 hours.
 *              States: DRAFT → REVIEWED → APPROVED → PUBLISHED.
 *              Built-in 5-whys template stored as JSONB.
 *              Published PIRs are visible to all tenants affected by the incident.
 * @doc.layer   Incident Management (R-02)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-R02-005: Post-incident review lifecycle with 5-whys template
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS post_incident_reviews (
 *   pir_id           TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   incident_id      TEXT NOT NULL UNIQUE,
 *   severity         TEXT NOT NULL,   -- P1 | P2
 *   summary          TEXT,
 *   timeline         TEXT,
 *   impact           TEXT,
 *   root_cause       TEXT,
 *   five_whys        JSONB,           -- array of {why, because}
 *   action_items     JSONB,           -- array of action item refs
 *   status           TEXT NOT NULL DEFAULT 'DRAFT',
 *   author_id        TEXT NOT NULL,
 *   reviewer_id      TEXT,
 *   approver_id      TEXT,
 *   deadline_at      TIMESTAMPTZ NOT NULL,
 *   draft_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   reviewed_at      TIMESTAMPTZ,
 *   approved_at      TIMESTAMPTZ,
 *   published_at     TIMESTAMPTZ
 * );
 * </pre>
 */
public class PostIncidentReviewService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface NotificationPort {
        void notifyReviewer(String reviewerId, String pirId, String incidentId) throws Exception;
        void notifyAffectedTenants(String incidentId, String pirId) throws Exception;
        void notifySlaWarning(String authorId, String pirId, long hoursRemaining) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private static final int PIR_SLA_HOURS = 72;

    private final javax.sql.DataSource ds;
    private final NotificationPort notify;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter pirsPublished;
    private final Counter slaMisses;

    public PostIncidentReviewService(
        javax.sql.DataSource ds,
        NotificationPort notify,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds            = ds;
        this.notify        = notify;
        this.audit         = audit;
        this.executor      = executor;
        this.pirsPublished = Counter.builder("incident.pir.published").register(registry);
        this.slaMisses     = Counter.builder("incident.pir.sla_misses").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Create a PIR draft for an incident. Deadline = now + 72h. Returns pirId. */
    public Promise<String> create(String incidentId, String severity, String authorId) {
        return Promise.ofBlocking(executor, () -> {
            String pirId;
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO post_incident_reviews (incident_id, severity, author_id, deadline_at, five_whys) " +
                     "VALUES (?,?,?, NOW() + INTERVAL '72 hours', '[]'::jsonb) RETURNING pir_id"
                 )) {
                ps.setString(1, incidentId); ps.setString(2, severity); ps.setString(3, authorId);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); pirId = rs.getString(1); }
            }
            audit.record(authorId, "PIR_CREATED", "pirId=" + pirId + " incidentId=" + incidentId + " severity=" + severity);
            return pirId;
        });
    }

    /** Update draft content including 5-whys analysis. */
    public Promise<Void> updateDraft(String pirId, String summary, String timeline,
                                      String impact, String rootCause, String fiveWhysJson, String authorId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE post_incident_reviews SET summary=?, timeline=?, impact=?, root_cause=?, five_whys=?::jsonb " +
                     "WHERE pir_id=? AND status='DRAFT'"
                 )) {
                ps.setString(1, summary); ps.setString(2, timeline); ps.setString(3, impact);
                ps.setString(4, rootCause); ps.setString(5, fiveWhysJson); ps.setString(6, pirId);
                if (ps.executeUpdate() == 0) throw new IllegalStateException("PIR not in DRAFT state or not found");
            }
            return null;
        });
    }

    /** Submit draft for review by a designated reviewer. */
    public Promise<Void> submitForReview(String pirId, String reviewerId, String authorId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE post_incident_reviews SET status='REVIEWED', reviewer_id=?, reviewed_at=NOW() WHERE pir_id=? AND status='DRAFT'"
                 )) { ps.setString(1, reviewerId); ps.setString(2, pirId);
                    if (ps.executeUpdate() == 0) throw new IllegalStateException("Cannot submit: PIR not in DRAFT");
                 }
            notify.notifyReviewer(reviewerId, pirId, getPirIncidentId(pirId));
            audit.record(authorId, "PIR_SUBMITTED_FOR_REVIEW", "pirId=" + pirId);
            return null;
        });
    }

    /** Reviewer approves the PIR. */
    public Promise<Void> approve(String pirId, String approverId) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE post_incident_reviews SET status='APPROVED', approver_id=?, approved_at=NOW() WHERE pir_id=? AND status='REVIEWED'"
                 )) { ps.setString(1, approverId); ps.setString(2, pirId);
                    if (ps.executeUpdate() == 0) throw new IllegalStateException("Cannot approve: PIR not in REVIEWED");
                 }
            audit.record(approverId, "PIR_APPROVED", "pirId=" + pirId);
            return null;
        });
    }

    /** Publish to affected tenants. */
    public Promise<Void> publish(String pirId, String publishedBy) {
        return Promise.ofBlocking(executor, () -> {
            String incidentId;
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE post_incident_reviews SET status='PUBLISHED', published_at=NOW() WHERE pir_id=? AND status='APPROVED' RETURNING incident_id"
                 )) { ps.setString(1, pirId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalStateException("Cannot publish: PIR not in APPROVED");
                        incidentId = rs.getString("incident_id");
                    }
                 }
            notify.notifyAffectedTenants(incidentId, pirId);
            audit.record(publishedBy, "PIR_PUBLISHED", "pirId=" + pirId);
            pirsPublished.increment();
            return null;
        });
    }

    /** Check for PIRs approaching or past the 72h deadline. */
    public Promise<Integer> checkDeadlines() {
        return Promise.ofBlocking(executor, () -> {
            int breached = 0;
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT pir_id, author_id, deadline_at FROM post_incident_reviews " +
                     "WHERE status IN ('DRAFT','REVIEWED') AND deadline_at < NOW() + INTERVAL '12 hours'"
                 )) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Timestamp dl = rs.getTimestamp("deadline_at");
                        long hoursLeft = (dl.getTime() - System.currentTimeMillis()) / 3_600_000L;
                        if (hoursLeft < 0) slaMisses.increment();
                        notify.notifySlaWarning(rs.getString("author_id"), rs.getString("pir_id"),
                            Math.max(0, hoursLeft));
                        breached++;
                    }
                }
            }
            return breached;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getPirIncidentId(String pirId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT incident_id FROM post_incident_reviews WHERE pir_id=?"
             )) {
            ps.setString(1, pirId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : ""; }
        }
    }
}
