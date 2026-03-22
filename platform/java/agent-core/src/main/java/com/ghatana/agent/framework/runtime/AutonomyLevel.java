/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ghatana.agent.framework.runtime;

/**
 * Defines the canonical level of human oversight required for agent decisions.
 *
 * <p>Autonomy progresses through five clearly scoped tiers, from pure
 * advisory mode (no execution) through full autonomous execution. Each tier
 * defines whether the agent may act immediately, must wait for approval,
 * or must only advise.
 *
 * <h2>Canonical autonomy progression</h2>
 * <pre>
 *   ADVISORY           → Agent advises only; never executes actions
 *   DRAFT              → Agent prepares drafts; human must confirm before execution
 *   SUPERVISED         → Agent acts when confidence ≥ threshold; raises approval otherwise
 *   BOUNDED_AUTONOMOUS → Agent acts within declared action budget/scope; escalates outside bounds
 *   AUTONOMOUS         → Agent acts immediately on any confidence-meeting result
 * </pre>
 *
 * <h2>Migration from legacy values</h2>
 * <ul>
 *   <li>{@code MANUAL} (legacy) → {@link #DRAFT}</li>
 *   <li>{@code semi-autonomous} (catalog) → {@link #SUPERVISED}</li>
 *   <li>{@code assisted} (spec) → {@link #DRAFT}</li>
 *   <li>{@code advisory} (spec) → {@link #ADVISORY}</li>
 * </ul>
 *
 * @doc.type enum
 * @doc.purpose Canonical five-tier autonomy classification for agent governance
 * @doc.layer framework
 * @doc.pattern ValueObject
 * @doc.gaa.lifecycle reason
 */
public enum AutonomyLevel {

    /**
     * The agent provides recommendations only; it never executes actions.
     * Use for analysis, reporting, and advisory assistants.
     */
    ADVISORY,

    /**
     * The agent prepares draft actions or outputs that require explicit human
     * confirmation before execution. Use for content generation, document
     * preparation, and staging workflows.
     */
    DRAFT,

    /**
     * The agent raises a {@code HumanApprovalRequest} when confidence is below
     * the threshold, and executes automatically only when confidence is sufficient.
     * Use for LLM-backed agents performing consequential actions.
     */
    SUPERVISED,

    /**
     * The agent executes autonomously within declared action budgets, scope
     * boundaries, and action class limits. Actions outside declared bounds
     * trigger escalation. Use for agents with well-defined operational envelopes.
     */
    BOUNDED_AUTONOMOUS,

    /**
     * The agent executes its decision immediately when confidence meets the threshold.
     * Use for well-tested, deterministic, or low-risk agents.
     */
    AUTONOMOUS;

    /**
     * Returns {@code true} when the agent can act without human review at the given
     * confidence level.
     *
     * @param confidence     the result confidence [0.0, 1.0]
     * @param threshold      the minimum confidence threshold for autonomous execution
     * @return {@code true} if the agent may proceed without human approval
     */
    public boolean canActAutonomously(double confidence, double threshold) {
        return switch (this) {
            case ADVISORY           -> false;
            case DRAFT              -> false;
            case SUPERVISED         -> confidence >= threshold;
            case BOUNDED_AUTONOMOUS -> confidence >= threshold;
            case AUTONOMOUS         -> confidence >= threshold;
        };
    }

    /**
     * Returns {@code true} if this level requires human review for all actions,
     * regardless of confidence.
     */
    public boolean alwaysRequiresApproval() {
        return this == ADVISORY || this == DRAFT;
    }

    /**
     * Resolves legacy or mixed-vocabulary autonomy strings to the canonical level.
     * Returns {@code null} if the value cannot be resolved.
     *
     * @param value the autonomy level string (case-insensitive)
     * @return canonical autonomy level, or {@code null} if unrecognized
     */
    public static AutonomyLevel fromString(String value) {
        if (value == null || value.isBlank()) return null;
        return switch (value.trim().toLowerCase().replace('-', '_').replace(' ', '_')) {
            case "advisory"                     -> ADVISORY;
            case "draft"                        -> DRAFT;
            case "supervised", "semi_autonomous" -> SUPERVISED;
            case "bounded_autonomous"            -> BOUNDED_AUTONOMOUS;
            case "autonomous"                    -> AUTONOMOUS;
            // Legacy aliases
            case "manual"                        -> DRAFT;
            case "assisted"                      -> DRAFT;
            default                              -> null;
        };
    }
}
