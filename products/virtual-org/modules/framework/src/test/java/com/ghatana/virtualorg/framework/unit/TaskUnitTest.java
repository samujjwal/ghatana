package com.ghatana.virtualorg.framework.unit;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.virtualorg.framework.task.Task;
import com.ghatana.virtualorg.framework.task.TaskPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task unit tests.
 *
 * Tests task creation, priority, and properties.
 *
 * @doc.type class
 * @doc.purpose Task component unit tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Task Unit Tests")
class TaskUnitTest extends EventloopTestBase {

    private Task task;

    @BeforeEach
    void setUp() {
        task = new Task("code_review", TaskPriority.HIGH);
    }

    /**
     * Verifies task creation with correct type and priority.
     */
    @Test
    @DisplayName("Should create task with correct type and priority")
    void shouldCreateTask() {
        assertThat(task.getType())
                .as("Task type should match")
                .isEqualTo("code_review");

        assertThat(task.getPriority())
                .as("Task priority should be HIGH")
                .isEqualTo(TaskPriority.HIGH);
    }

    /**
     * Verifies task with normal priority.
     */
    @Test
    @DisplayName("Should create task with MEDIUM priority")
    void shouldCreateMediumPriorityTask() {
        // WHEN: Create task with medium priority
        Task mediumTask = new Task("feature_development", TaskPriority.MEDIUM);

        // THEN: Priority should be preserved
        assertThat(mediumTask.getPriority())
                .as("Task should have MEDIUM priority")
                .isEqualTo(TaskPriority.MEDIUM);
    }

    /**
     * Verifies task with low priority.
     */
    @Test
    @DisplayName("Should create task with CRITICAL priority")
    void shouldCreateCriticalPriorityTask() {
        // WHEN: Create task with critical priority
        Task criticalTask = new Task("documentation", TaskPriority.CRITICAL);

        // THEN: Priority should be preserved
        assertThat(criticalTask.getPriority())
                .as("Task should have CRITICAL priority")
                .isEqualTo(TaskPriority.CRITICAL);
    }

    /**
     * Verifies multiple tasks can coexist with different priorities.
     */
    @Test
    @DisplayName("Should support multiple tasks with different priorities")
    void shouldSupportMultipleTasks() {
        // WHEN: Create tasks with different priorities
        Task urgent = new Task("production_fix", TaskPriority.HIGH);
        Task feature = new Task("new_feature", TaskPriority.MEDIUM);
        Task docs = new Task("update_docs", TaskPriority.LOW);

        // THEN: All tasks should exist independently
        assertThat(urgent.getType()).isEqualTo("production_fix");
        assertThat(feature.getType()).isEqualTo("new_feature");
        assertThat(docs.getType()).isEqualTo("update_docs");

        // AND: Priorities should be distinct
        assertThat(urgent.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(feature.getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(docs.getPriority()).isEqualTo(TaskPriority.LOW);
    }

    /**
     * Verifies task type preservation.
     */
    @Test
    @DisplayName("Should preserve task type")
    void shouldPreserveType() {
        assertThat(task.getType())
                .as("Task type should be preserved")
                .isEqualTo("code_review");
    }

    /**
     * Verifies task priority preservation.
     */
    @Test
    @DisplayName("Should preserve task priority")
    void shouldPreservePriority() {
        assertThat(task.getPriority())
                .as("Task priority should be preserved")
                .isEqualTo(TaskPriority.HIGH);
    }

    /**
     * Verifies task creation with various types.
     */
    @Test
    @DisplayName("Should support various task types")
    void shouldSupportVariousTypes() {
        // WHEN: Create tasks of different types
        Task t1 = new Task("bug_fix", TaskPriority.HIGH);
        Task t2 = new Task("feature_request", TaskPriority.MEDIUM);
        Task t3 = new Task("technical_debt", TaskPriority.LOW);
        Task t4 = new Task("refactoring", TaskPriority.MEDIUM);
        Task t5 = new Task("security_patch", TaskPriority.HIGH);

        // THEN: All types should be created
        assertThat(t1.getType()).isEqualTo("bug_fix");
        assertThat(t2.getType()).isEqualTo("feature_request");
        assertThat(t3.getType()).isEqualTo("technical_debt");
        assertThat(t4.getType()).isEqualTo("refactoring");
        assertThat(t5.getType()).isEqualTo("security_patch");
    }

    /**
     * Verifies all priority levels work correctly.
     */
    @Test
    @DisplayName("Should support all priority levels")
    void shouldSupportAllPriorities() {
        // WHEN: Create tasks with all priority levels
        Task high = new Task("high", TaskPriority.HIGH);
        Task normal = new Task("normal", TaskPriority.MEDIUM);
        Task low = new Task("low", TaskPriority.LOW);

        // THEN: All priorities should be supported
        assertThat(high.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(normal.getPriority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(low.getPriority()).isEqualTo(TaskPriority.LOW);
    }

    /**
     * Verifies task independence.
     */
    @Test
    @DisplayName("Should create independent task instances")
    void shouldCreateIndependentTasks() {
        // WHEN: Create multiple tasks
        Task t1 = new Task("task1", TaskPriority.HIGH);
        Task t2 = new Task("task1", TaskPriority.HIGH);
        Task t3 = new Task("task2", TaskPriority.LOW);

        // THEN: Tasks should be independent
        assertThat(t1)
                .as("Task t1 should not be null")
                .isNotNull();

        assertThat(t2)
                .as("Task t2 should not be null")
                .isNotNull();

        assertThat(t3)
                .as("Task t3 should not be null")
                .isNotNull();

        // AND: t1 and t2 can have same type but are different instances
        assertThat(t1.getType()).isEqualTo(t2.getType());
    }

    /**
     * Verifies task creation immutability pattern.
     */
    @Test
    @DisplayName("Should maintain task properties after creation")
    void shouldMaintainProperties() {
        // GIVEN: Created task
        Task created = new Task("analysis", TaskPriority.MEDIUM);

        // THEN: Properties should remain constant
        assertThat(created.getType()).isEqualTo("analysis");
        assertThat(created.getPriority()).isEqualTo(TaskPriority.MEDIUM);

        // WHEN: Access properties again
        String type1 = created.getType();
        String type2 = created.getType();
        TaskPriority p1 = created.getPriority();
        TaskPriority p2 = created.getPriority();

        // THEN: Properties should be consistent
        assertThat(type1).isEqualTo(type2);
        assertThat(p1).isEqualTo(p2);
    }

    /**
     * Verifies priority comparison.
     */
    @Test
    @DisplayName("Should support priority comparison")
    void shouldSupportPriorityComparison() {
        // WHEN: Create tasks with different priorities
        Task high = new Task("urgent", TaskPriority.HIGH);
        Task medium = new Task("regular", TaskPriority.MEDIUM);
        Task low = new Task("optional", TaskPriority.LOW);

        // THEN: Can distinguish priority levels
        assertThat(high.getPriority()).isNotEqualTo(medium.getPriority());
        assertThat(medium.getPriority()).isNotEqualTo(low.getPriority());
        assertThat(high.getPriority()).isNotEqualTo(low.getPriority());
    }

    /**
     * Verifies task lifecycle.
     */
    @Test
    @DisplayName("Should support task lifecycle")
    void shouldSupportLifecycle() {
        // GIVEN: New task
        Task newTask = new Task("new", TaskPriority.HIGH);

        // THEN: Task should be created
        assertThat(newTask)
                .as("Task should exist")
                .isNotNull();

        // AND: Task should have correct initial state
        assertThat(newTask.getType()).isEqualTo("new");
        assertThat(newTask.getPriority()).isEqualTo(TaskPriority.HIGH);
    }
}
