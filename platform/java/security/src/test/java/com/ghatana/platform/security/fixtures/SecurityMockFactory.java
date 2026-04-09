/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.security.fixtures;

import com.ghatana.platform.security.SecurityContext;
import org.mockito.ArgumentMatcher;

/**
 * Mock factory for security service testing.
 *
 * Provides pre-configured mock objects and argument matchers for
 * security domain services, reducing boilerplate in tests.
 *
 * @doc.type class
 * @doc.purpose Factory for creating security test mocks and matchers
 * @doc.layer platform
 * @doc.pattern MockFactory
 */
public final class SecurityMockFactory {
    private SecurityMockFactory() {}


    /**
     * Creates an authenticated SecurityContext with admin permissions.
     */
    public static SecurityContext adminContext() {
        return SecurityTestFixture.securityContext()
            .userId("admin-1")
            .roles("ADMIN")
            .permissions("read", "write", "delete", "admin")
            .build();
    }

    /**
     * Creates an authenticated SecurityContext with standard user permissions.
     */
    public static SecurityContext userContext() {
        return SecurityTestFixture.securityContext()
            .userId("user-1")
            .roles("USER")
            .permissions("read")
            .build();
    }

    /**
     * Creates an authenticated SecurityContext with viewer-only permissions.
     */
    public static SecurityContext viewerContext() {
        return SecurityTestFixture.securityContext()
            .viewer()
            .build();
    }

    /**
     * Creates an unauthenticated SecurityContext.
     */
    public static SecurityContext unauthenticatedContext() {
        return new SecurityContext.Default(null, null, java.util.Set.of(), java.util.Set.of());
    }

    /**
     * Argument matcher for SecurityContext with specific userId.
     */
    public static ArgumentMatcher<SecurityContext> contextWithUserId(String userId) {
        return ctx -> ctx != null && ctx.getUserId().map(u -> u.equals(userId)).orElse(false);
    }

    /**
     * Argument matcher for SecurityContext with specific role.
     */
    public static ArgumentMatcher<SecurityContext> contextWithRole(String roleName) {
        return ctx -> ctx != null && ctx.getRoles().contains(roleName);
    }

    /**
     * Argument matcher for SecurityContext with specific permission.
     */
    public static ArgumentMatcher<SecurityContext> contextWithPermission(String permission) {
        return ctx -> ctx != null && ctx.getPermissions().contains(permission);
    }
}
