package com.ghatana.datacloud.entity.governance;

import java.time.Instant;
import java.util.*;

/**
 * Immutable value object representing a Role in the system.
 *
 * <p><b>Purpose</b><br>
 * Defines a role with a set of permissions. Roles can be assigned to users or service accounts
 * within a tenant context. Supports role hierarchies and permission inheritance.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Role adminRole = Role.builder("admin")
 *     .description("Full administrative access")
 *     .permission("collection:create")
 *     .permission("collection:delete")
 *     .permission("audit:read")
 *     .build();
 * }</pre>
 *
 * <p><b>Permission Patterns</b><br>
 * Permissions follow resource:action format:
 * - collection:create, collection:read, collection:update, collection:delete
 * - audit:read, audit:export
 * - settings:manage, rbac:manage
 * - Wildcard: "admin:*" for full admin access
 *
 * @see RoleAssignment
 * @doc.type class
 * @doc.purpose Role definition with permissions
 * @doc.layer domain
 * @doc.pattern Value Object
 */
public final class Role {
    private final String roleId;
    private final String name;
    private final String description;
    private final Set<String> permissions;
    private final Set<String> inheritedRoles;
    private final boolean isSystemRole;
    private final Instant createdAt;
    private final Instant updatedAt;

    /**
     * Creates a new Role instance.
     *
     * @param roleId Unique role identifier (UUID format)
     * @param name Role name (e.g., "admin", "viewer")
     * @param description Human-readable description
     * @param permissions Set of permission strings (immutable copy)
     * @param inheritedRoles Set of parent role IDs (for inheritance)
     * @param isSystemRole Whether this is a system-defined role
     * @param createdAt Creation timestamp
     * @param updatedAt Last update timestamp
     */
    public Role(
            String roleId,
            String name,
            String description,
            Set<String> permissions,
            Set<String> inheritedRoles,
            boolean isSystemRole,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.roleId = Objects.requireNonNull(roleId, "roleId required");
        this.name = Objects.requireNonNull(name, "name required");
        this.description = description != null ? description : "";
        this.permissions = Collections.unmodifiableSet(new HashSet<>(Objects.requireNonNull(permissions, "permissions required")));
        this.inheritedRoles = Collections.unmodifiableSet(new HashSet<>(Objects.requireNonNull(inheritedRoles, "inheritedRoles required")));
        this.isSystemRole = isSystemRole;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt required");
    }

    /**
     * Returns role identifier.
     *
     * @return unique role ID
     */
    public String getRoleId() {
        return roleId;
    }

    /**
     * Returns role name.
     *
     * @return role name (e.g., "admin", "viewer")
     */
    public String getName() {
        return name;
    }

    /**
     * Returns role description.
     *
     * @return human-readable description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns set of permissions this role has.
     *
     * @return immutable set of permission strings
     */
    public Set<String> getPermissions() {
        return permissions;
    }

    /**
     * Returns set of parent roles this role inherits from.
     *
     * @return immutable set of inherited role IDs
     */
    public Set<String> getInheritedRoles() {
        return inheritedRoles;
    }

    /**
     * Checks if this role has a specific permission.
     *
     * @param permission Permission to check (e.g., "collection:read")
     * @return true if role has permission
     */
    public boolean hasPermission(String permission) {
        Objects.requireNonNull(permission, "permission required");
        return permissions.contains(permission) || permissions.contains("*");
    }

    /**
     * Checks if this is a system-defined role.
     *
     * @return true if system role (cannot be deleted)
     */
    public boolean isSystemRole() {
        return isSystemRole;
    }

    /**
     * Returns when this role was created.
     *
     * @return creation timestamp
     */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns when this role was last updated.
     *
     * @return update timestamp
     */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Creates a builder for Role construction.
     *
     * @param roleId Role identifier
     * @return new RoleBuilder instance
     */
    public static RoleBuilder builder(String roleId) {
        return new RoleBuilder(roleId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Role role = (Role) o;
        return roleId.equals(role.roleId) &&
                name.equals(role.name) &&
                Objects.equals(description, role.description) &&
                permissions.equals(role.permissions) &&
                inheritedRoles.equals(role.inheritedRoles) &&
                isSystemRole == role.isSystemRole;
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleId, name, description, permissions, inheritedRoles, isSystemRole);
    }

    @Override
    public String toString() {
        return "Role{" +
                "roleId='" + roleId + '\'' +
                ", name='" + name + '\'' +
                ", permissions=" + permissions.size() +
                ", inheritedRoles=" + inheritedRoles.size() +
                ", isSystemRole=" + isSystemRole +
                '}';
    }

    /**
     * Builder for constructing Role instances.
     */
    public static class RoleBuilder {
        private final String roleId;
        private String description = "";
        private final Set<String> permissions = new HashSet<>();
        private final Set<String> inheritedRoles = new HashSet<>();
        private boolean isSystemRole = false;
        private Instant createdAt = Instant.now();
        private Instant updatedAt = Instant.now();

        RoleBuilder(String roleId) {
            this.roleId = Objects.requireNonNull(roleId, "roleId required");
        }

        /**
         * Sets role description.
         *
         * @param description Human-readable description
         * @return this builder
         */
        public RoleBuilder description(String description) {
            this.description = description != null ? description : "";
            return this;
        }

        /**
         * Adds a permission to this role.
         *
         * @param permission Permission string (e.g., "collection:create")
         * @return this builder
         */
        public RoleBuilder permission(String permission) {
            this.permissions.add(Objects.requireNonNull(permission, "permission required"));
            return this;
        }

        /**
         * Adds multiple permissions to this role.
         *
         * @param permissions Collection of permission strings
         * @return this builder
         */
        public RoleBuilder permissions(Collection<String> permissions) {
            this.permissions.addAll(Objects.requireNonNull(permissions, "permissions required"));
            return this;
        }

        /**
         * Marks this role as inheriting from another role.
         *
         * @param parentRoleId Parent role ID
         * @return this builder
         */
        public RoleBuilder inheritsFrom(String parentRoleId) {
            this.inheritedRoles.add(Objects.requireNonNull(parentRoleId, "parentRoleId required"));
            return this;
        }

        /**
         * Marks this as a system role.
         *
         * @return this builder
         */
        public RoleBuilder asSystemRole() {
            this.isSystemRole = true;
            return this;
        }

        /**
         * Sets creation timestamp.
         *
         * @param createdAt Creation time
         * @return this builder
         */
        public RoleBuilder createdAt(Instant createdAt) {
            this.createdAt = Objects.requireNonNull(createdAt, "createdAt required");
            return this;
        }

        /**
         * Builds the Role instance.
         *
         * @return new Role instance
         */
        public Role build() {
            return new Role(
                    roleId,
                    roleId,
                    description,
                    permissions,
                    inheritedRoles,
                    isSystemRole,
                    createdAt,
                    updatedAt
            );
        }
    }
}
