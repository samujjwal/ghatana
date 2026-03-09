package com.ghatana.virtualorg.framework.task;

import com.ghatana.platform.types.identity.Identifier;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable instance of a task assigned to an agent.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents a specific task instance with lifecycle tracking (declared →
 * assigned → started → completed/failed). Includes actual timing, agent
 * assignment, and outcome data.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * TaskInstance instance = TaskInstance.create(taskDef, assignedAgentId);
 * instance = instance.start();
 * instance = instance.complete(Map.of("linesOfCode", 250));
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Immutable - all state changes return new instances. Thread-safe.
 *
 * <p>
 * <b>Performance Characteristics</b><br>
 * O(1) state transitions. Minimal memory overhead (~500 bytes per instance).
 *
 * @see TaskDefinition
 * @doc.type record
 * @doc.purpose Immutable task instance with lifecycle
 * @doc.layer product
 * @doc.pattern Value Object
 */
public record TaskInstance(
        Identifier id,
        TaskDefinition definition,
        Identifier assignedAgentId,
        TaskStatus status,
        Instant declaredAt,
        Instant assignedAt,
        Instant startedAt,
        Instant completedAt,
        Duration actualDuration,
        Map<String, Object> outcome
        ) {

    /**
     * Canonical constructor with validation.
     */
    public TaskInstance          {
        Objects.requireNonNull(id, "Task instance ID must not be null");
        Objects.requireNonNull(definition, "Task definition must not be null");
        Objects.requireNonNull(status, "Task status must not be null");
        Objects.requireNonNull(declaredAt, "Declared timestamp must not be null");

        // Make defensive copies of mutable fields
        outcome = outcome != null ? Map.copyOf(outcome) : Map.of();
    }

    /**
     * Creates new task instance in DECLARED state.
     *
     * GIVEN: A task definition WHEN: create() is called THEN: New instance in
     * DECLARED status is returned
     *
     * @param definition task definition blueprint
     * @return new task instance
     */
    public static TaskInstance create(TaskDefinition definition) {
        return new TaskInstance(
                Identifier.random(),
                definition,
                null, // Not yet assigned
                TaskStatus.DECLARED,
                Instant.now(),
                null,
                null,
                null,
                null,
                Map.of()
        );
    }

    /**
     * Creates new task instance with agent assignment.
     *
     * @param definition task definition blueprint
     * @param assignedAgentId agent to assign task
     * @return new task instance in ASSIGNED status
     */
    public static TaskInstance create(TaskDefinition definition, Identifier assignedAgentId) {
        Objects.requireNonNull(assignedAgentId, "Assigned agent ID must not be null");

        Instant now = Instant.now();
        return new TaskInstance(
                Identifier.random(),
                definition,
                assignedAgentId,
                TaskStatus.ASSIGNED,
                now,
                now,
                null,
                null,
                null,
                Map.of()
        );
    }

    /**
     * Assigns task to an agent.
     *
     * GIVEN: Task in DECLARED status WHEN: assign() is called THEN: New
     * instance in ASSIGNED status is returned
     *
     * @param agentId agent identifier
     * @return new instance with assignment
     * @throws IllegalStateException if task not in DECLARED status
     */
    public TaskInstance assign(Identifier agentId) {
        if (status != TaskStatus.DECLARED) {
            throw new IllegalStateException("Can only assign tasks in DECLARED status");
        }

        return new TaskInstance(
                id,
                definition,
                agentId,
                TaskStatus.ASSIGNED,
                declaredAt,
                Instant.now(),
                null,
                null,
                null,
                outcome
        );
    }

    /**
     * Starts task execution.
     *
     * GIVEN: Task in ASSIGNED status WHEN: start() is called THEN: New instance
     * in STARTED status is returned
     *
     * @return new instance with started timestamp
     * @throws IllegalStateException if task not in ASSIGNED status
     */
    public TaskInstance start() {
        if (status != TaskStatus.ASSIGNED) {
            throw new IllegalStateException("Can only start tasks in ASSIGNED status");
        }

        return new TaskInstance(
                id,
                definition,
                assignedAgentId,
                TaskStatus.STARTED,
                declaredAt,
                assignedAt,
                Instant.now(),
                null,
                null,
                outcome
        );
    }

    /**
     * Completes task with outcome data.
     *
     * GIVEN: Task in STARTED status WHEN: complete() is called THEN: New
     * instance in COMPLETED status with duration is returned
     *
     * @param outcomeData task results/artifacts
     * @return new instance with completion data
     * @throws IllegalStateException if task not in STARTED status
     */
    public TaskInstance complete(Map<String, Object> outcomeData) {
        if (status != TaskStatus.STARTED) {
            throw new IllegalStateException("Can only complete tasks in STARTED status");
        }

        Instant now = Instant.now();
        Duration duration = Duration.between(startedAt, now);

        return new TaskInstance(
                id,
                definition,
                assignedAgentId,
                TaskStatus.COMPLETED,
                declaredAt,
                assignedAt,
                startedAt,
                now,
                duration,
                outcomeData
        );
    }

    /**
     * Fails task with error reason.
     *
     * @param reason failure reason
     * @return new instance in FAILED status
     * @throws IllegalStateException if task not in STARTED status
     */
    public TaskInstance fail(String reason) {
        if (status != TaskStatus.STARTED) {
            throw new IllegalStateException("Can only fail tasks in STARTED status");
        }

        Instant now = Instant.now();
        Duration duration = Duration.between(startedAt, now);

        return new TaskInstance(
                id,
                definition,
                assignedAgentId,
                TaskStatus.FAILED,
                declaredAt,
                assignedAt,
                startedAt,
                now,
                duration,
                Map.of("failureReason", reason)
        );
    }

    /**
     * Checks if task is in terminal state.
     *
     * @return true if COMPLETED or FAILED
     */
    public boolean isTerminal() {
        return status == TaskStatus.COMPLETED || status == TaskStatus.FAILED;
    }

    /**
     * Retrieves task outcome if completed.
     *
     * @return outcome data or empty if not completed
     */
    public Optional<Map<String, Object>> getOutcome() {
        return isTerminal() ? Optional.of(outcome) : Optional.empty();
    }
}
