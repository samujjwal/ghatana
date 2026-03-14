/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.appplatform.secrets.registry;

import com.ghatana.appplatform.secrets.port.SecretProvider;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that selects a {@link SecretProvider} implementation by name (K14-001).
 *
 * <p>The active provider is determined by the {@code secrets.provider} configuration
 * key (wired via K-02 ConfigEngine). Available built-in names:
 * <ul>
 *   <li>{@code local} — {@code LocalFileSecretProvider} (default)</li>
 *   <li>{@code vault} — HashiCorp Vault KV v2 (K14-002, future)</li>
 * </ul>
 *
 * <p>Products can register custom providers via {@link #register(String, SecretProvider)}.
 *
 * @doc.type class
 * @doc.purpose Registry for pluggable secret provider implementations (K14-001)
 * @doc.layer core
 * @doc.pattern Service
 */
public final class SecretProviderRegistry {

    private static final String DEFAULT_PROVIDER = "local";

    private final Map<String, SecretProvider> providers = new ConcurrentHashMap<>();
    private volatile String activeProvider = DEFAULT_PROVIDER;

    /**
     * Registers a provider under a given name.
     *
     * @param name     provider name (e.g., "local", "vault")
     * @param provider provider implementation
     */
    public void register(String name, SecretProvider provider) {
        providers.put(name, provider);
    }

    /**
     * Sets the name of the active provider.
     *
     * @param name provider name (must have been registered prior)
     * @throws IllegalArgumentException if no provider exists with this name
     */
    public void setActiveProvider(String name) {
        if (!providers.containsKey(name)) {
            throw new IllegalArgumentException("No provider registered with name: " + name);
        }
        this.activeProvider = name;
    }

    /**
     * Returns the currently active {@link SecretProvider}.
     *
     * @throws IllegalStateException if no providers are registered or the active provider
     *                               has been deregistered
     */
    public SecretProvider getActive() {
        SecretProvider provider = providers.get(activeProvider);
        if (provider == null) {
            throw new IllegalStateException(
                    "No provider registered for active key: " + activeProvider);
        }
        return provider;
    }

    /**
     * Returns a specific provider by name without changing the active provider.
     *
     * @param name provider name
     * @throws IllegalArgumentException if no provider exists with this name
     */
    public SecretProvider get(String name) {
        SecretProvider provider = providers.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("No provider registered: " + name);
        }
        return provider;
    }
}
