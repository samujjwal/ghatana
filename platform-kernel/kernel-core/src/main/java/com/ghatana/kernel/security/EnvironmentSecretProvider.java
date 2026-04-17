/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;

/**
 * {@link SecretProvider} implementation that reads secrets from environment variables.
 *
 * <p>This is the default implementation used for local development and
 * container-based deployments where secrets are injected as environment variables.
 * For production vault-backed deployments a vault-specific implementation should
 * be registered in the kernel context instead.
 *
 * <p>Secret names are resolved as-is; no case normalization is applied so that
 * callers retain full control over naming (e.g. {@code DB_PASSWORD} vs
 * {@code db.password}).
 *
 * @doc.type class
 * @doc.purpose Environment-variable-backed SecretProvider for local and container deployments
 * @doc.layer core
 * @doc.pattern Strategy
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public final class EnvironmentSecretProvider implements SecretProvider {

    private static final Logger LOG = LoggerFactory.getLogger(EnvironmentSecretProvider.class);

    /** Singleton instance backed by {@code System.getenv()}. */
    public static final EnvironmentSecretProvider INSTANCE = new EnvironmentSecretProvider();

    private EnvironmentSecretProvider() {
        // Use INSTANCE or override in tests
    }

    /**
     * Resolves the secret from {@code System.getenv(name)}.
     *
     * @param name the environment variable name; must not be null
     * @return the value if set, or empty
     */
    @Override
    public Optional<String> get(String name) {
        Objects.requireNonNull(name, "name cannot be null");
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            LOG.debug("Secret '{}' is not set in the environment", name);
            return Optional.empty();
        }
        return Optional.of(value);
    }

    @Override
    public String toString() {
        return "EnvironmentSecretProvider";
    }
}
