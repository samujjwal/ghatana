/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.approval;

import io.activej.promise.Promise;

/**
 * Manages the lifecycle of human-approval workflow requests.
 *
 * <p>Callers create a request via {@link ApprovalGateway#requestApproval}, then poll
 * or subscribe for updates. Reviewers call {@link #approve} or {@link #reject}.
 *
 * @doc.type interface
 * @doc.purpose Manage the lifecycle (submit/approve/reject/query) of approval requests
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface ApprovalWorkflow {

    /**
     * Submit a new approval request, returning a new {@link ApprovalRequest} in PENDING state.
     *
     * @param tenantId   owning tenant
     * @param agentId    requesting agent
     * @param actionType the action type to approve
     * @param context    free-form context for reviewers
     * @return promise resolving to the created request
     */
    Promise<ApprovalRequest> submit(String tenantId, String agentId, String actionType, Object context);

    /**
     * Approve the request identified by {@code requestId}.
     *
     * @param requestId    the request to approve
     * @param reviewerNote optional reviewer comment
     * @return promise resolving to the updated request in APPROVED state
     */
    Promise<ApprovalRequest> approve(String requestId, String reviewerNote);

    /**
     * Reject the request identified by {@code requestId}.
     *
     * @param requestId    the request to reject
     * @param reviewerNote optional reviewer comment
     * @return promise resolving to the updated request in REJECTED state
     */
    Promise<ApprovalRequest> reject(String requestId, String reviewerNote);

    /**
     * Retrieve the current state of an approval request.
     *
     * @param requestId the request ID
     * @return promise resolving to the current state, or failed with {@link IllegalArgumentException} if not found
     */
    Promise<ApprovalRequest> get(String requestId);
}
