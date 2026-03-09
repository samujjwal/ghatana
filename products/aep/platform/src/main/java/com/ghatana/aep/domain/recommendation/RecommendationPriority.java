package com.ghatana.aep.domain.recommendation;

/**
 * Unified recommendation priority levels across all recommendation domains.
 * 
 * Defines ordinal priority levels for system recommendations. Supports both pattern learning
 * and cache optimization priorities. Priorities are numerically ordered (1-4) enabling
 * consistent comparison and sorting across recommendation engines.
 *
 * <h2>Priority Levels</h2>
 * <ul>
 *   <li><b>CRITICAL (4)</b>: Immediate action required - system health at risk</li>
 *   <li><b>HIGH (3)</b>: Important improvement - significant performance impact</li>
 *   <li><b>MEDIUM (2)</b>: Valuable optimization - moderate performance gain</li>
 *   <li><b>LOW (1)</b>: Minor enhancement - minimal performance impact</li>
 * </ul>
 *
 * <h2>Architecture Role</h2>
 * <p>
 * Used by Recommendation Engine for prioritization:
 * <ul>
 *   <li><b>Pattern Learning</b>: Prioritizes which patterns need tuning/optimization</li>
 *   <li><b>Cache Optimization</b>: Orders which cache strategies to implement first</li>
 *   <li><b>Resource Allocation</b>: Guides which recommendations consume execution time first</li>
 *   <li><b>User Visibility</b>: Determines recommendation ordering in UI and reports</li>
 * </ul>
 * </p>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Comparing priorities
 * RecommendationPriority p1 = RecommendationPriority.HIGH;
 * RecommendationPriority p2 = RecommendationPriority.MEDIUM;
 *
 * if (p1.isHigherThan(p2)) {
 *   // Process HIGH priority recommendation first
 * }
 *
 * // Numeric comparison
 * int diff = p1.compareLevel(p2);
 * if (diff > 0) {
 *   // p1 has higher priority
 * }
 *
 * // Parse from external system
 * RecommendationPriority parsed = RecommendationPriority.fromValue("critical");
 * if (parsed == RecommendationPriority.CRITICAL) {
 *   // Handle critical priority
 * }
 * }</pre>
 *
 * @doc.type enum
 * @doc.layer domain
 * @doc.purpose recommendation severity and urgency classifier
 * @doc.pattern value-object (immutable, ordinal enum with numeric levels)
 * @doc.test-hints verify priority ordering, test priority comparison, validate level calculations
 *
 * @see com.ghatana.platform.domain.domain.recommendation.RecommendationType
 * @see com.ghatana.platform.domain.domain.Priority (similar priority pattern in event domain)
 */
public enum RecommendationPriority {
    
    CRITICAL("critical", 4),
    HIGH("high", 3),
    MEDIUM("medium", 2),
    LOW("low", 1);
    
    private final String value;
    private final int level;
    
    RecommendationPriority(String value, int level) {
        this.value = value;
        this.level = level;
    }
    
    public String getValue() {
        return value;
    }
    
    public int getLevel() {
        return level;
    }
    
    /**
     * Parse a string value to RecommendationPriority enum.
     * 
     * @param value The string value to parse
     * @return The corresponding RecommendationPriority, or null if not found
     */
    public static RecommendationPriority fromValue(String value) {
        for (RecommendationPriority priority : values()) {
            if (priority.value.equals(value)) {
                return priority;
            }
        }
        return null;
    }
    
    /**
     * Compare priority levels numerically.
     * 
     * @param other The priority to compare to
     * @return negative if this < other, zero if equal, positive if this > other
     */
    public int compareLevel(RecommendationPriority other) {
        return Integer.compare(this.level, other.level);
    }
    
    /**
     * Check if this priority is higher than another.
     * 
     * @param other The priority to compare to
     * @return true if this priority is higher
     */
    public boolean isHigherThan(RecommendationPriority other) {
        return this.level > other.level;
    }
}
