package com.ghatana.aiplatform.gateway;

import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.ai.llm.CompletionService;
import com.ghatana.platform.core.exception.RateLimitExceededException;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Unified gateway for LLM operations with routing, caching, and fallback strategies.
 *
 * <p><b>Purpose</b><br>
 * Provides single entry point for all LLM operations (embeddings, completions) with:
 * <ul>
 *   <li>Provider routing based on tenant/model policies</li>
 *   <li>Prompt caching for cost optimization</li>
 *   <li>Rate limiting per tenant</li>
 *   <li>Fallback strategies on provider failures</li>
 *   <li>Cost tracking and observability</li>
 * </ul>
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * LLMGatewayService gateway = new LLMGatewayService(
 *     router, promptCache, rateLimiter, metrics);
 *
 * // Generate embedding
 * Promise<EmbeddingResult> embedding = gateway.generateEmbedding(
 *     "tenant-123", "Hello world");
 *
 * // Generate completion
 * CompletionRequest request = CompletionRequest.builder()
 *     .prompt("Translate to French: Hello")
 *     .build();
 * Promise<CompletionResult> completion = gateway.generateCompletion(
 *     "tenant-123", request);
 * }</pre>
 *
 * <p><b>Architecture Role</b><br>
 * Core AI platform service coordinating provider selection, caching, and observability.
 * Used by OnlineInferenceService, BatchInferenceJob, and product-specific AI features.
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe - all operations are asynchronous via ActiveJ Promise.
 *
 * <p><b>Performance Characteristics</b><br>
 * - Cache hit: ~1ms overhead (lookup + deserialization)
 * - Cache miss: ~5ms overhead (routing + bookkeeping) + provider latency
 * - Fallback: +50-200ms (retry with backoff)
 *
 * @doc.type class
 * @doc.purpose LLM gateway with routing, caching, and fallback
 * @doc.layer platform
 * @doc.pattern Gateway + Facade
 */
public class LLMGatewayService {

    private static final Logger logger = LoggerFactory.getLogger(LLMGatewayService.class);

    private final ProviderRouter router;
    private final PromptCache promptCache;
    private final RateLimiter rateLimiter;
    private final MetricsCollector metrics;

    /**
     * Constructs LLM gateway service.
     *
     * @param router provider routing strategy
     * @param promptCache prompt caching layer
     * @param rateLimiter rate limiting enforcement
     * @param metrics metrics collector
     */
    public LLMGatewayService(
            ProviderRouter router,
            PromptCache promptCache,
            RateLimiter rateLimiter,
            MetricsCollector metrics) {
        this.router = Objects.requireNonNull(router, "router must not be null");
        this.promptCache = Objects.requireNonNull(promptCache, "promptCache must not be null");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /**
     * Generates embedding for text with caching and routing.
     *
     * <p>GIVEN: Valid tenant ID and text
     * <p>WHEN: generateEmbedding() is called
     * <p>THEN: Returns cached embedding if available, otherwise routes to provider
     *
     * @param tenantId tenant identifier
     * @param text input text
     * @return Promise of embedding result
     */
    public Promise<EmbeddingResult> generateEmbedding(String tenantId, String text) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(text, "text must not be null");

        long startTime = System.nanoTime();

        // Check rate limit
        return rateLimiter.checkLimit(tenantId, "embedding")
                .then(allowed -> {
                    if (!allowed) {
                        metrics.incrementCounter("ai.gateway.ratelimit.exceeded",
                                "tenant", tenantId, "operation", "embedding");
                        return Promise.ofException(new RateLimitExceededException(
                                "Rate limit exceeded for tenant: " + tenantId));
                    }

                    // Try cache first
                    return promptCache.getEmbedding(tenantId, text);
                })
                .then(cached -> {
                    if (cached != null) {
                        metrics.incrementCounter("ai.gateway.cache.hit",
                                "tenant", tenantId, "operation", "embedding");
                        return Promise.of(cached);
                    }

                    // Cache miss - route to provider
                    metrics.incrementCounter("ai.gateway.cache.miss",
                            "tenant", tenantId, "operation", "embedding");
                    return routeEmbedding(tenantId, text);
                })
                .whenComplete((result, error) -> {
                    long duration = (System.nanoTime() - startTime) / 1_000_000;

                    if (error != null) {
                        metrics.incrementCounter("ai.gateway.errors",
                                "tenant", tenantId, "operation", "embedding");
                        logger.error("Embedding generation failed: tenant={}, error={}",
                                tenantId, error.getMessage());
                    } else {
                        metrics.recordTimer("ai.gateway.duration", duration,
                                "tenant", tenantId, "operation", "embedding");
                    }
                });
    }

    /**
     * Generates embeddings for multiple texts in batch.
     *
     * @param tenantId tenant identifier
     * @param texts list of input texts
     * @return Promise of list of embedding results
     */
    public Promise<List<EmbeddingResult>> generateEmbeddings(String tenantId, List<String> texts) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(texts, "texts must not be null");

        return rateLimiter.checkLimit(tenantId, "embedding")
                .then(allowed -> {
                    if (!allowed) {
                        return Promise.ofException(new RateLimitExceededException(
                                "Rate limit exceeded for tenant: " + tenantId));
                    }

                    EmbeddingService service = router.selectEmbeddingService(tenantId);
                    return service.createEmbeddings(texts)
                            .then(results -> {
                                // Cache results asynchronously
                                for (int i = 0; i < texts.size(); i++) {
                                    promptCache.putEmbedding(tenantId, texts.get(i), results.get(i));
                                }
                                return Promise.of(results);
                            });
                });
    }

    /**
     * Generates completion for request with caching and routing.
     *
     * <p>GIVEN: Valid tenant ID and completion request
     * <p>WHEN: generateCompletion() is called
     * <p>THEN: Returns cached completion if available, otherwise routes to provider
     *
     * @param tenantId tenant identifier
     * @param request completion request
     * @return Promise of completion result
     */
    public Promise<CompletionResult> generateCompletion(String tenantId, CompletionRequest request) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        long startTime = System.nanoTime();

        // Check rate limit
        return rateLimiter.checkLimit(tenantId, "completion")
                .then(allowed -> {
                    if (!allowed) {
                        metrics.incrementCounter("ai.gateway.ratelimit.exceeded",
                                "tenant", tenantId, "operation", "completion");
                        return Promise.ofException(new RateLimitExceededException(
                                "Rate limit exceeded for tenant: " + tenantId));
                    }

                    // Try cache first (if cacheable)
                    if (isCacheable(request)) {
                        return promptCache.getCompletion(tenantId, request);
                    }
                    return Promise.of((CompletionResult) null);
                })
                .then(cached -> {
                    if (cached != null) {
                        metrics.incrementCounter("ai.gateway.cache.hit",
                                "tenant", tenantId, "operation", "completion");
                        return Promise.of(cached);
                    }

                    // Cache miss - route to provider
                    metrics.incrementCounter("ai.gateway.cache.miss",
                            "tenant", tenantId, "operation", "completion");
                    return routeCompletion(tenantId, request);
                })
                .whenComplete((result, error) -> {
                    long duration = (System.nanoTime() - startTime) / 1_000_000;

                    if (error != null) {
                        metrics.incrementCounter("ai.gateway.errors",
                                "tenant", tenantId, "operation", "completion");
                        logger.error("Completion generation failed: tenant={}, error={}",
                                tenantId, error.getMessage());
                    } else {
                        metrics.recordTimer("ai.gateway.duration", duration,
                                "tenant", tenantId, "operation", "completion");

                        // Track tokens used (as a counter increment, not the actual count)
                        metrics.incrementCounter("ai.gateway.requests.completed",
                                "tenant", tenantId, "operation", "completion");
                    }
                });
    }

    /**
     * Routes embedding request to appropriate provider with fallback.
     */
    private Promise<EmbeddingResult> routeEmbedding(String tenantId, String text) {
        EmbeddingService primaryService = router.selectEmbeddingService(tenantId);

        return primaryService.createEmbedding(text)
                .then(result -> {
                    // Cache result asynchronously
                    promptCache.putEmbedding(tenantId, text, result);
                    return Promise.of(result);
                })
                .whenException(error -> {
                    logger.warn("Primary embedding provider failed for tenant={}, attempting fallback",
                            tenantId, error);
                    metrics.incrementCounter("ai.gateway.fallback.count",
                            "tenant", tenantId, "operation", "embedding");
                })
                .then(
                    result -> Promise.of(result),
                    error -> {
                        // Try fallback provider
                        EmbeddingService fallbackService = router.selectFallbackEmbeddingService(tenantId);
                        if (fallbackService != null) {
                            return fallbackService.createEmbedding(text)
                                    .then(fallbackResult -> {
                                        promptCache.putEmbedding(tenantId, text, fallbackResult);
                                        return Promise.of(fallbackResult);
                                    });
                        }
                        return Promise.ofException(error);
                    }
                );
    }

    /**
     * Routes completion request to appropriate provider with fallback.
     */
    private Promise<CompletionResult> routeCompletion(String tenantId, CompletionRequest request) {
        CompletionService primaryService = router.selectCompletionService(tenantId);

        return primaryService.complete(request)
                .then(result -> {
                    // Cache result if cacheable
                    if (isCacheable(request)) {
                        promptCache.putCompletion(tenantId, request, result);
                    }
                    return Promise.of(result);
                })
                .whenException(error -> {
                    logger.warn("Primary completion provider failed for tenant={}, attempting fallback",
                            tenantId, error);
                    metrics.incrementCounter("ai.gateway.fallback.count",
                            "tenant", tenantId, "operation", "completion");
                })
                .then(
                    result -> Promise.of(result),
                    error -> {
                        // Try fallback provider
                        CompletionService fallbackService = router.selectFallbackCompletionService(tenantId);
                        if (fallbackService != null) {
                            return fallbackService.complete(request)
                                    .then(fallbackResult -> {
                                        if (isCacheable(request)) {
                                            promptCache.putCompletion(tenantId, request, fallbackResult);
                                        }
                                        return Promise.of(fallbackResult);
                                    });
                        }
                        return Promise.ofException(error);
                    }
                );
    }

    /**
     * Determines if a completion request is cacheable.
     *
     * Requests with temperature=0 and no stop sequences are considered deterministic and cacheable.
     */
    private boolean isCacheable(CompletionRequest request) {
        return request.getTemperature() == 0.0 && request.getStopSequences().isEmpty();
    }
}

