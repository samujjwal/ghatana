/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning;

/**
 * State of a learning delta in the promotion pipeline.
 *
 * @doc.type enum
 * @doc.purpose State of learning delta in promotion pipeline
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum LearningDeltaState {
    /**
     * Delta has been proposed but not yet reviewed.
     */
    PROPOSED,

    /**
     * Delta is pending evaluation.
     */
    PENDING_EVALUATION,

    /**
     * Delta has been evaluated and is awaiting promotion decision.
     */
    EVALUATED,

    /**
     * Delta has been approved for promotion.
     */
    APPROVED,

    /**
     * Delta has been promoted to active knowledge.
     */
    PROMOTED,

    /**
     * Delta has been rejected.
     */
    REJECTED,

    /**
     * Delta is currently being promoted.
     */
    PROMOTING,

    /**
     * Delta promotion failed.
     */
    PROMOTION_FAILED,

    /**
     * Delta is obsolete and should not be used.
     */
    OBSOLETE
}
