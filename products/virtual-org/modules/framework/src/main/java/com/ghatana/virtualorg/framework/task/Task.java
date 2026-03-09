package com.ghatana.virtualorg.framework.task;

import com.ghatana.virtualorg.framework.agent.Agent;

import java.util.Objects;
import java.util.UUID;

/**
 * Framework-local Task representation for virtual-org product modules.
 *
 * <p>Standalone task implementation that is independent of deprecated
 * platform domain types. Uses the framework's own Agent and TaskPriority types.
 */
public class Task {

    private final String id;
    private final String type;
    private final TaskPriority priority;
    private TaskStatus status;
    private Agent assignedAgent;

    public Task(String type, TaskPriority priority) {
        this.id = UUID.randomUUID().toString();
        this.type = Objects.requireNonNull(type, "type");
        this.priority = priority == null ? TaskPriority.MEDIUM : priority;
        this.status = TaskStatus.DECLARED;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public void assignTo(Agent agent) {
        this.assignedAgent = agent;
        this.status = TaskStatus.ASSIGNED;
    }

    public Agent getAssignedAgent() {
        return assignedAgent;
    }

    @Override
    public String toString() {
        return "Task{" + id + ':' + type + '}';
    }
}
