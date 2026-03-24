package com.ghatana.products.yappc.domain.task;

import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Dynamic task execution service with extensible task registry.
 * <p>
 * Tasks are discovered from configuration, plugins, and runtime registration.
 * New tasks can be added/removed without code changes.
 *
 * @doc.type interface
 * @doc.purpose Dynamic task registry and execution
 * @doc.layer product
 * @doc.pattern Registry, Strategy, Plugin Architecture
 */
public interface TaskService {

    /**
     * Executes a task by ID with generic input/output.
     *
     * @param taskId  Unique task identifier (e.g., "explore-ideas", "generate-code")
     * @param input   Task-specific input as Map or typed object
     * @param context Execution context with metadata, user info, etc.
     * @param <TInput>  Input type
     * @param <TOutput> Output type
     * @return Promise of task result
     */
    @NotNull
    <TInput, TOutput> Promise<TaskResult<TOutput>> executeTask(
            @NotNull String taskId,
            @NotNull TInput input,
            @NotNull TaskExecutionContext context
    );

    /**
     * Lists all available tasks with metadata.
     *
     * @return Promise of task definitions
     */
    @NotNull
    Promise<List<TaskDefinition>> listTasks();

    /**
     * Lists tasks filtered by category/domain.
     *
     * @param domain Task domain (e.g., "architecture", "testing", "deployment")
     * @return Promise of filtered task definitions
     */
    @NotNull
    Promise<List<TaskDefinition>> listTasksByDomain(@NotNull String domain);

    /**
     * Gets detailed task definition including schema, examples, and documentation.
     *
     * @param taskId Task identifier
     * @return Promise of task definition or empty
     */
    @NotNull
    Promise<Optional<TaskDefinition>> getTaskDefinition(@NotNull String taskId);

    /**
     * Searches tasks by keywords, tags, or capabilities.
     *
     * @param query Search query
     * @return Promise of matching task definitions
     */
    @NotNull
    Promise<List<TaskDefinition>> searchTasks(@NotNull String query);

    /**
     * Registers a new task definition at runtime.
     * Enables plugins and extensions to add custom tasks.
     *
     * @param definition Task definition with schema, handler, and metadata
     * @return Promise of registration result
     */
    @NotNull
    Promise<TaskRegistrationResult> registerTask(@NotNull TaskDefinition definition);

    /**
     * Unregisters (removes) a task from the registry.
     *
     * @param taskId Task to remove
     * @return Promise of removal result
     */
    @NotNull
    Promise<TaskRemovalResult> unregisterTask(@NotNull String taskId);

    /**
     * Validates task input against schema before execution.
     *
     * @param taskId Task identifier
     * @param input  Input to validate
     * @return Promise of validation result
     */
    @NotNull
    Promise<ValidationResult> validateTaskInput(@NotNull String taskId, @NotNull Object input);

    /**
     * Estimates task execution cost (LLM tokens, compute resources).
     *
     * @param taskId Task identifier
     * @param input  Task input
     * @return Promise of estimated cost
     */
    @NotNull
    Promise<TaskCostEstimate> estimateTaskCost(@NotNull String taskId, @NotNull Object input);

    /**
     * Gets task execution history for user/project.
     *
     * @param filter History filter (user, project, date range, status)
     * @return Promise of past task executions
     */
    @NotNull
    Promise<List<TaskExecution>> getTaskHistory(@NotNull TaskHistoryFilter filter);

    /**
     * Gets specific task execution by ID.
     *
     * @param executionId Execution ID
     * @return Promise of task execution details
     */
    @NotNull
    Promise<Optional<TaskExecution>> getTaskExecution(@NotNull String executionId);

    /**
     * Cancels a running task execution.
     *
     * @param executionId Execution to cancel
     * @return Promise of cancellation result
     */
    @NotNull
    Promise<TaskCancellationResult> cancelTaskExecution(@NotNull String executionId);
}
