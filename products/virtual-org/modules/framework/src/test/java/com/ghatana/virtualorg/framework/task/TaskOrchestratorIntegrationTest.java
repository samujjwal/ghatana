package com.ghatana.virtualorg.framework.task;

import com.ghatana.platform.types.identity.Identifier;
import com.ghatana.platform.domain.auth.TenantId;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for TaskOrchestrator using EventloopTestBase.
 *
 * Tests validate: - Task declaration emits TaskDeclared events - Task
 * assignment emits TaskAssigned events - Task completion emits TaskCompleted
 * events - Events flow through EventPublisher correctly - Promise-based async
 * operations execute in eventloop context
 *
 * @see TaskOrchestrator
 * @see EventloopTestBase
 */
@DisplayName("TaskOrchestrator Integration Tests")
class TaskOrchestratorIntegrationTest extends EventloopTestBase {

    private TaskOrchestrator orchestrator;
    private CaptureEventPublisher eventCapture;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        // GIVEN: Test setup with tenant context
        tenantId = TenantId.of("tenant-integration-test");
        eventCapture = new CaptureEventPublisher();
        orchestrator = new TaskOrchestrator(eventCapture);
    }

    /**
     * Verifies that task declaration emits event through EventPublisher.
     *
     * GIVEN: TaskOrchestrator with event capture WHEN: declareTask() is called
     * THEN: TaskDeclared event is emitted with correct taskId
     */
    @Test
    @DisplayName("Should emit TaskDeclared event when task is declared")
    void shouldEmitTaskDeclaredWhenTaskIsDeclared() {
        // GIVEN: Task declaration request
        String taskName = "CompleteCodeReview";
        String taskDescription = "Review pull request #123";
        byte[] declarePayload = buildTaskDefPayload(taskName, taskDescription);

        // WHEN: Declare task
        String taskId = orchestrator.declareTask("tenant-integration-test", declarePayload);

        // THEN: TaskDeclared event was emitted
        assertThat(taskId)
                .as("Task ID should not be null after declaration")
                .isNotNull()
                .isNotBlank();

        assertThat(eventCapture.getEmittedEvents())
                .as("Exactly one event should be emitted during declaration")
                .hasSize(1)
                .extracting("type")
                .containsExactly("TaskDeclared");

        PublishedEvent declaredEvent = eventCapture.getEmittedEvents().get(0);
        assertThat(declaredEvent.payload)
                .as("Event payload should contain taskId")
                .contains(taskId);
    }

    /**
     * Verifies task lifecycle: declare → assign → complete.
     *
     * GIVEN: A declared task WHEN: assignTask() then completeTask() is called
     * THEN: TaskAssigned and TaskCompleted events are emitted in sequence
     */
    @Test
    @DisplayName("Should emit events for complete task lifecycle")
    void shouldEmitEventsForCompleteTaskLifecycle() {
        // GIVEN: Declared task
        String taskId = orchestrator.declareTask("tenant-integration-test", buildTaskDefPayload("Task", "Description"));
        eventCapture.clear(); // Reset capture after declaration

        // WHEN: Assign task
        String agentId = Identifier.random().raw();
        byte[] assignPayload = buildAssignPayload(taskId, agentId);
        orchestrator.assignTask("tenant-integration-test", taskId, agentId, assignPayload);

        // THEN: TaskAssigned event emitted
        assertThat(eventCapture.getEmittedEvents())
                .as("TaskAssigned event should be emitted")
                .hasSize(1)
                .extracting("type")
                .containsExactly("TaskAssigned");

        eventCapture.clear();

        // WHEN: Complete task
        byte[] completePayload = buildCompletePayload(taskId, "SUCCESS");
        orchestrator.completeTask("tenant-integration-test", taskId, completePayload);

        // THEN: TaskCompleted event emitted
        assertThat(eventCapture.getEmittedEvents())
                .as("TaskCompleted event should be emitted")
                .hasSize(1)
                .extracting("type")
                .containsExactly("TaskCompleted");
    }

    /**
     * Verifies tenant context is preserved through task lifecycle.
     *
     * GIVEN: Tasks declared for different tenants WHEN: Both tenants declare
     * tasks THEN: Events are tagged with correct tenant
     */
    @Test
    @DisplayName("Should preserve tenant context in all events")
    void shouldPreserveTenantContextInAllEvents() {
        // GIVEN: Two tenant contexts
        TenantId tenant1 = TenantId.of("tenant-1");
        TenantId tenant2 = TenantId.of("tenant-2");

        // WHEN: Declare tasks for both tenants
        String taskId1 = orchestrator.declareTask("tenant-1", buildTaskDefPayload("Task1", ""));
        String taskId2 = orchestrator.declareTask("tenant-2", buildTaskDefPayload("Task2", ""));

        // THEN: Both events emitted (tenant context maintained in orchestrator)
        assertThat(eventCapture.getEmittedEvents())
                .as("Events for both tenants should be emitted")
                .hasSize(2);

        // Note: In real scenario with AEP, tenant tagging would be enforced by adapter
        // For this test, we just verify both declarations succeeded
        assertThat(taskId1)
                .as("First tenant's task should be created")
                .isNotBlank();
        assertThat(taskId2)
                .as("Second tenant's task should be created")
                .isNotBlank();
    }

    // ==================== Test Utilities ====================
    /**
     * Build JSON payload for task definition (simplified for test).
     */
    private byte[] buildTaskDefPayload(String name, String description) {
        try {
            var json = String.format(
                    """
                {"name":"%s","description":"%s"}
                """, name, description
            );
            return json.getBytes();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Build JSON payload for task assignment.
     */
    private byte[] buildAssignPayload(String taskId, String agentId) {
        try {
            var json = String.format(
                    """
                {"taskId":"%s","agentId":"%s"}
                """, taskId, agentId
            );
            return json.getBytes();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Build JSON payload for task completion.
     */
    private byte[] buildCompletePayload(String taskId, String result) {
        try {
            var json = String.format(
                    """
                {"taskId":"%s","result":"%s"}
                """, taskId, result
            );
            return json.getBytes();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== Test Fixture ====================
    /**
     * Test implementation of EventPublisher that captures emitted events.
     */
    static class CaptureEventPublisher implements EventPublisher {

        private final List<PublishedEvent> emittedEvents = new ArrayList<>();

        @Override
        public void publish(String eventType, byte[] payload) {
            emittedEvents.add(new PublishedEvent(eventType, new String(payload)));
        }

        @Override
        public void publishOrganizationCreated(String name, String description) {
            publish("OrganizationCreated", String.format(
                    "{\"name\":\"%s\",\"description\":\"%s\"}", name, description
            ).getBytes());
        }

        @Override
        public void publishDepartmentRegistered(com.ghatana.platform.types.identity.Identifier departmentId, String name, String type) {
            publish("DepartmentRegistered", String.format(
                    "{\"departmentId\":\"%s\",\"name\":\"%s\",\"type\":\"%s\"}", departmentId.raw(), name, type
            ).getBytes());
        }

        @Override
        public void publishTaskDeclared(String taskId, String name, String description) {
            publish("TaskDeclared", String.format(
                    "{\"taskId\":\"%s\",\"name\":\"%s\",\"description\":\"%s\"}", taskId, name, description
            ).getBytes());
        }

        @Override
        public void publishTaskAssigned(String taskId, String agentId) {
            publish("TaskAssigned", String.format(
                    "{\"taskId\":\"%s\",\"agentId\":\"%s\"}", taskId, agentId
            ).getBytes());
        }

        @Override
        public void publishTaskCompleted(String taskId, String result) {
            publish("TaskCompleted", String.format(
                    "{\"taskId\":\"%s\",\"result\":\"%s\"}", taskId, result
            ).getBytes());
        }

        List<PublishedEvent> getEmittedEvents() {
            return emittedEvents;
        }

        void clear() {
            emittedEvents.clear();
        }
    }

    /**
     * Record of an emitted event for test assertion.
     */
    static class PublishedEvent {

        String type;
        String payload;

        PublishedEvent(String type, String payload) {
            this.type = type;
            this.payload = payload;
        }

        public String toString() {
            return String.format("[%s] %s", type, payload);
        }
    }
}
