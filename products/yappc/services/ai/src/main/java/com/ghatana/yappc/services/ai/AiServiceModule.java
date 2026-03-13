/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC AI Service Module
 */
package com.ghatana.yappc.services.ai;

import com.ghatana.ai.llm.DefaultLLMGateway;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.ai.llm.LLMGateway;
import com.ghatana.ai.llm.OllamaCompletionService;
import com.ghatana.ai.llm.ToolAwareAnthropicCompletionService;
import com.ghatana.ai.llm.ToolAwareOpenAICompletionService;
import com.ghatana.ai.prompts.PromptTemplateManager;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.NoopMetricsCollector;
import com.ghatana.yappc.ai.canvas.CanvasService;
import com.ghatana.yappc.ai.router.AIModelRouter;
import com.ghatana.yappc.ai.router.AIRouterConfig;
import com.ghatana.yappc.ai.router.ModelSelector.SelectionStrategy;
import com.ghatana.yappc.ai.router.SemanticCache.CacheConfig;
import com.ghatana.yappc.ai.service.YAPPCAIService;
import com.ghatana.yappc.ai.canvas.CanvasGenerationService;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ActiveJ DI module for YAPPC AI services.
 *
 * <p>Provides bindings for:
 * <ul>
 *   <li>{@link AIRouterConfig} — Multi-model routing configuration</li>
 *   <li>{@link AIModelRouter} — Intelligent model selection and routing</li>
 *   <li>{@link YAPPCAIService} — High-level AI facade (code gen, analysis, review)</li>
 *   <li>{@link CanvasService} — Canvas generation and management</li>
 *   <li>{@link CanvasGenerationService} — AI-powered canvas code generation</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose DI module for AI service layer
 * @doc.layer product
 * @doc.pattern Module
 */
public class AiServiceModule extends AbstractModule {

    private static final Logger logger = LoggerFactory.getLogger(AiServiceModule.class);

    @Override
    protected void configure() {
        logger.info("Configuring YAPPC AI Service DI bindings");
    }

    // ========== Platform Dependencies ==========

    /**
     * Provides MetricsCollector for AI service instrumentation.
     *
     * <p>Uses no-op implementation by default; override for production telemetry.</p>
     */
    @Provides
    MetricsCollector metricsCollector() {
        logger.info("Creating NoopMetricsCollector for AI service");
        return new NoopMetricsCollector();
    }

    /**
     * Provides PromptTemplateManager for prompt rendering and validation.
     */
    @Provides
    PromptTemplateManager promptTemplateManager() {
        logger.info("Creating PromptTemplateManager");
        return new PromptTemplateManager();
    }

    /**
     * Provides LLMGateway backed by environment-configured providers.
     *
     * <p>Consults, in order: {@code ANTHROPIC_API_KEY}, {@code OPENAI_API_KEY},
     * {@code OLLAMA_HOST}. At least one must be present; if none are set the
     * service refuses to start with a clear {@link IllegalStateException}.
     * Model names are read from the respective {@code *_MODEL} env vars and
     * must <b>not</b> be hardcoded in Java — all routing is config-driven.</p>
     *
     * @param metrics metrics collector for LLM call instrumentation
     * @return configured {@link LLMGateway}
     * @throws IllegalStateException if no provider env var is set
     */
    @Provides
    LLMGateway llmGateway(MetricsCollector metrics) {
        DefaultLLMGateway.Builder builder = DefaultLLMGateway.builder().metrics(metrics);
        boolean anyConfigured = false;

        String anthropicKey = System.getenv("ANTHROPIC_API_KEY");
        if (anthropicKey != null && !anthropicKey.isBlank()) {
            String model = System.getenv("ANTHROPIC_MODEL");
            if (model == null || model.isBlank()) {
                throw new IllegalStateException(
                        "ANTHROPIC_API_KEY is set but ANTHROPIC_MODEL env var is missing. "
                        + "Specify the Claude model name (e.g. claude-3-5-sonnet-20241022).");
            }
            LLMConfiguration cfg = LLMConfiguration.builder()
                    .apiKey(anthropicKey)
                    .modelName(model)
                    .temperature(0.7)
                    .maxTokens(2000)
                    .timeoutSeconds(30)
                    .maxRetries(3)
                    .build();
            builder.addProvider("anthropic", new ToolAwareAnthropicCompletionService(cfg, null, metrics));
            builder.defaultProvider("anthropic");
            anyConfigured = true;
            logger.info("YAPPC LLMGateway configured with Anthropic provider model={}", model);
        }

        String openAiKey = System.getenv("OPENAI_API_KEY");
        if (openAiKey != null && !openAiKey.isBlank()) {
            String model = System.getenv("OPENAI_MODEL");
            if (model == null || model.isBlank()) {
                throw new IllegalStateException(
                        "OPENAI_API_KEY is set but OPENAI_MODEL env var is missing. "
                        + "Specify the OpenAI model name (e.g. gpt-4o).");
            }
            LLMConfiguration cfg = LLMConfiguration.builder()
                    .apiKey(openAiKey)
                    .modelName(model)
                    .temperature(0.7)
                    .maxTokens(2000)
                    .timeoutSeconds(30)
                    .maxRetries(3)
                    .build();
            builder.addProvider("openai", new ToolAwareOpenAICompletionService(cfg, null, metrics));
            if (!anyConfigured) {
                builder.defaultProvider("openai");
            }
            anyConfigured = true;
            logger.info("YAPPC LLMGateway configured with OpenAI provider model={}", model);
        }

        String ollamaHost = System.getenv("OLLAMA_HOST");
        if (ollamaHost != null && !ollamaHost.isBlank()) {
            String model = System.getenv("OLLAMA_MODEL");
            if (model == null || model.isBlank()) {
                throw new IllegalStateException(
                        "OLLAMA_HOST is set but OLLAMA_MODEL env var is missing. "
                        + "Specify the Ollama model name (e.g. llama3.2).");
            }
            LLMConfiguration cfg = LLMConfiguration.builder()
                    .apiKey("ollama")
                    .baseUrl(ollamaHost)
                    .modelName(model)
                    .temperature(0.7)
                    .maxTokens(2000)
                    .timeoutSeconds(60)
                    .maxRetries(2)
                    .build();
            builder.addProvider("ollama", new OllamaCompletionService(cfg, null, metrics));
            if (!anyConfigured) {
                builder.defaultProvider("ollama");
            }
            anyConfigured = true;
            logger.info("YAPPC LLMGateway configured with Ollama provider host={} model={}", ollamaHost, model);
        }

        if (!anyConfigured) {
            throw new IllegalStateException(
                    "No LLM provider configured for YAPPC AI service. "
                    + "Set at least one of: ANTHROPIC_API_KEY (+ ANTHROPIC_MODEL), "
                    + "OPENAI_API_KEY (+ OPENAI_MODEL), OLLAMA_HOST (+ OLLAMA_MODEL).");
        }

        return builder.build();
    }

    /**
     * Provides default AI router configuration.
     *
     * <p>Uses task-based model selection with semantic caching enabled.
     * Override via system properties:
     * <ul>
     *   <li>{@code yappc.ai.model.default} — default model name</li>
     *   <li>{@code yappc.ai.cache.enabled} — enable/disable semantic cache</li>
     * </ul>
     */
    @Provides
    AIRouterConfig aiRouterConfig() {
        String defaultModel = System.getProperty("yappc.ai.model.default", "llama3.2");
        boolean cacheEnabled = Boolean.parseBoolean(
                System.getProperty("yappc.ai.cache.enabled", "true"));

        logger.info("AI Router config: model={}, cache={}", defaultModel, cacheEnabled);

        return AIRouterConfig.builder()
                .selectionStrategy(SelectionStrategy.TASK_BASED)
                .cacheConfig(CacheConfig.builder()
                        .enabled(cacheEnabled)
                        .build())
                .defaultModel(defaultModel)
                .build();
    }

    /**
     * Provides the multi-model AI router.
     *
     * <p>Supports Ollama (llama3.2, codellama, mistral, phi-3),
     * OpenAI, and Anthropic adapters with automatic fallback chains.</p>
     */
    @Provides
    AIModelRouter aiModelRouter(AIRouterConfig config) {
        logger.info("Creating AIModelRouter with strategy: {}", config.getSelectionStrategy());
        return new AIModelRouter(config);
    }

    /**
     * Provides the high-level YAPPC AI service.
     *
     * <p>Facade over AIModelRouter with simplified API for:
     * code generation, code analysis, code review, refactoring suggestions.</p>
     */
    @Provides
    YAPPCAIService yappcAiService() {
        logger.info("Creating YAPPCAIService with task-based routing");
        return YAPPCAIService.builder()
                .selectionStrategy(SelectionStrategy.TASK_BASED)
                .cacheEnabled(true)
                .build();
    }

    /**
     * Provides canvas generation and management service.
     */
    @Provides
    CanvasService canvasService() {
        logger.info("Creating CanvasService");
        return new CanvasService();
    }

    /**
     * Provides the AI-powered canvas code generation service.
     *
     * <p>Wires {@link LLMGateway}, {@link PromptTemplateManager}, and
     * {@link MetricsCollector} to produce production-ready code artifacts
     * from canvas designs.</p>
     */
    @Provides
    CanvasGenerationService canvasGenerationService(
            LLMGateway llmGateway,
            PromptTemplateManager promptTemplates,
            MetricsCollector metrics) {
        logger.info("Creating CanvasGenerationService backed by LLMGateway");
        return new CanvasGenerationService(llmGateway, promptTemplates, metrics);
    }
}
