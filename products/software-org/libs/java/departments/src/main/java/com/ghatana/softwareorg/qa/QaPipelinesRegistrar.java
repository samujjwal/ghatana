package com.ghatana.softwareorg.qa;

import com.ghatana.platform.domain.domain.event.Event;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * QA Pipelines Registrar - Registers and manages 3 core QA pipelines. 1. Test
 * Execution: Test plans → Test runs → Results 2. Quality Gate: Build artifacts
 * → Quality checks → Pass/Fail 3. Coverage Monitoring: Code coverage → Trend
 * analysis → Reports
 */
@Slf4j
@RequiredArgsConstructor
@Getter
public class QaPipelinesRegistrar {

    private final String operatorId;
    private final String organizationId;
    private final MetricsCollector metrics;
    private final Map<String, PipelineOperator> pipelines = new LinkedHashMap<>();
    private Status status = Status.INITIALIZED;

    public Promise<Void> initialize() {
        return Promise.complete().whenResult(() -> {
            try {
                pipelines.put("test-execution", new PipelineOperator("qa-test-execution", "Test Execution",
                        Arrays.asList("test.plan.created", "test.started", "test.completed")));
                pipelines.put("quality-gate", new PipelineOperator("qa-quality-gate", "Quality Gate",
                        Arrays.asList("build.ready", "quality.check.started", "quality.gate.passed")));
                pipelines.put("coverage-monitoring", new PipelineOperator("qa-coverage-monitoring", "Coverage Monitoring",
                        Arrays.asList("coverage.measured", "coverage.analyzed", "report.generated")));
                this.status = Status.READY;
                metrics.incrementCounter("qa.pipelines.registered", "organization", organizationId, "count", "3");
            } catch (Exception e) {
                this.status = Status.ERROR;
                metrics.incrementCounter("qa.pipelines.error", "organization", organizationId);
                throw e;
            }
        });
    }

    public Promise<Void> start() {
        return Promise.complete().whenResult(() -> {
            this.status = Status.RUNNING;
            pipelines.values().forEach(p -> p.setRunning(true));
            metrics.incrementCounter("qa.pipelines.started", "organization", organizationId);
        });
    }

    public Promise<Void> stop() {
        return Promise.complete().whenResult(() -> {
            this.status = Status.STOPPED;
            pipelines.values().forEach(p -> p.setRunning(false));
            pipelines.clear();
            metrics.incrementCounter("qa.pipelines.stopped", "organization", organizationId);
        });
    }

    public Promise<Event> process(Event event) {
        return Promise.ofCallback(cb -> {
            try {
                for (PipelineOperator p : pipelines.values()) {
                    if (p.matches(event.getType())) {
                        p.recordEvent(event);
                        metrics.incrementCounter("qa.pipeline.matched", "pipeline", p.name, "organization", organizationId);
                    }
                }
                cb.accept(event, null);
            } catch (Exception e) {
                metrics.incrementCounter("qa.pipeline.error", "organization", organizationId);
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
