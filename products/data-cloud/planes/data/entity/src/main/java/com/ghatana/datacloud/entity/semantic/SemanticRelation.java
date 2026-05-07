package com.ghatana.datacloud.entity.semantic;

import java.time.Instant;
import java.util.*;

/**
 * Immutable domain model representing a semantic relationship between nodes.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents directed edges in the semantic knowledge graph, connecting nodes
 * and describing their relationships (field references, collection joins,
 * aggregations, backend mappings, etc.). Supports arbitrary properties for
 * domain-specific relationship metadata.
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Domain model in semantic layer, used by: - SemanticGraphService - Manages
 * graph traversal and reasoning - NLQ services - Infers joins and field
 * resolution - QueryExecutionService - Cross-collection query planning -
 * SemanticAnalyzer - Computes relationship strengths and confidence
 *
 * <p>
 * <b>Relationship Types</b><br>
 * - FIELD_OF: Collection contains field - REFERENCES: Field references another
 * collection - JOINED_BY: Join relationship between collections - MAPPED_TO:
 * Semantic field mapped to physical backend location - DERIVED_FROM:
 * Aggregation derived from one or more source fields - TIME_SERIES_OF:
 * Time-series data related to entity - SYNONYM: Alias or synonym for a field
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Collection contains field relationship
 * SemanticRelation relation1 = SemanticRelation.builder()
 *     .id("rel-col-orders-fld-status")
 *     .type(RelationType.FIELD_OF)
 *     .tenantId("tenant-123")
 *     .sourceNodeId("col-orders")
 *     .targetNodeId("fld-order-status")
 *     .label("has field")
 *     .confidence(100)
 *     .build();
 *
 * // Foreign key reference between collections
 * SemanticRelation relation2 = SemanticRelation.builder()
 *     .id("rel-fld-customer-ref-customers")
 *     .type(RelationType.REFERENCES)
 *     .tenantId("tenant-123")
 *     .sourceNodeId("fld-order-customerId")
 *     .targetNodeId("col-customers")
 *     .label("references")
 *     .addProperty("cardinality", "many-to-one")
 *     .addProperty("join_condition", "customerId = customers.id")
 *     .confidence(95)
 *     .build();
 * }</pre>
 *
 * <p>
 * <b>Storage Strategy</b><br>
 * Semantic relations stored as entities in internal _semantic_relations
 * collection: - Stored with source/target node IDs (not full objects) -
 * Tenant-isolated: Each relation includes tenantId - Queryable by source,
 * target, or type - Indexed for graph traversal performance
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Immutable - thread-safe after construction.
 *
 * @see SemanticNode
 * @see SemanticGraphService
 * @doc.type class
 * @doc.purpose Domain model for semantic knowledge graph relationships
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class SemanticRelation {

    /**
     * Relationship type enumeration.
     */
    public enum RelationType {
        /**
         * Collection contains a field
         */
        FIELD_OF("field_of"),
        /**
         * Field references another collection (foreign key)
         */
        REFERENCES("references"),
        /**
         * Collections are joined together
         */
        JOINED_BY("joined_by"),
        /**
         * Semantic field mapped to physical backend location
         */
        MAPPED_TO("mapped_to"),
        /**
         * Aggregation derived from source field(s)
         */
        DERIVED_FROM("derived_from"),
        /**
         * Time-series data related to entity
         */
        TIME_SERIES_OF("time_series_of"),
        /**
         * Synonym/alias for field
         */
        SYNONYM("synonym");

        private final String value;

        RelationType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public static RelationType fromString(String value) {
            for (RelationType type : RelationType.values()) {
                if (type.value.equalsIgnoreCase(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown RelationType: " + value);
        }
    }

    private final String id;
    private final RelationType type;
    private final String tenantId;
    private final String sourceNodeId;
    private final String targetNodeId;
    private final String label;
    private final Map<String, Object> properties;
    private final int confidence;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * Private constructor - use Builder.
     */
    private SemanticRelation(
            String id,
            RelationType type,
            String tenantId,
            String sourceNodeId,
            String targetNodeId,
            String label,
            Map<String, Object> properties,
            int confidence,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.type = type;
        this.tenantId = tenantId;
        this.sourceNodeId = sourceNodeId;
        this.targetNodeId = targetNodeId;
        this.label = label;
        this.properties = new HashMap<>(properties);
        this.confidence = confidence;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Creates a builder for SemanticRelation.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for SemanticRelation.
     */
    public static class Builder {

        private String id;
        private RelationType type;
        private String tenantId;
        private String sourceNodeId;
        private String targetNodeId;
        private String label;
        private Map<String, Object> properties = new HashMap<>();
        private int confidence = 100;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder type(RelationType type) {
            this.type = type;
            return this;
        }

        /**
         * Set relation type from string value (compatibility method).
         *
         * @param relationType relation type string
         * @return this builder
         */
        public Builder relationType(String relationType) {
            this.type = RelationType.fromString(relationType);
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
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

        public Builder label(String label) {
            this.label = label;
            return this;
        }

        public Builder properties(Map<String, Object> properties) {
            this.properties = new HashMap<>(properties);
            return this;
        }

        public Builder addProperty(String key, Object value) {
            this.properties.put(key, value);
            return this;
        }

        public Builder confidence(int confidence) {
            if (confidence < 0 || confidence > 100) {
                throw new IllegalArgumentException("Confidence must be 0-100, got: " + confidence);
            }
            this.confidence = confidence;
            return this;
        }

        /**
         * Set confidence from strength value (0.0-1.0). Compatibility method
         * for application layer.
         *
         * @param strength strength value (0.0-1.0)
         * @return this builder
         */
        public Builder strength(double strength) {
            if (strength < 0.0 || strength > 1.0) {
                throw new IllegalArgumentException("Strength must be 0.0-1.0, got: " + strength);
            }
            this.confidence = (int) (strength * 100);
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
         * Builds SemanticRelation with validation.
         *
         * @return new SemanticRelation instance
         * @throws IllegalArgumentException if required fields missing
         */
        public SemanticRelation build() {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("id is required");
            }
            if (type == null) {
                throw new IllegalArgumentException("type is required");
            }
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId is required");
            }
            if (sourceNodeId == null || sourceNodeId.isBlank()) {
                throw new IllegalArgumentException("sourceNodeId is required");
            }
            if (targetNodeId == null || targetNodeId.isBlank()) {
                throw new IllegalArgumentException("targetNodeId is required");
            }

            Instant now = Instant.now();
            return new SemanticRelation(
                    id,
                    type,
                    tenantId,
                    sourceNodeId,
                    targetNodeId,
                    label,
                    properties,
                    confidence,
                    createdAt != null ? createdAt : now,
                    updatedAt != null ? updatedAt : now);
        }
    }

    // ===== Getters =====
    public String getId() {
        return id;
    }

    public RelationType getType() {
        return type;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getSourceNodeId() {
        return sourceNodeId;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public String getLabel() {
        return label;
    }

    public Map<String, Object> getProperties() {
        return Collections.unmodifiableMap(properties);
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
     * Get property value by key.
     *
     * @param key property key
     * @return property value or null if not present
     */
    public Object getProperty(String key) {
        return properties.get(key);
    }

    /**
     * Get property as string.
     *
     * @param key property key
     * @return property value as string, or null
     */
    public String getPropertyAsString(String key) {
        Object value = properties.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * Check if relation has property.
     *
     * @param key property key
     * @return true if property exists
     */
    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }

    /**
     * Compatibility method for application layer. Returns the relation type as
     * string.
     *
     * @return relation type string value
     */
    public String getRelationType() {
        return type != null ? type.getValue() : null;
    }

    /**
     * Compatibility method for application layer. Returns confidence as
     * strength (0.0-1.0 scale).
     *
     * @return strength value (confidence / 100.0)
     */
    public double getStrength() {
        return confidence / 100.0;
    }

    /**
     * Compatibility method for application layer. Returns properties as
     * metadata.
     *
     * @return unmodifiable map of properties
     */
    public Map<String, Object> getMetadata() {
        return getProperties();
    }

    /**
     * Create a copy of this relation with updated fields.
     *
     * @return builder initialized with current fields
     */
    public Builder copy() {
        return new Builder()
                .id(id)
                .type(type)
                .tenantId(tenantId)
                .sourceNodeId(sourceNodeId)
                .targetNodeId(targetNodeId)
                .label(label)
                .properties(new HashMap<>(properties))
                .confidence(confidence)
                .createdAt(createdAt)
                .updatedAt(Instant.now());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SemanticRelation)) {
            return false;
        }
        SemanticRelation that = (SemanticRelation) o;
        return Objects.equals(id, that.id) && Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId);
    }

    @Override
    public String toString() {
        return "SemanticRelation{"
                + "id='" + id + '\''
                + ", type=" + type
                + ", tenantId='" + tenantId + '\''
                + ", sourceNodeId='" + sourceNodeId + '\''
                + ", targetNodeId='" + targetNodeId + '\''
                + '}';
    }
}
