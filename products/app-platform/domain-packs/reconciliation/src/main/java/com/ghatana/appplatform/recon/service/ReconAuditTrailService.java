package com.ghatana.appplatform.recon.service;

import io.activej.promise.Promise;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executor;
import javax.sql.DataSource;

/**
 * @doc.type    Service (Application)
 * @doc.purpose Immutable reconciliation audit trail — append-only INSERT to recon_audit_log (D13-003).
 *              Every recon event (run start, step completion, break detected, escalation, override)
 *              is logged with actor, timestamp, and detail payload.
 *              Supports regulatory evidence export via K-07 audit integration.
 * @doc.layer   Domain — Reconciliation
 * @doc.pattern Append-only write model; no UPDATE/DELETE ever; JSONB details for flexible schema
 */
public class ReconAuditTrailService {

    public record AuditEvent(
        String eventId,
        String reconRunId,
        String eventType,  // RUN_STARTED / STEP_COMPLETED / BREAK_DETECTED / ESCALATED / OVERRIDE
        String actorId,
        String details,    // JSONB string (free-form event details)
        Instant eventTime
    ) {}

    private final DataSource dataSource;
    private final Executor executor;

    public ReconAuditTrailService(DataSource dataSource, Executor executor) {
        this.dataSource = dataSource;
        this.executor = executor;
    }

    /** Append a new audit event — immutable, no update or delete ever allowed. */
    public Promise<AuditEvent> log(String reconRunId, String eventType,
                                    String actorId, String detailsJson) {
        return Promise.ofBlocking(executor, () -> {
            AuditEvent event = new AuditEvent(UUID.randomUUID().toString(), reconRunId,
                eventType, actorId, detailsJson, Instant.now());
            insertEvent(event);
            return event;
        });
    }

    /** Convenience: log recon run start. */
    public Promise<AuditEvent> logRunStarted(String reconRunId, String operatorId,
                                              String reconDate) {
        return log(reconRunId, "RUN_STARTED", operatorId,
            "{\"reconDate\":\"" + reconDate + "\",\"status\":\"RUNNING\"}");
    }

    /** Convenience: log a recon break (mismatch) detected. */
    public Promise<AuditEvent> logBreakDetected(String reconRunId, String actorId,
                                                  String clientId, String currency,
                                                  String internalAmt, String externalAmt) {
        String details = String.format(
            "{\"clientId\":\"%s\",\"currency\":\"%s\",\"internal\":\"%s\",\"external\":\"%s\"}",
            clientId, currency, internalAmt, externalAmt);
        return log(reconRunId, "BREAK_DETECTED", actorId, details);
    }

    /** Convenience: log run completion. */
    public Promise<AuditEvent> logRunCompleted(String reconRunId, String actorId,
                                                int matchCount, int breakCount) {
        String details = String.format(
            "{\"matchCount\":%d,\"breakCount\":%d,\"status\":\"COMPLETED\"}",
            matchCount, breakCount);
        return log(reconRunId, "RUN_COMPLETED", actorId, details);
    }

    private void insertEvent(AuditEvent event) throws Exception {
        String sql = "INSERT INTO recon_audit_log(id, run_id, event_time, event_type, actor_id, details) " +
                     "VALUES(?,?,NOW(),?,?,?::jsonb)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, UUID.fromString(event.eventId()));
            ps.setObject(2, UUID.fromString(event.reconRunId()));
            ps.setString(3, event.eventType());
            ps.setObject(4, event.actorId() != null ? UUID.fromString(event.actorId()) : null);
            ps.setString(5, event.details());
            ps.executeUpdate();
        }
    }
}
