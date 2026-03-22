package com.ghatana.ai.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.ai.embedding.EmbeddingService;
import com.ghatana.ai.vectorstore.VectorStore;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.util.HashMap;
import java.util.Map;

/**
 * ActiveJ HTTP adapter for AI services (embeddings, vector search).
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides HTTP endpoints for embedding generation and semantic search using
 * ActiveJ HTTP framework (NOT Spring Framework). All operations return ActiveJ
 * Promise for non-blocking async execution.
 *
 * <p>
 * <b>Architecture Role</b><br>
 * HTTP adapter layer for AI integration module. Bridges HTTP requests to domain
 * services (EmbeddingService, VectorStore) using ActiveJ Promise-based async
 * operations.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * // Create adapter
 * AIHttpAdapter adapter = new AIHttpAdapter(
 *     embeddingService,
 *     vectorStore,
 *     objectMapper,
 *     metricsCollector
 * );
 *
 * // Register routes
 * RoutingServlet servlet = RoutingServlet.create()
 *     .map(POST, "/api/embeddings", adapter::handleCreateEmbedding)
 *     .map(POST, "/api/search", adapter::handleSemanticSearch)
 *     .map(GET, "/api/embeddings/:id", adapter::handleGetEmbedding);
 *
 * // Start server
 * HttpServer.create(eventloop, servlet).listen(8080);
 * }</pre>
 *
 * <p>
 * <b>Endpoints</b><br>
 * <ul>
 * <li>POST /api/embeddings - Generate embedding for text</li>
 * <li>POST /api/search - Semantic search by query text</li>
 * <li>GET /api/embeddings/:id - Retrieve embedding by ID</li>
 * <li>DELETE /api/embeddings/:id - Delete embedding</li>
 * </ul>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe (all operations delegated to thread-safe services). Must be used
 * within ActiveJ Eventloop for correct Promise execution.
 *
 * @see EmbeddingService
 * @see VectorStore
 * @doc.type class
 * @doc.purpose ActiveJ HTTP adapter for AI services
 * @doc.layer infrastructure
 * @doc.pattern Adapter
 */
public final class AIHttpAdapter {

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;
    private final MetricsCollector metricsCollector;

    /**
     * Creates HTTP adapter for AI services.
     *
     * @param embeddingService service for generating embeddings
     * @param vectorStore vector storage backend
     * @param objectMapper JSON serializer/deserializer
     * @param metricsCollector metrics collector for observability
     */
    public AIHttpAdapter(
            EmbeddingService embeddingService,
            VectorStore vectorStore,
            ObjectMapper objectMapper,
            MetricsCollector metricsCollector
    ) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.objectMapper = objectMapper;
        this.metricsCollector = metricsCollector;
    }

    /**
     * Handles POST /api/embeddings - Generate embedding for text.
     *
     * <p>
     * Request Body:
     * <pre>{@code
     * {
     *   "text": "sample text to embed",
     *   "id": "optional-id",
     *   "store": true
     * }
     * }</pre>
     *
     * <p>
     * Response:
     * <pre>{@code
     * {
     *   "id": "embedding-id",
     *   "vector": [0.123, -0.456, ...],
     *   "model": "text-embedding-ada-002",
     *   "dimensions": 1536
     * }
     * }</pre>
     *
     * @param request HTTP request with JSON body
     * @return Promise of HTTP response with embedding result
     */
    public Promise<HttpResponse> handleCreateEmbedding(HttpRequest request) {
        long startTime = System.currentTimeMillis();

        return request.loadBody()
                .then(body -> {
                    try {
                        // Parse request
                        Map<String, Object> req = objectMapper.readValue(
                                body.getArray(),
                                Map.class
                        );

                        String text = (String) req.get("text");
                        String id = (String) req.getOrDefault("id", generateId());
                        boolean store = (boolean) req.getOrDefault("store", false);

                        if (text == null || text.isEmpty()) {
                            return Promise.of(errorResponse(400, "Missing required field: text"));
                        }

                        // Generate embedding
                        return embeddingService.createEmbedding(text)
                                .then(embedding -> {
                                    // Store if requested
                                    if (store) {
                                        Map<String, String> metadata = new HashMap<>();
                                        metadata.put("originalText", text);
                                        metadata.put("createdAt", String.valueOf(System.currentTimeMillis()));

                                        return vectorStore.store(id, text, embedding.getVector(), metadata)
                                                .map(v -> embedding);
                                    } else {
                                        return Promise.of(embedding);
                                    }
                                })
                                .map(embedding -> {
                                    // Build response
                                    Map<String, Object> response = new HashMap<>();
                                    response.put("id", id);
                                    response.put("vector", embedding.getVector());
                                    response.put("model", embedding.getModel());
                                    // dimensions = vector length
                                    response.put("dimensions", embedding.getVector().length);

                                    return jsonResponse(200, response);
                                });

                    } catch (Exception e) {
                        return Promise.of(errorResponse(400, "Invalid request: " + e.getMessage()));
                    }
                })
                .whenComplete((response, error) -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metricsCollector.recordTimer("ai.http.create_embedding", duration,
                            "status", error == null ? "success" : "error");
                    metricsCollector.incrementCounter("ai.http.requests",
                            "endpoint", "create_embedding",
                            "status", error == null ? "success" : "error");
                })
                .then(
                        response -> Promise.of(response),
                        error -> Promise.of(errorResponse(500, "Internal error: " + error.getMessage()))
                );
    }

    /**
     * Handles POST /api/search - Semantic search by query text.
     *
     * <p>
     * Request Body:
     * <pre>{@code
     * {
     *   "query": "search query text",
     *   "limit": 10,
     *   "threshold": 0.7
     * }
     * }</pre>
     *
     * <p>
     * Response:
     * <pre>{@code
     * {
     *   "results": [
     *     {
     *       "id": "result-1",
     *       "content": "matching content",
     *       "similarity": 0.92,
     *       "metadata": {...}
     *     }
     *   ],
     *   "count": 5
     * }
     * }</pre>
     *
     * @param request HTTP request with JSON body
     * @return Promise of HTTP response with search results
     */
    public Promise<HttpResponse> handleSemanticSearch(HttpRequest request) {
        long startTime = System.currentTimeMillis();

        return request.loadBody()
                .then(body -> {
                    try {
                        // Parse request
                        Map<String, Object> req = objectMapper.readValue(
                                body.getArray(),
                                Map.class
                        );

                        String query = (String) req.get("query");
                        int limit = (int) req.getOrDefault("limit", 10);
                        double threshold = (double) req.getOrDefault("threshold", 0.7);

                        if (query == null || query.isEmpty()) {
                            return Promise.of(errorResponse(400, "Missing required field: query"));
                        }

                        // Generate embedding for query
                        return embeddingService.createEmbedding(query)
                                .then(embedding
                                        -> // Search vector store
                                        vectorStore.search(embedding.getVector(), limit, threshold)
                                )
                                .map(results -> {
                                    // Build response
                                    Map<String, Object> response = new HashMap<>();
                                    response.put("results", results);
                                    response.put("count", results.size());

                                    return jsonResponse(200, response);
                                });

                    } catch (Exception e) {
                        return Promise.of(errorResponse(400, "Invalid request: " + e.getMessage()));
                    }
                })
                .whenComplete((response, error) -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metricsCollector.recordTimer("ai.http.semantic_search", duration,
                            "status", error == null ? "success" : "error");
                    metricsCollector.incrementCounter("ai.http.requests",
                            "endpoint", "semantic_search",
                            "status", error == null ? "success" : "error");
                })
                .then(
                        response -> Promise.of(response),
                        error -> Promise.of(errorResponse(500, "Internal error: " + error.getMessage()))
                );
    }

    /**
     * Handles GET /api/embeddings/:id - Retrieve embedding by ID.
     *
     * @param request HTTP request with ID path parameter
     * @return Promise of HTTP response with embedding data
     */
    public Promise<HttpResponse> handleGetEmbedding(HttpRequest request) {
        long startTime = System.currentTimeMillis();

        String id = request.getPathParameter("id");
        if (id == null || id.isEmpty()) {
            return Promise.of(errorResponse(400, "Missing required parameter: id"));
        }

        return vectorStore.getById(id)
                .map(result -> {
                    if (result == null) {
                        return errorResponse(404, "Embedding not found: " + id);
                    }

                    Map<String, Object> response = new HashMap<>();
                    response.put("id", result.getId());
                    response.put("content", result.getContent());
                    response.put("vector", result.getVector());
                    response.put("similarity", result.getSimilarity());
                    response.put("rank", result.getRank());  // Use getRank() instead of getMetadata()

                    return jsonResponse(200, response);
                })
                .whenComplete((response, error) -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metricsCollector.recordTimer("ai.http.get_embedding", duration,
                            "status", error == null ? "success" : "error");
                    metricsCollector.incrementCounter("ai.http.requests",
                            "endpoint", "get_embedding",
                            "status", error == null ? "success" : "error");
                })
                .then(
                        response -> Promise.of(response),
                        error -> Promise.of(errorResponse(500, "Internal error: " + error.getMessage()))
                );
    }

    /**
     * Handles DELETE /api/embeddings/:id - Delete embedding.
     *
     * @param request HTTP request with ID path parameter
     * @return Promise of HTTP response confirming deletion
     */
    public Promise<HttpResponse> handleDeleteEmbedding(HttpRequest request) {
        long startTime = System.currentTimeMillis();

        String id = request.getPathParameter("id");
        if (id == null || id.isEmpty()) {
            return Promise.of(errorResponse(400, "Missing required parameter: id"));
        }

        return vectorStore.delete(id)
                .map(v -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("id", id);

                    return jsonResponse(200, response);
                })
                .whenComplete((response, error) -> {
                    long duration = System.currentTimeMillis() - startTime;
                    metricsCollector.recordTimer("ai.http.delete_embedding", duration,
                            "status", error == null ? "success" : "error");
                    metricsCollector.incrementCounter("ai.http.requests",
                            "endpoint", "delete_embedding",
                            "status", error == null ? "success" : "error");
                })
                .then(
                        response -> Promise.of(response),
                        error -> Promise.of(errorResponse(500, "Internal error: " + error.getMessage()))
                );
    }

    /**
     * Helper: Creates JSON response.
     */
    private HttpResponse jsonResponse(int status, Object data) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(data);
            return HttpResponse.ofCode(status)
                    .withHeader(io.activej.http.HttpHeaders.CONTENT_TYPE, "application/json")
                    .withBody(json)
                    .build();
        } catch (Exception e) {
            return errorResponse(500, "Failed to serialize response: " + e.getMessage());
        }
    }

    /**
     * Helper: Creates error response.
     */
    private HttpResponse errorResponse(int status, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", true);
        error.put("message", message);
        error.put("timestamp", System.currentTimeMillis());

        return jsonResponse(status, error);
    }

    /**
     * Helper: Generates unique ID for embedding.
     */
    private String generateId() {
        return "emb_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 10000);
    }
}
