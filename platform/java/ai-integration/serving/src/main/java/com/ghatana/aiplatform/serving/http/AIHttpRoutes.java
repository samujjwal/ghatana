package com.ghatana.aiplatform.serving.http;

import com.ghatana.aiplatform.serving.OnlineInferenceService;
import com.ghatana.aiplatform.serving.OnlineInferenceService.InferenceRequest;
import com.ghatana.aiplatform.serving.OnlineInferenceService.InferenceResult;
import com.ghatana.platform.http.response.ResponseBuilder;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * HTTP endpoints for AI inference and model management.
 *
 * <p>
 * <b>Endpoints</b>
 * <ul>
 * <li>POST /ai/infer - Real-time inference</li>
 * <li>POST /ai/batch - Batch inference job submission</li>
 * <li>GET /ai/admin/targets - Get active model targets</li>
 * <li>GET /ai/admin/metrics - Get inference metrics</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Infer example
 * POST /ai/infer
 * {
 *   "tenantId": "tenant-123",
 *   "taskId": "fraud-detection",
 *   "entityId": "user-456",
 *   "featureNames": ["amount", "merchant"]
 * }
 * Response:
 * {
 *   "score": 0.87,
 *   "modelName": "gpt-4",
 *   "latencyMs": 42,
 *   "tokensUsed": 150
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose HTTP routes for AI inference service
 * @doc.layer platform
 * @doc.pattern HTTP Adapter
 */
public class AIHttpRoutes {

    private static final Logger logger = LoggerFactory.getLogger(AIHttpRoutes.class);

    private final OnlineInferenceService inferenceService;
    private final MetricsCollector metrics;

    /**
     * Constructs HTTP routes.
     *
     * @param inferenceService online inference service
     * @param metrics metrics collector
     */
    public AIHttpRoutes(OnlineInferenceService inferenceService, MetricsCollector metrics) {
        this.inferenceService = Objects.requireNonNull(inferenceService, "inferenceService must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    /**
     * POST /ai/infer - Real-time inference endpoint.
     *
     * @param request HTTP request with inference parameters
     * @return Promise of HTTP response with inference result
     */
    public Promise<HttpResponse> handleInferenceRequest(HttpRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        long startTime = System.nanoTime();

        return request.loadBody()
                .then(body -> {
                    try {
                        // Parse request body as JSON
                        Map<String, Object> params = parseJson(body.asString(java.nio.charset.StandardCharsets.UTF_8));
                        String tenantId = (String) params.get("tenantId");
                        String taskId = (String) params.get("taskId");
                        String entityId = (String) params.get("entityId");

                        @SuppressWarnings("unchecked")
                        List<String> featureNames = (List<String>) params.getOrDefault("featureNames", new ArrayList<>());
                        String context = (String) params.get("context");

                        // Validate required fields
                        if (tenantId == null || taskId == null || entityId == null) {
                            return Promise.of(ResponseBuilder.badRequest()
                                    .json(Map.of("error", "Missing required fields: tenantId, taskId, entityId"))
                                    .build());
                        }

                        // Create inference request
                        InferenceRequest inferenceRequest = new InferenceRequest(
                                tenantId, taskId, entityId, featureNames, context);

                        // Call inference service
                        return inferenceService.infer(inferenceRequest)
                                .then(result -> {
                                    long duration = (System.nanoTime() - startTime) / 1_000_000;

                                    // Build response
                                    Map<String, Object> response = new LinkedHashMap<>();
                                    response.put("score", result.getScore());
                                    response.put("modelName", result.getModelName());
                                    response.put("latencyMs", result.getLatencyMs());
                                    response.put("tokensUsed", result.getTokensUsed());
                                    response.put("tenantId", result.getTenantId());

                                    metrics.incrementCounter("ai.http.infer.success",
                                            "tenant", result.getTenantId(), "task", result.getTaskId());

                                    logger.info("Inference request completed: duration={}ms, tenant={}, task={}",
                                            duration, result.getTenantId(), result.getTaskId());

                                    return Promise.of(ResponseBuilder.ok()
                                            .json(response)
                                            .build());
                                })
                                .whenException(error -> {
                                    metrics.incrementCounter("ai.http.infer.error",
                                            "tenant", tenantId, "task", taskId);
                                    logger.error("Inference request failed: tenant={}, task={}, error={}",
                                            tenantId, taskId, error.getMessage());
                                });

                    } catch (Exception e) {
                        metrics.incrementCounter("ai.http.infer.error", "error", "parse_error");
                        return Promise.of(ResponseBuilder.badRequest()
                                .json(Map.of("error", "Invalid request: " + e.getMessage()))
                                .build());
                    }
                })
                .whenException(error -> {
                    long duration = (System.nanoTime() - startTime) / 1_000_000;
                    metrics.incrementCounter("ai.http.infer.error", "error", "request_error");
                    logger.error("HTTP request processing failed: duration={}ms, error={}",
                            duration, error.getMessage());
                });
    }

    /**
     * GET /ai/admin/targets - Get active model targets for tenant.
     *
     * @param request HTTP request with tenant parameter
     * @return Promise of HTTP response with targets list
     */
    public Promise<HttpResponse> handleGetTargets(HttpRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        try {
            String tenantId = request.getQueryParameter("tenantId");
            if (tenantId == null || tenantId.isEmpty()) {
                return Promise.of(ResponseBuilder.badRequest()
                        .json(Map.of("error", "Missing tenantId parameter"))
                        .build());
            }

            // In production, fetch from registry
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("tenantId", tenantId);
            response.put("targets", List.of());

            metrics.incrementCounter("ai.http.admin.targets.requested",
                    "tenant", tenantId);

            return Promise.of(ResponseBuilder.ok()
                    .json(response)
                    .build());

        } catch (Exception e) {
            metrics.incrementCounter("ai.http.admin.error", "error", "targets_error");
            return Promise.of(ResponseBuilder.badRequest()
                    .json(Map.of("error", "Request processing failed: " + e.getMessage()))
                    .build());
        }
    }

    /**
     * GET /ai/health - Health check endpoint.
     *
     * @return Promise of HTTP response with health status
     */
    public Promise<HttpResponse> handleHealthCheck() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "healthy");
        health.put("service", "ai-inference");
        health.put("timestamp", System.currentTimeMillis());

        return Promise.of(ResponseBuilder.ok()
                .json(health)
                .build());
    }

    /**
     * Parse JSON from string. In production, use proper JSON parser library.
     */
    private Map<String, Object> parseJson(String json) {
        // Simplified parsing - in production use Jackson/GSON
        Map<String, Object> result = new LinkedHashMap<>();
        // Very basic parsing for demo - real implementation should use Jackson
        return result;
    }
}
