package com.ghatana.digitalmarketing.application;

/**
 * Canonical DMOS feature flag key constants (P2-3: Manifest-based).
 *
 * <p>This class defines the canonical feature flag keys for DMOS. All flags are
 * defined in {@code FEATURE_FLAGS_MANIFEST.json} and should be synchronized with
 * that single source of truth. Do not add new flags here without updating the manifest.</p>
 *
 * <p>Runtime flag values are resolved from environment variables with production-safe
 * defaults (incomplete features default to {@code false} in production).</p>
 *
 * @doc.type class
 * @doc.purpose Canonical DMOS feature flag key constants from manifest
 * @doc.layer product
 * @doc.pattern Config
 */
public final class DmosFeatureFlags {

    /**
     * Enables AI-powered generation features: strategy, ad-copy, SOW generation.
     * Env-var fallback: {@code DMOS_AI_ENABLED} (default {@code false} in production).
     */
    public static final String AI_ENABLED = "dmos.ai.enabled";

    /**
     * Enables the Google Ads connector runtime.
     * Env-var fallback: {@code DMOS_GOOGLE_ADS_CONNECTOR_ENABLED} (default {@code false} in production).
     */
    public static final String GOOGLE_ADS_CONNECTOR_ENABLED = "dmos.google_ads_connector.enabled";

    /**
     * Enables kill-switch enforcement. Must always be {@code true} in production.
     * Env-var fallback: {@code DMOS_KILL_SWITCH_ENABLED} (default {@code true}).
     */
    public static final String KILL_SWITCH_ENABLED = "dmos.kill_switch.enabled";

    /**
     * Enables the rollback and compensating-action workflow.
     * Env-var fallback: {@code DMOS_ROLLBACK_WORKFLOW_ENABLED} (default {@code true}).
     */
    public static final String ROLLBACK_WORKFLOW_ENABLED = "dmos.rollback_workflow.enabled";

    /**
     * Enables dashboard growth metrics widget.
     * Env-var fallback: {@code DMOS_DASHBOARD_GROWTH_METRICS} (default {@code false}).
     */
    public static final String DASHBOARD_GROWTH_METRICS = "dmos.dashboard_growth_metrics";

    /**
     * Enables the observability baseline (metrics + structured logging).
     * Env-var fallback: {@code DMOS_OBSERVABILITY_ENABLED} (default {@code true}).
     */
    public static final String OBSERVABILITY_ENABLED = "dmos.observability.enabled";

    private DmosFeatureFlags() {
        // Utility class - prevent instantiation
    }
}
