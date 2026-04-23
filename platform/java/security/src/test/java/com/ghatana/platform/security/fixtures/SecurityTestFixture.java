/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.platform.security.fixtures;

import com.ghatana.platform.security.SecurityContext;

import java.util.HashSet;
import java.util.Set;

/**
 * Builder fixtures for security test objects.
 *
 * Provides fluent builders for creating test SecurityContext objects
 * with sensible defaults.
 *
 * @doc.type class
 * @doc.purpose Fluent builders for security test objects
 * @doc.layer platform
 * @doc.pattern Fixture
 */
public final class SecurityTestFixture {
    private SecurityTestFixture() {} // GH-90000

    /**
     * Creates a SecurityContext with sensible defaults.
     * Default: user-123, tenant-456, roles [USER], permissions [read]
     */
    public static SecurityContextBuilder securityContext() { // GH-90000
        return new SecurityContextBuilder(); // GH-90000
    }

    /**
     * Fluent builder for SecurityContext objects.
     */
    public static class SecurityContextBuilder {
        private String userId = "user-123";
        private String tenantId = "tenant-456";
        private Set<String> roles = new HashSet<>(Set.of("USER"));
        private Set<String> permissions = new HashSet<>(Set.of("read"));

        public SecurityContextBuilder userId(String userId) { // GH-90000
            this.userId = userId;
            return this;
        }

        public SecurityContextBuilder tenantId(String tenantId) { // GH-90000
            this.tenantId = tenantId;
            return this;
        }

        public SecurityContextBuilder roles(String... roles) { // GH-90000
            this.roles = new HashSet<>(Set.of(roles)); // GH-90000
            return this;
        }

        public SecurityContextBuilder permissions(String... permissions) { // GH-90000
            this.permissions = new HashSet<>(Set.of(permissions)); // GH-90000
            return this;
        }

        public SecurityContextBuilder admin() { // GH-90000
            this.roles = new HashSet<>(Set.of("ADMIN"));
            this.permissions = new HashSet<>(Set.of("read", "write", "delete", "admin")); // GH-90000
            return this;
        }

        public SecurityContextBuilder viewer() { // GH-90000
            this.roles = new HashSet<>(Set.of("VIEWER"));
            this.permissions = new HashSet<>(Set.of("read"));
            return this;
        }

        public SecurityContext build() { // GH-90000
            return new SecurityContext.Default(userId, tenantId, roles, permissions); // GH-90000
        }
    }
}
