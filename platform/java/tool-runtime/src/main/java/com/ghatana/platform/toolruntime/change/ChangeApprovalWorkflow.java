/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.change;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;

/**
 * Risk-gated approval workflow for platform and configuration changes.
 *
 * <p>Unlike {@code ApprovalWorkflow} (which gates ad-hoc agent actions),
 * {@code ChangeApprovalWorkflow} governs structured changes to tenant configuration,
 * tooling, policies, and permissions. Each change is assigned a risk score (0–100);
 * changes above the workflow's threshold are automatically routed for human review.
 *
 * <p>Typical usage:
 * <pre>{@code
 * Promise<ChangeRequest> req = workflow.submitChange(
 *     tenantId, "agent-42", ChangeType.POLICY_UPDATE,
 *     "Add rate-limit policy v2", Map.of("policy", policyJson));
 *
 * // If riskScore >= threshold, status = PENDING_REVIEW; otherwise APPROVED.
 * if (req.getResult().status() == ChangeStatus.APPROVED) { apply(); }
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Risk-gated workflow for tenant/platform change approvals
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface ChangeApprovalWorkflow {

    /**
     * Submit a proposed change for review.
     *
     * <p>Changes with a computed risk score below the workflow threshold are
     * immediately approved; others are placed in {@link ChangeStatus#PENDING_REVIEW}.
     *
     * @param tenantId        owning tenant
     * @param requestingAgent agent or service requesting the change
     * @param changeType      type classification
     * @param description     human-readable description
     * @param metadata        optional before/after values or additional context
     * @return promise resolving to the new {@link ChangeRequest}
     */
    Promise<ChangeRequest> submitChange(String tenantId, String requestingAgent,
            ChangeType changeType, String description, Map<String, Object> metadata);

    /**
     * Approve a pending change request.
     *
     * @param changeId   the change to approve
     * @param reviewerId identifier of the reviewer
     * @param notes      optional approval notes
     * @return promise resolving to the updated request in {@link ChangeStatus#APPROVED}
     * @throws IllegalArgumentException if the change does not exist
     * @throws IllegalStateException    if the change is not in PENDING_REVIEW
     */
    Promise<ChangeRequest> approve(String changeId, String reviewerId, String notes);

    /**
     * Reject a pending change request.
     *
     * @param changeId   the change to reject
     * @param reviewerId identifier of the reviewer
     * @param reason     required rejection reason
     * @return promise resolving to the updated request in {@link ChangeStatus#REJECTED}
     * @throws IllegalArgumentException if the change does not exist
     * @throws IllegalStateException    if the change is not in PENDING_REVIEW
     */
    Promise<ChangeRequest> reject(String changeId, String reviewerId, String reason);

    /**
     * Withdraw a pending change request (requester-initiated cancellation).
     *
     * @param changeId the change to withdraw
     * @return promise resolving to the updated request in {@link ChangeStatus#WITHDRAWN}
     * @throws IllegalArgumentException if the change does not exist
     * @throws IllegalStateException    if the change is not in PENDING_REVIEW
     */
    Promise<ChangeRequest> withdraw(String changeId);

    /**
     * Retrieve a change request by its identifier.
     *
     * @param changeId the change request identifier
     * @return promise resolving to the {@link ChangeRequest}, or failing with
     *         {@link IllegalArgumentException} if not found
     */
    Promise<ChangeRequest> getChange(String changeId);

    /**
     * List all pending change requests for a tenant.
     *
     * @param tenantId the tenant to query
     * @return promise resolving to all requests in {@link ChangeStatus#PENDING_REVIEW}
     */
    Promise<List<ChangeRequest>> listPending(String tenantId);
}
