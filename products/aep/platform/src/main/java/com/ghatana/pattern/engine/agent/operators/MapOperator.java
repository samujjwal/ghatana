package com.ghatana.pattern.engine.agent.operators;

import com.ghatana.core.operator.OperatorId;
import com.ghatana.core.operator.OperatorResult;
import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.domain.domain.event.GEvent;
import com.ghatana.platform.observability.MetricsCollector;

import java.util.*;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Pattern engine operator that transforms (maps) events.
 *
 * <p>Applies a transformation function to each event, producing a new event
 * with modified type, payload, or headers. The original event is never mutated;
 * a new GEvent is built using the toBuilder() pattern.</p>
 *
 * <h2>Transformation Strategies</h2>
 * <ul>
 *   <li><b>Full transform</b>: Custom function mapping GEvent → Event</li>
 *   <li><b>Payload transform</b>: Transforms only the payload map</li>
 *   <li><b>Type rename</b>: Changes the event type while preserving data</li>
 *   <li><b>Enrichment</b>: Adds new payload or header fields</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   MapOperator enricher = MapOperator.builder()
 *       .operatorId(OperatorId.of("ghatana", "pattern", "enricher", "1.0"))
 *       .name("Event Enricher")
 *       .transformer(event -> event.toBuilder()
 *           .addPayload("enriched", true)
 *           .addPayload("enrichedAt", Instant.now().toString())
 *           .build())
 *       .build();
 * }</pre>
 */
public class MapOperator extends BaseOperator {

    private static final String CAPABILITY_MAP = "event-mapping";
    private static final String CAPABILITY_TRANSFORM = "event-transformation";

    private final Function<GEvent, Event> transformer;

    private MapOperator(
            OperatorId id,
            String name,
            String description,
            Set<String> acceptedEventTypes,
            Function<GEvent, Event> transformer,
            MetricsCollector metricsCollector) {

        super(id, name,
                description != null ? description : "Map/transform operator",
                List.of(CAPABILITY_MAP, CAPABILITY_TRANSFORM),
                acceptedEventTypes, metricsCollector);

        this.transformer = Objects.requireNonNull(transformer, "Transformer function must not be null");
    }

    @Override
    protected OperatorResult doProcessEvent(GEvent event) {
        Event transformed;
        try {
            transformed = transformer.apply(event);
        } catch (Exception e) {
            incrementErrorCount("transform_exception");
            return OperatorResult.failed("Transformation failed: " + e.getMessage());
        }

        if (transformed == null) {
            // Transformation returned null — treat as filter (drop event)
            return OperatorResult.empty();
        }

        return OperatorResult.of(transformed);
    }

    // ─── Static Factory Methods ──────────────────────────────────────────────────

    /**
     * Creates a map operator that enriches events by adding payload fields.
     *
     * @param id            operator identifier
     * @param name          operator name
     * @param enrichments   map of key-value pairs to add to the payload
     * @return a MapOperator that adds the specified fields to each event's payload
     */
    public static MapOperator enrich(OperatorId id, String name, Map<String, Object> enrichments) {
        return builder()
                .operatorId(id)
                .name(name)
                .description("Enriches events with " + enrichments.size() + " fields")
                .transformer(event -> {
                    // Build new payload with enrichments (safe for immutable source maps)
                    Map<String, Object> newPayload = new HashMap<>(event.getPayload());
                    newPayload.putAll(enrichments);
                    return GEvent.builder()
                            .id(event.getId())
                            .time(event.getTime())
                            .location(event.getLocation())
                            .stats(event.getStats())
                            .relations(event.getRelations())
                            .intervalBased(event.isIntervalBased())
                            .provenance(event.getProvenance())
                            .headers(event.getHeaders())
                            .payload(newPayload)
                            .build();
                })
                .build();
    }

    /**
     * Creates a map operator that renames the event type.
     *
     * @param id      operator identifier
     * @param name    operator name
     * @param newType the new event type
     * @return a MapOperator that changes the event type
     */
    public static MapOperator renameType(OperatorId id, String name, String newType) {
        return builder()
                .operatorId(id)
                .name(name)
                .description("Renames event type to: " + newType)
                .transformer(event -> GEvent.builder()
                        .type(newType)
                        .headers(event.getHeaders())
                        .payload(event.getPayload())
                        .time(event.getTime())
                        .location(event.getLocation())
                        .stats(event.getStats())
                        .relations(event.getRelations())
                        .intervalBased(event.isIntervalBased())
                        .provenance(event.getProvenance())
                        .build())
                .build();
    }

    /**
     * Creates a map operator that transforms the payload using a function.
     *
     * @param id                operator identifier
     * @param name              operator name
     * @param payloadTransformer function that transforms the payload map
     * @return a MapOperator that applies the payload transformation
     */
    public static MapOperator transformPayload(
            OperatorId id, String name,
            UnaryOperator<Map<String, Object>> payloadTransformer) {
        return builder()
                .operatorId(id)
                .name(name)
                .description("Transforms event payload")
                .transformer(event -> {
                    Map<String, Object> newPayload = payloadTransformer.apply(
                            new HashMap<>(event.getPayload()));
                    return GEvent.builder()
                            .type(event.getType())
                            .headers(event.getHeaders())
                            .payload(newPayload)
                            .time(event.getTime())
                            .location(event.getLocation())
                            .stats(event.getStats())
                            .relations(event.getRelations())
                            .intervalBased(event.isIntervalBased())
                            .provenance(event.getProvenance())
                            .build();
                })
                .build();
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
        private Function<GEvent, Event> transformer;
        private MetricsCollector metricsCollector;

        private Builder() {}

        public Builder operatorId(OperatorId operatorId) { this.operatorId = operatorId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder acceptedEventTypes(Set<String> types) { this.acceptedEventTypes = types; return this; }
        public Builder transformer(Function<GEvent, Event> transformer) { this.transformer = transformer; return this; }
        public Builder metricsCollector(MetricsCollector mc) { this.metricsCollector = mc; return this; }

        public MapOperator build() {
            Objects.requireNonNull(operatorId, "operatorId is required");
            Objects.requireNonNull(name, "name is required");
            Objects.requireNonNull(transformer, "transformer is required");
            return new MapOperator(operatorId, name, description, acceptedEventTypes, transformer, metricsCollector);
        }
    }
}

