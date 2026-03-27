/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.approval;

import io.activej.promise.Promise;

/**
 * Determines whether an agent action requires explicit human approval before execution.
 *
 * <p>Implementations may evaluate action type, risk level, tenant-specific policies,
 * or regulatory requirements to decide whether to gate execution behind an approval.
 *
 * @doc.type interface
 * @doc.purpose Classify agent actions as requiring (or not requiring) human approval
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface ApprovalGateway {

    /**
     * Returns {@code true} if the given action type requires human approval.
     *
     * @param tenantId   the calling tenant
     * @param agentId    the requesting agent
     * @param actionType a short descriptor of the action (e.g. "DELETE_RECORD", "SEND_EMAIL")
     * @return promise resolving to {@code true} if approval is required
     */
    Promise<Boolean> requiresApproval(String tenantId, String agentId, String actionType);

    /**
     * Submit an action for human approval, returning an opaque approval-request ID.
     *
     * @param tenantId   the calling tenant
     * @param agentId    the requesting agent
     * @param actionType the action type to be approved
     * @param context    free-form context object passed to reviewers (e.g. serialised payload)
     * @return promise resolving to a non-null approval request ID
     */
    Promise<String> requestApproval(String tenantId, String agentId, String actionType, Object context);
}
