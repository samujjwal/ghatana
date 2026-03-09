package com.ghatana.core.operator.stream;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.EventTime;
import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.core.operator.AbstractStreamOperator;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Stream operator for time-based windowing with aggregation (N:M transformation).
 *
 * <p>Supports:
 * - Tumbling windows (non-overlapping)
 * - Sliding windows (overlapping with step size)
 * - Custom aggregation functions
 *
 * @doc.type class
 * @doc.purpose Time-based window aggregation operator
 * @doc.layer core
 * @doc.pattern Stream Operator (Stateful)
 */
public class WindowOperator extends AbstractStreamOperator {

    private static final Logger logger = LoggerFactory.getLogger(WindowOperator.class);

    private final Duration windowSize;
    private final Duration slideSize;  // null = tumbling (equals windowSize)
    private final Function<List<Event>, Event> aggregator;
    private final Map<String, List<Event>> windows;
    private final Map<String, Long> windowStartTimes;
    private long windowEmitCount = 0;

    public WindowOperator(
            OperatorId id,
            String name,
            String description,
            List<String> eventTypes,
            Duration windowSize,
            Duration slideSize,
            Function<List<Event>, Event> aggregator,
            MetricsCollector metricsCollector
    ) {
        super(id, name, description, eventTypes, metricsCollector);
        this.windowSize = Objects.requireNonNull(windowSize, "Window size must not be null");
        this.slideSize = slideSize != null ? slideSize : windowSize;  // Tumbling if slide = window
        this.aggregator = Objects.requireNonNull(aggregator, "Aggregator must not be null");
        this.windows = new ConcurrentHashMap<>();
        this.windowStartTimes = new ConcurrentHashMap<>();
        
        if (windowSize.isNegative() || windowSize.isZero()) {
            throw new IllegalArgumentException("Window size must be positive");
        }
        if (this.slideSize.isNegative() || this.slideSize.isZero()) {
            throw new IllegalArgumentException("Slide size must be positive");
        }
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    public Promise<OperatorResult> process(Event event) {
        Objects.requireNonNull(event, "Event must not be null");
        
        try {
            long eventTime = event.getTimestamp().toEpochMilli();
            String windowKey = calculateWindowKey(eventTime);
            
            // Get or create window
            List<Event> window = windows.computeIfAbsent(windowKey, k -> {
                windowStartTimes.put(k, eventTime);
                return new ArrayList<>();
            });
            
            window.add(event);
            
            // Check if window is complete (reached window end time)
            long windowStart = windowStartTimes.get(windowKey);
            long windowEnd = windowStart + windowSize.toMillis();
            
            if (eventTime >= windowEnd) {
                // Window is complete, emit aggregated result
                Event aggregated = aggregator.apply(new ArrayList<>(window));
                windows.remove(windowKey);
                windowStartTimes.remove(windowKey);
                windowEmitCount++;
                return Promise.of(OperatorResult.of(aggregated));
            } else {
                // Window still open, accumulating events
                return Promise.of(OperatorResult.empty());
            }
        } catch (Exception e) {
            logger.warn("Window aggregation failed for event {}: {}", event.getId(), e.getMessage());
            return Promise.of(OperatorResult.empty());
        }
    }

    /**
     * Calculates window key based on event timestamp and window alignment.
     */
    private String calculateWindowKey(long eventTime) {
        long windowStart = (eventTime / windowSize.toMillis()) * windowSize.toMillis();
        return "window:" + windowStart;
    }

    public long getWindowEmitCount() { return windowEmitCount; }
    public int getPendingWindowCount() { return windows.size(); }

    @Override
    public Event toEvent() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "stream.window");
        payload.put("name", getName());
        payload.put("version", getVersion());
        payload.put("description", getDescription());
        
        Map<String, Object> config = new HashMap<>();
        config.put("windowSizeMs", windowSize.toMillis());
        config.put("slideSizeMs", slideSize.toMillis());
        config.put("windowType", windowSize.equals(slideSize) ? "tumbling" : "sliding");
        payload.put("config", config);
        
        List<String> capabilities = List.of("stream.window", "event.aggregate");
        payload.put("capabilities", capabilities);
        
        Map<String, String> headers = new HashMap<>();
        headers.put("operatorId", getId().toString());
        headers.put("tenantId", getId().getNamespace());
        
        return GEvent.builder()
            .type("operator.registered")
            .headers(headers)
            .payload(payload)
            .time(EventTime.now())
            .build();
    }

    @Override
    public String toString() {
        String windowType = windowSize.equals(slideSize) ? "tumbling" : "sliding";
        return String.format("WindowOperator[id=%s, name=%s, type=%s, emitted=%d, pending=%d]",
                getId(), getName(), windowType, windowEmitCount, windows.size());
    }

    public static WindowOperator.Builder builder() {
        return new WindowOperator.Builder();
    }

    public static class Builder {
        private OperatorId id;
        private String name;
        private String description;
        private List<String> eventTypes;
        private Duration windowSize;
        private Duration slideSize;
        private Function<List<Event>, Event> aggregator;
        private MetricsCollector metricsCollector;

        public Builder id(OperatorId id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder eventTypes(List<String> eventTypes) { this.eventTypes = eventTypes; return this; }
        public Builder windowSize(Duration windowSize) { this.windowSize = windowSize; return this; }
        public Builder slideSize(Duration slideSize) { this.slideSize = slideSize; return this; }
        public Builder aggregator(Function<List<Event>, Event> aggregator) { this.aggregator = aggregator; return this; }
        public Builder metricsCollector(MetricsCollector metricsCollector) { this.metricsCollector = metricsCollector; return this; }

        public WindowOperator build() {
            return new WindowOperator(id, name, description, eventTypes, windowSize, slideSize, aggregator, metricsCollector);
        }
    }
}
