package com.ghatana.yappc.services.run;

import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.domain.run.*;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Orchestrates build, deploy, and test execution
 * @doc.layer service
 * @doc.pattern Service
 */
public class RunServiceImpl implements RunService {
    
    private static final Logger log = LoggerFactory.getLogger(RunServiceImpl.class);
    
    private final AuditLogger auditLogger;
    private final MetricsCollector metrics;
    
    public RunServiceImpl(
            AuditLogger auditLogger,
            MetricsCollector metrics) {
        this.auditLogger = auditLogger;
        this.metrics = metrics;
    }
    
    @Override
    public Promise<RunResult> execute(RunSpec spec) {
        return executeWithObservation(spec, ObservationConfig.defaultConfig());
    }
    
    @Override
    public Promise<RunResult> executeWithObservation(RunSpec spec, ObservationConfig config) {
        long startTime = System.currentTimeMillis();
        Instant startedAt = Instant.now();
        
        return executeTasks(spec)
                .then(taskResults -> {
                    long duration = System.currentTimeMillis() - startTime;
                    RunStatus status = determineOverallStatus(taskResults);
                    
                    RunResult result = RunResult.builder()
                            .id(UUID.randomUUID().toString())
                            .runSpecRef(spec.id())
                            .status(status)
                            .taskResults(taskResults)
                            .startedAt(startedAt)
                            .completedAt(Instant.now())
                            .metadata(Map.of("duration_ms", String.valueOf(duration),
                                    "environment", spec.environment()))
                            .build();
                    
                    metrics.recordTimer("yappc.run.execute", duration,
                        Map.of("environment", spec.environment(), "status", status.name()));
                    
                    return auditLogger.log(createAuditEvent("run.execute", spec, result))
                            .map(v -> result);
                })
                .whenException(e -> {
                    log.error("Run execution failed", e);
                    metrics.incrementCounter("yappc.run.error",
                        Map.of("error", e.getClass().getSimpleName()));
                });
    }
    
    @Override
    public Promise<RunResult> rollback(String deploymentId, String targetVersion) {
        long startTime = System.currentTimeMillis();
        
        return performRollback(deploymentId, targetVersion)
                .then(result -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("yappc.run.rollback", duration,
                        Map.of("deployment", deploymentId));
                    
                    return auditLogger.log(createAuditEvent("run.rollback", 
                            Map.of("deploymentId", deploymentId, "targetVersion", targetVersion), 
                            result))
                            .map(v -> result);
                })
                .whenException(e -> {
                    log.error("Rollback failed", e);
                    metrics.incrementCounter("yappc.run.rollback.error",
                        Map.of("error", e.getClass().getSimpleName()));
                });
    }
    
    @Override
    public Promise<RunResult> promote(String deploymentId, String targetEnvironment) {
        long startTime = System.currentTimeMillis();
        
        return performPromotion(deploymentId, targetEnvironment)
                .then(result -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metrics.recordTimer("yappc.run.promote", duration,
                        Map.of("deployment", deploymentId, "target", targetEnvironment));
                    
                    return auditLogger.log(createAuditEvent("run.promote",
                            Map.of("deploymentId", deploymentId, "targetEnvironment", targetEnvironment),
                            result))
                            .map(v -> result);
                })
                .whenException(e -> {
                    log.error("Promotion failed", e);
                    metrics.incrementCounter("yappc.run.promote.error",
                        Map.of("error", e.getClass().getSimpleName()));
                });
    }
    
    private Promise<List<TaskResult>> executeTasks(RunSpec spec) {
        List<Promise<TaskResult>> taskPromises = new ArrayList<>();
        
        for (RunTask task : spec.tasks()) {
            taskPromises.add(executeTask(task));
        }
        
        return Promises.toList(taskPromises);
    }
    
    private Promise<TaskResult> executeTask(RunTask task) {
        long startTime = System.currentTimeMillis();
        
        return switch (task.type()) {
            case "build" -> executeBuildTask(task);
            case "test" -> executeTestTask(task);
            case "deploy" -> executeDeployTask(task);
            case "migrate" -> executeMigrateTask(task);
            default -> Promise.of(TaskResult.builder()
                    .taskId(task.id())
                    .status(RunStatus.FAILED)
                    .error("Unknown task type: " + task.type())
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build());
        };
    }
    
    private Promise<TaskResult> executeBuildTask(RunTask task) {
        long startTime = System.currentTimeMillis();
        
        // Simulate build execution
        return Promise.of(TaskResult.builder()
                .taskId(task.id())
                .status(RunStatus.SUCCESS)
                .output("Build completed successfully")
                .durationMs(System.currentTimeMillis() - startTime)
                .build());
    }
    
    private Promise<TaskResult> executeTestTask(RunTask task) {
        long startTime = System.currentTimeMillis();
        
        // Simulate test execution
        return Promise.of(TaskResult.builder()
                .taskId(task.id())
                .status(RunStatus.SUCCESS)
                .output("All tests passed")
                .durationMs(System.currentTimeMillis() - startTime)
                .build());
    }
    
    private Promise<TaskResult> executeDeployTask(RunTask task) {
        long startTime = System.currentTimeMillis();
        
        // Simulate deployment
        return Promise.of(TaskResult.builder()
                .taskId(task.id())
                .status(RunStatus.SUCCESS)
                .output("Deployment completed")
                .durationMs(System.currentTimeMillis() - startTime)
                .build());
    }
    
    private Promise<TaskResult> executeMigrateTask(RunTask task) {
        long startTime = System.currentTimeMillis();
        
        // Simulate migration
        return Promise.of(TaskResult.builder()
                .taskId(task.id())
                .status(RunStatus.SUCCESS)
                .output("Migration completed")
                .durationMs(System.currentTimeMillis() - startTime)
                .build());
    }
    
    private Promise<RunResult> performRollback(String deploymentId, String targetVersion) {
        return Promise.of(RunResult.builder()
                .id(UUID.randomUUID().toString())
                .runSpecRef(deploymentId)
                .status(RunStatus.SUCCESS)
                .taskResults(List.of())
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .metadata(Map.of("rollback_to", targetVersion))
                .build());
    }
    
    private Promise<RunResult> performPromotion(String deploymentId, String targetEnvironment) {
        return Promise.of(RunResult.builder()
                .id(UUID.randomUUID().toString())
                .runSpecRef(deploymentId)
                .status(RunStatus.SUCCESS)
                .taskResults(List.of())
                .startedAt(Instant.now())
                .completedAt(Instant.now())
                .metadata(Map.of("promoted_to", targetEnvironment,
                        "environment", targetEnvironment))
                .build());
    }
    
    private RunStatus determineOverallStatus(List<TaskResult> taskResults) {
        if (taskResults.stream().allMatch(r -> r.status() == RunStatus.SUCCESS)) {
            return RunStatus.SUCCESS;
        } else if (taskResults.stream().anyMatch(r -> r.status() == RunStatus.FAILED)) {
            return RunStatus.FAILED;
        } else if (taskResults.stream().anyMatch(r -> r.status() == RunStatus.CANCELLED)) {
            return RunStatus.CANCELLED;
        } else {
            return RunStatus.RUNNING;
        }
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
