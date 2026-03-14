package com.ghatana.platform.core.feature;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Service for managing feature flags at runtime.
 * 
 * Features can be enabled/disabled via:
 * - Environment variables: FEATURE_AEP_ADVANCED_PATTERNS=true
 * - System properties: -Dfeature.aep.advanced.patterns=true
 * - Programmatic configuration
 * 
 * Thread-safe and designed for high-performance lookups.
 *
 * @doc.type class
 * @doc.purpose Runtime feature flag management service
 * @doc.layer core
 * @doc.pattern Service
 */
public final class FeatureService {
    
    private static final Logger log = LoggerFactory.getLogger(FeatureService.class);
    
    private static final String ENV_PREFIX = "FEATURE_";
    private static final String PROP_PREFIX = "feature.";
    
    private final Map<Feature, Boolean> featureStates;
    private final Map<Feature, Boolean> defaultStates;
    
    private FeatureService(Map<Feature, Boolean> defaultStates) {
        this.defaultStates = Map.copyOf(defaultStates);
        this.featureStates = new ConcurrentHashMap<>();
        loadFeatureStates();
    }
    
    /**
     * Check if a feature is enabled.
     */
    public boolean isEnabled(@NotNull Feature feature) {
        return featureStates.getOrDefault(feature, defaultStates.getOrDefault(feature, false));
    }
    
    /**
     * Check if a feature is disabled.
     */
    public boolean isDisabled(@NotNull Feature feature) {
        return !isEnabled(feature);
    }
    
    /**
     * Execute action only if feature is enabled.
     */
    public void ifEnabled(@NotNull Feature feature, @NotNull Runnable action) {
        if (isEnabled(feature)) {
            action.run();
        }
    }
    
    /**
     * Get value from supplier only if feature is enabled, otherwise return default.
     */
    public <T> T getIfEnabled(@NotNull Feature feature, @NotNull Supplier<T> supplier, T defaultValue) {
        return isEnabled(feature) ? supplier.get() : defaultValue;
    }
    
    /**
     * Get all enabled features.
     */
    public Set<Feature> getEnabledFeatures() {
        return Arrays.stream(Feature.values())
                .filter(this::isEnabled)
                .collect(Collectors.toUnmodifiableSet());
    }
    
    /**
     * Get all disabled features.
     */
    public Set<Feature> getDisabledFeatures() {
        return Arrays.stream(Feature.values())
                .filter(this::isDisabled)
                .collect(Collectors.toUnmodifiableSet());
    }
    
    /**
     * Programmatically enable a feature (for testing or dynamic configuration).
     */
    public void enable(@NotNull Feature feature) {
        featureStates.put(feature, true);
        log.info("Feature enabled: {}", feature);
    }
    
    /**
     * Programmatically disable a feature.
     */
    public void disable(@NotNull Feature feature) {
        featureStates.put(feature, false);
        log.info("Feature disabled: {}", feature);
    }
    
    /**
     * Reset a feature to its default state.
     */
    public void reset(@NotNull Feature feature) {
        featureStates.remove(feature);
        log.info("Feature reset to default: {} -> {}", feature, defaultStates.getOrDefault(feature, false));
    }
    
    /**
     * Reload feature states from environment and system properties.
     */
    public void reload() {
        featureStates.clear();
        loadFeatureStates();
        log.info("Feature states reloaded");
    }
    
    private void loadFeatureStates() {
        for (Feature feature : Feature.values()) {
            // Check environment variable first
            String envName = ENV_PREFIX + feature.name();
            String envValue = System.getenv(envName);
            if (envValue != null) {
                featureStates.put(feature, Boolean.parseBoolean(envValue));
                continue;
            }
            
            // Check system property
            String propName = PROP_PREFIX + feature.name().toLowerCase().replace('_', '.');
            String propValue = System.getProperty(propName);
            if (propValue != null) {
                featureStates.put(feature, Boolean.parseBoolean(propValue));
            }
        }
        
        log.info("Loaded {} feature overrides from environment/properties", featureStates.size());
        log.debug("Enabled features: {}", getEnabledFeatures());
    }
    
    // ==========================================================================
    // Builder
    // ==========================================================================
    
    /**
     * Create a new FeatureService builder.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Create a FeatureService with sensible defaults.
     */
    public static FeatureService withDefaults() {
        return builder()
                // Platform defaults only
                .enableByDefault(Feature.PLATFORM_ADVANCED_OBSERVABILITY)
                .enableByDefault(Feature.SECURITY_GATEWAY_OAUTH)
                .enableByDefault(Feature.SECURITY_GATEWAY_RBAC)
                
                .build();
    }
    
    /**
     * Builder for FeatureService.
     */
    public static final class Builder {
        private final Map<Feature, Boolean> defaults = new EnumMap<>(Feature.class);
        
        private Builder() {}
        
        /**
         * Enable a feature by default.
         */
        public Builder enableByDefault(@NotNull Feature feature) {
            defaults.put(feature, true);
            return this;
        }
        
        /**
         * Disable a feature by default.
         */
        public Builder disableByDefault(@NotNull Feature feature) {
            defaults.put(feature, false);
            return this;
        }
        
        /**
         * Enable multiple features by default.
         */
        public Builder enableByDefault(@NotNull Feature... features) {
            for (Feature feature : features) {
                defaults.put(feature, true);
            }
            return this;
        }
        
        /**
         * Build the FeatureService.
         */
        public FeatureService build() {
            return new FeatureService(defaults);
        }
    }
}
