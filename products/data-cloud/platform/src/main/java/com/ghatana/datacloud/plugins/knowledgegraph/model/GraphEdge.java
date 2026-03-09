package com.ghatana.datacloud.plugins.knowledgegraph.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.Instant;
import java.util.Map;

/**
 * Represents an edge (relationship) in the knowledge graph.
 * 
 * <p>Immutable value object representing a directed edge between two nodes.
 * Edges can have properties and represent various types of relationships.
 * 
 * <p><b>Thread Safety:</b> Immutable and thread-safe
 * 
 * <p><b>Usage:</b>
 * <pre>{@code
 * GraphEdge edge = GraphEdge.builder()
 *     .id(UUID.randomUUID().toString())
 *     .sourceNodeId("node-1")
 *     .targetNodeId("node-2")
 *     .relationshipType("DEPENDS_ON")
 *     .properties(Map.of("strength", 0.8))
 *     .tenantId("tenant-123")
 *     .build();
 * }</pre>
 * 
 * @doc.type record
 * @doc.purpose Immutable graph edge representation
 * @doc.layer domain
 * @doc.pattern Value Object
 */
@Value
@Builder(toBuilder = true)
public class GraphEdge {
    
    /**
     * Unique identifier for the edge.
     * Must be unique within the tenant scope.
     */
    @With
    String id;
    
    /**
     * ID of the source node.
     * Must reference an existing node in the same tenant.
     */
    String sourceNodeId;
    
    /**
     * ID of the target node.
     * Must reference an existing node in the same tenant.
     */
    String targetNodeId;
    
    /**
     * Type of relationship (e.g., "DEPENDS_ON", "IMPLEMENTS", "EXTENDS").
     * Used for categorization and querying.
     */
    String relationshipType;
    
    /**
     * Arbitrary properties associated with the edge.
     * Can contain any JSON-serializable values.
     */
    Map<String, Object> properties;
    
    /**
     * Tenant identifier for multi-tenancy support.
     * All operations must validate tenant access.
     */
    String tenantId;
    
    /**
     * Timestamp when the edge was created.
     */
    Instant createdAt;
    
    /**
     * Timestamp when the edge was last updated.
     */
    Instant updatedAt;
    
    /**
     * Version for optimistic locking.
     */
    Long version;
    
    @JsonCreator
    public GraphEdge(
            @JsonProperty("id") String id,
            @JsonProperty("sourceNodeId") String sourceNodeId,
            @JsonProperty("targetNodeId") String targetNodeId,
            @JsonProperty("relationshipType") String relationshipType,
            @JsonProperty("properties") Map<String, Object> properties,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("updatedAt") Instant updatedAt,
            @JsonProperty("version") Long version) {
        this.id = id;
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.relationshipType = relationshipType;
        this.properties = properties != null ? Map.copyOf(properties) : Map.of();
        this.tenantId = tenantId;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
        this.version = version != null ? version : 1L;
    }
    
    /**
     * Creates a new edge with the current timestamp as updatedAt.
     */
    public GraphEdge withUpdated() {
        return toBuilder()
                .updatedAt(Instant.now())
                .version(version + 1)
                .build();
    }
    
    /**
     * Validates the edge has all required fields.
     */
    public void validate() {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Edge id cannot be null or blank");
        }
        if (sourceNodeId == null || sourceNodeId.isBlank()) {
            throw new IllegalArgumentException("Edge sourceNodeId cannot be null or blank");
        }
        if (targetNodeId == null || targetNodeId.isBlank()) {
            throw new IllegalArgumentException("Edge targetNodeId cannot be null or blank");
        }
        if (relationshipType == null || relationshipType.isBlank()) {
            throw new IllegalArgumentException("Edge relationshipType cannot be null or blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Edge tenantId cannot be null or blank");
        }
        if (sourceNodeId.equals(targetNodeId)) {
            throw new IllegalArgumentException("Edge cannot connect a node to itself");
        }
    }
    
    /**
     * Gets a property value by key.
     */
    public Object getProperty(String key) {
        return properties.get(key);
    }
    
    /**
     * Checks if this edge is directed from source to target.
     */
    public boolean isDirectedFrom(String nodeId) {
        return sourceNodeId.equals(nodeId);
    }
    
    /**
     * Checks if this edge is directed to target.
     */
    public boolean isDirectedTo(String nodeId) {
        return targetNodeId.equals(nodeId);
    }
    
    /**
     * Checks if this edge connects the given node (either as source or target).
     */
    public boolean connects(String nodeId) {
        return sourceNodeId.equals(nodeId) || targetNodeId.equals(nodeId);
    }
}
