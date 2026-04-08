/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.agent.framework.repetition;

/**
 * Conditions that terminate normal recursive or iterative agent loops.
 *
 * @doc.type enum
 * @doc.purpose Termination condition taxonomy for agent repetition policy
 * @doc.layer platform
 * @doc.pattern Enum
 */
public enum TerminationCondition {
    /** Terminate when the agent produces a success result. */
    ON_SUCCESS,
    /** Terminate only when a goal confidence threshold is reached. */
    ON_CONFIDENCE_THRESHOLD,
    /** Terminate when the iteration count is exhausted. */
    ON_MAX_ITERATIONS,
    /** Terminate when the designated timeout is exceeded. */
    ON_TIMEOUT,
    /** Terminate immediately on any error. */
    ON_ERROR
}
