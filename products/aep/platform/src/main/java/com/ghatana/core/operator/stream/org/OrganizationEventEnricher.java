package com.ghatana.core.operator.stream.org;

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
 * Stream operator that enriches organization events with additional context and
 * data.
 *
 * <p>
 * <b>Purpose</b><br>
 * Transforms organization events by adding enrichment data such as: -
 * Department hierarchy information - Organization metadata and tags -
 * User/agent context and roles - Historical state and audit trails - Computed
 * fields (timestamps, derived values)
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * OrganizationEventEnricher enricher = OrganizationEventEnricher.builder()
 *     .id(OperatorId.of("org:stream:enrich:hierarchy"))
 *     .name("Department Hierarchy Enricher")
 *     .enrichFunction(event -> event.toBuilder()  // use event's builder pattern
 *         .payload(new HashMap<>(event.getPayload()))  // copy and enhance
 *         .build())
 *     .metricsCollector(metrics)
 *     .build();
 * }</pre>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * STREAM operator that implements 1:1 transformation (map/enrich operation).
 * Part of the unified operator model for organization event processing
 * pipelines.
 *
 * @doc.type class
 * @doc.purpose Enrich organization events with context and metadata
 * @doc.layer core
 * @doc.pattern Stream Operator
 */
public class OrganizationEventEnricher extends AbstractStreamOperator {

    private final Function<Event, Event> enrichmentFunction;
    private final String enrichmentType;
    private final List<String> eventTypesList;
    private long enrichedCount = 0;
    private long errorCount = 0;

    private OrganizationEventEnricher(
            OperatorId id,
            String name,
            String description,
            List<String> eventTypes,
            Function<Event, Event> enrichmentFunction,
            String enrichmentType,
            MetricsCollector metricsCollector
    ) {
        super(id, name, description, eventTypes, metricsCollector);
        this.enrichmentFunction = Objects.requireNonNull(enrichmentFunction, "Enrichment function must not be null");
        this.enrichmentType = Objects.requireNonNull(enrichmentType, "Enrichment type must not be null");
        this.eventTypesList = Objects.requireNonNull(eventTypes, "Event types must not be null");
    }

    @Override
    public Promise<OperatorResult> process(Event event) {
        Objects.requireNonNull(event, "Event must not be null");
        try {
            Event enriched = enrichmentFunction.apply(event);
            enrichedCount++;
            getMetricsCollector().incrementCounter("org.event.enrich.success",
                    "operator", getId().toString(), "type", enrichmentType);
            return Promise.of(OperatorResult.of(enriched));
        } catch (Exception e) {
            errorCount++;
            getMetricsCollector().incrementCounter("org.event.enrich.error",
                    "operator", getId().toString(), "error", e.getClass().getSimpleName());
            // Failed enrichment - pass through original event rather than dropping
            return Promise.of(OperatorResult.of(event));
        }
    }

    public long getEnrichedCount() {
        return enrichedCount;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public String getEnrichmentType() {
        return enrichmentType;
    }

    @Override
    public Event toEvent() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "stream.org.enrich");
        payload.put("name", getName());
        payload.put("version", getVersion());
        payload.put("description", getDescription());

        Map<String, Object> config = new HashMap<>();
        config.put("enrichmentType", enrichmentType);
        config.put("eventTypes", eventTypesList);
        payload.put("config", config);

        List<String> capabilities = List.of("stream.enrich", "org.event.enrich", "data.enrichment");
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
        return String.format("OrganizationEventEnricher[id=%s, name=%s, type=%s, enriched=%d, errors=%d]",
                getId(), getName(), enrichmentType, enrichedCount, errorCount);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for OrganizationEventEnricher.
     */
    public static class Builder {

        private OperatorId id;
        private String name;
        private String description = "";
        private List<String> eventTypes = List.of("org.event", "org.department.event");
        private Function<Event, Event> enrichmentFunction;
        private String enrichmentType = "generic";
        private MetricsCollector metricsCollector;

        public Builder id(OperatorId id) {
            this.id = Objects.requireNonNull(id, "ID must not be null");
            return this;
        }

        public Builder name(String name) {
            this.name = Objects.requireNonNull(name, "Name must not be null");
            return this;
        }

        public Builder description(String description) {
            this.description = Objects.requireNonNull(description, "Description must not be null");
            return this;
        }

        public Builder eventTypes(List<String> eventTypes) {
            this.eventTypes = Objects.requireNonNull(eventTypes, "Event types must not be null");
            return this;
        }

        public Builder enrichFunction(Function<Event, Event> function) {
            this.enrichmentFunction = Objects.requireNonNull(function, "Enrichment function must not be null");
            return this;
        }

        public Builder enrichmentType(String type) {
            this.enrichmentType = Objects.requireNonNull(type, "Enrichment type must not be null");
            return this;
        }

        public Builder metricsCollector(MetricsCollector metricsCollector) {
            this.metricsCollector = Objects.requireNonNull(metricsCollector, "Metrics collector must not be null");
            return this;
        }

        public OrganizationEventEnricher build() {
            Objects.requireNonNull(id, "ID is required");
            Objects.requireNonNull(name, "Name is required");
            Objects.requireNonNull(enrichmentFunction, "Enrichment function is required");
            Objects.requireNonNull(metricsCollector, "Metrics collector is required");

            return new OrganizationEventEnricher(
                    id, name, description, eventTypes, enrichmentFunction, enrichmentType, metricsCollector
            );
        }
    }
}
