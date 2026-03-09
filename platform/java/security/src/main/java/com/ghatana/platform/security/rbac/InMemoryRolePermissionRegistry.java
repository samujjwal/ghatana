package com.ghatana.platform.security.rbac;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of RolePermissionRegistry.
 * Thread-safe and suitable for single-node deployments.
 
 *
 * @doc.type class
 * @doc.purpose In memory role permission registry
 * @doc.layer core
 * @doc.pattern Registry
*/
public class InMemoryRolePermissionRegistry implements RolePermissionRegistry {
    private final Map<String, Set<String>> rolePermissions = new ConcurrentHashMap<>();

    @Override
    public Set<String> getPermissions(String role) {
        return rolePermissions.get(role);
    }

    @Override
    public void registerRole(String role, Set<String> permissions) {
        if (role == null || role.trim().isEmpty()) {
            throw new IllegalArgumentException("Role cannot be null or empty");
        }
        if (permissions == null) {
            throw new IllegalArgumentException("Permissions cannot be null");
        }
        rolePermissions.put(role, Set.copyOf(permissions));
    }

    @Override
    public boolean hasPermission(String role, String permission) {
        Set<String> permissions = getPermissions(role);
        return permissions != null && permissions.contains(permission);
    }
}
