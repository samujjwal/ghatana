/**
 * Feature flags configuration for Tutorputor platform.
 *
 * @doc.type module
 * @doc.purpose Centralized feature flag management
 * @doc.layer config
 * @doc.pattern Configuration
 */

/**
 * Feature flags for content generation capabilities.
 */
export interface ContentGenerationFlags {
    /**
     * Enable animation generation in the content pipeline.
     * When disabled, animation jobs will be skipped even if content needs require them.
     */
    enableAnimationGeneration: boolean;

    /**
     * Enable automatic content needs analysis when claims don't include content_needs.
     */
    enableAutoContentNeedsAnalysis: boolean;

    /**
     * Enable evidence bundle generation before modality generation.
     * Phase 1 feature - currently disabled by default.
     */
    enableEvidenceBundleGeneration: boolean;

    /**
     * Enable FActScore validation for generated content.
     * Phase 4 feature - currently disabled by default.
     */
    enableFactScoreValidation: boolean;

    /**
     * Enable auto-publish for high-confidence content.
     * Phase 4 feature - currently disabled by default.
     */
    enableAutoPublish: boolean;

    /**
     * Enable deprecated Content Studio generation endpoints.
     * These endpoints are deprecated in favor of the unified /generation/requests lifecycle.
     * When disabled, they return 410 Gone.
     */
    enableDeprecatedContentStudioEndpoints: boolean;
}

/**
 * Default feature flag values.
 */
export const DEFAULT_FEATURE_FLAGS: ContentGenerationFlags = {
    // Phase 0: Stabilization - Animation generation disabled until Phase 2
    enableAnimationGeneration: false,

    // Phase 0: Auto content needs analysis enabled for reliable fan-out
    enableAutoContentNeedsAnalysis: true,

    // Phase 1: Evidence bundle generation - disabled until implemented
    enableEvidenceBundleGeneration: false,

    // Phase 4: FActScore validation - disabled until implemented
    enableFactScoreValidation: false,

    // Phase 4: Auto-publish - disabled until validation is reliable
    enableAutoPublish: false,

    // Deprecated Content Studio endpoints - disabled by default, use unified /generation/requests
    enableDeprecatedContentStudioEndpoints: false,
};

/**
 * Load feature flags from environment variables.
 */
export function loadFeatureFlags(): ContentGenerationFlags {
    return {
        enableAnimationGeneration:
            process.env.ENABLE_ANIMATION_GENERATION === 'true' ||
            DEFAULT_FEATURE_FLAGS.enableAnimationGeneration,

        enableAutoContentNeedsAnalysis:
            process.env.ENABLE_AUTO_CONTENT_NEEDS_ANALYSIS !== 'false',

        enableEvidenceBundleGeneration:
            process.env.ENABLE_EVIDENCE_BUNDLE_GENERATION === 'true',

        enableFactScoreValidation:
            process.env.ENABLE_FACT_SCORE_VALIDATION === 'true',

        enableAutoPublish:
            process.env.ENABLE_AUTO_PUBLISH === 'true',

        enableDeprecatedContentStudioEndpoints:
            process.env.ENABLE_DEPRECATED_CONTENT_STUDIO_ENDPOINTS === 'true' ||
            DEFAULT_FEATURE_FLAGS.enableDeprecatedContentStudioEndpoints,
    };
}

/**
 * Check if a specific feature is enabled.
 */
export function isFeatureEnabled(
    flags: ContentGenerationFlags,
    feature: keyof ContentGenerationFlags
): boolean {
    return flags[feature] ?? false;
}
