package com.ghatana.yappc.services.run;

import com.ghatana.audit.AuditLogger;
import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.yappc.common.ServiceObservability;
import com.ghatana.yappc.domain.run.*;
import com.ghatana.yappc.services.learn.LearningEvidenceService;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final CiCdPort ciCdAdapter;
    private final LearningEvidenceService learningEvidenceService;
    private final boolean persistLearningEvidence;

    public RunServiceImpl(
            AuditLogger auditLogger,
            MetricsCollector metrics,
            CiCdPort ciCdAdapter) {
        this(auditLogger, metrics, ciCdAdapter, LearningEvidenceService.noop(), false);
    }

    public RunServiceImpl(
            AuditLogger auditLogger,
            MetricsCollector metrics,
            CiCdPort ciCdAdapter,
            LearningEvidenceService learningEvidenceService) {
        this(auditLogger, metrics, ciCdAdapter, learningEvidenceService, true);
    }

    private RunServiceImpl(
            AuditLogger auditLogger,
            MetricsCollector metrics,
            CiCdPort ciCdAdapter,
            LearningEvidenceService learningEvidenceService,
            boolean persistLearningEvidence) {
        this.auditLogger = auditLogger;
        this.metrics = metrics;
        this.ciCdAdapter = ciCdAdapter != null ? ciCdAdapter : new NoOpCiCdAdapter();
        this.learningEvidenceService = learningEvidenceService == null
                ? LearningEvidenceService.noop()
                : learningEvidenceService;
        this.persistLearningEvidence = persistLearningEvidence;
    }

    @Override
    public Promise<RunResult> execute(RunSpec spec) {
        return executeWithObservation(spec, ObservationConfig.defaultConfig());
    }

    @Override
    public Promise<RunResult> executeWithObservation(RunSpec spec, ObservationConfig config) {
        if (spec == null || spec.id() == null || spec.id().isBlank()) {
            return Promise.ofException(new IllegalArgumentException("RunSpec.id is required"));
        }

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

                    Map<String, String> tags = Map.of("environment", spec.environment(), "status", status.name());
                    metrics.recordTimer("yappc.run.execute", duration, tags);
                    ServiceObservability.incrementSuccess(metrics, "yappc.run.execute", tags);

                    return auditLogger.log(ServiceObservability.auditEvent("run.execute", spec, result))
                            .then(v -> recordFailedRunLearningEvidence(spec, result))
                            .map(v -> result);
                })
                .whenException(e -> {
                    log.error("Run execution failed", e);
                    ServiceObservability.incrementFailure(
                        metrics,
                        "yappc.run.execute",
                        e,
                        Map.of("environment", spec.environment()));
                });
    }

    @Override
    public Promise<RunResult> retry(String failedRunId, RunSpec spec) {
        if (failedRunId == null || failedRunId.isBlank()) {
            return Promise.ofException(new IllegalArgumentException("failedRunId is required"));
        }
        if (spec == null || spec.id() == null || spec.id().isBlank()) {
            return Promise.ofException(new IllegalArgumentException("RunSpec.id is required"));
        }

        long startTime = System.currentTimeMillis();
        return executeWithObservation(spec, ObservationConfig.defaultConfig())
                .then(result -> {
                    long duration = System.currentTimeMillis() - startTime;
                    RunResult retryResult = RunResult.builder()
                            .id(result.id())
                            .runSpecRef(result.runSpecRef())
                            .status(result.status())
                            .taskResults(result.taskResults())
                            .startedAt(result.startedAt())
                            .completedAt(result.completedAt())
                            .metadata(mergeMetadata(result.metadata(), Map.of(
                                    "retry_of", failedRunId,
                                    "retry_run_spec", spec.id()
                            )))
                            .build();
                    Map<String, String> tags = Map.of("environment", spec.environment(), "status", result.status().name());
                    metrics.recordTimer("yappc.run.retry", duration, tags);
                    ServiceObservability.incrementSuccess(metrics, "yappc.run.retry", tags);
                    return auditLogger.log(ServiceObservability.auditEvent("run.retry",
                                    Map.of("failedRunId", failedRunId, "runSpecId", spec.id()),
                                    retryResult))
                            .map(v -> retryResult);
                })
                .whenException(e -> {
                    log.error("Run retry failed", e);
                    ServiceObservability.incrementFailure(
                            metrics,
                            "yappc.run.retry",
                            e,
                            Map.of("failedRunId", failedRunId));
                });
    }

    @Override
    public Promise<RunResult> rollback(String deploymentId, String targetVersion) {
        long startTime = System.currentTimeMillis();

        return performRollback(deploymentId, targetVersion)
                .then(result -> {
                    long duration = System.currentTimeMillis() - startTime;
                    Map<String, String> tags = Map.of("deployment", deploymentId);
                    metrics.recordTimer("yappc.run.rollback", duration, tags);
                    ServiceObservability.incrementSuccess(metrics, "yappc.run.rollback", tags);

                    return auditLogger.log(ServiceObservability.auditEvent("run.rollback",
                            Map.of("deploymentId", deploymentId, "targetVersion", targetVersion),
                            result))
                            .map(v -> result);
                })
                .whenException(e -> {
                    log.error("Rollback failed", e);
                    ServiceObservability.incrementFailure(
                        metrics,
                        "yappc.run.rollback",
                        e,
                        Map.of("deployment", deploymentId));
                });
    }

    @Override
    public Promise<RunResult> promote(String deploymentId, String targetEnvironment) {
        long startTime = System.currentTimeMillis();

        return performPromotion(deploymentId, targetEnvironment)
                .then(result -> {
                    long duration = System.currentTimeMillis() - startTime;
                    Map<String, String> tags = Map.of("deployment", deploymentId, "target", targetEnvironment);
                    metrics.recordTimer("yappc.run.promote", duration, tags);
                    ServiceObservability.incrementSuccess(metrics, "yappc.run.promote", tags);

                    return auditLogger.log(ServiceObservability.auditEvent("run.promote",
                            Map.of("deploymentId", deploymentId, "targetEnvironment", targetEnvironment),
                            result))
                            .map(v -> result);
                })
                .whenException(e -> {
                    log.error("Promotion failed", e);
                    ServiceObservability.incrementFailure(
                        metrics,
                        "yappc.run.promote",
                        e,
                        Map.of("deployment", deploymentId, "target", targetEnvironment));
                });
    }

    private Promise<List<TaskResult>> executeTasks(RunSpec spec) {
        List<RunTask> orderedTasks = sortTasksByDependencies(spec.tasks());
        List<Promise<TaskResult>> taskPromises = new ArrayList<>();

        for (RunTask task : orderedTasks) {
            taskPromises.add(executeTask(task));
        }

        return Promises.toList(taskPromises);
    }

    private Promise<TaskResult> executeTask(RunTask task) {
        long startTime = System.currentTimeMillis();

        if (task == null || task.id() == null || task.id().isBlank()) {
            return Promise.of(TaskResult.builder()
                    .taskId("unknown")
                    .status(RunStatus.FAILED)
                    .error("Task id is required")
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build());
        }

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

        if (isFailureInjected(task)) {
            return Promise.of(TaskResult.builder()
                .taskId(task.id())
                .status(RunStatus.FAILED)
                .error("Build failed due to injected failure")
                .durationMs(System.currentTimeMillis() - startTime)
                .build());
        }

        try {
            return ciCdAdapter.build(task)
                .then(
                    result -> Promise.of(result),
                    ex -> Promise.of(TaskResult.builder()
                        .taskId(task.id())
                        .status(RunStatus.FAILED)
                        .error(ex.getMessage())
                        .durationMs(System.currentTimeMillis() - startTime)
                        .build())
                );
        } catch (Exception ex) {
            return Promise.of(TaskResult.builder()
                .taskId(task.id())
                .status(RunStatus.FAILED)
                .error(ex.getMessage())
                .durationMs(System.currentTimeMillis() - startTime)
                .build());
        }
    }

    private Promise<TaskResult> executeTestTask(RunTask task) {
        long startTime = System.currentTimeMillis();

        if (isFailureInjected(task)) {
            return Promise.of(TaskResult.builder()
                .taskId(task.id())
                .status(RunStatus.FAILED)
                .error("Test suite failed due to injected failure")
                .durationMs(System.currentTimeMillis() - startTime)
                .build());
        }

        try {
            return ciCdAdapter.test(task)
                .then(
                    result -> Promise.of(result),
                    ex -> Promise.of(TaskResult.builder()
                        .taskId(task.id())
                        .status(RunStatus.FAILED)
                        .error(ex.getMessage())
                        .durationMs(System.currentTimeMillis() - startTime)
                        .build())
                );
        } catch (Exception ex) {
            return Promise.of(TaskResult.builder()
                .taskId(task.id())
                .status(RunStatus.FAILED)
                .error(ex.getMessage())
                .durationMs(System.currentTimeMillis() - startTime)
                .build());
        }
    }

    private Promise<TaskResult> executeDeployTask(RunTask task) {
        long startTime = System.currentTimeMillis();

        if (isFailureInjected(task)) {
            return Promise.of(TaskResult.builder()
                    .taskId(task.id())
                    .status(RunStatus.FAILED)
                    .error("Deployment failed due to injected failure")
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build());
        }

        try {
            return ciCdAdapter.deploy(task)
                .then(
                    result -> Promise.of(result),
                    ex -> Promise.of(TaskResult.builder()
                        .taskId(task.id())
                        .status(RunStatus.FAILED)
                        .error(ex.getMessage())
                        .durationMs(System.currentTimeMillis() - startTime)
                        .build())
                );
        } catch (Exception ex) {
            return Promise.of(TaskResult.builder()
                .taskId(task.id())
                .status(RunStatus.FAILED)
                .error(ex.getMessage())
                .durationMs(System.currentTimeMillis() - startTime)
                .build());
        }
    }

    private Promise<TaskResult> executeMigrateTask(RunTask task) {
        long startTime = System.currentTimeMillis();

        if (isFailureInjected(task)) {
            return Promise.of(TaskResult.builder()
                .taskId(task.id())
                .status(RunStatus.FAILED)
                .error("Migration failed due to injected failure")
                .durationMs(System.currentTimeMillis() - startTime)
                .build());
        }

        try {
            return ciCdAdapter.migrate(task)
                .then(
                    result -> Promise.of(result),
                    ex -> Promise.of(TaskResult.builder()
                        .taskId(task.id())
                        .status(RunStatus.FAILED)
                        .error(ex.getMessage())
                        .durationMs(System.currentTimeMillis() - startTime)
                        .build())
                );
        } catch (Exception ex) {
            return Promise.of(TaskResult.builder()
                .taskId(task.id())
                .status(RunStatus.FAILED)
                .error(ex.getMessage())
                .durationMs(System.currentTimeMillis() - startTime)
                .build());
        }
    }

    private Promise<RunResult> performRollback(String deploymentId, String targetVersion) {
        return ciCdAdapter.rollback(deploymentId, targetVersion)
            .map(taskResult -> RunResult.builder()
                        .id(UUID.randomUUID().toString())
                        .runSpecRef(deploymentId)
                        .status(taskResult.status())
                        .taskResults(List.of(taskResult))
                        .startedAt(Instant.now())
                        .completedAt(Instant.now())
                        .metadata(Map.of("rollback_to", targetVersion))
                        .build());
    }

    private Promise<RunResult> performPromotion(String deploymentId, String targetEnvironment) {
        // Promotion is typically a deploy operation to a different environment
        RunTask deployTask = RunTask.builder()
                .id("promote-" + deploymentId)
                .type("deploy")
                .name("Promote " + deploymentId + " to " + targetEnvironment)
                .config(Map.of("environment", targetEnvironment, "deploymentId", deploymentId))
                .dependencies(List.of())
                .build();

        return ciCdAdapter.deploy(deployTask)
            .map(taskResult -> RunResult.builder()
                        .id(UUID.randomUUID().toString())
                        .runSpecRef(deploymentId)
                        .status(taskResult.status())
                        .taskResults(List.of(taskResult))
                        .startedAt(Instant.now())
                        .completedAt(Instant.now())
                        .metadata(Map.of("promoted_to", targetEnvironment))
                        .build());
    }

    private RunStatus determineOverallStatus(List<TaskResult> taskResults) {
        if (taskResults.stream().anyMatch(r -> r.status() == RunStatus.FAILED)) {
            return RunStatus.FAILED;
        } else if (taskResults.stream().anyMatch(r -> r.status() == RunStatus.CANCELLED)) {
            return RunStatus.CANCELLED;
        } else if (taskResults.stream().anyMatch(r -> r.status() == RunStatus.NOT_READY)) {
            return RunStatus.NOT_READY;
        } else if (taskResults.stream().allMatch(r -> r.status() == RunStatus.SUCCESS)) {
            return RunStatus.SUCCESS;
        } else {
            return RunStatus.RUNNING;
        }
    }

    private List<RunTask> sortTasksByDependencies(List<RunTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }

        List<RunTask> ordered = new ArrayList<>();
        Set<String> completed = new HashSet<>();
        List<RunTask> remaining = new ArrayList<>(tasks);

        while (!remaining.isEmpty()) {
            int initialRemaining = remaining.size();
            remaining.removeIf(task -> {
                if (task.dependencies() == null || task.dependencies().isEmpty() || completed.containsAll(task.dependencies())) {
                    ordered.add(task);
                    completed.add(task.id());
                    return true;
                }
                return false;
            });

            if (remaining.size() == initialRemaining) {
                ordered.addAll(remaining);
                break;
            }
        }

        return ordered;
    }

    private boolean isFailureInjected(RunTask task) {
        Object failFlag = task.config().get("shouldFail");
        if (failFlag instanceof Boolean value) {
            return value;
        }
        if (failFlag instanceof String value) {
            return Boolean.parseBoolean(value);
        }
        return false;
    }

    private Map<String, String> mergeMetadata(Map<String, String> metadata, Map<String, String> additions) {
        java.util.LinkedHashMap<String, String> merged = new java.util.LinkedHashMap<>();
        if (metadata != null) {
            merged.putAll(metadata);
        }
        merged.putAll(additions);
        return Map.copyOf(merged);
    }

    private Promise<String> recordFailedRunLearningEvidence(RunSpec spec, RunResult result) {
        if (!persistLearningEvidence || result.status() != RunStatus.FAILED) {
            return Promise.of("learning-evidence-not-required");
        }
        LearningEvidenceService.EvidenceContext context = new LearningEvidenceService.EvidenceContext(
                resolveTenantId(spec),
                requiredConfig(spec, "workspaceId"),
                requiredConfig(spec, "projectId"),
                result.id(),
                configValue(spec, "correlationId"),
                Map.of(
                        "runSpecId", spec.id(),
                        "artifactsRef", spec.artifactsRef() == null ? "" : spec.artifactsRef(),
                        "environment", spec.environment() == null ? "" : spec.environment()
                )
        );
        return learningEvidenceService.recordRunOutcome(context, result);
    }

    private String resolveTenantId(RunSpec spec) {
        String tenantId = configValue(spec, "tenantId");
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = TenantContext.getCurrentTenantId();
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("RunSpec.config.tenantId is required for failed run learning evidence");
        }
        if ("default-tenant".equals(tenantId)) {
            throw new IllegalArgumentException("default-tenant is not allowed for failed run learning evidence");
        }
        return tenantId;
    }

    private String requiredConfig(RunSpec spec, String key) {
        String value = configValue(spec, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("RunSpec.config." + key + " is required for failed run learning evidence");
        }
        return value;
    }

    private String configValue(RunSpec spec, String key) {
        if (spec == null || spec.config() == null) {
            return null;
        }
        return spec.config().get(key);
    }

    private String safeTaskName(RunTask task) {
        return task.name() == null || task.name().isBlank() ? task.id() : task.name();
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

}
