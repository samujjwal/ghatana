package com.ghatana.digitalmarketing.domain.workspace;

import com.ghatana.digitalmarketing.contracts.DmTenantId;
import com.ghatana.digitalmarketing.contracts.DmWorkspaceId;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing a DMOS workspace.
 *
 * <p>A {@code Workspace} is the primary operational boundary within a tenant.
 * Every campaign, brand profile, contact, content asset, and connector configuration
 * is scoped to exactly one workspace. A tenant may own multiple workspaces.</p>
 *
 * <p>Workspaces are immutable after construction; state transitions return new instances.</p>
 *
 * @doc.type class
 * @doc.purpose DMOS workspace domain entity providing tenant-scoped operational boundaries
 * @doc.layer product
 * @doc.pattern Entity, AggregateRoot
 */
public final class Workspace {

    private final DmWorkspaceId id;
    private final DmTenantId tenantId;
    private final String name;
    private final String description;
    private final WorkspaceStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;
    private final String createdBy;

    private Workspace(Builder builder) {
        this.id          = Objects.requireNonNull(builder.id,          "id must not be null");
        this.tenantId    = Objects.requireNonNull(builder.tenantId,    "tenantId must not be null");
        this.name        = Objects.requireNonNull(builder.name,        "name must not be null");
        this.description = builder.description != null ? builder.description : "";
        this.status      = Objects.requireNonNull(builder.status,      "status must not be null");
        this.createdAt   = Objects.requireNonNull(builder.createdAt,   "createdAt must not be null");
        this.updatedAt   = Objects.requireNonNull(builder.updatedAt,   "updatedAt must not be null");
        this.createdBy   = Objects.requireNonNull(builder.createdBy,   "createdBy must not be null");
        if (this.name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }

    /** Returns the workspace identifier. Never {@code null}. */
    public DmWorkspaceId getId() { return id; }

    /** Returns the owning tenant identifier. Never {@code null}. */
    public DmTenantId getTenantId() { return tenantId; }

    /** Returns the workspace display name. Never blank. */
    public String getName() { return name; }

    /** Returns the optional workspace description. Never {@code null}; may be empty. */
    public String getDescription() { return description; }

    /** Returns the current lifecycle status. Never {@code null}. */
    public WorkspaceStatus getStatus() { return status; }

    /** Returns the creation timestamp. Never {@code null}. */
    public Instant getCreatedAt() { return createdAt; }

    /** Returns the last-updated timestamp. Never {@code null}. */
    public Instant getUpdatedAt() { return updatedAt; }

    /** Returns the actor who created the workspace. Never {@code null}. */
    public String getCreatedBy() { return createdBy; }

    /**
     * Returns a copy with status set to {@link WorkspaceStatus#SUSPENDED}.
     * Only valid from {@link WorkspaceStatus#ACTIVE}.
     *
     * @throws IllegalStateException if not in ACTIVE status
     */
    public Workspace suspend() {
        if (status != WorkspaceStatus.ACTIVE) {
            throw new IllegalStateException(
                "Cannot suspend workspace in status " + status + "; must be ACTIVE");
        }
        return toBuilder().status(WorkspaceStatus.SUSPENDED).updatedAt(Instant.now()).build();
    }

    /**
     * Returns a copy with status set to {@link WorkspaceStatus#ACTIVE}.
     * Only valid from {@link WorkspaceStatus#SUSPENDED}.
     *
     * @throws IllegalStateException if not in SUSPENDED status
     */
    public Workspace reactivate() {
        if (status != WorkspaceStatus.SUSPENDED) {
            throw new IllegalStateException(
                "Cannot reactivate workspace in status " + status + "; must be SUSPENDED");
        }
        return toBuilder().status(WorkspaceStatus.ACTIVE).updatedAt(Instant.now()).build();
    }

    /**
     * Returns a copy with status set to {@link WorkspaceStatus#ARCHIVED}.
     * Only valid from {@link WorkspaceStatus#ACTIVE} or {@link WorkspaceStatus#SUSPENDED}.
     *
     * @throws IllegalStateException if already archived
     */
    public Workspace archive() {
        if (status == WorkspaceStatus.ARCHIVED) {
            throw new IllegalStateException("Workspace is already archived");
        }
        return toBuilder().status(WorkspaceStatus.ARCHIVED).updatedAt(Instant.now()).build();
    }

    /** Returns a builder pre-populated with this workspace's values. */
    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .tenantId(tenantId)
            .name(name)
            .description(description)
            .status(status)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .createdBy(createdBy);
    }

    /** Returns a fresh {@link Builder}. */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Workspace other)) return false;
        return id.equals(other.id) && tenantId.equals(other.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, tenantId);
    }

    @Override
    public String toString() {
        return "Workspace{id=" + id + ", tenantId=" + tenantId + ", name='" + name + "', status=" + status + '}';
    }

    /**
     * Fluent builder for {@link Workspace}.
     */
    public static final class Builder {
        private DmWorkspaceId id;
        private DmTenantId tenantId;
        private String name;
        private String description;
        private WorkspaceStatus status;
        private Instant createdAt;
        private Instant updatedAt;
        private String createdBy;

        private Builder() { }

        public Builder id(DmWorkspaceId id) { this.id = id; return this; }
        public Builder tenantId(DmTenantId tenantId) { this.tenantId = tenantId; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder status(WorkspaceStatus status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder createdBy(String createdBy) { this.createdBy = createdBy; return this; }

        /** Builds a {@link Workspace}. Throws if required fields are missing or invalid. */
        public Workspace build() { return new Workspace(this); }
    }
}
