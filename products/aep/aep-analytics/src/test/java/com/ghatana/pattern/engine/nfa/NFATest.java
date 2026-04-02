package com.ghatana.pattern.engine.nfa;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("NFA Tests")
class NFATest {

    @Test
    @DisplayName("addState tracks accepting end states")
    void addStateTracksAcceptingEndStates() {
        NFA nfa = new NFA("login-sequence");
        NFAState intermediate = new NFAState("s1", NFAStateType.INTERMEDIATE);
        NFAState end = new NFAState("end", NFAStateType.END);

        nfa.addState(intermediate);
        nfa.addState(end);

        assertEquals("login-sequence", nfa.getPatternName());
        assertTrue(nfa.getAllStates().contains(intermediate));
        assertTrue(nfa.getAllStates().contains(end));
        assertTrue(nfa.getAcceptingStates().contains(end));
        assertFalse(nfa.getAcceptingStates().contains(intermediate));
    }

    @Test
    @DisplayName("epsilon closure includes transitively reachable states")
    void epsilonClosureIncludesTransitivelyReachableStates() {
        NFA nfa = new NFA("epsilon-chain");
        NFAState start = nfa.getStartState();
        NFAState middle = new NFAState("middle", NFAStateType.INTERMEDIATE);
        NFAState end = new NFAState("end", NFAStateType.END);

        nfa.addState(middle);
        nfa.addState(end);
        nfa.addEpsilonTransition(start, middle);
        nfa.addEpsilonTransition(middle, end);

        Set<NFAState> closure = nfa.getEpsilonClosure(Set.of(start));

        assertEquals(Set.of(start, middle, end), closure);
    }

    @Test
    @DisplayName("step follows matching transitions and applies epsilon closure")
    void stepFollowsMatchingTransitionsAndAppliesEpsilonClosure() {
        NFA nfa = new NFA("purchase-flow");
        NFAState start = nfa.getStartState();
        NFAState matched = new NFAState("matched", NFAStateType.INTERMEDIATE);
        NFAState accepting = new NFAState("accepting", NFAStateType.END);

        nfa.addState(matched);
        nfa.addState(accepting);
        nfa.addTransition(start, matched, "login");
        nfa.addEpsilonTransition(matched, accepting);

        Set<NFAState> nextStates = nfa.step(Set.of(start), "login", Instant.parse("2026-04-02T00:00:00Z"));

        assertEquals(Set.of(matched, accepting), nextStates);
        assertTrue(nfa.isAccepting(nextStates));
        assertEquals(Set.of(), nfa.step(Set.of(start), "purchase", Instant.parse("2026-04-02T00:00:00Z")));
    }

    @Test
    @DisplayName("transitions expose configured constraints")
    void transitionsExposeConfiguredConstraints() {
        NFAState from = new NFAState("from", NFAStateType.START);
        NFAState to = new NFAState("to", NFAStateType.REPEAT);
        NFATransition transition = new NFATransition(from, to, "click");

        transition.setNegationTransition(true);
        transition.setTimeConstraint(Duration.ofSeconds(30));
        transition.setRepeatConstraint(2, 5);

        assertFalse(transition.isEpsilonTransition());
        assertTrue(transition.isNegationTransition());
        assertEquals(Duration.ofSeconds(30), transition.getTimeConstraint());
        assertEquals(2, transition.getMinRepeat());
        assertEquals(5, transition.getMaxRepeat());
        assertTrue(transition.toString().contains("click"));
    }

    @Test
    @DisplayName("state equality is based on identifier")
    void stateEqualityIsBasedOnIdentifier() {
        NFAState left = new NFAState("same", NFAStateType.START);
        NFAState right = new NFAState("same", NFAStateType.END);
        NFAState other = new NFAState("other", NFAStateType.START);

        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertFalse(left.equals(other));
        assertTrue(left.toString().contains("same"));
    }
}