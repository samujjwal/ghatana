package com.ghatana.services.aiinference;

import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.ai.embedding.OpenAIEmbeddingService;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.ai.llm.LLMConfiguration;
import com.ghatana.ai.llm.OpenAICompletionService;
import com.ghatana.aiplatform.gateway.LLMGatewayService;
import com.ghatana.aiplatform.gateway.PromptCache;
import com.ghatana.aiplatform.gateway.ProviderRouter;
import com.ghatana.aiplatform.gateway.RateLimiter;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import com.ghatana.core.state.HybridStateStore;
import com.ghatana.core.state.InMemoryStateStore;
import com.ghatana.core.state.SyncStrategy;
import io.activej.eventloop.Eventloop;
import io.activej.http.HttpClient;
import io.activej.http.HttpServer;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.Module;
import io.activej.launcher.Launcher;
import io.activej.service.ServiceGraphModule;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Launcher for AI Inference Service.
 *
 * <p>
 * <b>Purpose</b><br>
 * Bootstraps AI Inference Service with ActiveJ DI, HTTP server, LLM Gateway,
 * and all required dependencies (providers, caching, rate limiting).
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * java -jar ai-inference-service.jar
 * }</pre>
 *
 * <p>
 * <b>Configuration</b><br>
 * Environment variables: - OPENAI_API_KEY: OpenAI API key (required) -
 * AI_SERVICE_PORT: HTTP server port (default: 8080) - AI_SERVICE_HOST: HTTP
 * server host (default: 0.0.0.0)
 *
 * @doc.type class
 * @doc.purpose AI Inference Service launcher with ActiveJ
 * @doc.layer application
 * @doc.pattern Launcher + DI Container
 */
public class AIInferenceServiceLauncher extends Launcher {

    private static final Logger logger = LoggerFactory.getLogger(AIInferenceServiceLauncher.class);

    /**
     * Main entry point.
     */
    public static void main(String[] args) throws Exception {
        Launcher launcher = new AIInferenceServiceLauncher();
        launcher.launch(args);
    }

    @Override
    protected Module getModule() {
        return ServiceGraphModule.create();
    }

    @Override
    protected void run() throws Exception {
        logger.info("AI Inference Service started");
        awaitShutdown();
    }

    /**
     * Provides Eventloop instance.
     */
    @Provides
    Eventloop eventloop() {
        return Eventloop.create();
    }

    /**
     * Provides MeterRegistry for metrics.
     */
    @Provides
    MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    /**
     * Provides MetricsCollector.
     */
    @Provides
    MetricsCollector metricsCollector(MeterRegistry registry) {
        return MetricsCollectorFactory.create(registry);
    }

    /**
     * Provides OpenAI embedding service.
     */
    @Provides
    EmbeddingService embeddingService(LLMConfiguration config, MetricsCollector metrics) {
        return new OpenAIEmbeddingService(config, metrics);
    }

    /**
     * Provides OpenAI completion service.
     */
    @Provides
    CompletionService completionService(LLMConfiguration config, HttpClient httpClient, MetricsCollector metrics) {
        return new OpenAICompletionService(config, httpClient, metrics);
    }

    /**
     * Provides HybridStateStore for prompt caching.
     */
    @Provides
    HybridStateStore<String, byte[]> cacheStore() {
        // For MVP, use in-memory only
        // In production, wire Redis as central store
        InMemoryStateStore<String, byte[]> localStore = new InMemoryStateStore<>();
        InMemoryStateStore<String, byte[]> centralStore = new InMemoryStateStore<>();
        return HybridStateStore.<String, byte[]>builder()
                .localStore(localStore)
                .centralStore(centralStore)
                .syncStrategy(SyncStrategy.BATCHED)
                .build();
    }

    /**
     * Provides HTTP client for external API calls.
     */
    @Provides
    HttpClient httpClient(Eventloop eventloop) {
        // HttpClient needs a DNS client for domain resolution
        io.activej.dns.IDnsClient dnsClient = io.activej.dns.DnsClient.builder(eventloop,
                java.net.InetAddress.getLoopbackAddress()).build();
        return HttpClient.create(eventloop, dnsClient);
    }

    /**
     * Provides LLM configuration from environment.
     */
    @Provides
    LLMConfiguration llmConfig() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                "OPENAI_API_KEY environment variable is required but not set. "
                + "Set OPENAI_API_KEY before starting the AI Inference Service.");
        }

        return LLMConfiguration.builder()
                .apiKey(apiKey)
                .modelName(System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4"))
                .maxTokens(Integer.parseInt(System.getenv().getOrDefault("OPENAI_MAX_TOKENS", "2000")))
                .temperature(Double.parseDouble(System.getenv().getOrDefault("OPENAI_TEMPERATURE", "0.7")))
                .timeoutSeconds(30)
                .maxRetries(3)
                .build();
    }

    /**
     * Provides PromptCache.
     */
    @Provides
    PromptCache promptCache(HybridStateStore<String, byte[]> store) {
        int ttlSeconds = Integer.parseInt(System.getenv().getOrDefault("PROMPT_CACHE_TTL", "600"));
        return new PromptCache(store, ttlSeconds);
    }

    /**
     * Provides RateLimiter.
     */
    @Provides
    RateLimiter rateLimiter() {
        int defaultCapacity = Integer.parseInt(System.getenv().getOrDefault("RATE_LIMIT_CAPACITY", "1000"));
        double defaultRate = Double.parseDouble(System.getenv().getOrDefault("RATE_LIMIT_RATE", "10.0"));
        return new RateLimiter(defaultCapacity, defaultRate);
    }

    /**
     * Provides ProviderRouter with default providers.
     */
    @Provides
    ProviderRouter providerRouter(EmbeddingService embeddingService, CompletionService completionService, MetricsCollector metrics) {
        ProviderRouter router = new ProviderRouter(metrics);

        // Register default providers (OpenAI)
        router.registerEmbeddingService("openai", embeddingService);
        router.registerCompletionService("openai", completionService);
        router.setDefaultEmbeddingProvider("openai");
        router.setDefaultCompletionProvider("openai");

        // TODO: Register fallback providers (local models)
        return router;
    }

    /**
     * Provides LLMGatewayService.
     */
    @Provides
    LLMGatewayService gateway(
            ProviderRouter router,
            PromptCache cache,
            RateLimiter limiter,
            MetricsCollector metrics) {
        return new LLMGatewayService(router, cache, limiter, metrics);
    }

    /**
     * Provides HTTP adapter.
     */
    @Provides
    AIInferenceHttpAdapter httpAdapter(LLMGatewayService gateway, MetricsCollector metrics) {
        return new AIInferenceHttpAdapter(gateway, metrics);
    }

    /**
     * Provides HTTP server.
     */
    @Provides
    HttpServer httpServer(Eventloop eventloop, AIInferenceHttpAdapter adapter) {
        String host = System.getenv().getOrDefault("AI_SERVICE_HOST", "0.0.0.0");
        int port = Integer.parseInt(System.getenv().getOrDefault("AI_SERVICE_PORT", "8080"));

        logger.info("Starting AI Inference Service on {}:{}", host, port);

        return HttpServer.builder(eventloop, adapter.buildServlet())
                .withListenPort(port)
                .build();
    }
}
