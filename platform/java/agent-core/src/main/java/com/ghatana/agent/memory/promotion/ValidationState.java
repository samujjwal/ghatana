/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.promotion;

/**
 * Validation state for memory promotion.
 *
 * @doc.type enum
 * @doc.purpose Validation state for memory promotion
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum ValidationState {
    /**
     * Validation has not started.
     */
    NOT_STARTED,

    /**
     * Validation is in progress.
     */
    IN_PROGRESS,

    /**
     * Validation has passed.
     */
    PASSED,

    /**
     * Validation has failed.
     */
    FAILED,

    /**
     * Validation was skipped.
     */
    SKIPPED
}
