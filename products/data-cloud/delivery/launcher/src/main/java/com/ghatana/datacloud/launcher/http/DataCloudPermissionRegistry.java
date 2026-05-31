package com.ghatana.datacloud.launcher.http;

import com.ghatana.platform.security.rbac.RolePermissionRegistry;

import java.util.Map;
import java.util.Set;

/**
 * Canonical permission registry for Data Cloud.
 * Defines the authoritative mapping of roles to permissions across all Data Cloud planes.
 *
 * @doc.type class
 * @doc.purpose Canonical permission registry for Data Cloud
 * @doc.layer product
 * @doc.pattern Registry
 */
public class DataCloudPermissionRegistry implements RolePermissionRegistry {
    private final RolePermissionRegistry delegate;

    public DataCloudPermissionRegistry(RolePermissionRegistry delegate) {
        this.delegate = delegate;
    }

    /**
     * Initialize the registry with canonical Data Cloud role-to-permission mappings.
     * This method should be called during application startup to populate the registry.
     */
    public void initialize() {
        // Admin role - full access to all Data Cloud capabilities
        delegate.registerRole("ADMIN", Set.of(
            "datacloud:read", "datacloud:write", "datacloud:delete", "datacloud:admin",
            "connector:read", "connector:register", "connector:update", "connector:delete", "connector:sync", "connector:rotate-credentials",
            "action:pipeline:read", "action:pipeline:create", "action:pipeline:update", "action:pipeline:delete", "action:pipeline:execute", "action:pipeline:cancel",
            "media:artifact:read", "media:artifact:create", "media:artifact:process", "media:artifact:delete",
            "surface:read", "governance:read", "governance:write", "governance:policy:manage"
        ));

        // PLATFORM_ADMIN - platform-wide admin (alias for ADMIN)
        delegate.registerRole("PLATFORM_ADMIN", Set.of(
            "datacloud:read", "datacloud:write", "datacloud:delete", "datacloud:admin",
            "connector:read", "connector:register", "connector:update", "connector:delete", "connector:sync", "connector:rotate-credentials",
            "action:pipeline:read", "action:pipeline:create", "action:pipeline:update", "action:pipeline:delete", "action:pipeline:execute", "action:pipeline:cancel",
            "media:artifact:read", "media:artifact:create", "media:artifact:process", "media:artifact:delete",
            "surface:read", "governance:read", "governance:write", "governance:policy:manage"
        ));

        // Operator role - operational access for day-to-day tasks
        delegate.registerRole("OPERATOR", Set.of(
            "datacloud:read", "datacloud:write",
            "connector:read", "connector:sync",
            "action:pipeline:read", "action:pipeline:execute",
            "media:artifact:read", "media:artifact:process",
            "surface:read", "context:read", "context:write"
        ));

        // Editor role - content editing capabilities
        delegate.registerRole("EDITOR", Set.of(
            "datacloud:read", "datacloud:write",
            "media:artifact:create", "media:artifact:read", "media:artifact:process",
            "surface:read", "context:read", "context:write"
        ));

        // Viewer role - read-only access
        delegate.registerRole("VIEWER", Set.of(
            "datacloud:read", "surface:read", "connector:read", "media:artifact:read", "action:pipeline:read"
        ));

        // Auditor role - audit and compliance access
        delegate.registerRole("AUDITOR", Set.of(
            "datacloud:read", "datacloud:audit", "governance:read", "governance:compliance:read"
        ));

        // Processor role - background processing and job execution
        delegate.registerRole("PROCESSOR", Set.of(
            "media:artifact:process", "action:pipeline:execute", "connector:sync"
        ));
    }

    @Override
    public Set<String> getPermissions(String role) {
        return delegate.getPermissions(role);
    }

    @Override
    public boolean hasPermission(String role, String permission) {
        return delegate.hasPermission(role, permission);
    }

    @Override
    public void registerRole(String role, Set<String> permissions) {
        delegate.registerRole(role, permissions);
    }

    /**
     * Get the canonical role-to-permission mapping as an immutable map.
     * This is useful for documentation and testing purposes.
     */
    public static Map<String, Set<String>> getCanonicalMapping() {
        return Map.of(
            "ADMIN", Set.of(
                "datacloud:read", "datacloud:write", "datacloud:delete", "datacloud:admin",
                "connector:read", "connector:register", "connector:update", "connector:delete", "connector:sync", "connector:rotate-credentials",
                "action:pipeline:read", "action:pipeline:create", "action:pipeline:update", "action:pipeline:delete", "action:pipeline:execute", "action:pipeline:cancel",
                "media:artifact:read", "media:artifact:create", "media:artifact:process", "media:artifact:delete",
                "surface:read", "governance:read", "governance:write", "governance:policy:manage"
            ),
            "PLATFORM_ADMIN", Set.of(
                "datacloud:read", "datacloud:write", "datacloud:delete", "datacloud:admin",
                "connector:read", "connector:register", "connector:update", "connector:delete", "connector:sync", "connector:rotate-credentials",
                "action:pipeline:read", "action:pipeline:create", "action:pipeline:update", "action:pipeline:delete", "action:pipeline:execute", "action:pipeline:cancel",
                "media:artifact:read", "media:artifact:create", "media:artifact:process", "media:artifact:delete",
                "surface:read", "governance:read", "governance:write", "governance:policy:manage"
            ),
            "OPERATOR", Set.of(
                "datacloud:read", "datacloud:write",
                "connector:read", "connector:sync",
                "action:pipeline:read", "action:pipeline:execute",
                "media:artifact:read", "media:artifact:process",
                "surface:read", "context:read", "context:write"
            ),
            "EDITOR", Set.of(
                "datacloud:read", "datacloud:write",
                "media:artifact:create", "media:artifact:read", "media:artifact:process",
                "surface:read", "context:read", "context:write"
            ),
            "VIEWER", Set.of(
                "datacloud:read", "surface:read", "connector:read", "media:artifact:read", "action:pipeline:read"
            ),
            "AUDITOR", Set.of(
                "datacloud:read", "datacloud:audit", "governance:read", "governance:compliance:read"
            ),
            "PROCESSOR", Set.of(
                "media:artifact:process", "action:pipeline:execute", "connector:sync"
            )
        );
    }
}
