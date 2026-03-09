package com.ghatana.yappc.services.observe;

import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.domain.observe.*;
import com.ghatana.yappc.domain.run.RunResult;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * @doc.type class
 * @doc.purpose Collects runtime observations and telemetry
 * @doc.layer service
 * @doc.pattern Service
 */
public class ObserveServiceImpl implements ObserveService {
    
    private static final Logger log = LoggerFactory.getLogger(ObserveServiceImpl.class);
    
    private final MetricsCollector metrics;
    private final AuditLogger auditLogger;
    
    public ObserveServiceImpl(
            MetricsCollector metrics,
            AuditLogger auditLogger) {
        this.metrics = metrics;
        this.auditLogger = auditLogger;
    }
    
    @Override
    public Promise<Observation> collect(RunResult run) {
        long startTime = System.currentTimeMillis();
        
        return collectObservationData(run)
                .then(observation -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("yappc.observe.collect", duration,
                        Map.of("run_status", run.status().name()));
                    
                    return auditLogger.log(createAuditEvent("observe.collect", run, observation))
                            .map(v -> observation);
                })
                .whenException(e -> {
                    log.error("Observation collection failed", e);
                    metrics.incrementCounter("yappc.observe.error",
                        Map.of("error", e.getClass().getSimpleName()));
                });
    }
    
    @Override
    public Promise<Void> streamObservations(RunResult run, Consumer<Observation> consumer) {
        return collect(run)
                .then(observation -> {
                    consumer.accept(observation);
                    return Promise.complete();
                })
                .whenException(e -> {
                    log.error("Observation streaming failed", e);
                    metrics.incrementCounter("yappc.observe.stream.error",
                        Map.of("error", e.getClass().getSimpleName()));
                });
    }
    
    private Promise<Observation> collectObservationData(RunResult run) {
        List<Metric> collectedMetrics = collectMetrics(run);
        List<LogEntry> collectedLogs = collectLogs(run);
        List<TraceSpan> collectedTraces = collectTraces(run);
        
        Observation observation = Observation.builder()
                .id(UUID.randomUUID().toString())
                .runRef(run.runSpecRef())
                .metrics(collectedMetrics)
                .logs(collectedLogs)
                .traces(collectedTraces)
                .collectedAt(Instant.now())
                .build();
        
        return Promise.of(observation);
    }
    
    private List<Metric> collectMetrics(RunResult run) {
        List<Metric> collectedMetrics = List.of(
            Metric.builder()
                    .name("run.duration")
                    .value(run.completedAt() != null ? 
                        run.completedAt().toEpochMilli() - run.startedAt().toEpochMilli() : 0)
                    .unit("milliseconds")
                    .tags(Map.of("run_id", run.id(), "status", run.status().name()))
                    .timestamp(Instant.now())
                    .build(),
            Metric.builder()
                    .name("run.task_count")
                    .value(run.taskResults().size())
                    .unit("count")
                    .tags(Map.of("run_id", run.id()))
                    .timestamp(Instant.now())
                    .build(),
            Metric.builder()
                    .name("run.success_rate")
                    .value(calculateSuccessRate(run))
                    .unit("percentage")
                    .tags(Map.of("run_id", run.id()))
                    .timestamp(Instant.now())
                    .build()
        );
        
        return collectedMetrics;
    }
    
    private List<LogEntry> collectLogs(RunResult run) {
        List<LogEntry> logs = List.of(
            LogEntry.builder()
                    .level("INFO")
                    .message("Run started")
                    .timestamp(run.startedAt())
                    .context(Map.of("run_id", run.id(), "status", run.status().name()))
                    .build(),
            LogEntry.builder()
                    .level("INFO")
                    .message("Run completed")
                    .timestamp(run.completedAt() != null ? run.completedAt() : Instant.now())
                    .context(Map.of("run_id", run.id(), "status", run.status().name()))
                    .build()
        );
        
        return logs;
    }
    
    private List<TraceSpan> collectTraces(RunResult run) {
        long startTimeMs = run.startedAt().toEpochMilli();
        long endTimeMs = run.completedAt() != null ? 
            run.completedAt().toEpochMilli() : Instant.now().toEpochMilli();
        
        List<TraceSpan> traces = List.of(
            TraceSpan.builder()
                    .traceId(run.id())
                    .spanId(UUID.randomUUID().toString())
                    .parentSpanId(null)
                    .operationName("run.execute")
                    .startTimeMs(startTimeMs)
                    .durationMs(endTimeMs - startTimeMs)
                    .tags(Map.of("run_id", run.id(), "status", run.status().name()))
                    .build()
        );
        
        return traces;
    }
    
    private double calculateSuccessRate(RunResult run) {
        if (run.taskResults().isEmpty()) {
            return 0.0;
        }
        
        long successCount = run.taskResults().stream()
                .filter(r -> r.status().name().equals("SUCCESS"))
                .count();
        
        return (double) successCount / run.taskResults().size() * 100.0;
    }
    
    private Map<String, Object> createAuditEvent(String action, Object input, Object output) {
        return Map.of(
            "action", action,
            "timestamp", Instant.now().toEpochMilli(),
            "input", input.toString(),
            "output", output.toString()
        );
    }
}
