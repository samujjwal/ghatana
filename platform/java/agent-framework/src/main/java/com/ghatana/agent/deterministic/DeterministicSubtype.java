/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.deterministic;

/**
 * Subtypes of deterministic agent behaviour.
 *
 * <p>All subtypes guarantee: same input + same config → same output (DeterminismGuarantee.FULL
 * or CONFIG_SCOPED). No stochastic components are permitted.
 *
 * @since 2.0.0
 *
 * @doc.type enum
 * @doc.purpose Subtypes of deterministic agent strategies
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public enum DeterministicSubtype {

    /** Condition → action rule evaluation (Drools, OPA-style). */
    RULE_BASED,

    /** Numeric threshold with optional hysteresis bands. */
    THRESHOLD,

    /** Sequence/structural pattern matching within an event stream. */
    PATTERN,

    /** Finite-state-machine driven transitions. */
    FSM,

    /** Exact-match lookup (hash-map or trie-based, constant time). */
    EXACT_MATCH,

    /**
     * Governance and compliance policy evaluation.
     * Evaluates a set of constraints (data classification, cost caps, access rules)
     * and returns an allow/deny verdict. No side-effects.
     */
    POLICY,

    /**
     * Mathematical or bitwise operator applied to scalar/vector inputs.
     * Used for pipeline arithmetic, unit conversion, and data-normalization steps.
     */
    OPERATOR,

    /**
     * Template-based output generation (Liquid, Mustache, Jinja2 without LLM).
     * Renders structured or text output from a deterministic template + data model.
     * Distinct from probabilistic LLM generation: no sampling, fully reproducible.
     */
    TEMPLATE
}
