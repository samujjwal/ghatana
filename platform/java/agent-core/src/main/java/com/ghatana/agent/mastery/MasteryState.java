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
 * <p><b>MASTERY_STATE vs Normal Self-Learning:</b>
 * MasteryState governance is distinct from normal self-learning:
 * <ul>
 *   <li>MasteryState tracks procedural skill lifecycle and execution permissions
 *       (UNKNOWN → OBSERVED → PRACTICED → COMPETENT → MASTERED → MAINTENANCE_ONLY → OBSOLETE → RETIRED → QUARANTINED)</li>
 *   <li>Normal self-learning uses LearningDelta and the promotion pipeline to propose
 *       and promote changes to agent behavior (semantic facts, retrieval policies, etc.)</li>
 *   <li>MasteryState is about runtime execution control based on skill proficiency</li>
 *   <li>LearningDelta is about governance of what changes an agent may propose and promote</li>
 * </ul>
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

    /**
     * Returns true if this state is potentially executable with appropriate conditions.
     * States that can execute (with or without constraints):
     * <ul>
     *   <li>MASTERED - fully executable</li>
     *   <li>COMPETENT - fully executable</li>
     *   <li>PRACTICED - executable with human approval</li>
     *   <li>MAINTENANCE_ONLY - executable in legacy context only</li>
     *   <li>OBSERVED - executable with human approval (observed but not practiced)</li>
     * </ul>
     *
     * @return true if potentially executable with appropriate conditions
     */
    public boolean isPotentiallyExecutable() {
        return this == MASTERED || this == COMPETENT || this == PRACTICED || 
               this == MAINTENANCE_ONLY || this == OBSERVED;
    }

    /**
     * Returns true if this state requires legacy context for execution.
     * Only MAINTENANCE_ONLY requires legacy context.
     *
     * @return true if legacy context is required
     */
    public boolean requiresLegacyContext() {
        return this == MAINTENANCE_ONLY;
    }
}
