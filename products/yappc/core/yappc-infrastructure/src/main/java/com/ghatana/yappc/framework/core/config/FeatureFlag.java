package com.ghatana.yappc.framework.core.config;

/**
 * Enumeration of feature flags used across YAPPC modules.
 *
 * <p>Each flag maps to a configuration key that can be toggled
 * in the application config without code changes.
 *
 * @doc.type enum
 * @doc.purpose Feature flag constants for conditional behavior
 * @doc.layer infrastructure
 * @doc.pattern Configuration
 */
public enum FeatureFlag {

    AEP_INTEGRATION("feature.aep.integration"),
    PATTERN_LEARNING("feature.pattern.learning"),
    AI_REQUIREMENT_EXTRACTION("feature.ai.requirement.extraction"),
    AI_CODE_REVIEW("feature.ai.code.review"),
    AI_CANVAS_GENERATION("feature.ai.canvas.generation");

    private final String key;

    FeatureFlag(String key) {
        this.key = key;
    }

    /**
     * Get the configuration key for this feature flag.
     *
     * @return the configuration key
     */
    public String key() {
        return key;
    }
}
