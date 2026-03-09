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
import java.util.function.Function;

/**
 * Stream operator that transforms events via a mapping function (1:1 transformation).
 *
 * @doc.type class
 * @doc.purpose Event transformation operator (1:1 mapping)
 * @doc.layer core
 * @doc.pattern Stream Operator
 */
public class MapOperator extends AbstractStreamOperator {

    private final Function<Event, Event> mapper;
    private long transformCount = 0;

    public MapOperator(
            OperatorId id,
            String name,
            String description,
            List<String> eventTypes,
            Function<Event, Event> mapper,
            MetricsCollector metricsCollector
    ) {
        super(id, name, description, eventTypes, metricsCollector);
        this.mapper = Objects.requireNonNull(mapper, "Mapper function must not be null");
    }

    @Override
    public Promise<OperatorResult> process(Event event) {
        Objects.requireNonNull(event, "Event must not be null");
        try {
            Event transformed = mapper.apply(event);
            if (transformed == null) {
                return Promise.of(OperatorResult.empty());
            }
            transformCount++;
            return Promise.of(OperatorResult.of(transformed));
        } catch (Exception e) {
            // On mapping error, pass through original event
            transformCount++;
            return Promise.of(OperatorResult.of(event));
        }
    }

    public long getTransformCount() { return transformCount; }

    @Override
    public Event toEvent() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "stream.map");
        payload.put("name", getName());
        payload.put("version", getVersion());
        payload.put("description", getDescription());
        
        Map<String, Object> config = new HashMap<>();
        config.put("mapperType", "custom_function");
        payload.put("config", config);
        
        List<String> capabilities = List.of("stream.map", "event.transform");
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
        return String.format("MapOperator[id=%s, name=%s, transformed=%d]",
                getId(), getName(), transformCount);
    }

    public static MapOperator.Builder builder() {
        return new MapOperator.Builder();
    }

    public static class Builder {
        private OperatorId id;
        private String name;
        private String description;
        private List<String> eventTypes;
        private Function<Event, Event> mapper;
        private MetricsCollector metricsCollector;

        public Builder id(OperatorId id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder eventTypes(List<String> eventTypes) { this.eventTypes = eventTypes; return this; }
        public Builder mapper(Function<Event, Event> mapper) { this.mapper = mapper; return this; }
        public Builder metricsCollector(MetricsCollector metricsCollector) { this.metricsCollector = metricsCollector; return this; }

        public MapOperator build() {
            return new MapOperator(id, name, description, eventTypes, mapper, metricsCollector);
        }
    }
}
