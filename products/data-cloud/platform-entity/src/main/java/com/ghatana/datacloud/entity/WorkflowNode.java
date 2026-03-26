package com.ghatana.datacloud.entity;

import java.util.*;

/**
 * Workflow node representing a single step in a workflow.
 *
 * <p><b>Purpose</b><br>
 * Represents a single executable step in a workflow. Nodes can be of various types:
 * API calls, decisions, approvals, transformations, etc.
 *
 * <p><b>Node Types</b><br>
 * - API_CALL: Call external API
 * - DECISION: Conditional branching
 * - APPROVAL: Human approval gate
 * - TRANSFORM: Data transformation
 * - QUERY: Query collection data
 * - NOTIFICATION: Send notification
 * - LOOP: Iterate over data
 * - WAIT: Delay execution
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkflowNode node = WorkflowNode.builder()
 *     .id("node-1")
 *     .type("API_CALL")
 *     .label("Send Email")
 *     .config(Map.of(
 *         "method", "POST",
 *         "url", "https://api.example.com/email",
 *         "headers", Map.of("Authorization", "Bearer token")
 *     ))
 *     .build();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable - thread-safe.
 *
 * @see Workflow
 * @see WorkflowEdge
 * @doc.type class
 * @doc.purpose Workflow node representing a workflow step
 * @doc.layer domain
 * @doc.pattern Value Object (Domain Layer)
 */
public class WorkflowNode {

    private final String id;
    private final String type;
    private final String label;
    private final Map<String, Object> config;
    private final Map<String, Object> metadata;
    private final Integer positionX;
    private final Integer positionY;

    /**
     * Creates a new workflow node.
     *
     * @param id the node ID
     * @param type the node type
     * @param label the node label
     * @param config the node configuration
     * @param metadata the node metadata
     * @param positionX the X position on canvas
     * @param positionY the Y position on canvas
     */
    public WorkflowNode(
            String id,
            String type,
            String label,
            Map<String, Object> config,
            Map<String, Object> metadata,
            Integer positionX,
            Integer positionY) {
        this.id = Objects.requireNonNull(id, "ID must not be null");
        this.type = Objects.requireNonNull(type, "Type must not be null");
        this.label = label;
        this.config = Objects.requireNonNull(config, "Config must not be null");
        this.metadata = Objects.requireNonNull(metadata, "Metadata must not be null");
        this.positionX = positionX;
        this.positionY = positionY;
    }

    // Getters
    public String getId() { return id; }
    public String getType() { return type; }
    public String getLabel() { return label; }
    public Map<String, Object> getConfig() { return config; }
    public Map<String, Object> getMetadata() { return metadata; }
    public Integer getPositionX() { return positionX; }
    public Integer getPositionY() { return positionY; }

    /**
     * Creates a new builder for WorkflowNode.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for WorkflowNode.
     */
    public static class Builder {
        private String id;
        private String type;
        private String label;
        private Map<String, Object> config = new HashMap<>();
        private Map<String, Object> metadata = new HashMap<>();
        private Integer positionX;
        private Integer positionY;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder config(Map<String, Object> config) {
            this.config = config;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder positionX(Integer positionX) {
            this.positionX = positionX;
            return this;
        }

        public Builder positionY(Integer positionY) {
            this.positionY = positionY;
            return this;
        }

        /**
         * Builds the WorkflowNode.
         *
         * @return the workflow node
         */
        public WorkflowNode build() {
            if (id == null) {
                id = UUID.randomUUID().toString();
            }
            return new WorkflowNode(id, type, label, config, metadata, positionX, positionY);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowNode node = (WorkflowNode) o;
        return Objects.equals(id, node.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "WorkflowNode{" +
                "id='" + id + '\'' +
                ", type='" + type + '\'' +
                ", label='" + label + '\'' +
                '}';
    }
}
