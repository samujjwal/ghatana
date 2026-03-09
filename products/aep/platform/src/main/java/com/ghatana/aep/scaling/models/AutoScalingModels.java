package com.ghatana.aep.scaling.models;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Auto-scaling models for the AEP platform
 * 
 * @doc.type class
 * @doc.purpose Auto-scaling data models and interfaces
 * @doc.layer scaling
 * @doc.pattern Auto Scaling
 */
public class AutoScalingModels {
    
    private static final Logger log = LoggerFactory.getLogger(AutoScalingModels.class);
    
    /**
     * Scaling decision types
     */
    public enum ScalingDecision {
        SCALE_UP,
        SCALE_DOWN,
        MAINTAIN,
        REBALANCE
    }
    
    /**
     * Scaling trigger types
     */
    public enum ScalingTrigger {
        CPU_THRESHOLD,
        MEMORY_THRESHOLD,
        REQUEST_RATE,
        CUSTOM_METRIC,
        SCHEDULED
    }
    
    /**
     * Scaling metrics
     */
    public static class ScalingMetrics {
        private final String metricId;
        private final double value;
        private final String unit;
        private final long timestamp;
        private final Map<String, Object> metadata;
        
        // Additional fields for AutoScalingEngine compatibility
        private long totalScalingEvents;
        private long successfulScalingEvents;
        private double successRate;
        private int activeScalingStates;
        
        public ScalingMetrics() {
            // Default constructor for AutoScalingEngine
            this.metricId = "scaling-metrics";
            this.value = 0.0;
            this.unit = "count";
            this.timestamp = System.currentTimeMillis();
            this.metadata = new HashMap<>();
            
            // Initialize additional fields
            this.totalScalingEvents = 0;
            this.successfulScalingEvents = 0;
            this.successRate = 0.0;
            this.activeScalingStates = 0;
        }
        
        public ScalingMetrics(String metricId, double value, String unit) {
            this.metricId = metricId;
            this.value = value;
            this.unit = unit;
            this.timestamp = System.currentTimeMillis();
            this.metadata = new HashMap<>();
            
            // Initialize additional fields
            this.totalScalingEvents = 0;
            this.successfulScalingEvents = 0;
            this.successRate = 0.0;
            this.activeScalingStates = 0;
        }
        
        public String getMetricId() { return metricId; }
        public double getValue() { return value; }
        public String getUnit() { return unit; }
        public long getTimestamp() { return timestamp; }
        public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
        
        // AutoScalingEngine compatibility methods
        public long getTotalScalingEvents() { return totalScalingEvents; }
        public long getSuccessfulScalingEvents() { return successfulScalingEvents; }
        public double getSuccessRate() { return successRate; }
        public int getActiveScalingStates() { return activeScalingStates; }
        
        public void setTotalScalingEvents(long totalScalingEvents) { this.totalScalingEvents = totalScalingEvents; }
        public void setSuccessfulScalingEvents(long successfulScalingEvents) { this.successfulScalingEvents = successfulScalingEvents; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
        public void setActiveScalingStates(int activeScalingStates) { this.activeScalingStates = activeScalingStates; }
    }
    
    /**
     * Scaling recommendation
     */
    public static class ScalingRecommendation {
        private final String recommendationId;
        private final ScalingDecision decision;
        private final int targetSize;
        private final ScalingTrigger trigger;
        private final double confidence;
        private final String reasoning;
        private final long timestamp;
        
        public ScalingRecommendation(String recommendationId, ScalingDecision decision, 
                                   int targetSize, ScalingTrigger trigger, double confidence, String reasoning) {
            this.recommendationId = recommendationId;
            this.decision = decision;
            this.targetSize = targetSize;
            this.trigger = trigger;
            this.confidence = confidence;
            this.reasoning = reasoning;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRecommendationId() { return recommendationId; }
        public ScalingDecision getDecision() { return decision; }
        public int getTargetSize() { return targetSize; }
        public ScalingTrigger getTrigger() { return trigger; }
        public double getConfidence() { return confidence; }
        public String getReasoning() { return reasoning; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Auto-scaling configuration
     */
    public static class AutoScalingConfiguration {
        private final String configId;
        private final int minNodes;
        private final int maxNodes;
        private final double scaleUpThreshold;
        private final double scaleDownThreshold;
        private final long cooldownPeriodMs;
        private final Map<String, Object> customSettings;
        
        public AutoScalingConfiguration(String configId, int minNodes, int maxNodes, 
                                     double scaleUpThreshold, double scaleDownThreshold, long cooldownPeriodMs) {
            this.configId = configId;
            this.minNodes = minNodes;
            this.maxNodes = maxNodes;
            this.scaleUpThreshold = scaleUpThreshold;
            this.scaleDownThreshold = scaleDownThreshold;
            this.cooldownPeriodMs = cooldownPeriodMs;
            this.customSettings = new HashMap<>();
        }
        
        public String getConfigId() { return configId; }
        public int getMinNodes() { return minNodes; }
        public int getMaxNodes() { return maxNodes; }
        public double getScaleUpThreshold() { return scaleUpThreshold; }
        public double getScaleDownThreshold() { return scaleDownThreshold; }
        public long getCooldownPeriodMs() { return cooldownPeriodMs; }
        public Map<String, Object> getCustomSettings() { return new HashMap<>(customSettings); }
    }
    
    /**
     * Load balancing node representation
     */
    public static class LoadBalancingNode {
        private final String nodeId;
        private final String address;
        private final int port;
        private final double currentLoad;
        private final double maxCapacity;
        private final boolean isHealthy;
        private final Map<String, Object> metadata;
        private final long lastHeartbeat;
        
        public LoadBalancingNode(String nodeId, String address, int port, double currentLoad, 
                               double maxCapacity, boolean isHealthy, Map<String, Object> metadata) {
            this.nodeId = nodeId;
            this.address = address;
            this.port = port;
            this.currentLoad = currentLoad;
            this.maxCapacity = maxCapacity;
            this.isHealthy = isHealthy;
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            this.lastHeartbeat = System.currentTimeMillis();
        }
        
        public String getNodeId() { return nodeId; }
        public String getAddress() { return address; }
        public int getPort() { return port; }
        public double getCurrentLoad() { return currentLoad; }
        public double getMaxCapacity() { return maxCapacity; }
        public boolean isHealthy() { return isHealthy; }
        public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
        public long getLastHeartbeat() { return lastHeartbeat; }
        
        public double getAvailableCapacity() {
            return maxCapacity - currentLoad;
        }
        
        public double getLoadPercentage() {
            return maxCapacity > 0 ? (currentLoad / maxCapacity) * 100 : 0;
        }
    }
    
    /**
     * Routing result for load balancing decisions
     */
    public static class RoutingResult {
        private final String requestId;
        private final String selectedNodeId;
        private final String algorithm;
        private final boolean success;
        private final String errorMessage;
        private final long routingTimeMs;
        private final Map<String, Object> routingMetadata;
        
        public RoutingResult(String requestId, String selectedNodeId, String algorithm, 
                           boolean success, String errorMessage, long routingTimeMs) {
            this.requestId = requestId;
            this.selectedNodeId = selectedNodeId;
            this.algorithm = algorithm;
            this.success = success;
            this.errorMessage = errorMessage;
            this.routingTimeMs = routingTimeMs;
            this.routingMetadata = new HashMap<>();
        }
        
        public String getRequestId() { return requestId; }
        public String getSelectedNodeId() { return selectedNodeId; }
        public String getAlgorithm() { return algorithm; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public long getRoutingTimeMs() { return routingTimeMs; }
        public Map<String, Object> getRoutingMetadata() { return new HashMap<>(routingMetadata); }
    }
    
    /**
     * Workload distribution result
     */
    public static class WorkloadDistributionResult {
        private final String distributionId;
        private final Map<String, WorkloadAllocation> allocations;
        private final boolean success;
        private final String errorMessage;
        private final long distributionTimeMs;
        private final double totalWorkload;
        
        public WorkloadDistributionResult(String distributionId, Map<String, WorkloadAllocation> allocations,
                                        boolean success, String errorMessage, long distributionTimeMs, double totalWorkload) {
            this.distributionId = distributionId;
            this.allocations = allocations != null ? new HashMap<>(allocations) : new HashMap<>();
            this.success = success;
            this.errorMessage = errorMessage;
            this.distributionTimeMs = distributionTimeMs;
            this.totalWorkload = totalWorkload;
        }
        
        public String getDistributionId() { return distributionId; }
        public Map<String, WorkloadAllocation> getAllocations() { return new HashMap<>(allocations); }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public long getDistributionTimeMs() { return distributionTimeMs; }
        public double getTotalWorkload() { return totalWorkload; }
    }
    
    /**
     * Workload allocation for a specific node
     */
    public static class WorkloadAllocation {
        private final String nodeId;
        private final double allocatedWorkload;
        private final double capacity;
        private final double utilizationPercentage;
        private final Map<String, Object> allocationMetadata;
        
        public WorkloadAllocation(String nodeId, double allocatedWorkload, double capacity) {
            this.nodeId = nodeId;
            this.allocatedWorkload = allocatedWorkload;
            this.capacity = capacity;
            this.utilizationPercentage = capacity > 0 ? (allocatedWorkload / capacity) * 100 : 0;
            this.allocationMetadata = new HashMap<>();
        }
        
        public String getNodeId() { return nodeId; }
        public double getAllocatedWorkload() { return allocatedWorkload; }
        public double getCapacity() { return capacity; }
        public double getUtilizationPercentage() { return utilizationPercentage; }
        public Map<String, Object> getAllocationMetadata() { return new HashMap<>(allocationMetadata); }
    }
    
    /**
     * Routing execution result
     */
    public static class RoutingExecutionResult {
        private final String nodeId;
        private final boolean success;
        private final String errorMessage;
        private final long executionTimeMs;
        private final Map<String, Object> executionMetadata;
        
        public RoutingExecutionResult(String nodeId, boolean success, String errorMessage, long executionTimeMs) {
            this.nodeId = nodeId;
            this.success = success;
            this.errorMessage = errorMessage;
            this.executionTimeMs = executionTimeMs;
            this.executionMetadata = new HashMap<>();
        }
        
        public String getNodeId() { return nodeId; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public Map<String, Object> getExecutionMetadata() { return new HashMap<>(executionMetadata); }
    }
    
    /**
     * Distribution execution result
     */
    public static class DistributionExecutionResult {
        private final String nodeId;
        private final boolean success;
        private final String errorMessage;
        private final long executionTimeMs;
        private final double actualWorkload;
        private final Map<String, Object> executionMetadata;
        
        public DistributionExecutionResult(String nodeId, boolean success, String errorMessage, 
                                        long executionTimeMs, double actualWorkload) {
            this.nodeId = nodeId;
            this.success = success;
            this.errorMessage = errorMessage;
            this.executionTimeMs = executionTimeMs;
            this.actualWorkload = actualWorkload;
            this.executionMetadata = new HashMap<>();
        }
        
        public String getNodeId() { return nodeId; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public long getExecutionTimeMs() { return executionTimeMs; }
        public double getActualWorkload() { return actualWorkload; }
        public Map<String, Object> getExecutionMetadata() { return new HashMap<>(executionMetadata); }
    }
    
    /**
     * Load balancing algorithm interface
     */
    public interface LoadBalancingAlgorithm {
        String getName();
        LoadBalancingNode selectNode(List<LoadBalancingNode> nodes, Map<String, Object> context);
        boolean isApplicable(Map<String, Object> requirements);
    }
    
    /**
     * Round-robin load balancing algorithm
     */
    public static class RoundRobinAlgorithm implements LoadBalancingAlgorithm {
        private final AtomicLong counter = new AtomicLong(0);
        
        @Override
        public String getName() {
            return "ROUND_ROBIN";
        }
        
        @Override
        public LoadBalancingNode selectNode(List<LoadBalancingNode> nodes, Map<String, Object> context) {
            if (nodes.isEmpty()) return null;
            
            List<LoadBalancingNode> healthyNodes = nodes.stream()
                .filter(LoadBalancingNode::isHealthy)
                .collect(Collectors.toList());
                
            if (healthyNodes.isEmpty()) return null;
            
            int index = (int) (counter.getAndIncrement() % healthyNodes.size());
            return healthyNodes.get(index);
        }
        
        @Override
        public boolean isApplicable(Map<String, Object> requirements) {
            return true; // Round-robin is always applicable
        }
    }
    
    /**
     * Load-based load balancing algorithm
     */
    public static class LoadBasedAlgorithm implements LoadBalancingAlgorithm {
        
        @Override
        public String getName() {
            return "LOAD_BASED";
        }
        
        @Override
        public LoadBalancingNode selectNode(List<LoadBalancingNode> nodes, Map<String, Object> context) {
            if (nodes.isEmpty()) return null;
            
            return nodes.stream()
                .filter(LoadBalancingNode::isHealthy)
                .filter(node -> node.getAvailableCapacity() > 0)
                .min(Comparator.comparingDouble(LoadBalancingNode::getCurrentLoad))
                .orElse(null);
        }
        
        @Override
        public boolean isApplicable(Map<String, Object> requirements) {
            return true; // Load-based is always applicable
        }
    }
    
    /**
     * Workload requirements for load balancing
     */
    public static class WorkloadRequirements {
        private final double requiredCapacity;
        private final Map<String, Object> constraints;
        private final String priority;
        private final long timeoutMs;
        
        public WorkloadRequirements(double requiredCapacity, Map<String, Object> constraints, 
                                   String priority, long timeoutMs) {
            this.requiredCapacity = requiredCapacity;
            this.constraints = constraints != null ? new HashMap<>(constraints) : new HashMap<>();
            this.priority = priority;
            this.timeoutMs = timeoutMs;
        }
        
        public double getRequiredCapacity() { return requiredCapacity; }
        public Map<String, Object> getConstraints() { return new HashMap<>(constraints); }
        public String getPriority() { return priority; }
        public long getTimeoutMs() { return timeoutMs; }
    }
    
    /**
     * Load balancing request
     */
    public static class LoadBalancingRequest {
        private final String requestId;
        private final WorkloadRequirements workloadRequirements;
        private final Map<String, Object> requestMetadata;
        private final long timestamp;
        
        public LoadBalancingRequest(String requestId, WorkloadRequirements workloadRequirements, 
                                   Map<String, Object> requestMetadata) {
            this.requestId = requestId;
            this.workloadRequirements = workloadRequirements;
            this.requestMetadata = requestMetadata != null ? new HashMap<>(requestMetadata) : new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRequestId() { return requestId; }
        public WorkloadRequirements getWorkloadRequirements() { return workloadRequirements; }
        public Map<String, Object> getRequestMetadata() { return new HashMap<>(requestMetadata); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Load balancer metrics
     */
    public static class LoadBalancerMetrics {
        private final String loadBalancerId;
        private final int totalNodes;
        private final int healthyNodes;
        private final int unhealthyNodes;
        private final double averageResponseTime;
        private final double throughput;
        private final double errorRate;
        private final long timestamp;
        
        public LoadBalancerMetrics(String loadBalancerId, int totalNodes, int healthyNodes, 
                                  double averageResponseTime, double throughput, double errorRate) {
            this.loadBalancerId = loadBalancerId;
            this.totalNodes = totalNodes;
            this.healthyNodes = healthyNodes;
            this.unhealthyNodes = totalNodes - healthyNodes;
            this.averageResponseTime = averageResponseTime;
            this.throughput = throughput;
            this.errorRate = errorRate;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getLoadBalancerId() { return loadBalancerId; }
        public int getTotalNodes() { return totalNodes; }
        public int getHealthyNodes() { return healthyNodes; }
        public int getUnhealthyNodes() { return unhealthyNodes; }
        public double getAverageResponseTime() { return averageResponseTime; }
        public double getThroughput() { return throughput; }
        public double getErrorRate() { return errorRate; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Node health status
     */
    public static class NodeHealthStatus {
        private final String nodeId;
        private final boolean isHealthy;
        private final String status;
        private final double healthScore;
        private final long lastCheck;
        private final Map<String, Object> healthDetails;
        
        public NodeHealthStatus(String nodeId, boolean isHealthy, String status, double healthScore) {
            this.nodeId = nodeId;
            this.isHealthy = isHealthy;
            this.status = status;
            this.healthScore = healthScore;
            this.lastCheck = System.currentTimeMillis();
            this.healthDetails = new HashMap<>();
        }
        
        public String getNodeId() { return nodeId; }
        public boolean isHealthy() { return isHealthy; }
        public String getStatus() { return status; }
        public double getHealthScore() { return healthScore; }
        public long getLastCheck() { return lastCheck; }
        public Map<String, Object> getHealthDetails() { return new HashMap<>(healthDetails); }
    }
    
    /**
     * Performance issue
     */
    public static class PerformanceIssue {
        private final String issueId;
        private final String nodeId;
        private final String issueType;
        private final String description;
        private final double severity;
        private final long timestamp;
        
        public PerformanceIssue(String issueId, String nodeId, String issueType, 
                               String description, double severity) {
            this.issueId = issueId;
            this.nodeId = nodeId;
            this.issueType = issueType;
            this.description = description;
            this.severity = severity;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getIssueId() { return issueId; }
        public String getNodeId() { return nodeId; }
        public String getIssueType() { return issueType; }
        public String getDescription() { return description; }
        public double getSeverity() { return severity; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Routing pattern
     */
    public static class RoutingPattern {
        private final String patternId;
        private final String algorithm;
        private final double successRate;
        private final double averageLatency;
        private final long totalRequests;
        private final long timestamp;
        
        public RoutingPattern(String patternId, String algorithm, double successRate, 
                             double averageLatency, long totalRequests) {
            this.patternId = patternId;
            this.algorithm = algorithm;
            this.successRate = successRate;
            this.averageLatency = averageLatency;
            this.totalRequests = totalRequests;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getPatternId() { return patternId; }
        public String getAlgorithm() { return algorithm; }
        public double getSuccessRate() { return successRate; }
        public double getAverageLatency() { return averageLatency; }
        public long getTotalRequests() { return totalRequests; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Load balancer insight
     */
    public static class LoadBalancerInsight {
        private final String insightId;
        private final String insightType;
        private final String description;
        private final double confidence;
        private final Map<String, Object> insightData;
        private final long timestamp;
        
        public LoadBalancerInsight(String insightId, String insightType, String description, 
                                   double confidence, Map<String, Object> insightData) {
            this.insightId = insightId;
            this.insightType = insightType;
            this.description = description;
            this.confidence = confidence;
            this.insightData = insightData != null ? new HashMap<>(insightData) : new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getInsightId() { return insightId; }
        public String getInsightType() { return insightType; }
        public String getDescription() { return description; }
        public double getConfidence() { return confidence; }
        public Map<String, Object> getInsightData() { return new HashMap<>(insightData); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Load balancer configuration
     */
    public static class LoadBalancerConfiguration {
        private final String configId;
        private final String algorithm;
        private final Map<String, Object> settings;
        private final boolean healthCheckEnabled;
        private final long healthCheckIntervalMs;
        private final long timestamp;
        
        public LoadBalancerConfiguration(String configId, String algorithm, Map<String, Object> settings,
                                        boolean healthCheckEnabled, long healthCheckIntervalMs) {
            this.configId = configId;
            this.algorithm = algorithm;
            this.settings = settings != null ? new HashMap<>(settings) : new HashMap<>();
            this.healthCheckEnabled = healthCheckEnabled;
            this.healthCheckIntervalMs = healthCheckIntervalMs;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getConfigId() { return configId; }
        public String getAlgorithm() { return algorithm; }
        public Map<String, Object> getSettings() { return new HashMap<>(settings); }
        public boolean isHealthCheckEnabled() { return healthCheckEnabled; }
        public long getHealthCheckIntervalMs() { return healthCheckIntervalMs; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Configuration change
     */
    public static class ConfigurationChange {
        private final String changeId;
        private final String changeType;
        private final String key;
        private final Object oldValue;
        private final Object newValue;
        private final String reason;
        private final long timestamp;
        
        public ConfigurationChange(String changeId, ChangeType changeType, String key, 
                                  Object oldValue, Object newValue, String reason) {
            this.changeId = changeId;
            this.changeType = changeType.name();
            this.key = key;
            this.oldValue = oldValue;
            this.newValue = newValue;
            this.reason = reason;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getChangeId() { return changeId; }
        public String getChangeType() { return changeType; }
        public String getKey() { return key; }
        public Object getOldValue() { return oldValue; }
        public Object getNewValue() { return newValue; }
        public String getReason() { return reason; }
        public long getTimestamp() { return timestamp; }
        
        public enum ChangeType {
            CREATE, UPDATE, DELETE, SCALE, CONFIGURE
        }
    }
    
    /**
     * Load balancer monitoring request
     */
    public static class LoadBalancerMonitoringRequest {
        private final String requestId;
        private final String loadBalancerId;
        private final Map<String, Object> monitoringParameters;
        private final long timeoutMs;
        
        public LoadBalancerMonitoringRequest(String requestId, String loadBalancerId, 
                                               Map<String, Object> monitoringParameters, long timeoutMs) {
            this.requestId = requestId;
            this.loadBalancerId = loadBalancerId;
            this.monitoringParameters = monitoringParameters != null ? new HashMap<>(monitoringParameters) : new HashMap<>();
            this.timeoutMs = timeoutMs;
        }
        
        public String getRequestId() { return requestId; }
        public String getLoadBalancerId() { return loadBalancerId; }
        public Map<String, Object> getMonitoringParameters() { return new HashMap<>(monitoringParameters); }
        public long getTimeoutMs() { return timeoutMs; }
    }
    
    /**
     * Load balancer monitoring result
     */
    public static class LoadBalancerMonitoringResult {
        private final String requestId;
        private final String loadBalancerId;
        private final LoadBalancerMetrics metrics;
        private final boolean success;
        private final String errorMessage;
        private final long monitoringTimeMs;
        
        public LoadBalancerMonitoringResult(String requestId, String loadBalancerId, LoadBalancerMetrics metrics,
                                                  boolean success, String errorMessage, long monitoringTimeMs) {
            this.requestId = requestId;
            this.loadBalancerId = loadBalancerId;
            this.metrics = metrics;
            this.success = success;
            this.errorMessage = errorMessage;
            this.monitoringTimeMs = monitoringTimeMs;
        }
        
        public String getRequestId() { return requestId; }
        public String getLoadBalancerId() { return loadBalancerId; }
        public LoadBalancerMetrics getMetrics() { return metrics; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public long getMonitoringTimeMs() { return monitoringTimeMs; }
    }
    
    /**
     * Configuration update request
     */
    public static class ConfigurationUpdateRequest {
        private final String requestId;
        private final LoadBalancerConfiguration newConfiguration;
        private final boolean validateBeforeUpdate;
        private final boolean rollbackOnFailure;
        
        public ConfigurationUpdateRequest(String requestId, LoadBalancerConfiguration newConfiguration, 
                                         boolean validateBeforeUpdate, boolean rollbackOnFailure) {
            this.requestId = requestId;
            this.newConfiguration = newConfiguration;
            this.validateBeforeUpdate = validateBeforeUpdate;
            this.rollbackOnFailure = rollbackOnFailure;
        }
        
        public String getRequestId() { return requestId; }
        public LoadBalancerConfiguration getNewConfiguration() { return newConfiguration; }
        public boolean isValidateBeforeUpdate() { return validateBeforeUpdate; }
        public boolean isRollbackOnFailure() { return rollbackOnFailure; }
    }
    
    /**
     * Configuration update result
     */
    public static class ConfigurationUpdateResult {
        private final String requestId;
        private final boolean success;
        private final LoadBalancerConfiguration previousConfiguration;
        private final LoadBalancerConfiguration appliedConfiguration;
        private final List<ConfigurationChange> appliedChanges;
        private final String errorMessage;
        private final long updateTimeMs;
        
        public ConfigurationUpdateResult(String requestId, boolean success, LoadBalancerConfiguration previousConfiguration,
                                         LoadBalancerConfiguration appliedConfiguration, List<ConfigurationChange> appliedChanges,
                                         String errorMessage, long updateTimeMs) {
            this.requestId = requestId;
            this.success = success;
            this.previousConfiguration = previousConfiguration;
            this.appliedConfiguration = appliedConfiguration;
            this.appliedChanges = appliedChanges != null ? new ArrayList<>(appliedChanges) : new ArrayList<>();
            this.errorMessage = errorMessage;
            this.updateTimeMs = updateTimeMs;
        }
        
        public String getRequestId() { return requestId; }
        public boolean isSuccess() { return success; }
        public LoadBalancerConfiguration getPreviousConfiguration() { return previousConfiguration; }
        public LoadBalancerConfiguration getAppliedConfiguration() { return appliedConfiguration; }
        public List<ConfigurationChange> getAppliedChanges() { return new ArrayList<>(appliedChanges); }
        public String getErrorMessage() { return errorMessage; }
        public long getUpdateTimeMs() { return updateTimeMs; }
    }
    
    /**
     * Routing request
     */
    public static class RoutingRequest {
        private final String requestId;
        private final String workloadId;
        private final Map<String, Object> routingContext;
        private final long timestamp;
        
        public RoutingRequest(String requestId, String workloadId, Map<String, Object> routingContext) {
            this.requestId = requestId;
            this.workloadId = workloadId;
            this.routingContext = routingContext != null ? new HashMap<>(routingContext) : new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRequestId() { return requestId; }
        public String getWorkloadId() { return workloadId; }
        public Map<String, Object> getRoutingContext() { return new HashMap<>(routingContext); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Workload distribution request
     */
    public static class WorkloadDistributionRequest {
        private final String distributionId;
        private final double totalWorkload;
        private final String workloadType;
        private final String priority;
        private final WorkloadRequirements workloadRequirements;
        private final Map<String, Object> distributionContext;
        
        public WorkloadDistributionRequest(String distributionId, double totalWorkload, String workloadType, 
                                               String priority, WorkloadRequirements workloadRequirements, 
                                               Map<String, Object> distributionContext) {
            this.distributionId = distributionId;
            this.totalWorkload = totalWorkload;
            this.workloadType = workloadType;
            this.priority = priority;
            this.workloadRequirements = workloadRequirements;
            this.distributionContext = distributionContext != null ? new HashMap<>(distributionContext) : new HashMap<>();
        }
        
        public String getDistributionId() { return distributionId; }
        public double getTotalWorkload() { return totalWorkload; }
        public String getWorkloadType() { return workloadType; }
        public String getPriority() { return priority; }
        public WorkloadRequirements getWorkloadRequirements() { return workloadRequirements; }
        public Map<String, Object> getDistributionContext() { return new HashMap<>(distributionContext); }
    }
    
    /**
     * Node performance metrics
     */
    public static class NodePerformanceMetrics {
        private final String nodeId;
        private final double responseTime;
        private final double throughput;
        private final double errorRate;
        private final int activeConnections;
        private final long timestamp;
        
        public NodePerformanceMetrics(String nodeId, double responseTime, double throughput, 
                                       double errorRate, int activeConnections) {
            this.nodeId = nodeId;
            this.responseTime = responseTime;
            this.throughput = throughput;
            this.errorRate = errorRate;
            this.activeConnections = activeConnections;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getNodeId() { return nodeId; }
        public double getResponseTime() { return responseTime; }
        public double getThroughput() { return throughput; }
        public double getErrorRate() { return errorRate; }
        public int getActiveConnections() { return activeConnections; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Algorithm performance metrics
     */
    public static class AlgorithmPerformance {
        private final String algorithmName;
        private final double averageResponseTime;
        private final double throughput;
        private final double errorRate;
        private final double successRate;
        private final long totalRequests;
        private final long timestamp;
        
        public AlgorithmPerformance(String algorithmName, double averageResponseTime, double throughput, 
                                  double errorRate, double successRate, long totalRequests) {
            this.algorithmName = algorithmName;
            this.averageResponseTime = averageResponseTime;
            this.throughput = throughput;
            this.errorRate = errorRate;
            this.successRate = successRate;
            this.totalRequests = totalRequests;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getAlgorithmName() { return algorithmName; }
        public double getAverageResponseTime() { return averageResponseTime; }
        public double getThroughput() { return throughput; }
        public double getErrorRate() { return errorRate; }
        public double getSuccessRate() { return successRate; }
        public long getTotalRequests() { return totalRequests; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Optimization recommendation
     */
    public static class OptimizationRecommendation {
        private final String recommendationId;
        private final String recommendationType;
        private final String description;
        private final double expectedImprovement;
        private final Map<String, Object> recommendationData;
        private final long timestamp;
        
        public OptimizationRecommendation(String recommendationId, String recommendationType, String description, 
                                         double expectedImprovement, Map<String, Object> recommendationData) {
            this.recommendationId = recommendationId;
            this.recommendationType = recommendationType;
            this.description = description;
            this.expectedImprovement = expectedImprovement;
            this.recommendationData = recommendationData != null ? new HashMap<>(recommendationData) : new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRecommendationId() { return recommendationId; }
        public String getRecommendationType() { return recommendationType; }
        public String getDescription() { return description; }
        public double getExpectedImprovement() { return expectedImprovement; }
        public Map<String, Object> getRecommendationData() { return new HashMap<>(recommendationData); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Node status
     */
    public static class NodeStatus {
        private final String nodeId;
        private final Status status;
        private final double cpuUsage;
        private final double memoryUsage;
        private final int activeConnections;
        private final long lastHeartbeat;
        private final Map<String, Object> metadata;
        
        public NodeStatus(String nodeId, Status status, double cpuUsage, double memoryUsage, 
                          int activeConnections, Map<String, Object> metadata) {
            this.nodeId = nodeId;
            this.status = status;
            this.cpuUsage = cpuUsage;
            this.memoryUsage = memoryUsage;
            this.activeConnections = activeConnections;
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            this.lastHeartbeat = System.currentTimeMillis();
        }
        
        public String getNodeId() { return nodeId; }
        public Status getStatus() { return status; }
        public double getCpuUsage() { return cpuUsage; }
        public double getMemoryUsage() { return memoryUsage; }
        public int getActiveConnections() { return activeConnections; }
        public long getLastHeartbeat() { return lastHeartbeat; }
        public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
        
        public boolean isHealthy() {
            return status == Status.ACTIVE;
        }
        
        public enum Status {
            ACTIVE, INACTIVE, MAINTENANCE, FAILED
        }
    }
    
    /**
     * Cluster metrics
     */
    public static class ClusterMetrics {
        private final String clusterId;
        private final int totalNodes;
        private final int activeNodes;
        private final double averageCpuUsage;
        private final double averageMemoryUsage;
        private final int totalConnections;
        private final long timestamp;
        
        // Additional fields for AutoScalingEngine compatibility
        private long totalScalingEvents;
        private long successfulScalingEvents;
        private double successRate;
        private int activeScalingStates;
        private double averageResponseTime;
        private double totalThroughput;
        
        public ClusterMetrics(String clusterId, int totalNodes, int activeNodes, 
                              double averageCpuUsage, double averageMemoryUsage, int totalConnections) {
            this.clusterId = clusterId;
            this.totalNodes = totalNodes;
            this.activeNodes = activeNodes;
            this.averageCpuUsage = averageCpuUsage;
            this.averageMemoryUsage = averageMemoryUsage;
            this.totalConnections = totalConnections;
            this.timestamp = System.currentTimeMillis();
            
            // Initialize additional fields
            this.totalScalingEvents = 0;
            this.successfulScalingEvents = 0;
            this.successRate = 0.0;
            this.activeScalingStates = 0;
            this.averageResponseTime = 0.0;
            this.totalThroughput = 0.0;
        }
        
        public String getClusterId() { return clusterId; }
        public int getTotalNodes() { return totalNodes; }
        public int getActiveNodes() { return activeNodes; }
        public double getAverageCpuUsage() { return averageCpuUsage; }
        public double getAverageMemoryUsage() { return averageMemoryUsage; }
        public int getTotalConnections() { return totalConnections; }
        public long getTimestamp() { return timestamp; }
        
        // Additional methods for compatibility
        public double getCpuUtilization() { return averageCpuUsage; }
        public double getMemoryUtilization() { return averageMemoryUsage; }
        public double getAverageResponseTime() { return averageResponseTime; }
        public double getTotalThroughput() { return totalThroughput; }
        
        // AutoScalingEngine compatibility methods
        public long getTotalScalingEvents() { return totalScalingEvents; }
        public long getSuccessfulScalingEvents() { return successfulScalingEvents; }
        public double getSuccessRate() { return successRate; }
        public int getActiveScalingStates() { return activeScalingStates; }
        
        public void setTotalScalingEvents(long totalScalingEvents) { this.totalScalingEvents = totalScalingEvents; }
        public void setSuccessfulScalingEvents(long successfulScalingEvents) { this.successfulScalingEvents = successfulScalingEvents; }
        public void setSuccessRate(double successRate) { this.successRate = successRate; }
        public void setActiveScalingStates(int activeScalingStates) { this.activeScalingStates = activeScalingStates; }
        public void setAverageResponseTime(double averageResponseTime) { this.averageResponseTime = averageResponseTime; }
        public void setTotalThroughput(double totalThroughput) { this.totalThroughput = totalThroughput; }
    }
    
    /**
     * Cluster issue
     */
    public static class ClusterIssue {
        private final String issueId;
        private final IssueType type;
        private final IssueSeverity severity;
        private final String description;
        private final String nodeId;
        private final long timestamp;
        
        public ClusterIssue(String issueId, IssueType type, IssueSeverity severity, 
                           String description, String nodeId) {
            this.issueId = issueId;
            this.type = type;
            this.severity = severity;
            this.description = description;
            this.nodeId = nodeId;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getIssueId() { return issueId; }
        public IssueType getType() { return type; }
        public IssueSeverity getSeverity() { return severity; }
        public String getDescription() { return description; }
        public String getNodeId() { return nodeId; }
        public long getTimestamp() { return timestamp; }
        
        public enum IssueType {
            UNHEALTHY_NODE,
            HIGH_CPU_USAGE,
            HIGH_MEMORY_USAGE,
            LOAD_IMBALANCE,
            NETWORK_LATENCY,
            DISK_SPACE_LOW
        }
        
        public enum IssueSeverity {
            LOW,
            MEDIUM,
            HIGH,
            CRITICAL
        }
    }
    
    /**
     * Node metrics
     */
    public static class NodeMetrics {
        private final String nodeId;
        private final double cpuUsage;
        private final double memoryUsage;
        private final int activeConnections;
        private final long requestCount;
        private final double averageResponseTime;
        private final long timestamp;
        
        public NodeMetrics(String nodeId, double cpuUsage, double memoryUsage, 
                          int activeConnections, long requestCount, double averageResponseTime) {
            this.nodeId = nodeId;
            this.cpuUsage = cpuUsage;
            this.memoryUsage = memoryUsage;
            this.activeConnections = activeConnections;
            this.requestCount = requestCount;
            this.averageResponseTime = averageResponseTime;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getNodeId() { return nodeId; }
        public double getCpuUsage() { return cpuUsage; }
        public double getMemoryUsage() { return memoryUsage; }
        public int getActiveConnections() { return activeConnections; }
        public long getRequestCount() { return requestCount; }
        public double getAverageResponseTime() { return averageResponseTime; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Cluster recommendation for scaling operations
     */
    public static class ClusterRecommendation {
        private final String nodeId;
        private final RecommendationType type;
        private final String description;
        private final double priority;
        private final long timestamp;
        
        public ClusterRecommendation(String nodeId, RecommendationType type, String description, double priority) {
            this.nodeId = nodeId;
            this.type = type;
            this.description = description;
            this.priority = priority;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getNodeId() { return nodeId; }
        public RecommendationType getType() { return type; }
        public String getDescription() { return description; }
        public double getPriority() { return priority; }
        public long getTimestamp() { return timestamp; }
        
        public enum RecommendationType {
            REPLACE_NODE,
            SCALE_OUT,
            SCALE_IN,
            REBALANCE,
            MAINTENANCE,
            OPTIMIZE
        }
    }
    
    /**
     * Maintenance operation for cluster management
     */
    public static class MaintenanceOperation {
        private final String operationId;
        private final MaintenanceType type;
        private final String nodeId;
        private final String description;
        private final long scheduledTime;
        private final long estimatedDuration;
        private final Map<String, Object> parameters;
        
        public MaintenanceOperation(String operationId, MaintenanceType type, String nodeId, 
                                 String description, long scheduledTime, long estimatedDuration,
                                 Map<String, Object> parameters) {
            this.operationId = operationId;
            this.type = type;
            this.nodeId = nodeId;
            this.description = description;
            this.scheduledTime = scheduledTime;
            this.estimatedDuration = estimatedDuration;
            this.parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();
        }
        
        public String getOperationId() { return operationId; }
        public MaintenanceType getType() { return type; }
        public String getNodeId() { return nodeId; }
        public String getDescription() { return description; }
        public long getScheduledTime() { return scheduledTime; }
        public long getEstimatedDuration() { return estimatedDuration; }
        public Map<String, Object> getParameters() { return new HashMap<>(parameters); }
        
        public enum MaintenanceType {
            HEALTH_CHECK,
            CLEANUP,
            OPTIMIZATION,
            UPDATE,
            BACKUP,
            RESTART
        }
    }
    
    /**
     * Rebalancing operation result
     */
    public static class RebalancingOperationResult {
        private final String operationId;
        private final boolean success;
        private final String errorMessage;
        private final long startTime;
        private final long endTime;
        private final int eventsMoved;
        
        public RebalancingOperationResult(String operationId, boolean success, String errorMessage, 
                                         long startTime, long endTime, int eventsMoved) {
            this.operationId = operationId;
            this.success = success;
            this.errorMessage = errorMessage;
            this.startTime = startTime;
            this.endTime = endTime;
            this.eventsMoved = eventsMoved;
        }
        
        public String getOperationId() { return operationId; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public int getEventsMoved() { return eventsMoved; }
    }
    
    /**
     * Rebalancing plan
     */
    public static class RebalancingPlan {
        private final String planId;
        private final List<RebalancingOperation> operations;
        private final double estimatedImpact;
        private final long estimatedDuration;
        private final Map<String, Object> metadata;
        
        public RebalancingPlan(String planId, List<RebalancingOperation> operations, 
                             double estimatedImpact, long estimatedDuration, Map<String, Object> metadata) {
            this.planId = planId;
            this.operations = operations != null ? new ArrayList<>(operations) : new ArrayList<>();
            this.estimatedImpact = estimatedImpact;
            this.estimatedDuration = estimatedDuration;
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        }
        
        public String getPlanId() { return planId; }
        public List<RebalancingOperation> getOperations() { return new ArrayList<>(operations); }
        public double getEstimatedImpact() { return estimatedImpact; }
        public long getEstimatedDuration() { return estimatedDuration; }
        public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
        
        public void addOperation(RebalancingOperation operation) {
            this.operations.add(operation);
        }
    }
    
    /**
     * Rebalancing operation
     */
    public static class RebalancingOperation {
        private final String operationId;
        private final String sourceNodeId;
        private final String targetNodeId;
        private final double loadAmount;
        private final OperationType type;
        
        public RebalancingOperation(String operationId, String sourceNodeId, 
                                 String targetNodeId, double loadAmount, OperationType type) {
            this.operationId = operationId;
            this.sourceNodeId = sourceNodeId;
            this.targetNodeId = targetNodeId;
            this.loadAmount = loadAmount;
            this.type = type;
        }
        
        public String getOperationId() { return operationId; }
        public String getSourceNodeId() { return sourceNodeId; }
        public String getTargetNodeId() { return targetNodeId; }
        public double getLoadAmount() { return loadAmount; }
        public OperationType getType() { return type; }
        
        public enum OperationType {
            MOVE_LOAD, ADD_CAPACITY, REMOVE_CAPACITY
        }
    }
    
    /**
     * Node state
     */
    public static class NodeState {
        private final String nodeId;
        private final String status;
        private final Map<String, Object> metadata;
        private final long timestamp;
        
        public NodeState(String nodeId, String status, Map<String, Object> metadata, long timestamp) {
            this.nodeId = nodeId;
            this.status = status;
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            this.timestamp = timestamp;
        }
        
        public String getNodeId() { return nodeId; }
        public String getStatus() { return status; }
        public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Cluster state
     */
    public enum ClusterState {
        INITIALIZING, ACTIVE, SCALING, MAINTENANCE, RECOVERING, SHUTTING_DOWN, SHUTDOWN, FAILED
    }
    
    /**
     * Cluster rebalancing request
     */
    public static class ClusterRebalancingRequest {
        private final String requestId;
        private final String clusterId;
        private final RebalancingStrategy strategy;
        private final Map<String, Object> parameters;
        
        public ClusterRebalancingRequest(String requestId, String clusterId, RebalancingStrategy strategy, Map<String, Object> parameters) {
            this.requestId = requestId;
            this.clusterId = clusterId;
            this.strategy = strategy;
            this.parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();
        }
        
        public String getRequestId() { return requestId; }
        public String getClusterId() { return clusterId; }
        public RebalancingStrategy getStrategy() { return strategy; }
        public Map<String, Object> getParameters() { return new HashMap<>(parameters); }
        
        public enum RebalancingStrategy {
            LOAD_BALANCE, HEALTH_OPTIMIZE, COST_OPTIMIZE, PERFORMANCE_OPTIMIZE
        }
    }
    
    /**
     * Cluster maintenance request
     */
    public static class ClusterMaintenanceRequest {
        private final String requestId;
        private final String clusterId;
        private final MaintenanceType type;
        private final List<String> targetNodes;
        private final long scheduledTime;
        private final Map<String, Object> parameters;
        
        public ClusterMaintenanceRequest(String requestId, String clusterId, MaintenanceType type, 
                                         List<String> targetNodes, long scheduledTime, Map<String, Object> parameters) {
            this.requestId = requestId;
            this.clusterId = clusterId;
            this.type = type;
            this.targetNodes = targetNodes != null ? new ArrayList<>(targetNodes) : new ArrayList<>();
            this.scheduledTime = scheduledTime;
            this.parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();
        }
        
        public String getRequestId() { return requestId; }
        public String getClusterId() { return clusterId; }
        public MaintenanceType getType() { return type; }
        public List<String> getTargetNodes() { return new ArrayList<>(targetNodes); }
        public long getScheduledTime() { return scheduledTime; }
        public Map<String, Object> getParameters() { return new HashMap<>(parameters); }
        
        public MaintenanceType getMaintenanceType() { return type; }
        
        public enum MaintenanceType {
            HEALTH_CHECK, CLEANUP, OPTIMIZATION, UPDATE, BACKUP, RESTART
        }
    }
    
    /**
     * Node registration request
     */
    public static class NodeRegistrationRequest {
        private final String requestId;
        private final String nodeId;
        private final String host;
        private final int port;
        private final Map<String, Object> capabilities;
        private final Map<String, Object> metadata;
        
        public NodeRegistrationRequest(String requestId, String nodeId, String host, int port, 
                                       Map<String, Object> capabilities, Map<String, Object> metadata) {
            this.requestId = requestId;
            this.nodeId = nodeId;
            this.host = host;
            this.port = port;
            this.capabilities = capabilities != null ? new HashMap<>(capabilities) : new HashMap<>();
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        }
        
        public String getRequestId() { return requestId; }
        public String getNodeId() { return nodeId; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public Map<String, Object> getCapabilities() { return new HashMap<>(capabilities); }
        public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
        
        public String getAddress() {
            return host + ":" + port;
        }
    }
    
    /**
     * Node registration result
     */
    public static class NodeRegistrationResult {
        private final String requestId;
        private final boolean success;
        private final String nodeId;
        private final String errorMessage;
        private final long timestamp;
        
        public NodeRegistrationResult(String requestId, boolean success, String nodeId, String errorMessage) {
            this.requestId = requestId;
            this.success = success;
            this.nodeId = nodeId;
            this.errorMessage = errorMessage;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRequestId() { return requestId; }
        public boolean isSuccess() { return success; }
        public String getNodeId() { return nodeId; }
        public String getErrorMessage() { return errorMessage; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Node unregistration result
     */
    public static class NodeUnregistrationResult {
        private final String requestId;
        private final boolean success;
        private final String nodeId;
        private final String errorMessage;
        private final long timestamp;
        
        public NodeUnregistrationResult(String requestId, boolean success, String nodeId, String errorMessage) {
            this.requestId = requestId;
            this.success = success;
            this.nodeId = nodeId;
            this.errorMessage = errorMessage;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRequestId() { return requestId; }
        public boolean isSuccess() { return success; }
        public String getNodeId() { return nodeId; }
        public String getErrorMessage() { return errorMessage; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Cluster initialization result
     */
    public static class ClusterInitializationResult {
        private final String requestId;
        private final boolean success;
        private final String clusterId;
        private final List<String> registeredNodes;
        private final String errorMessage;
        private final long timestamp;
        
        public ClusterInitializationResult(String requestId, boolean success, String clusterId, 
                                          List<String> registeredNodes, String errorMessage) {
            this.requestId = requestId;
            this.success = success;
            this.clusterId = clusterId;
            this.registeredNodes = registeredNodes != null ? new ArrayList<>(registeredNodes) : new ArrayList<>();
            this.errorMessage = errorMessage;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRequestId() { return requestId; }
        public boolean isSuccess() { return success; }
        public String getClusterId() { return clusterId; }
        public List<String> getRegisteredNodes() { return new ArrayList<>(registeredNodes); }
        public String getErrorMessage() { return errorMessage; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Cluster status result
     */
    public static class ClusterStatusResult {
        private final String requestId;
        private final boolean success;
        private final String clusterId;
        private final ClusterState state;
        private final int totalNodes;
        private final int activeNodes;
        private final String errorMessage;
        private final long timestamp;
        
        public ClusterStatusResult(String requestId, boolean success, String clusterId, ClusterState state,
                                   int totalNodes, int activeNodes, String errorMessage) {
            this.requestId = requestId;
            this.success = success;
            this.clusterId = clusterId;
            this.state = state;
            this.totalNodes = totalNodes;
            this.activeNodes = activeNodes;
            this.errorMessage = errorMessage;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRequestId() { return requestId; }
        public boolean isSuccess() { return success; }
        public String getClusterId() { return clusterId; }
        public ClusterState getState() { return state; }
        public int getTotalNodes() { return totalNodes; }
        public int getActiveNodes() { return activeNodes; }
        public String getErrorMessage() { return errorMessage; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Cluster maintenance result
     */
    public static class ClusterMaintenanceResult {
        private final String requestId;
        private final boolean success;
        private final String clusterId;
        private final List<String> completedNodes;
        private final List<String> failedNodes;
        private final String errorMessage;
        private final long timestamp;
        
        public ClusterMaintenanceResult(String requestId, boolean success, String clusterId,
                                          List<String> completedNodes, List<String> failedNodes, String errorMessage) {
            this.requestId = requestId;
            this.success = success;
            this.clusterId = clusterId;
            this.completedNodes = completedNodes != null ? new ArrayList<>(completedNodes) : new ArrayList<>();
            this.failedNodes = failedNodes != null ? new ArrayList<>(failedNodes) : new ArrayList<>();
            this.errorMessage = errorMessage;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRequestId() { return requestId; }
        public boolean isSuccess() { return success; }
        public String getClusterId() { return clusterId; }
        public List<String> getCompletedNodes() { return new ArrayList<>(completedNodes); }
        public List<String> getFailedNodes() { return new ArrayList<>(failedNodes); }
        public String getErrorMessage() { return errorMessage; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Service discovery for cluster nodes
     */
    public static class ServiceDiscovery {
        private final Map<String, DistributedModels.ClusterNode> discoveredNodes = new ConcurrentHashMap<>();
        
        public void registerNode(DistributedModels.ClusterNode node) {
            discoveredNodes.put(node.getNodeId(), node);
        }
        
        public void unregisterNode(String nodeId) {
            discoveredNodes.remove(nodeId);
        }
        
        public List<DistributedModels.ClusterNode> getDiscoveredNodes() {
            return new ArrayList<>(discoveredNodes.values());
        }
        
        public DistributedModels.ClusterNode getNode(String nodeId) {
            return discoveredNodes.get(nodeId);
        }
        
        public void stop() {
            // Mock implementation
            discoveredNodes.clear();
        }
        
        public void start() {
            // Mock implementation
        }
        
        public Promise<List<DistributedModels.ClusterNode>> discoverNodes() {
            // Mock implementation
            return Promise.of(getDiscoveredNodes());
        }
    }
    
    /**
     * Health monitor for cluster nodes
     */
    public static class HealthMonitor {
        private final Map<String, NodeHealth> nodeHealth = new ConcurrentHashMap<>();
        
        public void updateNodeHealth(String nodeId, NodeHealth health) {
            nodeHealth.put(nodeId, health);
        }
        
        public NodeHealth getNodeHealth(String nodeId) {
            return nodeHealth.get(nodeId);
        }
        
        public Map<String, NodeHealth> getAllNodeHealth() {
            return new HashMap<>(nodeHealth);
        }
        
        public List<String> getUnhealthyNodes() {
            return nodeHealth.entrySet().stream()
                    .filter(entry -> !entry.getValue().isHealthy())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }
        
        public Promise<Boolean> performHealthCheck(String nodeId) {
            // Mock implementation
            NodeHealth health = nodeHealth.get(nodeId);
            if (health != null) {
                return Promise.of(health.isHealthy());
            }
            return Promise.of(false);
        }
        
        public void stop() {
            // Mock implementation
            nodeHealth.clear();
        }
        
        public void start() {
            // Mock implementation
        }
        
        public void startMonitoring(DistributedModels.ClusterNode node) {
            // Mock implementation
            updateNodeHealth(node.getNodeId(), new NodeHealth(node.getNodeId(), true, "MONITORING"));
        }
    }
    
    /**
     * Configuration manager for cluster settings
     */
    public static class ConfigurationManager {
        private final Map<String, DistributedModels.ClusterConfiguration> configurations = new ConcurrentHashMap<>();
        
        public void updateConfiguration(String clusterId, DistributedModels.ClusterConfiguration config) {
            configurations.put(clusterId, config);
        }
        
        public DistributedModels.ClusterConfiguration getConfiguration(String clusterId) {
            return configurations.get(clusterId);
        }
        
        public Map<String, DistributedModels.ClusterConfiguration> getAllConfigurations() {
            return new HashMap<>(configurations);
        }
        
        public Promise<DistributedModels.ClusterConfiguration> getCurrentConfiguration() {
            // Mock implementation - return first available config
            return Promise.of(configurations.values().stream().findFirst().orElse(null));
        }
        
        public boolean backupConfiguration() {
            // Mock implementation
            return true;
        }
        
        public DistributedModels.ClusterConfiguration loadConfiguration() {
            // Mock implementation - return first available config
            return configurations.values().stream().findFirst().orElse(null);
        }
    }
    
    /**
     * Load distribution manager
     */
    public static class LoadDistributionManager {
        private final Map<String, NodeLoad> nodeLoads = new ConcurrentHashMap<>();
        
        public Promise<Boolean> offloadNode(String nodeId, int amount) {
            // Mock implementation
            NodeLoad load = nodeLoads.get(nodeId);
            if (load != null) {
                nodeLoads.put(nodeId, new NodeLoad(nodeId, Math.max(0, load.getCurrentLoad() - amount)));
                return Promise.of(true);
            }
            return Promise.of(false);
        }
        
        public Promise<Boolean> loadNode(String nodeId, int amount) {
            // Mock implementation
            NodeLoad load = nodeLoads.get(nodeId);
            if (load != null) {
                nodeLoads.put(nodeId, new NodeLoad(nodeId, load.getCurrentLoad() + amount));
                return Promise.of(true);
            }
            return Promise.of(false);
        }
        
        public void updateNodeLoad(String nodeId, NodeLoad load) {
            nodeLoads.put(nodeId, load);
        }
        
        public Map<String, NodeLoad> getAllNodeLoads() {
            return new HashMap<>(nodeLoads);
        }
        
        public Map<String, Double> getCurrentLoadDistribution() {
            Map<String, Double> distribution = new HashMap<>();
            for (Map.Entry<String, NodeLoad> entry : nodeLoads.entrySet()) {
                distribution.put(entry.getKey(), (double) entry.getValue().getCurrentLoad());
            }
            return distribution;
        }
        
        public void updateLoadDistribution(Map<String, Double> distribution) {
            for (Map.Entry<String, Double> entry : distribution.entrySet()) {
                nodeLoads.put(entry.getKey(), new NodeLoad(entry.getKey(), entry.getValue().intValue()));
            }
        }
        
        public int getNodeLoad(String nodeId) {
            NodeLoad load = nodeLoads.get(nodeId);
            return load != null ? load.getCurrentLoad() : 0;
        }
        
        public Promise<Void> drainNode(DistributedModels.ClusterNode node) {
            // Mock implementation
            NodeLoad load = nodeLoads.get(node.getNodeId());
            if (load != null) {
                nodeLoads.put(node.getNodeId(), new NodeLoad(node.getNodeId(), 0));
                return Promise.of(null);
            }
            return Promise.ofException(new RuntimeException("Node not found"));
        }
        
        public Promise<Map<String, Double>> optimizeLoadDistribution() {
            // Mock implementation
            return Promise.of(getCurrentLoadDistribution());
        }
        
        public void stop() {
            // Mock implementation
            nodeLoads.clear();
        }
        
        public void start() {
            // Mock implementation
        }
        
        public void addNode(DistributedModels.ClusterNode node) {
            // Mock implementation
            nodeLoads.put(node.getNodeId(), new NodeLoad(node.getNodeId(), 0));
        }
        
        public void removeNode(DistributedModels.ClusterNode node) {
            // Mock implementation
            nodeLoads.remove(node.getNodeId());
        }
    }
    
    /**
     * Node registry for cluster management
     */
    public static class NodeRegistry {
        private final Map<String, DistributedModels.ClusterNode> registeredNodes = new ConcurrentHashMap<>();
        
        public void registerNode(DistributedModels.ClusterNode node) {
            registeredNodes.put(node.getNodeId(), node);
        }
        
        public void unregisterNode(String nodeId) {
            registeredNodes.remove(nodeId);
        }
        
        public DistributedModels.ClusterNode getNode(String nodeId) {
            return registeredNodes.get(nodeId);
        }
        
        public List<DistributedModels.ClusterNode> getAllNodes() {
            return new ArrayList<>(registeredNodes.values());
        }
        
        public List<DistributedModels.ClusterNode> getActiveNodes() {
            return registeredNodes.values().stream()
                    .filter(node -> node.getStatus() == DistributedModels.NodeStatus.ACTIVE)
                    .collect(Collectors.toList());
        }
        
        public void shutdown() {
            // Mock implementation
            registeredNodes.clear();
        }
        
        public void initialize(DistributedModels.ClusterConfiguration config) {
            // Mock implementation
        }
        
        public void register(DistributedModels.ClusterNode node) {
            registerNode(node);
        }
        
        public void unregister(String nodeId) {
            unregisterNode(nodeId);
        }
    }
    
    /**
     * Node health status
     */
    public static class NodeHealth {
        private final String nodeId;
        private final boolean healthy;
        private final String status;
        private final long timestamp;
        
        public NodeHealth(String nodeId, boolean healthy, String status) {
            this.nodeId = nodeId;
            this.healthy = healthy;
            this.status = status;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getNodeId() { return nodeId; }
        public boolean isHealthy() { return healthy; }
        public String getStatus() { return status; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Node load information
     */
    public static class NodeLoad {
        private final String nodeId;
        private final int currentLoad;
        private final long timestamp;
        
        public NodeLoad(String nodeId, int currentLoad) {
            this.nodeId = nodeId;
            this.currentLoad = currentLoad;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getNodeId() { return nodeId; }
        public int getCurrentLoad() { return currentLoad; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Cluster status request
     */
    public static class ClusterStatusRequest {
        private final String requestId;
        private final String clusterId;
        private final boolean includeNodes;
        private final boolean includeMetrics;
        
        public ClusterStatusRequest(String requestId, String clusterId, boolean includeNodes, boolean includeMetrics) {
            this.requestId = requestId;
            this.clusterId = clusterId;
            this.includeNodes = includeNodes;
            this.includeMetrics = includeMetrics;
        }
        
        public String getRequestId() { return requestId; }
        public String getClusterId() { return clusterId; }
        public boolean isIncludeNodes() { return includeNodes; }
        public boolean isIncludeMetrics() { return includeMetrics; }
    }
    
    /**
     * Node unregistration request
     */
    public static class NodeUnregistrationRequest {
        private final String requestId;
        private final String nodeId;
        private final String reason;
        private final boolean graceful;
        
        public NodeUnregistrationRequest(String requestId, String nodeId, String reason, boolean graceful) {
            this.requestId = requestId;
            this.nodeId = nodeId;
            this.reason = reason;
            this.graceful = graceful;
        }
        
        public String getRequestId() { return requestId; }
        public String getNodeId() { return nodeId; }
        public String getReason() { return reason; }
        public boolean isGraceful() { return graceful; }
    }
    
    /**
     * Load distribution
     */
    public static class LoadDistribution {
        private final Map<String, Double> nodeLoads;
        private final double totalLoad;
        private final long timestamp;
        
        public LoadDistribution(Map<String, Double> nodeLoads, double totalLoad) {
            this.nodeLoads = nodeLoads != null ? new HashMap<>(nodeLoads) : new HashMap<>();
            this.totalLoad = totalLoad;
            this.timestamp = System.currentTimeMillis();
        }
        
        public Map<String, Double> getNodeLoads() { return new HashMap<>(nodeLoads); }
        public double getTotalLoad() { return totalLoad; }
        public long getTimestamp() { return timestamp; }
    }
    
    // ===== MISSING CLASSES FOR AUTO SCALING ENGINE =====
    
    /**
     * Predictive Scaler interface
     */
    public interface PredictiveScaler {
        PredictiveScalingRecommendation getRecommendation(String clusterId, ClusterMetrics metrics);
        WorkloadForecast generateWorkloadForecast(List<ClusterMetrics> historicalMetrics, long forecastHorizon);
    }
    
    /**
     * Cost Optimizer interface
     */
    public interface CostOptimizer {
        CostOptimizationResult optimizeScalingDecision(ScalingAction action, PredictiveScalingRecommendation recommendation, ClusterMetrics metrics);
        CostOptimizedSchedule optimizeScalingSchedule(ScalingSchedule schedule);
    }
    
    /**
     * Metrics Collector interface
     */
    public interface MetricsCollector {
        ClusterMetrics collectClusterMetrics(String clusterId);
        List<ClusterMetrics> collectHistoricalMetrics(String clusterId, long period);
        List<String> getActiveClusterIds();
    }
    
    /**
     * Scaling Policy Manager interface
     */
    public interface ScalingPolicyManager {
        List<ScalingPolicy> getApplicablePolicies(String clusterId);
        List<ScalingPolicy> getPolicies(String clusterId);
        void updatePolicies(String clusterId, List<ScalingPolicy> policies);
    }
    
    /**
     * Scaling Executor interface
     */
    public interface ScalingExecutor {
        ScalingExecutionResult execute(ScalingAction action);
    }
    
    /**
     * Scaling Evaluation Request
     */
    public static class ScalingEvaluationRequest {
        private final String clusterId;
        
        public ScalingEvaluationRequest(String clusterId) {
            this.clusterId = clusterId;
        }
        
        public String getClusterId() { return clusterId; }
    }
    
    /**
     * Scaling Evaluation Result
     */
    public static class ScalingEvaluationResult {
        private final ScalingDecisionRecord decision;
        private final long evaluationTime;
        private final boolean success;
        private final String errorMessage;
        
        public ScalingEvaluationResult(ScalingDecisionRecord decision, long evaluationTime, boolean success, String errorMessage) {
            this.decision = decision;
            this.evaluationTime = evaluationTime;
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public ScalingDecisionRecord getDecision() { return decision; }
        public long getEvaluationTime() { return evaluationTime; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * Scaling Execution Request
     */
    public static class ScalingExecutionRequest {
        private final ScalingDecisionRecord scalingDecision;
        
        public ScalingExecutionRequest(ScalingDecisionRecord scalingDecision) {
            this.scalingDecision = scalingDecision;
        }
        
        public ScalingDecisionRecord getScalingDecision() { return scalingDecision; }
        public String getClusterId() { return scalingDecision.getClusterId(); }
    }
    
    /**
     * Scaling Execution Result
     */
    public static class ScalingExecutionResult {
        private final ScalingDecisionRecord decision;
        private final ScalingResult scalingResult;
        private final ScalingReport report;
        private final long executionTime;
        private final boolean success;
        private final String errorMessage;
        private boolean complete = false;
        
        public ScalingExecutionResult(ScalingDecisionRecord decision, ScalingResult scalingResult, ScalingReport report,
                                    long executionTime, boolean success, String errorMessage) {
            this.decision = decision;
            this.scalingResult = scalingResult;
            this.report = report;
            this.executionTime = executionTime;
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public ScalingDecisionRecord getDecision() { return decision; }
        public ScalingResult getScalingResult() { return scalingResult; }
        public ScalingReport getReport() { return report; }
        public long getExecutionTime() { return executionTime; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public boolean isComplete() { return complete; }
        
        public void updateStatus() {
            // Simulate status update
            complete = true;
        }
    }
    
    /**
     * Predictive Scaling Request
     */
    public static class PredictiveScalingRequest {
        private final String clusterId;
        private final long historicalPeriod;
        private final long forecastHorizon;
        private final SchedulingConstraints schedulingConstraints;
        
        public PredictiveScalingRequest(String clusterId, long historicalPeriod, long forecastHorizon, SchedulingConstraints schedulingConstraints) {
            this.clusterId = clusterId;
            this.historicalPeriod = historicalPeriod;
            this.forecastHorizon = forecastHorizon;
            this.schedulingConstraints = schedulingConstraints;
        }
        
        public String getClusterId() { return clusterId; }
        public long getHistoricalPeriod() { return historicalPeriod; }
        public long getForecastHorizon() { return forecastHorizon; }
        public SchedulingConstraints getSchedulingConstraints() { return schedulingConstraints; }
    }
    
    /**
     * Predictive Scaling Result
     */
    public static class PredictiveScalingResult {
        private final PredictiveScalingPlan plan;
        private final long processingTime;
        private final boolean success;
        private final String errorMessage;
        
        public PredictiveScalingResult(PredictiveScalingPlan plan, long processingTime, boolean success, String errorMessage) {
            this.plan = plan;
            this.processingTime = processingTime;
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public PredictiveScalingPlan getPlan() { return plan; }
        public long getProcessingTime() { return processingTime; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * Cost Optimization Request
     */
    public static class CostOptimizationRequest {
        private final String clusterId;
        private final OptimizationConstraints constraints;
        
        public CostOptimizationRequest(String clusterId, OptimizationConstraints constraints) {
            this.clusterId = clusterId;
            this.constraints = constraints;
        }
        
        public String getClusterId() { return clusterId; }
        public OptimizationConstraints getConstraints() { return constraints; }
    }
    
    /**
     * Cost Optimization Result
     */
    public static class CostOptimizationResult {
        private final OptimizationStrategy optimalStrategy;
        private final OptimizationPlan plan;
        private final List<StrategyEvaluation> strategyEvaluations;
        private final long optimizationTime;
        private final boolean success;
        private final String errorMessage;
        
        public CostOptimizationResult(OptimizationStrategy optimalStrategy, OptimizationPlan plan,
                                   List<StrategyEvaluation> strategyEvaluations, long optimizationTime,
                                   boolean success, String errorMessage) {
            this.optimalStrategy = optimalStrategy;
            this.plan = plan;
            this.strategyEvaluations = strategyEvaluations != null ? new ArrayList<>(strategyEvaluations) : new ArrayList<>();
            this.optimizationTime = optimizationTime;
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public OptimizationStrategy getOptimalStrategy() { return optimalStrategy; }
        public OptimizationPlan getPlan() { return plan; }
        public List<StrategyEvaluation> getStrategyEvaluations() { return new ArrayList<>(strategyEvaluations); }
        public long getOptimizationTime() { return optimizationTime; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        
        public ScalingAction getOptimizedAction() {
            return optimalStrategy != null ? new ScalingAction(ScalingAction.Type.SCALE_OUT, 1, "Cost optimized") : null;
        }
    }
    
    /**
     * Auto Scaling Monitoring Request
     */
    public static class AutoScalingMonitoringRequest {
        private final String clusterId;
        private final long monitoringPeriod;
        
        public AutoScalingMonitoringRequest(String clusterId, long monitoringPeriod) {
            this.clusterId = clusterId;
            this.monitoringPeriod = monitoringPeriod;
        }
        
        public String getClusterId() { return clusterId; }
        public long getMonitoringPeriod() { return monitoringPeriod; }
    }
    
    /**
     * Auto Scaling Monitoring Result
     */
    public static class AutoScalingMonitoringResult {
        private final ScalingMetrics scalingMetrics;
        private final List<ScalingPattern> patterns;
        private final List<ScalingEfficiencyIssue> efficiencyIssues;
        private final List<ScalingInsight> insights;
        private final CostEfficiencyMetrics costEfficiency;
        private final List<AutoScalingRecommendation> recommendations;
        private final long monitoringTime;
        private final boolean success;
        private final String errorMessage;
        
        public AutoScalingMonitoringResult(ScalingMetrics scalingMetrics, List<ScalingPattern> patterns,
                                        List<ScalingEfficiencyIssue> efficiencyIssues, List<ScalingInsight> insights,
                                        CostEfficiencyMetrics costEfficiency, List<AutoScalingRecommendation> recommendations,
                                        long monitoringTime, boolean success, String errorMessage) {
            this.scalingMetrics = scalingMetrics;
            this.patterns = patterns != null ? new ArrayList<>(patterns) : new ArrayList<>();
            this.efficiencyIssues = efficiencyIssues != null ? new ArrayList<>(efficiencyIssues) : new ArrayList<>();
            this.insights = insights != null ? new ArrayList<>(insights) : new ArrayList<>();
            this.costEfficiency = costEfficiency;
            this.recommendations = recommendations != null ? new ArrayList<>(recommendations) : new ArrayList<>();
            this.monitoringTime = monitoringTime;
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public ScalingMetrics getScalingMetrics() { return scalingMetrics; }
        public List<ScalingPattern> getPatterns() { return new ArrayList<>(patterns); }
        public List<ScalingEfficiencyIssue> getEfficiencyIssues() { return new ArrayList<>(efficiencyIssues); }
        public List<ScalingInsight> getInsights() { return new ArrayList<>(insights); }
        public CostEfficiencyMetrics getCostEfficiency() { return costEfficiency; }
        public List<AutoScalingRecommendation> getRecommendations() { return new ArrayList<>(recommendations); }
        public long getMonitoringTime() { return monitoringTime; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * Policy Update Request
     */
    public static class PolicyUpdateRequest {
        private final String clusterId;
        private final List<ScalingPolicy> policies;
        
        public PolicyUpdateRequest(String clusterId, List<ScalingPolicy> policies) {
            this.clusterId = clusterId;
            this.policies = policies != null ? new ArrayList<>(policies) : new ArrayList<>();
        }
        
        public String getClusterId() { return clusterId; }
        public List<ScalingPolicy> getPolicies() { return new ArrayList<>(policies); }
    }
    
    /**
     * Policy Update Result
     */
    public static class PolicyUpdateResult {
        private final List<PolicyChange> changes;
        private final List<ScalingDecisionRecord> affectedDecisions;
        private final long updateTime;
        private final boolean success;
        private final String errorMessage;
        
        public PolicyUpdateResult(List<PolicyChange> changes, List<ScalingDecisionRecord> affectedDecisions,
                                long updateTime, boolean success, String errorMessage) {
            this.changes = changes != null ? new ArrayList<>(changes) : new ArrayList<>();
            this.affectedDecisions = affectedDecisions != null ? new ArrayList<>(affectedDecisions) : new ArrayList<>();
            this.updateTime = updateTime;
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public List<PolicyChange> getChanges() { return new ArrayList<>(changes); }
        public List<ScalingDecisionRecord> getAffectedDecisions() { return new ArrayList<>(affectedDecisions); }
        public long getUpdateTime() { return updateTime; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * Scaling Policy
     */
    public static class ScalingPolicy {
        private final String id;
        private final String name;
        private final List<PolicyCondition> conditions;
        private final ScalingAction.Type recommendedAction;
        
        public ScalingPolicy(String id, String name, List<PolicyCondition> conditions, ScalingAction.Type recommendedAction) {
            this.id = id;
            this.name = name;
            this.conditions = conditions != null ? new ArrayList<>(conditions) : new ArrayList<>();
            this.recommendedAction = recommendedAction;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public List<PolicyCondition> getConditions() { return new ArrayList<>(conditions); }
        public ScalingAction.Type getRecommendedAction() { return recommendedAction; }
    }
    
    /**
     * Policy Condition
     */
    public static class PolicyCondition {
        private final Metric metric;
        private final Operator operator;
        private final double threshold;
        
        public PolicyCondition(Metric metric, Operator operator, double threshold) {
            this.metric = metric;
            this.operator = operator;
            this.threshold = threshold;
        }
        
        public Metric getMetric() { return metric; }
        public Operator getOperator() { return operator; }
        public double getThreshold() { return threshold; }
        
        public enum Metric {
            CPU_USAGE, MEMORY_USAGE, RESPONSE_TIME, THROUGHPUT
        }
        
        public enum Operator {
            GREATER_THAN, LESS_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN_OR_EQUAL, EQUAL
        }
    }
    
    /**
     * Policy Evaluation Result
     */
    public static class PolicyEvaluationResult {
        private final String policyId;
        private final String policyName;
        private final boolean conditionsMet;
        private final ScalingAction.Type recommendedAction;
        private final List<ConditionEvaluation> conditionEvaluations;
        private final ClusterMetrics metrics;
        
        public PolicyEvaluationResult(String policyId, String policyName, boolean conditionsMet,
                                    ScalingAction.Type recommendedAction, List<ConditionEvaluation> conditionEvaluations,
                                    ClusterMetrics metrics) {
            this.policyId = policyId;
            this.policyName = policyName;
            this.conditionsMet = conditionsMet;
            this.recommendedAction = recommendedAction;
            this.conditionEvaluations = conditionEvaluations != null ? new ArrayList<>(conditionEvaluations) : new ArrayList<>();
            this.metrics = metrics;
        }
        
        public String getPolicyId() { return policyId; }
        public String getPolicyName() { return policyName; }
        public boolean isConditionsMet() { return conditionsMet; }
        public ScalingAction.Type getRecommendedAction() { return recommendedAction; }
        public List<ConditionEvaluation> getConditionEvaluations() { return new ArrayList<>(conditionEvaluations); }
        public ClusterMetrics getMetrics() { return metrics; }
    }
    
    /**
     * Condition Evaluation
     */
    public static class ConditionEvaluation {
        private final PolicyCondition.Metric metric;
        private final double actualValue;
        private final double threshold;
        private final PolicyCondition.Operator operator;
        private final boolean met;
        
        public ConditionEvaluation(PolicyCondition.Metric metric, double actualValue, double threshold,
                                 PolicyCondition.Operator operator, boolean met) {
            this.metric = metric;
            this.actualValue = actualValue;
            this.threshold = threshold;
            this.operator = operator;
            this.met = met;
        }
        
        public PolicyCondition.Metric getMetric() { return metric; }
        public double getActualValue() { return actualValue; }
        public double getThreshold() { return threshold; }
        public PolicyCondition.Operator getOperator() { return operator; }
        public boolean isMet() { return met; }
    }
    
    /**
     * Scaling Action
     */
    public static class ScalingAction {
        private final Type type;
        private final int magnitude;
        private final String reason;
        
        public ScalingAction(Type type, int magnitude, String reason) {
            this.type = type;
            this.magnitude = magnitude;
            this.reason = reason;
        }
        
        public Type getType() { return type; }
        public int getMagnitude() { return magnitude; }
        public String getReason() { return reason; }
        
        public enum Type {
            SCALE_OUT, SCALE_IN, NO_ACTION
        }
    }
    
    /**
     * Scaling Decision
     */
    public static class ScalingDecisionRecord {
        private final String clusterId;
        private final ScalingAction action;
        private final PredictiveScalingRecommendation predictiveRecommendation;
        private final ScalingAction optimizedAction;
        private final List<PolicyEvaluationResult> policyEvaluations;
        private final ClusterMetrics metrics;
        private final Instant timestamp;
        
        public ScalingDecisionRecord(String clusterId, ScalingAction action, PredictiveScalingRecommendation predictiveRecommendation,
                            ScalingAction optimizedAction, List<PolicyEvaluationResult> policyEvaluations,
                            ClusterMetrics metrics, Instant timestamp) {
            this.clusterId = clusterId;
            this.action = action;
            this.predictiveRecommendation = predictiveRecommendation;
            this.optimizedAction = optimizedAction;
            this.policyEvaluations = policyEvaluations != null ? new ArrayList<>(policyEvaluations) : new ArrayList<>();
            this.metrics = metrics;
            this.timestamp = timestamp;
        }
        
        public String getClusterId() { return clusterId; }
        public ScalingAction getAction() { return action; }
        public PredictiveScalingRecommendation getPredictiveRecommendation() { return predictiveRecommendation; }
        public ScalingAction getOptimizedAction() { return optimizedAction; }
        public List<PolicyEvaluationResult> getPolicyEvaluations() { return new ArrayList<>(policyEvaluations); }
        public ClusterMetrics getMetrics() { return metrics; }
        public Instant getTimestamp() { return timestamp; }
    }
    
    /**
     * Scaling Result
     */
    public static class ScalingResult {
        private final ScalingAction action;
        private final int initialNodeCount;
        private final boolean success;
        private final String errorMessage;
        
        public ScalingResult(ScalingAction action, int initialNodeCount, boolean success, String errorMessage) {
            this.action = action;
            this.initialNodeCount = initialNodeCount;
            this.success = success;
            this.errorMessage = errorMessage;
        }
        
        public ScalingAction getAction() { return action; }
        public int getInitialNodeCount() { return initialNodeCount; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
    }
    
    /**
     * Scaling Report
     */
    public static class ScalingReport {
        private final String clusterId;
        private final ScalingAction action;
        private final ScalingResult result;
        private final boolean success;
        private final ClusterMetrics metrics;
        private final List<PolicyEvaluationResult> policyEvaluations;
        private final Instant timestamp;
        
        public ScalingReport(String clusterId, ScalingAction action, ScalingResult result, boolean success,
                          ClusterMetrics metrics, List<PolicyEvaluationResult> policyEvaluations, Instant timestamp) {
            this.clusterId = clusterId;
            this.action = action;
            this.result = result;
            this.success = success;
            this.metrics = metrics;
            this.policyEvaluations = policyEvaluations != null ? new ArrayList<>(policyEvaluations) : new ArrayList<>();
            this.timestamp = timestamp;
        }
        
        public String getClusterId() { return clusterId; }
        public ScalingAction getAction() { return action; }
        public ScalingResult getResult() { return result; }
        public boolean isSuccess() { return success; }
        public ClusterMetrics getMetrics() { return metrics; }
        public List<PolicyEvaluationResult> getPolicyEvaluations() { return new ArrayList<>(policyEvaluations); }
        public Instant getTimestamp() { return timestamp; }
    }
    
    /**
     * Workload Forecast
     */
    public static class WorkloadForecast {
        private final List<WorkloadPrediction> predictions;
        private final Instant generatedAt;
        
        public WorkloadForecast(List<WorkloadPrediction> predictions) {
            this.predictions = predictions != null ? new ArrayList<>(predictions) : new ArrayList<>();
            this.generatedAt = Instant.now();
        }
        
        public List<WorkloadPrediction> getPredictions() { return new ArrayList<>(predictions); }
        public Instant getGeneratedAt() { return generatedAt; }
    }
    
    /**
     * Workload Prediction
     */
    public static class WorkloadPrediction {
        private final Instant timestamp;
        private final double predictedLoad;
        
        public WorkloadPrediction(Instant timestamp, double predictedLoad) {
            this.timestamp = timestamp;
            this.predictedLoad = predictedLoad;
        }
        
        public Instant getTimestamp() { return timestamp; }
        public double getPredictedLoad() { return predictedLoad; }
    }
    
    /**
     * Scaling Requirement
     */
    public static class ScalingRequirement {
        private final Instant timestamp;
        private final ScalingAction.Type action;
        private final int magnitude;
        private final String reason;
        
        public ScalingRequirement(Instant timestamp, ScalingAction.Type action, int magnitude, String reason) {
            this.timestamp = timestamp;
            this.action = action;
            this.magnitude = magnitude;
            this.reason = reason;
        }
        
        public Instant getTimestamp() { return timestamp; }
        public ScalingAction.Type getAction() { return action; }
        public int getMagnitude() { return magnitude; }
        public String getReason() { return reason; }
    }
    
    /**
     * Scaling Schedule
     */
    public static class ScalingSchedule {
        private List<ScalingEvent> events;
        
        public ScalingSchedule() {
            this.events = new ArrayList<>();
        }
        
        public List<ScalingEvent> getEvents() { return new ArrayList<>(events); }
        public void setEvents(List<ScalingEvent> events) { this.events = events != null ? new ArrayList<>(events) : new ArrayList<>(); }
    }
    
    /**
     * Scaling Event
     */
    public static class ScalingEvent {
        private final Instant timestamp;
        private final ScalingAction.Type action;
        private final int magnitude;
        private final String reason;
        
        public ScalingEvent(Instant timestamp, ScalingAction.Type action, int magnitude, String reason) {
            this.timestamp = timestamp;
            this.action = action;
            this.magnitude = magnitude;
            this.reason = reason;
        }
        
        public Instant getTimestamp() { return timestamp; }
        public ScalingAction.Type getAction() { return action; }
        public int getMagnitude() { return magnitude; }
        public String getReason() { return reason; }
    }
    
    /**
     * Scheduling Constraints
     */
    public static class SchedulingConstraints {
        private final Instant earliestTime;
        private final Instant latestTime;
        private final int maxConcurrentOperations;
        
        public SchedulingConstraints(Instant earliestTime, Instant latestTime, int maxConcurrentOperations) {
            this.earliestTime = earliestTime;
            this.latestTime = latestTime;
            this.maxConcurrentOperations = maxConcurrentOperations;
        }
        
        public Instant getEarliestTime() { return earliestTime; }
        public Instant getLatestTime() { return latestTime; }
        public int getMaxConcurrentOperations() { return maxConcurrentOperations; }
    }
    
    /**
     * Predictive Scaling Recommendation
     */
    public static class PredictiveScalingRecommendation {
        private final ScalingAction.Type recommendedAction;
        private final int magnitude;
        private final double confidence;
        private final String reasoning;
        
        public PredictiveScalingRecommendation(ScalingAction.Type recommendedAction, int magnitude, double confidence, String reasoning) {
            this.recommendedAction = recommendedAction;
            this.magnitude = magnitude;
            this.confidence = confidence;
            this.reasoning = reasoning;
        }
        
        public ScalingAction.Type getRecommendedAction() { return recommendedAction; }
        public int getMagnitude() { return magnitude; }
        public double getConfidence() { return confidence; }
        public String getReasoning() { return reasoning; }
    }
    
    /**
     * Predictive Scaling Plan
     */
    public static class PredictiveScalingPlan {
        private final String clusterId;
        private final WorkloadForecast forecast;
        private final List<ScalingRequirement> requirements;
        private final ScalingSchedule schedule;
        private final CostOptimizedSchedule optimizedSchedule;
        private final Instant createdAt;
        
        public PredictiveScalingPlan(String clusterId, WorkloadForecast forecast, List<ScalingRequirement> requirements,
                                   ScalingSchedule schedule, CostOptimizedSchedule optimizedSchedule, Instant createdAt) {
            this.clusterId = clusterId;
            this.forecast = forecast;
            this.requirements = requirements != null ? new ArrayList<>(requirements) : new ArrayList<>();
            this.schedule = schedule;
            this.optimizedSchedule = optimizedSchedule;
            this.createdAt = createdAt;
        }
        
        public String getClusterId() { return clusterId; }
        public WorkloadForecast getForecast() { return forecast; }
        public List<ScalingRequirement> getRequirements() { return new ArrayList<>(requirements); }
        public ScalingSchedule getSchedule() { return schedule; }
        public CostOptimizedSchedule getOptimizedSchedule() { return optimizedSchedule; }
        public Instant getCreatedAt() { return createdAt; }
    }
    
    /**
     * Cost Optimized Schedule
     */
    public static class CostOptimizedSchedule {
        private final ScalingSchedule originalSchedule;
        private final double estimatedSavings;
        private final List<String> optimizations;
        
        public CostOptimizedSchedule(ScalingSchedule originalSchedule, double estimatedSavings, List<String> optimizations) {
            this.originalSchedule = originalSchedule;
            this.estimatedSavings = estimatedSavings;
            this.optimizations = optimizations != null ? new ArrayList<>(optimizations) : new ArrayList<>();
        }
        
        public ScalingSchedule getOriginalSchedule() { return originalSchedule; }
        public double getEstimatedSavings() { return estimatedSavings; }
        public List<String> getOptimizations() { return new ArrayList<>(optimizations); }
    }
    
    /**
     * Cluster State Record
     */
    public static class ClusterStateRecord {
        private final String clusterId;
        private final int nodeCount;
        private final double averageCpuUsage;
        private final double averageMemoryUsage;
        private final double totalThroughput;
        private final Instant timestamp;
        
        public ClusterStateRecord(String clusterId, int nodeCount, double averageCpuUsage, double averageMemoryUsage,
                          double totalThroughput, Instant timestamp) {
            this.clusterId = clusterId;
            this.nodeCount = nodeCount;
            this.averageCpuUsage = averageCpuUsage;
            this.averageMemoryUsage = averageMemoryUsage;
            this.totalThroughput = totalThroughput;
            this.timestamp = timestamp;
        }
        
        public String getClusterId() { return clusterId; }
        public int getNodeCount() { return nodeCount; }
        public double getAverageCpuUsage() { return averageCpuUsage; }
        public double getAverageMemoryUsage() { return averageMemoryUsage; }
        public double getTotalThroughput() { return totalThroughput; }
        public Instant getTimestamp() { return timestamp; }
    }
    
    /**
     * Cost Driver
     */
    public static class CostDriver {
        private final Type type;
        private final String description;
        private final double value;
        private final Priority priority;
        
        public CostDriver(Type type, String description, double value, Priority priority) {
            this.type = type;
            this.description = description;
            this.value = value;
            this.priority = priority;
        }
        
        public Type getType() { return type; }
        public String getDescription() { return description; }
        public double getValue() { return value; }
        public Priority getPriority() { return priority; }
        
        public enum Type {
            HIGH_CPU_USAGE, HIGH_MEMORY_USAGE, OVER_PROVISIONING, INEFFICIENT_ALLOCATION
        }
        
        public enum Priority {
            HIGH, MEDIUM, LOW
        }
    }
    
    /**
     * Optimization Strategy
     */
    public static class OptimizationStrategy {
        private final Type type;
        private final String description;
        private final Priority priority;
        
        public OptimizationStrategy(Type type, String description, Priority priority) {
            this.type = type;
            this.description = description;
            this.priority = priority;
        }
        
        public Type getType() { return type; }
        public String getDescription() { return description; }
        public Priority getPriority() { return priority; }
        
        public enum Type {
            OPTIMIZE_CPU, OPTIMIZE_MEMORY, RIGHT_SIZE, CONSOLIDATE, SHUTDOWN_IDLE
        }
        
        public enum Priority {
            HIGH, MEDIUM, LOW
        }
    }
    
    /**
     * Optimization Constraints
     */
    public static class OptimizationConstraints {
        private final double maxCostReduction;
        private final double minPerformanceLevel;
        private final List<String> prohibitedActions;
        
        public OptimizationConstraints(double maxCostReduction, double minPerformanceLevel, List<String> prohibitedActions) {
            this.maxCostReduction = maxCostReduction;
            this.minPerformanceLevel = minPerformanceLevel;
            this.prohibitedActions = prohibitedActions != null ? new ArrayList<>(prohibitedActions) : new ArrayList<>();
        }
        
        public double getMaxCostReduction() { return maxCostReduction; }
        public double getMinPerformanceLevel() { return minPerformanceLevel; }
        public List<String> getProhibitedActions() { return new ArrayList<>(prohibitedActions); }
    }
    
    /**
     * Strategy Evaluation
     */
    public static class StrategyEvaluation {
        private final OptimizationStrategy strategy;
        private final double costSavings;
        private final double implementationComplexity;
        private final double riskScore;
        private final double overallScore;
        
        public StrategyEvaluation(OptimizationStrategy strategy, double costSavings, double implementationComplexity,
                               double riskScore, double overallScore) {
            this.strategy = strategy;
            this.costSavings = costSavings;
            this.implementationComplexity = implementationComplexity;
            this.riskScore = riskScore;
            this.overallScore = overallScore;
        }
        
        public OptimizationStrategy getStrategy() { return strategy; }
        public double getCostSavings() { return costSavings; }
        public double getImplementationComplexity() { return implementationComplexity; }
        public double getRiskScore() { return riskScore; }
        public double getOverallScore() { return overallScore; }
    }
    
    /**
     * Optimization Plan
     */
    public static class OptimizationPlan {
        private final OptimizationStrategy strategy;
        private final List<String> steps;
        private final long estimatedImplementationTime;
        private final Map<String, Object> resources;
        
        public OptimizationPlan(OptimizationStrategy strategy, List<String> steps, long estimatedImplementationTime,
                             Map<String, Object> resources) {
            this.strategy = strategy;
            this.steps = steps != null ? new ArrayList<>(steps) : new ArrayList<>();
            this.estimatedImplementationTime = estimatedImplementationTime;
            this.resources = resources != null ? new HashMap<>(resources) : new HashMap<>();
        }
        
        public OptimizationStrategy getStrategy() { return strategy; }
        public List<String> getSteps() { return new ArrayList<>(steps); }
        public long getEstimatedImplementationTime() { return estimatedImplementationTime; }
        public Map<String, Object> getResources() { return new HashMap<>(resources); }
    }
    
    /**
     * Scaling Pattern
     */
    public static class ScalingPattern {
        private final String patternId;
        private final String description;
        private final double frequency;
        private final double impact;
        private final Type type;
        private final Confidence confidence;
        
        public ScalingPattern(String patternId, String description, double frequency, double impact, Type type, Confidence confidence) {
            this.patternId = patternId;
            this.description = description;
            this.frequency = frequency;
            this.impact = impact;
            this.type = type;
            this.confidence = confidence;
        }
        
        public ScalingPattern(String patternId, String description, double frequency, double impact) {
            this(patternId, description, frequency, impact, Type.UNKNOWN, Confidence.MEDIUM);
        }
        
        public String getPatternId() { return patternId; }
        public String getDescription() { return description; }
        public double getFrequency() { return frequency; }
        public double getImpact() { return impact; }
        public Type getType() { return type; }
        public Confidence getConfidence() { return confidence; }
        
        public enum Type {
            HIGH_SUCCESS_RATE, FREQUENT_SCALING, LOW_LATENCY, HIGH_THROUGHPUT, UNKNOWN
        }
        
        public enum Confidence {
            LOW, MEDIUM, HIGH
        }
    }
    
    /**
     * Scaling Efficiency Issue
     */
    public static class ScalingEfficiencyIssue {
        private final String issueId;
        private final Type type;
        private final String description;
        private final Severity severity;
        private final String recommendation;
        
        public ScalingEfficiencyIssue(String issueId, Type type, String description, Severity severity, String recommendation) {
            this.issueId = issueId;
            this.type = type;
            this.description = description;
            this.severity = severity;
            this.recommendation = recommendation;
        }
        
        public String getIssueId() { return issueId; }
        public Type getType() { return type; }
        public String getDescription() { return description; }
        public Severity getSeverity() { return severity; }
        public String getRecommendation() { return recommendation; }
        
        public enum Type {
            LOW_SUCCESS_RATE, TOO_MANY_CONCURRENT_SCALING, HIGH_LATENCY, RESOURCE_WASTE
        }
        
        public enum Severity {
            LOW, MEDIUM, HIGH, CRITICAL
        }
    }
    
    /**
     * Scaling Insight
     */
    public static class ScalingInsight {
        private final String insightId;
        private final Type type;
        private final String description;
        private final double confidence;
        private final Map<String, Object> data;
        
        public ScalingInsight(String insightId, Type type, String description, double confidence, Map<String, Object> data) {
            this.insightId = insightId;
            this.type = type;
            this.description = description;
            this.confidence = confidence;
            this.data = data != null ? new HashMap<>(data) : new HashMap<>();
        }
        
        public String getInsightId() { return insightId; }
        public Type getType() { return type; }
        public String getDescription() { return description; }
        public double getConfidence() { return confidence; }
        public Map<String, Object> getData() { return new HashMap<>(data); }
        
        public enum Type {
            PERFORMANCE_SUMMARY, ISSUE_ALERT, RECOMMENDATION, TREND_ANALYSIS
        }
    }
    
    /**
     * Cost Efficiency Metrics
     */
    public static class CostEfficiencyMetrics {
        private final double costPerRequest;
        private final double resourceUtilization;
        private final double wastePercentage;
        private final double optimizationPotential;
        private final double wastedCost;
        
        public CostEfficiencyMetrics(double costPerRequest, double resourceUtilization, double wastePercentage, double optimizationPotential) {
            this.costPerRequest = costPerRequest;
            this.resourceUtilization = resourceUtilization;
            this.wastePercentage = wastePercentage;
            this.optimizationPotential = optimizationPotential;
            this.wastedCost = costPerRequest * wastePercentage / 100.0;
        }
        
        public double getCostPerRequest() { return costPerRequest; }
        public double getResourceUtilization() { return resourceUtilization; }
        public double getWastePercentage() { return wastePercentage; }
        public double getOptimizationPotential() { return optimizationPotential; }
        public double getWastedCost() { return wastedCost; }
    }
    
    /**
     * Auto Scaling Recommendation
     */
    public static class AutoScalingRecommendation {
        private final String recommendationId;
        private final Type type;
        private final String description;
        private final double priority;
        private final String action;
        
        public AutoScalingRecommendation(String recommendationId, Type type, String description, double priority, String action) {
            this.recommendationId = recommendationId;
            this.type = type;
            this.description = description;
            this.priority = priority;
            this.action = action;
        }
        
        public String getRecommendationId() { return recommendationId; }
        public Type getType() { return type; }
        public String getDescription() { return description; }
        public double getPriority() { return priority; }
        public String getAction() { return action; }
        
        public enum Type {
            OPTIMIZE_POLICIES, REDUCE_FREQUENCY, REDUCE_COSTS, IMPROVE_PERFORMANCE
        }
        
        public enum Severity {
            LOW, MEDIUM, HIGH, CRITICAL
        }
    }
    
    /**
     * Policy Change
     */
    public static class PolicyChange {
        private final String changeId;
        private final ChangeType type;
        private final String policyId;
        private final String description;
        
        public PolicyChange(String changeId, ChangeType type, String policyId, String description) {
            this.changeId = changeId;
            this.type = type;
            this.policyId = policyId;
            this.description = description;
        }
        
        public String getChangeId() { return changeId; }
        public ChangeType getType() { return type; }
        public String getPolicyId() { return policyId; }
        public String getDescription() { return description; }
        
        public enum ChangeType {
            ADD, REMOVE, UPDATE, ENABLE, DISABLE, MODIFIED, ADDED, REMOVED
        }
    }
    
    /**
     * Optimization Step
     */
    public static class OptimizationStep {
        private final String description;
        private final String action;
        private final int estimatedDuration;
        
        public OptimizationStep(String description, String action, int estimatedDuration) {
            this.description = description;
            this.action = action;
            this.estimatedDuration = estimatedDuration;
        }
        
        public String getDescription() { return description; }
        public String getAction() { return action; }
        public int getEstimatedDuration() { return estimatedDuration; }
    }
    
    /**
     * Scaling State
     */
    public static class ScalingState {
        private final String clusterId;
        private ScalingAction lastScalingAction;
        private Instant lastScalingTime;
        private boolean scalingInProgress;
        
        public ScalingState(String clusterId) {
            this.clusterId = clusterId;
            this.scalingInProgress = false;
        }
        
        public String getClusterId() { return clusterId; }
        public ScalingAction getLastScalingAction() { return lastScalingAction; }
        public Instant getLastScalingTime() { return lastScalingTime; }
        public boolean isScalingInProgress() { return scalingInProgress; }
        
        public void setLastScalingAction(ScalingAction lastScalingAction) { this.lastScalingAction = lastScalingAction; }
        public void setLastScalingTime(Instant lastScalingTime) { this.lastScalingTime = lastScalingTime; }
        public void setScalingInProgress(boolean scalingInProgress) { this.scalingInProgress = scalingInProgress; }
    }
    
    /**
     * Cluster rebalancing result
     */
    public static class ClusterRebalancingResult {
        private final String requestId;
        private final boolean success;
        private final String clusterId;
        private final List<String> movedNodes;
        private final String errorMessage;
        
        public ClusterRebalancingResult(String requestId, boolean success, String clusterId, 
                                      List<String> movedNodes, String errorMessage) {
            this.requestId = requestId;
            this.success = success;
            this.clusterId = clusterId;
            this.movedNodes = movedNodes;
            this.errorMessage = errorMessage;
        }
        
        public String getRequestId() { return requestId; }
        public boolean isSuccess() { return success; }
        public String getClusterId() { return clusterId; }
        public List<String> getMovedNodes() { return movedNodes; }
        public String getErrorMessage() { return errorMessage; }
    }
    
}
