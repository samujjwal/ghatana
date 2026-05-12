/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.mode;

/**
 * Execution mode determining how the agent should process a task.
 *
 * @doc.type enum
 * @doc.purpose Execution mode for agent behavior
 * @doc.layer agent-core
 * @doc.pattern Enumeration
 */
public enum ExecutionMode {
    /**
     * Deterministic execution - use mastered procedures with no exploration.
     */
    DETERMINISTIC,

    /**
     * Bounded probabilistic - allow some exploration within safety bounds.
     */
    BOUNDED_PROBABILISTIC,

    /**
     * Fast learning - explore aggressively to learn new patterns.
     */
    FAST_LEARNING,

    /**
     * Maintenance only - minimal fixes for legacy code, no architecture changes.
     */
    MAINTENANCE_ONLY,

    /**
     * Human gated - require human approval before execution.
     */
    HUMAN_GATED,

    /**
     * Verification first - verify before applying any changes.
     */
    VERIFICATION_FIRST,

    /**
     * Blocked - task cannot be executed due to safety or policy constraints.
     */
    BLOCKED
}
