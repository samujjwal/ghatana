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

    private static final Set<String> ADMIN_PERMISSIONS = Set.of(
        "datacloud:read", "datacloud:write", "datacloud:delete", "datacloud:admin", "datacloud:configure", "datacloud:audit",
        "governance:read", "governance:write", "governance:delete", "governance:policy:manage", "governance:retention:manage",
        "governance:privacy:manage", "governance:compliance:read",
        "connector:read", "connector:register", "connector:update", "connector:delete", "connector:test", "connector:sync",
        "connector:rotate-credentials", "connector:link-dataset",
        "media:artifact:create", "media:artifact:read", "media:artifact:delete", "media:artifact:update-consent",
        "media:artifact:process", "media:artifact:retry", "media:artifact:read-result",
        "action:pipeline:read", "action:pipeline:write", "action:pipeline:execute",
        "action:agent:read", "action:agent:execute",
        "action:pattern:read", "action:pattern:write", "action:pattern:activate",
        "action:review:approve",
        "surface:read"
    );

    private static final Set<String> OPERATOR_PERMISSIONS = Set.of(
        "datacloud:read", "datacloud:write",
        "action:checkpoint:read", "action:checkpoint:create", "action:checkpoint:delete",
        "context:read", "context:write", "context:delete",
        "connector:read", "connector:register", "connector:update", "connector:test", "connector:sync", "connector:link-dataset",
        "media:artifact:create", "media:artifact:read", "media:artifact:update-consent",
        "media:artifact:process", "media:artifact:retry", "media:artifact:read-result",
        "action:pipeline:read", "action:pipeline:write", "action:pipeline:execute",
        "action:agent:read", "action:agent:execute", "action:pattern:read",
        "surface:read"
    );

    private static final Set<String> VIEWER_PERMISSIONS = Set.of(
        "datacloud:read", "surface:read",
        "connector:read",
        "media:artifact:read", "media:artifact:read-result",
        "action:pipeline:read", "action:agent:read", "action:pattern:read"
    );

    public DataCloudPermissionRegistry(RolePermissionRegistry delegate) {
        this.delegate = delegate;
    }

    /**
     * Initialize the registry with canonical Data Cloud role-to-permission mappings.
     * This method should be called during application startup to populate the registry.
     */
    public void initialize() {
        // Admin role - full access to all Data Cloud capabilities
        delegate.registerRole("ADMIN", ADMIN_PERMISSIONS);

        // PLATFORM_ADMIN - platform-wide admin (alias for ADMIN)
        delegate.registerRole("PLATFORM_ADMIN", ADMIN_PERMISSIONS);

        // Operator role - operational access for day-to-day tasks
        delegate.registerRole("OPERATOR", OPERATOR_PERMISSIONS);

        // Editor role - content editing capabilities
        delegate.registerRole("EDITOR", Set.of(
            "datacloud:read", "datacloud:write",
            "media:artifact:create", "media:artifact:read", "media:artifact:process",
            "surface:read", "context:read", "context:write"
        ));

        // Viewer role - read-only access
        delegate.registerRole("VIEWER", VIEWER_PERMISSIONS);

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
            "ADMIN", ADMIN_PERMISSIONS,
            "PLATFORM_ADMIN", ADMIN_PERMISSIONS,
            "OPERATOR", OPERATOR_PERMISSIONS,
            "EDITOR", Set.of(
                "datacloud:read", "datacloud:write",
                "media:artifact:create", "media:artifact:read", "media:artifact:process",
                "surface:read", "context:read", "context:write"
            ),
            "VIEWER", VIEWER_PERMISSIONS,
            "AUDITOR", Set.of(
                "datacloud:read", "datacloud:audit", "governance:read", "governance:compliance:read"
            ),
            "PROCESSOR", Set.of(
                "media:artifact:process", "action:pipeline:execute", "connector:sync"
            )
        );
    }
}
