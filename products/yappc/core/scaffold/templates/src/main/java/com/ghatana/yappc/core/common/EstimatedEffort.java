package com.ghatana.yappc.core.common;

/**
 * Estimated effort levels for tasks, improvements, and remediation actions.
 * 
 * <p>This enum consolidates effort estimation from multiple sources:
 * <ul>
 *   <li>BuildScriptImprovement - effort to implement build improvements</li>
 *   <li>RCAResult - effort required for root cause remediation</li>
 * </ul>
 *
 * @doc.type enum
 * @doc.purpose Estimated effort levels for tasks, improvements, and remediation actions.
 * @doc.layer platform
 * @doc.pattern Enumeration
 */
public enum EstimatedEffort {
    /**
     * Minimal effort - quick fixes, simple changes (< 1 hour).
     */
    MINIMAL,
    
    /**
     * Low effort - straightforward tasks (1-4 hours).
     */
    LOW,
    
    /**
     * Medium effort - moderate complexity (4-8 hours or 1 day).
     */
    MEDIUM,
    
    /**
     * High effort - complex tasks requiring significant work (1-3 days).
     */
    HIGH,
    
    /**
     * Very high effort - major undertakings (3+ days).
     */
    VERY_HIGH;
    
    /**
     * Checks if this effort level is at least as high as the given level.
     * 
     * @param other the effort level to compare against
     * @return true if this effort is >= other
     */
    public boolean isAtLeast(EstimatedEffort other) {
        return this.ordinal() >= other.ordinal();
    }
    
    /**
     * Returns a human-readable description of the effort level.
     * 
     * @return effort description
     */
    public String getDescription() {
        return switch (this) {
            case MINIMAL -> "Quick fix (< 1 hour)";
            case LOW -> "Straightforward task (1-4 hours)";
            case MEDIUM -> "Moderate complexity (4-8 hours)";
            case HIGH -> "Complex task (1-3 days)";
            case VERY_HIGH -> "Major undertaking (3+ days)";
        };
    }
}
