package com.ghatana.platform.domain.domain.pattern;

/**
 * Window types for event pattern detection and query windowing.
 * 
 * Defines how events should be grouped into windows for processing. Windows control
 * the temporal boundaries for pattern detection and aggregation operations. Different
 * windowing strategies optimize for different analytical scenarios.
 *
 * <h2>Window Categories</h2>
 * <ul>
 *   <li><b>Bounded Windows</b>: TUMBLING, SLIDING, SESSION - Finite duration windows for finite data</li>
 *   <li><b>Unbounded Windows</b>: GLOBAL - Single infinite window for streaming data</li>
 * </ul>
 *
 * <h2>Architecture Role</h2>
 * <p>
 * Used by the Pattern Execution Engine to control event grouping:
 * <ul>
 *   <li><b>Pattern Matching</b>: Defines temporal scope for pattern sequence detection</li>
 *   <li><b>Aggregation</b>: Controls how events are grouped for statistical computation</li>
 *   <li><b>Performance Tuning</b>: Window choice impacts memory and latency tradeoffs</li>
 * </ul>
 * </p>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Tumbling window: non-overlapping 5-minute buckets
 * WindowType window = WindowType.TUMBLING;
 * if (window.isBounded()) {
 *   // Process finite 5-minute window of events
 * }
 *
 * // Sliding window: overlapping analysis
 * WindowType sliding = WindowType.SLIDING;
 * if (sliding.supportsOverlap()) {
 *   // Multiple active windows with overlap
 * }
 *
 * // Session window: activity-based grouping
 * WindowType session = WindowType.SESSION;
 * // Events with gap < 30min grouped into same session
 *
 * // Global window: all-time analysis
 * WindowType global = WindowType.GLOBAL;
 * if (!global.isBounded()) {
 *   // Single window for entire event stream
 * }
 * }</pre>
 *
 * @doc.type enum
 * @doc.layer domain
 * @doc.purpose temporal event grouping strategy selector
 * @doc.pattern strategy (each window type encapsulates different grouping logic)
 * @doc.test-hints verify window strategy selection, validate overlap behavior, test bounded/unbounded classification
 *
 * @see com.ghatana.platform.domain.domain.pattern.PatternId
 * @see com.ghatana.platform.domain.domain.event.Event
 */
public enum WindowType {
    /**
     * Fixed-size, non-overlapping windows.
     * Example: 5-minute tumbling windows group events into consecutive 5-minute buckets.
     */
    TUMBLING("tumbling"),
    
    /**
     * Fixed-size, overlapping windows.
     * Example: 5-minute windows sliding every 1 minute create overlapping time ranges.
     */
    SLIDING("sliding"),
    
    /**
     * Session-based windows with inactivity timeout.
     * Example: Group events with gaps <= 30 minutes into same session window.
     */
    SESSION("session"),
    
    /**
     * Global window (no windowing).
     * All events belong to a single unbounded window.
     */
    GLOBAL("global");
    
    private final String value;
    
    WindowType(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    /**
     * Parse a string value to WindowType enum.
     * 
     * @param value The string value to parse
     * @return The corresponding WindowType, or null if not found
     */
    public static WindowType fromValue(String value) {
        if (value == null) {
            return null;
        }
        for (WindowType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }
    
    /**
     * Check if this window type supports overlapping windows.
     * 
     * @return true for SLIDING windows, false otherwise
     */
    public boolean supportsOverlap() {
        return this == SLIDING;
    }
    
    /**
     * Check if this window type is bounded (has finite duration).
     * 
     * @return true for TUMBLING, SLIDING, and SESSION; false for GLOBAL
     */
    public boolean isBounded() {
        return this != GLOBAL;
    }
}
