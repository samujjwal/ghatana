package com.ghatana.core.workflow;

import java.util.*;

/**
 * Represents a task assigned to an agent within a workflow.
 *
 * <p><b>Purpose</b><br>
 * Models a task with context, requirements, and tracking information.
 * Tasks can be assignments, decisions requiring human input, or escalations.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkflowTask task = WorkflowTask.builder()
 *     .taskId("task-001")
 *     .title("Code Review")
 *     .description("Review PR #123")
 *     .assigneeId("agent-alice")
 *     .priority(Priority.HIGH)
 *     .deadline(LocalDateTime.now().plusDays(1))
 *     .type(TaskType.HUMAN_DECISION)
 *     .build();
 * }</pre>
 *
 * <p><b>Task States</b><br>
 * - CREATED: Initial state
 * - ASSIGNED: Sent to assignee
 * - IN_PROGRESS: Work has started
 * - PENDING_APPROVAL: Awaiting review
 * - APPROVED: Approved by reviewer
 * - REJECTED: Sent back for revision
 * - COMPLETED: Successfully finished
 * - ESCALATED: Moved to higher level
 * - CANCELLED: Task cancelled
 *
 * @doc.type class
 * @doc.purpose Workflow task definition
 * @doc.layer core
 * @doc.pattern Value Object
 */
public class WorkflowTask {

    public enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum State {
        CREATED,
        ASSIGNED,
        IN_PROGRESS,
        PENDING_APPROVAL,
        APPROVED,
        REJECTED,
        COMPLETED,
        ESCALATED,
        CANCELLED
    }

    public enum Type {
        ASSIGNMENT,        // Standard work assignment
        HUMAN_DECISION,    // Requires human decision
        APPROVAL,          // Approval needed
        ESCALATION,        // Management escalation
        REVIEW             // Peer/manager review
    }

    private final String taskId;
    private final String title;
    private final String description;
    private final String assigneeId;
    private final String requesterId;
    private final Priority priority;
    private final Type type;
    private final long deadline;
    private final State state;
    private final Map<String, Object> context;
    private final List<String> approverIds;
    private final long createdAt;
    private final long updatedAt;

    /**
     * Create workflow task.
     *
     * @param builder Builder with configuration
     */
    private WorkflowTask(Builder builder) {
        this.taskId = builder.taskId;
        this.title = builder.title;
        this.description = builder.description;
        this.assigneeId = builder.assigneeId;
        this.requesterId = builder.requesterId;
        this.priority = builder.priority;
        this.type = builder.type;
        this.deadline = builder.deadline;
        this.state = builder.state;
        this.context = Collections.unmodifiableMap(builder.context);
        this.approverIds = Collections.unmodifiableList(builder.approverIds);
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public String getTaskId() {
        return taskId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getAssigneeId() {
        return assigneeId;
    }

    public String getRequesterId() {
        return requesterId;
    }

    public Priority getPriority() {
        return priority;
    }

    public Type getType() {
        return type;
    }

    public long getDeadline() {
        return deadline;
    }

    public State getState() {
        return state;
    }

    public Map<String, Object> getContext() {
        return context;
    }

    public List<String> getApproverIds() {
        return approverIds;
    }

    public boolean isOverdue() {
        return System.currentTimeMillis() > deadline;
    }

    public boolean isUrgent() {
        return priority == Priority.CRITICAL || (priority == Priority.HIGH && isOverdue());
    }

    public boolean requiresApproval() {
        return type == Type.APPROVAL || type == Type.HUMAN_DECISION || type == Type.REVIEW;
    }

    /**
     * Builder for WorkflowTask.
     */
    public static class Builder {
        private String taskId;
        private String title;
        private String description;
        private String assigneeId;
        private String requesterId;
        private Priority priority = Priority.MEDIUM;
        private Type type = Type.ASSIGNMENT;
        private long deadline = System.currentTimeMillis() + 86400000;  // 1 day
        private State state = State.CREATED;
        private final Map<String, Object> context = new HashMap<>();
        private final List<String> approverIds = new ArrayList<>();

        public Builder taskId(String taskId) {
            this.taskId = taskId;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder assigneeId(String assigneeId) {
            this.assigneeId = assigneeId;
            return this;
        }

        public Builder requesterId(String requesterId) {
            this.requesterId = requesterId;
            return this;
        }

        public Builder priority(Priority priority) {
            this.priority = priority;
            return this;
        }

        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        public Builder deadline(long deadline) {
            this.deadline = deadline;
            return this;
        }

        public Builder state(State state) {
            this.state = state;
            return this;
        }

        public Builder context(String key, Object value) {
            this.context.put(key, value);
            return this;
        }

        public Builder approver(String approverId) {
            this.approverIds.add(approverId);
            return this;
        }

        public Builder approvers(Collection<String> approverIds) {
            this.approverIds.addAll(approverIds);
            return this;
        }

        public WorkflowTask build() {
            if (taskId == null || title == null || assigneeId == null) {
                throw new IllegalArgumentException("taskId, title, and assigneeId are required");
            }
            return new WorkflowTask(this);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return String.format("WorkflowTask{id=%s, title=%s, assignee=%s, state=%s, priority=%s}",
                taskId, title, assigneeId, state, priority);
    }
}

