/*
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API Module — Development Configuration
 */
package com.ghatana.yappc.api.config;

import com.ghatana.platform.security.rbac.InMemoryRolePermissionRegistry;
import java.util.Set;

/**
 * Development-environment configuration helpers.
 *
 * <p>Provides sensible defaults for local development and testing. Do NOT use in
 * production — these defaults disable security constraints for convenience.
 *
 * @doc.type class
 * @doc.purpose Development-time configuration utilities (RBAC defaults, etc.)
 * @doc.layer product
 * @doc.pattern Factory
 */
public final class DevelopmentModule {

    private DevelopmentModule() {}

    /**
     * Creates an {@link InMemoryRolePermissionRegistry} pre-populated with the
     * standard YAPPC roles and their default permissions for development / testing.
     *
     * @return a fully configured registry suitable for non-production use
     */
    public static InMemoryRolePermissionRegistry createDefaultRegistry() {
        InMemoryRolePermissionRegistry registry = new InMemoryRolePermissionRegistry();

        // Admin — full access
        registry.registerRole("admin", Set.of(
                "requirements:read", "requirements:write", "requirements:delete",
                "workspace:read", "workspace:write", "workspace:delete",
                "users:read", "users:write", "users:delete"
        ));

        // Developer — read/write but no delete on users
        registry.registerRole("developer", Set.of(
                "requirements:read", "requirements:write",
                "workspace:read", "workspace:write",
                "users:read"
        ));

        // Viewer — read-only
        registry.registerRole("viewer", Set.of(
                "requirements:read",
                "workspace:read",
                "users:read"
        ));

        return registry;
    }
}
