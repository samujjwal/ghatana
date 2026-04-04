package com.ghatana.datacloud.plugins.knowledgegraph.analytics;

import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphEdge;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphNode;
import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphQuery;
import com.ghatana.datacloud.plugins.knowledgegraph.storage.GraphStorageAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of graph analytics engine with centrality algorithms.
 * 
 * <p>Provides comprehensive graph analysis including:
 * <ul>
 *   <li>Betweenness centrality - identifies bridge nodes</li>
 *   <li>Closeness centrality - identifies well-connected nodes</li>
 *   <li>Community detection - identifies clusters</li>
 *   <li>Impact analysis - identifies affected nodes</li>
 *   <li>Graph statistics - overall graph metrics</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose Graph analytics implementation
 * @doc.layer analytics
 * @doc.pattern Strategy
 */
@Slf4j
public class CentralityAnalyticsEngine implements GraphAnalyticsEngine {
    
    private final GraphStorageAdapter storageAdapter;
    private volatile boolean initialized = false;
    private volatile boolean running = false;
    
    public CentralityAnalyticsEngine(GraphStorageAdapter storageAdapter) {
        this.storageAdapter = storageAdapter;
        log.info("CentralityAnalyticsEngine created");
    }
    
    @Override
    public void initialize(Map<String, Object> config) {
        log.info("Initializing CentralityAnalyticsEngine");
        initialized = true;
        log.info("CentralityAnalyticsEngine initialized");
    }
    
    @Override
    public void start() {
        if (!initialized) {
            throw new IllegalStateException("Analytics engine must be initialized before starting");
        }
        running = true;
        log.info("CentralityAnalyticsEngine started");
    }
    
    @Override
    public void stop() {
        running = false;
        log.info("CentralityAnalyticsEngine stopped");
    }
    
    @Override
    public void shutdown() {
        stop();
        log.info("CentralityAnalyticsEngine shutdown");
    }
    
    @Override
    public boolean isHealthy() {
        return initialized && running;
    }
    
    @Override
    public Map<String, Double> calculateBetweennessCentrality(String tenantId) {
        ensureRunning();
        log.debug("Calculating betweenness centrality for tenant: {}", tenantId);
        
        // Get all nodes for the tenant
        GraphQuery query = GraphQuery.builder()
                .tenantId(tenantId)
                .limit(10000)
                .build();
        
        List<GraphNode> nodes = storageAdapter.queryNodes(query);
        Map<String, Double> centrality = new HashMap<>();
        
        // Initialize centrality scores
        for (GraphNode node : nodes) {
            centrality.put(node.getId(), 0.0);
        }
        
        // Calculate betweenness centrality using Brandes' algorithm
        for (GraphNode source : nodes) {
            Stack<String> stack = new Stack<>();
            Map<String, List<String>> predecessors = new HashMap<>();
            Map<String, Integer> distance = new HashMap<>();
            Map<String, Integer> pathCount = new HashMap<>();
            Map<String, Double> dependency = new HashMap<>();
            
            // Initialize
            for (GraphNode node : nodes) {
                predecessors.put(node.getId(), new ArrayList<>());
                distance.put(node.getId(), -1);
                pathCount.put(node.getId(), 0);
                dependency.put(node.getId(), 0.0);
            }
            
            distance.put(source.getId(), 0);
            pathCount.put(source.getId(), 1);
            
            Queue<String> queue = new LinkedList<>();
            queue.offer(source.getId());
            
            // BFS
            while (!queue.isEmpty()) {
                String v = queue.poll();
                stack.push(v);
                
                List<GraphEdge> edges = storageAdapter.getOutgoingEdges(v, tenantId);
                for (GraphEdge edge : edges) {
                    String w = edge.getTargetNodeId();
                    
                    // First time visiting w
                    if (distance.get(w) < 0) {
                        queue.offer(w);
                        distance.put(w, distance.get(v) + 1);
                    }
                    
                    // Shortest path to w via v
                    if (distance.get(w) == distance.get(v) + 1) {
                        pathCount.put(w, pathCount.get(w) + pathCount.get(v));
                        predecessors.get(w).add(v);
                    }
                }
            }
            
            // Accumulation
            while (!stack.isEmpty()) {
                String w = stack.pop();
                for (String v : predecessors.get(w)) {
                    double contrib = (pathCount.get(v) / (double) pathCount.get(w)) * (1.0 + dependency.get(w));
                    dependency.put(v, dependency.get(v) + contrib);
                }
                if (!w.equals(source.getId())) {
                    centrality.put(w, centrality.get(w) + dependency.get(w));
                }
            }
        }
        
        // Normalize
        int n = nodes.size();
        if (n > 2) {
            double normFactor = 2.0 / ((n - 1) * (n - 2));
            for (String nodeId : centrality.keySet()) {
                centrality.put(nodeId, centrality.get(nodeId) * normFactor);
            }
        }
        
        log.debug("Betweenness centrality calculated for {} nodes", centrality.size());
        return centrality;
    }
    
    @Override
    public Map<String, Double> calculateClosenessCentrality(String tenantId) {
        ensureRunning();
        log.debug("Calculating closeness centrality for tenant: {}", tenantId);
        
        GraphQuery query = GraphQuery.builder()
                .tenantId(tenantId)
                .limit(10000)
                .build();
        
        List<GraphNode> nodes = storageAdapter.queryNodes(query);
        Map<String, Double> centrality = new HashMap<>();
        
        for (GraphNode source : nodes) {
            Map<String, Integer> distances = new HashMap<>();
            Queue<String> queue = new LinkedList<>();
            Set<String> visited = new HashSet<>();
            
            distances.put(source.getId(), 0);
            queue.offer(source.getId());
            visited.add(source.getId());
            
            // BFS to calculate distances
            while (!queue.isEmpty()) {
                String current = queue.poll();
                int currentDist = distances.get(current);
                
                List<GraphEdge> edges = storageAdapter.getOutgoingEdges(current, tenantId);
                for (GraphEdge edge : edges) {
                    String neighbor = edge.getTargetNodeId();
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        distances.put(neighbor, currentDist + 1);
                        queue.offer(neighbor);
                    }
                }
            }
            
            // Calculate closeness
            int totalDistance = distances.values().stream()
                    .mapToInt(Integer::intValue)
                    .sum();
            
            double closeness = totalDistance > 0 ? (distances.size() - 1.0) / totalDistance : 0.0;
            centrality.put(source.getId(), closeness);
        }
        
        log.debug("Closeness centrality calculated for {} nodes", centrality.size());
        return centrality;
    }
    
    @Override
    public Map<String, Integer> detectCommunities(String tenantId) {
        ensureRunning();
        log.debug("Detecting communities for tenant: {}", tenantId);
        
        GraphQuery query = GraphQuery.builder()
                .tenantId(tenantId)
                .limit(10000)
                .build();
        
        List<GraphNode> nodes = storageAdapter.queryNodes(query);
        Map<String, Integer> communities = new HashMap<>();
        
        // Simple connected components algorithm
        Set<String> visited = new HashSet<>();
        int communityId = 0;
        
        for (GraphNode node : nodes) {
            if (!visited.contains(node.getId())) {
                // BFS to find connected component
                Queue<String> queue = new LinkedList<>();
                queue.offer(node.getId());
                visited.add(node.getId());
                
                while (!queue.isEmpty()) {
                    String current = queue.poll();
                    communities.put(current, communityId);
                    
                    List<GraphEdge> edges = storageAdapter.getNodeEdges(current, tenantId);
                    for (GraphEdge edge : edges) {
                        String neighbor = edge.connects(current) 
                                ? (edge.getSourceNodeId().equals(current) 
                                        ? edge.getTargetNodeId() 
                                        : edge.getSourceNodeId())
                                : null;
                        
                        if (neighbor != null && !visited.contains(neighbor)) {
                            visited.add(neighbor);
                            queue.offer(neighbor);
                        }
                    }
                }
                
                communityId++;
            }
        }
        
        log.debug("Detected {} communities with {} nodes", communityId, communities.size());
        return communities;
    }
    
    @Override
    public List<GraphNode> analyzeImpact(String nodeId, int depth, String tenantId) {
        ensureRunning();
        log.debug("Analyzing impact for node: {}, depth: {}", nodeId, depth);
        
        Set<String> impactedNodes = new HashSet<>();
        Queue<NodeDepth> queue = new LinkedList<>();
        
        queue.offer(new NodeDepth(nodeId, 0));
        impactedNodes.add(nodeId);
        
        while (!queue.isEmpty()) {
            NodeDepth current = queue.poll();
            
            if (current.depth >= depth) {
                continue;
            }
            
            // Get all nodes that depend on current node (incoming edges)
            List<GraphEdge> incomingEdges = storageAdapter.getIncomingEdges(current.nodeId, tenantId);
            
            for (GraphEdge edge : incomingEdges) {
                String dependentNode = edge.getSourceNodeId();
                
                if (!impactedNodes.contains(dependentNode)) {
                    impactedNodes.add(dependentNode);
                    queue.offer(new NodeDepth(dependentNode, current.depth + 1));
                }
            }
        }
        
        // Remove the source node from results
        impactedNodes.remove(nodeId);
        
        // Fetch full node objects
        List<GraphNode> result = impactedNodes.stream()
                .map(id -> storageAdapter.getNode(id, tenantId))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        
        log.debug("Impact analysis found {} affected nodes", result.size());
        return result;
    }
    
    @Override
    public GraphStatistics calculateStatistics(String tenantId) {
        ensureRunning();
        log.debug("Calculating graph statistics for tenant: {}", tenantId);
        
        GraphQuery nodeQuery = GraphQuery.builder()
                .tenantId(tenantId)
                .limit(100000)
                .build();
        
        GraphQuery edgeQuery = GraphQuery.builder()
                .tenantId(tenantId)
                .limit(100000)
                .build();
        
        List<GraphNode> nodes = storageAdapter.queryNodes(nodeQuery);
        List<GraphEdge> edges = storageAdapter.queryEdges(edgeQuery);
        
        int nodeCount = nodes.size();
        int edgeCount = edges.size();
        
        // Calculate degree statistics
        Map<String, Integer> degrees = new HashMap<>();
        for (GraphNode node : nodes) {
            degrees.put(node.getId(), 0);
        }
        
        for (GraphEdge edge : edges) {
            degrees.merge(edge.getSourceNodeId(), 1, Integer::sum);
            degrees.merge(edge.getTargetNodeId(), 1, Integer::sum);
        }
        
        int maxDegree = degrees.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        int minDegree = degrees.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        double averageDegree = degrees.values().stream().mapToInt(Integer::intValue).average().orElse(0.0);
        
        // Calculate density
        double density = nodeCount > 1 
                ? (2.0 * edgeCount) / (nodeCount * (nodeCount - 1))
                : 0.0;
        
        // Calculate connected components
        Map<String, Integer> communities = detectCommunities(tenantId);
        int connectedComponents = communities.values().stream()
                .collect(Collectors.toSet())
                .size();
        
        GraphStatistics stats = new GraphStatistics(
                nodeCount,
                edgeCount,
                averageDegree,
                maxDegree,
                minDegree,
                density,
                connectedComponents
        );
        
        log.debug("Graph statistics calculated: {}", stats);
        return stats;
    }
    
    private void ensureRunning() {
        if (!running) {
            throw new IllegalStateException("Analytics engine is not running");
        }
    }
    
    private record NodeDepth(String nodeId, int depth) {}
}
