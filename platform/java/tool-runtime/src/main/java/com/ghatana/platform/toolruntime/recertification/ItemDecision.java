/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime.recertification;

/**
 * Decision outcome for a single {@link RecertificationItem}.
 *
 * @doc.type enum
 * @doc.purpose Represent the certifier's decision on a single item
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum ItemDecision {

    /** The item has not yet been reviewed. */
    PENDING,

    /** The certifier confirmed that the access/policy/tool should remain active. */
    CERTIFIED,

    /** The certifier determined the access/policy/tool should be removed or disabled. */
    REVOKED
}
