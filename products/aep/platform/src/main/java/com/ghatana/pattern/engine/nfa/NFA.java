package com.ghatana.pattern.engine.nfa;

import java.time.Instant;
import java.util.*;

/**
 * Represents a complete Non-deterministic Finite Automaton for pattern matching.
 * 
 * Day 26 Implementation: Core NFA structure with state management
 */
public class NFA {
    private final NFAState startState;
    private final Set<NFAState> acceptingStates;
    private final Set<NFAState> allStates;
    private final String patternName;
    
    public NFA(String patternName) {
        this.patternName = patternName;
        this.startState = new NFAState("start", NFAStateType.START);
        this.acceptingStates = new HashSet<>();
        this.allStates = new HashSet<>();
        allStates.add(startState);
    }
    
    public NFAState getStartState() {
        return startState;
    }
    
    public Set<NFAState> getAcceptingStates() {
        return Collections.unmodifiableSet(acceptingStates);
    }
    
    public Set<NFAState> getAllStates() {
        return Collections.unmodifiableSet(allStates);
    }
    
    public String getPatternName() {
        return patternName;
    }
    
    public void addState(NFAState state) {
        allStates.add(state);
        if (state.getType() == NFAStateType.END) {
            acceptingStates.add(state);
        }
    }
    
    public void addTransition(NFAState from, NFAState to, String eventType) {
        NFATransition transition = new NFATransition(from, to, eventType);
        from.addTransition(transition);
    }
    
    public void addEpsilonTransition(NFAState from, NFAState to) {
        NFATransition epsilonTransition = new NFATransition(from, to, null);
        from.addTransition(epsilonTransition);
    }
    
    /**
     * Get all states reachable via epsilon transitions from the given states.
     */
    public Set<NFAState> getEpsilonClosure(Set<NFAState> states) {
        Set<NFAState> closure = new HashSet<>(states);
        Queue<NFAState> queue = new LinkedList<>(states);
        
        while (!queue.isEmpty()) {
            NFAState current = queue.poll();
            for (NFATransition transition : current.getTransitions()) {
                if (transition.isEpsilonTransition()) {
                    NFAState target = transition.getToState();
                    if (!closure.contains(target)) {
                        closure.add(target);
                        queue.offer(target);
                    }
                }
            }
        }
        
        return closure;
    }
    
    /**
     * Execute one step of the NFA given current states and an event.
     */
    public Set<NFAState> step(Set<NFAState> currentStates, String eventType, Instant timestamp) {
        Set<NFAState> nextStates = new HashSet<>();
        
        for (NFAState state : currentStates) {
            for (NFATransition transition : state.getTransitions()) {
                if (!transition.isEpsilonTransition() && 
                    Objects.equals(transition.getEventType(), eventType)) {
                    
                    // Check time constraints if present
                    if (transition.getTimeConstraint() != null) {
                        // Time constraint checking would be implemented here
                        // For now, we accept all transitions
                    }
                    
                    nextStates.add(transition.getToState());
                }
            }
        }
        
        // Include epsilon closure of next states
        return getEpsilonClosure(nextStates);
    }
    
    public boolean isAccepting(Set<NFAState> states) {
        return states.stream().anyMatch(state -> acceptingStates.contains(state));
    }
    
    @Override
    public String toString() {
        return "NFA{" +
                "patternName='" + patternName + '\'' +
                ", states=" + allStates.size() +
                ", acceptingStates=" + acceptingStates.size() +
                '}';
    }
}