package com.ghatana.core.pattern.learning;

/**
 * Types of learned patterns discovered by the learning engine.
 *
 * @doc.type enum
 * @doc.purpose Learned pattern type classification
 * @doc.layer core
 * @doc.pattern Learning
 */
public enum LearnedPatternType {
    /**
     * Sequential patterns discovered from event sequences.
     */
    SEQUENTIAL("Sequential", "Patterns based on event order and timing"),
    
    /**
     * Frequency patterns based on event occurrence rates.
     */
    FREQUENCY("Frequency", "Patterns based on event frequency and timing"),
    
    /**
     * Correlation patterns between different event types.
     */
    CORRELATION("Correlation", "Patterns based on event co-occurrence and relationships"),
    
    /**
     * Anomaly patterns representing unusual event behavior.
     */
    ANOMALY("Anomaly", "Patterns representing deviations from normal behavior"),
    
    /**
     * Temporal patterns based on time-based event relationships.
     */
    TEMPORAL("Temporal", "Patterns based on temporal relationships and cycles"),
    
    /**
     * Behavioral patterns representing user or system behavior.
     */
    BEHAVIORAL("Behavioral", "Patterns representing characteristic behaviors"),
    
    /**
     * Predictive patterns that can forecast future events.
     */
    PREDICTIVE("Predictive", "Patterns that can predict future events or states"),
    
    /**
     * Composite patterns combining multiple pattern types.
     */
    COMPOSITE("Composite", "Complex patterns combining multiple sub-patterns");

    private final String displayName;
    private final String description;

    LearnedPatternType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    @Override
    public String toString() {
        return displayName;
    }
}
