package com.ghatana.datacloud.entity;

import java.time.Instant;
import java.util.*;

/**
 * Workflow definition domain model.
 *
 * <p><b>Purpose</b><br>
 * Represents a workflow definition with nodes, edges, triggers, and variables.
 * Workflows are executable automation sequences that operate on collections.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Workflow workflow = Workflow.builder()
 *     .tenantId("tenant-123")
 *     .name("Order Processing")
 *     .description("Automated order processing workflow")
 *     .collectionId(UUID.randomUUID())
 *     .nodes(List.of(...))
 *     .edges(List.of(...))
 *     .triggers(List.of(...))
 *     .variables(Map.of(...))
 *     .build();
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * - Domain entity in domain layer
 * - Immutable value object pattern
 * - Supports workflow execution and monitoring
 * - Multi-tenant scoped
 *
 * <p><b>Workflow Structure</b><br>
 * - Nodes: Individual workflow steps (API calls, decisions, approvals, etc.)
 * - Edges: Connections between nodes defining execution flow
 * - Triggers: Events that initiate workflow execution
 * - Variables: Workflow-scoped data accessible to all nodes
 *
 * <p><b>Thread Safety</b><br>
 * Immutable - thread-safe.
 *
 * @see WorkflowNode
 * @see WorkflowEdge
 * @see WorkflowTrigger
 * @doc.type class
 * @doc.purpose Workflow definition domain model
 * @doc.layer domain
 * @doc.pattern Value Object (Domain Layer)
 */
public class Workflow {

    private final UUID id;
    private final String tenantId;
    private final String name;
    private final String description;
    private final UUID collectionId;
    private final List<WorkflowNode> nodes;
    private final List<WorkflowEdge> edges;
    private final List<WorkflowTrigger> triggers;
    private final Map<String, Object> variables;
    private final String status;
    private final Integer version;
    private final Boolean active;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String createdBy;
    private final String updatedBy;

    /**
     * Creates a new workflow.
     *
     * @param id the workflow ID
     * @param tenantId the tenant ID
     * @param name the workflow name
     * @param description the workflow description
     * @param collectionId the collection ID
     * @param nodes the workflow nodes
     * @param edges the workflow edges
     * @param triggers the workflow triggers
     * @param variables the workflow variables
     * @param status the workflow status
     * @param version the workflow version
     * @param active whether the workflow is active
     * @param createdAt the creation timestamp
     * @param updatedAt the last update timestamp
     * @param createdBy the user who created the workflow
     * @param updatedBy the user who last updated the workflow
     */
    public Workflow(
            UUID id,
            String tenantId,
            String name,
            String description,
            UUID collectionId,
            List<WorkflowNode> nodes,
            List<WorkflowEdge> edges,
            List<WorkflowTrigger> triggers,
            Map<String, Object> variables,
            String status,
            Integer version,
            Boolean active,
            Instant createdAt,
            Instant updatedAt,
            String createdBy,
            String updatedBy) {
        this.id = Objects.requireNonNull(id, "ID must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        this.name = Objects.requireNonNull(name, "Name must not be null");
        this.description = description;
        this.collectionId = Objects.requireNonNull(collectionId, "Collection ID must not be null");
        this.nodes = Objects.requireNonNull(nodes, "Nodes must not be null");
        this.edges = Objects.requireNonNull(edges, "Edges must not be null");
        this.triggers = Objects.requireNonNull(triggers, "Triggers must not be null");
        this.variables = Objects.requireNonNull(variables, "Variables must not be null");
        this.status = status;
        this.version = version;
        this.active = active;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
    }

    // Getters
    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public UUID getCollectionId() { return collectionId; }
    public List<WorkflowNode> getNodes() { return nodes; }
    public List<WorkflowEdge> getEdges() { return edges; }
    public List<WorkflowTrigger> getTriggers() { return triggers; }
    public Map<String, Object> getVariables() { return variables; }
    public String getStatus() { return status; }
    public Integer getVersion() { return version; }
    public Boolean getActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getCreatedBy() { return createdBy; }
    public String getUpdatedBy() { return updatedBy; }

    /**
     * Creates a new builder for Workflow.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for Workflow.
     */
    public static class Builder {
        private UUID id;
        private String tenantId;
        private String name;
        private String description;
        private UUID collectionId;
        private List<WorkflowNode> nodes = new ArrayList<>();
        private List<WorkflowEdge> edges = new ArrayList<>();
        private List<WorkflowTrigger> triggers = new ArrayList<>();
        private Map<String, Object> variables = new HashMap<>();
        private String status = "DRAFT";
        private Integer version = 1;
        private Boolean active = true;
        private Instant createdAt;
        private Instant updatedAt;
        private String createdBy;
        private String updatedBy;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder collectionId(UUID collectionId) {
            this.collectionId = collectionId;
            return this;
        }

        public Builder nodes(List<WorkflowNode> nodes) {
            this.nodes = nodes;
            return this;
        }

        public Builder edges(List<WorkflowEdge> edges) {
            this.edges = edges;
            return this;
        }

        public Builder triggers(List<WorkflowTrigger> triggers) {
            this.triggers = triggers;
            return this;
        }

        public Builder variables(Map<String, Object> variables) {
            this.variables = variables;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder version(Integer version) {
            this.version = version;
            return this;
        }

        public Builder active(Boolean active) {
            this.active = active;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Builder createdBy(String createdBy) {
            this.createdBy = createdBy;
            return this;
        }

        public Builder updatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
            return this;
        }

        /**
         * Builds the Workflow.
         *
         * @return the workflow
         */
        public Workflow build() {
            if (id == null) {
                id = UUID.randomUUID();
            }
            if (createdAt == null) {
                createdAt = Instant.now();
            }
            if (updatedAt == null) {
                updatedAt = Instant.now();
            }
            return new Workflow(
                id, tenantId, name, description, collectionId,
                nodes, edges, triggers, variables,
                status, version, active,
                createdAt, updatedAt, createdBy, updatedBy
            );
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Workflow workflow = (Workflow) o;
        return Objects.equals(id, workflow.id) &&
                Objects.equals(tenantId, workflow.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId);
    }

    @Override
    public String toString() {
        return "Workflow{" +
                "id=" + id +
                ", tenantId='" + tenantId + '\'' +
                ", name='" + name + '\'' +
                ", status='" + status + '\'' +
                ", version=" + version +
                ", active=" + active +
                '}';
    }
}
