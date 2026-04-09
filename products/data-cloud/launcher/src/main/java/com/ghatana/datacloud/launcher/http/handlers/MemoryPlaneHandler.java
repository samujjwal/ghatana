package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.http.*;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private final DataCloudClient client;
    private final HttpHandlerSupport http;

    public MemoryPlaneHandler(DataCloudClient client, HttpHandlerSupport http) {
        this.client = client;
        this.http = http;
    }

    public Promise<HttpResponse> handleGetAgentMemory(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
        String agentId = request.getPathParameter("agentId");
        if (agentId == null || agentId.isBlank()) {
            return Promise.of(http.errorResponse(400, "agentId path parameter is required"));
        }

        int limit = HttpHandlerSupport.parseLimitParam(request.getQueryParameter("limit"), 10_000);
        DataCloudClient.Query query = DataCloudClient.Query.builder()
            .filter(DataCloudClient.Filter.eq("agentId", agentId))
            .limit(limit)
            .build();

        return client.query(tenantId, "dc_memory", query)
            .map(items -> {
                long episodic = items.stream()
                    .filter(e -> "EPISODIC".equals(e.data().get("type"))).count();
                long semantic = items.stream()
                    .filter(e -> "SEMANTIC".equals(e.data().get("type"))).count();
                long procedural = items.stream()
                    .filter(e -> "PROCEDURAL".equals(e.data().get("type"))).count();
                long preference = items.stream()
                    .filter(e -> "PREFERENCE".equals(e.data().get("type"))).count();
                long other = items.size() - episodic - semantic - procedural - preference;

                return http.jsonResponse(Map.of(
                    "agentId", agentId,
                    "tenantId", tenantId,
                    "total", items.size(),
                    "byType", Map.of(
                        "episodic", episodic,
                        "semantic", semantic,
                        "procedural", procedural,
                        "preference", preference,
                        "other", other
                    ),
                    "timestamp", Instant.now().toString()
                ));
            })
            .then(Promise::of, e -> {
                log.error("[DC-4] memory query failed for agentId={}: {}", agentId, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to query agent memory: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleGetAgentMemoryByTier(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
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
                DataCloudClient.Filter.eq("agentId", agentId),
                DataCloudClient.Filter.eq("type", tier)
            ))
            .limit(limit)
            .offset(offset)
            .build();

        return client.query(tenantId, "dc_memory", query)
            .map(items -> {
                List<Map<String, Object>> itemData = items.stream()
                    .map(e -> Map.<String, Object>of(
                        "id", e.id(),
                        "agentId", e.data().getOrDefault("agentId", agentId),
                        "type", tier,
                        "content", e.data().getOrDefault("content", ""),
                        "createdAt", e.data().getOrDefault("createdAt", Instant.now().toString()),
                        "metadata", e.data().getOrDefault("metadata", Map.of())
                    ))
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
            })
            .then(Promise::of, e -> {
                log.error("[DC-4] memory-by-tier query failed for agentId={} tier={}: {}",
                    agentId, tier, e.getMessage(), e);
                return Promise.of(http.errorResponse(500, "Failed to query agent memory: " + e.getMessage()));
            });
    }

    public Promise<HttpResponse> handleSearchAgentMemory(HttpRequest request) {
        String tenantId = http.resolveTenantId(request);
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
                    filters.add(DataCloudClient.Filter.eq("agentId", agentId));
                    if (type != null && !type.isBlank()) {
                        filters.add(DataCloudClient.Filter.eq("type", type.toUpperCase()));
                    }
                    if (!queryStr.isBlank()) {
                        filters.add(DataCloudClient.Filter.like("content", "%" + queryStr + "%"));
                    }

                    DataCloudClient.Query dcQuery = DataCloudClient.Query.builder()
                        .filters(filters)
                        .limit(limit)
                        .build();

                    return client.query(tenantId, "dc_memory", dcQuery)
                        .map(items -> {
                            List<Map<String, Object>> results = items.stream()
                                .map(e -> Map.<String, Object>of(
                                    "id",        e.id(),
                                    "agentId",   e.data().getOrDefault("agentId", agentId),
                                    "type",      e.data().getOrDefault("type", ""),
                                    "content",   e.data().getOrDefault("content", ""),
                                    "createdAt", e.data().getOrDefault("createdAt", ""),
                                    "metadata",  e.data().getOrDefault("metadata", Map.of())
                                ))
                                .toList();
                            return http.jsonResponse(Map.of(
                                "agentId",   agentId,
                                "tenantId",  tenantId,
                                "query",     queryStr,
                                "results",   results,
                                "count",     results.size(),
                                "timestamp", Instant.now().toString()
                            ));
                        });
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
        String tenantId = http.resolveTenantId(request);
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
        String tenantId = http.resolveTenantId(request);
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
                        .filter(DataCloudClient.Filter.eq("id", memoryId))
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
}
