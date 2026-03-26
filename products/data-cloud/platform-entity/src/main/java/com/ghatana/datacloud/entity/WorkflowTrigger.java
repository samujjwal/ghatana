package com.ghatana.datacloud.entity;

import java.util.*;

/**
 * Workflow trigger defining when a workflow should be executed.
 *
 * <p><b>Purpose</b><br>
 * Represents a trigger that initiates workflow execution. Triggers can be:
 * - Manual: User-initiated
 * - Event-based: Triggered by collection events (create, update, delete)
 * - Scheduled: Triggered on a schedule (cron)
 * - Webhook: Triggered by external webhooks
 *
 * <p><b>Trigger Types</b><br>
 * - MANUAL: User manually triggers the workflow
 * - ON_ENTITY_CREATED: Triggered when entity is created
 * - ON_ENTITY_UPDATED: Triggered when entity is updated
 * - ON_ENTITY_DELETED: Triggered when entity is deleted
 * - SCHEDULED: Triggered on a schedule
 * - WEBHOOK: Triggered by webhook
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkflowTrigger trigger = WorkflowTrigger.builder()
 *     .id("trigger-1")
 *     .type("ON_ENTITY_CREATED")
 *     .config(Map.of(
 *         "collectionName", "orders",
 *         "filter", Map.of("status", "pending")
 *     ))
 *     .build();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable - thread-safe.
 *
 * @see Workflow
 * @doc.type class
 * @doc.purpose Workflow trigger defining execution initiation
 * @doc.layer domain
 * @doc.pattern Value Object (Domain Layer)
 */
public class WorkflowTrigger {

    private final String id;
    private final String type;
    private final Map<String, Object> config;
    private final Boolean active;

    /**
     * Creates a new workflow trigger.
     *
     * @param id the trigger ID
     * @param type the trigger type
     * @param config the trigger configuration
     * @param active whether the trigger is active
     */
    public WorkflowTrigger(
            String id,
            String type,
            Map<String, Object> config,
            Boolean active) {
        this.id = Objects.requireNonNull(id, "ID must not be null");
        this.type = Objects.requireNonNull(type, "Type must not be null");
        this.config = Objects.requireNonNull(config, "Config must not be null");
        this.active = active != null ? active : true;
    }

    // Getters
    public String getId() { return id; }
    public String getType() { return type; }
    public Map<String, Object> getConfig() { return config; }
    public Boolean getActive() { return active; }

    /**
     * Creates a new builder for WorkflowTrigger.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for WorkflowTrigger.
     */
    public static class Builder {
        private String id;
        private String type;
        private Map<String, Object> config = new HashMap<>();
        private Boolean active = true;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder config(Map<String, Object> config) {
            this.config = config;
            return this;
        }

        public Builder active(Boolean active) {
            this.active = active;
            return this;
        }

        /**
         * Builds the WorkflowTrigger.
         *
         * @return the workflow trigger
         */
        public WorkflowTrigger build() {
            if (id == null) {
                id = UUID.randomUUID().toString();
            }
            return new WorkflowTrigger(id, type, config, active);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowTrigger trigger = (WorkflowTrigger) o;
        return Objects.equals(id, trigger.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "WorkflowTrigger{" +
                "id='" + id + '\'' +
                ", type='" + type + '\'' +
                ", active=" + active +
                '}';
    }
}
