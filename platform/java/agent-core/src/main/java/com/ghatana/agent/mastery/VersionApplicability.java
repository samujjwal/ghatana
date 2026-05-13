/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

/**
 * Applicability decision for a version context.
 *
 * <p>Provides a numeric score for ranking and decision-making:
 * <ul>
 *   <li>ACTIVE: 100 - fully supported and recommended</li>
 *   <li>MAINTENANCE: 50 - usable but not recommended for new work</li>
 *   <li>UNKNOWN: 0 - no explicit constraint match</li>
 *   <li>OBSOLETE: -100 - should not be used</li>
 * </ul>
 *
 * @doc.type enum
 * @doc.purpose Applicability decision for version context
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum VersionApplicability {
    /**
     * Version is actively supported and recommended.
     */
    ACTIVE(100),

    /**
     * Version is in maintenance mode - usable but not recommended for new work.
     */
    MAINTENANCE(50),

    /**
     * Version is obsolete - should not be used.
     */
    OBSOLETE(-100),

    /**
     * Version applicability is unknown.
     */
    UNKNOWN(0);

    private final int score;

    VersionApplicability(int score) {
        this.score = score;
    }

    /**
     * Returns the numeric score for this applicability.
     * Higher scores indicate better applicability.
     *
     * @return numeric score (ACTIVE=100, MAINTENANCE=50, UNKNOWN=0, OBSOLETE=-100)
     */
    public int getScore() {
        return score;
    }

    /**
     * Returns true if this applicability is usable (ACTIVE or MAINTENANCE).
     *
     * @return true if usable
     */
    public boolean isUsable() {
        return this == ACTIVE || this == MAINTENANCE;
    }

    /**
     * Returns true if this applicability blocks execution (OBSOLETE).
     *
     * @return true if blocks execution
     */
    public boolean isBlocked() {
        return this == OBSOLETE;
    }
}
