/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.pattern.engine.matching;

import com.ghatana.pattern.engine.nfa.NFA;
import com.ghatana.platform.domain.event.GEvent;

import java.util.Optional;

/**
 * Deterministic pattern matching strategy.
 * Uses exact state transitions with binary match/no-match outcomes.
 *
 * @doc.type class
 * @doc.purpose Deterministic pattern matching with exact state transitions
 * @doc.layer product
 * @doc.pattern Strategy
 */
public final class DeterministicMatchingStrategy implements PatternMatchingStrategy {

    private NFA.State currentState;

    public DeterministicMatchingStrategy() {
        this.currentState = null;
    }

    @Override
    public Optional<PatternMatch> evaluate(GEvent event, NFA nfa, MatchingContext context) {
        // Initialize state if needed
        if (currentState == null) {
            currentState = nfa.getInitialState();
        }

        // Find next state based on event
        Optional<NFA.State> nextState = findNextState(currentState, event, nfa);

        if (nextState.isPresent()) {
            currentState = nextState.get();

            // Check if we've reached an accepting state
            if (currentState.isAccepting()) {
                PatternMatch match = new PatternMatch(
                    nfa.getPatternName(),
                    1.0, // Deterministic = 100% confidence
                    List.of(event),
                    java.time.Instant.now(),
                    StrategyType.DETERMINISTIC
                );

                // Reset for next pattern
                currentState = nfa.getInitialState();
                return Optional.of(match);
            }

            return Optional.empty();
        }

        // No valid transition - reset to initial state
        currentState = nfa.getInitialState();
        return Optional.empty();
    }

    @Override
    public void reset() {
        currentState = null;
    }

    @Override
    public StrategyType getStrategyType() {
        return StrategyType.DETERMINISTIC;
    }

    private Optional<NFA.State> findNextState(NFA.State current, GEvent event, NFA nfa) {
        return nfa.getTransitions(current).stream()
            .filter(t -> t.matches(event))
            .map(NFA.Transition::getTarget)
            .findFirst();
    }
}
