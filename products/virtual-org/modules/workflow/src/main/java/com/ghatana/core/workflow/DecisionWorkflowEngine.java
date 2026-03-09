package com.ghatana.core.workflow;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages decision workflows and task routing.
 *
 * <p><b>Purpose</b><br>
 * Orchestrates decision workflows where tasks require human/agent decisions,
 * approval chains, or escalations. Routes tasks through approvers and manages
 * state transitions.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * DecisionWorkflowEngine workflow = new DecisionWorkflowEngine();
 *
 * // Create workflow with approval chain
 * WorkflowTask task = WorkflowTask.builder()
 *     .taskId("task-001")
 *     .title("Budget Approval")
 *     .assigneeId("alice")
 *     .type(WorkflowTask.Type.APPROVAL)
 *     .priority(WorkflowTask.Priority.HIGH)
 *     .approver("bob")    // First approver
 *     .approver("charlie") // Second approver
 *     .build();
 *
 * workflow.submitTask(task);
 * }</pre>
 *
 * <p><b>Workflow States</b><br>
 * CREATED → ASSIGNED → IN_PROGRESS → PENDING_APPROVAL →
 *   (APPROVED → COMPLETED) or (REJECTED → REASSIGNED)
 *
 * @doc.type class
 * @doc.purpose Decision workflow and approval routing
 * @doc.layer core
 * @doc.pattern State Machine, Observer
 */
public class DecisionWorkflowEngine {

    private static final Logger logger = LoggerFactory.getLogger(DecisionWorkflowEngine.class);

    private final Map<String, WorkflowTask> tasks;
    private final Map<String, WorkflowTransition> transitions;
    private final Map<String, List<WorkflowTask>> userTasks;  // userId -> tasks
    private final Map<String, List<String>> approvalChains;   // taskId -> approvers
    private final List<WorkflowListener> listeners;

    /**
     * Create decision workflow engine.
     */
    public DecisionWorkflowEngine() {
        this.tasks = new ConcurrentHashMap<>();
        this.transitions = new ConcurrentHashMap<>();
        this.userTasks = new ConcurrentHashMap<>();
        this.approvalChains = new ConcurrentHashMap<>();
        this.listeners = Collections.synchronizedList(new ArrayList<>());

        logger.debug("Created DecisionWorkflowEngine");
    }

    /**
     * Submit task to workflow.
     *
     * @param task Task to submit
     */
    public void submitTask(WorkflowTask task) {
        tasks.put(task.getTaskId(), task);
        userTasks.computeIfAbsent(task.getAssigneeId(), k -> new ArrayList<>()).add(task);
        approvalChains.put(task.getTaskId(), new ArrayList<>(task.getApproverIds()));

        logger.info("Submitted task: {} to {}", task.getTaskId(), task.getAssigneeId());
        notifyListeners(WorkflowEvent.TASK_SUBMITTED, task);
    }

    /**
     * Transition task to new state.
     *
     * @param taskId Task identifier
     * @param newState New state
     * @param actor Actor performing transition
     * @param notes Additional notes
     * @return Promise of success
     */
    public Promise<Void> transitionTask(String taskId, WorkflowTask.State newState,
                                        String actor, String notes) {
        WorkflowTask task = tasks.get(taskId);
        if (task == null) {
            return Promise.ofException(new IllegalArgumentException("Task not found: " + taskId));
        }

        // Validate transition
        if (!isValidTransition(task.getState(), newState)) {
            return Promise.ofException(
                new IllegalStateException("Invalid transition: " + task.getState() + " -> " + newState)
            );
        }

        // Handle approval logic
        if (newState == WorkflowTask.State.PENDING_APPROVAL) {
            List<String> approvers = approvalChains.get(taskId);
            if (approvers == null || approvers.isEmpty()) {
                // Auto-approve if no approvers
                return transitionTask(taskId, WorkflowTask.State.APPROVED, actor, notes);
            }
        }

        WorkflowTask updatedTask = updateTaskState(task, newState);
        tasks.put(taskId, updatedTask);
        refreshUserTask(updatedTask);

        // Record transition
        WorkflowTransition transition = new WorkflowTransition(
            taskId, task.getState(), newState, actor, notes, System.currentTimeMillis()
        );
        transitions.put(taskId + "-" + System.nanoTime(), transition);

        logger.info("Transitioned task {} from {} to {} by {}",
                   taskId, task.getState(), newState, actor);

        notifyListeners(WorkflowEvent.TASK_TRANSITIONED, updatedTask);
        return Promise.complete();
    }

    /**
     * Approve task (by approver).
     *
     * @param taskId Task identifier
     * @param approverId Approver identifier
     * @param comments Approval comments
     * @return Promise of success
     */
    public Promise<Void> approveTask(String taskId, String approverId, String comments) {
        WorkflowTask task = tasks.get(taskId);
        if (task == null) {
            return Promise.ofException(new IllegalArgumentException("Task not found"));
        }

        if (!task.getApproverIds().contains(approverId)) {
            return Promise.ofException(new IllegalArgumentException("Not an approver for this task"));
        }

        logger.info("Task {} approved by {}", taskId, approverId);
        notifyListeners(WorkflowEvent.TASK_APPROVED, task);

        return transitionTask(taskId, WorkflowTask.State.APPROVED, approverId, "Approved: " + comments);
    }

    /**
     * Reject task (send back for revision).
     *
     * @param taskId Task identifier
     * @param reviewerId Reviewer identifier
     * @param reason Rejection reason
     * @return Promise of success
     */
    public Promise<Void> rejectTask(String taskId, String reviewerId, String reason) {
        WorkflowTask task = tasks.get(taskId);
        if (task == null) {
            return Promise.ofException(new IllegalArgumentException("Task not found"));
        }

        logger.info("Task {} rejected by {}: {}", taskId, reviewerId, reason);
        notifyListeners(WorkflowEvent.TASK_REJECTED, task);

        return transitionTask(taskId, WorkflowTask.State.REJECTED, reviewerId, "Rejected: " + reason);
    }

    /**
     * Escalate task to higher level.
     *
     * @param taskId Task identifier
     * @param escalatedToId New assignee (higher level)
     * @param reason Escalation reason
     * @return Promise of success
     */
    public Promise<Void> escalateTask(String taskId, String escalatedToId, String reason) {
        WorkflowTask task = tasks.get(taskId);
        if (task == null) {
            return Promise.ofException(new IllegalArgumentException("Task not found"));
        }

        logger.info("Task {} escalated to {} (reason: {})", taskId, escalatedToId, reason);
        notifyListeners(WorkflowEvent.TASK_ESCALATED, task);

        return transitionTask(taskId, WorkflowTask.State.ESCALATED, escalatedToId, "Escalated: " + reason);
    }

    /**
     * Get tasks for user (inbox).
     *
     * @param userId User identifier
     * @return User's tasks
     */
    public List<WorkflowTask> getUserTasks(String userId) {
        return userTasks.getOrDefault(userId, new ArrayList<>());
    }

    /**
     * Get pending approval tasks for user.
     *
     * @param userId User identifier
     * @return Pending approval tasks
     */
    public List<WorkflowTask> getPendingApprovals(String userId) {
        List<WorkflowTask> pending = new ArrayList<>();

        for (WorkflowTask task : tasks.values()) {
            if (task.getApproverIds().contains(userId) &&
                task.getState() == WorkflowTask.State.PENDING_APPROVAL) {
                pending.add(task);
            }
        }

        return pending;
    }

    /**
     * Get urgent/overdue tasks.
     *
     * @return Urgent tasks
     */
    public List<WorkflowTask> getUrgentTasks() {
        List<WorkflowTask> urgent = new ArrayList<>();

        for (WorkflowTask task : tasks.values()) {
            if (task.isUrgent()) {
                urgent.add(task);
            }
        }

        return urgent;
    }

    /**
     * Register workflow listener.
     *
     * @param listener Listener to register
     */
    public void registerListener(WorkflowListener listener) {
        listeners.add(listener);
    }

    /**
     * Notify all listeners of workflow event.
     *
     * @param event Event type
     * @param task Related task
     */
    private void notifyListeners(WorkflowEvent event, WorkflowTask task) {
        for (WorkflowListener listener : listeners) {
            try {
                listener.onWorkflowEvent(event, task);
            } catch (Exception e) {
                logger.error("Error notifying listener", e);
            }
        }
    }

    /**
     * Validate state transition.
     *
     * @param currentState Current state
     * @param newState New state
     * @return true if transition is valid
     */
    private boolean isValidTransition(WorkflowTask.State currentState, WorkflowTask.State newState) {
        // Define valid transitions
        Map<WorkflowTask.State, Set<WorkflowTask.State>> validTransitions = new HashMap<>();
        validTransitions.put(WorkflowTask.State.CREATED, Set.of(
            WorkflowTask.State.ASSIGNED, WorkflowTask.State.CANCELLED));
        validTransitions.put(WorkflowTask.State.ASSIGNED, Set.of(
            WorkflowTask.State.IN_PROGRESS, WorkflowTask.State.CANCELLED));
        validTransitions.put(WorkflowTask.State.IN_PROGRESS, Set.of(
            WorkflowTask.State.PENDING_APPROVAL, WorkflowTask.State.COMPLETED, WorkflowTask.State.ESCALATED));
        validTransitions.put(WorkflowTask.State.PENDING_APPROVAL, Set.of(
            WorkflowTask.State.APPROVED, WorkflowTask.State.REJECTED, WorkflowTask.State.ESCALATED));
        validTransitions.put(WorkflowTask.State.APPROVED, Set.of(
            WorkflowTask.State.COMPLETED, WorkflowTask.State.ESCALATED));
        validTransitions.put(WorkflowTask.State.REJECTED, Set.of(
            WorkflowTask.State.IN_PROGRESS, WorkflowTask.State.ASSIGNED));
        validTransitions.put(WorkflowTask.State.ESCALATED, Set.of(
            WorkflowTask.State.IN_PROGRESS, WorkflowTask.State.APPROVED, WorkflowTask.State.REJECTED));

        Set<WorkflowTask.State> allowed = validTransitions.getOrDefault(currentState, Set.of());
        return allowed.contains(newState);
    }

    private WorkflowTask updateTaskState(WorkflowTask task, WorkflowTask.State newState) {
        if (task.getState() == newState) {
            return task;
        }

        WorkflowTask.Builder builder = WorkflowTask.builder()
            .taskId(task.getTaskId())
            .title(task.getTitle())
            .description(task.getDescription())
            .assigneeId(task.getAssigneeId())
            .requesterId(task.getRequesterId())
            .priority(task.getPriority())
            .type(task.getType())
            .deadline(task.getDeadline())
            .state(newState);

        task.getContext().forEach(builder::context);
        builder.approvers(task.getApproverIds());

        return builder.build();
    }

    private void refreshUserTask(WorkflowTask updatedTask) {
        List<WorkflowTask> userTaskList = userTasks.get(updatedTask.getAssigneeId());
        if (userTaskList == null) {
            return;
        }

        ListIterator<WorkflowTask> iterator = userTaskList.listIterator();
        while (iterator.hasNext()) {
            WorkflowTask existing = iterator.next();
            if (existing.getTaskId().equals(updatedTask.getTaskId())) {
                iterator.set(updatedTask);
                return;
            }
        }

        userTaskList.add(updatedTask);
    }

    /**
     * Workflow event types.
     */
    public enum WorkflowEvent {
        TASK_SUBMITTED,
        TASK_ASSIGNED,
        TASK_STARTED,
        TASK_TRANSITIONED,
        TASK_PENDING_APPROVAL,
        TASK_APPROVED,
        TASK_REJECTED,
        TASK_ESCALATED,
        TASK_COMPLETED,
        TASK_OVERDUE
    }

    /**
     * Workflow listener interface.
     */
    public interface WorkflowListener {
        void onWorkflowEvent(WorkflowEvent event, WorkflowTask task);
    }

    /**
     * Workflow transition record.
     */
    public static class WorkflowTransition {
        private final String taskId;
        private final WorkflowTask.State fromState;
        private final WorkflowTask.State toState;
        private final String actor;
        private final String notes;
        private final long timestamp;

        public WorkflowTransition(String taskId, WorkflowTask.State fromState, WorkflowTask.State toState,
                                String actor, String notes, long timestamp) {
            this.taskId = taskId;
            this.fromState = fromState;
            this.toState = toState;
            this.actor = actor;
            this.notes = notes;
            this.timestamp = timestamp;
        }

        public String getTaskId() {
            return taskId;
        }

        public WorkflowTask.State getFromState() {
            return fromState;
        }

        public WorkflowTask.State getToState() {
            return toState;
        }

        public String getActor() {
            return actor;
        }

        public String getNotes() {
            return notes;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}

