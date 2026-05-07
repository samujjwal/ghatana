/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.pattern.engine.matching;

import com.ghatana.pattern.engine.nfa.NFA;
import com.ghatana.pattern.engine.nfa.NFAState;
import com.ghatana.pattern.engine.nfa.NFATransition;
import com.ghatana.platform.domain.event.GEvent;

import java.util.List;
import java.util.Objects;
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

    private NFAState currentState;

    public DeterministicMatchingStrategy() {
        this.currentState = null;
    }

    @Override
    public Optional<PatternMatch> evaluate(GEvent event, NFA nfa, MatchingContext context) {
        // Initialize state if needed
        if (currentState == null) {
            currentState = nfa.getStartState();
        }

        // Find next state based on event
        Optional<NFAState> nextState = findNextState(currentState, event, nfa);

        if (nextState.isPresent()) {
            currentState = nextState.get();

            // Check if we've reached an accepting state
            if (nfa.getAcceptingStates().contains(currentState)) {
                PatternMatch match = new PatternMatch(
                    nfa.getPatternName(),
                    1.0, // Deterministic = 100% confidence
                    List.of(event),
                    java.time.Instant.now(),
                    StrategyType.DETERMINISTIC
                );

                // Reset for next pattern
                currentState = nfa.getStartState();
                return Optional.of(match);
            }

            return Optional.empty();
        }

        // No valid transition - reset to initial state
    currentState = nfa.getStartState();
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

    private Optional<NFAState> findNextState(NFAState current, GEvent event, NFA nfa) {
        return current.getTransitions().stream()
            .filter(t -> !t.isEpsilonTransition())
            .filter(t -> Objects.equals(t.getEventType(), event.getType()))
            .map(NFATransition::getToState)
            .findFirst();
    }
}
