package com.ghatana.tutorputor.experiment;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Content strategy selector that integrates with A/B experiments.
 * 
 * <p>Selects content generation strategies based on experiment assignments,
 * supporting multiple simultaneous experiments with fallback to defaults.
 *
 * @doc.type class
 * @doc.purpose Strategy selection based on experiments
 * @doc.layer product
 * @doc.pattern Strategy
 */
public class ContentStrategySelector {

    private static final Logger LOG = LoggerFactory.getLogger(ContentStrategySelector.class);

    private final ExperimentManager experimentManager;
    private final ConcurrentMap<String, ContentStrategy> strategies;
    private final String defaultStrategyId;

    /**
     * Creates a new strategy selector.
     *
     * @param experimentManager the experiment manager
     * @param defaultStrategyId the default strategy ID
     */
    public ContentStrategySelector(
            @NotNull ExperimentManager experimentManager,
            @NotNull String defaultStrategyId) {
        this.experimentManager = experimentManager;
        this.defaultStrategyId = defaultStrategyId;
        this.strategies = new ConcurrentHashMap<>();
        
        // Register built-in strategies
        registerBuiltInStrategies();
        
        LOG.info("ContentStrategySelector initialized with default strategy: {}", 
            defaultStrategyId);
    }

    /**
     * Registers a content strategy.
     *
     * @param strategy the strategy to register
     */
    public void registerStrategy(@NotNull ContentStrategy strategy) {
        strategies.put(strategy.id(), strategy);
        LOG.debug("Registered strategy: {}", strategy.id());
    }

    /**
     * Gets the strategy for a user based on experiment assignments.
     *
     * @param experimentId the experiment ID
     * @param userId the user ID
     * @return the selected strategy
     */
    public ContentStrategy selectStrategy(
            @NotNull String experimentId, 
            @NotNull String userId) {
        
        ExperimentManager.Variant variant = experimentManager.getVariant(experimentId, userId);
        
        if (variant == null) {
            LOG.debug("User {} not in experiment {}, using default", userId, experimentId);
            return getDefaultStrategy();
        }
        
        String strategyId = (String) variant.config().getOrDefault("strategyId", defaultStrategyId);
        ContentStrategy strategy = strategies.get(strategyId);
        
        if (strategy == null) {
            LOG.warn("Strategy {} not found, using default", strategyId);
            return getDefaultStrategy();
        }
        
        // Record exposure
        experimentManager.recordExposure(experimentId, userId, variant.id());
        
        LOG.debug("Selected strategy {} for user {} in experiment {}", 
            strategyId, userId, experimentId);
        
        return strategy;
    }

    /**
     * Gets the default strategy.
     *
     * @return the default strategy
     */
    public ContentStrategy getDefaultStrategy() {
        ContentStrategy strategy = strategies.get(defaultStrategyId);
        if (strategy == null) {
            throw new IllegalStateException("Default strategy not found: " + defaultStrategyId);
        }
        return strategy;
    }

    /**
     * Gets a strategy by ID.
     *
     * @param strategyId the strategy ID
     * @return the strategy, or null if not found
     */
    public ContentStrategy getStrategy(@NotNull String strategyId) {
        return strategies.get(strategyId);
    }

    /**
     * Lists all registered strategies.
     *
     * @return list of strategies
     */
    public List<ContentStrategy> listStrategies() {
        return new ArrayList<>(strategies.values());
    }

    private void registerBuiltInStrategies() {
        // Standard LLM-based strategy
        registerStrategy(new ContentStrategy(
            "llm-standard",
            "Standard LLM",
            "Standard LLM-based content generation",
            Map.of(
                "model", "gpt-4",
                "temperature", 0.7,
                "maxTokens", 2000,
                "useKnowledgeBase", true
            )
        ));

        // Creative LLM strategy with higher temperature
        registerStrategy(new ContentStrategy(
            "llm-creative",
            "Creative LLM",
            "Creative content with higher variability",
            Map.of(
                "model", "gpt-4",
                "temperature", 0.9,
                "maxTokens", 2500,
                "useKnowledgeBase", true,
                "creativityBoost", true
            )
        ));

        // Conservative LLM strategy with lower temperature
        registerStrategy(new ContentStrategy(
            "llm-conservative",
            "Conservative LLM",
            "Factual content with low variability",
            Map.of(
                "model", "gpt-4",
                "temperature", 0.3,
                "maxTokens", 1500,
                "useKnowledgeBase", true,
                "factCheckEnabled", true
            )
        ));

        // Local model strategy (Ollama)
        registerStrategy(new ContentStrategy(
            "local-llama",
            "Local Llama",
            "Local Llama model for privacy-sensitive content",
            Map.of(
                "provider", "ollama",
                "model", "llama2",
                "temperature", 0.7,
                "maxTokens", 2000
            )
        ));

        // Hybrid strategy
        registerStrategy(new ContentStrategy(
            "hybrid-verify",
            "Hybrid with Verification",
            "LLM generation with fact verification",
            Map.of(
                "model", "gpt-4",
                "temperature", 0.7,
                "maxTokens", 2000,
                "useKnowledgeBase", true,
                "factCheckEnabled", true,
                "verificationSource", "wikipedia"
            )
        ));

        // Age-adaptive strategy
        registerStrategy(new ContentStrategy(
            "age-adaptive",
            "Age Adaptive",
            "Adjusts complexity based on learner age",
            Map.of(
                "model", "gpt-4",
                "temperature", 0.6,
                "maxTokens", 1800,
                "ageAdaptive", true,
                "vocabularyAdjustment", true
            )
        ));

        // Multi-modal strategy
        registerStrategy(new ContentStrategy(
            "multi-modal",
            "Multi-Modal",
            "Generates text with suggested visuals",
            Map.of(
                "model", "gpt-4-vision",
                "temperature", 0.7,
                "maxTokens", 2500,
                "includeVisuals", true,
                "visualSuggestions", true
            )
        ));

        LOG.info("Registered {} built-in strategies", strategies.size());
    }

    // =========================================================================
    // Types
    // =========================================================================

    /**
     * Content generation strategy.
     */
    public record ContentStrategy(
        String id,
        String name,
        String description,
        Map<String, Object> parameters
    ) {
        /**
         * Gets a parameter value.
         *
         * @param key the parameter key
         * @param defaultValue the default value
         * @param <T> the value type
         * @return the parameter value
         */
        @SuppressWarnings("unchecked")
        public <T> T getParameter(String key, T defaultValue) {
            Object value = parameters.get(key);
            return value != null ? (T) value : defaultValue;
        }

        /**
         * Gets the model name.
         *
         * @return the model name
         */
        public String getModel() {
            return getParameter("model", "gpt-4");
        }

        /**
         * Gets the temperature.
         *
         * @return the temperature
         */
        public double getTemperature() {
            return getParameter("temperature", 0.7);
        }

        /**
         * Gets the max tokens.
         *
         * @return the max tokens
         */
        public int getMaxTokens() {
            return getParameter("maxTokens", 2000);
        }

        /**
         * Whether knowledge base is enabled.
         *
         * @return true if knowledge base should be used
         */
        public boolean useKnowledgeBase() {
            return getParameter("useKnowledgeBase", true);
        }

        /**
         * Whether fact checking is enabled.
         *
         * @return true if fact checking is enabled
         */
        public boolean isFactCheckEnabled() {
            return getParameter("factCheckEnabled", false);
        }

        /**
         * Gets the provider name.
         *
         * @return the provider (openai, ollama, etc.)
         */
        public String getProvider() {
            return getParameter("provider", "openai");
        }
    }
}
