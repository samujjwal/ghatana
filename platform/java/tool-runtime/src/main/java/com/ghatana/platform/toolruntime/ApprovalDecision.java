/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.toolruntime;

/**
 * Approval decision values for a tool execution that requires human review.
 *
 * @doc.type enum
 * @doc.purpose Approval gate decision for a tool execution requiring human review
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum ApprovalDecision {

    /** A human reviewer approved the tool call. */
    APPROVED,

    /** A human reviewer rejected the tool call. */
    DENIED,

    /** The tool call is awaiting a human decision. */
    PENDING;

    /**
     * Returns {@code true} if the decision is terminal (cannot change further).
     *
     * @return whether the decision is final
     */
    public boolean isTerminal() {
        return this == APPROVED || this == DENIED;
    }
}
