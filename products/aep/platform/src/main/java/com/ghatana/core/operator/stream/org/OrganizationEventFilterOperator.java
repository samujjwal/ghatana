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
import java.util.function.Predicate;

/**
 * Stream operator that filters organization events by type or event state.
 *
 * <p>
 * <b>Purpose</b><br>
 * Filters incoming organization events (org.*, org.v1.*) based on configurable
 * predicates. Supports filtering by: - Event type (e.g., "org.event",
 * "org.department.event") - Event source/department (e.g., "dept-123",
 * "org-456") - Status transitions (created, updated, deleted) - Custom
 * predicates for domain logic
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * OrganizationEventFilterOperator filter = OrganizationEventFilterOperator.builder()
 *     .id(OperatorId.of("org:stream:filter:active"))
 *     .name("Active Organization Filter")
 *     .eventTypes(List.of("org.event", "org.department.event"))
 *     .filterByStatus("ACTIVE")
 *     .metricsCollector(metrics)
 *     .build();
 *
 * OperatorResult result = filter.process(event).getResult();
 * }</pre>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * STREAM operator that implements 1:0-1 transformation (filter or drop events).
 * Part of the unified operator model for organization event processing
 * pipelines.
 *
 * @doc.type class
 * @doc.purpose Filter organization events by type, status, or custom predicate
 * @doc.layer core
 * @doc.pattern Stream Operator
 */
public class OrganizationEventFilterOperator extends AbstractStreamOperator {

    private final Predicate<Event> predicate;
    private final String filterCriteria;
    private final List<String> eventTypesList;
    private long filteredCount = 0;
    private long passedCount = 0;

    private OrganizationEventFilterOperator(
            OperatorId id,
            String name,
            String description,
            List<String> eventTypes,
            Predicate<Event> predicate,
            String filterCriteria,
            MetricsCollector metricsCollector
    ) {
        super(id, name, description, eventTypes, metricsCollector);
        this.predicate = Objects.requireNonNull(predicate, "Predicate must not be null");
        this.filterCriteria = Objects.requireNonNull(filterCriteria, "Filter criteria must not be null");
        this.eventTypesList = Objects.requireNonNull(eventTypes, "Event types must not be null");
    }

    @Override
    public Promise<OperatorResult> process(Event event) {
        Objects.requireNonNull(event, "Event must not be null");
        try {
            boolean pass = predicate.test(event);
            if (pass) {
                passedCount++;
                getMetricsCollector().incrementCounter("org.event.filter.pass",
                        "operator", getId().toString(), "event_type", event.getType());
                return Promise.of(OperatorResult.of(event));
            } else {
                filteredCount++;
                getMetricsCollector().incrementCounter("org.event.filter.filtered",
                        "operator", getId().toString(), "event_type", event.getType());
                return Promise.of(OperatorResult.empty());
            }
        } catch (Exception e) {
            filteredCount++;
            getMetricsCollector().incrementCounter("org.event.filter.error",
                    "operator", getId().toString(), "error", e.getClass().getSimpleName());
            return Promise.of(OperatorResult.empty());
        }
    }

    public long getFilteredCount() {
        return filteredCount;
    }

    public long getPassedCount() {
        return passedCount;
    }

    public String getFilterCriteria() {
        return filterCriteria;
    }

    @Override
    public Event toEvent() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "stream.org.filter");
        payload.put("name", getName());
        payload.put("version", getVersion());
        payload.put("description", getDescription());

        Map<String, Object> config = new HashMap<>();
        config.put("filterCriteria", filterCriteria);
        config.put("eventTypes", eventTypesList);
        payload.put("config", config);

        List<String> capabilities = List.of("stream.filter", "org.event.filter", "event.routing");
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
        return String.format("OrganizationEventFilterOperator[id=%s, name=%s, criteria=%s, passed=%d, filtered=%d]",
                getId(), getName(), filterCriteria, passedCount, filteredCount);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for OrganizationEventFilterOperator.
     */
    public static class Builder {

        private OperatorId id;
        private String name;
        private String description = "";
        private List<String> eventTypes = List.of("org.event", "org.department.event");
        private Predicate<Event> predicate;
        private String filterCriteria = "custom";
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

        public Builder predicate(Predicate<Event> predicate) {
            this.predicate = Objects.requireNonNull(predicate, "Predicate must not be null");
            return this;
        }

        public Builder filterCriteria(String filterCriteria) {
            this.filterCriteria = Objects.requireNonNull(filterCriteria, "Filter criteria must not be null");
            return this;
        }

        public Builder filterByStatus(String status) {
            this.filterCriteria = "status:" + status;
            this.predicate = event -> {
                Object statusObj = event.getPayload("status");
                return statusObj != null && statusObj.toString().equals(status);
            };
            return this;
        }

        public Builder filterByDepartment(String departmentId) {
            this.filterCriteria = "department:" + departmentId;
            this.predicate = event -> {
                Object deptObj = event.getPayload("departmentId");
                return deptObj != null && deptObj.toString().equals(departmentId);
            };
            return this;
        }

        public Builder metricsCollector(MetricsCollector metricsCollector) {
            this.metricsCollector = Objects.requireNonNull(metricsCollector, "Metrics collector must not be null");
            return this;
        }

        public OrganizationEventFilterOperator build() {
            Objects.requireNonNull(id, "ID is required");
            Objects.requireNonNull(name, "Name is required");
            Objects.requireNonNull(predicate, "Predicate is required");
            Objects.requireNonNull(metricsCollector, "Metrics collector is required");

            return new OrganizationEventFilterOperator(
                    id, name, description, eventTypes, predicate, filterCriteria, metricsCollector
            );
        }
    }
}
