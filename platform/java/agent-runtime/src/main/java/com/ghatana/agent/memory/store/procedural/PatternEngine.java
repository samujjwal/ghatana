/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */

package com.ghatana.agent.memory.store.procedural;

import com.ghatana.agent.memory.model.procedure.EnhancedProcedure;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pre-compiled policy index for O(1) pattern matching on procedural memory.
 *
 * <p>The PatternEngine is the "reflex layer" of the agent — it provides
 * sub-millisecond pattern matching against the agent's procedural memory
 * without requiring an LLM round-trip. Only when no matching pattern is
 * found does the system fall back to full LLM reasoning.
 *
 * <h2>Architecture</h2>
 * <pre>{@code
 * Incoming Situation
 *     │
 *     ▼
 * ┌──────────────────┐
 * │  PatternEngine    │ ← O(1) index lookup
 * │  (Reflex Layer)   │
 * └──────┬───────────┘
 *        │
 *   ┌────┴────┐
 *   │ Match?  │
 *   └────┬────┘
 *     Yes│    No
 *        │     └──► [LLM Reasoning Pipeline]
 *        ▼
 *   Execute Matched
 *   Procedure Directly
 * }</pre>
 *
 * <h2>Index Strategy</h2>
 * <ul>
 *   <li><b>Exact match</b>: Situation keywords hashed to procedure IDs</li>
 *   <li><b>Label match</b>: Procedures indexed by label key-value pairs</li>
 *   <li><b>Confidence filter</b>: Only high-confidence procedures (≥ threshold) are indexed</li>
 *   <li><b>Hot reload</b>: Index is rebuilt when procedures are added/updated</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Reflex-layer pattern matching engine
 * @doc.layer agent-memory
 * @doc.pattern Strategy / Index
 *
 * @since 2.4.0
 */
public interface PatternEngine {

    /**
     * Attempts O(1) pattern match against indexed procedures.
     *
     * @param situation the current situation description
     * @param context   additional context labels for matching
     * @return matching procedure if found, empty if no match (fall back to LLM)
     */
    @NotNull
    Optional<MatchResult> match(
            @NotNull String situation,
            @NotNull Map<String, String> context);

    /**
     * Indexes a procedure for fast matching. Called when a procedure is
     * stored or updated in the procedural memory.
     *
     * @param procedure the procedure to index
     */
    void index(@NotNull EnhancedProcedure procedure);

    /**
     * Removes a procedure from the index.
     *
     * @param procedureId the procedure to de-index
     */
    void deindex(@NotNull String procedureId);

    /**
     * Rebuilds the entire index from the given procedures.
     *
     * @param procedures all procedures to index
     * @return promise completing when rebuild is done
     */
    @NotNull
    Promise<Void> rebuild(@NotNull List<EnhancedProcedure> procedures);

    /**
     * Returns statistics about the current index state.
     *
     * @return index statistics
     */
    @NotNull IndexStats getStats();

    // ═══════════════════════════════════════════════════════════════════════════
    // Inner Types
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Result of a successful pattern match.
     *
     * @param procedure       the matched procedure
     * @param matchScore      how well the situation matched (0.0 - 1.0)
     * @param matchType       how the match was achieved
     * @param matchedKeywords keywords that triggered the match
     */
    record MatchResult(
            @NotNull EnhancedProcedure procedure,
            double matchScore,
            @NotNull MatchType matchType,
            @NotNull List<String> matchedKeywords
    ) {}

    /** How the pattern was matched. */
    enum MatchType {
        /** Exact keyword/situation match. */
        EXACT,
        /** Matched via label key-value pairs. */
        LABEL,
        /** Matched via partial keyword overlap. */
        PARTIAL
    }

    /**
     * Statistics about the pattern engine index.
     *
     * @param indexedProcedures total procedures in the index
     * @param indexedKeywords   total unique keywords indexed
     * @param indexedLabels     total unique label combinations indexed
     * @param lastRebuildMs    time of last full rebuild in milliseconds
     */
    record IndexStats(
            int indexedProcedures,
            int indexedKeywords,
            int indexedLabels,
            long lastRebuildMs
    ) {}
}
