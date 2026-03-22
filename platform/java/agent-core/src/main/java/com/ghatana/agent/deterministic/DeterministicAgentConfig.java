/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.deterministic;

import com.ghatana.agent.AgentConfig;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Configuration for {@link DeterministicAgent}.
 *
 * <p>Extends {@link AgentConfig} with deterministic-specific settings:
 * rules, thresholds, FSM definitions, and exact-match tables.
 *
 * @since 2.0.0
 *
 * @doc.type class
 * @doc.purpose Configuration for deterministic agent rules and thresholds
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
@Value
@lombok.experimental.NonFinal
@lombok.EqualsAndHashCode(callSuper = true)
@SuperBuilder(toBuilder = true)
public class DeterministicAgentConfig extends AgentConfig {

    /** Deterministic subtype driving the evaluation strategy. */
    @lombok.Builder.Default
    @NotNull DeterministicSubtype subtype = DeterministicSubtype.RULE_BASED;

    // ── Rule-Based ──────────────────────────────────────────────────────────

    /** Rules for RULE_BASED subtype. */
    @Singular @NotNull List<Rule> rules;

    /** Whether to evaluate all rules or stop at first match. */
    @lombok.Builder.Default boolean evaluateAllRules = false;

    // ── Threshold ───────────────────────────────────────────────────────────

    /** Threshold evaluators for THRESHOLD subtype. */
    @Singular @NotNull List<ThresholdEvaluator> thresholds;

    // ── FSM ─────────────────────────────────────────────────────────────────

    /** FSM definition for FSM subtype. */
    @Nullable FiniteStateMachine.FSMDefinition fsmDefinition;

    /** Field in the input map used as entity key for FSM state tracking. */
    @lombok.Builder.Default
    @NotNull String fsmEntityKeyField = "entityId";

    // ── Exact Match ─────────────────────────────────────────────────────────

    /** Field to check for exact match. */
    @Nullable String exactMatchField;

    /** Lookup table: match-value → output actions. */
    @Singular("exactMatchEntry") @NotNull Map<String, Map<String, Object>> exactMatchTable;

    // ── Default output ──────────────────────────────────────────────────────

    /** Actions to use when no rule/threshold/FSM/exact-match fires. */
    @Singular("defaultAction") @NotNull Map<String, Object> defaultActions;

    // ── Pattern (NFA) ────────────────────────────────────────────────────────

    /**
     * Strategy that wraps the NFA pattern-matching subsystem.
     *
     * <p>Must be provided when {@link #subtype} is {@link DeterministicSubtype#PATTERN}.
     * Callers in {@code agent-memory} can adapt {@code InMemoryPatternEngine}
     * via {@link PatternMatchStrategy}.
     */
    @Nullable PatternMatchStrategy patternMatchStrategy;

    /**
     * Input field that carries the free-text situation description used for
     * NFA pattern matching. Defaults to {@code "situation"}.
     */
    @lombok.Builder.Default
    @NotNull String patternSituationField = "situation";
}
