package com.ghatana.datacloud.infrastructure.governance.http.dto;

import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/**
 * HTTP request DTO for creating a new role.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates HTTP request data for role creation with validation.
 * Immutable request object with builder pattern for safe construction.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * CreateRoleRequest request = CreateRoleRequest.builder()
 *     .tenantId("tenant-123")
 *     .roleId("admin-role")
 *     .roleName("Administrator")
 *     .description("System administrator with full access")
 *     .addPermission("users:read")
 *     .addPermission("users:write")
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose HTTP request DTO for role creation
 * @doc.layer infrastructure
 * @doc.pattern Data Transfer Object (DTO)
 */
public final class CreateRoleRequest {
    private final String tenantId;
    private final String roleId;
    private final String roleName;
    private final String description;
    private final Set<String> permissions;
    private final boolean isActive;

    private CreateRoleRequest(
            String tenantId,
            String roleId,
            String roleName,
            String description,
            Set<String> permissions,
            boolean isActive) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId cannot be null");
        this.roleId = Objects.requireNonNull(roleId, "roleId cannot be null");
        this.roleName = Objects.requireNonNull(roleName, "roleName cannot be null");
        this.description = description != null ? description : "";
        this.permissions = new HashSet<>(permissions != null ? permissions : Set.of());
        this.isActive = isActive;

        // Validation
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be blank");
        }
        if (roleId.isBlank()) {
            throw new IllegalArgumentException("roleId cannot be blank");
        }
        if (roleName.isBlank()) {
            throw new IllegalArgumentException("roleName cannot be blank");
        }
        if (roleName.length() > 255) {
            throw new IllegalArgumentException("roleName exceeds max length (255)");
        }
        if (description.length() > 1000) {
            throw new IllegalArgumentException("description exceeds max length (1000)");
        }
    }

    /**
     * Gets tenant ID (from path parameter or header).
     */
    public String getTenantId() {
        return tenantId;
    }

    /**
     * Gets unique role identifier.
     */
    public String getRoleId() {
        return roleId;
    }

    /**
     * Gets human-readable role name.
     */
    public String getRoleName() {
        return roleName;
    }

    /**
     * Gets role description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets set of permissions for this role (defensive copy).
     */
    public Set<String> getPermissions() {
        return new HashSet<>(permissions);
    }

    /**
     * Gets active status.
     */
    public boolean isActive() {
        return isActive;
    }

    /**
     * Creates a builder for CreateRoleRequest.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for CreateRoleRequest.
     */
    public static class Builder {
        private String tenantId;
        private String roleId;
        private String roleName;
        private String description = "";
        private Set<String> permissions = new HashSet<>();
        private boolean isActive = true;

        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public Builder roleId(String roleId) {
            this.roleId = roleId;
            return this;
        }

        public Builder roleName(String roleName) {
            this.roleName = roleName;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder addPermission(String permission) {
            this.permissions.add(Objects.requireNonNull(permission));
            return this;
        }

        public Builder permissions(Set<String> permissions) {
            this.permissions = new HashSet<>(permissions);
            return this;
        }

        public Builder isActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public CreateRoleRequest build() {
            return new CreateRoleRequest(
                    tenantId,
                    roleId,
                    roleName,
                    description,
                    permissions,
                    isActive);
        }
    }

    @Override
    public String toString() {
        return "CreateRoleRequest{" +
                "tenantId='" + tenantId + '\'' +
                ", roleId='" + roleId + '\'' +
                ", roleName='" + roleName + '\'' +
                ", permissionCount=" + permissions.size() +
                ", isActive=" + isActive +
                '}';
    }
}
