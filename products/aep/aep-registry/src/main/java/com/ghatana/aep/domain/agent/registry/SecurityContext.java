/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.domain.agent.registry;

import java.util.Set;

/**
 * Security context for agent execution.
 *
 * <p><b>DEPRECATED</b> - Use {@link com.ghatana.platform.security.SecurityContext} instead.
 * This interface is deprecated and will be removed in a future release.
 *
 * @doc.type interface
 * @doc.purpose Security context contract for agent execution (DEPRECATED)
 * @doc.layer product
 * @doc.pattern ValueObject
 * @deprecated Use {@link com.ghatana.platform.security.SecurityContext} instead
 */
@Deprecated(since = "1.0", forRemoval = true)
public interface SecurityContext {

    /**
     * Get the authenticated principal.
     */
    String getPrincipal();

    /**
     * Get the assigned roles.
     */
    Set<String> roles();

    /**
     * Get the assigned permissions.
     */
    Set<String> permissions();

    /**
     * Get the authentication token.
     */
    String token();

    /**
     * Check if the context is authenticated.
     */
    boolean authenticated();
}
