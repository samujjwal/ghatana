package com.ghatana.digitalmarketing.infra;

/**
 * Production profile guard to prevent unsafe adapter usage in production.
 *
 * <p>This class validates that in-memory adapters are not used in production
 * environments. In-memory adapters should only be used in development and test
 * environments where data loss is acceptable.</p>
 *
 * <p>Usage: Call {@link #validate()} during application bootstrap before
 * wiring any repositories. If the environment is production and in-memory
 * adapters would be used, this throws an {@link IllegalStateException}.</p>
 *
 * @doc.type class
 * @doc.purpose Production profile validation to prevent in-memory adapter usage
 * @doc.layer product
 * @doc.pattern Guard
 */
public final class ProductionProfileGuard {

    private static final String DMOS_ENV = "DMOS_ENV";
    private static final String PRODUCTION_ENV = "production";

    private ProductionProfileGuard() {
        // Utility class - prevent instantiation
    }

    /**
     * Validates that the current environment is safe for in-memory adapter usage.
     *
     * <p>In production environments, this method throws an exception to prevent
     * accidental use of in-memory adapters which would cause data loss.</p>
     *
     * @throws IllegalStateException if in-memory adapters would be used in production
     */
    public static void validate() {
        String env = resolveEnvironment();
        if (env == null || env.isBlank()) {
            env = "development";
        }

        if (PRODUCTION_ENV.equalsIgnoreCase(env.trim())) {
            throw new IllegalStateException(
                "In-memory adapters cannot be used in production environment. " +
                "DMOS_ENV is set to 'production'. Please configure PostgreSQL adapters " +
                "or set DMOS_ENV to 'development' or 'test' for in-memory adapters."
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

    private static String resolveEnvironment() {
        String env = System.getProperty(DMOS_ENV);
        if (env == null || env.isBlank()) {
            env = System.getenv(DMOS_ENV);
        }
        return env;
    }

    /**
     * Returns {@code true} if the current environment allows in-memory adapters.
     *
     * <p>In-memory adapters are allowed in development and test environments only.</p>
     *
     * @return {@code true} if in-memory adapters are allowed
     */
    public static boolean isInMemoryAllowed() {
        return !isProduction();
    }
}
