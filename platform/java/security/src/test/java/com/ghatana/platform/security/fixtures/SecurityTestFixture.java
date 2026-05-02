/*
 * Copyright (c) 2026 Ghatana Inc. 
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
    private SecurityTestFixture() {} 

    /**
     * Creates a SecurityContext with sensible defaults.
     * Default: user-123, tenant-456, roles [USER], permissions [read]
     */
    public static SecurityContextBuilder securityContext() { 
        return new SecurityContextBuilder(); 
    }

    /**
     * Fluent builder for SecurityContext objects.
     */
    public static class SecurityContextBuilder {
        private String userId = "user-123";
        private String tenantId = "tenant-456";
        private Set<String> roles = new HashSet<>(Set.of("USER"));
        private Set<String> permissions = new HashSet<>(Set.of("read"));

        public SecurityContextBuilder userId(String userId) { 
            this.userId = userId;
            return this;
        }

        public SecurityContextBuilder tenantId(String tenantId) { 
            this.tenantId = tenantId;
            return this;
        }

        public SecurityContextBuilder roles(String... roles) { 
            this.roles = new HashSet<>(Set.of(roles)); 
            return this;
        }

        public SecurityContextBuilder permissions(String... permissions) { 
            this.permissions = new HashSet<>(Set.of(permissions)); 
            return this;
        }

        public SecurityContextBuilder admin() { 
            this.roles = new HashSet<>(Set.of("ADMIN"));
            this.permissions = new HashSet<>(Set.of("read", "write", "delete", "admin")); 
            return this;
        }

        public SecurityContextBuilder viewer() { 
            this.roles = new HashSet<>(Set.of("VIEWER"));
            this.permissions = new HashSet<>(Set.of("read"));
            return this;
        }

        public SecurityContext build() { 
            return new SecurityContext.Default(userId, tenantId, roles, permissions); 
        }
    }
}
