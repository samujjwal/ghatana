/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.deterministic;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Strategy interface for NFA-based pattern matching used by the PATTERN
 * subtype of {@link DeterministicAgent}.
 *
 * <p>This interface follows the Dependency-Inversion Principle: the
 * {@code agent-framework} module defines the contract here, and the
 * higher-level {@code agent-memory} module provides a concrete implementation
 * that adapts {@code InMemoryPatternEngine} to this interface. This avoids a
 * circular module dependency.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * PatternEngine nfa = new InMemoryPatternEngine();
 * PatternMatchStrategy strategy = (situation, context) -> nfa.match(situation, context)
 *     .map(r -> new PatternMatchStrategy.MatchResult(
 *             r.procedure().action(),
 *             r.procedure().steps().stream().map(ProcedureStep::description).toList(),
 *             r.matchScore(),
 *             r.procedure().id(),
 *             r.matchedKeywords()));
 *
 * DeterministicAgentConfig config = DeterministicAgentConfig.builder()
 *     .subtype(DeterministicSubtype.PATTERN)
 *     .patternMatchStrategy(strategy)
 *     .build();
 * }</pre>
 *
 * @since 2.4.0
 *
 * @doc.type interface
 * @doc.purpose Dependency-inversion bridge for NFA pattern matching in PATTERN agents
 * @doc.layer platform
 * @doc.pattern Strategy
 * @doc.gaa.lifecycle act
 */
@FunctionalInterface
public interface PatternMatchStrategy {

    /**
     * Attempts to find a matching procedure for the given situation.
     *
     * @param situation free-text description of the current situation
     * @param context   additional label key-value pairs for discriminating matches
     * @return {@link MatchResult} if a procedure matched, or empty to signal
     *         that the caller should fall back to LLM reasoning
     */
    @NotNull
    Optional<MatchResult> match(
            @NotNull String situation,
            @NotNull Map<String, String> context);

    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Result of a successful pattern match.
     *
     * @param action          the primary action string from the matched procedure
     * @param steps           ordered step descriptions (may be empty)
     * @param confidence      match confidence [0.0, 1.0]
     * @param procedureId     ID of the matched procedure (for traceability)
     * @param matchedKeywords keywords that triggered the match
     */
    record MatchResult(
            @NotNull String action,
            @NotNull List<String> steps,
            double confidence,
            @NotNull String procedureId,
            @NotNull List<String> matchedKeywords
    ) {}
}
