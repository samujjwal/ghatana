/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.pattern.engine.matching;

import com.ghatana.pattern.engine.nfa.NFA;
import com.ghatana.platform.domain.event.GEvent;

import java.util.Optional;

/**
 * Strategy interface for different pattern matching approaches.
 * Supports deterministic, rule-based, and probabilistic rule-based matching.
 *
 * @doc.type interface
 * @doc.purpose Pattern matching strategy interface for flexible detection approaches
 * @doc.layer product
 * @doc.pattern Strategy
 */
public interface PatternMatchingStrategy {

    /**
     * Evaluates an event against the pattern using this strategy.
     *
     * @param event the event to evaluate
     * @param nfa the NFA defining the pattern
     * @param context optional matching context for stateful strategies
     * @return optional match result if pattern detected
     */
    Optional<PatternMatch> evaluate(GEvent event, NFA nfa, MatchingContext context);

    /**
     * Resets the strategy's internal state.
     */
    void reset();

    /**
     * Gets the strategy type identifier.
     *
     * @return strategy type (DETERMINISTIC, RULE_BASED, PROBABILISTIC_RULE_BASED)
     */
    StrategyType getStrategyType();

    /**
     * Strategy type enumeration.
     */
    enum StrategyType {
        DETERMINISTIC,
        RULE_BASED,
        PROBABILISTIC_RULE_BASED
    }

    /**
     * Match result containing pattern detection information.
     *
     * @param patternName the matched pattern name
     * @param confidence match confidence (0.0-1.0)
     * @param matchedEvents events that contributed to the match
     * @param matchTime when the match occurred
     * @param strategyType the strategy that produced the match
     */
    record PatternMatch(
        String patternName,
        double confidence,
        java.util.List<GEvent> matchedEvents,
        java.time.Instant matchTime,
        StrategyType strategyType
    ) {
        public PatternMatch {
            matchedEvents = java.util.List.copyOf(matchedEvents);
            confidence = Math.max(0.0, Math.min(1.0, confidence));
        }
    }

    /**
     * Context for stateful pattern matching.
     *
     * @param state current matching state
     * @param eventHistory history of events seen
     * @param metadata additional strategy-specific metadata
     */
    record MatchingContext(
        Object state,
        java.util.List<GEvent> eventHistory,
        java.util.Map<String, Object> metadata
    ) {
        public MatchingContext {
            eventHistory = java.util.List.copyOf(eventHistory);
            metadata = java.util.Map.copyOf(metadata);
        }

        public static MatchingContext empty() {
            return new MatchingContext(null, List.of(), Map.of());
        }
    }
}
