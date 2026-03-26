package com.ghatana.datacloud.entity.version;

import com.ghatana.datacloud.entity.Entity;
import java.time.Instant;
import java.util.*;

/**
 * Represents a stored version of an entity in version history.
 *
 * <p><b>Purpose</b><br>
 * Stores complete snapshots of entities over time with metadata about the change.
 * Enables version comparison, rollback, and audit trail inspection.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * EntityVersion version = new EntityVersion(
 *     UUID.randomUUID(),
 *     entity.getTenantId(),
 *     entity.getId(),
 *     entity,
 *     1,
 *     new VersionMetadata("user-123", Instant.now(), "Initial creation"),
 *     Instant.now()
 * );
 * }</pre>
 *
 * <p><b>Versioning Strategy</b><br>
 * - Version numbers: auto-incrementing integers starting from 1
 * - Snapshots: complete entity data stored for each version
 * - Metadata: tracks who made the change and why
 * - Ordering: versions ordered by sequence for efficient traversal
 *
 * @doc.type class
 * @doc.purpose Entity version snapshot with metadata
 * @doc.layer domain
 * @doc.pattern Value Object (immutable)
 */
public class EntityVersion {

    private final UUID id;
    private final String tenantId;
    private final UUID entityId;
    private final Entity entitySnapshot;
    private final Integer versionNumber;
    private final VersionMetadata metadata;
    private final Instant createdAt;

    /**
     * Creates an EntityVersion.
     *
     * @param id the unique version record ID
     * @param tenantId the tenant ID
     * @param entityId the entity ID this is a version of
     * @param entitySnapshot the complete entity data at this version
     * @param versionNumber the version sequence number (1, 2, 3, ...)
     * @param metadata change metadata (author, timestamp, reason)
     * @param createdAt when this version was stored
     */
    public EntityVersion(
            UUID id,
            String tenantId,
            UUID entityId,
            Entity entitySnapshot,
            Integer versionNumber,
            VersionMetadata metadata,
            Instant createdAt) {
        this.id = Objects.requireNonNull(id, "ID must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "Tenant ID must not be null");
        this.entityId = Objects.requireNonNull(entityId, "Entity ID must not be null");
        this.entitySnapshot = Objects.requireNonNull(entitySnapshot, "Entity snapshot must not be null");
        this.versionNumber = Objects.requireNonNull(versionNumber, "Version number must not be null");
        this.metadata = Objects.requireNonNull(metadata, "Metadata must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "Created at must not be null");

        if (versionNumber < 1) {
            throw new IllegalArgumentException("Version number must be >= 1");
        }
    }

    /**
     * Gets the unique version record ID.
     *
     * @return version record ID
     */
    public UUID getId() {
        return id;
    }

    /**
     * Gets the tenant ID.
     *
     * @return tenant ID
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets the entity ID this version belongs to.
     *
     * @return entity ID
     */
    public UUID getEntityId() {
        return entityId;
    }

    /**
     * Gets the complete entity snapshot at this version.
     *
     * @return entity data
     */
    public Entity getEntitySnapshot() {
        return entitySnapshot;
    }

    /**
     * Gets the version number (1-based sequence).
     *
     * @return version number
     */
    public Integer getVersionNumber() {
        return versionNumber;
    }

    /**
     * Gets the version metadata (author, timestamp, reason).
     *
     * @return metadata
     */
    public VersionMetadata getMetadata() {
        return metadata;
    }

    /**
     * Gets when this version was stored.
     *
     * @return creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Gets author from metadata.
     *
     * @return author user ID
     */
    public String getAuthor() {
        return metadata.author();
    }

    /**
     * Gets change reason from metadata.
     *
     * @return change reason (may be null/empty)
     */
    public String getReason() {
        return metadata.reason();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntityVersion that = (EntityVersion) o;
        return Objects.equals(id, that.id) &&
               Objects.equals(tenantId, that.tenantId) &&
               Objects.equals(entityId, that.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId, entityId);
    }

    @Override
    public String toString() {
        return "EntityVersion{" +
                "id=" + id +
                ", entityId=" + entityId +
                ", versionNumber=" + versionNumber +
                ", author='" + getAuthor() + '\'' +
                '}';
    }

    /**
     * Creates a builder for EntityVersion.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for EntityVersion.
     */
    public static class Builder {
        private UUID id;
        private String tenantId;
        private UUID entityId;
        private Entity entitySnapshot;
        private Integer versionNumber;
        private VersionMetadata metadata;
        private Instant createdAt;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder entityId(UUID entityId) {
            this.entityId = entityId;
            return this;
        }

        public Builder entitySnapshot(Entity entitySnapshot) {
            this.entitySnapshot = entitySnapshot;
            return this;
        }

        public Builder versionNumber(Integer versionNumber) {
            this.versionNumber = versionNumber;
            return this;
        }

        public Builder metadata(VersionMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        /**
         * Builds the EntityVersion.
         *
         * @return entity version instance
         */
        public EntityVersion build() {
            if (id == null) {
                id = UUID.randomUUID();
            }
            if (createdAt == null) {
                createdAt = Instant.now();
            }
            return new EntityVersion(id, tenantId, entityId, entitySnapshot, versionNumber, metadata, createdAt);
        }
    }
}
