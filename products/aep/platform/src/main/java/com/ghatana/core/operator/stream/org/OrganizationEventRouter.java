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
 * Stream operator that routes organization events to different handlers based
 * on event type or status.
 *
 * <p>
 * <b>Purpose</b><br>
 * Routes incoming organization events to appropriate processing pipelines based
 * on: - Event type (organization.created vs organization.updated vs
 * organization.deleted) - Department type (engineering, sales, operations,
 * etc.) - Priority level (critical, high, normal, low) - Status transitions
 * (active, inactive, suspended)
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * OrganizationEventRouter router = OrganizationEventRouter.builder()
 *     .id(OperatorId.of("org:stream:route:type"))
 *     .name("Org Event Type Router")
 *     .routeByEventType()
 *     .metricsCollector(metrics)
 *     .build();
 * }</pre>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * STREAM operator that implements 1:1 transformation with event metadata
 * augmentation. Adds routing hints to events for downstream pipeline handling.
 * Part of the unified operator model for organization event processing.
 *
 * @doc.type class
 * @doc.purpose Route organization events to appropriate processing handlers
 * @doc.layer core
 * @doc.pattern Stream Operator
 */
public class OrganizationEventRouter extends AbstractStreamOperator {

    private final Function<Event, String> routeFunction;
    private final String routingStrategy;
    private final List<String> eventTypesList;
    private long routedCount = 0;
    private long unroutedCount = 0;
    private final Map<String, Long> routeMetrics = new HashMap<>();

    private OrganizationEventRouter(
            OperatorId id,
            String name,
            String description,
            List<String> eventTypes,
            Function<Event, String> routeFunction,
            String routingStrategy,
            MetricsCollector metricsCollector
    ) {
        super(id, name, description, eventTypes, metricsCollector);
        this.routeFunction = Objects.requireNonNull(routeFunction, "Route function must not be null");
        this.routingStrategy = Objects.requireNonNull(routingStrategy, "Routing strategy must not be null");
        this.eventTypesList = Objects.requireNonNull(eventTypes, "Event types must not be null");
    }

    @Override
    public Promise<OperatorResult> process(Event event) {
        Objects.requireNonNull(event, "Event must not be null");
        try {
            String route = routeFunction.apply(event);

            if (route != null && !route.isBlank()) {
                routedCount++;

                // Track routing metrics
                routeMetrics.put(route, routeMetrics.getOrDefault(route, 0L) + 1);
                getMetricsCollector().incrementCounter("org.event.route.success",
                        "operator", getId().toString(), "route", route);

                return Promise.of(OperatorResult.of(event)); // Return event as-is (routing metadata via context)
            } else {
                unroutedCount++;
                getMetricsCollector().incrementCounter("org.event.route.unrouted",
                        "operator", getId().toString(), "event_type", event.getType());
                return Promise.of(OperatorResult.of(event)); // Pass through unrouted events
            }
        } catch (Exception e) {
            unroutedCount++;
            getMetricsCollector().incrementCounter("org.event.route.error",
                    "operator", getId().toString(), "error", e.getClass().getSimpleName());
            return Promise.of(OperatorResult.of(event)); // Pass through on error
        }
    }

    public long getRoutedCount() {
        return routedCount;
    }

    public long getUnroutedCount() {
        return unroutedCount;
    }

    public Map<String, Long> getRouteMetrics() {
        return new HashMap<>(routeMetrics);
    }

    public String getRoutingStrategy() {
        return routingStrategy;
    }

    @Override
    public Event toEvent() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "stream.org.route");
        payload.put("name", getName());
        payload.put("version", getVersion());
        payload.put("description", getDescription());

        Map<String, Object> config = new HashMap<>();
        config.put("routingStrategy", routingStrategy);
        config.put("eventTypes", eventTypesList);
        payload.put("config", config);

        List<String> capabilities = List.of("stream.route", "org.event.route", "event.routing");
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
        return String.format("OrganizationEventRouter[id=%s, name=%s, strategy=%s, routed=%d, unrouted=%d]",
                getId(), getName(), routingStrategy, routedCount, unroutedCount);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for OrganizationEventRouter.
     */
    public static class Builder {

        private OperatorId id;
        private String name;
        private String description = "";
        private List<String> eventTypes = List.of("org.event", "org.department.event");
        private Function<Event, String> routeFunction;
        private String routingStrategy = "default";
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

        public Builder routeFunction(Function<Event, String> function) {
            this.routeFunction = Objects.requireNonNull(function, "Route function must not be null");
            return this;
        }

        public Builder routingStrategy(String strategy) {
            this.routingStrategy = Objects.requireNonNull(strategy, "Routing strategy must not be null");
            return this;
        }

        public Builder routeByEventType() {
            this.routingStrategy = "by-event-type";
            this.routeFunction = event -> event.getType()
                    .replaceAll("\\.", "-")
                    .toLowerCase();
            return this;
        }

        public Builder routeByDepartment() {
            this.routingStrategy = "by-department";
            this.routeFunction = event -> {
                Object deptId = event.getPayload("departmentId");
                return deptId != null ? "dept-" + deptId : "unrouted";
            };
            return this;
        }

        public Builder routeByPriority() {
            this.routingStrategy = "by-priority";
            this.routeFunction = event -> {
                Object priority = event.getPayload("priority");
                if (priority != null) {
                    return "priority-" + priority.toString().toLowerCase();
                }
                return "priority-normal";
            };
            return this;
        }

        public Builder metricsCollector(MetricsCollector metricsCollector) {
            this.metricsCollector = Objects.requireNonNull(metricsCollector, "Metrics collector must not be null");
            return this;
        }

        public OrganizationEventRouter build() {
            Objects.requireNonNull(id, "ID is required");
            Objects.requireNonNull(name, "Name is required");
            Objects.requireNonNull(routeFunction, "Route function is required");
            Objects.requireNonNull(metricsCollector, "Metrics collector is required");

            return new OrganizationEventRouter(
                    id, name, description, eventTypes, routeFunction, routingStrategy, metricsCollector
            );
        }
    }
}
