/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.deterministic;

/**
 * Subtypes of deterministic agent behaviour.
 *
 * @since 2.0.0
 *
 * @doc.type enum
 * @doc.purpose Subtypes of deterministic agent strategies
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum DeterministicSubtype {

    /** Condition → action rule evaluation. */
    RULE_BASED,

    /** Numeric threshold with optional hysteresis. */
    THRESHOLD,

    /** Complex-event-processing pattern matching (sequence, within-time). */
    PATTERN,

    /** Finite-state-machine driven transitions. */
    FSM,

    /** Exact-match lookup (hash-based). */
    EXACT_MATCH
}
