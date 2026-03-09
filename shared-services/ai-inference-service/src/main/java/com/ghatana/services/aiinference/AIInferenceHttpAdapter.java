package com.ghatana.services.aiinference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import com.ghatana.ai.embedding.EmbeddingResult;
import com.ghatana.ai.llm.CompletionRequest;
import com.ghatana.ai.llm.CompletionResult;
import com.ghatana.aiplatform.gateway.LLMGatewayService;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.http.server.servlet.RoutingServlet;
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

/**
 * HTTP REST adapter for AI Inference Service.
 *
 * <p>
 * <b>Purpose</b><br>
 * Exposes LLM Gateway operations via REST API with JSON request/response
 * format. Provides endpoints for embeddings, completions, and health checks.
 *
 * <p>
 * <b>Endpoints</b><br>
 * - POST /ai/infer/embedding - Generate single embedding - POST
 * /ai/infer/embeddings - Generate batch embeddings - POST /ai/infer/completion
 * - Generate completion - GET /health - Service health check - GET /metrics -
 * Prometheus metrics (via observability)
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * AIInferenceHttpAdapter adapter = new AIInferenceHttpAdapter(gateway, metrics);
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

    private final LLMGatewayService gateway;
    private final MetricsCollector metrics;

    /**
     * Constructs HTTP adapter.
     *
     * @param gateway LLM gateway service
     * @param metrics metrics collector
     */
    public AIInferenceHttpAdapter(LLMGatewayService gateway, MetricsCollector metrics) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /**
     * Builds routing servlet with all endpoints.
     *
     * @return configured routing servlet
     */
    public RoutingServlet buildServlet() {
        RoutingServlet servlet = new RoutingServlet();

        // Health check
        servlet.addAsyncRoute(HttpMethod.GET, "/health", this::handleHealth);

        // Embedding endpoints
        servlet.addAsyncRoute(HttpMethod.POST, "/ai/infer/embedding", this::handleEmbedding);
        servlet.addAsyncRoute(HttpMethod.POST, "/ai/infer/embeddings", this::handleBatchEmbeddings);

        // Completion endpoints
        servlet.addAsyncRoute(HttpMethod.POST, "/ai/infer/completion", this::handleCompletion);

        // Admin endpoints
        servlet.addAsyncRoute(HttpMethod.GET, "/ai/admin/status", this::handleAdminStatus);

        return servlet;
    }

    /**
     * Handles health check requests.
     *
     * GET /health Response: {"status": "healthy", "timestamp":
     * "2025-11-16T..."}
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
     * POST /ai/infer/embedding Request: {"tenant": "tenant-123", "text": "Hello
     * world"} Response: {"vector": [...], "dimensions": 1536}
     */
    private Promise<HttpResponse> handleEmbedding(HttpRequest request) {
        return parseRequestBody(request, EmbeddingRequest.class)
                .then(req -> {
                    // Validate request
                    if (req.tenant == null || req.tenant.isEmpty()) {
                        return Promise.of(ResponseBuilder.badRequest()
                                .json(Map.of("error", "tenant is required"))
                                .build());
                    }
                    if (req.text == null || req.text.isEmpty()) {
                        return Promise.of(ResponseBuilder.badRequest()
                                .json(Map.of("error", "text is required"))
                                .build());
                    }

                    // Generate embedding
                    return gateway.generateEmbedding(req.tenant, req.text)
                            .map(result -> {
                                Map<String, Object> response = Map.of(
                                        "vector", result.getVector(),
                                        "dimensions", result.getVector().length,
                                        "model", result.getModelName() != null ? result.getModelName() : "unknown"
                                );

                                return ResponseBuilder.ok()
                                        .json(response)
                                        .build();
                            });
                })
                .whenException(error -> {
                    logger.error("Failed to generate embedding", error);
                    metrics.incrementCounter("ai.infer.http.errors", "endpoint", "embedding");
                })
                .then(
                        response -> Promise.of(response),
                        error -> Promise.of(ResponseBuilder.serverError()
                                .json(Map.of("error", error.getMessage()))
                                .build())
                );
    }

    /**
     * Handles batch embeddings generation.
     *
     * POST /ai/infer/embeddings Request: {"tenant": "tenant-123", "texts":
     * ["Hello", "World"]} Response: {"embeddings": [[...], [...]]}
     */
    private Promise<HttpResponse> handleBatchEmbeddings(HttpRequest request) {
        return parseRequestBody(request, BatchEmbeddingRequest.class)
                .then(req -> {
                    // Validate request
                    if (req.tenant == null || req.tenant.isEmpty()) {
                        return Promise.of(ResponseBuilder.badRequest()
                                .json(Map.of("error", "tenant is required"))
                                .build());
                    }
                    if (req.texts == null || req.texts.isEmpty()) {
                        return Promise.of(ResponseBuilder.badRequest()
                                .json(Map.of("error", "texts is required"))
                                .build());
                    }

                    // Generate embeddings
                    return gateway.generateEmbeddings(req.tenant, req.texts)
                            .map(results -> {
                                List<float[]> vectors = results.stream()
                                        .map(EmbeddingResult::getVector)
                                        .toList();

                                Map<String, Object> response = Map.of(
                                        "embeddings", vectors,
                                        "count", vectors.size()
                                );

                                return ResponseBuilder.ok()
                                        .json(response)
                                        .build();
                            });
                })
                .whenException(error -> {
                    logger.error("Failed to generate batch embeddings", error);
                    metrics.incrementCounter("ai.infer.http.errors", "endpoint", "embeddings");
                })
                .then(
                        response -> Promise.of(response),
                        error -> Promise.of(ResponseBuilder.serverError()
                                .json(Map.of("error", error.getMessage()))
                                .build())
                );
    }

    /**
     * Handles completion generation.
     *
     * POST /ai/infer/completion Request: {"tenant": "tenant-123", "prompt":
     * "Translate...", "maxTokens": 100} Response: {"text": "...", "tokensUsed":
     * 50, "finishReason": "stop"}
     */
    private Promise<HttpResponse> handleCompletion(HttpRequest request) {
        return parseRequestBody(request, CompletionRequestDto.class)
                .then(req -> {
                    // Validate request
                    if (req.tenant == null || req.tenant.isEmpty()) {
                        return Promise.of(ResponseBuilder.badRequest()
                                .json(Map.of("error", "tenant is required"))
                                .build());
                    }
                    if (req.prompt == null || req.prompt.isEmpty()) {
                        return Promise.of(ResponseBuilder.badRequest()
                                .json(Map.of("error", "prompt is required"))
                                .build());
                    }

                    // Build completion request
                    CompletionRequest completionRequest = CompletionRequest.builder()
                            .prompt(req.prompt)
                            .maxTokens(req.maxTokens != null ? req.maxTokens : 1000)
                            // Note: temperature defaults to 1.0 in CompletionRequest.Builder
                            // TODO: Add temperature setter method to CompletionRequest.Builder if needed
                            .build();                    // Generate completion
                    return gateway.generateCompletion(req.tenant, completionRequest)
                            .map(result -> {
                                Map<String, Object> response = Map.of(
                                        "text", result.getText(),
                                        "tokensUsed", result.getTokensUsed(),
                                        "promptTokens", result.getPromptTokens(),
                                        "completionTokens", result.getCompletionTokens(),
                                        "finishReason", result.getFinishReason() != null ? result.getFinishReason() : "unknown",
                                        "model", result.getModelUsed() != null ? result.getModelUsed() : "unknown"
                                );

                                return ResponseBuilder.ok()
                                        .json(response)
                                        .build();
                            });
                })
                .whenException(error -> {
                    logger.error("Failed to generate completion", error);
                    metrics.incrementCounter("ai.infer.http.errors", "endpoint", "completion");
                })
                .then(
                        response -> Promise.of(response),
                        error -> Promise.of(ResponseBuilder.serverError()
                                .json(Map.of("error", error.getMessage()))
                                .build())
                );
    }

    /**
     * Handles admin status requests.
     *
     * GET /ai/admin/status Response: {"gateway": "active", "providers": [...]}
     */
    private Promise<HttpResponse> handleAdminStatus(HttpRequest request) {
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
     * Parses request body as JSON.
     */
    private <T> Promise<T> parseRequestBody(HttpRequest request, Class<T> clazz) {
        try {
            String body = request.getBody().asString(StandardCharsets.UTF_8);
            T parsed = objectMapper.readValue(body, clazz);
            return Promise.of(parsed);
        } catch (Exception e) {
            return Promise.ofException(new IllegalArgumentException("Invalid JSON: " + e.getMessage()));
        }
    }

    // Request DTOs
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
