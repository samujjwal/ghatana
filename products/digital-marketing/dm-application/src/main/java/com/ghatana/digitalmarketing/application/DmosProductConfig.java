package com.ghatana.digitalmarketing.application;

import java.time.Duration;

/**
 * Product-level configuration constants and feature flag defaults for DMOS.
 * Values are read from environment variables at startup with safe defaults.
 * Override at deployment time via env vars (e.g., {@code DMOS_AI_ENABLED=false}).
 *
 * @doc.type class
 * @doc.purpose DMOS product configuration and feature flag baseline for R0-008
 * @doc.layer product
 * @doc.pattern Config
 */
public final class DmosProductConfig {

    // ---- Feature Flags ----

    /**
     * Whether AI-powered generation features (strategy, ad-copy, SOW, etc.) are enabled.
     * Default: {@code false} in production for incomplete features.
     * Set {@code DMOS_AI_ENABLED=true} to explicitly enable in a region.
     */
    public static final boolean AI_FEATURES_ENABLED =
        boolEnv("DMOS_AI_ENABLED", isProduction() ? false : true);

    /**
     * Whether the Google Ads connector runtime is enabled.
     * Default: {@code false} in production until workflow wiring is complete.
     * Set {@code DMOS_GOOGLE_ADS_CONNECTOR_ENABLED=true} to explicitly enable.
     */
    public static final boolean GOOGLE_ADS_CONNECTOR_ENABLED =
        boolEnv("DMOS_GOOGLE_ADS_CONNECTOR_ENABLED", isProduction() ? false : true);

    /**
     * Whether kill-switch enforcement is active.
     * Default: {@code true}. Must always be {@code true} in production.
     */
    public static final boolean KILL_SWITCH_ENABLED =
        boolEnv("DMOS_KILL_SWITCH_ENABLED", true);

    /**
     * Whether the rollback and compensating-action workflow is active.
     * Default: {@code true}.
     */
    public static final boolean ROLLBACK_ENABLED =
        boolEnv("DMOS_ROLLBACK_ENABLED", true);

    /**
     * Whether the observability baseline (metrics + structured logging) is enabled.
     * Default: {@code true}.
     */
    public static final boolean OBSERVABILITY_ENABLED =
        boolEnv("DMOS_OBSERVABILITY_ENABLED", true);

    // ---- Runtime Limits ----

    /**
     * Maximum intake questionnaire fields per submission.
     * Default: 50. Override with {@code DMOS_MAX_INTAKE_FIELDS}.
     */
    public static final int MAX_INTAKE_FIELDS =
        intEnv("DMOS_MAX_INTAKE_FIELDS", 50);

    /**
     * Maximum number of ad copy variants generated per request.
     * Default: 5. Override with {@code DMOS_MAX_AD_COPY_VARIANTS}.
     */
    public static final int MAX_AD_COPY_VARIANTS =
        intEnv("DMOS_MAX_AD_COPY_VARIANTS", 5);

    /**
     * Connector OAuth token refresh window before expiry.
     * Default: 5 minutes.
     */
    public static final Duration CONNECTOR_TOKEN_REFRESH_WINDOW =
        Duration.ofMinutes(intEnv("DMOS_CONNECTOR_TOKEN_REFRESH_MINUTES", 5));

    /**
     * Durable workflow step timeout.
     * Default: 30 seconds.
     */
    public static final Duration WORKFLOW_STEP_TIMEOUT =
        Duration.ofSeconds(intEnv("DMOS_WORKFLOW_STEP_TIMEOUT_SECONDS", 30));

    /**
     * Maximum budget recommendation cap (in currency units).
     * Default: 100,000.
     */
    public static final int MAX_BUDGET_CAP =
        intEnv("DMOS_MAX_BUDGET_CAP", 100_000);

    // ---- Environment Identifiers ----

    /**
     * Deployment environment name (e.g., "production", "staging", "development").
     * Set via {@code DMOS_ENV}.
     */
    public static final String ENVIRONMENT =
        stringEnv("DMOS_ENV", "development");

    /**
     * Product version string, injected at build time.
     * Set via {@code DMOS_VERSION}.
     */
    public static final String VERSION =
        stringEnv("DMOS_VERSION", "local");

    // ---- private helpers ----

    private DmosProductConfig() {
        // constants-only class
    }

    private static boolean boolEnv(String key, boolean defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value.trim());
    }

    private static int intEnv(String key, int defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static String stringEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value.trim() : defaultValue;
    }

    private static boolean isProduction() {
        String env = stringEnv("DMOS_ENV", "development");
        return "production".equalsIgnoreCase(env);
    }
}
