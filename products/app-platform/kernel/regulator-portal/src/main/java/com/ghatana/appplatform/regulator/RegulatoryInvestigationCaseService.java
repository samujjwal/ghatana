package com.ghatana.appplatform.regulator;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Lifecycle management for formal regulatory investigation cases.
 *              States: OPENED → INVESTIGATING → EVIDENCE_REVIEW → FINDING → CLOSED.
 *              SLA: 5 business days per state transition before automatic escalation.
 *              Investigations are linked to specific tenants, jurisdictions, and date ranges.
 *              Regulators can attach evidence items; operators provide formal responses.
 * @doc.layer   Regulator Portal (R-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-R01-006: Regulatory investigation case management with SLA enforcement
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS investigation_cases (
 *   case_id          TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   regulator_id     TEXT NOT NULL,
 *   subject_tenant   TEXT,
 *   jurisdiction     TEXT NOT NULL,
 *   title            TEXT NOT NULL,
 *   description      TEXT NOT NULL,
 *   date_range_from  DATE,
 *   date_range_to    DATE,
 *   status           TEXT NOT NULL DEFAULT 'OPENED',
 *   sla_deadline     TIMESTAMPTZ NOT NULL,
 *   escalated        BOOLEAN NOT NULL DEFAULT FALSE,
 *   finding          TEXT,
 *   opened_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   closed_at        TIMESTAMPTZ
 * );
 * CREATE TABLE IF NOT EXISTS investigation_evidence (
 *   evidence_id      TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   case_id          TEXT NOT NULL REFERENCES investigation_cases(case_id),
 *   submitted_by     TEXT NOT NULL,
 *   description      TEXT NOT NULL,
 *   storage_ref      TEXT,
 *   submitted_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * CREATE TABLE IF NOT EXISTS investigation_responses (
 *   response_id      TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   case_id          TEXT NOT NULL REFERENCES investigation_cases(case_id),
 *   author_id        TEXT NOT NULL,
 *   body             TEXT NOT NULL,
 *   submitted_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class RegulatoryInvestigationCaseService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface EscalationPort {
        void escalate(String caseId, String regulatorId, String reason) throws Exception;
    }

    public interface NotificationPort {
        void notifyRegulator(String regulatorId, String subject, String body) throws Exception;
        void notifyOperator(String subject, String body) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private static final int SLA_BUSINESS_DAYS = 5;

    private final javax.sql.DataSource ds;
    private final EscalationPort escalation;
    private final NotificationPort notify;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter casesOpened;
    private final Counter casesClosed;
    private final Counter slaBreaches;

    public RegulatoryInvestigationCaseService(
        javax.sql.DataSource ds,
        EscalationPort escalation,
        NotificationPort notify,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds          = ds;
        this.escalation  = escalation;
        this.notify      = notify;
        this.audit       = audit;
        this.executor    = executor;
        this.casesOpened = Counter.builder("regulator.investigation.opened").register(registry);
        this.casesClosed = Counter.builder("regulator.investigation.closed").register(registry);
        this.slaBreaches = Counter.builder("regulator.investigation.sla_breaches").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Open a new investigation case. SLA deadline = now + 5 business days. */
    public Promise<String> open(String regulatorId, String subjectTenant, String jurisdiction,
                                 String title, String description, String dateFrom, String dateTo) {
        return Promise.ofBlocking(executor, () -> {
            String caseId;
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO investigation_cases " +
                     "(regulator_id, subject_tenant, jurisdiction, title, description, date_range_from, date_range_to, sla_deadline) " +
                     "VALUES (?,?,?,?,?,?::date,?::date, NOW() + INTERVAL '7 days') RETURNING case_id"
                 )) {
                ps.setString(1, regulatorId); ps.setString(2, subjectTenant);
                ps.setString(3, jurisdiction); ps.setString(4, title);
                ps.setString(5, description); ps.setString(6, dateFrom); ps.setString(7, dateTo);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); caseId = rs.getString(1); }
            }
            notify.notifyOperator("New investigation case opened: " + title, "Case ID: " + caseId);
            audit.record(regulatorId, "INVESTIGATION_OPENED", "caseId=" + caseId + " jurisdiction=" + jurisdiction);
            casesOpened.increment();
            return caseId;
        });
    }

    /** Advance the case to the next state. Resets SLA clock. */
    public Promise<Void> advance(String caseId, String actorId) {
        return Promise.ofBlocking(executor, () -> {
            Map<String, String> transitions = Map.of(
                "OPENED", "INVESTIGATING",
                "INVESTIGATING", "EVIDENCE_REVIEW",
                "EVIDENCE_REVIEW", "FINDING"
            );
            try (Connection c = ds.getConnection()) {
                String current = getStatus(c, caseId);
                String next = transitions.get(current);
                if (next == null) throw new IllegalStateException("Cannot advance from status: " + current);
                try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE investigation_cases SET status=?, sla_deadline=NOW()+INTERVAL '7 days' WHERE case_id=?"
                )) { ps.setString(1, next); ps.setString(2, caseId); ps.executeUpdate(); }
            }
            audit.record(actorId, "INVESTIGATION_ADVANCED", "caseId=" + caseId);
            return null;
        });
    }

    /** Close with a finding summary. */
    public Promise<Void> close(String caseId, String finding, String closedBy) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE investigation_cases SET status='CLOSED', finding=?, closed_at=NOW() WHERE case_id=?"
                 )) {
                ps.setString(1, finding); ps.setString(2, caseId); ps.executeUpdate();
            }
            audit.record(closedBy, "INVESTIGATION_CLOSED", "caseId=" + caseId);
            casesClosed.increment();
            return null;
        });
    }

    /** Attach an evidence item to the case. */
    public Promise<String> addEvidence(String caseId, String submittedBy, String description, String storageRef) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO investigation_evidence (case_id, submitted_by, description, storage_ref) " +
                     "VALUES (?,?,?,?) RETURNING evidence_id"
                 )) {
                ps.setString(1, caseId); ps.setString(2, submittedBy);
                ps.setString(3, description); ps.setString(4, storageRef);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
            }
        });
    }

    /** Operator submits a formal response to the investigation. */
    public Promise<String> submitResponse(String caseId, String authorId, String body) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO investigation_responses (case_id, author_id, body) VALUES (?,?,?) RETURNING response_id"
                 )) {
                ps.setString(1, caseId); ps.setString(2, authorId); ps.setString(3, body);
                try (ResultSet rs = ps.executeQuery()) { rs.next(); String id = rs.getString(1);
                    audit.record(authorId, "INVESTIGATION_RESPONSE_SUBMITTED", "caseId=" + caseId);
                    return id;
                }
            }
        });
    }

    /** Batch SLA check — escalate any overdue open cases. Returns escalated count. */
    public Promise<Integer> checkSlaBreaches() {
        return Promise.ofBlocking(executor, () -> {
            List<String[]> overdue = new ArrayList<>();
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "SELECT case_id, regulator_id, status FROM investigation_cases " +
                     "WHERE status NOT IN ('CLOSED') AND sla_deadline < NOW() AND escalated=FALSE"
                 )) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) overdue.add(new String[]{rs.getString("case_id"), rs.getString("regulator_id"), rs.getString("status")});
                }
            }
            for (String[] row : overdue) {
                escalation.escalate(row[0], row[1], "SLA breached at status " + row[2]);
                try (Connection c = ds.getConnection();
                     PreparedStatement ps = c.prepareStatement(
                         "UPDATE investigation_cases SET escalated=TRUE WHERE case_id=?"
                     )) { ps.setString(1, row[0]); ps.executeUpdate(); }
                slaBreaches.increment();
            }
            return overdue.size();
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String getStatus(Connection c, String caseId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
            "SELECT status FROM investigation_cases WHERE case_id=?"
        )) {
            ps.setString(1, caseId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new NoSuchElementException("Case not found: " + caseId);
                return rs.getString("status");
            }
        }
    }
}
