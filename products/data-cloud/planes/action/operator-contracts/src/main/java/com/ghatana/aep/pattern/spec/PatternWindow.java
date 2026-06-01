package com.ghatana.aep.pattern.spec;

import java.util.Map;
import java.util.Objects;

/**
 * Canonical window specification for PatternSpec.
 *
 * <p>Defines temporal windowing semantics for pattern matching,
 * including time windows, count windows, and session windows.
 *
 * @doc.type record
 * @doc.purpose Canonical window specification for PatternSpec
 * @doc.layer product
 * @doc.pattern Model
 */
public record PatternWindow(
        String type,
        String size,
        String slide,
        String grace,
        Map<String, Object> parameters) {

    public PatternWindow {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(size, "size");
    }

    /**
     * Create a time-based window.
     *
     * @param size window size (e.g., "5m", "1h")
     * @return PatternWindow instance
     */
    public static PatternWindow timeWindow(String size) {
        return new PatternWindow("time", size, null, null, null);
    }

    /**
     * Create a time-based window with slide interval.
     *
     * @param size window size (e.g., "5m", "1h")
     * @param slide slide interval (e.g., "1m", "10m")
     * @return PatternWindow instance
     */
    public static PatternWindow slidingTimeWindow(String size, String slide) {
        return new PatternWindow("time", size, slide, null, null);
    }

    /**
     * Create a count-based window.
     *
     * @param size window size (number of events)
     * @return PatternWindow instance
     */
    public static PatternWindow countWindow(int size) {
        return new PatternWindow("count", String.valueOf(size), null, null, null);
    }

    /**
     * Create a session window.
     *
     * @param timeout session timeout (e.g., "30m")
     * @return PatternWindow instance
     */
    public static PatternWindow sessionWindow(String timeout) {
        return new PatternWindow("session", timeout, null, null, null);
    }

    /**
     * Convert this PatternWindow to a map representation.
     *
     * @return map representation
     */
    public Map<String, Object> toMap() {
        java.util.HashMap<String, Object> map = new java.util.HashMap<>();
        map.put("type", type);
        map.put("size", size);
        if (slide != null) map.put("slide", slide);
        if (grace != null) map.put("grace", grace);
        if (parameters != null && !parameters.isEmpty()) map.put("parameters", parameters);
        return java.util.Collections.unmodifiableMap(map);
    }
}
