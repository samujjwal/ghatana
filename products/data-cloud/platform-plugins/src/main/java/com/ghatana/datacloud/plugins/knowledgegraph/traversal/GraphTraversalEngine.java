package com.ghatana.datacloud.plugins.knowledgegraph.traversal;

import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphNode;

import java.util.List;
import java.util.Map;

/**
 * Graph traversal engine interface.
 * 
 * <p>Provides graph traversal algorithms including:
 * <ul>
 *   <li>Breadth-first search (BFS)</li>
 *   <li>Depth-first search (DFS)</li>
 *   <li>Shortest path (Dijkstra)</li>
 *   <li>All paths enumeration</li>
 *   <li>Neighbor discovery</li>
 * </ul>
 * 
 * <p><b>Thread Safety:</b> All operations must be thread-safe
 * 
 * @doc.type interface
 * @doc.purpose Graph traversal algorithms
 * @doc.layer engine
 * @doc.pattern Strategy
 */
public interface GraphTraversalEngine {
    
    /**
     * Initializes the traversal engine with configuration.
     */
    void initialize(Map<String, Object> config);
    
    /**
     * Starts the traversal engine.
     */
    void start();
    
    /**
     * Stops the traversal engine.
     */
    void stop();
    
    /**
     * Shuts down the traversal engine.
     */
    void shutdown();
    
    /**
     * Checks if the traversal engine is healthy.
     */
    boolean isHealthy();
    
    /**
     * Gets neighbor nodes at a specific depth using BFS.
     */
    List<GraphNode> getNeighbors(String nodeId, int depth, String tenantId);
    
    /**
     * Finds the shortest path between two nodes using Dijkstra's algorithm.
     */
    List<GraphNode> findShortestPath(String sourceNodeId, String targetNodeId, String tenantId);
    
    /**
     * Finds all paths between two nodes up to a maximum length.
     */
    List<List<GraphNode>> findAllPaths(String sourceNodeId, String targetNodeId, int maxLength, String tenantId);
}
