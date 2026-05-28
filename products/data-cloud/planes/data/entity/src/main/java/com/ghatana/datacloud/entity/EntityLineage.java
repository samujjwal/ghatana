package com.ghatana.datacloud.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Lineage tracking for entity provenance and relationships.
 *
 * <p><b>Purpose</b><br>
 * Tracks the origin, transformation history, and relationships of entities.
 * Enables data governance, audit trails, and impact analysis.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * EntityLineage lineage = EntityLineage.builder()
 *     .entityId(entityId)
 *     .tenantId("tenant-123")
 *     .collectionName("orders")
 *     .sourceType("ingest")
 *     .sourceId("csv-import-456")
 *     .parentEntityId(parentOrderId)
 *     .relationshipType("derived_from")
 *     .build();
 * }</pre>
 *
 * <p><b>Lineage Types</b><br>
 * - <b>Provenance</b>: Where the entity came from (ingest, API, transform, etc.)
 * - <b>Parent-Child</b>: Entity relationships (derived_from, merged_from, split_from)
 * - <b>Transformation</b>: How the entity was modified (enriched, filtered, aggregated)
 *
 * @doc.type class
 * @doc.purpose Entity lineage tracking for provenance and relationships
 * @doc.layer product
 * @doc.pattern Domain Model (JPA Entity)
 */
@Entity
@Table(name = "entity_lineage", indexes = {
    @Index(name = "idx_lineage_entity", columnList = "entity_id"),
    @Index(name = "idx_lineage_tenant", columnList = "tenant_id, collection_name"),
    @Index(name = "idx_lineage_parent", columnList = "parent_entity_id"),
    @Index(name = "idx_lineage_source", columnList = "source_type, source_id"),
    @Index(name = "idx_lineage_created", columnList = "created_at DESC")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntityLineage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The entity this lineage record describes.
     */
    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    /**
     * Tenant identifier for multi-tenancy.
     */
    @Column(name = "tenant_id", nullable = false, length = 255)
    private String tenantId;

    /**
     * Collection name for context.
     */
    @Column(name = "collection_name", nullable = false, length = 255)
    private String collectionName;

    /**
     * Type of data source (ingest, api, transform, manual, etc.).
     */
    @Column(name = "source_type", nullable = false, length = 50)
    private String sourceType;

    /**
     * Identifier of the source (job ID, API endpoint, user ID, etc.).
     */
    @Column(name = "source_id", length = 255)
    private String sourceId;

    /**
     * Parent entity ID if this entity is derived from another.
     */
    @Column(name = "parent_entity_id")
    private UUID parentEntityId;

    /**
     * Parent collection name for context.
     */
    @Column(name = "parent_collection", length = 255)
    private String parentCollection;

    /**
     * Type of relationship to parent (derived_from, merged_from, split_from, etc.).
     */
    @Column(name = "relationship_type", length = 50)
    private String relationshipType;

    /**
     * Transformation description (e.g., "enriched with customer data").
     */
    @Column(name = "transformation", columnDefinition = "TEXT")
    private String transformation;

    /**
     * Additional metadata as JSONB.
     */
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;

    /**
     * When this lineage record was created.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * User or system that created this lineage record.
     */
    @Column(name = "created_by", length = 255)
    private String createdBy;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /**
     * Check if this lineage has a parent relationship.
     *
     * @return true if parentEntityId is set
     */
    public boolean hasParent() {
        return parentEntityId != null;
    }

    /**
     * Check if this lineage has a source.
     *
     * @return true if sourceType and sourceId are set
     */
    public boolean hasSource() {
        return sourceType != null && sourceId != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityLineage that = (EntityLineage) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "EntityLineage{" +
                "id=" + id +
                ", entityId=" + entityId +
                ", tenantId='" + tenantId + '\'' +
                ", collectionName='" + collectionName + '\'' +
                ", sourceType='" + sourceType + '\'' +
                ", sourceId='" + sourceId + '\'' +
                ", parentEntityId=" + parentEntityId +
                ", relationshipType='" + relationshipType + '\'' +
                '}';
    }
}
