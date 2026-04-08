/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.tools;

/**
 * Status codes for a single tool execution attempt.
 *
 * @doc.type enum
 * @doc.purpose Status of a tool execution result
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum ToolExecutionStatus {

    /** The tool completed successfully and produced a valid output. */
    SUCCESS,

    /** The tool encountered an error during execution. */
    FAILED,

    /** A policy or governance rule denied the execution before it could start. */
    DENIED,

    /** The execution is waiting for a human approval decision. */
    APPROVAL_PENDING,

    /** The tool execution exceeded the allowed time limit. */
    TIMEOUT;

    /**
     * Returns {@code true} if the execution produced a usable output (i.e., {@code SUCCESS}).
     *
     * @return whether this status represents a successful outcome
     */
    public boolean isSuccessful() {
        return this == SUCCESS;
    }

    /**
     * Returns {@code true} if the execution was blocked before reaching the tool.
     *
     * @return whether the execution was blocked by policy or approval
     */
    public boolean isBlocked() {
        return this == DENIED || this == APPROVAL_PENDING;
    }
}
