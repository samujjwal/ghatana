package com.ghatana.datacloud.plugins.knowledgegraph.analytics;

import com.ghatana.datacloud.plugins.knowledgegraph.model.GraphNode;

import java.util.List;
import java.util.Map;

/**
 * Graph analytics engine interface.
 * 
 * <p>Provides graph analysis algorithms including:
 * <ul>
 *   <li>Centrality analysis (betweenness, closeness, eigenvector)</li>
 *   <li>Community detection</li>
 *   <li>Impact analysis</li>
 *   <li>Pattern matching</li>
 *   <li>Graph statistics</li>
 * </ul>
 * 
 * @doc.type interface
 * @doc.purpose Graph analytics algorithms
 * @doc.layer analytics
 * @doc.pattern Strategy
 */
public interface GraphAnalyticsEngine {
    
    /**
     * Initializes the analytics engine.
     */
    void initialize(Map<String, Object> config);
    
    /**
     * Starts the analytics engine.
     */
    void start();
    
    /**
     * Stops the analytics engine.
     */
    void stop();
    
    /**
     * Shuts down the analytics engine.
     */
    void shutdown();
    
    /**
     * Checks if the analytics engine is healthy.
     */
    boolean isHealthy();
    
    /**
     * Calculates betweenness centrality for all nodes.
     * Measures how often a node appears on shortest paths.
     */
    Map<String, Double> calculateBetweennessCentrality(String tenantId);
    
    /**
     * Calculates closeness centrality for all nodes.
     * Measures average distance to all other nodes.
     */
    Map<String, Double> calculateClosenessCentrality(String tenantId);
    
    /**
     * Detects communities in the graph using modularity optimization.
     */
    Map<String, Integer> detectCommunities(String tenantId);
    
    /**
     * Analyzes the impact of a node on the graph.
     * Returns nodes that would be affected if this node changes.
     */
    List<GraphNode> analyzeImpact(String nodeId, int depth, String tenantId);
    
    /**
     * Calculates graph statistics.
     */
    GraphStatistics calculateStatistics(String tenantId);
    
    /**
     * Graph statistics result.
     */
    record GraphStatistics(
        int nodeCount,
        int edgeCount,
        double averageDegree,
        int maxDegree,
        int minDegree,
        double density,
        int connectedComponents
    ) {}
}
