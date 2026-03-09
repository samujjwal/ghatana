/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.security.config;

import java.util.Map;

/**
 * Configuration interface for security settings.
 * 
 * <p>Implementations provide API key mappings and default role configuration.
 * This is the canonical location for security configuration — replacing
 * the deprecated {@code com.ghatana.core.config.SecurityConfig}.</p>
 *
 * @see com.ghatana.platform.security.rbac
 * @see com.ghatana.platform.security.auth
 *
 * @doc.type interface
 * @doc.purpose Security configuration abstraction
 * @doc.layer platform
 * @doc.pattern Configuration
 */
public interface SecurityConfig {

    /**
     * Returns the mapping of API keys to tenant identifiers.
     *
     * @return Map of API key to tenant ID, never null
     */
    Map<String, String> apiKeys();

    /**
     * Returns the default role assigned to authenticated principals.
     *
     * @return The default role name, never null
     */
    String defaultRole();

    /**
     * Returns whether API key authentication is enabled.
     *
     * @return true if API key auth is enabled, false otherwise
     */
    default boolean isApiKeyAuthEnabled() {
        return !apiKeys().isEmpty();
    }
}
