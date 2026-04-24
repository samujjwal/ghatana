package com.ghatana.yappc.ai.router;

import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-model AI router that intelligently selects the best model for each task type.
 *
 * <p>Supports multiple AI models with automatic fallback chains and semantic caching.
 * Models are selected based on task characteristics:
 * <ul>
 *   <li>llama3.2 - General chat and reasoning</li>
 *   <li>codellama - Code generation and analysis</li>
 *   <li>mistral - Fast reasoning and analysis</li>
 *   <li>phi-3 - Lightweight, fast responses</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Multi-model AI routing with intelligent selection
 * @doc.layer ai
 * @doc.pattern Strategy + Chain of Responsibility
 */
public final class AIModelRouter {

    private static final Logger logger = LoggerFactory.getLogger(AIModelRouter.class);

    private final Map<String, ModelConfig> modelConfigs;
    private final Map<String, ModelAdapter> modelAdapters;
    private final SemanticCache semanticCache;
    private final ModelSelector selector;
    private final ModelAdapterFactory modelAdapterFactory;
    private volatile boolean initialized = false;

    public AIModelRouter(AIRouterConfig config) {
        this(config, AIModelRouter::createAdapter);
    }

    public AIModelRouter(AIRouterConfig config, ModelAdapterFactory modelAdapterFactory) {
        this.modelConfigs = new ConcurrentHashMap<>();
        this.modelAdapters = new ConcurrentHashMap<>();
        this.semanticCache = new SemanticCache(config.getCacheConfig());
        this.selector = new ModelSelector(config.getSelectionStrategy());
        this.modelAdapterFactory = Objects.requireNonNull(modelAdapterFactory, "modelAdapterFactory is required");

        // Register default models
        registerDefaultModels();
    }

    /**
     * Initializes the router and all model adapters.
     */
    public Promise<Void> initialize() {
        return Promise.ofCallback(cb -> {
            if (initialized) {
                cb.set(null);
                return;
            }

            logger.info("Initializing AI Model Router...");

            try {
                // Initialize all model adapters
                List<Promise<Void>> initPromises = new ArrayList<>();
                for (ModelAdapter adapter : modelAdapters.values()) {
                    initPromises.add(adapter.initialize());
                }

                Promises.all(initPromises)
                    .whenComplete((v, error) -> {
                        if (error != null) {
                            logger.error("Failed to initialize model adapters", error);
                            cb.setException(error);
                        } else {
                            initialized = true;
                            logger.info("AI Model Router initialized with {} models",
                                modelAdapters.size());
                            cb.set(null);
                        }
                    });
            } catch (Exception e) {
                logger.error("Failed to initialize AI Model Router", e);
                cb.setException(e);
            }
        });
    }

    /**
     * Routes an AI request to the appropriate model with caching.
     *
     * @param request the AI request
     * @return Promise containing the AI response
     */
    public Promise<AIResponse> route(AIRequest request) {
        return Promise.ofCallback(cb -> {
            if (!initialized) {
                cb.setException(new IllegalStateException("Router not initialized"));
                return;
            }

            try {
                // Check semantic cache first
                semanticCache.get(request)
                    .whenComplete((cachedResponse, cacheError) -> {
                        if (cachedResponse != null) {
                            logger.debug("Cache hit for request: {}", request.getTaskType());
                            cb.set(cachedResponse.withCacheHit(true));
                            return;
                        }

                        // Select best model for this request
                        String selectedModel = selector.selectModel(request, modelConfigs);
                        logger.debug("Selected model {} for task type: {}",
                            selectedModel, request.getTaskType());

                        // Execute with fallback chain
                        executeWithFallback(request, selectedModel)
                            .whenComplete((response, error) -> {
                                if (error != null) {
                                    cb.setException(error);
                                } else {
                                    // Cache successful response
                                    semanticCache.put(request, response);
                                    cb.set(response);
                                }
                            });
                    });
            } catch (Exception e) {
                logger.error("Failed to route AI request", e);
                cb.setException(e);
            }
        });
    }

    /**
     * Executes request with automatic fallback to alternative models.
     */
    private Promise<AIResponse> executeWithFallback(AIRequest request, String primaryModel) {
        return executeWithFallback(request, primaryModel, new LinkedHashSet<>());
    }

    private Promise<AIResponse> executeWithFallback(
            AIRequest request,
            String modelId,
            LinkedHashSet<String> attemptedModels) {
        return Promise.ofCallback(cb -> {
            if (!attemptedModels.add(modelId)) {
                cb.setException(new IllegalStateException(
                        "Deterministic fallback cycle detected: " + String.join(" -> ", attemptedModels)
                                + " -> " + modelId));
                return;
            }

            ModelAdapter adapter = modelAdapters.get(modelId);
            if (adapter == null) {
                cb.setException(new IllegalStateException("Model not found: " + modelId));
                return;
            }

            adapter.execute(request)
                .whenComplete((response, error) -> {
                    if (error != null) {
                        ModelConfig config = modelConfigs.get(modelId);
                        List<String> fallbackChain = config == null
                                ? List.of()
                                : config.getFallbackChain();

                        String nextModel = fallbackChain.stream()
                                .filter(candidate -> !attemptedModels.contains(candidate))
                                .findFirst()
                                .orElse(null);

                        if (nextModel == null) {
                            if (!fallbackChain.isEmpty()) {
                            cb.setException(new IllegalStateException(
                                "Deterministic fallback cycle detected: "
                                    + String.join(" -> ", attemptedModels)
                                    + " -> " + fallbackChain.get(0),
                                error));
                            return;
                            }
                            cb.setException(error);
                            return;
                        }

                        logger.warn("Primary model {} failed, trying fallback: {}",
                            modelId, nextModel);
                        executeWithFallback(request, nextModel, attemptedModels)
                            .whenComplete((fallbackResponse, fallbackError) -> {
                                if (fallbackError != null) {
                                    cb.setException(fallbackError);
                                } else {
                                    cb.set(fallbackResponse.withFallbackUsed(true));
                                }
                            });
                    } else {
                        cb.set(response);
                    }
                });
        });
    }

    /**
     * Registers default Ollama models.
     */
    private void registerDefaultModels() {
        // llama3.2 - General purpose
        registerModel(ModelConfig.builder()
            .modelId("llama3.2")
            .displayName("Llama 3.2")
            .provider("ollama")
            .capabilities(Set.of("chat", "reasoning", "general"))
            .maxTokens(4096)
            .costPerToken(0.0) // Local Ollama is free
            .fallbackChain(List.of("mistral", "phi-3"))
            .build());

        // codellama - Code specialist
        registerModel(ModelConfig.builder()
            .modelId("codellama")
            .displayName("Code Llama")
            .provider("ollama")
            .capabilities(Set.of("code", "programming", "analysis"))
            .maxTokens(4096)
            .costPerToken(0.0)
            .fallbackChain(List.of("llama3.2"))
            .build());

        // mistral - Fast reasoning
        registerModel(ModelConfig.builder()
            .modelId("mistral")
            .displayName("Mistral 7B")
            .provider("ollama")
            .capabilities(Set.of("reasoning", "analysis", "fast"))
            .maxTokens(4096)
            .costPerToken(0.0)
            .fallbackChain(List.of("phi-3"))
            .build());

        // phi-3 - Lightweight and fast
        registerModel(ModelConfig.builder()
            .modelId("phi-3")
            .displayName("Phi-3 Mini")
            .provider("ollama")
            .capabilities(Set.of("fast", "lightweight", "general"))
            .maxTokens(2048)
            .costPerToken(0.0)
            .fallbackChain(List.of("llama3.2"))
            .build());
    }

    /**
     * Registers a model configuration and creates its adapter.
     */
    public void registerModel(ModelConfig config) {
        modelConfigs.put(config.getModelId(), config);

        ModelAdapter adapter = modelAdapterFactory.create(config);
        modelAdapters.put(config.getModelId(), adapter);
        logger.info("Registered model: {} ({})", config.getDisplayName(), config.getModelId());
    }

    private static ModelAdapter createAdapter(ModelConfig config) {
        return switch (config.getProvider()) {
            case "ollama" -> new OllamaModelAdapter(config);
            case "openai" -> new OpenAIModelAdapter(config);
            case "anthropic" -> new AnthropicModelAdapter(config);
            default -> throw new IllegalArgumentException("Unknown provider: " + config.getProvider());
        };
    }

    /**
     * Gets current cache statistics.
     */
    public CacheStatistics getCacheStatistics() {
        return semanticCache.getStatistics();
    }

    /**
     * Gets available models.
     */
    public Map<String, ModelConfig> getAvailableModels() {
        return Collections.unmodifiableMap(modelConfigs);
    }

    /**
     * Shuts down the router and all adapters.
     */
    public Promise<Void> shutdown() {
        return Promise.ofCallback(cb -> {
            logger.info("Shutting down AI Model Router...");

            List<Promise<Void>> shutdownPromises = new ArrayList<>();
            for (ModelAdapter adapter : modelAdapters.values()) {
                shutdownPromises.add(adapter.shutdown());
            }

            Promises.all(shutdownPromises)
                .whenComplete((v, error) -> {
                    initialized = false;
                    semanticCache.clear();
                    logger.info("AI Model Router shut down");
                    cb.set(null);
                });
        });
    }

    @FunctionalInterface
    public interface ModelAdapterFactory {
        ModelAdapter create(ModelConfig config);
    }
}
