package com.ghatana.tutorputor.contentgeneration;

import java.util.Map;
import java.util.Objects;

/**
 * Platform feature flag service implementation.
 *
 * <p><b>Purpose</b><br>
 * Bridges the Java content generation service with the platform's TypeScript feature flag system.
 * Reads feature flags from environment variables and provides tenant-aware flag evaluation.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PlatformFeatureFlagService service = new PlatformFeatureFlagService();
 *
 * boolean autoPublishEnabled = service.isEnabled(
 *     "autonomous_content_auto_publish",
 *     "tenant-123"
 * );
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Infrastructure adapter integrating with platform feature flag configuration.
 *
 * @doc.type class
 * @doc.purpose Feature flag service adapter for platform integration
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public final class PlatformFeatureFlagService implements GenerationPublishPolicy.FeatureFlagService {

    private final Map<String, Boolean> featureFlags;

    /**
     * Creates PlatformFeatureFlagService with environment-based flags.
     */
    public PlatformFeatureFlagService() {
        this.featureFlags = Map.of(
                "autonomous_content_auto_publish",
                Boolean.parseBoolean(
                        System.getenv().getOrDefault(
                                "TUTORPUTOR_AUTONOMOUS_CONTENT_AUTO_PUBLISH_ENABLED",
                                "false"
                        )
                ),
                "micro_viva_enabled",
                Boolean.parseBoolean(
                        System.getenv().getOrDefault("TUTORPUTOR_MICRO_VIVA_ENABLED", "false")
                ),
                "social_learning_enabled",
                Boolean.parseBoolean(
                        System.getenv().getOrDefault("TUTORPUTOR_SOCIAL_LEARNING_ENABLED", "false")
                ),
                "mobile_sync_enabled",
                Boolean.parseBoolean(
                        System.getenv().getOrDefault("TUTORPUTOR_MOBILE_SYNC_ENABLED", "false")
                ),
                "semantic_evidence_search_enabled",
                Boolean.parseBoolean(
                        System.getenv().getOrDefault("TUTORPUTOR_SEMANTIC_EVIDENCE_SEARCH_ENABLED", "false")
                ),
                "ai_grading_enabled",
                Boolean.parseBoolean(
                        System.getenv().getOrDefault("TUTORPUTOR_AI_GRADING_ENABLED", "false")
                ),
                "generated_content_i18n_enabled",
                Boolean.parseBoolean(
                        System.getenv().getOrDefault("TUTORPUTOR_GENERATED_CONTENT_I18N_ENABLED", "false")
                )
        );
    }

    /**
     * Creates PlatformFeatureFlagService with custom flag map for testing.
     *
     * @param featureFlags custom feature flag map (non-null)
     */
    public PlatformFeatureFlagService(Map<String, Boolean> featureFlags) {
        this.featureFlags = Objects.requireNonNull(
                featureFlags, "featureFlags cannot be null"
        );
    }

    @Override
    public boolean isEnabled(String flagKey, String tenantId) {
        Objects.requireNonNull(flagKey, "flagKey cannot be null");
        // TenantId is currently unused but reserved for future tenant-specific flag logic
        return featureFlags.getOrDefault(flagKey, false);
    }

    /**
     * Gets all feature flags for debugging.
     *
     * @return immutable map of feature flags
     */
    public Map<String, Boolean> getAllFlags() {
        return Map.copyOf(featureFlags);
    }
}
