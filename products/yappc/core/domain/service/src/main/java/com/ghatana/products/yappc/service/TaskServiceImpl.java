package com.ghatana.products.yappc.service;

import com.ghatana.products.yappc.domain.task.*;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of TaskService that dynamically executes tasks.
 *
 * @doc.type class
 * @doc.purpose Dynamic task execution service implementation
 * @doc.layer product
 * @doc.pattern Service
 */
public class TaskServiceImpl implements TaskService {

    private static final Logger LOG = LoggerFactory.getLogger(TaskServiceImpl.class);

    private final TaskRegistry taskRegistry;
    private final TaskOrchestrator orchestrator;
    private final TaskValidator validator;
    private final Map<String, TaskExecution> executionStore;

    public TaskServiceImpl(
            @NotNull TaskRegistry taskRegistry,
            @NotNull TaskOrchestrator orchestrator,
            @NotNull TaskValidator validator
    ) {
        this.taskRegistry = taskRegistry;
        this.orchestrator = orchestrator;
        this.validator = validator;
        this.executionStore = new ConcurrentHashMap<>();
    }

    @Override
    @NotNull
    public <TInput, TOutput> Promise<TaskResult<TOutput>> executeTask(
            @NotNull String taskId,
            @NotNull TInput input,
            @NotNull TaskExecutionContext context
    ) {
        String executionId = generateExecutionId();
        Instant startTime = Instant.now();

        LOG.info("Executing task: {} (execution: {}, user: {})", taskId, executionId, context.userId());

        // 1. Get task definition
        TaskDefinition task;
        try {
            task = taskRegistry.getTask(taskId)
                .orElseThrow(() -> new TaskNotFoundException("Task not found: " + taskId));
        } catch (Exception e) {
            return Promise.ofException(e);
        }

        LOG.debug("Found task definition: {} (domain: {}, phase: {})",
            task.name(), task.domain(), task.phase());

        // 2. Validate input
        ValidationResult validation = validator.validateInput(task, input);
        if (!validation.valid()) {
            LOG.warn("Task input validation failed for {}: {}", taskId, validation.errors());
            return Promise.of(TaskResult.<TOutput>failure(
                executionId,
                taskId,
                "Validation failed: " + String.join(", ", validation.errors())
            ));
        }

        // 3. Create execution record
        TaskExecution execution = new TaskExecution(
            executionId,
            taskId,
            context.userId(),
            context.projectId(),
            input,
            null,
            TaskExecutionStatus.RUNNING,
            null,
            startTime,
            null,
            context.metadata()
        );
        executionStore.put(executionId, execution);

        // 4. Execute task through orchestrator
        return executeTaskWithDependencies(task, input, context)
            .map(output -> {
                Instant endTime = Instant.now();
                LOG.info("Task {} completed successfully (duration: {}ms)",
                    taskId, endTime.toEpochMilli() - startTime.toEpochMilli());

                // Update execution record
                TaskExecution completedExecution = new TaskExecution(
                    executionId,
                    taskId,
                    context.userId(),
                    context.projectId(),
                    input,
                    output,
                    TaskExecutionStatus.COMPLETED,
                    null,
                    startTime,
                    endTime,
                    context.metadata()
                );
                executionStore.put(executionId, completedExecution);

                return new TaskResult<TOutput>(
                    executionId,
                    taskId,
                    TaskExecutionStatus.COMPLETED,
                    (TOutput) output,
                    null,
                    startTime,
                    endTime,
                    Map.of(
                        "durationMs", endTime.toEpochMilli() - startTime.toEpochMilli(),
                        "taskName", task.name(),
                        "domain", task.domain()
                    )
                );
            })
            .whenException(error -> {
                Instant endTime = Instant.now();
                LOG.error("Task {} failed: {}", taskId, error.getMessage(), error);

                // Update execution record
                TaskExecution failedExecution = new TaskExecution(
                    executionId,
                    taskId,
                    context.userId(),
                    context.projectId(),
                    input,
                    null,
                    TaskExecutionStatus.FAILED,
                    error.getMessage(),
                    startTime,
                    endTime,
                    context.metadata()
                );
                executionStore.put(executionId, failedExecution);
            });
    }

    @NotNull
    private <TInput> Promise<Object> executeTaskWithDependencies(
            @NotNull TaskDefinition root,
            @NotNull TInput input,
            @NotNull TaskExecutionContext context
    ) {
        boolean includeOptional = Boolean.TRUE.equals(context.metadata().get("includeOptionalDependencies"));
        boolean skipDependencies = Boolean.TRUE.equals(context.metadata().get("skipDependencies"));

        if (skipDependencies || root.dependencies().isEmpty()) {
            return orchestrator.execute(root, input, context).map(o -> (Object) o);
        }

        Map<String, TaskDefinition> graph = new HashMap<>();
        collectTaskGraph(root, includeOptional, graph);
        List<List<TaskDefinition>> stages = buildStages(graph, includeOptional);

        Map<String, Object> outputs = new ConcurrentHashMap<>();

        Promise<Void> chain = Promise.complete();
        for (List<TaskDefinition> stage : stages) {
            chain = chain.then(v -> executeStage(stage, input, outputs, context));
        }

        return chain.map(v -> outputs.get(root.id()));
    }

    private void collectTaskGraph(
            @NotNull TaskDefinition root,
            boolean includeOptional,
            @NotNull Map<String, TaskDefinition> graph
    ) {
        Deque<TaskDefinition> queue = new ArrayDeque<>();
        queue.add(root);

        while (!queue.isEmpty()) {
            TaskDefinition current = queue.removeFirst();
            if (graph.putIfAbsent(current.id(), current) != null) {
                continue;
            }

            for (TaskDependency dep : current.dependencies()) {
                if (!dep.required() && !includeOptional) {
                    continue;
                }

                TaskDefinition depTask = taskRegistry.getTask(dep.taskId())
                        .orElseThrow(() -> new TaskNotFoundException("Dependency task not found: " + dep.taskId()));
                queue.add(depTask);
            }
        }
    }

    @NotNull
    private List<List<TaskDefinition>> buildStages(
            @NotNull Map<String, TaskDefinition> graph,
            boolean includeOptional
    ) {
        Map<String, Set<String>> remainingDeps = new HashMap<>();
        Map<String, TaskDefinition> remaining = new HashMap<>(graph);

        for (TaskDefinition task : graph.values()) {
            Set<String> deps = task.dependencies().stream()
                    .filter(d -> includeOptional || d.required())
                    .map(TaskDependency::taskId)
                    .collect(java.util.stream.Collectors.toCollection(HashSet::new));
            remainingDeps.put(task.id(), deps);
        }

        List<List<TaskDefinition>> stages = new ArrayList<>();

        while (!remaining.isEmpty()) {
            List<TaskDefinition> stage = new ArrayList<>();
            for (Map.Entry<String, Set<String>> entry : new HashMap<>(remainingDeps).entrySet()) {
                if (entry.getValue().isEmpty()) {
                    stage.add(remaining.get(entry.getKey()));
                }
            }

            if (stage.isEmpty()) {
                throw new IllegalArgumentException("Circular dependency detected in task graph");
            }

            for (TaskDefinition completed : stage) {
                remaining.remove(completed.id());
                remainingDeps.remove(completed.id());
                for (Set<String> deps : remainingDeps.values()) {
                    deps.remove(completed.id());
                }
            }

            stages.add(stage);
        }

        return stages;
    }

    @NotNull
    private <TInput> Promise<Void> executeStage(
            @NotNull List<TaskDefinition> stage,
            @NotNull TInput baseInput,
            @NotNull Map<String, Object> outputs,
            @NotNull TaskExecutionContext context
    ) {
        List<Promise<Void>> stepPromises = stage.stream()
                .map(task -> {
                    Object effectiveInput = enrichInputWithOutputs(baseInput, outputs);
                    return orchestrator.execute(task, effectiveInput, context)
                            .map(out -> {
                                outputs.put(task.id(), out);
                                return null;
                            })
                            .toVoid();
                })
                .toList();

        return io.activej.promise.Promises.all(stepPromises).toVoid();
    }

    @NotNull
    private Object enrichInputWithOutputs(@NotNull Object baseInput, @NotNull Map<String, Object> outputs) {
        if (!(baseInput instanceof Map<?, ?> rawMap)) {
            return baseInput;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> input = (Map<String, Object>) rawMap;

        Map<String, Object> enriched = new HashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof String s && s.startsWith("@")) {
                String refId = s.substring(1);
                if (outputs.containsKey(refId)) {
                    enriched.put(entry.getKey(), outputs.get(refId));
                    continue;
                }
            }
            enriched.put(entry.getKey(), value);
        }

        enriched.putIfAbsent("dependencyOutputs", Map.copyOf(outputs));
        return enriched;
    }

    @Override
    @NotNull
    public Promise<List<TaskDefinition>> listTasks() {
        return Promise.of(taskRegistry.getAllTasks());
    }

    @Override
    @NotNull
    public Promise<List<TaskDefinition>> listTasksByDomain(@NotNull String domain) {
        return Promise.of(taskRegistry.getTasksByDomain(domain));
    }

    @Override
    @NotNull
    public Promise<Optional<TaskDefinition>> getTaskDefinition(@NotNull String taskId) {
        return Promise.of(taskRegistry.getTask(taskId));
    }

    @Override
    @NotNull
    public Promise<List<TaskDefinition>> searchTasks(@NotNull String query) {
        return Promise.of(taskRegistry.searchTasks(query));
    }

    @Override
    @NotNull
    public Promise<TaskRegistrationResult> registerTask(@NotNull TaskDefinition definition) {
        try {
            // Validate task definition
            ValidationResult validation = validator.validateTaskDefinition(definition);
            if (!validation.valid()) {
                return Promise.of(TaskRegistrationResult.failure(
                        "Invalid task definition: " + String.join(", ", validation.errors())
                ));
            }

            // Check for conflicts
            if (taskRegistry.hasTask(definition.id())) {
                return Promise.of(TaskRegistrationResult.conflict(
                        "Task already exists: " + definition.id()
                ));
            }

            // Register task
            taskRegistry.register(definition);
            LOG.info("Task registered: {} ({})", definition.id(), definition.name());

            return Promise.of(TaskRegistrationResult.success(definition.id()));
        } catch (Exception e) {
            LOG.error("Failed to register task: {}", definition.id(), e);
            return Promise.of(TaskRegistrationResult.failure("Registration failed: " + e.getMessage()));
        }
    }

    @Override
    @NotNull
    public Promise<TaskRemovalResult> unregisterTask(@NotNull String taskId) {
        boolean removed = taskRegistry.unregister(taskId);
        if (removed) {
            LOG.info("Task unregistered: {}", taskId);
            return Promise.of(TaskRemovalResult.successful());
        }
        return Promise.of(TaskRemovalResult.notFound(taskId));
    }

    @Override
    @NotNull
    public Promise<ValidationResult> validateTaskInput(@NotNull String taskId, @NotNull Object input) {
        Optional<TaskDefinition> taskOpt = taskRegistry.getTask(taskId);
        if (taskOpt.isEmpty()) {
            return Promise.of(ValidationResult.failure("Task not found: " + taskId));
        }
        return Promise.of(validator.validateInput(taskOpt.get(), input));
    }

    @Override
    @NotNull
    public Promise<TaskCostEstimate> estimateTaskCost(@NotNull String taskId, @NotNull Object input) {
        Optional<TaskDefinition> taskOpt = taskRegistry.getTask(taskId);
        if (taskOpt.isEmpty()) {
            return Promise.of(TaskCostEstimate.zero());
        }
        return Promise.of(TaskCostEstimate.fromComplexity(taskOpt.get().complexity()));
    }

    @Override
    @NotNull
    public Promise<List<TaskExecution>> getTaskHistory(@NotNull TaskHistoryFilter filter) {
        return Promise.of(
            executionStore.values().stream()
                .filter(execution -> matchesFilter(execution, filter))
                .limit(filter.limit())
                .toList()
        );
    }

    @Override
    @NotNull
    public Promise<Optional<TaskExecution>> getTaskExecution(@NotNull String executionId) {
        return Promise.of(Optional.ofNullable(executionStore.get(executionId)));
    }

    @Override
    @NotNull
    public Promise<TaskCancellationResult> cancelTaskExecution(@NotNull String executionId) {
        TaskExecution execution = executionStore.get(executionId);
        if (execution == null) {
            return Promise.of(TaskCancellationResult.notFound(executionId));
        }

        if (execution.status().isTerminal()) {
            return Promise.of(TaskCancellationResult.alreadyCompleted());
        }

        // Update execution to cancelled
        TaskExecution cancelledExecution = new TaskExecution(
                execution.id(),
                execution.taskId(),
                execution.userId(),
                execution.projectId(),
                execution.input(),
                null,
                TaskExecutionStatus.CANCELLED,
                "Cancelled by user",
                execution.startTime(),
                Instant.now(),
                execution.metadata()
        );
        executionStore.put(executionId, cancelledExecution);

        LOG.info("Task execution cancelled: {}", executionId);
        return Promise.of(TaskCancellationResult.successful());
    }

    private boolean matchesFilter(TaskExecution execution, TaskHistoryFilter filter) {
        if (filter.userId() != null && !filter.userId().equals(execution.userId())) {
            return false;
        }
        if (filter.projectId() != null && !filter.projectId().equals(execution.projectId())) {
            return false;
        }
        if (filter.taskId() != null && !filter.taskId().equals(execution.taskId())) {
            return false;
        }
        if (filter.status() != null && !filter.status().equals(execution.status())) {
            return false;
        }
        if (filter.startTime() != null && execution.startTime().isBefore(filter.startTime())) {
            return false;
        }
        if (filter.endTime() != null && execution.endTime() != null && execution.endTime().isAfter(filter.endTime())) {
            return false;
        }
        return true;
    }

    private String generateExecutionId() {
        return "exec-" + UUID.randomUUID().toString();
    }

    /**
     * Exception thrown when a task is not found.
     */
    public static class TaskNotFoundException extends RuntimeException {
        public TaskNotFoundException(String message) {
            super(message);
        }
    }
}
