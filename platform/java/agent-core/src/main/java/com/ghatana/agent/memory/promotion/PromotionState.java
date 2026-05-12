/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.memory.promotion;

/**
 * State of memory promotion through the validation pipeline.
 *
 * @doc.type enum
 * @doc.purpose Promotion state for memory items
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum PromotionState {
    /**
     * Promotion is pending validation.
     */
    PENDING_VALIDATION,

    /**
     * Promotion has been approved.
     */
    APPROVED,

    /**
     * Promotion has been rejected.
     */
    REJECTED,

    /**
     * Promotion is being evaluated.
     */
    UNDER_EVALUATION,

    /**
     * Promotion is on hold pending additional evidence.
     */
    ON_HOLD
}
