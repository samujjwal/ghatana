package com.ghatana.platform.governance.rbac;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.Set;

/**
 * Role definition in the RBAC system — a named role bundled with its permission set.
 *
 * <p>A role definition is a collection of permissions that can be assigned to users.
 * Renamed from {@code Role} to avoid ambiguity with
 * {@link com.ghatana.platform.domain.auth.Role}, the canonical typed role-name value object.
 *
 * <p>Usage example:
 * <pre>{@code
 * RoleDefinition adminRole = RoleDefinition.builder()
 *     .name("admin")
 *     .permission("users:read")
 *     .permission("users:write")
 *     .permission("config:read")
 *     .permission("config:write")
 *     .build();
 * }</pre>
 *
 * @author Platform Team
 * @since 1.0.0
 *
 * @doc.type class
 * @doc.purpose RBAC role definition with bundled permission set
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
@Value
@Builder
public class RoleDefinition {

    /**
     * Role name.
     */
    String name;

    /**
     * Set of permissions granted by this role.
     */
    @Singular
    Set<String> permissions;

    /**
     * Checks if this role has the specified permission.
     *
     * @param permission permission to check
     * @return true if this role has the permission
     */
    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    /**
     * Checks if this role has all the specified permissions.
     *
     * @param requiredPermissions permissions to check
     * @return true if this role has all the permissions
     */
    public boolean hasAllPermissions(Set<String> requiredPermissions) {
        return permissions.containsAll(requiredPermissions);
    }

    /**
     * Checks if this role has any of the specified permissions.
     *
     * @param anyPermissions permissions to check
     * @return true if this role has any of the permissions
     */
    public boolean hasAnyPermission(Set<String> anyPermissions) {
        return anyPermissions.stream().anyMatch(this::hasPermission);
    }
}
