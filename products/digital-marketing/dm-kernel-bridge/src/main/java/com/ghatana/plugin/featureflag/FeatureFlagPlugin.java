package com.ghatana.plugin.featureflag;

import com.ghatana.digitalmarketing.contracts.DmOperationContext;
import io.activej.promise.Promise;

import java.util.Map;

/**
 * Feature flag plugin interface for kernel platform integration.
 *
 * <p>Provides feature flag capabilities for:
 * <ul>
 *   <li>Checking if features are enabled</li>
 *   <li>Getting feature configuration</li>
 *   <li>Listing available features</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Feature flag plugin interface
 * @doc.layer product
 * @doc.pattern Plugin, Feature Flag
 */
public interface FeatureFlagPlugin {

    /**
     * Checks if a feature flag is enabled for the given tenant.
     *
     * @param flagKey feature flag key
     * @param tenantId tenant identifier
     * @return true if enabled
     */
    Promise<Boolean> isEnabled(String flagKey, String tenantId);

    /**
     * Gets the value of a feature flag as a string.
     *
     * @param flagKey feature flag key
     * @param tenantId tenant identifier
     * @param defaultValue default value if flag not set
     * @return flag value or default
     */
    Promise<String> getString(String flagKey, String tenantId, String defaultValue);

    /**
     * Gets the value of a feature flag as an integer.
     *
     * @param flagKey feature flag key
     * @param tenantId tenant identifier
     * @param defaultValue default value if flag not set
     * @return flag value or default
     */
    Promise<Integer> getInt(String flagKey, String tenantId, int defaultValue);

    /**
     * Gets the value of a feature flag as a boolean.
     *
     * @param flagKey feature flag key
     * @param tenantId tenant identifier
     * @param defaultValue default value if flag not set
     * @return flag value or default
     */
    Promise<Boolean> getBoolean(String flagKey, String tenantId, boolean defaultValue);

    /**
     * Gets all feature flags for the tenant.
     *
     * @param tenantId tenant identifier
     * @return map of flag keys to values
     */
    Promise<Map<String, Object>> getAllFlags(String tenantId);
}
