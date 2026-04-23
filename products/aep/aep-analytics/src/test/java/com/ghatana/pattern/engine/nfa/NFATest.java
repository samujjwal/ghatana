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
    void addStateTracksAcceptingEndStates() { // GH-90000
        NFA nfa = new NFA("login-sequence");
        NFAState intermediate = new NFAState("s1", NFAStateType.INTERMEDIATE); // GH-90000
        NFAState end = new NFAState("end", NFAStateType.END); // GH-90000

        nfa.addState(intermediate); // GH-90000
        nfa.addState(end); // GH-90000

        assertEquals("login-sequence", nfa.getPatternName()); // GH-90000
        assertTrue(nfa.getAllStates().contains(intermediate)); // GH-90000
        assertTrue(nfa.getAllStates().contains(end)); // GH-90000
        assertTrue(nfa.getAcceptingStates().contains(end)); // GH-90000
        assertFalse(nfa.getAcceptingStates().contains(intermediate)); // GH-90000
    }

    @Test
    @DisplayName("epsilon closure includes transitively reachable states")
    void epsilonClosureIncludesTransitivelyReachableStates() { // GH-90000
        NFA nfa = new NFA("epsilon-chain");
        NFAState start = nfa.getStartState(); // GH-90000
        NFAState middle = new NFAState("middle", NFAStateType.INTERMEDIATE); // GH-90000
        NFAState end = new NFAState("end", NFAStateType.END); // GH-90000

        nfa.addState(middle); // GH-90000
        nfa.addState(end); // GH-90000
        nfa.addEpsilonTransition(start, middle); // GH-90000
        nfa.addEpsilonTransition(middle, end); // GH-90000

        Set<NFAState> closure = nfa.getEpsilonClosure(Set.of(start)); // GH-90000

        assertEquals(Set.of(start, middle, end), closure); // GH-90000
    }

    @Test
    @DisplayName("step follows matching transitions and applies epsilon closure")
    void stepFollowsMatchingTransitionsAndAppliesEpsilonClosure() { // GH-90000
        NFA nfa = new NFA("purchase-flow");
        NFAState start = nfa.getStartState(); // GH-90000
        NFAState matched = new NFAState("matched", NFAStateType.INTERMEDIATE); // GH-90000
        NFAState accepting = new NFAState("accepting", NFAStateType.END); // GH-90000

        nfa.addState(matched); // GH-90000
        nfa.addState(accepting); // GH-90000
        nfa.addTransition(start, matched, "login"); // GH-90000
        nfa.addEpsilonTransition(matched, accepting); // GH-90000

        Set<NFAState> nextStates = nfa.step(Set.of(start), "login", Instant.parse("2026-04-02T00:00:00Z"));

        assertEquals(Set.of(matched, accepting), nextStates); // GH-90000
        assertTrue(nfa.isAccepting(nextStates)); // GH-90000
        assertEquals(Set.of(), nfa.step(Set.of(start), "purchase", Instant.parse("2026-04-02T00:00:00Z")));
    }

    @Test
    @DisplayName("transitions expose configured constraints")
    void transitionsExposeConfiguredConstraints() { // GH-90000
        NFAState from = new NFAState("from", NFAStateType.START); // GH-90000
        NFAState to = new NFAState("to", NFAStateType.REPEAT); // GH-90000
        NFATransition transition = new NFATransition(from, to, "click"); // GH-90000

        transition.setNegationTransition(true); // GH-90000
        transition.setTimeConstraint(Duration.ofSeconds(30)); // GH-90000
        transition.setRepeatConstraint(2, 5); // GH-90000

        assertFalse(transition.isEpsilonTransition()); // GH-90000
        assertTrue(transition.isNegationTransition()); // GH-90000
        assertEquals(Duration.ofSeconds(30), transition.getTimeConstraint()); // GH-90000
        assertEquals(2, transition.getMinRepeat()); // GH-90000
        assertEquals(5, transition.getMaxRepeat()); // GH-90000
        assertTrue(transition.toString().contains("click"));
    }

    @Test
    @DisplayName("state equality is based on identifier")
    void stateEqualityIsBasedOnIdentifier() { // GH-90000
        NFAState left = new NFAState("same", NFAStateType.START); // GH-90000
        NFAState right = new NFAState("same", NFAStateType.END); // GH-90000
        NFAState other = new NFAState("other", NFAStateType.START); // GH-90000

        assertEquals(left, right); // GH-90000
        assertEquals(left.hashCode(), right.hashCode()); // GH-90000
        assertFalse(left.equals(other)); // GH-90000
        assertTrue(left.toString().contains("same"));
    }
}
