package com.ghatana.platform.domain.domain.pattern;

/**
 * Defines how pattern matches should be selected when multiple matches are possible.
 * 
 * Enumeration controlling match selection strategy in event pattern detection and correlation.
 * Enables flexible pattern matching semantics for different use cases and correlation requirements.
 * 
 * Selection Strategies:
 * - ALL: Collect all possible matches (comprehensive detection)
 * - FIRST: Return only first match found (early exit optimization)
 * - LAST: Return only last match found (most recent match)
 * - MAX_CONFIDENCE: Select highest confidence match (quality-based)
 * - SKIP_PAST_END: Filter out matches extending beyond stream (boundary handling)
 * - CHRONOLOGICAL: Preserve time order ascending (temporal sequencing)
 * - REVERSE_CHRONOLOGICAL: Reverse time order (newest-first analysis)
 * 
 * Architecture Role:
 * - Used by: Pattern matching engines, correlation rules, event processors
 * - Created by: Pattern definitions, rule configurations
 * - Stored in: Pattern definitions, correlation rule metadata
 * - Purpose: Control match cardinality and ordering in pattern results
 * 
 * Usage Patterns:
 * {@code
 * SelectionMode mode = SelectionMode.ALL;  // Get all matches
 * SelectionMode high = SelectionMode.MAX_CONFIDENCE;  // Best match
 * 
 * // Parse from string (e.g., from config)
 * SelectionMode parsed = SelectionMode.fromValue("chronological");
 * 
 * // Test capabilities
 * if (mode.allowsMultipleMatches()) { ... }
 * if (mode.requiresConfidence()) { ... }
 * }
 * 
 * Thread Safety: Enum constants are immutable and thread-safe.
 * Performance: O(1) for all operations including fromValue() lookup.
 * 
 * @doc.type enum
 * @doc.layer domain
 * @doc.purpose type-safe pattern match selection strategy specification
 * @doc.pattern enum-with-methods
 * @doc.test-hints test fromValue(), allowsMultipleMatches(), requiresConfidence()
 * @see EventParameterType (related enum for event data types)
 */
public enum SelectionMode {
    
    /**
     * Return all possible matches.
     */
    ALL("all"),
    
    /**
     * Return only the first match found.
     */
    FIRST("first"),
    
    /**
     * Return only the last match found.
     */
    LAST("last"),
    
    /**
     * Return the match with the highest confidence score.
     */
    MAX_CONFIDENCE("maxConfidence"),
    
    /**
     * Skip matches that extend past the end of the event stream.
     */
    SKIP_PAST_END("skipPastEnd"),
    
    /**
     * Return matches in chronological order.
     */
    CHRONOLOGICAL("chronological"),
    
    /**
     * Return matches in reverse chronological order.
     */
    REVERSE_CHRONOLOGICAL("reverseChronological");
    
    private final String value;
    
    SelectionMode(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Parse a string value to SelectionMode enum.
     * 
     * @param value The string value to parse
     * @return The corresponding SelectionMode, or null if not found
     */
    public static SelectionMode fromValue(String value) {
        for (SelectionMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        return null;
    }
    
    /**
     * Check if this selection mode allows multiple matches.
     * 
     * @return true if multiple matches are allowed
     */
    public boolean allowsMultipleMatches() {
        return this == ALL || this == CHRONOLOGICAL || this == REVERSE_CHRONOLOGICAL;
    }
    
    /**
     * Check if this selection mode requires confidence scoring.
     * 
     * @return true if confidence scoring is required
     */
    public boolean requiresConfidence() {
        return this == MAX_CONFIDENCE;
    }
}

