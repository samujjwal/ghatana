/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

/**
 * Type of mastery evidence bundle.
 *
 * <p>Determines the purpose and workflow for the evidence bundle.
 *
 * @doc.type enum
 * @doc.purpose Type of mastery evidence bundle
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum MasteryEvidenceBundleType {
    /**
     * Evidence supporting a mastery state transition (e.g., OBSERVED → PRACTICED).
     */
    TRANSITION,

    /**
     * Evidence supporting a promotion request (e.g., L2 → L3 procedural skill).
     */
    PROMOTION,

    /**
     * Evidence from evaluation pack execution.
     */
    EVALUATION,

    /**
     * Evidence from regression testing.
     */
    REGRESSION,

    /**
     * Evidence from safety evaluation.
     */
    SAFETY,

    /**
     * Evidence from compatibility testing.
     */
    COMPATIBILITY,

    /**
     * Manual evidence submitted by human reviewer.
     */
    MANUAL
}
