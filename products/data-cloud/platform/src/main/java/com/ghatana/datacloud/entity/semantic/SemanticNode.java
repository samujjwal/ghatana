package com.ghatana.datacloud.entity.semantic;

import java.time.Instant;
import java.util.*;

/**
 * Immutable domain model representing a semantic node in the knowledge graph.
 *
 * <p><b>Purpose</b><br>
 * Represents metadata about a collection, field, or relationship in the system's
 * semantic model. Nodes are vertices in a directed graph that describes logical
 * data structures, field types, relationships, and cross-store mappings.
 *
 * <p><b>Architecture Role</b><br>
 * Domain model in semantic layer, used by:
 * - SemanticGraphService (application) - Manages nodes and relationships
 * - NLQ services - Resolves ambiguous field/collection references
 * - QueryExecutionService - Cross-store query planning
 * - StorageRoutingService - Determines optimal backends per field semantics
 *
 * <p><b>Node Types</b><br>
 * - COLLECTION: Represents a logical collection (parent node for fields)
 * - FIELD: Represents a collection field with type/constraints
 * - RELATIONSHIP: Represents a 1:M or M:M relationship between collections
 * - BACKEND_MAPPING: Maps semantic field to physical backend storage location
 * - AGGREGATION: Represents computed/aggregated field
 * - TIME_SERIES: Represents time-series specific field
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Collection node
 * SemanticNode collection = SemanticNode.builder()
 *     .id("col-orders")
 *     .type(NodeType.COLLECTION)
 *     .tenantId("tenant-123")
 *     .name("orders")
 *     .description("E-commerce orders")
 *     .metadata(Map.of(
 *         "backend", "postgres",
 *         "latency_class", "standard"
 *     ))
 *     .build();
 *
 * // Field node
 * SemanticNode field = SemanticNode.builder()
 *     .id("fld-order-status")
 *     .type(NodeType.FIELD)
 *     .tenantId("tenant-123")
 *     .parentId("col-orders")
 *     .name("status")
 *     .description("Order status (pending, shipped, delivered)")
 *     .metadata(Map.of(
 *         "data_type", "string",
 *         "enum_values", "pending,shipped,delivered",
 *         "is_indexed", "true"
 *     ))
 *     .build();
 * }</pre>
 *
 * <p><b>Storage Strategy</b><br>
 * Semantic nodes stored as entities in internal _semantic_nodes collection:
 * - Collection-scoped: One semantic graph per collection
 * - Tenant-isolated: Nodes include tenantId for multi-tenancy
 * - Versioned: createdAt/updatedAt for audit trails
 * - Queryable: Support by-type, by-parent, by-name queries
 *
 * <p><b>Thread Safety</b><br>
 * Immutable - thread-safe after construction.
 *
 * @see SemanticRelation
 * @see SemanticGraphService
 * @doc.type class
 * @doc.purpose Domain model for semantic knowledge graph nodes
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class SemanticNode {

    /**
     * Node type enumeration.
     */
    public enum NodeType {
        /** Collection node (e.g., "orders", "customers") */
        COLLECTION("collection"),
        /** Field node within a collection (e.g., "status", "amount") */
        FIELD("field"),
        /** Relationship node between collections */
        RELATIONSHIP("relationship"),
        /** Backend mapping node (physical storage location) */
        BACKEND_MAPPING("backend_mapping"),
        /** Computed/aggregated field */
        AGGREGATION("aggregation"),
        /** Time-series specific field */
        TIME_SERIES("time_series");

        private final String value;

        NodeType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static NodeType fromString(String value) {
            for (NodeType type : NodeType.values()) {
                if (type.value.equalsIgnoreCase(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown NodeType: " + value);
        }
    }

    private final String id;
    private final NodeType type;
    private final String tenantId;
    private final UUID collectionId;
    private final String parentId;
    private final String name;
    private final String description;
    private final Map<String, Object> metadata;
    private final int confidence;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * Private constructor - use Builder.
     */
    private SemanticNode(
            String id,
            NodeType type,
            String tenantId,
            UUID collectionId,
            String parentId,
            String name,
            String description,
            Map<String, Object> metadata,
            int confidence,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.type = type;
        this.tenantId = tenantId;
        this.collectionId = collectionId;
        this.parentId = parentId;
        this.name = name;
        this.description = description;
        this.metadata = new HashMap<>(metadata);
        this.confidence = confidence;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Creates a builder for SemanticNode.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for SemanticNode.
     */
    public static class Builder {
        private String id;
        private NodeType type;
        private String tenantId;
        private UUID collectionId;
        private String parentId;
        private String name;
        private String description;
        private Map<String, Object> metadata = new HashMap<>();
        private int confidence = 100;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(NodeType type) {
            this.type = type;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder collectionId(UUID collectionId) {
            this.collectionId = collectionId;
            return this;
        }

        public Builder parentId(String parentId) {
            this.parentId = parentId;
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

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = new HashMap<>(metadata);
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder confidence(int confidence) {
            if (confidence < 0 || confidence > 100) {
                throw new IllegalArgumentException("Confidence must be 0-100, got: " + confidence);
            }
            this.confidence = confidence;
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

        /**
         * Builds SemanticNode with validation.
         *
         * @return new SemanticNode instance
         * @throws IllegalArgumentException if required fields missing
         */
        public SemanticNode build() {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id is required");
            }
            if (type == null) {
                throw new IllegalArgumentException("type is required");
            }
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId is required");
            }
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("name is required");
            }

            Instant now = Instant.now();
            return new SemanticNode(
                    id,
                    type,
                    tenantId,
                    collectionId,
                    parentId,
                    name,
                    description,
                    metadata,
                    confidence,
                    createdAt != null ? createdAt : now,
                    updatedAt != null ? updatedAt : now);
        }
    }

    // ===== Getters =====

    public String getId() {
        return id;
    }

    public NodeType getType() {
        return type;
    }

    public String getTenantId() {
        return tenantId;
    }

    public UUID getCollectionId() {
        return collectionId;
    }

    public String getParentId() {
        return parentId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }

    public int getConfidence() {
        return confidence;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Get metadata value by key.
     *
     * @param key metadata key
     * @return metadata value or null if not present
     */
    public Object getMetadataValue(String key) {
        return metadata.get(key);
    }

    /**
     * Check if node is a child of another node (has parentId).
     *
     * @return true if parentId is not null
     */
    public boolean isChild() {
        return parentId != null;
    }

    /**
     * Check if node is a root node (no parent).
     *
     * @return true if parentId is null
     */
    public boolean isRoot() {
        return parentId == null;
    }

    /**
     * Create a copy of this node with updated fields.
     *
     * @return builder initialized with current fields
     */
    public Builder copy() {
        return new Builder()
                .id(id)
                .type(type)
                .tenantId(tenantId)
                .collectionId(collectionId)
                .parentId(parentId)
                .name(name)
                .description(description)
                .metadata(new HashMap<>(metadata))
                .confidence(confidence)
                .createdAt(createdAt)
                .updatedAt(Instant.now());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SemanticNode)) return false;
        SemanticNode that = (SemanticNode) o;
        return Objects.equals(id, that.id) && Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId);
    }

    @Override
    public String toString() {
        return "SemanticNode{" +
                "id='" + id + '\'' +
                ", type=" + type +
                ", tenantId='" + tenantId + '\'' +
                ", name='" + name + '\'' +
                ", parentId='" + parentId + '\'' +
                '}';
    }
}
