/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    private SecurityMockFactory() {} // GH-90000


    /**
     * Creates an authenticated SecurityContext with admin permissions.
     */
    public static SecurityContext adminContext() { // GH-90000
        return SecurityTestFixture.securityContext() // GH-90000
            .userId("admin-1 [GH-90000]")
            .roles("ADMIN [GH-90000]")
            .permissions("read", "write", "delete", "admin") // GH-90000
            .build(); // GH-90000
    }

    /**
     * Creates an authenticated SecurityContext with standard user permissions.
     */
    public static SecurityContext userContext() { // GH-90000
        return SecurityTestFixture.securityContext() // GH-90000
            .userId("user-1 [GH-90000]")
            .roles("USER [GH-90000]")
            .permissions("read [GH-90000]")
            .build(); // GH-90000
    }

    /**
     * Creates an authenticated SecurityContext with viewer-only permissions.
     */
    public static SecurityContext viewerContext() { // GH-90000
        return SecurityTestFixture.securityContext() // GH-90000
            .viewer() // GH-90000
            .build(); // GH-90000
    }

    /**
     * Creates an unauthenticated SecurityContext.
     */
    public static SecurityContext unauthenticatedContext() { // GH-90000
        return new SecurityContext.Default(null, null, java.util.Set.of(), java.util.Set.of()); // GH-90000
    }

    /**
     * Argument matcher for SecurityContext with specific userId.
     */
    public static ArgumentMatcher<SecurityContext> contextWithUserId(String userId) { // GH-90000
        return ctx -> ctx != null && ctx.getUserId().map(u -> u.equals(userId)).orElse(false); // GH-90000
    }

    /**
     * Argument matcher for SecurityContext with specific role.
     */
    public static ArgumentMatcher<SecurityContext> contextWithRole(String roleName) { // GH-90000
        return ctx -> ctx != null && ctx.getRoles().contains(roleName); // GH-90000
    }

    /**
     * Argument matcher for SecurityContext with specific permission.
     */
    public static ArgumentMatcher<SecurityContext> contextWithPermission(String permission) { // GH-90000
        return ctx -> ctx != null && ctx.getPermissions().contains(permission); // GH-90000
    }
}
