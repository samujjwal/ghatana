package com.ghatana.softwareorg.support;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
@Getter
public class SupportPipelinesRegistrar {

    private final String operatorId, organizationId;
    private final MetricsCollector metrics;
    private final Map<String, PipelineOp> pipelines = new LinkedHashMap<>();
    private Status status = Status.INITIALIZED;

    public Promise<Void> initialize() {
        return Promise.complete().whenResult(() -> {
            pipelines.put("ticket-routing", new PipelineOp("support-routing", "Ticket Routing",
                    Arrays.asList("ticket.created", "ticket.assigned", "ticket.acknowledged")));
            pipelines.put("resolution", new PipelineOp("support-resolution", "Resolution",
                    Arrays.asList("issue.diagnosed", "solution.provided", "ticket.closed")));
            pipelines.put("feedback-agg", new PipelineOp("support-feedback", "Feedback Aggregation",
                    Arrays.asList("customer.feedback.received", "trend.identified", "report.generated")));
            this.status = Status.READY;
            metrics.incrementCounter("support.pipelines.registered", "organization", organizationId, "count", "3");
        });
    }

    public Promise<Void> start() {
        return Promise.complete().whenResult(() -> {
            this.status = Status.RUNNING;
            pipelines.values().forEach(p -> p.setRunning(true));
            metrics.incrementCounter("support.pipelines.started", "organization", organizationId);
        });
    }

    public Promise<Void> stop() {
        return Promise.complete().whenResult(() -> {
            this.status = Status.STOPPED;
            pipelines.values().forEach(p -> p.setRunning(false));
            pipelines.clear();
        });
    }

    public Promise<Event> process(Event event) {
        return Promise.ofCallback(cb -> {
            pipelines.values().forEach(p -> {
                if (p.matches(event.getType())) {
                    p.recordEvent(event);
                    metrics.incrementCounter("support.pipeline.matched", "pipeline", p.name, "org", organizationId);
                }
            });
            cb.accept(event, null);
        });
    }

    public Status getHealth() {
        return status;
    }

    public boolean isHealthy() {
        return status == Status.RUNNING || status == Status.READY;
    }

    @Getter
    public static class PipelineOp {

        private final String id, name;
        private final List<String> eventTypes;
        private boolean running;
        private final LinkedList<Long> history = new LinkedList<>();

        public PipelineOp(String id, String name, List<String> et) {
            this.id = id;
            this.name = name;
            this.eventTypes = et;
        }

        public boolean matches(String et) {
            return running && eventTypes.contains(et);
        }

        public void recordEvent(Event e) {
            history.add(System.currentTimeMillis());
            if (history.size() > 1000) {
                history.removeFirst();

            }
        }

        public void setRunning(boolean r) {
            this.running = r;
        }
    }

    public enum Status {
        INITIALIZED, READY, RUNNING, STOPPED, ERROR
    }
}
