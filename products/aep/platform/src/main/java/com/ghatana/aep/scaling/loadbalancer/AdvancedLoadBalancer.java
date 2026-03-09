package com.ghatana.aep.scaling.loadbalancer;

import io.activej.promise.Promise;
import io.activej.eventloop.Eventloop;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ghatana.aep.scaling.models.AutoScalingModels.LoadBalancingNode;
import com.ghatana.aep.scaling.models.AutoScalingModels.LoadBalancingAlgorithm;
import com.ghatana.aep.scaling.models.AutoScalingModels.RoutingResult;
import com.ghatana.aep.scaling.models.AutoScalingModels.RoutingExecutionResult;
import com.ghatana.aep.scaling.models.AutoScalingModels.WorkloadDistributionResult;
import com.ghatana.aep.scaling.models.AutoScalingModels.WorkloadAllocation;
import com.ghatana.aep.scaling.models.AutoScalingModels.DistributionExecutionResult;
import com.ghatana.aep.scaling.models.AutoScalingModels.WorkloadRequirements;
import com.ghatana.aep.scaling.models.AutoScalingModels.LoadBalancerMetrics;
import com.ghatana.aep.scaling.models.AutoScalingModels.RoundRobinAlgorithm;
import com.ghatana.aep.scaling.models.AutoScalingModels.LoadBasedAlgorithm;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Advanced Load Balancer that provides intelligent workload distribution,
 * adaptive routing, and resource optimization for horizontal scaling
 * with multiple algorithms and real-time performance monitoring.
 * 
 * @doc.type class
 * @doc.purpose Intelligent load balancing and resource management
 * @doc.layer scaling
 * @doc.pattern Load Balancer
 */
public class AdvancedLoadBalancer {
    
    private static final Logger log = LoggerFactory.getLogger(AdvancedLoadBalancer.class);
    
    private final Eventloop eventloop;
    
    // Load balancing state
    private final Map<String, LoadBalancingNode> nodes = new ConcurrentHashMap<>();
    private final Map<String, LoadBalancingAlgorithm> algorithms = new ConcurrentHashMap<>();
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong routedRequests = new AtomicLong(0);
    
    // Current active algorithm
    private volatile LoadBalancingAlgorithm activeAlgorithm;
    
    public AdvancedLoadBalancer(Eventloop eventloop) {
        this.eventloop = eventloop;
        
        initializeAlgorithms();
        log.info("Advanced Load Balancer initialized");
    }
    
    /**
     * Routes a workload request to the optimal node.
     * 
     * @param requestId Request ID
     * @param context Routing context
     * @return Promise of routing result
     */
    public Promise<RoutingResult> routeRequest(String requestId, Map<String, Object> context) {
        return Promise.ofBlocking(eventloop, () -> {
            Instant startTime = Instant.now();
            totalRequests.incrementAndGet();
            
            try {
                log.debug("Routing request: {} to optimal node", requestId);
                
                // Get healthy nodes
                List<LoadBalancingNode> healthyNodes = getHealthyNodes();
                
                if (healthyNodes.isEmpty()) {
                    return new RoutingResult(
                        requestId,
                        null,
                        "round_robin",
                        false,
                        "No healthy nodes available",
                        java.time.Duration.between(startTime, Instant.now()).toMillis()
                    );
                }
                
                // Select optimal node using active algorithm
                LoadBalancingNode selectedNode = activeAlgorithm.selectNode(healthyNodes, context);
                
                if (selectedNode == null) {
                    return new RoutingResult(
                        requestId,
                        null,
                        activeAlgorithm.getName(),
                        false,
                        "Failed to select optimal node",
                        java.time.Duration.between(startTime, Instant.now()).toMillis()
                    );
                }
                
                // Update routing statistics
                routedRequests.incrementAndGet();
                
                long routingTime = java.time.Duration.between(startTime, Instant.now()).toMillis();
                
                return new RoutingResult(
                    requestId,
                    selectedNode.getNodeId(),
                    activeAlgorithm.getName(),
                    true,
                    null,
                    routingTime
                );
                
            } catch (Exception e) {
                log.error("Request routing failed", e);
                return new RoutingResult(
                    requestId,
                    null,
                    activeAlgorithm.getName(),
                    false,
                    e.getMessage(),
                    java.time.Duration.between(startTime, Instant.now()).toMillis()
                );
            }
        });
    }
    
    /**
     * Distributes workload across multiple nodes.
     * 
     * @param distributionId Distribution ID
     * @param totalWorkload Total workload to distribute
     * @param context Distribution context
     * @return Promise of distribution result
     */
    public Promise<WorkloadDistributionResult> distributeWorkload(String distributionId, double totalWorkload, Map<String, Object> context) {
        return Promise.ofBlocking(eventloop, () -> {
            Instant startTime = Instant.now();
            
            try {
                log.info("Distributing {} workload units across cluster", totalWorkload);
                
                // Get healthy nodes
                List<LoadBalancingNode> availableNodes = getHealthyNodes();
                
                if (availableNodes.isEmpty()) {
                    return new WorkloadDistributionResult(
                        distributionId,
                        Collections.emptyMap(),
                        false,
                        "No nodes available for workload distribution",
                        java.time.Duration.between(startTime, Instant.now()).toMillis(),
                        0.0
                    );
                }
                
                // Calculate optimal distribution
                Map<String, WorkloadAllocation> allocations = calculateOptimalDistribution(
                    availableNodes, totalWorkload);
                
                // Execute workload distribution
                Map<String, DistributionExecutionResult> executionResults = new HashMap<>();
                
                for (Map.Entry<String, WorkloadAllocation> entry : allocations.entrySet()) {
                    String nodeId = entry.getKey();
                    WorkloadAllocation allocation = entry.getValue();
                    
                    try {
                        // Simulate workload distribution execution
                        boolean success = simulateWorkloadDistribution(nodeId, allocation.getAllocatedWorkload());
                        
                        executionResults.put(nodeId, new DistributionExecutionResult(
                            nodeId,
                            success,
                            success ? null : "Distribution failed",
                            java.time.Duration.between(startTime, Instant.now()).toMillis(),
                            success ? allocation.getAllocatedWorkload() : 0.0
                        ));
                    } catch (Exception e) {
                        log.warn("Workload distribution failed for node: {}", nodeId, e);
                        executionResults.put(nodeId, new DistributionExecutionResult(
                            nodeId,
                            false,
                            e.getMessage(),
                            java.time.Duration.between(startTime, Instant.now()).toMillis(),
                            0.0
                        ));
                    }
                }
                
                long distributionTime = java.time.Duration.between(startTime, Instant.now()).toMillis();
                
                return new WorkloadDistributionResult(
                    distributionId,
                    allocations,
                    true,
                    null,
                    distributionTime,
                    totalWorkload
                );
                
            } catch (Exception e) {
                log.error("Workload distribution failed", e);
                return new WorkloadDistributionResult(
                    distributionId,
                    Collections.emptyMap(),
                    false,
                    e.getMessage(),
                    java.time.Duration.between(startTime, Instant.now()).toMillis(),
                    0.0
                );
            }
        });
    }
    
    // Private helper methods
    
    private void initializeAlgorithms() {
        // Initialize load balancing algorithms
        
        // Round Robin
        algorithms.put("round_robin", new RoundRobinAlgorithm());
        
        // Load Based
        algorithms.put("load_based", new LoadBasedAlgorithm());
        
        // Set default active algorithm
        activeAlgorithm = algorithms.get("round_robin");
        
        log.info("Initialized {} load balancing algorithms", algorithms.size());
    }
    
    private List<LoadBalancingNode> getHealthyNodes() {
        return nodes.values().stream()
            .filter(LoadBalancingNode::isHealthy)
            .collect(Collectors.toList());
    }
    
    private Map<String, WorkloadAllocation> calculateOptimalDistribution(
            List<LoadBalancingNode> availableNodes, double totalWorkload) {
        
        Map<String, WorkloadAllocation> allocations = new HashMap<>();
        
        // Calculate total capacity
        double totalCapacity = availableNodes.stream()
            .mapToDouble(LoadBalancingNode::getAvailableCapacity)
            .sum();
        
        // Distribute workload proportionally to capacity
        for (LoadBalancingNode node : availableNodes) {
            double nodeCapacity = node.getAvailableCapacity();
            double allocationRatio = totalCapacity > 0 ? nodeCapacity / totalCapacity : 1.0 / availableNodes.size();
            double allocatedWorkload = totalWorkload * allocationRatio;
            
            allocations.put(node.getNodeId(), new WorkloadAllocation(
                node.getNodeId(),
                allocatedWorkload,
                node.getMaxCapacity()
            ));
        }
        
        return allocations;
    }
    
    private boolean simulateWorkloadDistribution(String nodeId, double workload) {
        // Simulate workload distribution with 90% success rate
        return Math.random() > 0.1;
    }
    
    /**
     * Adds a node to the load balancer.
     * 
     * @param node Load balancing node
     */
    public void addNode(LoadBalancingNode node) {
        nodes.put(node.getNodeId(), node);
        log.info("Added node {} to load balancer", node.getNodeId());
    }
    
    /**
     * Removes a node from the load balancer.
     * 
     * @param nodeId Node ID to remove
     */
    public void removeNode(String nodeId) {
        nodes.remove(nodeId);
        log.info("Removed node {} from load balancer", nodeId);
    }
    
    /**
     * Gets load balancer metrics.
     * 
     * @return Load balancer metrics
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalRequests", totalRequests.get());
        metrics.put("routedRequests", routedRequests.get());
        metrics.put("successRate", totalRequests.get() > 0 ? 
                   (double) routedRequests.get() / totalRequests.get() : 0.0);
        metrics.put("activeNodes", nodes.size());
        metrics.put("healthyNodes", (int) nodes.values().stream()
            .filter(LoadBalancingNode::isHealthy)
            .count());
        metrics.put("activeAlgorithm", activeAlgorithm.getName());
        
        return metrics;
    }
}
