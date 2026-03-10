package com.ghatana.yappc.ai.requirements.domain.workspace;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Workspace domain model.
 *
 * <p><b>Purpose</b><br>
 * A workspace is a container for projects and team collaboration within the AI Requirements Tool.
 * Maps to OrgUnit from virtual-org framework to leverage organizational hierarchy.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Workspace ws = Workspace.builder()
 *     .workspaceId(UUID.randomUUID().toString())
 *     .name("Q1 Planning")
 *     .description("2025 Q1 Requirements")
 *     .ownerId(userId)
 *     .orgUnitId(orgUnitId)
 *     .status(Workspace.WorkspaceStatus.ACTIVE)
 *     .build();
 * }</pre>
 *
 * <p><b>Integration</b><br>
 * - Links to OrgUnit (org hierarchy) from core/virtualorg
 * - Uses WorkflowEngine for approval workflows from core/workflow
 * - Role system maps to Permission system from core/auth
 * - Workspace members have specific WorkspaceRoles distinct from system roles
 *
 * <p><b>Thread Safety</b><br>
 * Immutable after construction. Safe for concurrent access.
 *
 * @doc.type class
 * @doc.purpose Workspace domain entity
 * @doc.layer product
 * @doc.pattern Domain Model
 * @see WorkspaceMember
 * @see WorkspaceSettings
 */
public final class Workspace {
    private final String workspaceId;
    private final String name;
    private final String description;
    private final String ownerId;           // User who created
    private final String orgUnitId;         // Link to OrgUnit
    private final WorkspaceSettings settings;
    private final List<WorkspaceMember> members;
    private final WorkspaceStatus status;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * Workspace lifecycle status.
     */
    public enum WorkspaceStatus {
        /**
         * Workspace is active and operational.
         */
        ACTIVE,
        /**
         * Workspace is archived and read-only.
         */
        ARCHIVED,
        /**
         * Workspace is temporarily suspended.
         */
        SUSPENDED
    }

    /**
     * Creates workspace (use builder instead).
     */
    private Workspace(Builder builder) {
        this.workspaceId = Objects.requireNonNull(builder.workspaceId, "workspaceId cannot be null");
        this.name = Objects.requireNonNull(builder.name, "name cannot be null");
        this.description = builder.description != null ? builder.description : "";
        this.ownerId = Objects.requireNonNull(builder.ownerId, "ownerId cannot be null");
        this.orgUnitId = Objects.requireNonNull(builder.orgUnitId, "orgUnitId cannot be null");
        this.settings = builder.settings != null ? builder.settings : WorkspaceSettings.defaults();
        this.members = new ArrayList<>(builder.members != null ? builder.members : new ArrayList<>());
        this.status = builder.status != null ? builder.status : WorkspaceStatus.ACTIVE;
        this.createdAt = Objects.requireNonNull(builder.createdAt, "createdAt cannot be null");
        this.updatedAt = Objects.requireNonNull(builder.updatedAt, "updatedAt cannot be null");

        if (name.isBlank()) {
            throw new IllegalArgumentException("Workspace name cannot be blank");
        }
    }

    // ============ Accessors ============

    public String workspaceId() {
        return workspaceId;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public String ownerId() {
        return ownerId;
    }

    public String orgUnitId() {
        return orgUnitId;
    }

    public WorkspaceSettings settings() {
        return settings;
    }

    public List<WorkspaceMember> members() {
        return new ArrayList<>(members);
    }

    public WorkspaceStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    // ============ Business Logic ============

    /**
     * Check if workspace is active.
     *
     * @return true if workspace status is ACTIVE
     */
    public boolean isActive() {
        return status == WorkspaceStatus.ACTIVE;
    }

    /**
     * Check if user is member.
     *
     * @param userId User ID to check
     * @return true if user is member
     */
    public boolean isMember(String userId) {
        return members.stream()
            .anyMatch(m -> m.userId().equals(userId));
    }

    /**
     * Get member role.
     *
     * @param userId User ID
     * @return Optional containing member's role
     */
    public Optional<WorkspaceRole> getMemberRole(String userId) {
        return members.stream()
            .filter(m -> m.userId().equals(userId))
            .map(WorkspaceMember::role)
            .findFirst();
    }

    /**
     * Check if user is owner.
     *
     * @param userId User ID
     * @return true if user is workspace owner
     */
    public boolean isOwner(String userId) {
        return ownerId.equals(userId);
    }

    /**
     * Check if user has admin role in workspace.
     *
     * @param userId User ID
     * @return true if user is OWNER or ADMIN
     */
    public boolean isAdmin(String userId) {
        return getMemberRole(userId)
            .map(role -> role == WorkspaceRole.OWNER || role == WorkspaceRole.ADMIN)
            .orElse(false);
    }

    // ============ Builder ============

    /**
     * Create builder for workspace.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for Workspace.
     */
    public static final class Builder {
        private String workspaceId;
        private String name;
        private String description;
        private String ownerId;
        private String orgUnitId;
        private WorkspaceSettings settings;
        private List<WorkspaceMember> members;
        private WorkspaceStatus status;
        private Instant createdAt;
        private Instant updatedAt;

        public Builder workspaceId(String workspaceId) {
            this.workspaceId = workspaceId;
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

        public Builder ownerId(String ownerId) {
            this.ownerId = ownerId;
            return this;
        }

        public Builder orgUnitId(String orgUnitId) {
            this.orgUnitId = orgUnitId;
            return this;
        }

        public Builder settings(WorkspaceSettings settings) {
            this.settings = settings;
            return this;
        }

        public Builder members(List<WorkspaceMember> members) {
            this.members = members;
            return this;
        }

        public Builder status(WorkspaceStatus status) {
            this.status = status;
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
         * Build the workspace.
         *
         * @return Workspace instance
         * @throws NullPointerException if required fields are missing
         * @throws IllegalArgumentException if values are invalid
         */
        public Workspace build() {
            return new Workspace(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Workspace)) return false;
        Workspace workspace = (Workspace) o;
        return Objects.equals(workspaceId, workspace.workspaceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workspaceId);
    }

    @Override
    public String toString() {
        return "Workspace{" +
            "workspaceId='" + workspaceId + '\'' +
            ", name='" + name + '\'' +
            ", status=" + status +
            ", ownerId='" + ownerId + '\'' +
            ", createdAt=" + createdAt +
            '}';
    }
}