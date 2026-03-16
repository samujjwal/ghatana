package com.ghatana.appplatform.regulator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Formal Q&amp;A workflow between regulator and operator.
 *              Each query has a response deadline; overdue queries auto-escalate.
 *              Supports threaded follow-up questions.
 *              States: SUBMITTED → ACKNOWLEDGED → ANSWERED → FOLLOW_UP → CLOSED.
 * @doc.layer   Regulator Portal (R-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-R01-007: Regulatory formal query-response workflow with deadline tracking
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS regulatory_queries (
 *   query_id        TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   regulator_id    TEXT NOT NULL,
 *   jurisdiction    TEXT NOT NULL,
 *   subject         TEXT NOT NULL,
 *   body            TEXT NOT NULL,
 *   priority        TEXT NOT NULL DEFAULT 'NORMAL',  -- NORMAL | HIGH | URGENT
 *   status          TEXT NOT NULL DEFAULT 'SUBMITTED',
 *   response_deadline TIMESTAMPTZ NOT NULL,
 *   escalated       BOOLEAN NOT NULL DEFAULT FALSE,
 *   submitted_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   closed_at       TIMESTAMPTZ
 * );
 * CREATE TABLE IF NOT EXISTS regulatory_query_messages (
 *   message_id      TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   query_id        TEXT NOT NULL REFERENCES regulatory_queries(query_id),
 *   author_role     TEXT NOT NULL,   -- REGULATOR | OPERATOR
 *   author_id       TEXT NOT NULL,
 *   body            TEXT NOT NULL,
 *   sent_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class RegulatoryQueryResponseWorkflowService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface NotificationPort {
        void notifyOperator(String subject, String body) throws Exception;
        void notifyRegulator(String regulatorId, String subject, String body) throws Exception;
    }

    public interface EscalationPort {
        void escalate(String queryId, String regulatorId, String reason) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Priority → SLA hours mapping ─────────────────────────────────────────

    private static final Map<String, Integer> DEADLINE_HOURS = Map.of(
        "URGENT", 24,
        "HIGH",   72,
        "NORMAL", 120   // 5 business days ≈ 120h
    );

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final NotificationPort notify;
    private final EscalationPort escalation;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter queriesSubmitted;
    private final Counter deadlineBreaches;

    public RegulatoryQueryResponseWorkflowService(
        javax.sql.DataSource ds,
        NotificationPort notify,
        EscalationPort escalation,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds               = ds;
        this.notify           = notify;
        this.escalation       = escalation;
        this.audit            = audit;
        this.executor         = executor;
        this.queriesSubmitted = Counter.builder("regulator.query.submitted").register(registry);
        this.deadlineBreaches = Counter.builder("regulator.query.deadline_breaches").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Regulator submits a formal query. Returns queryId. */
    public Promise<String> submit(String regulatorId, String jurisdiction, String subject,
                                   String body, String priority) {
        return Promise.ofBlocking(executor, () -> {
            int hours = DEADLINE_HOURS.getOrDefault(priority.toUpperCase(), 120);
            String queryId;
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO regulatory_queries (regulator_id, jurisdiction, subject, body, priority, response_deadline) " +
                     "VALUES (?,?,?,?,?, NOW() + (? || ' hours')::INTERVAL) RETURNING query_id"
                 )) {
                ps.setString(1, regulatorId); ps.setString(2, jurisdiction);
                ps.setString(3, subject);     ps.setString(4, body);
                ps.setString(5, priority.toUpperCase()); ps.setInt(6, hours);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); queryId = rs.getString(1); }
            }
            appendMessage(queryId, "REGULATOR", regulatorId, body);
            notify.notifyOperator("Regulatory query received [" + priority + "]: " + subject, "Query ID: " + queryId);
            audit.record(regulatorId, "REGULATORY_QUERY_SUBMITTED", "queryId=" + queryId + " priority=" + priority);
            queriesSubmitted.increment();
            return queryId;
        });
    }

    /** Operator acknowledges receipt. */
    public Promise<Void> acknowledge(String queryId, String operatorId) {
        return Promise.ofBlocking(executor, () -> {
            transition(queryId, "SUBMITTED", "ACKNOWLEDGED");
            audit.record(operatorId, "QUERY_ACKNOWLEDGED", "queryId=" + queryId);
            return null;
        });
    }

    /** Operator submits an answer. */
    public Promise<Void> answer(String queryId, String operatorId, String answerBody) {
        return Promise.ofBlocking(executor, () -> {
            appendMessage(queryId, "OPERATOR", operatorId, answerBody);
            transition(queryId, "ACKNOWLEDGED", "ANSWERED");
            String regulatorId = getRegulatorId(queryId);
            notify.notifyRegulator(regulatorId, "Query answered", "Query ID: " + queryId);
            audit.record(operatorId, "QUERY_ANSWERED", "queryId=" + queryId);
            return null;
        });
    }

    /** Regulator raises a follow-up question. */
    public Promise<Void> followUp(String queryId, String regulatorId, String followUpBody) {
        return Promise.ofBlocking(executor, () -> {
            appendMessage(queryId, "REGULATOR", regulatorId, followUpBody);
            // Reset deadline and move state
            int hours = 120;
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE regulatory_queries SET status='FOLLOW_UP', response_deadline=NOW()+(? || ' hours')::INTERVAL WHERE query_id=?"
                 )) { ps.setInt(1, hours); ps.setString(2, queryId); ps.executeUpdate(); }
            notify.notifyOperator("Regulatory follow-up on query " + queryId, followUpBody);
            audit.record(regulatorId, "QUERY_FOLLOW_UP", "queryId=" + queryId);
            return null;
        });
    }

    /** Close the query thread. */
    public Promise<Void> close(String queryId, String closedBy) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE regulatory_queries SET status='CLOSED', closed_at=NOW() WHERE query_id=?"
                 )) { ps.setString(1, queryId); ps.executeUpdate(); }
            audit.record(closedBy, "QUERY_CLOSED", "queryId=" + queryId);
            return null;
        });
    }

    /** Batch SLA check — escalate overdue open queries. */
    public Promise<Integer> checkDeadlines() {
        return Promise.ofBlocking(executor, () -> {
            List<String[]> overdue = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT query_id, regulator_id FROM regulatory_queries " +
                     "WHERE status NOT IN ('CLOSED') AND response_deadline < NOW() AND escalated=FALSE"
                 )) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) overdue.add(new String[]{rs.getString("query_id"), rs.getString("regulator_id")});
                }
            }
            for (String[] row : overdue) {
                escalation.escalate(row[0], row[1], "Response deadline breached");
                try (Connection c = ds.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                         "UPDATE regulatory_queries SET escalated=TRUE WHERE query_id=?"
                     )) { ps.setString(1, row[0]); ps.executeUpdate(); }
                deadlineBreaches.increment();
            }
            return overdue.size();
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void appendMessage(String queryId, String role, String authorId, String body) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO regulatory_query_messages (query_id, author_role, author_id, body) VALUES (?,?,?,?)"
             )) {
            ps.setString(1, queryId); ps.setString(2, role);
            ps.setString(3, authorId); ps.setString(4, body); ps.executeUpdate();
        }
    }

    private void transition(String queryId, String expectedFrom, String to) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE regulatory_queries SET status=? WHERE query_id=? AND status=?"
             )) {
            ps.setString(1, to); ps.setString(2, queryId); ps.setString(3, expectedFrom);
            if (ps.executeUpdate() == 0) throw new IllegalStateException("Cannot transition: expected " + expectedFrom);
        }
    }

    private String getRegulatorId(String queryId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT regulator_id FROM regulatory_queries WHERE query_id=?"
             )) {
            ps.setString(1, queryId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getString(1) : ""; }
        }
    }
}
