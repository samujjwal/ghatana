package com.ghatana.datacloud;

import com.ghatana.datacloud.entity.DataCloudColumnNames;
import com.ghatana.datacloud.entity.EntityInterface;
import com.ghatana.datacloud.spi.DeletionMode;
import com.ghatana.datacloud.spi.DeletionTombstone;
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
    @Column(name = DataCloudColumnNames.VERSION)
    @Builder.Default
    private Integer version = 1;

    /**
     * Soft delete flag.
     * <p>
     * When false, record is considered deleted but retained for audit.
     */
    @Column(name = DataCloudColumnNames.ACTIVE)
    @Builder.Default
    private Boolean active = true;

    /**
     * DC-BE-004: Deletion mode for this entity.
     * <p>
     * Tracks how this entity was deleted (SOFT_DELETE, HARD_DELETE, ARCHIVE, RETENTION_PURGE).
     * Used for lifecycle management and policy enforcement.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "deletion_mode", length = 20)
    private DeletionMode deletionMode;

    /**
     * DC-BE-004: Timestamp when the entity was deleted.
     * <p>
     * Set when the entity is soft-deleted or archived. Used for retention calculations.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /**
     * DC-BE-004: User or system that deleted this entity.
     * <p>
     * Set when the entity is soft-deleted or archived. Used for audit trail.
     */
    @Column(name = "deleted_by", length = 255)
    private String deletedBy;

    /**
     * DC-BE-004: Reason for deletion.
     * <p>
     * Optional field to record why the entity was deleted. Used for audit trail.
     */
    @Column(name = "deletion_reason", length = 500)
    private String deletionReason;

    /**
     * Timestamp of last update.
     */
    @Column(name = DataCloudColumnNames.UPDATED_AT)
    private Instant updatedAt;

    /**
     * User or system that last updated this record.
     */
    @Column(name = DataCloudColumnNames.UPDATED_BY, length = 255)
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
     * Sets active to false, records deletion mode, and records who performed the delete.
     *
     * @param deletedBy user performing the delete
     * @param reason optional reason for deletion
     */
    public void softDelete(String deletedBy, String reason) {
        this.active = false;
        this.deletionMode = DeletionMode.SOFT_DELETE;
        this.deletedAt = Instant.now();
        this.deletedBy = deletedBy;
        this.deletionReason = reason;
        this.updatedBy = deletedBy;
        this.updatedAt = Instant.now();
    }

    /**
     * Soft delete this entity (legacy method for backward compatibility).
     *
     * @param deletedBy user performing the delete
     */
    public void softDelete(String deletedBy) {
        softDelete(deletedBy, null);
    }

    /**
     * DC-BE-004: Archive this entity.
     * <p>
     * Marks the entity as archived for long-term retention in cold storage.
     *
     * @param archivedBy user or system performing the archive
     * @param reason optional reason for archival
     */
    public void archive(String archivedBy, String reason) {
        this.active = false;
        this.deletionMode = DeletionMode.ARCHIVE;
        this.deletedAt = Instant.now();
        this.deletedBy = archivedBy;
        this.deletionReason = reason;
        this.updatedBy = archivedBy;
        this.updatedAt = Instant.now();
    }

    /**
     * DC-BE-004: Mark this entity for retention purge.
     * <p>
     * Marks the entity as scheduled for automated deletion based on retention policies.
     *
     * @param purgedBy system identifier performing the purge
     * @param reason reason for purge (e.g., "retention policy expired")
     */
    public void markForRetentionPurge(String purgedBy, String reason) {
        this.deletionMode = DeletionMode.RETENTION_PURGE;
        this.deletedAt = Instant.now();
        this.deletedBy = purgedBy;
        this.deletionReason = reason;
        this.updatedBy = purgedBy;
        this.updatedAt = Instant.now();
    }

    /**
     * Restore a soft-deleted entity.
     *
     * @param restoredBy user performing the restore
     */
    public void restore(String restoredBy) {
        this.active = true;
        this.deletionMode = null;
        this.deletedAt = null;
        this.deletedBy = null;
        this.deletionReason = null;
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

    /**
     * DC-BE-004: Check if this entity is archived.
     *
     * @return true if entity is archived
     */
    public boolean isArchived() {
        return deletionMode == DeletionMode.ARCHIVE;
    }

    /**
     * DC-BE-004: Check if this entity is marked for retention purge.
     *
     * @return true if entity is marked for retention purge
     */
    public boolean isMarkedForPurge() {
        return deletionMode == DeletionMode.RETENTION_PURGE;
    }

    /**
     * DC-BE-004: Create a tombstone for this entity.
     *
     * @return deletion tombstone
     */
    public DeletionTombstone toTombstone() {
        if (!isDeleted()) {
            throw new IllegalStateException("Cannot create tombstone for active entity");
        }
        return DeletionTombstone.builder(
                id.toString(),
                tenantId,
                "entity",
                id.toString()
            )
            .deletionMode(deletionMode)
            .deletedAt(deletedAt)
            .deletedBy(deletedBy)
            .reason(deletionReason)
            .build();
    }

    @Override
    public String toString() {
        return "EntityRecord{"
                + "id=" + id
                + ", tenantId='" + tenantId + '\''
                + ", collectionName='" + collectionName + '\''
                + ", version=" + version
                + ", active=" + active
                + ", deletionMode=" + deletionMode
                + '}';
    }
}
