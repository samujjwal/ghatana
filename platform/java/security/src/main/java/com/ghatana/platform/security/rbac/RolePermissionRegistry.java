package com.ghatana.platform.security.rbac;

import java.util.Set;

/**
 * Registry that maps roles to their permissions.
 
 *
 * @doc.type interface
 * @doc.purpose Role permission registry
 * @doc.layer core
 * @doc.pattern Registry
*/
public interface RolePermissionRegistry {
    /**
     * Get all permissions for a role.
     *
     * @param role The role name
     * @return Set of permissions, or null if role not found
     */
    Set<String> getPermissions(String role);

    /**
     * Check if a role has a specific permission.
     *
     * @param role       The role name
     * @param permission The permission to check
     * @return true if the role has the permission, false otherwise
     */
    default boolean hasPermission(String role, String permission) {
        Set<String> permissions = getPermissions(role);
        return permissions != null && permissions.contains(permission);
    }

    /**
     * Register permissions for a role.
     *
     * @param role        The role name
     * @param permissions Set of permissions to register
     */
    void registerRole(String role, Set<String> permissions);
}
