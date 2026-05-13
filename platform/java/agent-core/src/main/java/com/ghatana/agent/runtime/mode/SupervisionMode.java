/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.runtime.mode;

/**
 * Supervision mode for agent task execution.
 *
 * <p>Determines <em>who</em> may gate or approve execution, distinct from
 * the execution strategy that determines <em>how</em> the agent executes.
 *
 * @doc.type enum
 * @doc.purpose Supervision level for agent task execution
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 * @see ExecutionStrategy
 */
public enum SupervisionMode {

    /**
     * Fully autonomous execution — no human gate required.
     * Used for mastered skills with high confidence.
     */
    AUTONOMOUS,

    /**
     * Supervised execution — results are reviewed but agent may proceed.
     * Used for competent skills where outcomes are auditable.
     */
    SUPERVISED,

    /**
     * Human-gated execution — explicit human approval required before acting.
     * Used for high-risk, irreversible, or low-mastery operations.
     */
    HUMAN_GATED,

    /**
     * Blocked — execution is prevented outright.
     * Used for obsolete, retired, or quarantined skills, and version-incompatible contexts.
     */
    BLOCKED
}
