package com.ghatana.appplatform.certification;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Human reviewer checklist for plugin certification tiers.
 *              T3 plugins (highest risk) go through all checklist items;
 *              T2 and T1 cover applicable subsets.
 *              Each checklist item is independently signed off by an assigned reviewer.
 *              Any FAILED item blocks certification and requires re-submission after fix.
 *              Five canonical verification domains: DATA_HANDLING, AUDIT_LOGGING,
 *              ERROR_HANDLING, DOCUMENTATION, REGULATORY_COMPLIANCE.
 * @doc.layer   Pack Certification (P-01)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-P01-009: Human reviewer compliance checklist per certification tier
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS certification_checklists (
 *   checklist_id  TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   plugin_id     TEXT NOT NULL,
 *   version       TEXT NOT NULL,
 *   tier          TEXT NOT NULL,   -- T1 | T2 | T3
 *   assigned_to   TEXT NOT NULL,
 *   status        TEXT NOT NULL DEFAULT 'IN_PROGRESS',  -- IN_PROGRESS | PASSED | FAILED | RESUBMITTED
 *   created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
 *   completed_at  TIMESTAMPTZ,
 *   UNIQUE (plugin_id, version)
 * );
 * CREATE TABLE IF NOT EXISTS checklist_items (
 *   item_id       TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   checklist_id  TEXT NOT NULL REFERENCES certification_checklists(checklist_id),
 *   domain        TEXT NOT NULL,   -- DATA_HANDLING | AUDIT_LOGGING | ERROR_HANDLING | DOCUMENTATION | REGULATORY_COMPLIANCE
 *   description   TEXT NOT NULL,
 *   outcome       TEXT,            -- PASS | FAIL | NULL (pending)
 *   note          TEXT,
 *   reviewed_by   TEXT,
 *   reviewed_at   TIMESTAMPTZ
 * );
 * </pre>
 */
public class ComplianceVerificationChecklistService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Canonical checklist items per tier ────────────────────────────────────

    private static final Map<String, List<ChecklistTemplate>> TIER_TEMPLATES;

    static {
        TIER_TEMPLATES = new HashMap<>();

        List<ChecklistTemplate> t1 = List.of(
            new ChecklistTemplate("DATA_HANDLING",        "No raw PII stored or transmitted without encryption"),
            new ChecklistTemplate("AUDIT_LOGGING",        "Integration with K-07 audit SDK confirmed"),
            new ChecklistTemplate("DOCUMENTATION",        "Plugin manifest and API contract docs present")
        );

        List<ChecklistTemplate> t2 = List.of(
            new ChecklistTemplate("DATA_HANDLING",        "No raw PII stored or transmitted without encryption"),
            new ChecklistTemplate("DATA_HANDLING",        "Data retention policy declared in plugin manifest"),
            new ChecklistTemplate("AUDIT_LOGGING",        "Integration with K-07 audit SDK confirmed"),
            new ChecklistTemplate("ERROR_HANDLING",       "Graceful degradation on downstream unavailability"),
            new ChecklistTemplate("DOCUMENTATION",        "Plugin manifest and API contract docs present"),
            new ChecklistTemplate("REGULATORY_COMPLIANCE","Jurisdictions and applicable regulations declared")
        );

        List<ChecklistTemplate> t3 = List.of(
            new ChecklistTemplate("DATA_HANDLING",        "No raw PII stored or transmitted without encryption"),
            new ChecklistTemplate("DATA_HANDLING",        "Data retention policy declared in plugin manifest"),
            new ChecklistTemplate("DATA_HANDLING",        "Third-party data sharing explicitly listed"),
            new ChecklistTemplate("AUDIT_LOGGING",        "Integration with K-07 audit SDK confirmed"),
            new ChecklistTemplate("AUDIT_LOGGING",        "Audit events include correlation-id and idempotency-key"),
            new ChecklistTemplate("ERROR_HANDLING",       "Graceful degradation on downstream unavailability"),
            new ChecklistTemplate("ERROR_HANDLING",       "Circuit-breaker pattern implemented"),
            new ChecklistTemplate("DOCUMENTATION",        "Plugin manifest and API contract docs present"),
            new ChecklistTemplate("DOCUMENTATION",        "Migration notes provided for breaking-change versions"),
            new ChecklistTemplate("REGULATORY_COMPLIANCE","Jurisdictions and applicable regulations declared"),
            new ChecklistTemplate("REGULATORY_COMPLIANCE","Regulator access hooks approved by legal")
        );

        TIER_TEMPLATES.put("T1", t1);
        TIER_TEMPLATES.put("T2", t2);
        TIER_TEMPLATES.put("T3", t3);
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter checklistsPassed;
    private final Counter checklistsFailed;

    public ComplianceVerificationChecklistService(
        javax.sql.DataSource ds,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds               = ds;
        this.audit            = audit;
        this.executor         = executor;
        this.checklistsPassed = Counter.builder("certification.checklist.passed").register(registry);
        this.checklistsFailed = Counter.builder("certification.checklist.failed").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Create a new checklist for a plugin+version with tier-appropriate items. */
    public Promise<String> create(String pluginId, String version, String tier, String assignedTo, String requestedBy) {
        return Promise.ofBlocking(executor, () -> {
            List<ChecklistTemplate> templates = TIER_TEMPLATES.getOrDefault(tier.toUpperCase(), List.of());
            try (Connection c = ds.getConnection()) {
                c.setAutoCommit(false);
                String checklistId;
                try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO certification_checklists (plugin_id, version, tier, assigned_to) " +
                    "VALUES (?,?,?,?) RETURNING checklist_id"
                )) {
                    ps.setString(1, pluginId); ps.setString(2, version);
                    ps.setString(3, tier.toUpperCase()); ps.setString(4, assignedTo);
                    try (ResultSet rs = ps.executeQuery()) { rs.next(); checklistId = rs.getString(1); }
                }
                for (ChecklistTemplate t : templates) {
                    try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO checklist_items (checklist_id, domain, description) VALUES (?,?,?)"
                    )) {
                        ps.setString(1, checklistId); ps.setString(2, t.domain()); ps.setString(3, t.description());
                        ps.executeUpdate();
                    }
                }
                c.commit();
                audit.record(requestedBy, "CHECKLIST_CREATED", "checklistId=" + checklistId + " tier=" + tier);
                return checklistId;
            }
        });
    }

    /** Reviewer signs off on a single checklist item with outcome PASS or FAIL + optional note. */
    public Promise<Void> reviewItem(String itemId, String outcome, String note, String reviewerId) {
        return Promise.ofBlocking(executor, () -> {
            if (!outcome.equals("PASS") && !outcome.equals("FAIL"))
                throw new IllegalArgumentException("outcome must be PASS or FAIL");
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE checklist_items SET outcome=?, note=?, reviewed_by=?, reviewed_at=NOW() WHERE item_id=?"
                 )) {
                ps.setString(1, outcome); ps.setString(2, note);
                ps.setString(3, reviewerId); ps.setString(4, itemId);
                ps.executeUpdate();
            }
            audit.record(reviewerId, "CHECKLIST_ITEM_REVIEWED", "itemId=" + itemId + " outcome=" + outcome);
            return null;
        });
    }

    /**
     * Compute the overall checklist outcome once all items have been reviewed.
     * If any item FAILs → checklist moves to FAILED.
     * If all PASS → checklist moves to PASSED.
     * Items not yet reviewed block finalisation.
     */
    public Promise<String> finalise(String checklistId, String finalisedBy) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection()) {
                int pending = 0, failed = 0;
                try (PreparedStatement ps = c.prepareStatement(
                    "SELECT outcome FROM checklist_items WHERE checklist_id=?"
                )) {
                    ps.setString(1, checklistId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String o = rs.getString("outcome");
                            if (o == null) pending++;
                            else if ("FAIL".equals(o)) failed++;
                        }
                    }
                }
                if (pending > 0) throw new IllegalStateException("Checklist has " + pending + " unreviewed items");
                String finalStatus = failed > 0 ? "FAILED" : "PASSED";
                try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE certification_checklists SET status=?, completed_at=NOW() WHERE checklist_id=?"
                )) {
                    ps.setString(1, finalStatus); ps.setString(2, checklistId); ps.executeUpdate();
                }
                if ("PASSED".equals(finalStatus)) checklistsPassed.increment();
                else checklistsFailed.increment();
                audit.record(finalisedBy, "CHECKLIST_FINALISED", "checklistId=" + checklistId + " status=" + finalStatus);
                return finalStatus;
            }
        });
    }

    /** Mark the checklist as RESUBMITTED after the developer has addressed FAIL items. */
    public Promise<Void> markResubmitted(String checklistId, String requestedBy) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection()) {
                // Reset all FAIL items to pending for re-review
                try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE checklist_items SET outcome=NULL, note=NULL, reviewed_by=NULL, reviewed_at=NULL " +
                    "WHERE checklist_id=? AND outcome='FAIL'"
                )) { ps.setString(1, checklistId); ps.executeUpdate(); }
                try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE certification_checklists SET status='RESUBMITTED', completed_at=NULL WHERE checklist_id=?"
                )) { ps.setString(1, checklistId); ps.executeUpdate(); }
            }
            audit.record(requestedBy, "CHECKLIST_RESUBMITTED", "checklistId=" + checklistId);
            return null;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private record ChecklistTemplate(String domain, String description) {}
}
