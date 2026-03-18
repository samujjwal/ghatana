package com.ghatana.softwareorg.devops;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * DevOps Pipelines Registrar - Manages 3 core DevOps pipelines. 1. Deployment:
 * Artifacts → Staging → Production 2. Incident Detection: Alerts → Triage →
 * Escalation 3. Infrastructure Monitoring: Metrics → Health checks → Reports
 */
@Slf4j
@RequiredArgsConstructor
@Getter
public class DevopsPipelinesRegistrar {

    private final String operatorId;
    private final String organizationId;
    private final MetricsCollector metrics;
    private final Map<String, PipelineOperator> pipelines = new LinkedHashMap<>();
    private Status status = Status.INITIALIZED;

    public Promise<Void> initialize() {
        return Promise.complete().whenResult(() -> {
            try {
                pipelines.put("deployment", new PipelineOperator("devops-deployment", "Deployment Pipeline",
                        Arrays.asList("artifact.ready", "deployment.started", "deployment.succeeded")));
                pipelines.put("incident-detection", new PipelineOperator("devops-incidents", "Incident Detection",
                        Arrays.asList("alert.triggered", "incident.created", "incident.resolved")));
                pipelines.put("infra-monitoring", new PipelineOperator("devops-monitoring", "Infrastructure Monitoring",
                        Arrays.asList("metrics.collected", "health.checked", "report.generated")));
                this.status = Status.READY;
                metrics.incrementCounter("devops.pipelines.registered", "organization", organizationId, "count", "3");
            } catch (Exception e) {
                this.status = Status.ERROR;
                metrics.incrementCounter("devops.pipelines.error", "organization", organizationId);
                throw e;
            }
        });
    }

    public Promise<Void> start() {
        return Promise.complete().whenResult(() -> {
            this.status = Status.RUNNING;
            pipelines.values().forEach(p -> p.setRunning(true));
            metrics.incrementCounter("devops.pipelines.started", "organization", organizationId);
        });
    }

    public Promise<Void> stop() {
        return Promise.complete().whenResult(() -> {
            this.status = Status.STOPPED;
            pipelines.values().forEach(p -> p.setRunning(false));
            pipelines.clear();
            metrics.incrementCounter("devops.pipelines.stopped", "organization", organizationId);
        });
    }

    public Promise<Event> process(Event event) {
        return Promise.ofCallback(cb -> {
            try {
                for (PipelineOperator p : pipelines.values()) {
                    if (p.matches(event.getType())) {
                        p.recordEvent(event);
                        metrics.incrementCounter("devops.pipeline.matched", "pipeline", p.name, "organization", organizationId);
                    }
                }
                cb.accept(event, null);
            } catch (Exception e) {
                metrics.incrementCounter("devops.pipeline.error", "organization", organizationId);
                cb.accept(null, e);
            }
        });
    }

    public Status getHealth() {
        return status;
    }

    public boolean isHealthy() {
        return status == Status.RUNNING || status == Status.READY;
    }

    @Getter
    public static class PipelineOperator {

        private final String id;
        private final String name;
        private final List<String> eventTypes;
        private boolean running;
        private final LinkedList<Long> history = new LinkedList<>();

        public PipelineOperator(String id, String name, List<String> eventTypes) {
            this.id = id;
            this.name = name;
            this.eventTypes = eventTypes;
        }

        public boolean matches(String eventType) {
            return running && eventTypes.contains(eventType);
        }

        public void recordEvent(Event event) {
            history.add(System.currentTimeMillis());
            if (history.size() > 1000) {
                history.removeFirst();
            }
        }

        public void setRunning(boolean running) {
            this.running = running;
        }
    }

    public enum Status {
        INITIALIZED, READY, RUNNING, STOPPED, ERROR
    }
}
