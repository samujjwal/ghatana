package com.ghatana.plugin.approval;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * An approval record capturing the full lifecycle state of a human approval request.
 *
 * <p>{@code reviewerId} and {@code reviewerNotes} are non-null only when
 * {@code status} is {@link ApprovalStatus#APPROVED} or {@link ApprovalStatus#REJECTED}.</p>
 *
 * @param requestId     stable UUID identifying this approval request
 * @param subjectId     identifier of the subject being approved
 * @param requestedBy   identifier of the requesting principal
 * @param action        the operation requiring approval
 * @param status        current lifecycle state
 * @param requestedAt   timestamp when the request was created
 * @param expiresAt     optional expiry deadline; {@code null} means no expiry
 * @param decidedAt     timestamp of the review decision; {@code null} while PENDING
 * @param reviewerId    identifier of the reviewer; {@code null} while PENDING
 * @param reviewerNotes notes from the reviewer; {@code null} while PENDING
 * @param context       optional contextual metadata for the approval request
 *
 * @doc.type class
 * @doc.purpose Immutable snapshot of a human approval request lifecycle
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record ApprovalRecord(
        String requestId,
        String subjectId,
        String requestedBy,
        String action,
        ApprovalStatus status,
        Instant requestedAt,
        Instant expiresAt,
        Instant decidedAt,
        String reviewerId,
        String reviewerNotes,
        Map<String, Object> context
) {
    /**
     * Compact canonical constructor that validates invariants.
     */
    public ApprovalRecord {
        Objects.requireNonNull(requestId,   "requestId must not be null");
        Objects.requireNonNull(subjectId,   "subjectId must not be null");
        Objects.requireNonNull(requestedBy, "requestedBy must not be null");
        Objects.requireNonNull(action,      "action must not be null");
        Objects.requireNonNull(status,      "status must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");
    }

    /**
     * Convenience factory for creating a new PENDING record from an {@link ApprovalRequest}.
     *
     * @param request the approval request
     * @return a new {@code ApprovalRecord} in PENDING state
     */
    public static ApprovalRecord pending(ApprovalRequest request) {
        return new ApprovalRecord(
                request.requestId(),
                request.subjectId(),
                request.requestedBy(),
                request.action(),
                ApprovalStatus.PENDING,
                request.requestedAt(),
                request.expiresAt(),
                null, null, null, null
        );
    }

    /**
     * Returns a copy of this record with a completed decision applied.
     *
     * @param decision   the reviewer decision
     * @param reviewerId the reviewer identifier
     * @param notes      optional notes
     * @param decidedAt  the decision timestamp
     * @return updated record
     */
    public ApprovalRecord withDecision(ApprovalDecision decision, String reviewerId,
                                        String notes, Instant decidedAt) {
        ApprovalStatus newStatus = (decision == ApprovalDecision.APPROVED)
                ? ApprovalStatus.APPROVED
                : ApprovalStatus.REJECTED;
        return new ApprovalRecord(
                requestId, subjectId, requestedBy, action,
                newStatus, requestedAt, expiresAt,
                decidedAt, reviewerId, notes, context
        );
    }

    /**
     * Returns a copy of this record in CANCELLED state.
     *
     * @param reason    the cancellation reason stored as reviewer notes
     * @param cancelledAt the cancellation timestamp
     * @return updated record
     */
    public ApprovalRecord cancelled(String reason, Instant cancelledAt) {
        return new ApprovalRecord(
                requestId, subjectId, requestedBy, action,
                ApprovalStatus.CANCELLED, requestedAt, expiresAt,
                cancelledAt, null, reason, context
        );
    }
}
