package com.ghatana.pattern.engine.agent.operators;

import com.ghatana.core.operator.OperatorConfig;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Windowed stream processing operator for the pattern engine.
 *
 * <p>Accumulates events within configurable time windows and produces aggregate
 * results when windows close. Supports tumbling windows (non-overlapping, fixed-size)
 * and sliding windows (overlapping, with a slide interval).</p>
 *
 * <h2>Window Types</h2>
 * <ul>
 *   <li><b>Tumbling</b>: Fixed-size, non-overlapping windows. Events belong to exactly one window.</li>
 *   <li><b>Sliding</b>: Fixed-size windows that advance by a slide interval. Events may appear in multiple windows.</li>
 * </ul>
 *
 * <h2>Processing Model</h2>
 * <ol>
 *   <li>Each incoming event is buffered in the current window</li>
 *   <li>When the window duration elapses, the aggregator function is called with all buffered events</li>
 *   <li>The aggregator produces zero or more output events</li>
 *   <li>The buffer is cleared for the next window (or trimmed for sliding)</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   StreamOperator countWindow = StreamOperator.builder()
 *       .operatorId(OperatorId.of("ghatana", "pattern", "count-per-minute", "1.0"))
 *       .name("Events Per Minute")
 *       .windowDuration(Duration.ofMinutes(1))
 *       .aggregator(events -> {
 *           Map<String, Object> payload = Map.of("count", events.size());
 *           return List.of(GEvent.builder()
 *               .type("metric.event.count")
 *               .payload(payload)
 *               .build());
 *       })
 *       .build();
 * }</pre>
 */
public class StreamOperator extends BaseOperator {

    private static final String CAPABILITY_WINDOWED = "windowed-processing";
    private static final String CAPABILITY_AGGREGATION = "stream-aggregation";

    private static final Duration DEFAULT_WINDOW_DURATION = Duration.ofMinutes(1);
    private static final int DEFAULT_MAX_BUFFER_SIZE = 10_000;

    private final Duration windowDuration;
    private final Duration slideDuration; // null = tumbling window
    private final int maxBufferSize;
    private final Function<List<GEvent>, List<Event>> aggregator;

    // ─── Mutable State ───────────────────────────────────────────────────────────
    private final ConcurrentLinkedDeque<TimestampedEvent> eventBuffer;
    private final AtomicReference<Instant> windowOpenedAt;
    private final AtomicLong windowCount;
    private final AtomicLong eventsBuffered;
    private final AtomicLong eventsDropped;

    private record TimestampedEvent(GEvent event, Instant receivedAt) {}

    private StreamOperator(
            OperatorId id,
            String name,
            String description,
            Set<String> acceptedEventTypes,
            Duration windowDuration,
            Duration slideDuration,
            int maxBufferSize,
            Function<List<GEvent>, List<Event>> aggregator,
            MetricsCollector metricsCollector) {

        super(id, name,
                description != null ? description : "Windowed stream operator (" + windowDuration + ")",
                List.of(CAPABILITY_WINDOWED, CAPABILITY_AGGREGATION),
                acceptedEventTypes, metricsCollector);

        this.windowDuration = windowDuration != null ? windowDuration : DEFAULT_WINDOW_DURATION;
        this.slideDuration = slideDuration;
        this.maxBufferSize = maxBufferSize > 0 ? maxBufferSize : DEFAULT_MAX_BUFFER_SIZE;
        this.aggregator = Objects.requireNonNull(aggregator, "Aggregator function must not be null");

        this.eventBuffer = new ConcurrentLinkedDeque<>();
        this.windowOpenedAt = new AtomicReference<>(Instant.now());
        this.windowCount = new AtomicLong(0);
        this.eventsBuffered = new AtomicLong(0);
        this.eventsDropped = new AtomicLong(0);
    }

    @Override
    protected Promise<Void> doStart() {
        eventBuffer.clear();
        windowOpenedAt.set(Instant.now());
        windowCount.set(0);
        eventsBuffered.set(0);
        eventsDropped.set(0);
        return Promise.complete();
    }

    @Override
    protected Promise<Void> doStop() {
        eventBuffer.clear();
        return Promise.complete();
    }

    /**
     * Buffers the event and checks if the window should close.
     * If the window closes, runs the aggregator on buffered events and returns the result.
     * Otherwise returns empty (event is buffered for later aggregation).
     */
    @Override
    protected OperatorResult doProcessEvent(GEvent event) {
        Instant now = Instant.now();

        // Buffer the event (with size limit)
        if (eventBuffer.size() >= maxBufferSize) {
            // Drop oldest event to make room
            eventBuffer.pollFirst();
            eventsDropped.incrementAndGet();
        }
        eventBuffer.addLast(new TimestampedEvent(event, now));
        eventsBuffered.incrementAndGet();

        // Check window expiry
        Instant windowStart = windowOpenedAt.get();
        if (Duration.between(windowStart, now).compareTo(windowDuration) >= 0) {
            return closeWindow(now);
        }

        // Window still open — event buffered, no output yet
        return OperatorResult.empty();
    }

    /**
     * Closes the current window: collects buffered events, runs the aggregator,
     * clears the buffer (or trims for sliding), and advances the window.
     */
    private OperatorResult closeWindow(Instant now) {
        // Collect events for this window
        List<GEvent> windowEvents = new ArrayList<>();
        Instant cutoff = slideDuration != null
                ? now.minus(windowDuration) // sliding: take events in the window range
                : Instant.MIN;             // tumbling: take all buffered events

        for (TimestampedEvent te : eventBuffer) {
            if (slideDuration != null) {
                // Sliding: only include events within the window duration
                if (!te.receivedAt().isBefore(cutoff)) {
                    windowEvents.add(te.event());
                }
            } else {
                windowEvents.add(te.event());
            }
        }

        // Advance window
        if (slideDuration != null) {
            // Sliding window: advance by slide duration, trim old events
            Instant newWindowStart = windowOpenedAt.get().plus(slideDuration);
            windowOpenedAt.set(newWindowStart);
            Instant trimCutoff = newWindowStart.minus(windowDuration);
            eventBuffer.removeIf(te -> te.receivedAt().isBefore(trimCutoff));
        } else {
            // Tumbling window: clear buffer, advance to now
            eventBuffer.clear();
            windowOpenedAt.set(now);
        }

        windowCount.incrementAndGet();

        if (windowEvents.isEmpty()) {
            return OperatorResult.empty();
        }

        // Run aggregator
        List<Event> results;
        try {
            results = aggregator.apply(Collections.unmodifiableList(windowEvents));
        } catch (Exception e) {
            incrementErrorCount("aggregator_exception");
            return OperatorResult.failed("Window aggregator failed: " + e.getMessage());
        }

        if (results == null || results.isEmpty()) {
            return OperatorResult.empty();
        }

        return OperatorResult.of(results);
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>(super.getMetrics());
        metrics.put("window_count", windowCount.get());
        metrics.put("events_buffered_total", eventsBuffered.get());
        metrics.put("events_dropped", eventsDropped.get());
        metrics.put("current_buffer_size", eventBuffer.size());
        metrics.put("window_duration_seconds", windowDuration.getSeconds());
        if (slideDuration != null) {
            metrics.put("slide_duration_seconds", slideDuration.getSeconds());
        }
        return Collections.unmodifiableMap(metrics);
    }

    @Override
    public Map<String, Object> getInternalState() {
        Map<String, Object> state = new LinkedHashMap<>(super.getInternalState());
        state.put("window_opened_at", windowOpenedAt.get().toString());
        state.put("buffer_size", eventBuffer.size());
        state.put("max_buffer_size", maxBufferSize);
        state.put("window_type", slideDuration != null ? "SLIDING" : "TUMBLING");
        return Collections.unmodifiableMap(state);
    }

    /** Returns the window duration. */
    public Duration getWindowDuration() {
        return windowDuration;
    }

    /** Returns the slide duration (null for tumbling windows). */
    public Duration getSlideDuration() {
        return slideDuration;
    }

    /** Returns the number of completed windows. */
    public long getWindowCount() {
        return windowCount.get();
    }

    /** Returns the current buffer size. */
    public int getCurrentBufferSize() {
        return eventBuffer.size();
    }

    // ─── Builder ─────────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private OperatorId operatorId;
        private String name;
        private String description;
        private Set<String> acceptedEventTypes;
        private Duration windowDuration;
        private Duration slideDuration;
        private int maxBufferSize = DEFAULT_MAX_BUFFER_SIZE;
        private Function<List<GEvent>, List<Event>> aggregator;
        private MetricsCollector metricsCollector;

        private Builder() {}

        public Builder operatorId(OperatorId operatorId) { this.operatorId = operatorId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder acceptedEventTypes(Set<String> types) { this.acceptedEventTypes = types; return this; }

        /** Sets the window duration (default: 1 minute). */
        public Builder windowDuration(Duration windowDuration) { this.windowDuration = windowDuration; return this; }

        /** Sets the slide duration for sliding windows (null = tumbling window). */
        public Builder slideDuration(Duration slideDuration) { this.slideDuration = slideDuration; return this; }

        /** Sets maximum buffer size (default: 10000). Events are dropped from oldest when exceeded. */
        public Builder maxBufferSize(int maxBufferSize) { this.maxBufferSize = maxBufferSize; return this; }

        /**
         * Sets the aggregator function that processes buffered events when a window closes.
         * The function receives an unmodifiable list of buffered GEvents and returns
         * zero or more output Events.
         */
        public Builder aggregator(Function<List<GEvent>, List<Event>> aggregator) {
            this.aggregator = aggregator;
            return this;
        }

        public Builder metricsCollector(MetricsCollector mc) { this.metricsCollector = mc; return this; }

        public StreamOperator build() {
            Objects.requireNonNull(operatorId, "operatorId is required");
            Objects.requireNonNull(name, "name is required");
            Objects.requireNonNull(aggregator, "aggregator is required");
            return new StreamOperator(operatorId, name, description, acceptedEventTypes,
                    windowDuration, slideDuration, maxBufferSize, aggregator, metricsCollector);
        }
    }
}

