package com.ghatana.datacloud.spi;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Tombstone metadata for soft-deleted data (DC-BE-004).
 *
 * <p>When data is soft-deleted, it remains in storage but is marked with a tombstone
 * that records when and why it was deleted, who deleted it, and the original deletion mode.
 * This enables audit trails, recovery operations, and lifecycle management.
 *
 * <h2>DC-BE-004: Deletion Lifecycle Standardization</h2>
 * This tombstone model is used across all data planes to track soft-deleted data:
 * - Entity plane (collections, entities)
 * - Event plane (event logs, event streams)
 * - Pipeline plane (pipelines, checkpoints)
 * - Governance plane (audit logs, compliance records)
 *
 * @doc.type class
 * @doc.purpose Tombstone metadata for soft-deleted data
 * @doc.layer spi
 * @doc.pattern Value Object
 */
public final class DeletionTombstone {

    private final String id;
    private final String tenantId;
    private final String resourceType; // entity, event, pipeline, etc.
    private final String resourceId;
    private final DeletionMode deletionMode;
    private final Instant deletedAt;
    private final String deletedBy; // user ID or system identifier
    private final String reason;
    private final Map<String, Object> metadata;

    private DeletionTombstone(Builder builder) {
        this.id = Objects.requireNonNull(builder.id, "id");
        this.tenantId = Objects.requireNonNull(builder.tenantId, "tenantId");
        this.resourceType = Objects.requireNonNull(builder.resourceType, "resourceType");
        this.resourceId = Objects.requireNonNull(builder.resourceId, "resourceId");
        this.deletionMode = Objects.requireNonNull(builder.deletionMode, "deletionMode");
        this.deletedAt = Objects.requireNonNull(builder.deletedAt, "deletedAt");
        this.deletedBy = builder.deletedBy;
        this.reason = builder.reason;
        this.metadata = builder.metadata != null ? Map.copyOf(builder.metadata) : Map.of();
    }

    /**
     * Creates a new tombstone builder.
     *
     * @param id unique tombstone identifier
     * @param tenantId tenant owning the deleted resource
     * @param resourceType type of resource (entity, event, pipeline, etc.)
     * @param resourceId identifier of the deleted resource
     * @return builder instance
     */
    public static Builder builder(String id, String tenantId, String resourceType, String resourceId) {
        return new Builder(id, tenantId, resourceType, resourceId);
    }

    /**
     * Creates a tombstone for a soft-deleted resource.
     *
     * @param id unique tombstone identifier
     * @param tenantId tenant owning the deleted resource
     * @param resourceType type of resource
     * @param resourceId identifier of the deleted resource
     * @param deletedBy user ID or system identifier
     * @param reason deletion reason
     * @return new tombstone
     */
    public static DeletionTombstone softDelete(String id,
                                                  String tenantId,
                                                  String resourceType,
                                                  String resourceId,
                                                  String deletedBy,
                                                  String reason) {
        return builder(id, tenantId, resourceType, resourceId)
                .deletionMode(DeletionMode.SOFT_DELETE)
                .deletedAt(Instant.now())
                .deletedBy(deletedBy)
                .reason(reason)
                .build();
    }

    public String id() {
        return id;
    }

    public String tenantId() {
        return tenantId;
    }

    public String resourceType() {
        return resourceType;
    }

    public String resourceId() {
        return resourceId;
    }

    public DeletionMode deletionMode() {
        return deletionMode;
    }

    public Instant deletedAt() {
        return deletedAt;
    }

    public Optional<String> deletedBy() {
        return Optional.ofNullable(deletedBy);
    }

    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    /**
     * Checks if the tombstone has expired based on the given retention period.
     *
     * @param retentionPeriod the retention period to check against
     * @return true if the tombstone has expired
     */
    public boolean isExpired(Duration retentionPeriod) {
        return deletedAt.plus(retentionPeriod).isBefore(Instant.now());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DeletionTombstone that = (DeletionTombstone) o;
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "DeletionTombstone{" +
                "id='" + id + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", resourceType='" + resourceType + '\'' +
                ", resourceId='" + resourceId + '\'' +
                ", deletionMode=" + deletionMode +
                ", deletedAt=" + deletedAt +
                '}';
    }

    /**
     * Builder for creating deletion tombstones.
     */
    public static class Builder {
        private final String id;
        private final String tenantId;
        private final String resourceType;
        private final String resourceId;
        private DeletionMode deletionMode = DeletionMode.SOFT_DELETE;
        private Instant deletedAt = Instant.now();
        private String deletedBy;
        private String reason;
        private Map<String, Object> metadata;

        private Builder(String id, String tenantId, String resourceType, String resourceId) {
            this.id = id;
            this.tenantId = tenantId;
            this.resourceType = resourceType;
            this.resourceId = resourceId;
        }

        public Builder deletionMode(DeletionMode deletionMode) {
            this.deletionMode = deletionMode;
            return this;
        }

        public Builder deletedAt(Instant deletedAt) {
            this.deletedAt = deletedAt;
            return this;
        }

        public Builder deletedBy(String deletedBy) {
            this.deletedBy = deletedBy;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder addMetadata(String key, Object value) {
            if (this.metadata == null) {
                this.metadata = new java.util.HashMap<>();
            }
            this.metadata.put(key, value);
            return this;
        }

        public DeletionTombstone build() {
            return new DeletionTombstone(this);
        }
    }
}
