package com.ghatana.digitalmarketing.infra;

/**
 * Profile guard to prevent unsafe in-memory adapter usage in non-development environments.
 *
 * <p>Both production and staging require real PostgreSQL adapters. In-memory (ephemeral)
 * adapters are only permitted in development and test environments where data loss is
 * acceptable and there is no real traffic.</p>
 *
 * <p>Usage: Call {@link #validate()} during application bootstrap before wiring any
 * repositories. An {@link IllegalStateException} is thrown when the environment is
 * {@code production} or {@code staging} and an ephemeral adapter would be wired.</p>
 *
 * @doc.type class
 * @doc.purpose Profile guard preventing in-memory adapter usage outside development/test
 * @doc.layer product
 * @doc.pattern Guard
 */
public final class ProductionProfileGuard {

    private static final String DMOS_ENV = "DMOS_ENV";
    private static final String PRODUCTION_ENV = "production";
    private static final String STAGING_ENV = "staging";

    private ProductionProfileGuard() {
        // Utility class - prevent instantiation
    }

    /**
     * Validates that the current environment is safe for in-memory adapter usage.
     *
     * <p>Throws if {@code DMOS_ENV} is {@code production} or {@code staging}, since both
     * environments require durable PostgreSQL adapters. Development and test environments
     * are explicitly allowed.</p>
     *
     * @throws IllegalStateException if the environment is production or staging
     */
    public static void validate() {
        String resolved = resolveEnvironment();
        String env = (resolved == null || resolved.isBlank()) ? "development" : resolved.trim().toLowerCase();

        if (PRODUCTION_ENV.equals(env)) {
            throw new IllegalStateException(
                "In-memory adapters cannot be used in production environment. " +
                "DMOS_ENV is set to 'production'. Configure PostgreSQL adapters " +
                "or set DMOS_ENV to 'development' for in-memory adapters."
            );
        }

        if (STAGING_ENV.equals(env)) {
            throw new IllegalStateException(
                "In-memory adapters cannot be used in staging environment. " +
                "DMOS_ENV is set to 'staging'. Staging requires real PostgreSQL adapters " +
                "matching the production bootstrap evidence contract. " +
                "Set DMOS_ENV to 'development' for in-memory adapters."
            );
        }
    }

    /**
     * Returns {@code true} if the current environment is production.
     *
     * @return {@code true} if production environment
     */
    public static boolean isProduction() {
        String env = resolveEnvironment();
        if (env == null || env.isBlank()) {
            return false;
        }
        return PRODUCTION_ENV.equalsIgnoreCase(env.trim());
    }

    /**
     * Returns {@code true} if the current environment is staging.
     *
     * @return {@code true} if staging environment
     */
    public static boolean isStaging() {
        String env = resolveEnvironment();
        if (env == null || env.isBlank()) {
            return false;
        }
        return STAGING_ENV.equalsIgnoreCase(env.trim());
    }

    /**
     * Returns {@code true} if the current environment allows in-memory (ephemeral) adapters.
     *
     * <p>Only development and test environments permit ephemeral adapters. Both production
     * and staging require durable PostgreSQL adapters.</p>
     *
     * @return {@code true} only for development/test environments
     */
    public static boolean isEphemeralAllowed() {
        return !isProduction() && !isStaging();
    }

    private static String resolveEnvironment() {
        String env = System.getProperty(DMOS_ENV);
        if (env == null || env.isBlank()) {
            env = System.getenv(DMOS_ENV);
        }
        return env;
    }
}
