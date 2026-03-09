package com.ghatana.datacloud.entity;

import java.util.*;

/**
 * Workflow edge representing a connection between workflow nodes.
 *
 * <p><b>Purpose</b><br>
 * Represents a directed edge connecting two workflow nodes, defining the execution flow.
 * Edges can have conditions that determine whether the flow should proceed.
 *
 * <p><b>Edge Types</b><br>
 * - DEFAULT: Always execute
 * - CONDITIONAL: Execute if condition is true
 * - ERROR: Execute on error
 * - SUCCESS: Execute on success
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * WorkflowEdge edge = WorkflowEdge.builder()
 *     .id("edge-1")
 *     .sourceNodeId("node-1")
 *     .targetNodeId("node-2")
 *     .type("CONDITIONAL")
 *     .condition(Map.of(
 *         "operator", "equals",
 *         "field", "status",
 *         "value", "approved"
 *     ))
 *     .build();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable - thread-safe.
 *
 * @see Workflow
 * @see WorkflowNode
 * @doc.type class
 * @doc.purpose Workflow edge connecting workflow nodes
 * @doc.layer domain
 * @doc.pattern Value Object (Domain Layer)
 */
public class WorkflowEdge {

    private final String id;
    private final String sourceNodeId;
    private final String targetNodeId;
    private final String type;
    private final Map<String, Object> condition;
    private final String label;

    /**
     * Creates a new workflow edge.
     *
     * @param id the edge ID
     * @param sourceNodeId the source node ID
     * @param targetNodeId the target node ID
     * @param type the edge type
     * @param condition the edge condition (optional)
     * @param label the edge label
     */
    public WorkflowEdge(
            String id,
            String sourceNodeId,
            String targetNodeId,
            String type,
            Map<String, Object> condition,
            String label) {
        this.id = Objects.requireNonNull(id, "ID must not be null");
        this.sourceNodeId = Objects.requireNonNull(sourceNodeId, "Source node ID must not be null");
        this.targetNodeId = Objects.requireNonNull(targetNodeId, "Target node ID must not be null");
        this.type = Objects.requireNonNull(type, "Type must not be null");
        this.condition = condition != null ? condition : Map.of();
        this.label = label;
    }

    // Getters
    public String getId() { return id; }
    public String getSourceNodeId() { return sourceNodeId; }
    public String getTargetNodeId() { return targetNodeId; }
    public String getType() { return type; }
    public Map<String, Object> getCondition() { return condition; }
    public String getLabel() { return label; }

    /**
     * Creates a new builder for WorkflowEdge.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for WorkflowEdge.
     */
    public static class Builder {
        private String id;
        private String sourceNodeId;
        private String targetNodeId;
        private String type = "DEFAULT";
        private Map<String, Object> condition;
        private String label;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder sourceNodeId(String sourceNodeId) {
            this.sourceNodeId = sourceNodeId;
            return this;
        }

        public Builder targetNodeId(String targetNodeId) {
            this.targetNodeId = targetNodeId;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder condition(Map<String, Object> condition) {
            this.condition = condition;
            return this;
        }

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        /**
         * Builds the WorkflowEdge.
         *
         * @return the workflow edge
         */
        public WorkflowEdge build() {
            if (id == null) {
                id = UUID.randomUUID().toString();
            }
            return new WorkflowEdge(id, sourceNodeId, targetNodeId, type, condition, label);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkflowEdge edge = (WorkflowEdge) o;
        return Objects.equals(id, edge.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "WorkflowEdge{" +
                "id='" + id + '\'' +
                ", sourceNodeId='" + sourceNodeId + '\'' +
                ", targetNodeId='" + targetNodeId + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
