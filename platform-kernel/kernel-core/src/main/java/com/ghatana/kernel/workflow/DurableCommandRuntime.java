package com.ghatana.kernel.workflow;

import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Runtime for durable command execution and workflow orchestration.
 *
 * @doc.type interface
 * @doc.purpose Durable command and workflow execution (KERNEL-P0)
 * @doc.layer core
 * @doc.pattern Service
 */
public interface DurableCommandRuntime {

    /**
     * Submit a command for execution.
     *
     * @param command the command to execute
     * @return Promise containing the command ID
     */
    Promise<String> submitCommand(Command command);

    /**
     * Get command by ID.
     *
     * @param commandId command identifier
     * @return Promise containing command if found
     */
    Promise<Optional<Command>> getCommand(String commandId);

    /**
     * Get command status.
     *
     * @param commandId command identifier
     * @return Promise containing command status
     */
    Promise<CommandStatus> getCommandStatus(String commandId);

    /**
     * Retry a failed command.
     *
     * @param commandId command identifier
     * @return Promise that completes when retry is scheduled
     */
    Promise<Void> retryCommand(String commandId);

    /**
     * Cancel a pending command.
     *
     * @param commandId command identifier
     * @return Promise that completes when cancellation is finished
     */
    Promise<Void> cancelCommand(String commandId);

    /**
     * Start a workflow execution.
     *
     * @param workflowId workflow identifier
     * @param context execution context
     * @return Promise containing the workflow instance ID
     */
    Promise<String> startWorkflow(String workflowId, WorkflowContext context);

    /**
     * Get workflow instance by ID.
     *
     * @param instanceId workflow instance identifier
     * @return Promise containing workflow instance if found
     */
    Promise<Optional<WorkflowInstance>> getWorkflowInstance(String instanceId);

    /**
     * Get workflow instance status.
     *
     * @param instanceId workflow instance identifier
     * @return Promise containing workflow status
     */
    Promise<WorkflowStatus> getWorkflowStatus(String instanceId);

    /**
     * Resume a paused or failed workflow.
     *
     * @param instanceId workflow instance identifier
     * @return Promise that completes when resume is scheduled
     */
    Promise<Void> resumeWorkflow(String instanceId);

    /**
     * Cancel a running workflow.
     *
     * @param instanceId workflow instance identifier
     * @return Promise that completes when cancellation is finished
     */
    Promise<Void> cancelWorkflow(String instanceId);

    /**
     * Process pending commands (called by scheduler).
     *
     * @return Promise containing count of commands processed
     */
    Promise<Integer> processPendingCommands();

    /**
     * Process pending workflows (called by scheduler).
     *
     * @return Promise containing count of workflows processed
     */
    Promise<Integer> processPendingWorkflows();
}
