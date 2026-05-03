package com.ghatana.digitalmarketing.application.approval;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import com.ghatana.digitalmarketing.domain.approval.ApprovalSnapshot;
import com.ghatana.digitalmarketing.domain.approval.ApprovalTargetType;
import com.ghatana.plugin.approval.ApprovalDecision;
import com.ghatana.plugin.approval.ApprovalRecord;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Optional;

/**
 * Application-level approval workflow service for DMOS.
 *
 * <p>Manages approval requests, decisions, and snapshots for content versions,
 * strategies, proposals, SOWs, budgets, campaign launches, connector writes,
 * and policy overrides.</p>
 *
 * @doc.type interface
 * @doc.purpose DMOS F1-022 approval workflow service interface
 * @doc.layer product
 * @doc.pattern Service
 */
public interface ApprovalWorkflowService {

    /**
     * Submits a target entity for human approval.
     *
     * <p>An approval snapshot is stored capturing the entity state at submission
     * time. Returns the approval record with a stable {@code requestId}.</p>
     *
     * @param ctx     operation context carrying tenant, workspace, and actor
     * @param command the submit command
     * @return Promise resolving to the created {@link ApprovalRecord}
     */
    Promise<ApprovalRecord> submitForApproval(DmOperationContext ctx, SubmitForApprovalCommand command);

    /**
     * Records an approval decision (approve or reject) by the current actor.
     *
     * @param ctx     operation context — actor must hold the required approver role
     * @param command the approval decision command
     * @return Promise resolving to the updated {@link ApprovalRecord}
     */
    Promise<ApprovalRecord> recordDecision(DmOperationContext ctx, RecordApprovalDecisionCommand command);

    /**
     * Gets the current approval status for a request ID.
     *
     * @param ctx       operation context
     * @param requestId the approval request identifier
     * @return Promise resolving to the record, or {@link Optional#empty()} if not found
     */
    Promise<Optional<ApprovalRecord>> getApprovalStatus(DmOperationContext ctx, String requestId);

    /**
     * Lists pending approvals for a given subject (workspace or entity).
     *
     * @param ctx       operation context
     * @param subjectId the workspace ID or entity ID used as subject
     * @return Promise resolving to the list of pending approval records
     */
    Promise<List<ApprovalRecord>> listPendingApprovals(DmOperationContext ctx, String subjectId);

    /**
     * Retrieves the stored approval snapshot for a request.
     *
     * @param ctx       operation context
     * @param requestId the approval request identifier
     * @return Promise resolving to the snapshot, or {@link Optional#empty()} if not found
     */
    Promise<Optional<ApprovalSnapshot>> getSnapshot(DmOperationContext ctx, String requestId);

    // -------------------------------------------------------------------------
    // Command records
    // -------------------------------------------------------------------------

    /**
     * Command to submit a target entity for human approval.
     *
     * @param targetType         type of entity being submitted
     * @param targetId           entity identifier
     * @param description        human-readable description of what requires approval
     * @param riskLevel          numeric risk level 1-5
     * @param requiredApproverRole the role required to approve this request
     * @param validationResultId reference to the most recent validation result, may be {@code null}
     */
    record SubmitForApprovalCommand(
            ApprovalTargetType targetType,
            String targetId,
            String description,
            int riskLevel,
            String requiredApproverRole,
            String validationResultId
    ) {}

    /**
     * Command to record an approval or rejection decision.
     *
     * @param requestId approval request identifier
     * @param decision  APPROVED or REJECTED
     * @param notes     reviewer notes (required for rejections; optional for approvals)
     */
    record RecordApprovalDecisionCommand(
            String requestId,
            ApprovalDecision decision,
            String notes
    ) {}
}
