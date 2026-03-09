package com.ghatana.pattern.engine.nfa;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a state in the NFA (Non-deterministic Finite Automaton).
 * 
 * Day 26 Implementation: NFA state with pattern matching capabilities
 */
public class NFAState {
    private final String id;
    private final NFAStateType type;
    private final List<NFATransition> transitions;
    
    public NFAState(String id, NFAStateType type) {
        this.id = id;
        this.type = type;
        this.transitions = new ArrayList<>();
    }
    
    public void addTransition(NFATransition transition) {
        transitions.add(transition);
    }
    
    public String getId() { return id; }
    public NFAStateType getType() { return type; }
    public List<NFATransition> getTransitions() { return transitions; }
    
    @Override
    public String toString() {
        return "NFAState{id='" + id + "', type=" + type + "}";
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        NFAState nfaState = (NFAState) obj;
        return id.equals(nfaState.id);
    }
    
    @Override
    public int hashCode() {
        return id.hashCode();
    }
}

