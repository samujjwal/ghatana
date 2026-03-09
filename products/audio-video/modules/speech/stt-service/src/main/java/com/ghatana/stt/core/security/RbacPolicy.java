package com.ghatana.stt.core.security;

import java.util.Set;

/**
 * Interface for RBAC policy evaluation.
 *
 * @doc.type interface
 * @doc.purpose RBAC policy strategy
 * @doc.layer security
 * @doc.pattern Strategy
 */
public interface RbacPolicy {
    /**
     * Checks if a role has a specific permission.
     *
     * @param role the role to check
     * @param permission the permission to check
     * @return true if the role has the permission
     */
    boolean hasPermission(String role, String permission);

    /**
     * Gets all permissions for a role.
     *
     * @param role the role
     * @return set of permissions
     */
    Set<String> getPermissions(String role);

    /**
     * Checks if user roles are allowed to access a method.
     *
     * @param userRoles the user's roles
     * @param method the method being accessed
     * @return true if allowed
     */
    boolean isAllowed(Set<String> userRoles, String method);
}
