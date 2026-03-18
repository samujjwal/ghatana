package com.ghatana.softwareorg.hr;

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
public class HrPipelinesRegistrar {

    private final String operatorId, organizationId;
    private final MetricsCollector metrics;
    private final Map<String, PipelineOp> pipelines = new LinkedHashMap<>();
    private Status status = Status.INITIALIZED;

    public Promise<Void> initialize() {
        return Promise.complete().whenResult(() -> {
            pipelines.put("recruiting", new PipelineOp("hr-recruiting", "Recruiting",
                    Arrays.asList("job.posted", "candidate.screened", "offer.made")));
            pipelines.put("performance-management", new PipelineOp("hr-performance", "Performance",
                    Arrays.asList("review.initiated", "goals.set", "feedback.collected", "rating.assigned")));
            pipelines.put("capacity-planning", new PipelineOp("hr-capacity", "Capacity Planning",
                    Arrays.asList("headcount.forecasted", "skills.assessed", "plan.updated")));
            this.status = Status.READY;
            metrics.incrementCounter("hr.pipelines.registered", "organization", organizationId, "count", "3");
        });
    }

    public Promise<Void> start() {
        return Promise.complete().whenResult(() -> {
            this.status = Status.RUNNING;
            pipelines.values().forEach(p -> p.setRunning(true));
            metrics.incrementCounter("hr.pipelines.started", "organization", organizationId);
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
                    metrics.incrementCounter("hr.pipeline.matched", "pipeline", p.name, "org", organizationId);
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
