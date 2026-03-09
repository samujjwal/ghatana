package com.ghatana.virtualorg.framework.event;

import com.ghatana.virtualorg.framework.task.TaskDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;

/**
 * Factory for converting TaskDefinition and task lifecycle events to byte
 * payloads suitable for publishing via EventPublisher.
 *
 * This is a lightweight mapper that serializes framework objects to JSON for
 * now; in Phase 2 this can be upgraded to proto-based serialization when proto
 * mappings are finalized.
 */
public class TaskEventFactory {

    private static final ObjectMapper mapper = JsonUtils.getDefaultMapper();

    /**
     * Create a TaskDeclared event payload from a TaskDefinition.
     */
    public static byte[] taskDeclared(String tenantId, TaskDefinition taskDef) {
        try {
            var payload = mapper.createObjectNode();
            payload.put("tenantId", tenantId);
            payload.put("taskId", taskDef.id().raw());
            payload.put("name", taskDef.name());
            payload.put("description", taskDef.description());
            return mapper.writeValueAsBytes(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize TaskDeclared event", e);
        }
    }

    /**
     * Create a TaskAssigned event payload.
     */
    public static byte[] taskAssigned(String tenantId, String taskId, String agentId) {
        try {
            var payload = mapper.createObjectNode();
            payload.put("tenantId", tenantId);
            payload.put("taskId", taskId);
            payload.put("agentId", agentId);
            return mapper.writeValueAsBytes(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize TaskAssigned event", e);
        }
    }

    /**
     * Create a TaskCompleted event payload.
     */
    public static byte[] taskCompleted(String tenantId, String taskId, String result) {
        try {
            var payload = mapper.createObjectNode();
            payload.put("tenantId", tenantId);
            payload.put("taskId", taskId);
            payload.put("result", result);
            return mapper.writeValueAsBytes(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize TaskCompleted event", e);
        }
    }
}
