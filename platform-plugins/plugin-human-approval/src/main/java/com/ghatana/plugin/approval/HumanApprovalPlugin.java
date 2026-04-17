package com.ghatana.plugin.approval;

import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Human Approval Plugin SPI — human-in-the-loop approval for regulated operations.
 *
 * <p>Provides the canonical interface for requesting, querying, and completing
 * human approvals in regulated workflows. Use cases include:</p>
 * <ul>
 *   <li>Healthcare: manual override of automated clinical decisions</li>
 *   <li>Finance: large-value trade approval gates, regulatory sign-off</li>
 *   <li>Compliance: exception request review, policy waiver approval</li>
 * </ul>
 *
 * <p>Implementations must be idempotent on duplicate requests with the same
 * {@code requestId}. The caller is responsible for generating stable IDs.</p>
 *
 * <p>Implementations: {@link impl.StandardHumanApprovalPlugin} (in-memory, dev/test),
 * {@link impl.DurableHumanApprovalPlugin} (JDBC-backed, production).</p>
 *
 * @doc.type interface
 * @doc.purpose Human-in-the-loop approval SPI for regulated workflows
 * @doc.layer platform
 * @doc.pattern Plugin, SPI
 * @author Ghatana Platform Team
 * @since 1.0.0
 */
public interface HumanApprovalPlugin extends Plugin {

    /**
     * Requests human approval for a regulated operation.
     *
     * <p>Creates an approval record in the PENDING state. If an approval with the
     * same {@code requestId} already exists, the existing record is returned
     * unchanged (idempotency guarantee).</p>
     *
     * @param request the approval request details — must not be {@code null}
     * @return Promise containing the created {@link ApprovalRecord}
     */
    Promise<ApprovalRecord> requestApproval(ApprovalRequest request);

    /**
     * Gets the current status of an approval by request ID.
     *
     * @param requestId the approval request identifier
     * @return Promise containing the approval record if found
     * @throws IllegalArgumentException if {@code requestId} is blank
     */
    Promise<java.util.Optional<ApprovalRecord>> getApprovalStatus(String requestId);

    /**
     * Records an approval decision (APPROVED or REJECTED).
     *
     * <p>Transitions an approval in PENDING state to APPROVED or REJECTED. Calling
     * this method on an already-decided approval is a no-op and returns the existing
     * record.</p>
     *
     * @param requestId  the approval request identifier
     * @param decision   the approval decision — APPROVED or REJECTED
     * @param reviewerId the identifier of the approving reviewer
     * @param notes      optional notes from the reviewer (may be {@code null})
     * @return Promise containing the updated {@link ApprovalRecord}
     * @throws IllegalStateException if the approval is not in PENDING state
     */
    Promise<ApprovalRecord> completeApproval(String requestId, ApprovalDecision decision,
                                              String reviewerId, String notes);

    /**
     * Lists all pending approvals for a given subject (e.g. a patient, trade ID).
     *
     * @param subjectId the subject identifier
     * @return Promise containing pending approval records for the subject
     */
    Promise<List<ApprovalRecord>> listPendingForSubject(String subjectId);

    /**
     * Cancels a pending approval request.
     *
     * <p>Transitions a PENDING approval to CANCELLED. No-op on already-decided records.</p>
     *
     * @param requestId the approval request identifier
     * @param reason    the cancellation reason
     * @return Promise completing when cancellation is applied
     */
    Promise<Void> cancelApproval(String requestId, String reason);
}
