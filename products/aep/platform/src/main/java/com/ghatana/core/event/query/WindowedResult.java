package com.ghatana.core.event.query;


import com.ghatana.platform.domain.domain.event.Event;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the result of a windowed query, containing the events within a specific window
 * and any computed aggregations.
 */
public class WindowedResult {
    private final Instant windowStart;
    private final Instant windowEnd;
    private final List<Event> events;
    private final Map<String, Object> aggregations;
    private final Map<String, Object> metadata;

    /**
     * Creates a new windowed result.
     * 
     * @param windowStart The start time of the window (inclusive)
     * @param windowEnd The end time of the window (exclusive)
     * @param events The events in this window
     * @param aggregations Computed aggregations for this window
     * @param metadata Additional metadata about the window
     */
    /**
     * Creates a new windowed result with just the window bounds and events.
     * This is a convenience constructor that initializes empty aggregations and metadata.
     * 
     * @param windowStart The start time of the window (inclusive)
     * @param windowEnd The end time of the window (exclusive)
     * @param events The events in this window
     */
    public WindowedResult(Instant windowStart, Instant windowEnd, List<Event> events) {
        this(windowStart, windowEnd, events, Map.of(), Map.of());
    }
    
    /**
     * Creates a new windowed result.
     * 
     * @param windowStart The start time of the window (inclusive)
     * @param windowEnd The end time of the window (exclusive)
     * @param events The events in this window
     * @param aggregations Computed aggregations for this window
     * @param metadata Additional metadata about the window
     */
    public WindowedResult(
            Instant windowStart,
            Instant windowEnd,
            List<Event> events,
            Map<String, Object> aggregations,
            Map<String, Object> metadata) {
        
        this.windowStart = Objects.requireNonNull(windowStart, "windowStart cannot be null");
        this.windowEnd = Objects.requireNonNull(windowEnd, "windowEnd cannot be null");
        
        if (windowEnd.isBefore(windowStart)) {
            throw new IllegalArgumentException("windowEnd must be after windowStart");
        }
        
        this.events = events != null ? List.copyOf(events) : List.of();
        this.aggregations = aggregations != null ? Map.copyOf(aggregations) : Map.of();
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Creates a new windowed result with just the window bounds and events.
     */
    public static WindowedResult of(Instant windowStart, Instant windowEnd, List<Event> events) {
        return new WindowedResult(windowStart, windowEnd, events, null, null);
    }

    /**
     * Creates a new windowed result with aggregations.
     */
    public static WindowedResult withAggregations(
            Instant windowStart,
            Instant windowEnd,
            Map<String, Object> aggregations) {
        
        return new WindowedResult(windowStart, windowEnd, null, aggregations, null);
    }

    // Getters
    public Instant getWindowStart() {
        return windowStart;
    }

    public Instant getWindowEnd() {
        return windowEnd;
    }

    public List<Event> getEvents() {
        return events;
    }

    public Map<String, Object> getAggregations() {
        return aggregations;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Gets a specific aggregation value by name.
     * 
     * @param name The name of the aggregation
     * @return The aggregation value, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getAggregation(String name) {
        return (T) aggregations.get(name);
    }

    /**
     * Gets a specific metadata value by key.
     * 
     * @param key The metadata key
     * @return The metadata value, or null if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata(String key) {
        if (metadata.containsKey(key)) {
            return (T) metadata.get(key);
        } else {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WindowedResult that = (WindowedResult) o;
        return windowStart.equals(that.windowStart) &&
               windowEnd.equals(that.windowEnd) &&
               events.equals(that.events) &&
               aggregations.equals(that.aggregations) &&
               metadata.equals(that.metadata);
    }

    @Override
    public int hashCode() {
        return Objects.hash(windowStart, windowEnd, events, aggregations, metadata);
    }

    @Override
    public String toString() {
        return "WindowedResult{" +
               "windowStart=" + windowStart +
               ", windowEnd=" + windowEnd +
               ", eventCount=" + events.size() +
               ", aggregations=" + aggregations.keySet() +
               '}';
    }
}
