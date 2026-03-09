package com.ghatana.datacloud.application.policy;

import java.util.Map;
import java.util.Objects;

/**
 * Descriptor for RBAC changes requiring policy evaluation.
 *
 * <p><b>Purpose</b><br>
 * Represents role or permission changes for governance workflows.
 * Includes role assignments, permission grants, and revocations.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RbacChange roleAssignment = RbacChange.builder()
 *     .type("ROLE_ASSIGNMENT")
 *     .targetUserId("user-789")
 *     .role("collection:admin")
 *     .build();
 *
 * RbacChange permissionGrant = RbacChange.builder()
 *     .type("PERMISSION_GRANT")
 *     .targetUserId("user-123")
 *     .permission("schema:update")
 *     .build();
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * Immutable value object - thread-safe.
 *
 * @doc.type class
 * @doc.purpose RBAC change descriptor for policy evaluation
 * @doc.layer product
 * @doc.pattern Value Object
 */
public class RbacChange {

    private final String type;
    private final String targetUserId;
    private final String role;
    private final String permission;

    private RbacChange(Builder builder) {
        this.type = Objects.requireNonNull(builder.type, "type must not be null");
        this.targetUserId = Objects.requireNonNull(builder.targetUserId, "targetUserId must not be null");
        this.role = builder.role;
        this.permission = builder.permission;

        // Validate: either role or permission must be present
        if (role == null && permission == null) {
            throw new IllegalArgumentException("Either role or permission must be specified");
        }
    }

    /**
     * Gets change type.
     *
     * @return change type (e.g., "ROLE_ASSIGNMENT", "PERMISSION_GRANT")
     */
    public String getType() {
        return type;
    }

    /**
     * Gets target user ID.
     *
     * @return user ID receiving the role/permission
     */
    public String getTargetUserId() {
        return targetUserId;
    }

    /**
     * Gets role (if role assignment).
     *
     * @return role name or null if permission grant
     */
    public String getRole() {
        return role;
    }

    /**
     * Gets permission (if permission grant).
     *
     * @return permission name or null if role assignment
     */
    public String getPermission() {
        return permission;
    }

    /**
     * Converts to map for policy evaluation.
     *
     * @return map representation suitable for policy engine
     */
    public Map<String, Object> toMap() {
        return Map.of(
            "type", type,
            "targetUserId", targetUserId,
            "role", role != null ? role : "",
            "permission", permission != null ? permission : ""
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String type;
        private String targetUserId;
        private String role;
        private String permission;

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder targetUserId(String targetUserId) {
            this.targetUserId = targetUserId;
            return this;
        }

        public Builder role(String role) {
            this.role = role;
            return this;
        }

        public Builder permission(String permission) {
            this.permission = permission;
            return this;
        }

        public RbacChange build() {
            return new RbacChange(this);
        }
    }

    @Override
    public String toString() {
        return String.format("RbacChange{type='%s', targetUserId='%s', role='%s', permission='%s'}",
            type, targetUserId, role, permission);
    }
}
