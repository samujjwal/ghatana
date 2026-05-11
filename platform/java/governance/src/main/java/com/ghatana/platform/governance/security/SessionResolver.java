/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

package com.ghatana.platform.governance.security;

import org.jetbrains.annotations.Nullable;

/**
 * Resolves a session cookie into a Principal for browser-based authentication.
 *
 * <p>This interface enables cookie-based session authentication as an alternative
 * to API key or Bearer token authentication. Implementations should validate
 * the session cookie, check for expiration, and resolve to a Principal with
 * appropriate roles and tenant context.
 *
 * @doc.type interface
 * @doc.purpose Resolves session cookies to Principal objects for browser authentication
 * @doc.layer governance
 * @doc.pattern Resolver
 */
@FunctionalInterface
public interface SessionResolver {

    /**
     * Resolves a session cookie value to a Principal.
     *
     * @param sessionCookie the session cookie value (may be null or blank)
     * @return the resolved Principal, or null if the session is invalid/expired
     */
    @Nullable
    Principal resolve(String sessionCookie);
}
