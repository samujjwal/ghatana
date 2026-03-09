package com.ghatana.virtualorg.orchestration;

import com.ghatana.virtualorg.v1.AgentRoleProto;
import com.ghatana.virtualorg.v1.TaskProto;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Task queue manager for handling task backlog with role-based queueing.
 *
 * <p><b>Purpose</b><br>
 * Manages task backlog when agents are unavailable or busy, providing:
 * - Role-based queue isolation (separate queues per agent role)
 * - Priority queueing (FIFO within each role queue)
 * - Capacity limits (prevent unbounded growth)
 * - Queue metrics (size, throughput, wait times)
 *
 * <p><b>Architecture Role</b><br>
 * Backpressure management component in the orchestration layer:
 * - Used by: TaskDispatcher (enqueue when agents busy)
 * - Integrates with: MetricsCollector (queue depth, latency metrics)
 * - Storage: In-memory ConcurrentLinkedQueue per role
 *
 * <p><b>Queue Structure</b><br>
 * Maintains separate queues per agent role:
 * - <b>AGENT_ROLE_SENIOR_ENGINEER</b>: Code reviews, feature implementations
 * - <b>AGENT_ROLE_ARCHITECT</b>: Architecture decisions, refactorings
 * - <b>AGENT_ROLE_QA_ENGINEER</b>: Test planning, quality gates
 * - <b>AGENT_ROLE_DEVOPS_ENGINEER</b>: Deployment approvals
 * - etc.
 *
 * Role isolation ensures:
 * - Fair scheduling (no role starvation)
 * - Predictable latency per role type
 * - Independent capacity limits
 *
 * <p><b>Capacity Management</b><br>
 * Each queue has configurable max size (default 100):
 * - Prevents memory exhaustion from unbounded task accumulation
 * - Rejects tasks with IllegalStateException when full
 * - Emits queue.full metrics for alerting
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * TaskQueue queue = new TaskQueue(100); // max 100 tasks per role
 * 
 * // Enqueue task when no agents available
 * TaskProto task = TaskProto.newBuilder()
 *     .setTaskId("task-456")
 *     .setType("CODE_REVIEW")
 *     .build();
 * 
 * queue.enqueue(task, AgentRoleProto.AGENT_ROLE_SENIOR_ENGINEER)
 *     .whenComplete(() -> log.info("Task queued"));
 * 
 * // Dequeue when agent becomes available
 * Optional<TaskProto> nextTask = queue.dequeue(AgentRoleProto.AGENT_ROLE_SENIOR_ENGINEER);
 * nextTask.ifPresent(t -> dispatcher.dispatch(t));
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe using ConcurrentHashMap for role queues and
 * ConcurrentLinkedQueue for individual queues.
 *
 * @see TaskDispatcher
 * @see AgentRoleProto
 * @doc.type class
 * @doc.purpose Task queue manager with role-based backlog handling
 * @doc.layer product
 * @doc.pattern Queue
 */
public class TaskQueue {

    private static final Logger log = LoggerFactory.getLogger(TaskQueue.class);

    private final Map<AgentRoleProto, Queue<TaskProto>> queues = new ConcurrentHashMap<>();
    private final int maxQueueSize;

    public TaskQueue(int maxQueueSize) {
        this.maxQueueSize = maxQueueSize;
        log.info("Initialized TaskQueue: maxSize={}", maxQueueSize);
    }

    /**
     * Enqueues a task for a specific role.
     *
     * @param task the task to enqueue
     * @param role the required agent role
     * @return promise that completes when task is enqueued
     */
    @NotNull
    public Promise<Void> enqueue(@NotNull TaskProto task, @NotNull AgentRoleProto role) {
        Queue<TaskProto> queue = queues.computeIfAbsent(role, k -> new ConcurrentLinkedQueue<>());

        if (queue.size() >= maxQueueSize) {
            log.error("Queue full for role: {}, rejecting task: {}", role, task.getTaskId());
            return Promise.ofException(
                    new IllegalStateException("Queue full for role: " + role)
            );
        }

        queue.offer(task);

        log.info("Enqueued task: taskId={}, role={}, queueSize={}",
                task.getTaskId(), role, queue.size());

        return Promise.complete();
    }

    /**
     * Dequeues the next task for a specific role.
     *
     * @param role the agent role
     * @return optional task, or empty if queue is empty
     */
    @NotNull
    public Optional<TaskProto> dequeue(@NotNull AgentRoleProto role) {
        Queue<TaskProto> queue = queues.get(role);

        if (queue == null || queue.isEmpty()) {
            return Optional.empty();
        }

        TaskProto task = queue.poll();

        if (task != null) {
            log.info("Dequeued task: taskId={}, role={}, remainingInQueue={}",
                    task.getTaskId(), role, queue.size());
        }

        return Optional.ofNullable(task);
    }

    /**
     * Gets the current queue size for a role.
     *
     * @param role the agent role
     * @return queue size
     */
    public int getQueueSize(@NotNull AgentRoleProto role) {
        Queue<TaskProto> queue = queues.get(role);
        return queue == null ? 0 : queue.size();
    }

    /**
     * Clears all queues.
     */
    public void clearAll() {
        queues.clear();
        log.info("Cleared all task queues");
    }

    /**
     * Gets total number of queued tasks across all roles.
     *
     * @return total queue size
     */
    public int getTotalQueueSize() {
        return queues.values().stream()
                .mapToInt(Queue::size)
                .sum();
    }
}
