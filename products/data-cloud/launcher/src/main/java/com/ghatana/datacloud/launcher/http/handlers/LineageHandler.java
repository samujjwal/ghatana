package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.plugins.lineage.LineagePlugin;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP handler for entity lineage tracking and visualization (P3.9.1).
 *
 * <p>Exposes a DAG API for upstream/downstream data lineage derived from
 * {@link LineagePlugin}. Routes wired in {@code DataCloudHttpServer}:
 * <ul>
 *   <li>{@code GET /api/v1/lineage/:collection}        — full lineage graph (upstream + downstream + records)</li>
 *   <li>{@code GET /api/v1/lineage/:collection/impact} — impact analysis (what is affected by changes)</li>
 * </ul>
 *
 * <p>When no {@link LineagePlugin} is wired, both endpoints return HTTP 501.</p>
 *
 * @doc.type class
 * @doc.purpose Entity lineage tracking and visualization HTTP handler (P3.9.1)
 * @doc.layer product
 * @doc.pattern Handler
 */
public final class LineageHandler {

    private static final Logger log = LoggerFactory.getLogger(LineageHandler.class);

    /** Maximum depth for graph traversal when no explicit depth is requested. */
    public static final int DEFAULT_GRAPH_DEPTH = -1; // unlimited

    private final HttpHandlerSupport http;

    /** Optional lineage plugin — null when lineage-plugin is not configured. */
    private final LineagePlugin lineagePlugin;

    /**
     * Creates a handler backed by a live {@link LineagePlugin}.
     *
     * @param http          handler support utilities
     * @param objectMapper  JSON serializer
     * @param lineagePlugin lineage plugin instance; may be {@code null} to return 501 for all routes
     */
    public LineageHandler(HttpHandlerSupport http, ObjectMapper objectMapper, LineagePlugin lineagePlugin) {
        this.http = http;
        this.lineagePlugin = lineagePlugin;
    }

    /**
     * {@code GET /api/v1/lineage/:collection}
     *
     * <p>Returns the full lineage graph for the requested collection as a JSON object with
     * {@code upstream}, {@code downstream}, and {@code records} arrays. Supports an optional
     * {@code direction} query parameter ({@code UPSTREAM | DOWNSTREAM | BOTH}, default {@code BOTH}).
     */
    public Promise<HttpResponse> handleGetLineage(HttpRequest request) {
        if (lineagePlugin == null) {
            return Promise.of(http.errorResponse(501, "Lineage tracking is not enabled — configure lineage-plugin in launcher bootstrap"));
        }

        String collection = request.getPathParameter("collection");
        if (collection == null || collection.isBlank()) {
            return Promise.of(http.errorResponse(400, "Missing path parameter: collection"));
        }

        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }
        String direction = request.getQueryParameter("direction");
        if (direction == null) {
            direction = "BOTH";
        }

        final String finalDirection = direction.toUpperCase();
        if (!finalDirection.equals("UPSTREAM") && !finalDirection.equals("DOWNSTREAM") && !finalDirection.equals("BOTH")) {
            return Promise.of(http.errorResponse(400,
                    "Invalid direction — must be UPSTREAM, DOWNSTREAM, or BOTH"));
        }

        log.debug("[P3.9.1] lineage graph request: tenant={} collection={} direction={}",
                tenantId, collection, finalDirection);

        return lineagePlugin.getLineageGraph(tenantId)
                .then(graph -> buildLineageDag(graph, tenantId, collection, finalDirection))
                .then(Promise::of, e -> {
                    log.error("[P3.9.1] lineage graph error: tenant={} collection={}", tenantId, collection, e);
                    return Promise.of(http.errorResponse(500, "Lineage graph error: " + e.getMessage()));
                });
    }

    private Promise<HttpResponse> buildLineageDag(
            LineagePlugin.LineageGraph graph,
            String tenantId,
            String collection,
            String direction) {

        return lineagePlugin.getUpstreamLineage(tenantId, collection)
                .then(upstream -> lineagePlugin.getDownstreamLineage(tenantId, collection)
                        .map(downstream -> {
                            Map<String, Object> response = new LinkedHashMap<>();
                            response.put("collection", collection);
                            response.put("tenantId", tenantId);
                            response.put("direction", direction);
                            response.put("timestamp", Instant.now().toString());

                            if (direction.equals("UPSTREAM") || direction.equals("BOTH")) {
                                response.put("upstream", graph.getUpstream().getOrDefault(collection, java.util.Set.of()));
                            }
                            if (direction.equals("DOWNSTREAM") || direction.equals("BOTH")) {
                                response.put("downstream", graph.getDownstream().getOrDefault(collection, java.util.Set.of()));
                            }

                            List<Map<String, Object>> nodes = new ArrayList<>();
                            List<Map<String, Object>> edges = new ArrayList<>();

                            nodes.add(buildNode(collection, "DATASET", "root"));
                            for (String col : upstream) {
                                String colName = extractCollectionName(col);
                                nodes.add(buildNode(colName, "DATASET", "upstream"));
                                edges.add(buildEdge(colName, collection, "DERIVES_FROM"));
                            }
                            for (String col : downstream) {
                                String colName = extractCollectionName(col);
                                nodes.add(buildNode(colName, "DATASET", "downstream"));
                                edges.add(buildEdge(collection, colName, "FEEDS_INTO"));
                            }

                            response.put("dag", Map.of("nodes", nodes, "edges", edges));
                            response.put("upstreamCount", upstream.size());
                            response.put("downstreamCount", downstream.size());

                            try {
                                return http.jsonResponse(response);
                            } catch (Exception e) {
                                log.error("[P3.9.1] serialization error", e);
                                return http.errorResponse(500, "Serialization error: " + e.getMessage());
                            }
                        }));
    }

    /**
     * {@code GET /api/v1/lineage/:collection/impact}
     *
     * <p>Returns an impact analysis payload showing all downstream collections affected if
     * this collection changes. Impact level is LOW/MEDIUM/HIGH based on number of affected
     * downstream datasets.
     */
    public Promise<HttpResponse> handleGetImpact(HttpRequest request) {
        if (lineagePlugin == null) {
            return Promise.of(http.errorResponse(501, "Lineage tracking is not enabled — configure lineage-plugin in launcher bootstrap"));
        }

        String collection = request.getPathParameter("collection");
        if (collection == null || collection.isBlank()) {
            return Promise.of(http.errorResponse(400, "Missing path parameter: collection"));
        }

        String tenantId = http.requireTenantIdOrFail(request);
        if (tenantId == null) {
            return Promise.of(http.errorResponse(400, "X-Tenant-Id header is required"));
        }

        log.debug("[P3.9.1] impact analysis request: tenant={} collection={}", tenantId, collection);

        return lineagePlugin.analyzeImpact(tenantId, collection)
                .map(impact -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("collection", impact.getCollection());
                    response.put("tenantId", tenantId);
                    response.put("impactLevel", impact.getImpactLevel());
                    response.put("affectedCount", impact.getAffectedCollections().size());
                    response.put("affectedCollections", impact.getAffectedCollections());
                    response.put("timestamp", impact.getTimestamp().toString());

                    try {
                        return http.jsonResponse(response);
                    } catch (Exception e) {
                        log.error("[P3.9.1] serialization error during impact analysis", e);
                        return http.errorResponse(500, "Serialization error: " + e.getMessage());
                    }
                })
                .then(Promise::of, e -> {
                    log.error("[P3.9.1] impact analysis error: tenant={} collection={}", tenantId, collection, e);
                    return Promise.of(http.errorResponse(500, "Impact analysis error: " + e.getMessage()));
                });
    }

    /**
     * Records a transformation for a collection pair.
     *
     * <p>Called internally by entity CRUD flows to track source of entity writes.
     *
     * @param tenantId           tenant identifier
     * @param sourceCollection   collection that produced data
     * @param targetCollection   collection that consumed or derived data
     * @param transformationType human-readable label (e.g. "API_WRITE", "PIPELINE_TRANSFORM")
     * @param metadata           additional metadata (caller, agent ID, workflow step, etc.)
     * @return promise of void
     */
    public Promise<Void> recordTransformation(
            String tenantId,
            String sourceCollection,
            String targetCollection,
            String transformationType,
            Map<String, Object> metadata) {
        if (lineagePlugin == null) {
            return Promise.complete();
        }
        return lineagePlugin.recordTransformation(
                tenantId, sourceCollection, targetCollection, transformationType, metadata);
    }

    // --- private helpers ---

    private Map<String, Object> buildNode(String id, String type, String role) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", id);
        node.put("type", type);
        node.put("name", id);
        node.put("role", role);
        node.put("metadata", Map.of());
        return node;
    }

    private Map<String, Object> buildEdge(String source, String target, String type) {
        Map<String, Object> edge = new LinkedHashMap<>();
        edge.put("source", source);
        edge.put("target", target);
        edge.put("type", type);
        return edge;
    }

    /**
     * Strips the {@code tenantId:} prefix from a dependency key if present.
     */
    private String extractCollectionName(String key) {
        int colon = key.indexOf(':');
        return colon >= 0 ? key.substring(colon + 1) : key;
    }
}
