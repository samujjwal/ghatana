/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.change;

/**
 * Lifecycle state of a {@link ChangeRequest}.
 *
 * @doc.type enum
 * @doc.purpose Represent the review lifecycle state of a change request
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum ChangeStatus {

    /** The change has been submitted and is awaiting reviewer action. */
    PENDING_REVIEW,

    /** The change has been approved and may be applied. */
    APPROVED,

    /** The change was rejected; it must not be applied. */
    REJECTED,

    /** The requester withdrew the change before a reviewer acted. */
    WITHDRAWN
}
