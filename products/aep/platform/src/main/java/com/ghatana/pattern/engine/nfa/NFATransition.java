package com.ghatana.pattern.engine.nfa;

import java.time.Duration;

/**
 * Represents a transition between NFA states.
 * 
 * Day 26 Implementation: NFA transition with event conditions and constraints
 */
public class NFATransition {
    private final NFAState fromState;
    private final NFAState toState;
    private final String eventType; // null for epsilon transitions
    
    // Additional constraints
    private boolean negationTransition = false;
    private Duration timeConstraint;
    private int minRepeat = -1;
    private int maxRepeat = -1;
    
    public NFATransition(NFAState fromState, NFAState toState, String eventType) {
        this.fromState = fromState;
        this.toState = toState;
        this.eventType = eventType;
    }
    
    // Getters
    public NFAState getFromState() { return fromState; }
    public NFAState getToState() { return toState; }
    public String getEventType() { return eventType; }
    public boolean isNegationTransition() { return negationTransition; }
    public Duration getTimeConstraint() { return timeConstraint; }
    public int getMinRepeat() { return minRepeat; }
    public int getMaxRepeat() { return maxRepeat; }
    
    // Setters for constraints
    public void setNegationTransition(boolean negationTransition) {
        this.negationTransition = negationTransition;
    }
    
    public void setTimeConstraint(Duration timeConstraint) {
        this.timeConstraint = timeConstraint;
    }
    
    public void setRepeatConstraint(int minRepeat, int maxRepeat) {
        this.minRepeat = minRepeat;
        this.maxRepeat = maxRepeat;
    }
    
    public boolean isEpsilonTransition() {
        return eventType == null;
    }
    
    @Override
    public String toString() {
        return "NFATransition{" +
                "from=" + fromState.getId() +
                ", to=" + toState.getId() +
                ", event='" + eventType + '\'' +
                '}';
    }
}