package com.ghatana.kernel.workflow;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * In-memory implementation of {@link DurableCommandRuntime} for testing.
 *
 * @doc.type class
 * @doc.purpose In-memory durable command runtime for testing (KERNEL-P0)
 * @doc.layer core
 * @doc.pattern Service
 */
public final class InMemoryDurableCommandRuntime implements DurableCommandRuntime {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryDurableCommandRuntime.class);

    private final Map<String, Command> commands = new ConcurrentHashMap<>();
    private final Map<String, WorkflowInstance> workflowInstances = new ConcurrentHashMap<>();
    private final Executor executor;

    public InMemoryDurableCommandRuntime(Executor executor) {
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
    }

    @Override
    public Promise<String> submitCommand(Command command) {
        Objects.requireNonNull(command, "command must not be null");

        return Promise.ofBlocking(executor, () -> {
            Command finalCommand = command;
            String commandId = finalCommand.getId();
            if (commandId == null || commandId.isBlank()) {
                commandId = UUID.randomUUID().toString();
                finalCommand = finalCommand.toBuilder().id(commandId).build();
            }

            commands.put(commandId, finalCommand);
            LOG.info("[COMMAND-RUNTIME] Submitted command commandId={} type={} tenant={}", 
                commandId, finalCommand.getCommandType(), finalCommand.getTenantId());
            return commandId;
        });
    }

    @Override
    public Promise<Optional<Command>> getCommand(String commandId) {
        Objects.requireNonNull(commandId, "commandId must not be null");

        return Promise.ofBlocking(executor, () -> Optional.ofNullable(commands.get(commandId)));
    }

    @Override
    public Promise<CommandStatus> getCommandStatus(String commandId) {
        Objects.requireNonNull(commandId, "commandId must not be null");

        return Promise.ofBlocking(executor, () -> {
            Command command = commands.get(commandId);
            if (command == null) {
                throw new IllegalArgumentException("Command not found: " + commandId);
            }
            return command.getStatus();
        });
    }

    @Override
    public Promise<Void> retryCommand(String commandId) {
        Objects.requireNonNull(commandId, "commandId must not be null");

        return Promise.ofBlocking(executor, () -> {
            Command command = commands.get(commandId);
            if (command == null) {
                throw new IllegalArgumentException("Command not found: " + commandId);
            }
            if (!command.canRetry()) {
                throw new IllegalArgumentException("Command cannot be retried: " + commandId);
            }

            Command retried = command.toBuilder()
                .status(CommandStatus.PENDING)
                .retryCount(command.getRetryCount() + 1)
                .error(null)
                .build();

            commands.put(commandId, retried);
            LOG.info("[COMMAND-RUNTIME] Retried command commandId={} retryCount={}", commandId, retried.getRetryCount());
            return null;
        });
    }

    @Override
    public Promise<Void> cancelCommand(String commandId) {
        Objects.requireNonNull(commandId, "commandId must not be null");

        return Promise.ofBlocking(executor, () -> {
            Command command = commands.get(commandId);
            if (command == null) {
                throw new IllegalArgumentException("Command not found: " + commandId);
            }

            Command cancelled = command.toBuilder()
                .status(CommandStatus.CANCELLED)
                .completedAt(Instant.now())
                .build();

            commands.put(commandId, cancelled);
            LOG.info("[COMMAND-RUNTIME] Cancelled command commandId={}", commandId);
            return null;
        });
    }

    @Override
    public Promise<String> startWorkflow(String workflowId, WorkflowContext context) {
        Objects.requireNonNull(workflowId, "workflowId must not be null");
        Objects.requireNonNull(context, "context must not be null");

        return Promise.ofBlocking(executor, () -> {
            String instanceId = UUID.randomUUID().toString();
            WorkflowInstance instance = WorkflowInstance.builder()
                .instanceId(instanceId)
                .workflowId(workflowId)
                .tenantId(context.getTenantId())
                .userId(context.getUserId())
                .status(WorkflowStatus.RUNNING)
                .input(context.getInput())
                .startedAt(Instant.now())
                .build();

            workflowInstances.put(instanceId, instance);
            LOG.info("[COMMAND-RUNTIME] Started workflow instanceId={} workflowId={} tenant={}", 
                instanceId, workflowId, context.getTenantId());
            return instanceId;
        });
    }

    @Override
    public Promise<Optional<WorkflowInstance>> getWorkflowInstance(String instanceId) {
        Objects.requireNonNull(instanceId, "instanceId must not be null");

        return Promise.ofBlocking(executor, () -> Optional.ofNullable(workflowInstances.get(instanceId)));
    }

    @Override
    public Promise<WorkflowStatus> getWorkflowStatus(String instanceId) {
        Objects.requireNonNull(instanceId, "instanceId must not be null");

        return Promise.ofBlocking(executor, () -> {
            WorkflowInstance instance = workflowInstances.get(instanceId);
            if (instance == null) {
                throw new IllegalArgumentException("Workflow instance not found: " + instanceId);
            }
            return instance.getStatus();
        });
    }

    @Override
    public Promise<Void> resumeWorkflow(String instanceId) {
        Objects.requireNonNull(instanceId, "instanceId must not be null");

        return Promise.ofBlocking(executor, () -> {
            WorkflowInstance instance = workflowInstances.get(instanceId);
            if (instance == null) {
                throw new IllegalArgumentException("Workflow instance not found: " + instanceId);
            }
            if (!instance.canResume()) {
                throw new IllegalArgumentException("Workflow cannot be resumed: " + instanceId);
            }

            WorkflowInstance resumed = instance.toBuilder()
                .status(WorkflowStatus.RUNNING)
                .error(null)
                .build();

            workflowInstances.put(instanceId, resumed);
            LOG.info("[COMMAND-RUNTIME] Resumed workflow instanceId={}", instanceId);
            return null;
        });
    }

    @Override
    public Promise<Void> cancelWorkflow(String instanceId) {
        Objects.requireNonNull(instanceId, "instanceId must not be null");

        return Promise.ofBlocking(executor, () -> {
            WorkflowInstance instance = workflowInstances.get(instanceId);
            if (instance == null) {
                throw new IllegalArgumentException("Workflow instance not found: " + instanceId);
            }

            WorkflowInstance cancelled = instance.toBuilder()
                .status(WorkflowStatus.CANCELLED)
                .completedAt(Instant.now())
                .build();

            workflowInstances.put(instanceId, cancelled);
            LOG.info("[COMMAND-RUNTIME] Cancelled workflow instanceId={}", instanceId);
            return null;
        });
    }

    @Override
    public Promise<Integer> processPendingCommands() {
        return Promise.ofBlocking(executor, () -> {
            int count = 0;
            for (Map.Entry<String, Command> entry : commands.entrySet()) {
                Command command = entry.getValue();
                if (command.getStatus() == CommandStatus.PENDING || 
                    (command.getStatus() == CommandStatus.SCHEDULED && 
                     command.getScheduledAt() != null && 
                     Instant.now().isAfter(command.getScheduledAt()))) {
                    
                    Command running = command.toBuilder()
                        .status(CommandStatus.RUNNING)
                        .startedAt(Instant.now())
                        .build();

                    commands.put(entry.getKey(), running);
                    count++;

                    // Simulate execution - in real implementation this would call actual command handlers
                    try {
                        Command completed = running.toBuilder()
                            .status(CommandStatus.COMPLETED)
                            .completedAt(Instant.now())
                            .result("SUCCESS")
                            .build();
                        commands.put(entry.getKey(), completed);
                    } catch (Exception e) {
                        Command failed = running.toBuilder()
                            .status(CommandStatus.FAILED)
                            .completedAt(Instant.now())
                            .error(e.getMessage())
                            .build();
                        commands.put(entry.getKey(), failed);
                    }
                }
            }
            if (count > 0) {
                LOG.info("[COMMAND-RUNTIME] Processed {} pending commands", count);
            }
            return count;
        });
    }

    @Override
    public Promise<Integer> processPendingWorkflows() {
        return Promise.ofBlocking(executor, () -> {
            int count = 0;
            for (Map.Entry<String, WorkflowInstance> entry : workflowInstances.entrySet()) {
                WorkflowInstance instance = entry.getValue();
                if (instance.getStatus() == WorkflowStatus.RUNNING) {
                    // Simulate workflow step execution
                    int nextStep = instance.getCurrentStep() + 1;
                    if (nextStep >= instance.getTotalSteps()) {
                        WorkflowInstance completed = instance.toBuilder()
                            .status(WorkflowStatus.COMPLETED)
                            .currentStep(nextStep)
                            .completedAt(Instant.now())
                            .build();
                        workflowInstances.put(entry.getKey(), completed);
                    } else {
                        WorkflowInstance progressed = instance.toBuilder()
                            .currentStep(nextStep)
                            .build();
                        workflowInstances.put(entry.getKey(), progressed);
                    }
                    count++;
                }
            }
            if (count > 0) {
                LOG.info("[COMMAND-RUNTIME] Processed {} pending workflows", count);
            }
            return count;
        });
    }
}
