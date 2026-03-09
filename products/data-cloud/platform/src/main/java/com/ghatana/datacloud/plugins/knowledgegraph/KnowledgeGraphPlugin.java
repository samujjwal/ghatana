package com.ghatana.datacloud.plugins.knowledgegraph;

import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphEdge;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphNode;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphQuery;
import com.ghatana.platform.plugin.Plugin;
import io.activej.promise.Promise;

import java.util.List;

/**
 * Data-Cloud Knowledge Graph Plugin interface.
 * 
 * <p>Provides graph database capabilities as a first-class Data-Cloud plugin.
 * Supports nodes, edges, queries, traversals, and analytics operations.
 * 
 * <p><b>Plugin Type:</b> GRAPH_DATABASE
 * 
 * <p><b>Capabilities:</b>
 * <ul>
 *   <li>Node and edge CRUD operations</li>
 *   <li>Graph queries with filtering and pagination</li>
 *   <li>Graph traversal algorithms</li>
 *   <li>Graph analytics (centrality, communities, paths)</li>
 *   <li>Multi-tenant isolation</li>
 *   <li>Async operations with ActiveJ Promises</li>
 * </ul>
 * 
 * <p><b>Thread Safety:</b> All operations are thread-safe and non-blocking
 * 
 * <p><b>Usage:</b>
 * <pre>{@code
 * KnowledgeGraphPlugin plugin = pluginRegistry.getPlugin("knowledge-graph");
 * 
 * // Create node
 * GraphNode node = GraphNode.builder()
 *     .id("node-1")
 *     .type("CLASS")
 *     .properties(Map.of("name", "UserService"))
 *     .tenantId("tenant-123")
 *     .build();
 * plugin.createNode(node).whenComplete((result, error) -> {
 *     if (error == null) {
 *         System.out.println("Node created: " + result.getId());
 *     }
 * });
 * 
 * // Query nodes
 * GraphQuery query = GraphQuery.builder()
 *     .nodeTypes(Set.of("CLASS"))
 *     .tenantId("tenant-123")
 *     .build();
 * plugin.queryNodes(query).whenComplete((nodes, error) -> {
 *     if (error == null) {
 *         System.out.println("Found " + nodes.size() + " nodes");
 *     }
 * });
 * }</pre>
 * 
 * @doc.type interface
 * @doc.purpose Knowledge Graph plugin for Data-Cloud platform
 * @doc.layer plugin
 * @doc.pattern Plugin, Repository
 */
public interface KnowledgeGraphPlugin extends Plugin {
    
    // ========================================================================
    // Node Operations
    // ========================================================================
    
    /**
     * Creates a new node in the graph.
     * 
     * @param node the node to create (must have valid id, type, and tenantId)
     * @return Promise with the created node
     * @throws IllegalArgumentException if node validation fails
     */
    Promise<GraphNode> createNode(GraphNode node);
    
    /**
     * Gets a node by ID.
     * 
     * @param nodeId the node ID
     * @param tenantId the tenant ID for security validation
     * @return Promise with the node, or empty if not found
     */
    Promise<GraphNode> getNode(String nodeId, String tenantId);
    
    /**
     * Updates an existing node.
     * 
     * @param node the node with updated values
     * @return Promise with the updated node
     * @throws IllegalArgumentException if node validation fails
     */
    Promise<GraphNode> updateNode(GraphNode node);
    
    /**
     * Deletes a node and all its connected edges.
     * 
     * @param nodeId the node ID to delete
     * @param tenantId the tenant ID for security validation
     * @return Promise with true if deleted, false if not found
     */
    Promise<Boolean> deleteNode(String nodeId, String tenantId);
    
    /**
     * Queries nodes based on filter criteria.
     * 
     * @param query the query specification
     * @return Promise with list of matching nodes
     */
    Promise<List<GraphNode>> queryNodes(GraphQuery query);
    
    // ========================================================================
    // Edge Operations
    // ========================================================================
    
    /**
     * Creates a new edge in the graph.
     * 
     * @param edge the edge to create (must have valid source, target, type, and tenantId)
     * @return Promise with the created edge
     * @throws IllegalArgumentException if edge validation fails or nodes don't exist
     */
    Promise<GraphEdge> createEdge(GraphEdge edge);
    
    /**
     * Gets an edge by ID.
     * 
     * @param edgeId the edge ID
     * @param tenantId the tenant ID for security validation
     * @return Promise with the edge, or empty if not found
     */
    Promise<GraphEdge> getEdge(String edgeId, String tenantId);
    
    /**
     * Updates an existing edge.
     * 
     * @param edge the edge with updated values
     * @return Promise with the updated edge
     * @throws IllegalArgumentException if edge validation fails
     */
    Promise<GraphEdge> updateEdge(GraphEdge edge);
    
    /**
     * Deletes an edge.
     * 
     * @param edgeId the edge ID to delete
     * @param tenantId the tenant ID for security validation
     * @return Promise with true if deleted, false if not found
     */
    Promise<Boolean> deleteEdge(String edgeId, String tenantId);
    
    /**
     * Queries edges based on filter criteria.
     * 
     * @param query the query specification
     * @return Promise with list of matching edges
     */
    Promise<List<GraphEdge>> queryEdges(GraphQuery query);
    
    /**
     * Gets all edges connected to a node (incoming and outgoing).
     * 
     * @param nodeId the node ID
     * @param tenantId the tenant ID for security validation
     * @return Promise with list of connected edges
     */
    Promise<List<GraphEdge>> getNodeEdges(String nodeId, String tenantId);
    
    /**
     * Gets outgoing edges from a node.
     * 
     * @param nodeId the node ID
     * @param tenantId the tenant ID for security validation
     * @return Promise with list of outgoing edges
     */
    Promise<List<GraphEdge>> getOutgoingEdges(String nodeId, String tenantId);
    
    /**
     * Gets incoming edges to a node.
     * 
     * @param nodeId the node ID
     * @param tenantId the tenant ID for security validation
     * @return Promise with list of incoming edges
     */
    Promise<List<GraphEdge>> getIncomingEdges(String nodeId, String tenantId);
    
    // ========================================================================
    // Traversal Operations
    // ========================================================================
    
    /**
     * Gets neighbor nodes at a specific depth.
     * 
     * @param nodeId the starting node ID
     * @param depth the traversal depth (1 = direct neighbors)
     * @param tenantId the tenant ID for security validation
     * @return Promise with list of neighbor nodes
     */
    Promise<List<GraphNode>> getNeighbors(String nodeId, int depth, String tenantId);
    
    /**
     * Finds the shortest path between two nodes.
     * 
     * @param sourceNodeId the source node ID
     * @param targetNodeId the target node ID
     * @param tenantId the tenant ID for security validation
     * @return Promise with list of nodes representing the path, or empty if no path exists
     */
    Promise<List<GraphNode>> findShortestPath(String sourceNodeId, String targetNodeId, String tenantId);
    
    /**
     * Finds all paths between two nodes up to a maximum length.
     * 
     * @param sourceNodeId the source node ID
     * @param targetNodeId the target node ID
     * @param maxLength the maximum path length
     * @param tenantId the tenant ID for security validation
     * @return Promise with list of paths (each path is a list of nodes)
     */
    Promise<List<List<GraphNode>>> findAllPaths(String sourceNodeId, String targetNodeId, int maxLength, String tenantId);
    
    // ========================================================================
    // Batch Operations
    // ========================================================================
    
    /**
     * Creates multiple nodes in a single batch operation.
     * 
     * @param nodes the list of nodes to create
     * @return Promise with list of created nodes
     */
    Promise<List<GraphNode>> batchCreateNodes(List<GraphNode> nodes);
    
    /**
     * Creates multiple edges in a single batch operation.
     * 
     * @param edges the list of edges to create
     * @return Promise with list of created edges
     */
    Promise<List<GraphEdge>> batchCreateEdges(List<GraphEdge> edges);
    
    /**
     * Deletes multiple nodes in a single batch operation.
     * 
     * @param nodeIds the list of node IDs to delete
     * @param tenantId the tenant ID for security validation
     * @return Promise with count of deleted nodes
     */
    Promise<Integer> batchDeleteNodes(List<String> nodeIds, String tenantId);
    
    /**
     * Deletes multiple edges in a single batch operation.
     * 
     * @param edgeIds the list of edge IDs to delete
     * @param tenantId the tenant ID for security validation
     * @return Promise with count of deleted edges
     */
    Promise<Integer> batchDeleteEdges(List<String> edgeIds, String tenantId);
}
