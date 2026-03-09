package com.ghatana.pattern.engine.agent.operators;

import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.platform.observability.MetricsCollector;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * Pattern engine operator that filters events based on configurable predicates.
 *
 * <p>Supports multiple filter strategies:</p>
 * <ul>
 *   <li><b>Predicate-based</b>: Custom Java predicate function on GEvent</li>
 *   <li><b>Header-based</b>: Filter on header key existence or value match</li>
 *   <li><b>Payload-based</b>: Filter on payload field existence or value match</li>
 *   <li><b>Composite</b>: AND/OR combination of multiple filters</li>
 * </ul>
 *
 * <p>Events that pass the filter are forwarded unchanged. Events that do not
 * pass are silently dropped (empty OperatorResult).</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   FilterOperator filter = FilterOperator.builder()
 *       .operatorId(OperatorId.of("ghatana", "pattern", "severity-filter", "1.0"))
 *       .name("Severity Filter")
 *       .predicate(event -> {
 *           Object severity = event.getPayload("severity");
 *           return severity instanceof Number && ((Number)severity).intValue() >= 5;
 *       })
 *       .build();
 * }</pre>
 */
public class FilterOperator extends BaseOperator {

    private static final String CAPABILITY_FILTER = "event-filtering";
    private static final String CAPABILITY_PREDICATE = "predicate-evaluation";

    private final Predicate<GEvent> filterPredicate;
    private final AtomicLong passedCount;
    private final AtomicLong rejectedCount;

    private FilterOperator(
            OperatorId id,
            String name,
            String description,
            Set<String> acceptedEventTypes,
            Predicate<GEvent> filterPredicate,
            MetricsCollector metricsCollector) {

        super(id, name,
                description != null ? description : "Filter operator with predicate",
                List.of(CAPABILITY_FILTER, CAPABILITY_PREDICATE),
                acceptedEventTypes, metricsCollector);

        this.filterPredicate = Objects.requireNonNull(filterPredicate, "Filter predicate must not be null");
        this.passedCount = new AtomicLong(0);
        this.rejectedCount = new AtomicLong(0);
    }

    @Override
    protected OperatorResult doProcessEvent(GEvent event) {
        boolean passes;
        try {
            passes = filterPredicate.test(event);
        } catch (Exception e) {
            incrementErrorCount("predicate_exception");
            return OperatorResult.failed("Filter predicate threw exception: " + e.getMessage());
        }

        if (passes) {
            passedCount.incrementAndGet();
            return OperatorResult.of(event);
        } else {
            rejectedCount.incrementAndGet();
            return OperatorResult.empty();
        }
    }

    @Override
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new LinkedHashMap<>(super.getMetrics());
        metrics.put("passed_count", passedCount.get());
        metrics.put("rejected_count", rejectedCount.get());
        long total = passedCount.get() + rejectedCount.get();
        if (total > 0) {
            metrics.put("pass_rate", (double) passedCount.get() / total);
        }
        return Collections.unmodifiableMap(metrics);
    }

    /** Returns the number of events that passed the filter. */
    public long getPassedCount() {
        return passedCount.get();
    }

    /** Returns the number of events rejected by the filter. */
    public long getRejectedCount() {
        return rejectedCount.get();
    }

    // ─── Static Factory Methods ──────────────────────────────────────────────────

    /**
     * Creates a filter that checks for the existence of a header.
     */
    public static FilterOperator headerExists(OperatorId id, String name, String headerKey) {
        return builder()
                .operatorId(id)
                .name(name)
                .description("Filters events that have header: " + headerKey)
                .predicate(event -> event.getHeader(headerKey) != null)
                .build();
    }

    /**
     * Creates a filter that checks a header value equals the expected value.
     */
    public static FilterOperator headerEquals(OperatorId id, String name, String headerKey, String expectedValue) {
        return builder()
                .operatorId(id)
                .name(name)
                .description("Filters events where header " + headerKey + " = " + expectedValue)
                .predicate(event -> Objects.equals(event.getHeader(headerKey), expectedValue))
                .build();
    }

    /**
     * Creates a filter that checks for the existence of a payload field.
     */
    public static FilterOperator payloadExists(OperatorId id, String name, String payloadKey) {
        return builder()
                .operatorId(id)
                .name(name)
                .description("Filters events that have payload field: " + payloadKey)
                .predicate(event -> event.getPayload(payloadKey) != null)
                .build();
    }

    /**
     * Creates a filter that checks a payload field equals the expected value.
     */
    public static FilterOperator payloadEquals(OperatorId id, String name, String payloadKey, Object expectedValue) {
        return builder()
                .operatorId(id)
                .name(name)
                .description("Filters events where payload " + payloadKey + " = " + expectedValue)
                .predicate(event -> Objects.equals(event.getPayload(payloadKey), expectedValue))
                .build();
    }

    /**
     * Combines two predicates with AND logic.
     */
    public static Predicate<GEvent> and(Predicate<GEvent> a, Predicate<GEvent> b) {
        return a.and(b);
    }

    /**
     * Combines two predicates with OR logic.
     */
    public static Predicate<GEvent> or(Predicate<GEvent> a, Predicate<GEvent> b) {
        return a.or(b);
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
        private Predicate<GEvent> predicate;
        private MetricsCollector metricsCollector;

        private Builder() {}

        public Builder operatorId(OperatorId operatorId) { this.operatorId = operatorId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder acceptedEventTypes(Set<String> types) { this.acceptedEventTypes = types; return this; }
        public Builder predicate(Predicate<GEvent> predicate) { this.predicate = predicate; return this; }
        public Builder metricsCollector(MetricsCollector mc) { this.metricsCollector = mc; return this; }

        public FilterOperator build() {
            Objects.requireNonNull(operatorId, "operatorId is required");
            Objects.requireNonNull(name, "name is required");
            Objects.requireNonNull(predicate, "predicate is required");
            return new FilterOperator(operatorId, name, description, acceptedEventTypes, predicate, metricsCollector);
        }
    }
}

