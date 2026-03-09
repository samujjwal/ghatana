package com.ghatana.datacloud.entity.governance;

import java.time.Instant;
import java.util.*;

/**
 * Immutable value object representing assignment of a role to a principal.
 *
 * <p><b>Purpose</b><br>
 * Associates a role with a user or service account within a tenant. Supports role inheritance
 * through transitivity and enables efficient permission checking via recursive lookup.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RoleAssignment assignment = RoleAssignment.builder()
 *     .assignmentId(UUID.randomUUID().toString())
 *     .tenantId("tenant-1")
 *     .principalId("user-123")
 *     .principalType(PrincipalType.USER)
 *     .roleId("admin")
 *     .grantedAt(Instant.now())
 *     .grantedBy("system")
 *     .build();
 * }</pre>
 *
 * <p><b>Multi-Tenant Isolation</b><br>
 * All role assignments are scoped to a tenant. Users cannot access roles outside their tenant.
 * Role inheritance is tenant-scoped (parent roles must be in same tenant).
 *
 * @see Role
 * @doc.type class
 * @doc.purpose Role assignment to principal (user/service account)
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public final class RoleAssignment {
    private final String assignmentId;
    private final String tenantId;
    private final String principalId;
    private final PrincipalType principalType;
    private final String roleId;
    private final Instant grantedAt;
    private final String grantedBy;
    private final Instant revokedAt;
    private final String revokedBy;
    private final Map<String, String> metadata;

    /**
     * Enumeration of principal types that can have role assignments.
     */
    public enum PrincipalType {
        USER("user"),
        SERVICE_ACCOUNT("service_account"),
        GROUP("group");

        private final String value;

        PrincipalType(String value) {
            this.value = value;
        }

        /**
         * Returns string representation.
         *
         * @return principal type as string
         */
        public String getValue() {
            return value;
        }
    }

    /**
     * Creates a new RoleAssignment instance.
     *
     * @param assignmentId Unique assignment identifier
     * @param tenantId Tenant identifier (for isolation)
     * @param principalId User/service account/group ID
     * @param principalType Type of principal
     * @param roleId Role being assigned
     * @param grantedAt When the role was granted
     * @param grantedBy Who granted the role
     * @param revokedAt When the role was revoked (null if active)
     * @param revokedBy Who revoked the role
     * @param metadata Optional metadata (immutable)
     */
    public RoleAssignment(
            String assignmentId,
            String tenantId,
            String principalId,
            PrincipalType principalType,
            String roleId,
            Instant grantedAt,
            String grantedBy,
            Instant revokedAt,
            String revokedBy,
            Map<String, String> metadata
    ) {
        this.assignmentId = Objects.requireNonNull(assignmentId, "assignmentId required");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId required");
        this.principalId = Objects.requireNonNull(principalId, "principalId required");
        this.principalType = Objects.requireNonNull(principalType, "principalType required");
        this.roleId = Objects.requireNonNull(roleId, "roleId required");
        this.grantedAt = Objects.requireNonNull(grantedAt, "grantedAt required");
        this.grantedBy = Objects.requireNonNull(grantedBy, "grantedBy required");
        this.revokedAt = revokedAt;
        this.revokedBy = revokedBy;
        this.metadata = Collections.unmodifiableMap(new HashMap<>(Objects.requireNonNull(metadata, "metadata required")));
    }

    /**
     * Returns assignment identifier.
     *
     * @return unique assignment ID
     */
    public String getAssignmentId() {
        return assignmentId;
    }

    /**
     * Returns tenant identifier.
     *
     * @return tenant ID (for multi-tenant isolation)
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Returns principal identifier.
     *
     * @return user/service account/group ID
     */
    public String getPrincipalId() {
        return principalId;
    }

    /**
     * Returns principal type.
     *
     * @return type of principal (USER, SERVICE_ACCOUNT, GROUP)
     */
    public PrincipalType getPrincipalType() {
        return principalType;
    }

    /**
     * Returns role identifier.
     *
     * @return role ID assigned to principal
     */
    public String getRoleId() {
        return roleId;
    }

    /**
     * Returns when role was granted.
     *
     * @return grant timestamp
     */
    public Instant getGrantedAt() {
        return grantedAt;
    }

    /**
     * Returns who granted the role.
     *
     * @return granter principal ID
     */
    public String getGrantedBy() {
        return grantedBy;
    }

    /**
     * Returns when role was revoked (if revoked).
     *
     * @return revoke timestamp or null if active
     */
    public Instant getRevokedAt() {
        return revokedAt;
    }

    /**
     * Returns who revoked the role (if revoked).
     *
     * @return revoker principal ID or null if active
     */
    public String getRevokedBy() {
        return revokedBy;
    }

    /**
     * Checks if this assignment is currently active.
     *
     * @return true if not revoked
     */
    public boolean isActive() {
        return revokedAt == null;
    }

    /**
     * Returns optional metadata.
     *
     * @return immutable metadata map
     */
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * Creates a builder for RoleAssignment construction.
     *
     * @return new RoleAssignmentBuilder instance
     */
    public static RoleAssignmentBuilder builder() {
        return new RoleAssignmentBuilder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RoleAssignment that = (RoleAssignment) o;
        return assignmentId.equals(that.assignmentId) &&
                tenantId.equals(that.tenantId) &&
                principalId.equals(that.principalId) &&
                roleId.equals(that.roleId) &&
                isActive() == that.isActive();
    }

    @Override
    public int hashCode() {
        return Objects.hash(assignmentId, tenantId, principalId, roleId, isActive());
    }

    @Override
    public String toString() {
        return "RoleAssignment{" +
                "assignmentId='" + assignmentId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", principalId='" + principalId + '\'' +
                ", principalType=" + principalType +
                ", roleId='" + roleId + '\'' +
                ", active=" + isActive() +
                '}';
    }

    /**
     * Builder for constructing RoleAssignment instances.
     */
    public static class RoleAssignmentBuilder {
        private String assignmentId = UUID.randomUUID().toString();
        private String tenantId;
        private String principalId;
        private PrincipalType principalType = PrincipalType.USER;
        private String roleId;
        private Instant grantedAt = Instant.now();
        private String grantedBy = "system";
        private Instant revokedAt;
        private String revokedBy;
        private final Map<String, String> metadata = new HashMap<>();

        /**
         * Sets assignment ID.
         *
         * @param assignmentId Unique assignment identifier
         * @return this builder
         */
        public RoleAssignmentBuilder assignmentId(String assignmentId) {
            this.assignmentId = Objects.requireNonNull(assignmentId, "assignmentId required");
            return this;
        }

        /**
         * Sets tenant ID.
         *
         * @param tenantId Tenant identifier
         * @return this builder
         */
        public RoleAssignmentBuilder tenantId(String tenantId) {
            this.tenantId = Objects.requireNonNull(tenantId, "tenantId required");
            return this;
        }

        /**
         * Sets principal ID.
         *
         * @param principalId Principal identifier
         * @return this builder
         */
        public RoleAssignmentBuilder principalId(String principalId) {
            this.principalId = Objects.requireNonNull(principalId, "principalId required");
            return this;
        }

        /**
         * Sets principal type.
         *
         * @param principalType Type of principal
         * @return this builder
         */
        public RoleAssignmentBuilder principalType(PrincipalType principalType) {
            this.principalType = Objects.requireNonNull(principalType, "principalType required");
            return this;
        }

        /**
         * Sets role ID.
         *
         * @param roleId Role being assigned
         * @return this builder
         */
        public RoleAssignmentBuilder roleId(String roleId) {
            this.roleId = Objects.requireNonNull(roleId, "roleId required");
            return this;
        }

        /**
         * Sets when role was granted.
         *
         * @param grantedAt Grant timestamp
         * @return this builder
         */
        public RoleAssignmentBuilder grantedAt(Instant grantedAt) {
            this.grantedAt = Objects.requireNonNull(grantedAt, "grantedAt required");
            return this;
        }

        /**
         * Sets who granted the role.
         *
         * @param grantedBy Granter principal ID
         * @return this builder
         */
        public RoleAssignmentBuilder grantedBy(String grantedBy) {
            this.grantedBy = Objects.requireNonNull(grantedBy, "grantedBy required");
            return this;
        }

        /**
         * Sets revocation info.
         *
         * @param revokedAt Revoke timestamp
         * @param revokedBy Revoker principal ID
         * @return this builder
         */
        public RoleAssignmentBuilder revokedAt(Instant revokedAt, String revokedBy) {
            this.revokedAt = revokedAt;
            this.revokedBy = Objects.requireNonNull(revokedBy, "revokedBy required");
            return this;
        }

        /**
         * Adds metadata entry.
         *
         * @param key Metadata key
         * @param value Metadata value
         * @return this builder
         */
        public RoleAssignmentBuilder metadata(String key, String value) {
            this.metadata.put(Objects.requireNonNull(key, "key required"), Objects.requireNonNull(value, "value required"));
            return this;
        }

        /**
         * Builds the RoleAssignment instance.
         *
         * @return new RoleAssignment instance
         */
        public RoleAssignment build() {
            return new RoleAssignment(
                    assignmentId,
                    tenantId,
                    principalId,
                    principalType,
                    roleId,
                    grantedAt,
                    grantedBy,
                    revokedAt,
                    revokedBy,
                    metadata
            );
        }
    }
}
