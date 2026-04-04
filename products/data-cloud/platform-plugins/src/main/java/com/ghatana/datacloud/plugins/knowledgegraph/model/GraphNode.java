package com.ghatana.datacloud.plugins.knowledgegraph.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a node in the knowledge graph.
 * 
 * <p>Immutable value object representing a graph node with properties,
 * labels, and metadata. Nodes are the primary entities in the graph.
 * 
 * <p><b>Thread Safety:</b> Immutable and thread-safe
 * 
 * <p><b>Usage:</b>
 * <pre>{@code
 * GraphNode node = GraphNode.builder()
 *     .id(UUID.randomUUID().toString())
 *     .type("CLASS")
 *     .properties(Map.of("name", "UserService", "package", "com.example"))
 *     .labels(Set.of("java", "service"))
 *     .tenantId("tenant-123")
 *     .build();
 * }</pre>
 * 
 * @doc.type record
 * @doc.purpose Immutable graph node representation
 * @doc.layer domain
 * @doc.pattern Value Object
 */
@Value
@Builder(toBuilder = true)
public class GraphNode {
    
    /**
     * Unique identifier for the node.
     * Must be unique within the tenant scope.
     */
    @With
    String id;
    
    /**
     * Node type (e.g., "CLASS", "SERVICE", "COMPONENT").
     * Used for categorization and querying.
     */
    String type;
    
    /**
     * Arbitrary properties associated with the node.
     * Can contain any JSON-serializable values.
     */
    Map<String, Object> properties;
    
    /**
     * Labels for categorization and indexing.
     * Used for efficient querying and filtering.
     */
    Set<String> labels;
    
    /**
     * Tenant identifier for multi-tenancy support.
     * All operations must validate tenant access.
     */
    String tenantId;
    
    /**
     * Timestamp when the node was created.
     */
    Instant createdAt;
    
    /**
     * Timestamp when the node was last updated.
     */
    Instant updatedAt;
    
    /**
     * Version for optimistic locking.
     */
    Long version;
    
    @JsonCreator
    public GraphNode(
            @JsonProperty("id") String id,
            @JsonProperty("type") String type,
            @JsonProperty("properties") Map<String, Object> properties,
            @JsonProperty("labels") Set<String> labels,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("updatedAt") Instant updatedAt,
            @JsonProperty("version") Long version) {
        this.id = id;
        this.type = type;
        this.properties = properties != null ? Map.copyOf(properties) : Map.of();
        this.labels = labels != null ? Set.copyOf(labels) : Set.of();
        this.tenantId = tenantId;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
        this.version = version != null ? version : 1L;
    }
    
    /**
     * Creates a new node with the current timestamp as updatedAt.
     */
    public GraphNode withUpdated() {
        return toBuilder()
                .updatedAt(Instant.now())
                .version(version + 1)
                .build();
    }
    
    /**
     * Validates the node has all required fields.
     */
    public void validate() {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Node id cannot be null or blank");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("Node type cannot be null or blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Node tenantId cannot be null or blank");
        }
    }
    
    /**
     * Gets a property value by key.
     */
    public Object getProperty(String key) {
        return properties.get(key);
    }
    
    /**
     * Checks if the node has a specific label.
     */
    public boolean hasLabel(String label) {
        return labels.contains(label);
    }
}
