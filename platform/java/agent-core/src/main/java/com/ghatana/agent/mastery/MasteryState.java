/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mastery;

/**
 * Lifecycle states for a mastery item tracking skill maturity.
 *
 * <p>The mastery lifecycle represents an agent's progression from unknown
 * to mastered knowledge, with branches for maintenance, obsolescence, and quarantine.
 *
 * @doc.type enum
 * @doc.purpose Lifecycle states for skill mastery tracking
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum MasteryState {
    /**
     * Initial state - no evidence of skill exists.
     */
    UNKNOWN,

    /**
     * Skill has been observed but not practiced.
     * Requires at least one trace or verified source.
     */
    OBSERVED,

    /**
     * Skill has been practiced through repeated episodes or sandbox experiments.
     */
    PRACTICED,

    /**
     * Skill is competent - procedure exists and basic evaluation passes.
     */
    COMPETENT,

    /**
     * Skill is mastered - regression, safety, recovery, and compatibility tests pass.
     */
    MASTERED,

    /**
     * Skill is in maintenance-only mode - new active version exists, old version still used.
     * Retrieval only when environment context matches legacy scope.
     */
    MAINTENANCE_ONLY,

    /**
     * Skill is obsolete - docs/API/security/runtime contradiction or repeated failures.
     * Excluded from active retrieval by default.
     */
    OBSOLETE,

    /**
     * Skill is retired - no active retrieval/use case remains.
     * Archive/audit only.
     */
    RETIRED,

    /**
     * Skill is quarantined - unsafe behavior or failed safety evaluation.
     * Cannot be used for execution.
     */
    QUARANTINED;

    /**
     * Returns true if this state is considered active for retrieval purposes.
     *
     * @return true if the state is active for retrieval
     */
    public boolean isActiveForRetrieval() {
        return this == OBSERVED || this == PRACTICED || this == COMPETENT || this == MASTERED;
    }

    /**
     * Returns true if this state requires evaluation evidence for promotion.
     *
     * @return true if evaluation evidence is required
     */
    public boolean requiresEvaluationForPromotion() {
        return this == COMPETENT || this == MASTERED;
    }

    /**
     * Returns true if this state can be transitioned to directly from UNKNOWN.
     *
     * @return true if direct transition from UNKNOWN is allowed
     */
    public boolean canTransitionFromUnknown() {
        return this == OBSERVED;
    }

    /**
     * Returns true if this state is executable.
     * MASTERED and COMPETENT are executable.
     *
     * @return true if the state is executable
     */
    public boolean isExecutable() {
        return this == MASTERED || this == COMPETENT;
    }

    /**
     * Returns true if this state is retrievable for new work.
     * MASTERED, COMPETENT, and PRACTICED are retrievable for new work.
     *
     * @return true if retrievable for new work
     */
    public boolean isRetrievableForNewWork() {
        return this == MASTERED || this == COMPETENT || this == PRACTICED;
    }

    /**
     * Returns true if this state is retrievable for legacy work.
     * MAINTENANCE_ONLY is executable only in matching legacy context.
     *
     * @return true if retrievable for legacy work
     */
    public boolean isRetrievableForLegacyWork() {
        return this == MAINTENANCE_ONLY;
    }

    /**
     * Returns true if this state is terminal (no further transitions expected).
     * OBSOLETE, RETIRED, and QUARANTINED are terminal states.
     *
     * @return true if the state is terminal
     */
    public boolean isTerminal() {
        return this == OBSOLETE || this == RETIRED || this == QUARANTINED;
    }
}
