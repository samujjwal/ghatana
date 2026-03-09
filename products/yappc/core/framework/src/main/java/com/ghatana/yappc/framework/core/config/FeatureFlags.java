package com.ghatana.yappc.framework.core.config;

import io.activej.config.Config;
import io.activej.config.converter.ConfigConverters;
import io.activej.inject.annotation.Inject;
import io.activej.inject.annotation.Provides;
import org.jetbrains.annotations.NotNull;

/**
 * Feature flags configuration for YAPPC platform.
 *
 * <p>Provides feature flag management for gradual rollout of incomplete features.
 * Features can be enabled/disabled via configuration without code changes.</p>
 *
 * @doc.type class
 * @doc.purpose Feature flag management for gradual rollout
 * @doc.layer infrastructure
 * @doc.pattern Configuration
 */
public final class FeatureFlags {

    // Singleton instance for static access (set by DI or explicitly)
    private static volatile FeatureFlags INSTANCE;

    /**
     * Set the global FeatureFlags instance.
     * Called during application initialization.
     */
    public static void setGlobal(@NotNull FeatureFlags flags) {
        INSTANCE = flags;
    }

    /**
     * Static convenience method to check if a feature flag is enabled.
     * Returns false if no global instance has been set.
     *
     * @param flag the feature flag to check
     * @return true if enabled, false otherwise
     */
    public static boolean isEnabled(@NotNull FeatureFlag flag) {
        FeatureFlags instance = INSTANCE;
        if (instance == null) {
            return false; // Safe default when not yet initialized
        }
        return instance.isFeatureEnabled(flag.key());
    }

    // LLM Provider Features
    public static final String LLM_OPENAI_ENABLED = "feature.llm.openai.enabled";
    public static final String LLM_ANTHROPIC_ENABLED = "feature.llm.anthropic.enabled";
    public static final String LLM_OLLAMA_ENABLED = "feature.llm.ollama.enabled";

    // AI Features
    public static final String AI_CANVAS_GENERATION = "feature.ai.canvas.generation";
    public static final String AI_CODE_REVIEW = "feature.ai.code.review";
    public static final String AI_REQUIREMENTS_PARSING = "feature.ai.requirements.parsing";

    // DataCloud Features
    public static final String DATACLOUD_DASHBOARD = "feature.datacloud.dashboard";
    public static final String DATACLOUD_WIDGET = "feature.datacloud.widget";

    // UI Features
    public static final String UI_CANVAS_EDITING = "feature.ui.canvas.editing";
    public static final String UI_DARK_MODE = "feature.ui.dark.mode";
    public static final String UI_MOBILE_VIEW = "feature.ui.mobile.view";

    // Integration Features
    public static final String INTEGRATION_GITHUB = "feature.integration.github";
    public static final String INTEGRATION_FIGMA = "feature.integration.figma";

    // Development Features
    public static final String DEV_DEBUG_MODE = "feature.dev.debug";
    public static final String DEV_MOCK_SERVICES = "feature.dev.mock.services";

    private final Config config;

    @Inject
    private FeatureFlags(@NotNull Config config) {
        this.config = config;
    }

    @Provides
    public static FeatureFlags create(@NotNull Config config) {
        return new FeatureFlags(config);
    }

    // LLM Provider Checks

    /**
     * Check if OpenAI provider is enabled.
     */
    public boolean isOpenAIEnabled() {
        return config.get(ConfigConverters.ofBoolean(), LLM_OPENAI_ENABLED, false);
    }

    /**
     * Check if Anthropic provider is enabled.
     */
    public boolean isAnthropicEnabled() {
        return config.get(ConfigConverters.ofBoolean(), LLM_ANTHROPIC_ENABLED, false);
    }

    /**
     * Check if Ollama provider is enabled.
     */
    public boolean isOllamaEnabled() {
        return config.get(ConfigConverters.ofBoolean(), LLM_OLLAMA_ENABLED, false);
    }

    /**
     * Get the default LLM provider to use.
     */
    @NotNull
    public String getDefaultLLMProvider() {
        if (isOpenAIEnabled()) {
            return "openai";
        } else if (isAnthropicEnabled()) {
            return "anthropic";
        } else if (isOllamaEnabled()) {
            return "ollama";
        }
        return "mock";
    }

    // AI Feature Checks

    /**
     * Check if canvas AI generation is enabled.
     */
    public boolean isCanvasGenerationEnabled() {
        return config.get(ConfigConverters.ofBoolean(), AI_CANVAS_GENERATION, false);
    }

    /**
     * Check if AI code review is enabled.
     */
    public boolean isCodeReviewEnabled() {
        return config.get(ConfigConverters.ofBoolean(), AI_CODE_REVIEW, false);
    }

    /**
     * Check if AI requirements parsing is enabled.
     */
    public boolean isRequirementsParsingEnabled() {
        return config.get(ConfigConverters.ofBoolean(), AI_REQUIREMENTS_PARSING, false);
    }

    // DataCloud Feature Checks

    /**
     * Check if DataCloud dashboard persistence is enabled.
     */
    public boolean isDashboardPersistenceEnabled() {
        return config.get(ConfigConverters.ofBoolean(), DATACLOUD_DASHBOARD, false);
    }

    /**
     * Check if DataCloud widget persistence is enabled.
     */
    public boolean isWidgetPersistenceEnabled() {
        return config.get(ConfigConverters.ofBoolean(), DATACLOUD_WIDGET, false);
    }

    // UI Feature Checks

    /**
     * Check if canvas editing UI is enabled.
     */
    public boolean isCanvasEditingEnabled() {
        return config.get(ConfigConverters.ofBoolean(), UI_CANVAS_EDITING, true);
    }

    /**
     * Check if dark mode is enabled.
     */
    public boolean isDarkModeEnabled() {
        return config.get(ConfigConverters.ofBoolean(), UI_DARK_MODE, true);
    }

    /**
     * Check if mobile view is enabled.
     */
    public boolean isMobileViewEnabled() {
        return config.get(ConfigConverters.ofBoolean(), UI_MOBILE_VIEW, false);
    }

    // Integration Feature Checks

    /**
     * Check if GitHub integration is enabled.
     */
    public boolean isGitHubIntegrationEnabled() {
        return config.get(ConfigConverters.ofBoolean(), INTEGRATION_GITHUB, false);
    }

    /**
     * Check if Figma integration is enabled.
     */
    public boolean isFigmaIntegrationEnabled() {
        return config.get(ConfigConverters.ofBoolean(), INTEGRATION_FIGMA, false);
    }

    // Development Feature Checks

    /**
     * Check if debug mode is enabled.
     */
    public boolean isDebugModeEnabled() {
        return config.get(ConfigConverters.ofBoolean(), DEV_DEBUG_MODE, false);
    }

    /**
     * Check if mock services are enabled (for development/testing).
     */
    public boolean areMockServicesEnabled() {
        return config.get(ConfigConverters.ofBoolean(), DEV_MOCK_SERVICES, true);
    }

    /**
     * Check if a feature is enabled by name.
     */
    public boolean isFeatureEnabled(@NotNull String featureName) {
        return config.get(ConfigConverters.ofBoolean(), featureName, false);
    }

    /**
     * Get a feature flag value with default.
     */
    @NotNull
    public String getFeatureValue(@NotNull String featureName, @NotNull String defaultValue) {
        return config.get(featureName, defaultValue);
    }
}
