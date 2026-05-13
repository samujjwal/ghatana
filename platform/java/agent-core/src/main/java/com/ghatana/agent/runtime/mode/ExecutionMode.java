/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.mode;

/**
 * Execution mode for agent task execution.
 *
 * <p>This enum previously mixed execution strategy and supervision semantics.
 * Use {@link ExecutionStrategy} and {@link SupervisionMode} for new code.
 *
 * @deprecated Use {@link ExecutionStrategy} + {@link SupervisionMode} together.
 * @doc.type enum
 * @doc.purpose Execution mode for agent task execution (deprecated compatibility shim)
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
@Deprecated(forRemoval = true)
public enum ExecutionMode {
    /**
     * Deterministic execution with full confidence.
     * Used for mastered skills in stable environments.
     */
    DETERMINISTIC_EXECUTION,

    /**
     * Bounded probabilistic reasoning with confidence thresholds.
     * Used for competent skills with some uncertainty.
     */
    BOUNDED_PROBABILISTIC_REASONING,

    /**
     * Supervised execution requiring human oversight.
     * Used for skills that require human monitoring.
     */
    SUPERVISED,

    /**
     * Autonomous execution without human intervention.
     * Used for fully mastered and trusted skills.
     */
    AUTONOMOUS,

    /**
     * Exploratory fast learning mode for new or unknown tasks.
     * Used when mastery is low or context is novel.
     */
    EXPLORATORY_FAST_LEARNING,

    /**
     * Maintenance-only mode for legacy versions.
     * Used when skill is in MAINTENANCE_ONLY state and context matches legacy scope.
     */
    MAINTENANCE_ONLY,

    /**
     * Human-gated mode requiring approval before execution.
     * Used for high-risk or irreversible operations.
     */
    HUMAN_GATED,

    /**
     * Verification-first mode with extra validation.
     * Used when contradictions or staleness are detected.
     */
    VERIFICATION_FIRST,

    /**
     * Blocked mode preventing execution.
     * Used for obsolete, retired, or quarantined skills.
     */
    BLOCKED
}
