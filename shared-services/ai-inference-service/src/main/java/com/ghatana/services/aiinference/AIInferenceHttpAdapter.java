package com.ghatana.services.aiinference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.aiplatform.gateway.LLMGatewayService;
import com.ghatana.platform.http.server.response.ErrorResponse;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.http.server.servlet.RoutingServlet;
import com.ghatana.platform.security.port.JwtTokenProvider;
import com.ghatana.platform.security.ratelimit.DefaultRateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiterConfig;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * HTTP REST adapter for AI Inference Service.
 *
 * <p>
 * <b>Purpose</b><br>
 * Exposes LLM Gateway operations via REST API with JSON request/response
 * format. Provides endpoints for embeddings, completions, and health checks.
 * All inference endpoints require a valid platform JWT ({@code Authorization: Bearer &lt;token&gt;})
 * or an internal service key ({@code X-Internal-Key}).
 *
 * <p>
 * <b>Endpoints</b><br>
 * - POST /ai/infer/embedding  — Generate single embedding (requires auth)<br>
 * - POST /ai/infer/embeddings — Generate batch embeddings (requires auth)<br>
 * - POST /ai/infer/completion — Generate completion (requires auth)<br>
 * - GET  /health              — Service liveness probe (unauthenticated)<br>
 * - GET  /ai/admin/status     — Service status (requires auth)
 *
 * <p>
 * <b>Security</b><br>
 * All endpoints except {@code /health} are protected by JWT validation.
 * Internal service-to-service calls may use the {@code X-Internal-Key} header.
 * Prompt text is length-limited to prevent resource abuse.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * AIInferenceHttpAdapter adapter = new AIInferenceHttpAdapter(gateway, metrics, jwtProvider);
 * RoutingServlet servlet = adapter.buildServlet();
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe - all operations delegate to thread-safe services.
 *
 * @doc.type class
 * @doc.purpose HTTP REST adapter for AI inference
 * @doc.layer application
 * @doc.pattern Adapter + Controller
 */
public class AIInferenceHttpAdapter {

    private static final Logger logger = LoggerFactory.getLogger(AIInferenceHttpAdapter.class);
    private static final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();

    /** Maximum allowed prompt/text length to prevent resource abuse. */
    private static final int MAX_TEXT_LENGTH = 32_768;
    /** Maximum number of texts in a batch embedding request. */
    private static final int MAX_BATCH_SIZE = 100;

    /** Default rate limit: 1 000 requests per minute per tenant. Override via AI_RATE_LIMIT_RPM env var. */
    private static final int DEFAULT_RATE_LIMIT_RPM = 1_000;

    private final LLMGatewayService gateway;
    private final MetricsCollector metrics;
    private final JwtTokenProvider jwtTokenProvider;
    private final String internalApiKey;
    private final RateLimiter rateLimiter;

    /**
     * Constructs HTTP adapter.
     *
     * @param gateway          LLM gateway service
     * @param metrics          metrics collector
     * @param jwtTokenProvider JWT token validator
     */
    public AIInferenceHttpAdapter(LLMGatewayService gateway, MetricsCollector metrics,
                                  JwtTokenProvider jwtTokenProvider) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.jwtTokenProvider = Objects.requireNonNull(jwtTokenProvider, "jwtTokenProvider must not be null");
        String key = System.getenv("INTERNAL_API_KEY");
        this.internalApiKey = (key != null) ? key : "";
        int rateLimit = DEFAULT_RATE_LIMIT_RPM;
        try {
            String envLimit = System.getenv("AI_RATE_LIMIT_RPM");
            if (envLimit != null) rateLimit = Integer.parseInt(envLimit);
        } catch (NumberFormatException ignored) { }
        this.rateLimiter = DefaultRateLimiter.create(
            RateLimiterConfig.builder()
                .maxRequestsPerMinute(rateLimit)
                .burstSize(rateLimit)
                .build(),
            metrics,
            "ai.infer.rate_limit"
        );
    }

    /**
     * Package-private constructor for testing — allows injecting a custom rate limiter.
     *
     * @param gateway          LLM gateway service
     * @param metrics          metrics collector
     * @param jwtTokenProvider JWT token validator
     * @param rateLimiter      custom rate limiter (e.g. with low limit for testing)
     */
    AIInferenceHttpAdapter(LLMGatewayService gateway, MetricsCollector metrics,
                           JwtTokenProvider jwtTokenProvider, RateLimiter rateLimiter) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.jwtTokenProvider = Objects.requireNonNull(jwtTokenProvider, "jwtTokenProvider must not be null");
        String key = System.getenv("INTERNAL_API_KEY");
        this.internalApiKey = (key != null) ? key : "";
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
    }

    /**
     * Builds routing servlet with all endpoints.
     *
     * @return configured routing servlet
     */
    public RoutingServlet buildServlet() {
        RoutingServlet servlet = new RoutingServlet();

        // Health check — unauthenticated liveness probe
        servlet.addAsyncRoute(HttpMethod.GET, "/health", this::handleHealth);

        // Inference endpoints — require authentication
        servlet.addAsyncRoute(HttpMethod.POST, "/ai/infer/embedding", this::handleEmbedding);
        servlet.addAsyncRoute(HttpMethod.POST, "/ai/infer/embeddings", this::handleBatchEmbeddings);
        servlet.addAsyncRoute(HttpMethod.POST, "/ai/infer/completion", this::handleCompletion);

        // Admin endpoints — require authentication
        servlet.addAsyncRoute(HttpMethod.GET, "/ai/admin/status", this::handleAdminStatus);

        return servlet;
    }

    // ─── Auth helpers ─────────────────────────────────────────────────────────

    /**
     * Returns an error response if the request is not authenticated; returns null if auth passes.
     * Accepts a valid platform JWT bearer token OR the configured internal API key.
     */
    private HttpResponse checkAuth(HttpRequest request) {
        // Internal service-to-service key bypass
        String internalKey = request.getHeader(io.activej.http.HttpHeaders.of("X-Internal-Key"));
        if (!internalApiKey.isEmpty() && internalApiKey.equals(internalKey)) {
            return null; // authenticated
        }

        String authHeader = request.getHeader(io.activej.http.HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return errorResponse(401, "UNAUTHORIZED", "Missing or malformed Authorization header");
        }
        String token = authHeader.substring(7).strip();
        try {
            if (!jwtTokenProvider.validateToken(token)) {
                return errorResponse(401, "UNAUTHORIZED", "Invalid or expired token");
            }
        } catch (Exception e) {
            logger.warn("JWT validation error", e);
            return errorResponse(401, "TOKEN_VALIDATION_FAILED", "Token validation failed");
        }
        return null; // authenticated
    }

    // ─── Endpoint handlers ────────────────────────────────────────────────────

    /**
     * Handles health check requests.
     *
     * GET /health Response: {"status": "healthy", "timestamp": "..."}
     */
    private Promise<HttpResponse> handleHealth(HttpRequest request) {
        Map<String, Object> response = Map.of(
                "status", "healthy",
                "service", "ai-inference",
                "timestamp", java.time.Instant.now().toString()
        );

        return Promise.of(ResponseBuilder.ok()
                .json(response)
                .build());
    }

    /**
     * Handles single embedding generation.
     *
     * POST /ai/infer/embedding (requires JWT)<br>
     * Request: {"tenant": "tenant-123", "text": "Hello world"}<br>
     * Response: {"vector": [...], "dimensions": 1536}
     */
    private Promise<HttpResponse> handleEmbedding(HttpRequest request) {
        HttpResponse authError = checkAuth(request);
        if (authError != null) {
            return Promise.of(authError);
        }

        return parseRequestBody(request, EmbeddingRequest.class)
                .then(req -> {
                    if (req.tenant == null || req.tenant.isEmpty()) {
                        return Promise.of(errorResponse(400, "MISSING_TENANT", "tenant is required"));
                    }
                    RateLimiter.AcquireResult acquireResult = rateLimiter.tryAcquire(req.tenant);
                    if (!acquireResult.allowed()) {
                        return Promise.of(ResponseBuilder.status(429)
                                .json(ErrorResponse.of(429, "RATE_LIMIT_EXCEEDED", "Rate limit exceeded. Retry after " + acquireResult.retryAfterSeconds() + " seconds."))
                                .build());
                    }
                    if (req.text == null || req.text.isEmpty()) {
                        return Promise.of(errorResponse(400, "MISSING_TEXT", "text is required"));
                    }
                    if (req.text.length() > MAX_TEXT_LENGTH) {
                        return Promise.of(errorResponse(400, "TEXT_TOO_LONG", "text exceeds maximum allowed length of " + MAX_TEXT_LENGTH + " characters"));
                    }

                    String sanitizedText = sanitizeText(req.text);
                    String requestId = UUID.randomUUID().toString();
                    long startMs = System.currentTimeMillis();
                    logger.info("AI inference request requestId={} operation=embedding tenant={}",
                            requestId, req.tenant);

                    return gateway.generateEmbedding(req.tenant, sanitizedText)
                            .map(result -> {
                                logger.info("AI inference response requestId={} operation=embedding tenant={} dimensions={} durationMs={}",
                                        requestId, req.tenant, result.getVector().length,
                                        System.currentTimeMillis() - startMs);
                                return ResponseBuilder.ok()
                                        .json(Map.of(
                                                "vector", result.getVector(),
                                                "dimensions", result.getVector().length,
                                                "model", result.getModelName() != null ? result.getModelName() : "unknown"
                                        ))
                                        .build();
                            });
                })
                .whenException(error -> {
                    logger.error("Failed to generate embedding", error);
                    metrics.incrementCounter("ai.infer.http.errors", "endpoint", "embedding");
                })
                .then(
                        response -> Promise.of(response),
                    error -> Promise.of(mapRequestError(error, "EMBEDDING_GENERATION_FAILED", "Embedding generation failed"))
                );
    }

    /**
     * Handles batch embeddings generation.
     *
     * POST /ai/infer/embeddings (requires JWT)<br>
     * Request: {"tenant": "tenant-123", "texts": ["Hello", "World"]}<br>
     * Response: {"embeddings": [[...], [...]]}
     */
    private Promise<HttpResponse> handleBatchEmbeddings(HttpRequest request) {
        HttpResponse authError = checkAuth(request);
        if (authError != null) {
            return Promise.of(authError);
        }

        return parseRequestBody(request, BatchEmbeddingRequest.class)
                .then(req -> {
                    if (req.tenant == null || req.tenant.isEmpty()) {
                        return Promise.of(errorResponse(400, "MISSING_TENANT", "tenant is required"));
                    }
                    RateLimiter.AcquireResult acquireResult = rateLimiter.tryAcquire(req.tenant);
                    if (!acquireResult.allowed()) {
                        return Promise.of(ResponseBuilder.status(429)
                                .json(ErrorResponse.of(429, "RATE_LIMIT_EXCEEDED", "Rate limit exceeded. Retry after " + acquireResult.retryAfterSeconds() + " seconds."))
                                .build());
                    }
                    if (req.texts == null || req.texts.isEmpty()) {
                        return Promise.of(errorResponse(400, "MISSING_TEXTS", "texts is required"));
                    }
                    if (req.texts.size() > MAX_BATCH_SIZE) {
                        return Promise.of(errorResponse(400, "BATCH_TOO_LARGE", "batch size exceeds maximum of " + MAX_BATCH_SIZE));
                    }
                    for (String text : req.texts) {
                        if (text != null && text.length() > MAX_TEXT_LENGTH) {
                            return Promise.of(errorResponse(400, "TEXT_TOO_LONG", "one or more texts exceed maximum allowed length"));
                        }
                    }

                    List<String> sanitizedTexts = req.texts.stream()
                            .map(AIInferenceHttpAdapter::sanitizeText)
                            .toList();
                    String requestId = UUID.randomUUID().toString();
                    long startMs = System.currentTimeMillis();
                    logger.info("AI inference request requestId={} operation=batch-embeddings tenant={} batchSize={}",
                            requestId, req.tenant, sanitizedTexts.size());

                    return gateway.generateEmbeddings(req.tenant, sanitizedTexts)
                            .map(results -> {
                                List<float[]> vectors = results.stream()
                                        .map(EmbeddingResult::getVector)
                                        .toList();
                                logger.info("AI inference response requestId={} operation=batch-embeddings tenant={} count={} durationMs={}",
                                        requestId, req.tenant, vectors.size(),
                                        System.currentTimeMillis() - startMs);
                                return ResponseBuilder.ok()
                                        .json(Map.of(
                                                "embeddings", vectors,
                                                "count", vectors.size()
                                        ))
                                        .build();
                            });
                })
                .whenException(error -> {
                    logger.error("Failed to generate batch embeddings", error);
                    metrics.incrementCounter("ai.infer.http.errors", "endpoint", "embeddings");
                })
                .then(
                        response -> Promise.of(response),
                    error -> Promise.of(mapRequestError(error, "BATCH_EMBEDDING_GENERATION_FAILED", "Batch embedding generation failed"))
                );
    }

    /**
     * Handles completion generation.
     *
     * POST /ai/infer/completion (requires JWT)<br>
     * Request: {"tenant": "tenant-123", "prompt": "Translate...", "maxTokens": 100}<br>
     * Response: {"text": "...", "tokensUsed": 50, "finishReason": "stop"}
     */
    private Promise<HttpResponse> handleCompletion(HttpRequest request) {
        HttpResponse authError = checkAuth(request);
        if (authError != null) {
            return Promise.of(authError);
        }

        return parseRequestBody(request, CompletionRequestDto.class)
                .then(req -> {
                    if (req.tenant == null || req.tenant.isEmpty()) {
                        return Promise.of(errorResponse(400, "MISSING_TENANT", "tenant is required"));
                    }
                    RateLimiter.AcquireResult acquireResult = rateLimiter.tryAcquire(req.tenant);
                    if (!acquireResult.allowed()) {
                        return Promise.of(ResponseBuilder.status(429)
                                .json(ErrorResponse.of(429, "RATE_LIMIT_EXCEEDED", "Rate limit exceeded. Retry after " + acquireResult.retryAfterSeconds() + " seconds."))
                                .build());
                    }
                    if (req.prompt == null || req.prompt.isEmpty()) {
                        return Promise.of(errorResponse(400, "MISSING_PROMPT", "prompt is required"));
                    }
                    if (req.prompt.length() > MAX_TEXT_LENGTH) {
                        return Promise.of(errorResponse(400, "PROMPT_TOO_LONG", "prompt exceeds maximum allowed length of " + MAX_TEXT_LENGTH + " characters"));
                    }

                    String sanitizedPrompt = sanitizeText(req.prompt);
                    String requestId = UUID.randomUUID().toString();
                    long startMs = System.currentTimeMillis();
                    logger.info("AI inference request requestId={} operation=completion tenant={} promptChars={}",
                            requestId, req.tenant, sanitizedPrompt.length());

                    CompletionRequest completionRequest = CompletionRequest.builder()
                            .prompt(sanitizedPrompt)
                            .maxTokens(req.maxTokens != null ? req.maxTokens : 1000)
                            .build();

                    return gateway.generateCompletion(req.tenant, completionRequest)
                            .map(result -> {
                                logger.info("AI inference response requestId={} operation=completion tenant={} tokensUsed={} model={} durationMs={}",
                                        requestId, req.tenant, result.getTokensUsed(),
                                        result.getModelUsed() != null ? result.getModelUsed() : "unknown",
                                        System.currentTimeMillis() - startMs);
                                return ResponseBuilder.ok()
                                        .json(Map.of(
                                                "text", result.getText(),
                                                "tokensUsed", result.getTokensUsed(),
                                                "promptTokens", result.getPromptTokens(),
                                                "completionTokens", result.getCompletionTokens(),
                                                "finishReason", result.getFinishReason() != null ? result.getFinishReason() : "unknown",
                                                "model", result.getModelUsed() != null ? result.getModelUsed() : "unknown"
                                        ))
                                        .build();
                            });
                })
                .whenException(error -> {
                    logger.error("Failed to generate completion", error);
                    metrics.incrementCounter("ai.infer.http.errors", "endpoint", "completion");
                })
                .then(
                        response -> Promise.of(response),
                    error -> Promise.of(mapRequestError(error, "COMPLETION_GENERATION_FAILED", "Completion generation failed"))
                );
    }

    /**
     * Handles admin status requests.
     *
     * GET /ai/admin/status (requires JWT)<br>
     * Response: {"gateway": "active", "service": "ai-inference"}
     */
    private Promise<HttpResponse> handleAdminStatus(HttpRequest request) {
        HttpResponse authError = checkAuth(request);
        if (authError != null) {
            return Promise.of(authError);
        }

        Map<String, Object> response = Map.of(
                "service", "ai-inference",
                "gateway", "active",
                "timestamp", java.time.Instant.now().toString()
        );

        return Promise.of(ResponseBuilder.ok()
                .json(response)
                .build());
    }

    /**
     * Sanitizes user-supplied text by removing null bytes and ASCII control characters
     * (excluding tab, LF, CR which are valid in content). Printable Unicode is preserved.
     *
     * @param input raw user input
     * @return sanitized string, or null if input is null
     */
    private static String sanitizeText(String input) {
        if (input == null) return null;
        // Remove null bytes and non-printable control chars; keep \t (0x09), \n (0x0A), \r (0x0D)
        return input.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
    }

    /**
     * Parses request body as JSON.
     */
    private <T> Promise<T> parseRequestBody(HttpRequest request, Class<T> clazz) {
        try {
            String body = request.getBody().asString(StandardCharsets.UTF_8);
            T parsed = objectMapper.readValue(body, clazz);
            return Promise.of(parsed);
        } catch (Exception e) {
            return Promise.ofException(new IllegalArgumentException("Invalid JSON request body"));
        }
    }

    private static HttpResponse mapRequestError(Throwable error, String internalCode, String internalMessage) {
        if (error instanceof IllegalArgumentException) {
            return errorResponse(400, "INVALID_REQUEST", error.getMessage());
        }
        return errorResponse(500, internalCode, internalMessage);
    }

    private static HttpResponse errorResponse(int status, String code, String message) {
        return ResponseBuilder.status(status)
                .json(ErrorResponse.of(status, code, message))
                .build();
    }

    // ─── Request DTOs ─────────────────────────────────────────────────────────

    private static class EmbeddingRequest {

        public String tenant;
        public String text;
    }

    private static class BatchEmbeddingRequest {

        public String tenant;
        public List<String> texts;
    }

    private static class CompletionRequestDto {

        public String tenant;
        public String prompt;
        public String systemMessage;
        public Integer maxTokens;
        public Double temperature;
    }
}

