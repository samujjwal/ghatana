/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.security;

import java.util.Optional;

/**
 * SPI for resolving named secrets within the kernel platform.
 *
 * <p>Products retrieve credentials, API keys, and other sensitive values
 * via this interface instead of calling {@code System.getenv()} directly.
 * This eliminates per-product secret-reading drift and allows the underlying
 * implementation to be swapped (e.g. from environment variables to a vault
 * or secret manager) without touching product code.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * SecretProvider secrets = context.getDependency(SecretProvider.class);
 *
 * // Required secret — throws if absent
 * String dbPassword = secrets.require("DB_PASSWORD");
 *
 * // Optional secret — fallback to a default
 * String apiKey = secrets.get("EXTERNAL_API_KEY").orElse("demo-key");
 *
 * // Versioned secret (for rotation support)
 * String tlsCert = secrets.get("TLS_CERT", "v2").orElseThrow();
 * }</pre>
 *
 * <h2>Implementations</h2>
 * <ul>
 *   <li>{@link EnvironmentSecretProvider} — reads from {@code System.getenv()},
 *       suitable for local development and container deployments.</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Kernel SPI for named-secret resolution — avoids per-product env-var drift
 * @doc.layer core
 * @doc.pattern SPI, Strategy
 * @author Ghatana Kernel Team
 * @since 1.1.0
 */
public interface SecretProvider {

    /**
     * Resolves a named secret, returning an empty {@link Optional} when the
     * secret is not configured.
     *
     * @param name the secret name; must not be null
     * @return the secret value if present, or empty
     */
    Optional<String> get(String name);

    /**
     * Resolves a specific version of a named secret.  Implementations that do
     * not support versioning may ignore the {@code version} parameter and fall
     * back to {@link #get(String)}.
     *
     * @param name    the secret name; must not be null
     * @param version the requested version label (e.g. {@code "v2"}, {@code "latest"})
     * @return the secret value if present, or empty
     */
    default Optional<String> get(String name, String version) {
        return get(name);
    }

    /**
     * Resolves a named secret, throwing {@link MissingSecretException} if absent.
     *
     * @param name the secret name; must not be null
     * @return the secret value; never null
     * @throws MissingSecretException if the secret is not configured
     */
    default String require(String name) {
        return get(name).orElseThrow(() -> new MissingSecretException(name));
    }

    // ─── Exception ────────────────────────────────────────────────────────────

    /**
     * Thrown when a required secret is not present.
     */
    final class MissingSecretException extends RuntimeException {

        private final String secretName;

        /**
         * Creates a new {@code MissingSecretException}.
         *
         * @param secretName the name of the missing secret
         */
        public MissingSecretException(String secretName) {
            super("Required secret '" + secretName + "' is not configured. "
                    + "Ensure the environment variable or vault entry is set.");
            this.secretName = secretName;
        }

        /**
         * Returns the name of the missing secret.
         *
         * @return secret name; never null
         */
        public String getSecretName() {
            return secretName;
        }
    }
}
