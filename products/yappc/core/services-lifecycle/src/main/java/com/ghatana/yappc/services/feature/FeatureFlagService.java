package com.ghatana.yappc.services.feature;

import com.ghatana.yappc.framework.core.config.FeatureFlag;
import com.ghatana.yappc.framework.core.config.FeatureFlags;
import io.activej.inject.annotation.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;

/**
 * Lifecycle-facing feature flag evaluation service.
 *
 * <p>Wraps the low-level {@link FeatureFlags} singleton with a service-layer
 * boundary that is injectable, loggable, and testable within the lifecycle module.
 * All feature checks in lifecycle business logic should go through this service
 * rather than calling {@link FeatureFlags#isEnabled(FeatureFlag)} directly.
 *
 * @doc.type class
 * @doc.purpose Lifecycle service boundary for feature flag evaluation
 * @doc.layer product
 * @doc.pattern Service
 */
public final class FeatureFlagService {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagService.class);

    @Inject
    public FeatureFlagService() {}

    /**
     * Check whether the given feature flag is enabled.
     *
     * <p>Evaluation order:
     * <ol>
     *   <li>Test overrides set via {@link FeatureFlags#override} take highest precedence.</li>
     *   <li>Global {@link FeatureFlags} instance config value.</li>
     *   <li>Returns {@code false} when no global instance is configured (safe default).</li>
     * </ol>
     *
     * @param flag the feature flag to evaluate
     * @return {@code true} when the feature is enabled; {@code false} otherwise
     */
    public boolean isEnabled(FeatureFlag flag) {
        boolean enabled = FeatureFlags.isEnabled(flag);
        log.trace("Feature flag check: {} = {}", flag.key(), enabled);
        return enabled;
    }

    /**
     * Check whether the given feature flag is disabled.
     *
     * @param flag the feature flag to evaluate
     * @return {@code true} when the feature is disabled
     */
    public boolean isDisabled(FeatureFlag flag) {
        return !isEnabled(flag);
    }

    /**
     * Return a snapshot of all known feature flag states.
     *
     * <p>Useful for diagnostic endpoints and health checks that need to surface
     * the current flag configuration.
     *
     * @return immutable map of flag → current enabled state
     */
    public Map<FeatureFlag, Boolean> snapshot() {
        Map<FeatureFlag, Boolean> snapshot = new EnumMap<>(FeatureFlag.class);
        for (FeatureFlag flag : FeatureFlag.values()) {
            snapshot.put(flag, FeatureFlags.isEnabled(flag));
        }
        return Map.copyOf(snapshot);
    }
}
