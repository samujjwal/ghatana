package com.ghatana.digitalmarketing.application;

/**
 * Canonical runtime feature flag keys for DMOS used with
 * {@link com.ghatana.digitalmarketing.bridge.DigitalMarketingKernelAdapter#isFeatureEnabled}.
 *
 * <p>These constants replace the static JVM-evaluated booleans in {@link DmosProductConfig}
 * for flags that must be toggleable at runtime without a service restart (KE-05).
 * {@code DmosProductConfig} retains the env-var-based defaults which serve as the
 * fallback when the platform {@code FeatureFlagPlugin} is not yet wired.</p>
 *
 * <p>Naming convention: {@code dmos.<feature_area>.<flag_name>} using lowercase
 * snake-case matching the env-var suffix (e.g. {@code DMOS_AI_ENABLED →
 * dmos.ai.enabled}).</p>
 *
 * @doc.type class
 * @doc.purpose Canonical DMOS feature flag key constants for KE-05 runtime toggling
 * @doc.layer product
 * @doc.pattern Config
 */
public final class DmosFeatureFlags {

    /**
     * Enables AI-powered generation features: strategy, ad-copy, SOW generation.
     * Env-var fallback: {@code DMOS_AI_ENABLED} (default {@code true}).
     */
    public static final String AI_ENABLED = "dmos.ai.enabled";

    /**
     * Enables the Google Ads connector runtime.
     * Env-var fallback: {@code DMOS_GOOGLE_ADS_CONNECTOR_ENABLED} (default {@code true}).
     */
    public static final String GOOGLE_ADS_CONNECTOR_ENABLED = "dmos.google_ads_connector.enabled";

    /**
     * Enables kill-switch enforcement. Must always be {@code true} in production.
     * Env-var fallback: {@code DMOS_KILL_SWITCH_ENABLED} (default {@code true}).
     */
    public static final String KILL_SWITCH_ENABLED = "dmos.kill_switch.enabled";

    /**
     * Enables the rollback and compensating-action workflow.
     * Env-var fallback: {@code DMOS_ROLLBACK_ENABLED} (default {@code true}).
     */
    public static final String ROLLBACK_ENABLED = "dmos.rollback.enabled";

    /**
     * Enables the observability baseline (metrics + structured logging).
     * Env-var fallback: {@code DMOS_OBSERVABILITY_ENABLED} (default {@code true}).
     */
    public static final String OBSERVABILITY_ENABLED = "dmos.observability.enabled";

    private DmosFeatureFlags() {
        // constants-only class
    }
}
