package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.plugins.knowledgegraph.KnowledgeGraphPlugin;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphEdge;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphQuery;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP handler for the tenant-scoped context layer (P3.1).
 *
 * <p>Exposes a per-tenant key-value context store backed by an in-memory
 * {@link ConcurrentHashMap}.  The context layer is intended for lightweight
 * runtime metadata —feature flags, user preferences, session hints — that do
 * not require durable persistence.
 *
 * <p>Routes wired in {@code DataCloudHttpServer}:
 * <ul>
 *   <li>{@code GET    /api/v1/context}         — retrieve all entries for the request tenant</li>
 *   <li>{@code PUT    /api/v1/context}         — upsert one or many entries</li>
 *   <li>{@code DELETE /api/v1/context/keys/:key} — remove a single entry by key</li>
 *   <li>{@code GET    /api/v1/context/snapshot} — full versioned snapshot (P3.1.3)</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Tenant-scoped runtime key-value context store HTTP handler (P3.1)
 * @doc.layer product
 * @doc.pattern Handler
 */
public final class ContextLayerHandler {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /** Tenant-scoped key-value entries. Each value is an arbitrary JSON-compatible object. */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Object>> tenantContexts =
            new ConcurrentHashMap<>();

    /** Monotonically increasing version counter per tenant, incremented on every write. */
    private final ConcurrentHashMap<String, AtomicLong> tenantVersions =
            new ConcurrentHashMap<>();

    /** Tracks when context was first created for a tenant. */
    private final ConcurrentHashMap<String, Instant> tenantCreatedAt =
            new ConcurrentHashMap<>();

    private final HttpHandlerSupport http;
    private final ObjectMapper objectMapper;

    /**
     * Optional knowledge graph plugin for entity relationship enrichment (P3.5.1).
     * When present, context responses include a {@code relationships} field.
     */
    private final KnowledgeGraphPlugin knowledgeGraph;

    /** Maximum relationships to include per context response. */
    private static final int MAX_RELATIONSHIPS = 20;

    /**
     * @param http         shared HTTP support (JSON responses, tenant resolution, CORS)
     * @param objectMapper Jackson mapper for body parsing
     */
    public ContextLayerHandler(HttpHandlerSupport http, ObjectMapper objectMapper) {
        this(http, objectMapper, null);
    }

    /**
     * @param http           shared HTTP support
     * @param objectMapper   Jackson mapper for body parsing
     * @param knowledgeGraph optional knowledge graph plugin for relationship enrichment
     */
    public ContextLayerHandler(HttpHandlerSupport http, ObjectMapper objectMapper,
                               KnowledgeGraphPlugin knowledgeGraph) {
        this.http = Objects.requireNonNull(http, "http");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.knowledgeGraph = knowledgeGraph;
    }

    // ─── Routes ───────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/context
     *
     * <p>Returns all key-value context entries for the request tenant.  An empty
     * object is returned when no entries have been set yet.
     */
    public Promise<HttpResponse> handleGetContext(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = http.resolveCorrelationId(request);

        Map<String, Object> entries = currentEntries(tenantId);
        long version = currentVersion(tenantId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tenantId", tenantId);
        body.put("entries", entries);
        body.put("count", entries.size());
        body.put("version", version);
        body.put("requestId", requestId);

        if (knowledgeGraph == null) {
            return Promise.of(http.jsonResponse(body, requestId));
        }

        // P3.5.1: Enrich response with entity relationships from knowledge graph
        GraphQuery edgeQuery = GraphQuery.builder()
                .tenantId(tenantId)
                .limit(MAX_RELATIONSHIPS)
                .build();
        return knowledgeGraph.queryEdges(edgeQuery)
                .map(edges -> {
                    List<Map<String, Object>> relationships = buildRelationships(edges);
                    body.put("relationships", relationships);
                    return http.jsonResponse(body, requestId);
                })
                .then(Promise::of, ex -> Promise.of(http.jsonResponse(body, requestId)));
    }

    /**
     * PUT /api/v1/context
     *
     * <p>Upserts context entries for the request tenant.  The request body must be
     * a JSON object.  The top-level key {@code entries} may be a nested object,
     * or a flat set of key-value pairs.
     *
     * <p>Accepted shapes:
     * <pre>{@code
     * // Shape A – explicit "entries" wrapper:
     * { "entries": { "key1": "value1", "key2": 42 } }
     *
     * // Shape B – flat key-value (any other keys treated as context entries):
     * { "key1": "value1", "key2": 42 }
     * }</pre>
     */
    public Promise<HttpResponse> handlePutContext(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = http.resolveCorrelationId(request);

        return request.loadBody(64 * 1024)
            .then(body -> {
                Map<String, Object> input = parseBody(body.getString(StandardCharsets.UTF_8));
                if (input == null || input.isEmpty()) {
                    return Promise.of(http.errorResponse(400, "Request body must be a non-empty JSON object"));
                }

                Map<String, Object> entriesToUpsert = resolveEntries(input);
                if (entriesToUpsert.isEmpty()) {
                    return Promise.of(http.errorResponse(400, "No context entries provided"));
                }

                ConcurrentHashMap<String, Object> store = tenantContexts
                        .computeIfAbsent(tenantId, k -> new ConcurrentHashMap<>());
                tenantCreatedAt.putIfAbsent(tenantId, Instant.now());
                store.putAll(entriesToUpsert);
                long newVersion = tenantVersions
                        .computeIfAbsent(tenantId, k -> new AtomicLong(0))
                        .incrementAndGet();

                Map<String, Object> responseBody = new LinkedHashMap<>();
                responseBody.put("tenantId", tenantId);
                responseBody.put("upserted", entriesToUpsert.size());
                responseBody.put("version", newVersion);
                responseBody.put("updatedAt", Instant.now().toString());
                responseBody.put("requestId", requestId);

                return Promise.of(http.jsonResponse(responseBody, requestId));
            });
    }

    /**
     * DELETE /api/v1/context/keys/:key
     *
     * <p>Removes a single context entry identified by {@code key} for the request tenant.
     * Returns {@code 204 No Content} on success. Returns {@code 404} if the key is unknown.
     */
    public Promise<HttpResponse> handleDeleteContextKey(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String rawKey = request.getPathParameter("key");

        if (rawKey == null || rawKey.isBlank()) {
            return Promise.of(http.errorResponse(400, "key path parameter is required"));
        }

        String key = rawKey.trim();
        ConcurrentHashMap<String, Object> store = tenantContexts.get(tenantId);
        if (store == null || !store.containsKey(key)) {
            return Promise.of(http.jsonResponse(404,
                    Map.of("error", "Key not found", "key", key, "tenantId", tenantId)));
        }

        store.remove(key);
        tenantVersions.computeIfAbsent(tenantId, k -> new AtomicLong(0)).incrementAndGet();

        return Promise.of(http.noContentResponse());
    }

    /**
     * GET /api/v1/context/snapshot
     *
     * <p>Returns a complete, versioned snapshot of the tenant's context with metadata.
     * Suitable for audit trails, debugging, and cross-service context propagation.
     * The snapshot is ordered by insertion order.
     *
     * <p>Response shape:
     * <pre>{@code
     * {
     *   "tenantId": "...",
     *   "version": 3,
     *   "count": 5,
     *   "createdAt": "2026-04-15T10:00:00Z",   // first write for this tenant
     *   "snapshotAt": "2026-04-15T10:05:00Z",   // time of this request
     *   "entries": { ... }
     * }
     * }</pre>
     */
    public Promise<HttpResponse> handleGetSnapshot(HttpRequest request) {
        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String requestId = http.resolveCorrelationId(request);

        Map<String, Object> entries = currentEntries(tenantId);
        long version = currentVersion(tenantId);
        Instant createdAt = tenantCreatedAt.getOrDefault(tenantId, Instant.now());

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("tenantId", tenantId);
        snapshot.put("version", version);
        snapshot.put("count", entries.size());
        snapshot.put("createdAt", createdAt.toString());
        snapshot.put("snapshotAt", Instant.now().toString());
        snapshot.put("entries", entries);
        snapshot.put("requestId", requestId);

        return Promise.of(http.jsonResponse(snapshot, requestId));
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Maps a list of {@link GraphEdge} objects into the JSON-serializable relationship
     * representation exposed in the context API response.
     *
     * <p>Each entry contains: {@code sourceEntity}, {@code targetEntity},
     * {@code type}, and an optional {@code confidence} extracted from edge properties.
     */
    private List<Map<String, Object>> buildRelationships(List<GraphEdge> edges) {
        List<Map<String, Object>> result = new ArrayList<>(edges.size());
        for (GraphEdge edge : edges) {
            Map<String, Object> rel = new LinkedHashMap<>();
            rel.put("sourceEntity", edge.getSourceNodeId());
            rel.put("targetEntity", edge.getTargetNodeId());
            rel.put("type", edge.getRelationshipType());
            Object confidence = edge.getProperties() != null
                    ? edge.getProperties().get("confidence")
                    : null;
            if (confidence != null) {
                rel.put("confidence", confidence);
            }
            result.add(rel);
        }
        return result;
    }

    /**
     * Returns an unmodifiable copy of the context entries for {@code tenantId}.
     * Returns an empty map when no entries exist.
     * Package-private to allow voice and other handlers in the same package to read context.
     */
    Map<String, Object> currentEntries(String tenantId) {
        ConcurrentHashMap<String, Object> store = tenantContexts.get(tenantId);
        if (store == null || store.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(store);
    }

    private long currentVersion(String tenantId) {
        AtomicLong versionCounter = tenantVersions.get(tenantId);
        return versionCounter == null ? 0L : versionCounter.get();
    }

    /**
     * Parses a JSON request body. Returns an empty map on parse failure.
     */
    private Map<String, Object> parseBody(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * Extracts the map of entries to upsert from the parsed request body.
     *
     * <p>If the body contains an {@code entries} key whose value is a Map, that
     * nested map is used.  Otherwise the entire body is treated as entries.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolveEntries(Map<String, Object> input) {
        Object nested = input.get("entries");
        if (nested instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return input;
    }
}
