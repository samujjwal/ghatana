package com.ghatana.datacloud.plugins.knowledgegraph.api;

import com.ghatana.datacloud.plugins.knowledgegraph.KnowledgeGraphPlugin;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphEdge;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphNode;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphQuery;
import com.ghatana.platform.http.server.security.TenantExtractor;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * REST API controller for Knowledge Graph operations.
 * 
 * <p>Provides HTTP endpoints for graph operations including:
 * <ul>
 *   <li>Node CRUD operations</li>
 *   <li>Edge CRUD operations</li>
 *   <li>Graph queries and filtering</li>
 *   <li>Graph traversal operations</li>
 *   <li>Batch operations</li>
 * </ul>
 * 
 * <p><b>Endpoints:</b>
 * <pre>
 * POST   /api/v1/graph/nodes              - Create node
 * GET    /api/v1/graph/nodes/:id          - Get node
 * PUT    /api/v1/graph/nodes/:id          - Update node
 * DELETE /api/v1/graph/nodes/:id          - Delete node
 * POST   /api/v1/graph/nodes/query        - Query nodes
 * POST   /api/v1/graph/nodes/batch        - Batch create nodes
 * 
 * POST   /api/v1/graph/edges              - Create edge
 * GET    /api/v1/graph/edges/:id          - Get edge
 * PUT    /api/v1/graph/edges/:id          - Update edge
 * DELETE /api/v1/graph/edges/:id          - Delete edge
 * POST   /api/v1/graph/edges/query        - Query edges
 * 
 * GET    /api/v1/graph/nodes/:id/neighbors - Get neighbors
 * GET    /api/v1/graph/nodes/:id/edges    - Get node edges
 * POST   /api/v1/graph/path/shortest      - Find shortest path
 * POST   /api/v1/graph/path/all           - Find all paths
 * </pre>
 * 
 * @doc.type class
 * @doc.purpose REST API controller for graph operations
 * @doc.layer api
 * @doc.pattern Controller
 */
@Slf4j
public class GraphApiController {
    
    private final KnowledgeGraphPlugin graphPlugin;
    private final JsonMapper jsonMapper;
    
    public GraphApiController(KnowledgeGraphPlugin graphPlugin) {
        this.graphPlugin = graphPlugin;
        this.jsonMapper = new JsonMapper();
        log.info("GraphApiController initialized");
    }
    
    // ========================================================================
    // Node Operations
    // ========================================================================
    
    /**
     * POST /api/v1/graph/nodes
     * Creates a new graph node.
     */
    public Promise<HttpResponse> createNode(HttpRequest request) {
        log.debug("POST /api/v1/graph/nodes");
        
        return request.loadBody()
                .then(body -> {
                    // TODO: Parse JSON body to GraphNode
                    GraphNode node = parseNodeFromJson(body.asString(StandardCharsets.UTF_8));
                    return graphPlugin.createNode(node);
                })
                .map(node -> HttpResponse.ok200()
                        .withJson(toJson(node))
                        .build())
                .whenException(e -> log.error("Error creating node", e));
    }
    
    /**
     * GET /api/v1/graph/nodes/:id
     * Gets a node by ID.
     */
    public Promise<HttpResponse> getNode(HttpRequest request) {
        String nodeId = request.getPathParameter("id");
        String tenantId = getTenantId(request);
        
        log.debug("GET /api/v1/graph/nodes/{}", nodeId);
        
        return graphPlugin.getNode(nodeId, tenantId)
                .map(node -> {
                    if (node == null) {
                        return HttpResponse.ofCode(404)
                                .withJson("{\"error\":\"Node not found\"}")
                                .build();
                    }
                    return HttpResponse.ok200().withJson(toJson(node)).build();
                })
                .whenException(e -> log.error("Error getting node", e));
    }
    
    /**
     * PUT /api/v1/graph/nodes/:id
     * Updates an existing node.
     */
    public Promise<HttpResponse> updateNode(HttpRequest request) {
        String nodeId = request.getPathParameter("id");
        log.debug("PUT /api/v1/graph/nodes/{}", nodeId);
        
        return request.loadBody()
                .then(body -> {
                    GraphNode node = parseNodeFromJson(body.asString(StandardCharsets.UTF_8));
                    return graphPlugin.updateNode(node.withId(nodeId));
                })
                .map(node -> HttpResponse.ok200().withJson(toJson(node)).build())
                .whenException(e -> log.error("Error updating node", e));
    }
    
    /**
     * DELETE /api/v1/graph/nodes/:id
     * Deletes a node and its connected edges.
     */
    public Promise<HttpResponse> deleteNode(HttpRequest request) {
        String nodeId = request.getPathParameter("id");
        String tenantId = getTenantId(request);
        
        log.debug("DELETE /api/v1/graph/nodes/{}", nodeId);
        
        return graphPlugin.deleteNode(nodeId, tenantId)
                .map(deleted -> {
                    if (deleted) {
                        return HttpResponse.ok200()
                                .withJson("{\"success\":true,\"message\":\"Node deleted\"}")
                                .build();
                    }
                    return HttpResponse.ofCode(404)
                            .withJson("{\"error\":\"Node not found\"}")
                            .build();
                })
                .whenException(e -> log.error("Error deleting node", e));
    }
    
    /**
     * POST /api/v1/graph/nodes/query
     * Queries nodes with filtering.
     */
    public Promise<HttpResponse> queryNodes(HttpRequest request) {
        log.debug("POST /api/v1/graph/nodes/query");
        
        return request.loadBody()
                .then(body -> {
                    GraphQuery query = parseQueryFromJson(body.asString(StandardCharsets.UTF_8));
                    return graphPlugin.queryNodes(query);
                })
                .map(nodes -> HttpResponse.ok200()
                        .withJson(toJson(Map.of("nodes", nodes, "count", nodes.size())))
                        .build())
                .whenException(e -> log.error("Error querying nodes", e));
    }
    
    /**
     * POST /api/v1/graph/nodes/batch
     * Batch creates multiple nodes.
     */
    public Promise<HttpResponse> batchCreateNodes(HttpRequest request) {
        log.debug("POST /api/v1/graph/nodes/batch");
        
        return request.loadBody()
                .then(body -> {
                    List<GraphNode> nodes = parseNodesFromJson(body.asString(StandardCharsets.UTF_8));
                    return graphPlugin.batchCreateNodes(nodes);
                })
                .map(nodes -> HttpResponse.ok200()
                        .withJson(toJson(Map.of("nodes", nodes, "count", nodes.size())))
                        .build())
                .whenException(e -> log.error("Error batch creating nodes", e));
    }
    
    // ========================================================================
    // Edge Operations
    // ========================================================================
    
    /**
     * POST /api/v1/graph/edges
     * Creates a new graph edge.
     */
    public Promise<HttpResponse> createEdge(HttpRequest request) {
        log.debug("POST /api/v1/graph/edges");
        
        return request.loadBody()
                .then(body -> {
                    GraphEdge edge = parseEdgeFromJson(body.asString(StandardCharsets.UTF_8));
                    return graphPlugin.createEdge(edge);
                })
                .map(edge -> HttpResponse.ok200().withJson(toJson(edge)).build())
                .whenException(e -> log.error("Error creating edge", e));
    }
    
    /**
     * GET /api/v1/graph/edges/:id
     * Gets an edge by ID.
     */
    public Promise<HttpResponse> getEdge(HttpRequest request) {
        String edgeId = request.getPathParameter("id");
        String tenantId = getTenantId(request);
        
        log.debug("GET /api/v1/graph/edges/{}", edgeId);
        
        return graphPlugin.getEdge(edgeId, tenantId)
                .map(edge -> {
                    if (edge == null) {
                        return HttpResponse.ofCode(404)
                                .withJson("{\"error\":\"Edge not found\"}")
                                .build();
                    }
                    return HttpResponse.ok200().withJson(toJson(edge)).build();
                })
                .whenException(e -> log.error("Error getting edge", e));
    }
    
    /**
     * POST /api/v1/graph/edges/query
     * Queries edges with filtering.
     */
    public Promise<HttpResponse> queryEdges(HttpRequest request) {
        log.debug("POST /api/v1/graph/edges/query");
        
        return request.loadBody()
                .then(body -> {
                    GraphQuery query = parseQueryFromJson(body.asString(StandardCharsets.UTF_8));
                    return graphPlugin.queryEdges(query);
                })
                .map(edges -> HttpResponse.ok200()
                        .withJson(toJson(Map.of("edges", edges, "count", edges.size())))
                        .build())
                .whenException(e -> log.error("Error querying edges", e));
    }
    
    // ========================================================================
    // Traversal Operations
    // ========================================================================
    
    /**
     * GET /api/v1/graph/nodes/:id/neighbors?depth=2
     * Gets neighbor nodes at specified depth.
     */
    public Promise<HttpResponse> getNeighbors(HttpRequest request) {
        String nodeId = request.getPathParameter("id");
        String tenantId = getTenantId(request);
        int depth = Integer.parseInt(request.getQueryParameter("depth"));
        
        log.debug("GET /api/v1/graph/nodes/{}/neighbors?depth={}", nodeId, depth);
        
        return graphPlugin.getNeighbors(nodeId, depth, tenantId)
                .map(neighbors -> HttpResponse.ok200()
                        .withJson(toJson(Map.of("neighbors", neighbors, "count", neighbors.size())))
                        .build())
                .whenException(e -> log.error("Error getting neighbors", e));
    }
    
    /**
     * GET /api/v1/graph/nodes/:id/edges
     * Gets all edges connected to a node.
     */
    public Promise<HttpResponse> getNodeEdges(HttpRequest request) {
        String nodeId = request.getPathParameter("id");
        String tenantId = getTenantId(request);
        
        log.debug("GET /api/v1/graph/nodes/{}/edges", nodeId);
        
        return graphPlugin.getNodeEdges(nodeId, tenantId)
                .map(edges -> HttpResponse.ok200()
                        .withJson(toJson(Map.of("edges", edges, "count", edges.size())))
                        .build())
                .whenException(e -> log.error("Error getting node edges", e));
    }
    
    /**
     * POST /api/v1/graph/path/shortest
     * Finds shortest path between two nodes.
     */
    public Promise<HttpResponse> findShortestPath(HttpRequest request) {
        log.debug("POST /api/v1/graph/path/shortest");
        
        return request.loadBody()
                .then(body -> {
                    Map<String, String> params = parsePathRequest(body.asString(StandardCharsets.UTF_8));
                    String sourceId = params.get("sourceNodeId");
                    String targetId = params.get("targetNodeId");
                    String tenantId = params.get("tenantId");
                    
                    return graphPlugin.findShortestPath(sourceId, targetId, tenantId);
                })
                .map(path -> HttpResponse.ok200()
                        .withJson(toJson(Map.of("path", path, "length", path.size())))
                        .build())
                .whenException(e -> log.error("Error finding shortest path", e));
    }
    
    /**
     * POST /api/v1/graph/path/all
     * Finds all paths between two nodes.
     */
    public Promise<HttpResponse> findAllPaths(HttpRequest request) {
        log.debug("POST /api/v1/graph/path/all");
        
        return request.loadBody()
                .then(body -> {
                    Map<String, Object> params = parseAllPathsRequest(body.asString(StandardCharsets.UTF_8));
                    String sourceId = (String) params.get("sourceNodeId");
                    String targetId = (String) params.get("targetNodeId");
                    int maxLength = (Integer) params.get("maxLength");
                    String tenantId = (String) params.get("tenantId");
                    
                    return graphPlugin.findAllPaths(sourceId, targetId, maxLength, tenantId);
                })
                .map(paths -> HttpResponse.ok200()
                        .withJson(toJson(Map.of("paths", paths, "count", paths.size())))
                        .build())
                .whenException(e -> log.error("Error finding all paths", e));
    }
    
    // ========================================================================
    // Helper Methods
    // ========================================================================
    
    private String getTenantId(HttpRequest request) {
        // Extract tenant ID from header or query parameter
        String tenantId = TenantExtractor.fromHttp(request).orElse(null);
        if (tenantId == null) {
            tenantId = request.getQueryParameter("tenantId");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID is required");
        }
        return tenantId;
    }
    
    private GraphNode parseNodeFromJson(String json) {
        return jsonMapper.parseNode(json);
    }
    
    private GraphEdge parseEdgeFromJson(String json) {
        return jsonMapper.parseEdge(json);
    }
    
    private GraphQuery parseQueryFromJson(String json) {
        return jsonMapper.parseQuery(json);
    }
    
    private List<GraphNode> parseNodesFromJson(String json) {
        return jsonMapper.parseNodes(json);
    }
    
    private Map<String, String> parsePathRequest(String json) {
        JsonMapper.PathRequest request = jsonMapper.parsePathRequest(json);
        return Map.of(
            "sourceNodeId", request.sourceNodeId(),
            "targetNodeId", request.targetNodeId(),
            "tenantId", request.tenantId()
        );
    }
    
    private Map<String, Object> parseAllPathsRequest(String json) {
        JsonMapper.PathRequest request = jsonMapper.parsePathRequest(json);
        return Map.of(
            "sourceNodeId", request.sourceNodeId(),
            "targetNodeId", request.targetNodeId(),
            "tenantId", request.tenantId(),
            "maxLength", request.maxLength() != null ? request.maxLength() : 10
        );
    }
    
    private String toJson(Object obj) {
        return jsonMapper.toJson(obj);
    }
}
