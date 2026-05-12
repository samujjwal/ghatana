/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.learning.delta;

/**
 * Lifecycle state of a learning delta.
 *
 * @doc.type enum
 * @doc.purpose Lifecycle state for learning deltas
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum LearningDeltaState {
    /**
     * Delta has been proposed but not yet validated.
     */
    PROPOSED,

    /**
     * Delta is being validated.
     */
    VALIDATING,

    /**
     * Delta has been evaluated.
     */
    EVALUATED,

    /**
     * Delta has been approved for promotion.
     */
    APPROVED,

    /**
     * Delta has been promoted to active memory/mastery.
     */
    PROMOTED,

    /**
     * Delta has been rejected.
     */
    REJECTED,

    /**
     * Delta has been quarantined due to safety concerns.
     */
    QUARANTINED,

    /**
     * Delta has been rolled back.
     */
    ROLLED_BACK
}
