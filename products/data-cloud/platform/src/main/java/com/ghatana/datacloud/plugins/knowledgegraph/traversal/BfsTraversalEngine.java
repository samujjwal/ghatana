package com.ghatana.datacloud.plugins.knowledgegraph.traversal;

import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphEdge;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphNode;
import com.ghatana.datacloud.plugins.knowledgegraph.storage.GraphStorageAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Breadth-first search based graph traversal engine.
 * 
 * <p>Implements graph traversal algorithms using BFS for neighbor discovery
 * and shortest path finding. Provides efficient traversal with cycle detection
 * and multi-tenant isolation.
 * 
 * <p><b>Algorithms:</b>
 * <ul>
 *   <li>BFS for neighbor discovery</li>
 *   <li>BFS for shortest path (unweighted)</li>
 *   <li>DFS for all paths enumeration</li>
 * </ul>
 * 
 * <p><b>Thread Safety:</b> Thread-safe with concurrent operations support
 * 
 * @doc.type class
 * @doc.purpose BFS-based graph traversal implementation
 * @doc.layer engine
 * @doc.pattern Strategy
 */
@Slf4j
public class BfsTraversalEngine implements GraphTraversalEngine {
    
    private final GraphStorageAdapter storageAdapter;
    private volatile boolean initialized = false;
    private volatile boolean running = false;
    
    public BfsTraversalEngine(GraphStorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
        log.info("BfsTraversalEngine created");
    }
    
    @Override
    public void initialize(Map<String, Object> config) {
        log.info("Initializing BfsTraversalEngine with config: {}", config);
        initialized = true;
        log.info("BfsTraversalEngine initialized successfully");
    }
    
    @Override
    public void start() {
        if (!initialized) {
            throw new IllegalStateException("Traversal engine must be initialized before starting");
        }
        running = true;
        log.info("BfsTraversalEngine started");
    }
    
    @Override
    public void stop() {
        running = false;
        log.info("BfsTraversalEngine stopped");
    }
    
    @Override
    public void shutdown() {
        stop();
        log.info("BfsTraversalEngine shutdown");
    }
    
    @Override
    public boolean isHealthy() {
        return initialized && running;
    }
    
    @Override
    public List<GraphNode> getNeighbors(String nodeId, int depth, String tenantId) {
        ensureRunning();
        
        log.debug("Getting neighbors: nodeId={}, depth={}, tenantId={}", nodeId, depth, tenantId);
        
        Set<String> visited = new HashSet<>();
        List<GraphNode> neighbors = new ArrayList<>();
        Queue<NodeDepth> queue = new LinkedList<>();
        
        // Start with the source node
        GraphNode startNode = storageAdapter.getNode(nodeId, tenantId);
        if (startNode == null) {
            log.warn("Start node not found: {}", nodeId);
            return neighbors;
        }
        
        queue.offer(new NodeDepth(startNode, 0));
        visited.add(nodeId);
        
        while (!queue.isEmpty()) {
            NodeDepth current = queue.poll();
            
            // Skip if we've reached the depth limit
            if (current.depth >= depth) {
                continue;
            }
            
            // Get all edges from current node
            List<GraphEdge> edges = storageAdapter.getOutgoingEdges(current.node.getId(), tenantId);
            
            for (GraphEdge edge : edges) {
                String targetId = edge.getTargetNodeId();
                
                // Skip if already visited
                if (visited.contains(targetId)) {
                    continue;
                }
                
                // Get target node
                GraphNode targetNode = storageAdapter.getNode(targetId, tenantId);
                if (targetNode == null) {
                    continue;
                }
                
                // Add to neighbors if not the start node
                if (!targetId.equals(nodeId)) {
                    neighbors.add(targetNode);
                }
                
                // Mark as visited and add to queue
                visited.add(targetId);
                queue.offer(new NodeDepth(targetNode, current.depth + 1));
            }
        }
        
        log.debug("Found {} neighbors at depth {} for node {}", neighbors.size(), depth, nodeId);
        return neighbors;
    }
    
    @Override
    public List<GraphNode> findShortestPath(String sourceNodeId, String targetNodeId, String tenantId) {
        ensureRunning();
        
        log.debug("Finding shortest path: source={}, target={}, tenantId={}", sourceNodeId, targetNodeId, tenantId);
        
        // BFS for shortest path
        Map<String, String> parent = new HashMap<>();
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        
        queue.offer(sourceNodeId);
        visited.add(sourceNodeId);
        parent.put(sourceNodeId, null);
        
        boolean found = false;
        
        while (!queue.isEmpty() && !found) {
            String currentId = queue.poll();
            
            // Check if we reached the target
            if (currentId.equals(targetNodeId)) {
                found = true;
                break;
            }
            
            // Get outgoing edges
            List<GraphEdge> edges = storageAdapter.getOutgoingEdges(currentId, tenantId);
            
            for (GraphEdge edge : edges) {
                String neighborId = edge.getTargetNodeId();
                
                if (!visited.contains(neighborId)) {
                    visited.add(neighborId);
                    parent.put(neighborId, currentId);
                    queue.offer(neighborId);
                }
            }
        }
        
        if (!found) {
            log.debug("No path found from {} to {}", sourceNodeId, targetNodeId);
            return List.of();
        }
        
        // Reconstruct path
        List<GraphNode> path = new ArrayList<>();
        String currentId = targetNodeId;
        
        while (currentId != null) {
            GraphNode node = storageAdapter.getNode(currentId, tenantId);
            if (node != null) {
                path.add(0, node);
            }
            currentId = parent.get(currentId);
        }
        
        log.debug("Found shortest path with {} nodes", path.size());
        return path;
    }
    
    @Override
    public List<List<GraphNode>> findAllPaths(String sourceNodeId, String targetNodeId, int maxLength, String tenantId) {
        ensureRunning();
        
        log.debug("Finding all paths: source={}, target={}, maxLength={}, tenantId={}", 
                sourceNodeId, targetNodeId, maxLength, tenantId);
        
        List<List<GraphNode>> allPaths = new ArrayList<>();
        List<GraphNode> currentPath = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        // Get source node
        GraphNode sourceNode = storageAdapter.getNode(sourceNodeId, tenantId);
        if (sourceNode == null) {
            log.warn("Source node not found: {}", sourceNodeId);
            return allPaths;
        }
        
        // DFS to find all paths
        findAllPathsDfs(sourceNode, targetNodeId, maxLength, tenantId, currentPath, visited, allPaths);
        
        log.debug("Found {} paths from {} to {}", allPaths.size(), sourceNodeId, targetNodeId);
        return allPaths;
    }
    
    // ========================================================================
    // Private Helper Methods
    // ========================================================================
    
    private void ensureRunning() {
        if (!running) {
            throw new IllegalStateException("Traversal engine is not running");
        }
    }
    
    private void findAllPathsDfs(
            GraphNode currentNode,
            String targetNodeId,
            int maxLength,
            String tenantId,
            List<GraphNode> currentPath,
            Set<String> visited,
            List<List<GraphNode>> allPaths) {
        
        // Add current node to path
        currentPath.add(currentNode);
        visited.add(currentNode.getId());
        
        // Check if we reached the target
        if (currentNode.getId().equals(targetNodeId)) {
            allPaths.add(new ArrayList<>(currentPath));
        } else if (currentPath.size() < maxLength) {
            // Continue exploring if we haven't exceeded max length
            List<GraphEdge> edges = storageAdapter.getOutgoingEdges(currentNode.getId(), tenantId);
            
            for (GraphEdge edge : edges) {
                String neighborId = edge.getTargetNodeId();
                
                if (!visited.contains(neighborId)) {
                    GraphNode neighborNode = storageAdapter.getNode(neighborId, tenantId);
                    if (neighborNode != null) {
                        findAllPathsDfs(neighborNode, targetNodeId, maxLength, tenantId, 
                                currentPath, visited, allPaths);
                    }
                }
            }
        }
        
        // Backtrack
        currentPath.remove(currentPath.size() - 1);
        visited.remove(currentNode.getId());
    }
    
    /**
     * Helper class to track node and its depth during BFS.
     */
    private static class NodeDepth {
        final GraphNode node;
        final int depth;
        
        NodeDepth(GraphNode node, int depth) {
            this.node = node;
            this.depth = depth;
        }
    }
}
