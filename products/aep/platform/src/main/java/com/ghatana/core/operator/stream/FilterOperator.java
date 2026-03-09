package com.ghatana.core.operator.stream;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.EventTime;
import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.core.operator.AbstractStreamOperator;
import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;

import java.util.*;
import java.util.function.Predicate;

/**
 * Stream operator that filters events based on a predicate (1:0-1 transformation).
 *
 * @doc.type class
 * @doc.purpose Event filtering operator (1:0-1 transformation)
 * @doc.layer core
 * @doc.pattern Stream Operator
 */
public class FilterOperator extends AbstractStreamOperator {

    private final Predicate<Event> predicate;
    private long filterCount = 0;
    private long passThroughCount = 0;

    public FilterOperator(
            OperatorId id,
            String name,
            String description,
            List<String> eventTypes,
            Predicate<Event> predicate,
            MetricsCollector metricsCollector
    ) {
        super(id, name, description, eventTypes, metricsCollector);
        this.predicate = Objects.requireNonNull(predicate, "Predicate must not be null");
    }

    @Override
    public Promise<OperatorResult> process(Event event) {
        Objects.requireNonNull(event, "Event must not be null");
        try {
            boolean pass = predicate.test(event);
            if (pass) {
                passThroughCount++;
                return Promise.of(OperatorResult.of(event));
            } else {
                filterCount++;
                return Promise.of(OperatorResult.empty());
            }
        } catch (Exception e) {
            filterCount++;
            return Promise.of(OperatorResult.empty());
        }
    }

    public long getFilterCount() { return filterCount; }
    public long getPassThroughCount() { return passThroughCount; }

    @Override
    public Event toEvent() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "stream.filter");
        payload.put("name", getName());
        payload.put("version", getVersion());
        payload.put("description", getDescription());
        
        Map<String, Object> config = new HashMap<>();
        config.put("filterType", "custom_predicate");
        payload.put("config", config);
        
        List<String> capabilities = List.of("stream.filter", "event.filter");
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
        return String.format("FilterOperator[id=%s, name=%s, pass=%d, filtered=%d]",
                getId(), getName(), passThroughCount, filterCount);
    }

    public static FilterOperator.Builder builder() {
        return new FilterOperator.Builder();
    }

    public static class Builder {
        private OperatorId id;
        private String name;
        private String description;
        private List<String> eventTypes;
        private Predicate<Event> predicate;
        private MetricsCollector metricsCollector;

        public Builder id(OperatorId id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder eventTypes(List<String> eventTypes) { this.eventTypes = eventTypes; return this; }
        public Builder predicate(Predicate<Event> predicate) { this.predicate = predicate; return this; }
        public Builder metricsCollector(MetricsCollector metricsCollector) { this.metricsCollector = metricsCollector; return this; }

        public FilterOperator build() {
            return new FilterOperator(id, name, description, eventTypes, predicate, metricsCollector);
        }
    }
}
