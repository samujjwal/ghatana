/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.approval;

/**
 * The lifecycle states of an approval request.
 *
 * @doc.type enum
 * @doc.purpose Represent the state machine for human-approval workflow requests
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum ApprovalStatus {
    /** The request has been submitted and is awaiting a reviewer. */
    PENDING,
    /** A human reviewer approved the action. */
    APPROVED,
    /** A human reviewer rejected the action. */
    REJECTED,
    /** The approval request expired before a decision was made. */
    EXPIRED
}
