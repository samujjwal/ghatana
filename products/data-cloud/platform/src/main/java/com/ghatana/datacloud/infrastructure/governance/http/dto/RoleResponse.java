package com.ghatana.datacloud.infrastructure.governance.http.dto;

import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

/**
 * HTTP response DTO for role data.
 *
 * <p><b>Purpose</b><br>
 * Encapsulates HTTP response data for role information. Immutable response object.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * RoleResponse response = RoleResponse.builder()
 *     .tenantId("tenant-123")
 *     .roleId("admin-role")
 *     .roleName("Administrator")
 *     .description("System administrator")
 *     .addPermission("users:read")
 *     .isActive(true)
 *     .createdAt(Instant.now())
 *     .updatedAt(Instant.now())
 *     .build();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose HTTP response DTO for role information
 * @doc.layer infrastructure
 * @doc.pattern Data Transfer Object (DTO)
 */
public final class RoleResponse {
    private final String tenantId;
    private final String roleId;
    private final String roleName;
    private final String description;
    private final Set<String> permissions;
    private final boolean isActive;
    private final long createdAt;
    private final long updatedAt;
    private final int permissionCount;

    private RoleResponse(
            String tenantId,
            String roleId,
            String roleName,
            String description,
            Set<String> permissions,
            boolean isActive,
            long createdAt,
            long updatedAt) {
        this.tenantId = Objects.requireNonNull(tenantId);
        this.roleId = Objects.requireNonNull(roleId);
        this.roleName = Objects.requireNonNull(roleName);
        this.description = description != null ? description : "";
        this.permissions = new HashSet<>(permissions != null ? permissions : Set.of());
        this.isActive = isActive;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.permissionCount = this.permissions.size();
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getRoleId() {
        return roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public String getDescription() {
        return description;
    }

    public Set<String> getPermissions() {
        return new HashSet<>(permissions);
    }

    public boolean isActive() {
        return isActive;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public int getPermissionCount() {
        return permissionCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String tenantId;
        private String roleId;
        private String roleName;
        private String description = "";
        private Set<String> permissions = new HashSet<>();
        private boolean isActive = true;
        private long createdAt;
        private long updatedAt;

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

        public Builder createdAt(long createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(long updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public RoleResponse build() {
            return new RoleResponse(
                    tenantId,
                    roleId,
                    roleName,
                    description,
                    permissions,
                    isActive,
                    createdAt,
                    updatedAt);
        }
    }

    @Override
    public String toString() {
        return "RoleResponse{" +
                "tenantId='" + tenantId + '\'' +
                ", roleId='" + roleId + '\'' +
                ", roleName='" + roleName + '\'' +
                ", permissionCount=" + permissionCount +
                ", isActive=" + isActive +
                '}';
    }
}
