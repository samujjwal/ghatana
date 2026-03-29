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
public class DistributedHttpDataCloudClient extends ManagedDataCloudClient {
    private static final Logger log = LoggerFactory.getLogger(DistributedHttpDataCloudClient.class);
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
        log.info("DistributedHttpDataCloudClient initialized with {} nodes", nodeUrls.length);
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

    private <T> Promise<T> post(String path, Object body, Class<T> responseType, String tenantId) {
        requireRunning();
        try {
            String json = MAPPER.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url(path)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(30));
            if (tenantId != null) {
                builder.header("X-Tenant-ID", tenantId);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }
            if (responseType == Void.class) return Promise.of(null);
            return Promise.of(MAPPER.readValue(response.body(), responseType));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Promise<T> get(String path, Class<T> responseType) {
        return get(path, responseType, null);
    }

    @SuppressWarnings("unchecked")
    private <T> Promise<T> get(String path, Class<T> responseType, String tenantId) {
        requireRunning();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url(path)))
                    .GET()
                    .timeout(Duration.ofSeconds(30));
            if (tenantId != null) {
                builder.header("X-Tenant-ID", tenantId);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) return Promise.of(null);
            if (response.statusCode() >= 400) {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return Promise.of(MAPPER.readValue(response.body(), responseType));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    private Promise<Void> delete(String path, String tenantId) {
        requireRunning();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url(path)))
                    .DELETE()
                    .timeout(Duration.ofSeconds(30));
            if (tenantId != null) {
                builder.header("X-Tenant-ID", tenantId);
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400 && response.statusCode() != 404) {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return Promise.of(null);
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    // ============ Entity Operations ============

    @Override
    public Promise<EntityInterface> createEntity(String tenantId, String collectionName, Map<String, Object> data) {
        String path = String.format("/api/v1/entities/%s", collectionName);
        return post(path, data, EntityInterface.class, tenantId);
    }

    @Override
    public Promise<Optional<EntityInterface>> getEntity(String tenantId, String collectionName, UUID entityId) {
        String path = String.format("/api/v1/entities/%s/%s", collectionName, entityId);
        return get(path, EntityInterface.class, tenantId).map(Optional::ofNullable);
    }

    @Override
    public Promise<EntityInterface> updateEntity(String tenantId, String collectionName, UUID entityId, Map<String, Object> data) {
        String path = String.format("/api/v1/entities/%s/%s", collectionName, entityId);
        try {
            String json = MAPPER.writeValueAsString(data);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url(path)))
                    .header("Content-Type", "application/json")
                    .header("X-Tenant-ID", tenantId)
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(30));
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return Promise.of(MAPPER.readValue(response.body(), EntityInterface.class));
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    @Override
    public Promise<Void> deleteEntity(String tenantId, String collectionName, UUID entityId) {
        return delete(String.format("/api/v1/entities/%s/%s", collectionName, entityId), tenantId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Promise<List<EntityInterface>> queryEntities(String tenantId, String collectionName, QuerySpecInterface query) {
        String path = String.format("/api/v1/entities/%s/query", collectionName);
        return (Promise<List<EntityInterface>>) (Promise<?>) post(path, query, List.class, tenantId);
    }

    @Override
    public Promise<Long> countEntities(String tenantId, String collectionName, String filterExpression) {
        String path = String.format("/api/v1/entities/%s/count?filter=%s", collectionName, filterExpression);
        return get(path, Long.class, tenantId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Promise<List<EntityInterface>> bulkCreateEntities(String tenantId, String collectionName, List<EntityInterface> entities) {
        String path = String.format("/api/v1/entities/%s/batch", collectionName);
        return (Promise<List<EntityInterface>>) (Promise<?>) post(path, entities, List.class, tenantId);
    }

    @Override
    public Promise<Long> bulkDeleteEntities(String tenantId, String collectionName, List<UUID> entityIds) {
        String path = String.format("/api/v1/entities/%s/batch", collectionName);
        return post(path, entityIds, Long.class, tenantId);
    }

    // ============ Event Operations ============

    @Override
    public Promise<Long> appendEvent(String tenantId, String streamName, DataRecord event) {
        String path = "/api/v1/events";
        return post(path, event, Long.class, tenantId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Promise<List<DataRecord>> readEvents(String tenantId, String streamName, long fromOffset, int limit) {
        String path = String.format("/api/v1/events?stream=%s&from=%d&limit=%d", streamName, fromOffset, limit);
        return (Promise<List<DataRecord>>) (Promise<?>) get(path, List.class, tenantId);
    }

    public Promise<DataRecord> getEventByOffset(String tenantId, String streamName, long offset) {
        String path = String.format("/api/v1/events?stream=%s&offset=%d", streamName, offset);
        return get(path, DataRecord.class, tenantId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Promise<List<DataRecord>> readEventsByTimeRange(String tenantId, String streamName, String startTime, String endTime) {
        String path = String.format("/api/v1/events?stream=%s&start=%s&end=%s", streamName, startTime, endTime);
        return (Promise<List<DataRecord>>) (Promise<?>) get(path, List.class, tenantId);
    }

    @Override
    public Promise<Long> getLatestOffset(String tenantId, String streamName) {
        return get(String.format("/api/v1/events?stream=%s&meta=latest-offset", streamName), Long.class, tenantId);
    }

    @Override
    public Promise<Long> countEvents(String tenantId, String streamName) {
        return get(String.format("/api/v1/events?stream=%s&meta=count", streamName), Long.class, tenantId);
    }

    // ============ Search Operations ============

    @Override
    public Promise<SearchResults> search(String tenantId, SearchQuery query) {
        return post("/api/v1/entities/" + query.getCollectionName() + "/search", query, SearchResults.class, tenantId);
    }

    @Override
    public Promise<SearchResults> fullTextSearch(String tenantId, String collectionName, String queryText, int limit) {
        String path = String.format("/api/v1/entities/%s/search?q=%s&limit=%d", collectionName, queryText, limit);
        return get(path, SearchResults.class, tenantId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Promise<Map<String, Long>> getFacets(String tenantId, String collectionName, String fieldName) {
        String path = String.format("/api/v1/entities/%s/facets/%s", collectionName, fieldName);
        return (Promise<Map<String, Long>>) (Promise<?>) get(path, Map.class, tenantId);
    }

    // ============ Analytics Operations ============

    @Override
    public Promise<QualityMetrics> getQualityMetrics(String tenantId, String collectionName) {
        return get(String.format("/api/v1/analytics/quality?collection=%s", collectionName), QualityMetrics.class, tenantId);
    }

    @Override
    public Promise<CostAnalysis> getCostAnalysis(String tenantId, int daysBack) {
        return get(String.format("/api/v1/analytics/cost?days=%d", daysBack), CostAnalysis.class, tenantId);
    }

    @Override
    public Promise<LineageGraph> getLineage(String tenantId, String collectionName) {
        return get(String.format("/api/v1/analytics/lineage?collection=%s", collectionName), LineageGraph.class, tenantId);
    }

    // ============ AI Operations ============

    @Override
    public Promise<AIProcessingResult> processWithAI(String tenantId, DataRecord record) {
        return post("/api/v1/brain/process", record, AIProcessingResult.class, tenantId);
    }

    @Override
    public Promise<AIModelInfo> getAIModelInfo(String tenantId, String modelName) {
        return get(String.format("/api/v1/brain/models/%s", modelName), AIModelInfo.class, tenantId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Promise<Map<String, Double>> getFeatures(String tenantId, String entityId, List<String> featureNames) {
        String path = String.format("/api/v1/learning/features/%s?names=%s", entityId, String.join(",", featureNames));
        return (Promise<Map<String, Double>>) (Promise<?>) get(path, Map.class, tenantId);
    }

    // ============ Health & Status ============

    @Override
    public Promise<HealthStatus> healthCheck() {
        if (!isRunning()) {
            return Promise.of(closedHealthStatus());
        }
        return get("/api/v1/health", HealthStatus.class);
    }

    @Override
    public Promise<SystemMetrics> getMetrics() {
        if (!isRunning()) {
            return Promise.of(systemMetrics(0, 0.0, 1.0, Map.of()));
        }
        return get("/api/v1/metrics", SystemMetrics.class);
    }

    @Override
    public void close() {
        if (!markClosed()) {
            return;
        }
        log.info("DistributedHttpDataCloudClient closed");
        executor.shutdown();
    }
}

