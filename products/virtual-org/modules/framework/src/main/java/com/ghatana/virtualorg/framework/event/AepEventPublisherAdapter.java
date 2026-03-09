package com.ghatana.virtualorg.framework.event;

import com.ghatana.platform.types.identity.Identifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import java.nio.charset.StandardCharsets;

/**
 * Adapter that delegates framework event publishing to the AEP
 * OrganizationEventPublisher. This keeps framework code decoupled from the
 * concrete AEP implementation.
 *
 * <p>
 * This adapter enables the virtual-org framework to remain independent of AEP
 * implementation details while delegating all actual event publishing to the
 * AEP ingress layer.
 *
 * <p>
 * Serialization approach: Events are wrapped in JSON with tenant context for
 * now. In Phase 2, this can be upgraded to proto-based serialization using
 * canonical contracts.
 *
 * @doc.type class
 * @doc.purpose Adapter from framework EventPublisher to AEP publisher
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class AepEventPublisherAdapter implements EventPublisher {

    private static final ObjectMapper mapper = JsonUtils.getDefaultMapper();
    private final OrganizationEventPublisher aepPublisher;

    public AepEventPublisherAdapter(OrganizationEventPublisher aepPublisher) {
        this.aepPublisher = aepPublisher;
    }

    @Override
    public void publish(String eventType, byte[] payload) {
        // The AEP publisher expects higher-level objects; for this adapter we forward the
        // raw payload as-is using a helper method on the AEP publisher. If richer mapping is
        // required, replace this with proper proto conversion.
        aepPublisher.appendRawEvent(eventType, new String(payload, StandardCharsets.UTF_8));
    }

    @Override
    public void publishOrganizationCreated(String name, String description) {
        try {
            var payload = mapper.createObjectNode();
            payload.put("name", name);
            payload.put("description", description);
            publish("OrganizationCreated", mapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish OrganizationCreated event", e);
        }
    }

    @Override
    public void publishDepartmentRegistered(Identifier departmentId, String name, String type) {
        try {
            var payload = mapper.createObjectNode();
            payload.put("departmentId", departmentId.raw());
            payload.put("name", name);
            payload.put("type", type);
            publish("DepartmentRegistered", mapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish DepartmentRegistered event", e);
        }
    }

    @Override
    public void publishTaskDeclared(String taskId, String name, String description) {
        try {
            var payload = mapper.createObjectNode();
            payload.put("taskId", taskId);
            payload.put("name", name);
            payload.put("description", description);
            publish("TaskDeclared", mapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish TaskDeclared event", e);
        }
    }

    @Override
    public void publishTaskAssigned(String taskId, String agentId) {
        try {
            var payload = mapper.createObjectNode();
            payload.put("taskId", taskId);
            payload.put("agentId", agentId);
            publish("TaskAssigned", mapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish TaskAssigned event", e);
        }
    }

    @Override
    public void publishTaskCompleted(String taskId, String result) {
        try {
            var payload = mapper.createObjectNode();
            payload.put("taskId", taskId);
            payload.put("result", result);
            publish("TaskCompleted", mapper.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new RuntimeException("Failed to publish TaskCompleted event", e);
        }
    }
}
