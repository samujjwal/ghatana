package com.ghatana.datacloud;

import com.ghatana.datacloud.entity.EntityInterface;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Mutable entity record - supports full CRUD operations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents business objects that change over time. Supports create, read,
 * update, and delete operations with optimistic locking and soft delete.
 *
 * <p>
 * <b>Features</b><br>
 * <ul>
 * <li><b>Versioning</b> - Optimistic locking via version field</li>
 * <li><b>Soft Delete</b> - Records marked inactive rather than deleted</li>
 * <li><b>Audit Trail</b> - Tracks createdAt/By and updatedAt/By</li>
 * <li><b>JSONB Data</b> - Dynamic schema stored as JSON</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * EntityRecord customer = EntityRecord.builder()
 *     .tenantId("tenant-123")
 *     .collectionName("customers")
 *     .data(Map.of(
 *         "name", "John Doe",
 *         "email", "john@example.com",
 *         "status", "active"
 *     ))
 *     .createdBy("user-456")
 *     .build();
 *
 * // Update
 * customer.getData().put("email", "john.doe@example.com");
 * customer.setUpdatedBy("user-789");
 * repository.save(customer); // version auto-increments
 *
 * // Soft delete
 * customer.setActive(false);
 * repository.save(customer);
 * }</pre>
 *
 * <p>
 * <b>Database Table</b><br>
 * <pre>
 * CREATE TABLE entities (
 *     id UUID PRIMARY KEY,
 *     tenant_id VARCHAR(255) NOT NULL,
 *     collection_name VARCHAR(255) NOT NULL,
 *     record_type VARCHAR(50) NOT NULL,
 *     data JSONB,
 *     metadata JSONB,
 *     version INTEGER DEFAULT 1,
 *     active BOOLEAN DEFAULT TRUE,
 *     created_at TIMESTAMP,
 *     created_by VARCHAR(255),
 *     updated_at TIMESTAMP,
 *     updated_by VARCHAR(255)
 * );
 * </pre>
 *
 * @see Record
 * @see RecordType#ENTITY
 * @doc.type class
 * @doc.purpose Mutable entity record with CRUD support
 * @doc.layer core
 * @doc.pattern Domain Model
 */
@Entity
@Table(name = "entities", indexes = {
    @Index(name = "idx_entities_tenant", columnList = "tenant_id"),
    @Index(name = "idx_entities_collection", columnList = "tenant_id, collection_name"),
    @Index(name = "idx_entities_active", columnList = "tenant_id, collection_name, active"),
    @Index(name = "idx_entities_created_at", columnList = "tenant_id, collection_name, created_at DESC")
})
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class EntityRecord extends DataRecord implements EntityInterface {

    /**
     * Version for optimistic locking.
     * <p>
     * Incremented on each update. Concurrent updates will fail if versions
     * mismatch.
     */
    @Version
    @Column(name = "version")
    @Builder.Default
    private Integer version = 1;

    /**
     * Soft delete flag.
     * <p>
     * When false, record is considered deleted but retained for audit.
     */
    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;

    /**
     * Timestamp of last update.
     */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * User or system that last updated this record.
     */
    @Column(name = "updated_by", length = 255)
    private String updatedBy;

    @Override
    public RecordType getRecordType() {
        return RecordType.ENTITY;
    }

    @Override
    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (recordType == null) {
            recordType = RecordType.ENTITY;
        }
        if (version == null) {
            version = 1;
        }
        if (active == null) {
            active = true;
        }
    }

    /**
     * Pre-update callback to set update timestamp.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Soft delete this entity.
     * <p>
     * Sets active to false and records who performed the delete.
     *
     * @param deletedBy user performing the delete
     */
    public void softDelete(String deletedBy) {
        this.active = false;
        this.updatedBy = deletedBy;
        this.updatedAt = Instant.now();
    }

    /**
     * Restore a soft-deleted entity.
     *
     * @param restoredBy user performing the restore
     */
    public void restore(String restoredBy) {
        this.active = true;
        this.updatedBy = restoredBy;
        this.updatedAt = Instant.now();
    }

    /**
     * Check if this entity is deleted (inactive).
     *
     * @return true if entity is soft-deleted
     */
    public boolean isDeleted() {
        return !Boolean.TRUE.equals(active);
    }

    @Override
    public String toString() {
        return "EntityRecord{"
                + "id=" + id
                + ", tenantId='" + tenantId + '\''
                + ", collectionName='" + collectionName + '\''
                + ", version=" + version
                + ", active=" + active
                + '}';
    }
}
