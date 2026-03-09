package com.ghatana.virtualorg.framework.task;

import com.ghatana.virtualorg.framework.event.EventPublisher;

import java.util.UUID;

/**
 * Minimal TaskOrchestrator skeleton that manages simple task lifecycle events.
 * This is intentionally small: it demonstrates the pattern for
 * declaring/assigning/completing tasks and emits events via the injected
 * {@link EventPublisher}.
 */
public class TaskOrchestrator {

    private final EventPublisher publisher;

    public TaskOrchestrator(EventPublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Declare a new task and emit a TaskDeclared event.
     *
     * @param tenantId tenant identifier
     * @param taskDefJson task definition serialized as JSON
     * @return task id
     */
    public String declareTask(String tenantId, byte[] taskDefJson) {
        String taskId = UUID.randomUUID().toString();
        // For Phase 1 we embed the generated taskId into a simple JSON wrapper so
        // downstream components (and tests) can assert on the id without
        // depending on full proto mapping.
        String payload = String.format(
                "{\"taskId\":\"%s\",\"tenantId\":\"%s\",\"definition\":%s}",
                taskId,
                tenantId,
                new String(taskDefJson)
        );
        publisher.publish("TaskDeclared", payload.getBytes());
        return taskId;
    }

    /**
     * Assign a task to an agent and emit TaskAssigned event.
     */
    public void assignTask(String tenantId, String taskId, String agentId, byte[] assignPayload) {
        publisher.publish("TaskAssigned", assignPayload);
    }

    /**
     * Mark a task completed and emit TaskCompleted event.
     */
    public void completeTask(String tenantId, String taskId, byte[] resultPayload) {
        publisher.publish("TaskCompleted", resultPayload);
    }
}
