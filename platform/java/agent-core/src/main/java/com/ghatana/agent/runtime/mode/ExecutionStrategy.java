/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.mode;

/**
 * Execution strategy for agent task execution.
 *
 * <p>Determines <em>how</em> the agent should execute a task, distinct from
 * the supervision level that determines <em>who</em> may gate or approve it.
 *
 * @doc.type enum
 * @doc.purpose Execution strategy for agent task execution
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 * @see SupervisionMode
 */
public enum ExecutionStrategy {

    /**
     * Deterministic execution with full confidence.
     * Used for mastered skills in stable, active-version environments.
     */
    DETERMINISTIC_EXECUTION,

    /**
     * Bounded probabilistic reasoning with defined confidence thresholds.
     * Used for competent skills with some uncertainty.
     */
    BOUNDED_PROBABILISTIC_REASONING,

    /**
     * Exploratory fast-learning mode for new or unknown tasks.
     * Used when mastery is low or context is novel.
     */
    EXPLORATORY_FAST_LEARNING,

    /**
     * Maintenance-only mode for legacy versions.
     * Used when skill is in MAINTENANCE_ONLY state matching a legacy scope.
     */
    MAINTENANCE_ONLY,

    /**
     * Verification-first mode with extra validation before any action.
     * Used when contradictions, staleness, or version obsolescence are detected.
     */
    VERIFICATION_FIRST
}
