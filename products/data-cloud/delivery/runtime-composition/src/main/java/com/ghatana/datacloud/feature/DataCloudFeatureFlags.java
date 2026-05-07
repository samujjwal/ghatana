package com.ghatana.datacloud.feature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Feature flag evaluation utility for Data-Cloud.
 *
 * <p>Each {@link DataCloudFeature} maps to an environment variable
 * {@code DC_FEATURE_<NAME>} (e.g. {@code DC_FEATURE_DATA_CLOUD_AI_ASSIST=true}).
 * When the variable is absent the constant's {@link DataCloudFeature#defaultEnabled()} value
 * is used.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 *   // Application bootstrap
 *   DataCloudFeatureFlags flags = DataCloudFeatureFlags.fromEnvironment();
 *   DataCloudFeatureFlags.setGlobal(flags);
 *
 *   // Guard a capability at call site
 *   if (DataCloudFeatureFlags.isEnabled(DataCloudFeature.DATA_CLOUD_AI_ASSIST)) {
 *       aiAssistHandler.handle(request);
 *   }
 *
 *   // Override in tests
 *   DataCloudFeatureFlags.override(DataCloudFeature.DATA_CLOUD_AI_ASSIST, true);
 *   // ... test ...
 *   DataCloudFeatureFlags.clearOverrides();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Centralised, observable, testable feature flag evaluation for Data-Cloud
 * @doc.layer product
 * @doc.pattern Configuration
 */
public final class DataCloudFeatureFlags {

    private static final Logger log = LoggerFactory.getLogger(DataCloudFeatureFlags.class);

    /** Environment-variable prefix shared by all data-cloud feature flags. */
    public static final String ENV_PREFIX = "DC_FEATURE_";

    // Volatile singleton set by application bootstrap or DI
    private static volatile DataCloudFeatureFlags INSTANCE;

    // Test overrides take precedence over everything else
    private static final Map<DataCloudFeature, Boolean> OVERRIDES = new ConcurrentHashMap<>();

    // Resolved flag values (immutable after construction)
    private final Map<DataCloudFeature, Boolean> config;

    private DataCloudFeatureFlags(Map<DataCloudFeature, Boolean> config) {
        this.config = Map.copyOf(config);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Factory
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Constructs a {@code DataCloudFeatureFlags} instance by reading
     * {@code DC_FEATURE_<NAME>} environment variables.
     *
     * <p>Absent variables fall back to each feature's
     * {@link DataCloudFeature#defaultEnabled()} value.
     *
     * @return configured feature flags instance
     */
    public static DataCloudFeatureFlags fromEnvironment() {
        Map<DataCloudFeature, Boolean> resolved = new EnumMap<>(DataCloudFeature.class);
        for (DataCloudFeature feature : DataCloudFeature.values()) {
            String envVar = ENV_PREFIX + feature.name();
            String raw = System.getenv(envVar);
            boolean value = (raw != null) ? Boolean.parseBoolean(raw) : feature.defaultEnabled();
            resolved.put(feature, value);
            log.info("[feature-flag] {} = {} (source: {})",
                    feature, value, raw != null ? "env:" + envVar : "default");
        }
        return new DataCloudFeatureFlags(resolved);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Global instance management
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Sets the global singleton used by {@link #isEnabled}.
     * Typically called once during application startup.
     *
     * @param instance the initialized flags instance
     */
    public static void setGlobal(DataCloudFeatureFlags instance) {
        INSTANCE = instance;
        log.info("[feature-flag] global instance registered with {} flags", instance.config.size());
    }

    /**
     * Returns the current global singleton, or {@code null} if not yet initialised.
     *
     * @return current global instance
     */
    public static DataCloudFeatureFlags global() {
        return INSTANCE;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Evaluation
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if {@code feature} is enabled.
     *
     * <p>Resolution order (highest to lowest precedence):
     * <ol>
     *   <li>Test override set via {@link #override}</li>
     *   <li>Global instance loaded from environment</li>
     *   <li>{@link DataCloudFeature#defaultEnabled()} safe default</li>
     * </ol>
     *
     * @param feature the feature to evaluate
     * @return {@code true} when enabled
     */
    public static boolean isEnabled(DataCloudFeature feature) {
        // 1. Test override
        Boolean override = OVERRIDES.get(feature);
        if (override != null) {
            log.debug("[feature-flag] {} = {} (test-override)", feature, override);
            return override;
        }
        // 2. Global instance
        DataCloudFeatureFlags instance = INSTANCE;
        if (instance != null) {
            boolean value = instance.config.getOrDefault(feature, feature.defaultEnabled());
            log.debug("[feature-flag] {} = {}", feature, value);
            return value;
        }
        // 3. Safe default (startup race)
        boolean fallback = feature.defaultEnabled();
        log.warn("[feature-flag] global instance not yet set; returning default {} for {}", fallback, feature);
        return fallback;
    }

    /**
     * Instance-level evaluation without the global singleton — useful for
     * injected usage patterns.
     *
     * @param feature the feature to evaluate
     * @return {@code true} when enabled
     */
    public boolean enabled(DataCloudFeature feature) {
        return config.getOrDefault(feature, feature.defaultEnabled());
    }

    // ────────────────────────────────────────────────────────────────────────
    // Test helpers
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Overrides a single feature flag for the duration of a test.
     * Call {@link #clearOverrides} in an {@code @AfterEach} to keep tests isolated.
     *
     * @param feature the feature to override
     * @param enabled the overridden value
     */
    public static void override(DataCloudFeature feature, boolean enabled) {
        OVERRIDES.put(feature, enabled);
    }

    /**
     * Clears all test overrides registered via {@link #override}.
     */
    public static void clearOverrides() {
        OVERRIDES.clear();
    }

    /**
     * Resets the global singleton to {@code null}.
     * Intended for test isolation only.
     */
    public static void resetGlobalForTesting() {
        INSTANCE = null;
    }
}
