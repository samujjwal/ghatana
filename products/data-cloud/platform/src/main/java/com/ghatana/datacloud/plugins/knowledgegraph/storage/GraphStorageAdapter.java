package com.ghatana.datacloud.plugins.knowledgegraph.storage;

import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphEdge;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphNode;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphQuery;

import java.util.List;
import java.util.Map;

/**
 * Storage adapter interface for graph data persistence.
 * 
 * <p>Abstracts the underlying storage mechanism for graph nodes and edges.
 * Implementations can use Data-Cloud EntityRepository, dedicated graph databases,
 * or other storage backends.
 * 
 * <p><b>Thread Safety:</b> All operations must be thread-safe
 * 
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Persist and retrieve graph nodes and edges</li>
 *   <li>Execute graph queries with filtering</li>
 *   <li>Manage indexes for efficient queries</li>
 *   <li>Ensure multi-tenant isolation</li>
 *   <li>Handle batch operations efficiently</li>
 * </ul>
 * 
 * @doc.type interface
 * @doc.purpose Storage abstraction for graph data
 * @doc.layer storage
 * @doc.pattern Adapter, Repository
 */
public interface GraphStorageAdapter {
    
    /**
     * Initializes the storage adapter with configuration.
     */
    void initialize(Map<String, Object> config);
    
    /**
     * Starts the storage adapter.
     */
    void start();
    
    /**
     * Stops the storage adapter.
     */
    void stop();
    
    /**
     * Shuts down the storage adapter and releases resources.
     */
    void shutdown();
    
    /**
     * Checks if the storage adapter is healthy.
     */
    boolean isHealthy();
    
    // Node operations
    GraphNode createNode(GraphNode node);
    GraphNode getNode(String nodeId, String tenantId);
    GraphNode updateNode(GraphNode node);
    boolean deleteNode(String nodeId, String tenantId);
    List<GraphNode> queryNodes(GraphQuery query);
    List<GraphNode> batchCreateNodes(List<GraphNode> nodes);
    int batchDeleteNodes(List<String> nodeIds, String tenantId);
    
    // Edge operations
    GraphEdge createEdge(GraphEdge edge);
    GraphEdge getEdge(String edgeId, String tenantId);
    GraphEdge updateEdge(GraphEdge edge);
    boolean deleteEdge(String edgeId, String tenantId);
    List<GraphEdge> queryEdges(GraphQuery query);
    List<GraphEdge> getNodeEdges(String nodeId, String tenantId);
    List<GraphEdge> getOutgoingEdges(String nodeId, String tenantId);
    List<GraphEdge> getIncomingEdges(String nodeId, String tenantId);
    List<GraphEdge> batchCreateEdges(List<GraphEdge> edges);
    int batchDeleteEdges(List<String> edgeIds, String tenantId);
}
