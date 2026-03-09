package com.ghatana.platform.governance.rbac;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.Set;

/**
 * Role in the RBAC system.
 *
 * <p>A role is a collection of permissions that can be assigned to users.
 *
 * <p>Usage example:
 * <pre>{@code
 * Role adminRole = Role.builder()
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
 * @doc.purpose RBAC role with permission set
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
@Value
@Builder
public class Role {
    
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
