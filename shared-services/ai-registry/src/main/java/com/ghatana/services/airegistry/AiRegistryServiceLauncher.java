package com.ghatana.services.airegistry;

import com.ghatana.platform.http.server.server.HttpServerBuilder;
import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.MetricsCollectorFactory;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP REST API service for AI Model Registry.
 *
 * <p>
 * <b>Purpose</b><br>
 * Exposes ModelRegistryService over HTTP/REST with: - Model registration (POST
 * /models) - Model retrieval (GET /models/:id, GET /models?status=...) -
 * Version listing (GET /models/:name/versions) - Status updates (PUT
 * /models/:id/status)
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Start service
 * ./gradlew :products:shared-services:ai-registry:run
 *
 * // Register model
 * curl -X POST http://localhost:8080/api/v1/models \
 *   -H "Content-Type: application/json" \
 *   -d '{"tenantId":"tenant-1","name":"fraud-detector","version":"1.0.0",...}'
 *
 * // Get model
 * curl http://localhost:8080/api/v1/models/model-id
 * }</pre>
 *
 * <p>
 * <b>Architecture Role</b><br>
 * Shared service layer exposing AI Platform registry over HTTP. Integrates with
 * core/http-server for routing and observability.
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe - uses ActiveJ Eventloop for async request handling.
 *
 * @doc.type class
 * @doc.purpose HTTP REST API for model registry
 * @doc.layer product
 * @doc.pattern Service
 */
public class AiRegistryServiceLauncher {

    private static final int PORT = 8080;
    private static final ConcurrentHashMap<String, ModelRecord> modelStore = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        // Initialize metrics
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        MetricsCollector metrics = MetricsCollectorFactory.create(meterRegistry);

        // Initialize model registry — in-memory store for bootstrapping
        // Production: replace with ModelRegistryService backed by persistent storage
        System.out.println("Using in-memory model registry (no database configured)");

        // Build HTTP server using core abstractions
        try {
            HttpServerBuilder.create()
                    .withPort(PORT)
                    .addAsyncRoute(HttpMethod.GET, "/health", AiRegistryServiceLauncher::handleHealth)
                    .addAsyncRoute(HttpMethod.GET, "/api/v1/models", req -> handleModels(req, metrics))
                    .addAsyncRoute(HttpMethod.GET, "/api/v1/models/:id", req -> handleModelById(req, metrics))
                    .build()
                    .listen();

            System.out.println("AI Registry Service started on port " + PORT);
        } catch (IOException e) {
            System.err.println("Failed to start AI Registry Service: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Health check endpoint.
     *
     * GIVEN: HTTP GET /health WHEN: Request is received THEN: Returns 200 OK
     * with status
     */
    private static Promise<HttpResponse> handleHealth(HttpRequest request) {
        return Promise.of(
                ResponseBuilder.ok()
                        .json(new HealthResponse("healthy"))
                        .build()
        );
    }

    /**
     * Models collection endpoint.
     *
     * GIVEN: HTTP GET /api/v1/models
     * WHEN: Request is received
     * THEN: Returns all registered models from the in-memory store
     */
    private static Promise<HttpResponse> handleModels(
            HttpRequest request,
            MetricsCollector metrics
    ) {
        metrics.incrementCounter("ai_registry.models.list");
        List<ModelRecord> models = List.copyOf(modelStore.values());
        return Promise.of(
                ResponseBuilder.ok()
                        .json(new ModelsResponse(models, models.size()))
                        .build()
        );
    }

    /**
     * Individual model endpoint.
     *
     * GIVEN: HTTP GET /api/v1/models/:id
     * WHEN: Request is received with a model ID path parameter
     * THEN: Returns model metadata if found, or 404 if not
     */
    private static Promise<HttpResponse> handleModelById(
            HttpRequest request,
            MetricsCollector metrics
    ) {
        String modelId = request.getPathParameter("id");
        metrics.incrementCounter("ai_registry.models.get");

        ModelRecord model = modelStore.get(modelId);
        if (model == null) {
            return Promise.of(
                    ResponseBuilder.notFound()
                            .json(new ErrorResponse("Model not found: " + modelId))
                            .build()
            );
        }
        return Promise.of(
                ResponseBuilder.ok()
                        .json(model)
                        .build()
        );
    }

    // Response DTOs
    private record HealthResponse(String status) {
    }

    private record ModelsResponse(List<ModelRecord> models, int total) {
    }

    private record ApiResponse(String message) {
    }

    private record ErrorResponse(String error) {
    }

    /** In-memory model record. Production: replace with ModelRegistryService-backed entity. */
    private record ModelRecord(String id, String tenantId, String name, String version, String status) {
    }
}
