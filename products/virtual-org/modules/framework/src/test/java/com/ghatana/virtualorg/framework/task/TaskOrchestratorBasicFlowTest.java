package com.ghatana.virtualorg.framework.task;

import com.ghatana.virtualorg.framework.event.EventPublisher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class StubPublisher implements EventPublisher {

    public volatile String lastType;
    public volatile byte[] lastPayload;

    @Override
    public void publish(String eventType, byte[] payload) {
        this.lastType = eventType;
        this.lastPayload = payload;
    }

    @Override
    public void publishOrganizationCreated(String name, String description) {
        publish("OrganizationCreated", ("{}".getBytes()));
    }

    @Override
    public void publishDepartmentRegistered(com.ghatana.platform.types.identity.Identifier departmentId, String name, String type) {
        publish("DepartmentRegistered", ("{}".getBytes()));
    }

    @Override
    public void publishTaskDeclared(String taskId, String name, String description) {
        publish("TaskDeclared", ("{}".getBytes()));
    }

    @Override
    public void publishTaskAssigned(String taskId, String agentId) {
        publish("TaskAssigned", ("{}".getBytes()));
    }

    @Override
    public void publishTaskCompleted(String taskId, String result) {
        publish("TaskCompleted", ("{}".getBytes()));
    }
}

public class TaskOrchestratorBasicFlowTest {

    @Test
    void shouldDeclareAssignAndCompleteTask() {
        StubPublisher pub = new StubPublisher();
        TaskOrchestrator orchestrator = new TaskOrchestrator(pub);

        String taskId = orchestrator.declareTask("tenant-1", "{\"name\":\"test\"}".getBytes());
        assertNotNull(taskId);

        orchestrator.assignTask("tenant-1", taskId, "agent-1", "{}".getBytes());
        assertNotNull(pub.lastType);

        orchestrator.completeTask("tenant-1", taskId, "{\"result\":\"ok\"}".getBytes());
        assertNotNull(pub.lastPayload);
    }
}
