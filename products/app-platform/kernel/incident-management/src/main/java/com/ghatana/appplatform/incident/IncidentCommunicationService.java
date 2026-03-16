package com.ghatana.appplatform.incident;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    Service
 * @doc.purpose Manage incident communication templates and dispatch workflow.
 *              Template types: INITIAL_NOTIFICATION, PROGRESS_UPDATE, RESOLUTION.
 *              Audience segmentation: INTERNAL (operator), EXTERNAL (affected tenants).
 *              Draft → REVIEWED → SENT workflow; prevents unsanctioned external comms.
 *              P1/P2 auto-dispatch initial notification on incident creation.
 *              Full communication log maintained per incident.
 * @doc.layer   Incident Management (R-02)
 * @doc.pattern Port-Adapter; HikariCP + JDBC; Promise.ofBlocking; Micrometer
 *
 * STORY-R02-003: Incident communication templates
 *
 * DDL:
 * <pre>
 * CREATE TABLE IF NOT EXISTS incident_comm_templates (
 *   template_id    TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   template_type  TEXT NOT NULL UNIQUE,   -- INITIAL_NOTIFICATION | PROGRESS_UPDATE | RESOLUTION
 *   subject_tmpl   TEXT NOT NULL,
 *   body_tmpl      TEXT NOT NULL,
 *   audience       TEXT NOT NULL DEFAULT 'EXTERNAL',  -- INTERNAL | EXTERNAL | BOTH
 *   updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * CREATE TABLE IF NOT EXISTS incident_communications (
 *   comm_id        TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
 *   incident_id    TEXT NOT NULL,
 *   template_type  TEXT NOT NULL,
 *   audience       TEXT NOT NULL,
 *   subject        TEXT NOT NULL,
 *   body           TEXT NOT NULL,
 *   status         TEXT NOT NULL DEFAULT 'DRAFT',    -- DRAFT | REVIEWED | SENT
 *   drafted_by     TEXT NOT NULL,
 *   reviewed_by    TEXT,
 *   sent_at        TIMESTAMPTZ,
 *   channel        TEXT NOT NULL DEFAULT 'EMAIL',    -- EMAIL | SLACK | WEBHOOK
 *   created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
 * );
 * </pre>
 */
public class IncidentCommunicationService {

    // ── Inner port interfaces ─────────────────────────────────────────────────

    public interface DispatchPort {
        void sendEmail(List<String> recipients, String subject, String body) throws Exception;
        void sendSlack(String channel, String message) throws Exception;
        void sendWebhook(String url, String payload) throws Exception;
    }

    public interface AudienceResolverPort {
        /** Internal recipients for a severity (e.g. ops team). */
        List<String> internalRecipients(String severity) throws Exception;
        /** External recipients: affected tenant contacts. */
        List<String> externalRecipients(List<String> tenantIds) throws Exception;
    }

    public interface AuditPort {
        void record(String actorId, String action, String detail) throws Exception;
    }

    // ── Value types ───────────────────────────────────────────────────────────

    public record Communication(
        String commId, String incidentId, String templateType,
        String audience, String subject, String body,
        String status, String draftedBy
    ) {}

    // ── Fields ────────────────────────────────────────────────────────────────

    private final javax.sql.DataSource ds;
    private final DispatchPort dispatch;
    private final AudienceResolverPort audienceResolver;
    private final AuditPort audit;
    private final Executor executor;
    private final Counter dispatchedCounter;

    public IncidentCommunicationService(
        javax.sql.DataSource ds,
        DispatchPort dispatch,
        AudienceResolverPort audienceResolver,
        AuditPort audit,
        MeterRegistry registry,
        Executor executor
    ) {
        this.ds               = ds;
        this.dispatch         = dispatch;
        this.audienceResolver = audienceResolver;
        this.audit            = audit;
        this.executor         = executor;
        this.dispatchedCounter = Counter.builder("incident.communications.sent").register(registry);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Auto-dispatch initial notification. Called when P1 or P2 incident is detected.
     */
    public Promise<Void> autoNotify(String incidentId, String severity, String title, List<String> affectedTenantIds) {
        return Promise.ofBlocking(executor, () -> {
            TemplateData tmpl = loadTemplate("INITIAL_NOTIFICATION");
            String subject = interpolate(tmpl.subjectTmpl(), Map.of("title", title, "severity", severity));
            String body    = interpolate(tmpl.bodyTmpl(),    Map.of("title", title, "severity", severity, "incidentId", incidentId));

            // Internal dispatch
            List<String> internalRecips = audienceResolver.internalRecipients(severity);
            if (!internalRecips.isEmpty()) {
                dispatch.sendEmail(internalRecips, "[INTERNAL] " + subject, body);
                persistComm(incidentId, "INITIAL_NOTIFICATION", "INTERNAL", subject, body, "system", "SENT");
            }

            // External dispatch (affected tenants)
            if (!affectedTenantIds.isEmpty()) {
                List<String> externalRecips = audienceResolver.externalRecipients(affectedTenantIds);
                if (!externalRecips.isEmpty()) {
                    dispatch.sendEmail(externalRecips, subject, body);
                    persistComm(incidentId, "INITIAL_NOTIFICATION", "EXTERNAL", subject, body, "system", "SENT");
                }
            }
            dispatchedCounter.increment();
            audit.record("system", "INCIDENT_AUTO_NOTIFY", "incidentId=" + incidentId + " severity=" + severity);
            return null;
        });
    }

    /**
     * Draft a communication for an incident. Requires review before send.
     */
    public Promise<Communication> draft(
        String incidentId, String templateType, String audience, String draftedBy
    ) {
        return Promise.ofBlocking(executor, () -> {
            TemplateData tmpl = loadTemplate(templateType);
            String commId = persistComm(incidentId, templateType, audience,
                tmpl.subjectTmpl(), tmpl.bodyTmpl(), draftedBy, "DRAFT");
            audit.record(draftedBy, "COMM_DRAFTED", "incidentId=" + incidentId + " type=" + templateType);
            return new Communication(commId, incidentId, templateType, audience,
                tmpl.subjectTmpl(), tmpl.bodyTmpl(), "DRAFT", draftedBy);
        });
    }

    /**
     * Reviewer approves a draft communication (transitions DRAFT → REVIEWED).
     */
    public Promise<Void> review(String commId, String reviewedBy) {
        return Promise.ofBlocking(executor, () -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE incident_communications SET status='REVIEWED', reviewed_by=? WHERE comm_id=? AND status='DRAFT'"
                 )) {
                ps.setString(1, reviewedBy); ps.setString(2, commId); ps.executeUpdate();
            }
            audit.record(reviewedBy, "COMM_REVIEWED", "commId=" + commId);
            return null;
        });
    }

    /**
     * Send a reviewed communication. Transitions REVIEWED → SENT.
     */
    public Promise<Void> send(String commId, String sentBy) {
        return Promise.ofBlocking(executor, () -> {
            Communication comm = loadComm(commId);
            if (!"REVIEWED".equals(comm.status())) {
                throw new IllegalStateException("Communication must be in REVIEWED status to send");
            }
            List<String> recipients = "INTERNAL".equals(comm.audience())
                ? audienceResolver.internalRecipients("P2")
                : audienceResolver.externalRecipients(List.of("all"));
            dispatch.sendEmail(recipients, comm.subject(), comm.body());

            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                     "UPDATE incident_communications SET status='SENT', sent_at=NOW() WHERE comm_id=?"
                 )) {
                ps.setString(1, commId); ps.executeUpdate();
            }
            dispatchedCounter.increment();
            audit.record(sentBy, "COMM_SENT", "commId=" + commId);
            return null;
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private record TemplateData(String subjectTmpl, String bodyTmpl) {}

    private TemplateData loadTemplate(String templateType) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT subject_tmpl, body_tmpl FROM incident_comm_templates WHERE template_type=?"
             )) {
            ps.setString(1, templateType);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    // Return a sensible default if no custom template configured
                    return new TemplateData(
                        "[{{severity}}] Platform Incident: {{title}}",
                        "Incident ID: {{incidentId}}\nTitle: {{title}}\nSeverity: {{severity}}\nWe are currently investigating this issue."
                    );
                }
                return new TemplateData(rs.getString("subject_tmpl"), rs.getString("body_tmpl"));
            }
        }
    }

    private String interpolate(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            result = result.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return result;
    }

    private String persistComm(String incidentId, String templateType, String audience,
                                 String subject, String body, String draftedBy, String status) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO incident_communications (incident_id, template_type, audience, subject, body, drafted_by, status) " +
                 "VALUES (?,?,?,?,?,?,?) RETURNING comm_id"
             )) {
            ps.setString(1, incidentId); ps.setString(2, templateType); ps.setString(3, audience);
            ps.setString(4, subject); ps.setString(5, body);
            ps.setString(6, draftedBy); ps.setString(7, status);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString("comm_id"); }
        }
    }

    private Communication loadComm(String commId) throws SQLException {
        try (Connection c = ds.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT comm_id, incident_id, template_type, audience, subject, body, status, drafted_by " +
                 "FROM incident_communications WHERE comm_id=?"
             )) {
            ps.setString(1, commId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("Communication not found: " + commId);
                return new Communication(rs.getString("comm_id"), rs.getString("incident_id"),
                    rs.getString("template_type"), rs.getString("audience"),
                    rs.getString("subject"), rs.getString("body"),
                    rs.getString("status"), rs.getString("drafted_by"));
            }
        }
    }
}
