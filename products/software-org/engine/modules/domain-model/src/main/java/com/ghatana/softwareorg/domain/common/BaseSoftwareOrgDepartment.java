package com.ghatana.softwareorg.domain.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.virtualorg.framework.AbstractOrganization;
import com.ghatana.virtualorg.framework.Department;
import com.ghatana.virtualorg.framework.agent.Agent;
import com.ghatana.virtualorg.framework.event.EventPublisher;
import com.ghatana.virtualorg.framework.task.Task;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for all Software Organization departments.
 * Provides common event publishing and payload construction utilities.
 *
 * @doc.type class
 * @doc.purpose Base class for Software-Org departments
 * @doc.layer product
 * @doc.pattern Template Method
 */
public abstract class BaseSoftwareOrgDepartment extends Department {

    private final EventPublisher publisher;
    private static final ObjectMapper mapper = JsonUtils.getDefaultMapper();

    protected BaseSoftwareOrgDepartment(AbstractOrganization organization, EventPublisher publisher, String name, String type) {
        super(organization, name, type);
        this.publisher = publisher;
    }

    /**
     * Creates a new event payload builder.
     *
     * @return a new PayloadBuilder
     */
    protected PayloadBuilder newPayload() {
        return new PayloadBuilder();
    }

    /**
     * Publishes an event with the given type and payload.
     *
     * @param eventType the type of the event
     * @param payload   the event payload (Map or byte[])
     */
    protected void publishEvent(String eventType, Object payload) {
        if (payload instanceof byte[]) {
            publisher.publish(eventType, (byte[]) payload);
        } else {
            try {
                byte[] bytes = mapper.writeValueAsBytes(payload);
                publisher.publish(eventType, bytes);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize event payload", e);
            }
        }
    }

    // Implement abstract methods from Department with default behavior
    // Subclasses can override if needed

    @Override
    protected Promise<Agent> assignTask(Task task) {
        // Default implementation: return empty promise or throw not implemented if not critical
        // For now, to allow compilation, returning a promise that fails or finding any agent
        if (getAgents().isEmpty()) {
            return Promise.ofException(new RuntimeException("No agents available in department " + getName()));
        }
        // Simple round-robin or random assignment could be here, but for now just pick first
        return Promise.of(getAgents().get(0));
    }

    @Override
    public Map<String, Object> getKpis() {
        // Default empty KPIs
        return new HashMap<>();
    }

    /**
     * Builder for constructing event payloads.
     */
    public static class PayloadBuilder {
        private final Map<String, Object> payload = new HashMap<>();

        public PayloadBuilder withField(String key, Object value) {
            payload.put(key, value);
            return this;
        }

        public PayloadBuilder withTimestamp(String key) {
            payload.put(key, Instant.now().toString());
            return this;
        }

        public PayloadBuilder withTimestamp() {
            return withTimestamp("timestamp");
        }

        public Map<String, Object> build() {
            return payload;
        }
    }
}
