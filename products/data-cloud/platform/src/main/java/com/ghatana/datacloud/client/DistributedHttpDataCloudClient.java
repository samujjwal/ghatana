package com.ghatana.datacloud.client;

import com.ghatana.datacloud.DataRecord;
import com.ghatana.datacloud.entity.EntityInterface;
import com.ghatana.datacloud.entity.storage.QuerySpecInterface;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Distributed HTTP Data-Cloud Client.
 *
 * <p>Routes requests across multiple Data-Cloud node URLs in round-robin
 * fashion. All blocking HTTP calls are wrapped in {@code Promise.ofBlocking}
 * to keep the ActiveJ eventloop free.</p>
 *
 * @doc.type class
 * @doc.purpose Distributed HTTP client for multi-node Data-Cloud access
 * @doc.layer core
 * @doc.pattern Client, Facade
 */
public class DistributedHttpDataCloudClient implements DataCloudClient {
    private static final Logger logger = LoggerFactory.getLogger(DistributedHttpDataCloudClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String[] nodeUrls;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final AtomicInteger roundRobin = new AtomicInteger(0);

    /**
     * Creates distributed HTTP client with multiple node URLs.
     *
     * @param nodeUrls array of node URLs
     */
    public DistributedHttpDataCloudClient(String[] nodeUrls) {
        Objects.requireNonNull(nodeUrls, "nodeUrls must not be null");
        if (nodeUrls.length == 0) {
            throw new IllegalArgumentException("At least one node URL is required");
        }
        this.nodeUrls = nodeUrls.clone();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .executor(executor)
                .build();
        logger.info("DistributedHttpDataCloudClient initialized with {} nodes", nodeUrls.length);
    }

    // ── Routing ─────────────────────────────────────────────────────────

    private String nextNode() {
        int idx = Math.abs(roundRobin.getAndIncrement() % nodeUrls.length);
        return nodeUrls[idx];
    }

    private String url(String path) {
        return nextNode() + path;
    }

    // ── HTTP helpers ────────────────────────────────────────────────────

    private <T> Promise<T> post(String path, Object body, Class<T> responseType) {
        return Promise.ofBlocking(executor, () -> {
            String json = MAPPER.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url(path)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }
            if (responseType == Void.class) return null;
            return MAPPER.readValue(response.body(), responseType);
        });
    }

    @SuppressWarnings("unchecked")
    private <T> Promise<T> get(String path, Class<T> responseType) {
        return Promise.ofBlocking(executor, () -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url(path)))
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return null;
            if (response.statusCode() >= 400) {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return MAPPER.readValue(response.body(), responseType);
        });
    }

    private Promise<Void> delete(String path) {
        return Promise.ofBlocking(executor, () -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url(path)))
                    .DELETE()
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400 && response.statusCode() != 404) {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return null;
        });
    }

    // ============ Entity Operations ============

    @Override
    public Promise<EntityInterface> createEntity(String tenantId, String collectionName, Map<String, Object> data) {
        String path = String.format("/api/v1/tenants/%s/collections/%s/entities", tenantId, collectionName);
        return post(path, data, EntityInterface.class);
    }

    @Override
    public Promise<Optional<EntityInterface>> getEntity(String tenantId, String collectionName, UUID entityId) {
        String path = String.format("/api/v1/tenants/%s/collections/%s/entities/%s", tenantId, collectionName, entityId);
        return get(path, EntityInterface.class).map(Optional::ofNullable);
    }

    @Override
    public Promise<EntityInterface> updateEntity(String tenantId, String collectionName, UUID entityId, Map<String, Object> data) {
        String path = String.format("/api/v1/tenants/%s/collections/%s/entities/%s", tenantId, collectionName, entityId);
        return Promise.ofBlocking(executor, () -> {
            String json = MAPPER.writeValueAsString(data);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url(path)))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return MAPPER.readValue(response.body(), EntityInterface.class);
        });
    }

    @Override
    public Promise<Void> deleteEntity(String tenantId, String collectionName, UUID entityId) {
        return delete(String.format("/api/v1/tenants/%s/collections/%s/entities/%s", tenantId, collectionName, entityId));
    }

    @Override
    @SuppressWarnings("unchecked")
    public Promise<List<EntityInterface>> queryEntities(String tenantId, String collectionName, QuerySpecInterface query) {
        String path = String.format("/api/v1/tenants/%s/collections/%s/entities/query", tenantId, collectionName);
        return (Promise<List<EntityInterface>>) (Promise<?>) post(path, query, List.class);
    }

    @Override
    public Promise<Long> countEntities(String tenantId, String collectionName, String filterExpression) {
        String path = String.format("/api/v1/tenants/%s/collections/%s/entities/count?filter=%s", tenantId, collectionName, filterExpression);
        return get(path, Long.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Promise<List<EntityInterface>> bulkCreateEntities(String tenantId, String collectionName, List<EntityInterface> entities) {
        String path = String.format("/api/v1/tenants/%s/collections/%s/entities/bulk", tenantId, collectionName);
        return (Promise<List<EntityInterface>>) (Promise<?>) post(path, entities, List.class);
    }

    @Override
    public Promise<Long> bulkDeleteEntities(String tenantId, String collectionName, List<UUID> entityIds) {
        String path = String.format("/api/v1/tenants/%s/collections/%s/entities/bulk-delete", tenantId, collectionName);
        return post(path, entityIds, Long.class);
    }

    // ============ Event Operations ============

    @Override
    public Promise<Long> appendEvent(String tenantId, String streamName, DataRecord event) {
        String path = String.format("/api/v1/tenants/%s/streams/%s/events", tenantId, streamName);
        return post(path, event, Long.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Promise<List<DataRecord>> readEvents(String tenantId, String streamName, long fromOffset, int limit) {
        String path = String.format("/api/v1/tenants/%s/streams/%s/events?from=%d&limit=%d", tenantId, streamName, fromOffset, limit);
        return (Promise<List<DataRecord>>) (Promise<?>) get(path, List.class);
    }

    public Promise<DataRecord> getEventByOffset(String tenantId, String streamName, long offset) {
        String path = String.format("/api/v1/tenants/%s/streams/%s/events/%d", tenantId, streamName, offset);
        return get(path, DataRecord.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Promise<List<DataRecord>> readEventsByTimeRange(String tenantId, String streamName, String startTime, String endTime) {
        String path = String.format("/api/v1/tenants/%s/streams/%s/events?start=%s&end=%s", tenantId, streamName, startTime, endTime);
        return (Promise<List<DataRecord>>) (Promise<?>) get(path, List.class);
    }

    @Override
    public Promise<Long> getLatestOffset(String tenantId, String streamName) {
        return get(String.format("/api/v1/tenants/%s/streams/%s/latest-offset", tenantId, streamName), Long.class);
    }

    @Override
    public Promise<Long> countEvents(String tenantId, String streamName) {
        return get(String.format("/api/v1/tenants/%s/streams/%s/count", tenantId, streamName), Long.class);
    }

    // ============ Search Operations ============

    @Override
    public Promise<SearchResults> search(String tenantId, SearchQuery query) {
        return post(String.format("/api/v1/tenants/%s/search", tenantId), query, SearchResults.class);
    }

    @Override
    public Promise<SearchResults> fullTextSearch(String tenantId, String collectionName, String queryText, int limit) {
        String path = String.format("/api/v1/tenants/%s/collections/%s/search?q=%s&limit=%d", tenantId, collectionName, queryText, limit);
        return get(path, SearchResults.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Promise<Map<String, Long>> getFacets(String tenantId, String collectionName, String fieldName) {
        String path = String.format("/api/v1/tenants/%s/collections/%s/facets/%s", tenantId, collectionName, fieldName);
        return (Promise<Map<String, Long>>) (Promise<?>) get(path, Map.class);
    }

    // ============ Analytics Operations ============

    @Override
    public Promise<QualityMetrics> getQualityMetrics(String tenantId, String collectionName) {
        return get(String.format("/api/v1/tenants/%s/collections/%s/quality", tenantId, collectionName), QualityMetrics.class);
    }

    @Override
    public Promise<CostAnalysis> getCostAnalysis(String tenantId, int daysBack) {
        return get(String.format("/api/v1/tenants/%s/cost?days=%d", tenantId, daysBack), CostAnalysis.class);
    }

    @Override
    public Promise<LineageGraph> getLineage(String tenantId, String collectionName) {
        return get(String.format("/api/v1/tenants/%s/collections/%s/lineage", tenantId, collectionName), LineageGraph.class);
    }

    // ============ AI Operations ============

    @Override
    public Promise<AIProcessingResult> processWithAI(String tenantId, DataRecord record) {
        return post(String.format("/api/v1/tenants/%s/ai/process", tenantId), record, AIProcessingResult.class);
    }

    @Override
    public Promise<AIModelInfo> getAIModelInfo(String tenantId, String modelName) {
        return get(String.format("/api/v1/tenants/%s/ai/models/%s", tenantId, modelName), AIModelInfo.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Promise<Map<String, Double>> getFeatures(String tenantId, String entityId, List<String> featureNames) {
        String path = String.format("/api/v1/tenants/%s/features/%s?names=%s", tenantId, entityId, String.join(",", featureNames));
        return (Promise<Map<String, Double>>) (Promise<?>) get(path, Map.class);
    }

    // ============ Health & Status ============

    @Override
    public Promise<HealthStatus> healthCheck() {
        return get("/api/v1/health", HealthStatus.class);
    }

    @Override
    public Promise<SystemMetrics> getMetrics() {
        return get("/api/v1/metrics", SystemMetrics.class);
    }

    @Override
    public void close() {
        logger.info("DistributedHttpDataCloudClient closed");
        executor.shutdown();
    }
}

