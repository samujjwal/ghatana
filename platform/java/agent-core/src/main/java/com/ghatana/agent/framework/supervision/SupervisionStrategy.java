/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.supervision;

/**
 * Strategy applied by a supervising agent when a child agent fails.
 *
 * <p>The six canonical strategies follow the actor-model "one-for-one / one-for-all"
 * terminology, extended with Ghatana-specific agent strategies.
 *
 * @doc.type enum
 * @doc.purpose Supervision strategy taxonomy for inter-agent failure handling
 * @doc.layer platform
 * @doc.pattern Enum
 */
public enum SupervisionStrategy {
    /**
     * Restart only the failed child agent; leave siblings untouched.
     * Default strategy for isolated stateless failures.
     */
    RESTART_ONE,
    /**
     * Restart all child agents when any one fails.
     * Appropriate when children share significant state.
     */
    RESTART_ALL,
    /**
     * Stop only the failed child; do not restart it.
     * The supervisor escalates or routes around the stopped agent.
     */
    STOP_ONE,
    /**
     * Stop all child agents when any one fails; escalate to the parent.
     */
    STOP_ALL,
    /**
     * Escalate the failure to the parent supervisor without restarting anything.
     * Useful when the current supervisor cannot handle the failure class.
     */
    ESCALATE,
    /**
     * Resume the failed child from its last known checkpoint without restarting.
     * Requires checkpoint support in the child agent.
     */
    RESUME_FROM_CHECKPOINT
}
