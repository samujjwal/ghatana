package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles agent memory plane HTTP endpoints (DC-4).
 *
 * <p>Memory items are stored in the {@code dc_memory} collection and support
 * four tiers: EPISODIC, SEMANTIC, PROCEDURAL, PREFERENCE.
 *
 * @doc.type class
 * @doc.purpose Agent memory plane HTTP handlers (DC-4)
 * @doc.layer product
 * @doc.pattern Handler
 * @doc.gaa.memory episodic
 * @doc.gaa.lifecycle perceive
 */
public class MemoryPlaneHandler {

    private static final Logger log = LoggerFactory.getLogger(MemoryPlaneHandler.class);
    private static final String MEMORY_COLLECTION = "dc_memory";
    private static final String RECORD_TYPE_AGENT_MEMORY = "AGENT_MEMORY";
    private static final int DEFAULT_CONTEXT_LIMIT = 50;
    private static final int MAX_CONTEXT_LIMIT = 500;

    private final DataCloudClient client;
    private final HttpHandlerSupport http;

    public MemoryPlaneHandler(DataCloudClient client, HttpHandlerSupport http) {
        this.client = client;
        this.http = http;
    }

    public Promise<HttpResponse> handleStoreMemory(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(http.errorResponse(400, "agentId path parameter is required"));
        }

        return request.loadBody()
            .then(body -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = http.objectMapper().readValue(
                        body.getString(StandardCharsets.UTF_8), Map.class);

                    String content = stringValue(payload.get("content"));
                    if (content == null || content.isBlank()) {
                        return Promise.of(http.errorResponse(400, "'content' field is required"));
                    }

                    String tier = normalizeTier(payload.get("type"));
                    if (tier == null) {
                        return Promise.of(http.errorResponse(400,
                            "'type' field is required and must be one of episodic, semantic, procedural, preference"));
                    }

                    int ttlSeconds = parseTtlSeconds(payload.get("ttlSeconds"));
                    Instant now = Instant.now();
                    Instant expiresAt = ttlSeconds > 0 ? now.plusSeconds(ttlSeconds) : now.plus(Duration.ofDays(30));

                    List<String> tags = payload.get("tags") instanceof List<?> rawTags
                        ? rawTags.stream().filter(Objects::nonNull).map(String::valueOf).toList()
                        : List.of();

                    Map<String, Object> metadata = payload.get("metadata") instanceof Map<?, ?> rawMetadata
                        ? rawMetadata.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                            entry -> String.valueOf(entry.getKey()),
                            Map.Entry::getValue,
                            (left, right) -> right,
                            LinkedHashMap::new))
                        : Map.of();

                    double salience = payload.get("salience") instanceof Number number
                        ? number.doubleValue()
                        : 0.5d;

                    Map<String, Object> entity = new LinkedHashMap<>();
                    entity.put("id", UUID.randomUUID().toString());
                    entity.put("recordType", RECORD_TYPE_AGENT_MEMORY);
                    entity.put("agentId", agentId);
                    entity.put("type", tier);
                    entity.put("content", content);
                    entity.put("tags", tags);
                    entity.put("salience", salience);
                    entity.put("metadata", metadata);
                    entity.put("createdAt", now.toString());
                    entity.put("expiresAt", expiresAt.toString());
                    entity.put("retained", Boolean.FALSE);

                    return client.save(tenantId, MEMORY_COLLECTION, Map.copyOf(entity))
                        .map(saved -> http.jsonResponse(toMemoryItem(saved, tenantId)));
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Invalid request body: " + e.getMessage()));
                }
            })
            .then(Promise::of, e -> {
                log.error("[DC-4] store memory failed for agentId={}: {}", agentId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to store memory: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleListMemory(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String agentId = request.getQueryParameter("agentId");
        String rawType = request.getQueryParameter("type");
        String queryText = Optional.ofNullable(request.getQueryParameter("query")).orElse("").trim();
        int limit = Math.min(HttpHandlerSupport.parseLimitParam(request.getQueryParameter("limit"), DEFAULT_CONTEXT_LIMIT), MAX_CONTEXT_LIMIT);

        List<DataCloudClient.Filter> filters = new ArrayList<>();
        filters.add(DataCloudClient.Filter.eq("recordType", RECORD_TYPE_AGENT_MEMORY));
        if (agentId != null && !agentId.isBlank()) {
            filters.add(DataCloudClient.Filter.eq("agentId", agentId));
        }
        String normalizedType = normalizeTier(rawType);
        if (rawType != null && normalizedType == null) {
            return Promise.of(http.errorResponse(400,
                "Invalid type '" + rawType + "'. Valid values: episodic, semantic, procedural, preference"));
        }
        if (normalizedType != null) {
            filters.add(DataCloudClient.Filter.eq("type", normalizedType));
        }
        if (!queryText.isBlank()) {
            filters.add(DataCloudClient.Filter.like("content", "%" + queryText + "%"));
        }

        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filters(filters)
            .limit(MAX_CONTEXT_LIMIT)
            .build();

        return client.query(tenantId, MEMORY_COLLECTION, query)
            .then(items -> cleanupExpiredItems(tenantId, items)
                .map(activeItems -> http.jsonResponse(
                    buildRootListResponse(activeItems, tenantId, agentId, normalizedType, queryText, limit))))
            .then(Promise::of, e -> {
                log.error("[DC-4] list memory failed for tenant={} agentId={}: {}", tenantId, agentId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to list memory: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleGetAgentMemory(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(http.errorResponse(400, "agentId path parameter is required"));
        }

        int limit = Math.min(HttpHandlerSupport.parseLimitParam(request.getQueryParameter("limit"), DEFAULT_CONTEXT_LIMIT), MAX_CONTEXT_LIMIT);
        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filters(List.of(
                DataCloudClient.Filter.eq("recordType", RECORD_TYPE_AGENT_MEMORY),
                DataCloudClient.Filter.eq("agentId", agentId)
            ))
            .limit(MAX_CONTEXT_LIMIT)
            .build();

        return client.query(tenantId, "dc_memory", query)
            .then(items -> cleanupExpiredItems(tenantId, items)
                .map(activeItems -> {
                long episodic = activeItems.stream()
                    .filter(e -> "EPISODIC".equals(e.data().get("type"))).count();
                long semantic = activeItems.stream()
                    .filter(e -> "SEMANTIC".equals(e.data().get("type"))).count();
                long procedural = activeItems.stream()
                    .filter(e -> "PROCEDURAL".equals(e.data().get("type"))).count();
                long preference = activeItems.stream()
                    .filter(e -> "PREFERENCE".equals(e.data().get("type"))).count();
                long other = activeItems.size() - episodic - semantic - procedural - preference;

                List<Map<String, Object>> contextWindow = activeItems.stream()
                    .sorted(memoryItemComparator())
                    .limit(limit)
                    .map(item -> toMemoryItem(item, tenantId))
                    .toList();

                return http.jsonResponse(Map.of(
                    "agentId", agentId,
                    "tenantId", tenantId,
                    "total", activeItems.size(),
                    "items", contextWindow,
                    "contextWindowSize", contextWindow.size(),
                    "byType", Map.of(
                        "episodic", episodic,
                        "semantic", semantic,
                        "procedural", procedural,
                        "preference", preference,
                        "other", other
                    ),
                    "timestamp", Instant.now().toString()
                ));
            }))
            .then(Promise::of, e -> {
                log.error("[DC-4] memory query failed for agentId={}: {}", agentId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to query agent memory: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleGetAgentMemoryByTier(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String agentId = request.getPathParameter("agentId");
        String rawTier = request.getPathParameter("tier");

        if (agentId == null || agentId.isBlank()) {
            return Promise.of(http.errorResponse(400, "agentId path parameter is required"));
        }
        if (rawTier == null || rawTier.isBlank()) {
            return Promise.of(http.errorResponse(400, "tier path parameter is required"));
        }

        String tier = rawTier.toUpperCase();
        if (!List.of("EPISODIC", "SEMANTIC", "PROCEDURAL", "PREFERENCE").contains(tier)) {
            return Promise.of(http.errorResponse(400,
                "Invalid tier '" + rawTier + "'. Valid values: episodic, semantic, procedural, preference"));
        }

        int limit = Math.min(HttpHandlerSupport.parseLimitParam(request.getQueryParameter("limit"), 100), 1000);
        int offset = HttpHandlerSupport.parseLimitParam(request.getQueryParameter("offset"), 0);

        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filters(List.of(
                DataCloudClient.Filter.eq("recordType", RECORD_TYPE_AGENT_MEMORY),
                DataCloudClient.Filter.eq("agentId", agentId),
                DataCloudClient.Filter.eq("type", tier)
            ))
            .limit(MAX_CONTEXT_LIMIT)
            .build();

        return client.query(tenantId, "dc_memory", query)
            .then(items -> cleanupExpiredItems(tenantId, items)
                .map(activeItems -> {
                List<Map<String, Object>> itemData = activeItems.stream()
                    .sorted(memoryItemComparator())
                    .skip(offset)
                    .limit(limit)
                    .map(e -> toMemoryItem(e, tenantId))
                    .toList();

                return http.jsonResponse(Map.of(
                    "agentId", agentId,
                    "tenantId", tenantId,
                    "tier", tier.toLowerCase(),
                    "items", itemData,
                    "count", itemData.size(),
                    "offset", offset,
                    "limit", limit,
                    "timestamp", Instant.now().toString()
                ));
            }))
            .then(Promise::of, e -> {
                log.error("[DC-4] memory-by-tier query failed for agentId={} tier={}: {}",
                    agentId, tier, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to query agent memory: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleSearchAgentMemory(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(http.errorResponse(400, "agentId path parameter is required"));
        }
        return request.loadBody()
            .then(body -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> req = http.objectMapper().readValue(body.asArray(), Map.class);
                    String queryStr = (String) req.getOrDefault("query", "");
                    String type    = (String) req.get("type");
                    int limit = req.containsKey("limit")
                        ? Math.min(((Number) req.get("limit")).intValue(), 1000)
                        : 100;

                    List<DataCloudClient.Filter> filters = new ArrayList<>();
                    filters.add(DataCloudClient.Filter.eq("recordType", RECORD_TYPE_AGENT_MEMORY));
                    filters.add(DataCloudClient.Filter.eq("agentId", agentId));
                    if (type != null && !type.isBlank()) {
                        filters.add(DataCloudClient.Filter.eq("type", type.toUpperCase()));
                    }
                    if (!queryStr.isBlank()) {
                        filters.add(DataCloudClient.Filter.like("content", "%" + queryStr + "%"));
                    }

                    DataCloudClient.Query dcQuery = DataCloudClient.Query.builder()
                        .filters(filters)
                        .limit(MAX_CONTEXT_LIMIT)
                        .build();

                    return client.query(tenantId, "dc_memory", dcQuery)
                        .then(items -> cleanupExpiredItems(tenantId, items)
                            .map(activeItems -> {
                            List<Map<String, Object>> results = activeItems.stream()
                                .sorted(memoryItemComparator())
                                .limit(limit)
                                .map(e -> toMemoryItem(e, tenantId))
                                .toList();
                            return http.jsonResponse(Map.of(
                                "agentId",   agentId,
                                "tenantId",  tenantId,
                                "query",     queryStr,
                                "results",   results,
                                "count",     results.size(),
                                "timestamp", Instant.now().toString()
                            ));
                        }));
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Invalid request body: " + e.getMessage()));
                }
            })
            .then(Promise::of, e -> {
                log.error("[DC-4] memory search failed for agentId={}: {}", agentId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Memory search failed: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleDeleteMemory(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String agentId  = request.getPathParameter("agentId");
        String memoryId = request.getPathParameter("memoryId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(http.errorResponse(400, "agentId path parameter is required"));
        }
        if (memoryId == null || memoryId.isBlank()) {
            return Promise.of(http.errorResponse(400, "memoryId path parameter is required"));
        }
        return client.delete(tenantId, "dc_memory", memoryId)
            .map(v -> http.jsonResponse(Map.of(
                "deleted",   true,
                "memoryId",  memoryId,
                "agentId",   agentId,
                "tenantId",  tenantId,
                "timestamp", Instant.now().toString()
            )))
            .then(Promise::of, e -> {
                log.error("[DC-4] memory delete failed id={}: {}", memoryId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Memory delete failed: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleRetainMemory(HttpRequest request) {
        HttpHandlerSupport.TenantResolutionResult resolutionResult = http.requireTenantIdWithError(request);
        if (!resolutionResult.isSuccess()) {
            return Promise.of(http.errorResponse(resolutionResult.errorCode(), resolutionResult.errorMessage()));
        }
        String tenantId = resolutionResult.tenantId();
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String agentId  = request.getPathParameter("agentId");
        String memoryId = request.getPathParameter("memoryId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(http.errorResponse(400, "agentId path parameter is required"));
        }
        if (memoryId == null || memoryId.isBlank()) {
            return Promise.of(http.errorResponse(400, "memoryId path parameter is required"));
        }
        return request.loadBody()
            .then(body -> {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> req = http.objectMapper().readValue(body.asArray(), Map.class);
                    long retainUntilEpoch = req.containsKey("retainUntilEpoch")
                        ? ((Number) req.get("retainUntilEpoch")).longValue() : 0L;
                    String reason = (String) req.getOrDefault("reason", "manual-retain");

                    DataCloudClient.Query query = DataCloudClient.Query.builder()
                        .filters(List.of(
                            DataCloudClient.Filter.eq("recordType", RECORD_TYPE_AGENT_MEMORY),
                            DataCloudClient.Filter.eq("id", memoryId)
                        ))
                        .limit(1)
                        .build();

                    return client.query(tenantId, "dc_memory", query)
                        .then(items -> {
                            if (items.isEmpty()) {
                                return Promise.of(http.errorResponse(404,
                                    "Memory item not found: " + memoryId));
                            }
                            DataCloudClient.Entity entity = items.get(0);
                            Map<String, Object> updated = new LinkedHashMap<>(entity.data());
                            updated.put("retained", true);
                            updated.put("retainReason", reason);
                            if (retainUntilEpoch > 0L) {
                                updated.put("retainUntil",
                                    Instant.ofEpochMilli(retainUntilEpoch).toString());
                            }
                            return client.save(tenantId, "dc_memory", Map.copyOf(updated))
                                .map(v -> http.jsonResponse(Map.of(
                                    "retained",  true,
                                    "memoryId",  memoryId,
                                    "agentId",   agentId,
                                    "tenantId",  tenantId,
                                    "reason",    reason,
                                    "timestamp", Instant.now().toString()
                                )));
                        });
                } catch (Exception e) {
                    return Promise.of(http.errorResponse(400, "Invalid request body: " + e.getMessage()));
                }
            })
            .then(Promise::of, e -> {
                log.error("[DC-4] retain memory failed id={}: {}", memoryId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Memory retain failed: " + e.getMessage()));
            });
    }

    private Map<String, Object> buildRootListResponse(
            List<DataCloudClient.Entity> activeItems,
            String tenantId,
            String agentId,
            String type,
            String queryText,
            int limit) {
        List<Map<String, Object>> items = activeItems.stream()
            .sorted(memoryItemComparator())
            .limit(limit)
            .map(item -> toMemoryItem(item, tenantId))
            .toList();

        return Map.of(
            "items", items,
            "total", activeItems.size(),
            "tenantId", tenantId,
            "agentId", agentId != null ? agentId : "ALL",
            "type", type != null ? type : "ALL",
            "query", queryText,
            "timestamp", Instant.now().toString()
        );
    }

    private Promise<List<DataCloudClient.Entity>> cleanupExpiredItems(String tenantId, List<DataCloudClient.Entity> items) {
        List<DataCloudClient.Entity> activeItems = new ArrayList<>();
        Promise<Void> deleteChain = Promise.complete();

        for (DataCloudClient.Entity item : items) {
            if (isExpired(item)) {
                deleteChain = deleteChain.then(() -> client.delete(tenantId, MEMORY_COLLECTION, item.id()));
            } else {
                activeItems.add(item);
            }
        }

        return deleteChain.map(ignored -> List.copyOf(activeItems));
    }

    private boolean isExpired(DataCloudClient.Entity entity) {
        Object retained = entity.data().get("retained");
        if (Boolean.TRUE.equals(retained)) {
            return false;
        }
        String expiresAt = stringValue(entity.data().get("expiresAt"));
        if (expiresAt == null || expiresAt.isBlank()) {
            return false;
        }
        try {
            return Instant.parse(expiresAt).isBefore(Instant.now());
        } catch (Exception e) {
            log.warn("[DC-4] invalid expiresAt on memory item id={}: {}", entity.id(), expiresAt);
            return false;
        }
    }

    private Map<String, Object> toMemoryItem(DataCloudClient.Entity entity, String tenantId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", entity.id());
        payload.put("tenantId", tenantId);
        payload.put("agentId", entity.data().getOrDefault("agentId", ""));
        payload.put("type", entity.data().getOrDefault("type", "EPISODIC"));
        payload.put("content", entity.data().getOrDefault("content", ""));
        payload.put("tags", entity.data().getOrDefault("tags", List.of()));
        payload.put("salience", entity.data().getOrDefault("salience", 0.5d));
        payload.put("createdAt", entity.data().getOrDefault("createdAt", entity.createdAt().toString()));
        if (entity.data().containsKey("expiresAt")) {
            payload.put("expiresAt", entity.data().get("expiresAt"));
        }
        payload.put("metadata", entity.data().getOrDefault("metadata", Map.of()));
        return Map.copyOf(payload);
    }

    private Comparator<DataCloudClient.Entity> memoryItemComparator() {
        return Comparator
            .comparingDouble(this::salienceOf)
            .reversed()
            .thenComparing(this::createdAtOf, Comparator.reverseOrder());
    }

    private double salienceOf(DataCloudClient.Entity entity) {
        Object salience = entity.data().get("salience");
        return salience instanceof Number number ? number.doubleValue() : 0.0d;
    }

    private Instant createdAtOf(DataCloudClient.Entity entity) {
        String createdAt = stringValue(entity.data().get("createdAt"));
        try {
            return createdAt != null ? Instant.parse(createdAt) : entity.createdAt();
        } catch (Exception e) {
            return entity.createdAt();
        }
    }

    private static int parseTtlSeconds(Object rawTtl) {
        if (rawTtl instanceof Number number) {
            return Math.max(number.intValue(), 1);
        }
        return 0;
    }

    private static String normalizeTier(Object rawType) {
        String tier = stringValue(rawType);
        if (tier == null || tier.isBlank()) {
            return null;
        }
        String normalized = tier.toUpperCase();
        return List.of("EPISODIC", "SEMANTIC", "PROCEDURAL", "PREFERENCE").contains(normalized)
            ? normalized
            : null;
    }

    private static String stringValue(Object rawValue) {
        return rawValue != null ? String.valueOf(rawValue) : null;
    }
}
