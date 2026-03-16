package com.ghatana.appplatform.dlq;

import com.zaxxer.hikari.HikariDataSource;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;

/**
 * @doc.type    DomainService
 * @doc.purpose Managed workflow for discarding dead-letter messages that cannot be replayed
 *              or must be intentionally dropped. Lifecycle: DISCARD_REQUESTED →
 *              AWAITING_APPROVAL → APPROVED | REJECTED → DISCARDED. Requires maker-checker
 *              approval via K-01 WorkflowPort for messages above a configurable payload-size
 *              threshold or critical topics. Records a discard justification and immutable
 *              audit log entry. Satisfies STORY-K19-009.
 * @doc.layer   Kernel
 * @doc.pattern Maker-checker discard approval (K-01 WorkflowPort); discard state machine;
 *              K-07 audit trail; discardRequested/discarded/rejected Counters.
 */
public class DeadLetterDiscardWorkflowService {

    private final HikariDataSource  dataSource;
    private final Executor          executor;
    private final ApprovalPort      approvalPort;
    private final AuditPort         auditPort;
    private final Counter           discardRequestedCounter;
    private final Counter           discardedCounter;
    private final Counter           rejectedCounter;

    public DeadLetterDiscardWorkflowService(HikariDataSource dataSource, Executor executor,
                                             ApprovalPort approvalPort,
                                             AuditPort auditPort,
                                             MeterRegistry registry) {
        this.dataSource              = dataSource;
        this.executor                = executor;
        this.approvalPort            = approvalPort;
        this.auditPort               = auditPort;
        this.discardRequestedCounter = Counter.builder("dlq.discard.requested_total").register(registry);
        this.discardedCounter        = Counter.builder("dlq.discard.discarded_total").register(registry);
        this.rejectedCounter         = Counter.builder("dlq.discard.rejected_total").register(registry);
    }

    // ─── Inner ports ─────────────────────────────────────────────────────────

    /** K-01 maker-checker approval for discard decisions on critical messages. */
    public interface ApprovalPort {
        boolean requiresApproval(String topicName, long payloadSizeBytes);
        String requestApproval(String resourceId, String resourceType, String reason, String requestedBy);
        boolean isApproved(String approvalRequestId);
    }

    /** K-07 audit trail. */
    public interface AuditPort {
        void log(String action, String resourceType, String resourceId, Map<String, Object> details);
    }

    // ─── Domain records ──────────────────────────────────────────────────────

    public enum DiscardStatus { DISCARD_REQUESTED, AWAITING_APPROVAL, APPROVED, REJECTED, DISCARDED }

    public record DiscardRequest(
        String requestId, String deadLetterId,
        DiscardStatus status, String justification,
        String requestedBy, String approvalId,
        Instant requestedAt, Instant resolvedAt
    ) {}

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Submit a discard request. If the message requires maker-checker approval (critical topic
     * or large payload), transitions to AWAITING_APPROVAL and requests approval via K-01.
     * Otherwise transitions directly to APPROVED and executes immediately.
     */
    public Promise<DiscardRequest> requestDiscard(String deadLetterId, String justification,
                                                   String requestedBy) {
        return Promise.ofBlocking(executor, () -> {
            String requestId    = UUID.randomUUID().toString();
            Instant now         = Instant.now();
            String topicName    = fetchTopic(deadLetterId);
            long payloadSize    = fetchPayloadSize(deadLetterId);

            discardRequestedCounter.increment();

            if (approvalPort.requiresApproval(topicName, payloadSize)) {
                String approvalId = approvalPort.requestApproval(
                    deadLetterId, "DeadLetter", justification, requestedBy);

                persistRequest(requestId, deadLetterId, DiscardStatus.AWAITING_APPROVAL,
                    justification, requestedBy, approvalId, now);

                auditPort.log("DISCARD_REQUEST_AWAITING_APPROVAL", "DeadLetter", deadLetterId,
                    Map.of("requestId", requestId, "approvalId", approvalId, "requestedBy", requestedBy));

                return new DiscardRequest(requestId, deadLetterId, DiscardStatus.AWAITING_APPROVAL,
                    justification, requestedBy, approvalId, now, null);
            }

            // Auto-approve for non-critical messages
            persistRequest(requestId, deadLetterId, DiscardStatus.APPROVED,
                justification, requestedBy, null, now);
            executeDiscard(deadLetterId, requestId, requestedBy);

            return new DiscardRequest(requestId, deadLetterId, DiscardStatus.DISCARDED,
                justification, requestedBy, null, now, Instant.now());
        });
    }

    /**
     * Poll for approval and execute discard if approved.
     * Called by scheduler for AWAITING_APPROVAL requests.
     */
    public Promise<DiscardRequest> checkAndExecute(String requestId) {
        return Promise.ofBlocking(executor, () -> {
            DiscardRequest request = fetchRequest(requestId);
            if (request == null) throw new IllegalArgumentException("Request not found: " + requestId);
            if (request.status() != DiscardStatus.AWAITING_APPROVAL) return request;

            if (approvalPort.isApproved(request.approvalId())) {
                executeDiscard(request.deadLetterId(), requestId, request.requestedBy());
                updateStatus(requestId, DiscardStatus.DISCARDED, Instant.now());
                discardedCounter.increment();
                return fetchRequest(requestId);
            }
            return request;
        });
    }

    /**
     * Reject a discard request (approved by compliance / business).
     */
    public Promise<DiscardRequest> rejectDiscard(String requestId, String rejectedBy, String reason) {
        return Promise.ofBlocking(executor, () -> {
            updateStatus(requestId, DiscardStatus.REJECTED, Instant.now());
            rejectedCounter.increment();

            DiscardRequest request = fetchRequest(requestId);
            if (request != null) {
                auditPort.log("DISCARD_REJECTED", "DeadLetter", request.deadLetterId(),
                    Map.of("requestId", requestId, "rejectedBy", rejectedBy, "reason", reason));
            }
            return request;
        });
    }

    // ─── Private helpers ─────────────────────────────────────────────────────

    private void executeDiscard(String deadLetterId, String requestId, String operatorId)
            throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE dead_letters SET status = 'DISCARDED', discard_reason = ?, " +
                 "discarded_at = NOW() WHERE dead_letter_id = ?")) {
            ps.setString(1, "Discard workflow " + requestId);
            ps.setString(2, deadLetterId);
            ps.executeUpdate();
        }
        auditPort.log("DEAD_LETTER_DISCARDED", "DeadLetter", deadLetterId,
            Map.of("requestId", requestId, "operatorId", operatorId));
    }

    private void persistRequest(String requestId, String deadLetterId, DiscardStatus status,
                                 String justification, String requestedBy, String approvalId,
                                 Instant requestedAt) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "INSERT INTO dlq_discard_requests " +
                 "(request_id, dead_letter_id, status, justification, requested_by, " +
                 "approval_id, requested_at) VALUES (?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, requestId);
            ps.setString(2, deadLetterId);
            ps.setString(3, status.name());
            ps.setString(4, justification);
            ps.setString(5, requestedBy);
            ps.setString(6, approvalId);
            ps.setTimestamp(7, Timestamp.from(requestedAt));
            ps.executeUpdate();
        }
    }

    private void updateStatus(String requestId, DiscardStatus status, Instant resolvedAt)
            throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "UPDATE dlq_discard_requests SET status = ?, resolved_at = ? WHERE request_id = ?")) {
            ps.setString(1, status.name());
            ps.setTimestamp(2, Timestamp.from(resolvedAt));
            ps.setString(3, requestId);
            ps.executeUpdate();
        }
    }

    private DiscardRequest fetchRequest(String requestId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT dead_letter_id, status, justification, requested_by, " +
                 "approval_id, requested_at, resolved_at FROM dlq_discard_requests " +
                 "WHERE request_id = ?")) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Timestamp rt = rs.getTimestamp("resolved_at");
                return new DiscardRequest(requestId,
                    rs.getString("dead_letter_id"),
                    DiscardStatus.valueOf(rs.getString("status")),
                    rs.getString("justification"),
                    rs.getString("requested_by"),
                    rs.getString("approval_id"),
                    rs.getTimestamp("requested_at").toInstant(),
                    rt != null ? rt.toInstant() : null);
            }
        }
    }

    private String fetchTopic(String deadLetterId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT topic_name FROM dead_letters WHERE dead_letter_id = ?")) {
            ps.setString(1, deadLetterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("topic_name") : "";
            }
        }
    }

    private long fetchPayloadSize(String deadLetterId) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                 "SELECT LENGTH(payload) FROM dead_letters WHERE dead_letter_id = ?")) {
            ps.setString(1, deadLetterId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }
}
