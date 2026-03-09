package com.ghatana.pattern.engine.agent.operators;

import com.ghatana.core.operator.AbstractOperator;
import com.ghatana.core.operator.OperatorConfig;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.core.operator.OperatorType;
import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Abstract base operator for pattern engine operators.
 *
 * <p>Provides common functionality for all pattern engine operators including:</p>
 * <ul>
 *   <li>Event type validation and routing</li>
 *   <li>GEvent conversion from the generic Event interface</li>
 *   <li>Error handling with consistent error result formatting</li>
 *   <li>Metrics tracking for processed/filtered/error counts</li>
 *   <li>Operator-as-agent serialization via toEvent()</li>
 * </ul>
 *
 * <h2>Subclass Contract</h2>
 * <p>Subclasses must implement {@link #doProcessEvent(GEvent)} to provide their
 * specific processing logic on a guaranteed non-null GEvent.</p>
 *
 * @see FilterOperator
 * @see MapOperator
 * @see StreamOperator
 */
public abstract class BaseOperator extends AbstractOperator {

    private final Set<String> acceptedEventTypes;
    private final AtomicLong inputCount;
    private final AtomicLong outputCount;
    private final AtomicLong filteredCount;

    /**
     * Constructs a BaseOperator.
     *
     * @param id                  unique operator identifier
     * @param name                operator name
     * @param description         operator description
     * @param capabilities        list of operator capabilities
     * @param acceptedEventTypes  set of event types this operator processes (empty = all)
     * @param metricsCollector    metrics collector (null uses noop)
     */
    protected BaseOperator(
            OperatorId id,
            String name,
            String description,
            List<String> capabilities,
            Set<String> acceptedEventTypes,
            MetricsCollector metricsCollector) {

        super(id, OperatorType.PATTERN, name, description, capabilities, metricsCollector);
        this.acceptedEventTypes = acceptedEventTypes != null ? Set.copyOf(acceptedEventTypes) : Set.of();
        this.inputCount = new AtomicLong(0);
        this.outputCount = new AtomicLong(0);
        this.filteredCount = new AtomicLong(0);
    }

    /**
     * Processes an event through the pattern engine operator.
     *
     * <p>Validates, converts to GEvent, checks type acceptance, then
     * delegates to the subclass-specific {@link #doProcessEvent(GEvent)}.</p>
     */
    @Override
    public final Promise<OperatorResult> process(Event event) {
        if (event == null) {
            incrementErrorCount("null_event");
            return Promise.of(OperatorResult.failed("Input event must not be null"));
        }

        inputCount.incrementAndGet();

        // Event type filter
        if (!acceptedEventTypes.isEmpty()) {
            String eventType = event.getType();
            if (eventType == null || !acceptedEventTypes.contains(eventType)) {
                filteredCount.incrementAndGet();
                return Promise.of(OperatorResult.empty());
            }
        }

        // Convert to GEvent
        GEvent gEvent = asGEvent(event);
        if (gEvent == null) {
            incrementErrorCount("conversion_failed");
            return Promise.of(OperatorResult.failed("Failed to convert event to GEvent"));
        }

        try {
            return Promise.of(recordProcessing(() -> {
                OperatorResult result = doProcessEvent(gEvent);
                if (result.isSuccess() && result.getOutputEvents() != null) {
                    outputCount.addAndGet(result.getOutputEvents().size());
                }
                return result;
            }));
        } catch (Exception e) {
            incrementErrorCount("exception");
            return Promise.of(OperatorResult.failed("Operator processing failed: " + e.getMessage()));
        }
    }

    /**
     * Subclass-specific event processing logic.
     *
     * @param event the input event, guaranteed non-null and type-filtered
     * @return the processing result
     */
    protected abstract OperatorResult doProcessEvent(GEvent event);

    /**
     * Converts an Event to GEvent. Returns as-is if already GEvent,
     * otherwise builds a minimal GEvent preserving type, headers, and payload.
     */
    protected GEvent asGEvent(Event event) {
        if (event instanceof GEvent ge) {
            return ge;
        }
        try {
            Map<String, String> headers = new HashMap<>();
            for (String key : List.of("correlationId", "causationId", "stream", "source", "traceId")) {
                String v = event.getHeader(key);
                if (v != null) headers.put(key, v);
            }
            return GEvent.builder()
                    .type(event.getType())
                    .headers(headers)
                    .payload(Map.of())
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Event toEvent() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("operatorClass", getClass().getSimpleName());
        payload.put("operatorId", getId().toString());
        payload.put("acceptedEventTypes", List.copyOf(acceptedEventTypes));
        payload.put("state", getState().name());
        payload.put("inputCount", inputCount.get());
        payload.put("outputCount", outputCount.get());

        return GEvent.builder()
                .type("operator." + getClass().getSimpleName().toLowerCase())
                .payload(payload)
                .headers(Map.of("operatorId", getId().toString()))
                .build();
    }

    @Override
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>(super.getMetrics());
        metrics.put("input_count", inputCount.get());
        metrics.put("output_count", outputCount.get());
        metrics.put("filtered_count", filteredCount.get());
        return Collections.unmodifiableMap(metrics);
    }

    /** Returns the set of accepted event types (empty = all). */
    public Set<String> getAcceptedEventTypes() {
        return acceptedEventTypes;
    }

    /** Returns the number of input events received. */
    public long getInputCount() {
        return inputCount.get();
    }

    /** Returns the number of output events produced. */
    public long getOutputCount() {
        return outputCount.get();
    }

    /** Returns the number of events filtered (type mismatch). */
    public long getFilteredCount() {
        return filteredCount.get();
    }
}
