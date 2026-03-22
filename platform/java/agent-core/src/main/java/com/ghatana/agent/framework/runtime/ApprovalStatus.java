/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.runtime;

/**
 * Status of a human approval request.
 *
 * @doc.type enum
 * @doc.purpose Tracks approval request lifecycle
 * @doc.layer framework
 * @doc.pattern ValueObject
 */
public enum ApprovalStatus {

    /** Request has been created and is awaiting review. */
    PENDING,

    /** Request was approved by all required roles. */
    APPROVED,

    /** Request was rejected by a reviewer. */
    REJECTED,

    /** Request expired before approval was granted. */
    EXPIRED,

    /** Request was cancelled by the requestor or system. */
    CANCELLED
}
