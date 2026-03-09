package com.ghatana.core.event.query;

import com.ghatana.aep.domain.pattern.WindowType;
import lombok.Data;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Defines the windowing behavior for queries, allowing events to be grouped into finite
 * sets for processing. This class supports three types of windows:
 * 
 * <ul>
 *   <li><b>Tumbling Windows</b>: Fixed-size, non-overlapping windows. Example: 5-minute tumbling windows</li>
 *   <li><b>Sliding Windows</b>: Fixed-size, overlapping windows. Example: 5-minute windows sliding every 1 minute</li>
 *   <li><b>Session Windows</b>: Variable-sized windows based on gaps between events. Example: Session windows with 30-minute inactivity gap</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * // Tumbling window: 5-minute non-overlapping windows
 * WindowSpec tumblingWindow = WindowSpec.tumbling(Duration.ofMinutes(5));
 *
 * // Sliding window: 5-minute windows sliding every 1 minute
 * WindowSpec slidingWindow = WindowSpec.sliding(
 *     Duration.ofMinutes(5),
 *     Duration.ofMinutes(1)
 * );
 *
 * // Session window: group events with gaps <= 30 minutes
 * WindowSpec sessionWindow = WindowSpec.session(Duration.ofMinutes(30));
 * }</pre>
 *
 * @see WindowedResult
 */
@Data
@SuperBuilder
public class WindowSpec {
    private final WindowType type;
    private final Duration size;
    private final Duration slide; // Only used for SLIDING windows
    private final Duration sessionGap; // Only used for SESSION windows

    private WindowSpec(WindowType type, Duration size, Duration slide, Duration sessionGap) {
        this.type = type;
        if (size != null) {
            this.size = size;
        } else {
            this.size = null;
        }
        if (slide != null) {
            this.slide = slide;
        } else {
            this.slide = null;
        }
        this.sessionGap = sessionGap;
    }

    /**
     * Creates a tumbling window specification.
     * 
     * @param size The fixed size of each window
     * @return A new tumbling window specification
     */
    public static WindowSpec tumbling(Duration size) {
        if (size == null || size.isZero() || size.isNegative()) {
            throw new IllegalArgumentException("Window size must be positive");
        }
        return new WindowSpec(WindowType.TUMBLING, size, null, null);
    }

    /**
     * Creates a sliding window specification.
     * 
     * @param size The size of each window
     * @param slide The slide interval (must be positive and &lt;= size)
     * @return A new sliding window specification
     */
    public static WindowSpec sliding(Duration size, Duration slide) {
        if (size == null || size.isZero() || size.isNegative()) {
            throw new IllegalArgumentException("Window size must be positive");
        }
        if (slide == null || slide.isZero() || slide.isNegative()) {
            throw new IllegalArgumentException("Slide must be positive");
        }
        if (slide.compareTo(size) > 0) {
            throw new IllegalArgumentException("Slide must be less than or equal to window size");
        }
        return new WindowSpec(WindowType.SLIDING, size, slide, null);
    }

    /**
     * Creates a session window specification.
     * 
     * @param gap The maximum gap between events in the same session
     * @return A new session window specification
     */
    public static WindowSpec session(Duration gap) {
        if (gap == null || gap.isNegative()) {
            throw new IllegalArgumentException("Gap must be non-negative");
        }
        return new WindowSpec(WindowType.SESSION, null, null, gap);
    }
    
    /**
     * Gets the start time of the window that contains the given timestamp.
     * 
     * @param timestamp The timestamp to find the window for
     * @return The start time of the window (inclusive)
     */
    public Instant getWindowStart(Instant timestamp) {
        Objects.requireNonNull(timestamp, "Timestamp cannot be null");
        
        switch (type) {
            case TUMBLING:
            case SLIDING:
                // For tumbling and sliding windows, align to the nearest window boundary
                long windowSizeMillis = size.toMillis();
                long timestampMillis = timestamp.toEpochMilli();
                long windowStartMillis = (timestampMillis / windowSizeMillis) * windowSizeMillis;
                return Instant.ofEpochMilli(windowStartMillis);
                
            case SESSION:
                // For session windows, the start time is the timestamp itself
                // The actual window is determined by the session gap
                return timestamp;
                
            default:
                throw new UnsupportedOperationException("Unsupported window type: " + type);
        }
    }
    
    /**
     * Gets the end time of the window that starts at the given timestamp.
     * 
     * @param windowStart The start time of the window
     * @return The end time of the window (exclusive)
     */
    public Instant getWindowEnd(Instant windowStart) {
        Objects.requireNonNull(windowStart, "Window start cannot be null");
        
        switch (type) {
            case TUMBLING:
                // For tumbling windows, the window ends at windowStart + size
                return windowStart.plus(size);
                
            case SLIDING:
                // For sliding windows, the window ends at windowStart + size
                return windowStart.plus(size);
                
            case SESSION:
                // For session windows, the window ends at windowStart + sessionGap
                return windowStart.plus(sessionGap);
                
            default:
                throw new UnsupportedOperationException("Unsupported window type: " + type);
        }
    }

    // Getters
    public WindowType getType() {
        return type;
    }

    public Duration getSize() {
        if (type == WindowType.SESSION) {
            throw new UnsupportedOperationException("Session windows don't have a fixed size");
        }
        return size;
    }

    public Duration getSlide() {
        if (type != WindowType.SLIDING) {
            throw new UnsupportedOperationException("Only sliding windows have a slide");
        }
        return slide;
    }

    public Duration getSessionGap() {
        if (type != WindowType.SESSION) {
            throw new UnsupportedOperationException("Only session windows have a session gap");
        }
        return sessionGap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        WindowSpec that = (WindowSpec) o;
        return type == that.type &&
               Objects.equals(size, that.size) &&
               Objects.equals(slide, that.slide) &&
               Objects.equals(sessionGap, that.sessionGap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, size, slide, sessionGap);
    }

    @Override
    public String toString() {
        switch (type) {
            case TUMBLING:
                return "TumblingWindow(size=" + size + ")";
            case SLIDING:
                return "SlidingWindow(size=" + size + ", slide=" + slide + ")";
            case SESSION:
                return "SessionWindow(gap=" + sessionGap + ")";
            default:
                return "WindowSpec{type=" + type + "}";
        }
    }
}
