/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.approval;

import java.time.Instant;

/**
 * An immutable snapshot of an approval request.
 *
 * @param requestId   opaque unique identifier
 * @param tenantId    owning tenant
 * @param agentId     requesting agent
 * @param actionType  the action type submitted for approval
 * @param context     free-form context passed to reviewers
 * @param status      current lifecycle status
 * @param submittedAt submission timestamp
 * @param decidedAt   when a APPROVED/REJECTED decision was made (null if still PENDING)
 * @param reviewerNote optional comment from the reviewer
 *
 * @doc.type record
 * @doc.purpose Immutable approval request state
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public record ApprovalRequest(
    String requestId,
    String tenantId,
    String agentId,
    String actionType,
    Object context,
    ApprovalStatus status,
    Instant submittedAt,
    Instant decidedAt,
    String reviewerNote
) {}
