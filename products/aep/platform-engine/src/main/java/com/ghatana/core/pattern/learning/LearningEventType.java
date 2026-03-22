package com.ghatana.core.pattern.learning;

/**
 * Types of learning events.
 *
 * @doc.type enum
 * @doc.purpose Learning event type classification
 * @doc.layer core
 * @doc.pattern Learning
 */
public enum LearningEventType {
    /**
     * Learning engine started.
     */
    ENGINE_STARTED("Engine Started", "Pattern learning engine has started"),
    
    /**
     * Learning engine stopped.
     */
    ENGINE_STOPPED("Engine Stopped", "Pattern learning engine has stopped"),
    
    /**
     * New pattern discovered.
     */
    PATTERN_DISCOVERED("Pattern Discovered", "A new pattern has been discovered"),
    
    /**
     * Pattern optimized.
     */
    PATTERN_OPTIMIZED("Pattern Optimized", "An existing pattern has been optimized"),
    
    /**
     * Pattern evolved.
     */
    PATTERN_EVOLVED("Pattern Evolved", "A pattern has evolved through genetic algorithms"),
    
    /**
     * Learning cycle completed.
     */
    LEARNING_CYCLE_COMPLETED("Learning Cycle Completed", "A learning cycle has completed"),
    
    /**
     * Error occurred during learning.
     */
    LEARNING_ERROR("Learning Error", "An error occurred during pattern learning"),
    
    /**
     * Threshold reached.
     */
    THRESHOLD_REACHED("Threshold Reached", "A learning threshold has been reached");

    private final String displayName;
    private final String description;

    LearningEventType(String displayName, String description) {
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
