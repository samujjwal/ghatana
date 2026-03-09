package com.ghatana.pattern.engine.nfa;

/**
 * Types of NFA states for different pattern processing needs.
 */
public enum NFAStateType {
    START,          // Initial state
    END,            // Final/accepting state
    INTERMEDIATE,   // Regular processing state
    NEGATION,       // State for NOT patterns
    TIMED,          // State with time constraints
    REPEAT          // State for REPEAT patterns
}