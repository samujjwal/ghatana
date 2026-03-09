package com.ghatana.aep.scaling.models;

import io.activej.promise.Promise;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Model classes for distributed processing.
 *
 * <p>Purpose: Contains domain models for distributed pattern processing
 * including PatternData, ProcessingNode, and related types. Used by the
 * distributed processing layer for cross-node communication.</p>
 *
 * @doc.type class
 * @doc.purpose Container for distributed processing model classes
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
public class DistributedModels {
    
    /**
     * Pattern data for distributed processing
     */
    public static class PatternData {
        private final String id;
        private final String type;
        private final byte[] data;
        private final Map<String, Object> metadata;
        private final long timestamp;
        
        public PatternData(String id, String type, byte[] data, Map<String, Object> metadata) {
            this.id = id;
            this.type = type;
            this.data = data != null ? data.clone() : new byte[0];
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getId() { return id; }
        public String getType() { return type; }
        public byte[] getData() { return data.clone(); }
        public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Processing node configuration
     */
    public static class ProcessingNode {
        private final String nodeId;
        private final String host;
        private final int port;
        private final int capacity;
        private final NodeStatus status;
        private final Map<String, Object> capabilities;
        private final long lastHeartbeat;
        
        public ProcessingNode(String nodeId, String host, int port, int capacity, NodeStatus status) {
            this.nodeId = nodeId;
            this.host = host;
            this.port = port;
            this.capacity = capacity;
            this.status = status;
            this.capabilities = new HashMap<>();
            this.lastHeartbeat = System.currentTimeMillis();
        }
        
        public String getNodeId() { return nodeId; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public int getCapacity() { return capacity; }
        public NodeStatus getStatus() { return status; }
        public Map<String, Object> getCapabilities() { return new HashMap<>(capabilities); }
        public long getLastHeartbeat() { return lastHeartbeat; }
        
        public String getId() { return nodeId; }
        public double getCurrentLoad() { return capacity * 0.7; } // Simulated current load
        public double getMaxCapacity() { return capacity; }
        public int getPatternCount() { return (int) (capacity * 0.5); } // Simulated pattern count
    }
    
    /**
     * Node status enumeration
     */
    public enum NodeStatus {
        STARTING,
        ACTIVE,
        IDLE,
        BUSY,
        STOPPING,
        STOPPED,
        ERROR
    }
    
    /**
     * Node state enumeration
     */
    public enum NodeState {
        STARTING,
        ACTIVE,
        IDLE,
        BUSY,
        STOPPING,
        STOPPED,
        ERROR,
        MAINTENANCE,
        FAILED
    }
    
    /**
     * Node processing result
     */
    public static class NodeProcessingResult {
        private final String nodeId;
        private final boolean success;
        private final List<PatternData> processedPatterns;
        private final String errorMessage;
        private final long processingTimeMs;
        private final Map<String, Object> metrics;
        private final long timestamp;
        
        public NodeProcessingResult(String nodeId, boolean success, List<PatternData> processedPatterns,
                                  String errorMessage, long processingTimeMs) {
            this.nodeId = nodeId;
            this.success = success;
            this.processedPatterns = processedPatterns != null ? new ArrayList<>(processedPatterns) : new ArrayList<>();
            this.errorMessage = errorMessage;
            this.processingTimeMs = processingTimeMs;
            this.metrics = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getNodeId() { return nodeId; }
        public boolean isSuccess() { return success; }
        public List<PatternData> getProcessedPatterns() { return new ArrayList<>(processedPatterns); }
        public String getErrorMessage() { return errorMessage; }
        public long getProcessingTimeMs() { return processingTimeMs; }
        public Map<String, Object> getMetrics() { return new HashMap<>(metrics); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Distributed processing result
     */
    public static class DistributedProcessingResult {
        private final boolean success;
        private final List<NodeProcessingResult> nodeResults;
        private final List<PatternData> aggregatedResults;
        private final List<PatternData> results;
        private final String errorMessage;
        private final long totalProcessingTimeMs;
        private final Map<String, Object> metrics;
        private final long timestamp;
        
        public DistributedProcessingResult(boolean success, List<NodeProcessingResult> nodeResults,
                                         List<PatternData> aggregatedResults, String errorMessage,
                                         long totalProcessingTimeMs) {
            this.success = success;
            this.nodeResults = nodeResults != null ? new ArrayList<>(nodeResults) : new ArrayList<>();
            this.aggregatedResults = aggregatedResults != null ? new ArrayList<>(aggregatedResults) : new ArrayList<>();
            this.results = aggregatedResults != null ? new ArrayList<>(aggregatedResults) : new ArrayList<>();
            this.errorMessage = errorMessage;
            this.totalProcessingTimeMs = totalProcessingTimeMs;
            this.metrics = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public boolean isSuccess() { return success; }
        public List<NodeProcessingResult> getNodeResults() { return new ArrayList<>(nodeResults); }
        public List<PatternData> getAggregatedResults() { return new ArrayList<>(aggregatedResults); }
        public List<PatternData> getResults() { return new ArrayList<>(results); }
        public String getErrorMessage() { return errorMessage; }
        public long getTotalProcessingTimeMs() { return totalProcessingTimeMs; }
        public Map<String, Object> getMetrics() { return new HashMap<>(metrics); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Node startup result
     */
    public static class NodeStartupResult {
        private final String nodeId;
        private final boolean success;
        private final String host;
        private final int port;
        private final String errorMessage;
        private final long startupTimeMs;
        private final Map<String, Object> nodeInfo;
        private final long timestamp;
        
        public NodeStartupResult(String nodeId, boolean success, String host, int port,
                                String errorMessage, long startupTimeMs) {
            this.nodeId = nodeId;
            this.success = success;
            this.host = host;
            this.port = port;
            this.errorMessage = errorMessage;
            this.startupTimeMs = startupTimeMs;
            this.nodeInfo = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getNodeId() { return nodeId; }
        public boolean isSuccess() { return success; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public String getErrorMessage() { return errorMessage; }
        public long getStartupTimeMs() { return startupTimeMs; }
        public Map<String, Object> getNodeInfo() { return new HashMap<>(nodeInfo); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Distributed processing request
     */
    public static class DistributedProcessingRequest {
        private final String requestId;
        private final Map<String, List<PatternData>> patternsByNode;
        private final List<PatternData> patterns;
        private final ProcessingOptions processingOptions;
        private final Map<String, Object> requestMetadata;
        private final long timestamp;
        
        public DistributedProcessingRequest(String requestId, Map<String, List<PatternData>> patternsByNode,
                                         ProcessingOptions processingOptions) {
            this.requestId = requestId;
            this.patternsByNode = patternsByNode != null ? new HashMap<>(patternsByNode) : new HashMap<>();
            this.patterns = extractAllPatterns(patternsByNode);
            this.processingOptions = processingOptions;
            this.requestMetadata = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        private List<PatternData> extractAllPatterns(Map<String, List<PatternData>> patternsByNode) {
            List<PatternData> allPatterns = new ArrayList<>();
            for (List<PatternData> patternList : patternsByNode.values()) {
                allPatterns.addAll(patternList);
            }
            return allPatterns;
        }
        
        public String getRequestId() { return requestId; }
        public Map<String, List<PatternData>> getPatternsByNode() { 
            Map<String, List<PatternData>> result = new HashMap<>();
            for (Map.Entry<String, List<PatternData>> entry : patternsByNode.entrySet()) {
                result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            return result;
        }
        public List<PatternData> getPatterns() { return new ArrayList<>(patterns); }
        public ProcessingOptions getProcessingOptions() { return processingOptions; }
        public Map<String, Object> getRequestMetadata() { return new HashMap<>(requestMetadata); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Processing options for distributed processing
     */
    public static class ProcessingOptions {
        private final int maxConcurrency;
        private final long timeoutMs;
        private final boolean enableFaultTolerance;
        private final int retryAttempts;
        private final Map<String, Object> algorithmParameters;
        
        public ProcessingOptions(int maxConcurrency, long timeoutMs, boolean enableFaultTolerance,
                                int retryAttempts) {
            this.maxConcurrency = maxConcurrency;
            this.timeoutMs = timeoutMs;
            this.enableFaultTolerance = enableFaultTolerance;
            this.retryAttempts = retryAttempts;
            this.algorithmParameters = new HashMap<>();
        }
        
        public int getMaxConcurrency() { return maxConcurrency; }
        public long getTimeoutMs() { return timeoutMs; }
        public boolean isEnableFaultTolerance() { return enableFaultTolerance; }
        public int getRetryAttempts() { return retryAttempts; }
        public Map<String, Object> getAlgorithmParameters() { return new HashMap<>(algorithmParameters); }
    }
    
    /**
     * Node configuration for startup
     */
    public static class NodeConfiguration {
        private final String nodeId;
        private final String host;
        private final int port;
        private final int capacity;
        private final Map<String, Object> capabilities;
        private final Map<String, Object> environment;
        
        public NodeConfiguration(String nodeId, String host, int port, int capacity,
                                Map<String, Object> capabilities, Map<String, Object> environment) {
            this.nodeId = nodeId;
            this.host = host;
            this.port = port;
            this.capacity = capacity;
            this.capabilities = capabilities != null ? new HashMap<>(capabilities) : new HashMap<>();
            this.environment = environment != null ? new HashMap<>(environment) : new HashMap<>();
        }
        
        public String getNodeId() { return nodeId; }
        public String getHost() { return host; }
        public int getPort() { return port; }
        public int getCapacity() { return capacity; }
        public Map<String, Object> getCapabilities() { return new HashMap<>(capabilities); }
        public Map<String, Object> getEnvironment() { return new HashMap<>(environment); }
    }
    
    /**
     * Node shutdown result
     */
    public static class NodeShutdownResult {
        private final String nodeId;
        private final boolean success;
        private final String errorMessage;
        private final long shutdownTimeMs;
        private final Map<String, Object> finalMetrics;
        private final long timestamp;
        
        public NodeShutdownResult(String nodeId, boolean success, String errorMessage, long shutdownTimeMs) {
            this.nodeId = nodeId;
            this.success = success;
            this.errorMessage = errorMessage;
            this.shutdownTimeMs = shutdownTimeMs;
            this.finalMetrics = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getNodeId() { return nodeId; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public long getShutdownTimeMs() { return shutdownTimeMs; }
        public Map<String, Object> getFinalMetrics() { return new HashMap<>(finalMetrics); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Load balancing strategy
     */
    public enum LoadBalancingStrategy {
        ROUND_ROBIN,
        WEIGHTED_ROUND_ROBIN,
        LEAST_CONNECTIONS,
        LEAST_RESPONSE_TIME,
        HASH_BASED,
        ADAPTIVE
    }
    
    /**
     * Load balancer metrics
     */
    public static class LoadBalancerMetrics {
        private final String loadBalancerId;
        private final Map<String, Integer> nodeConnections;
        private final Map<String, Double> nodeResponseTimes;
        private final Map<String, Double> nodeWeights;
        private final int totalRequests;
        private final int activeConnections;
        private final long timestamp;
        
        public LoadBalancerMetrics(String loadBalancerId) {
            this.loadBalancerId = loadBalancerId;
            this.nodeConnections = new HashMap<>();
            this.nodeResponseTimes = new HashMap<>();
            this.nodeWeights = new HashMap<>();
            this.totalRequests = 0;
            this.activeConnections = 0;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getLoadBalancerId() { return loadBalancerId; }
        public Map<String, Integer> getNodeConnections() { return new HashMap<>(nodeConnections); }
        public Map<String, Double> getNodeResponseTimes() { return new HashMap<>(nodeResponseTimes); }
        public Map<String, Double> getNodeWeights() { return new HashMap<>(nodeWeights); }
        public int getTotalRequests() { return totalRequests; }
        public int getActiveConnections() { return activeConnections; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Routing table entry
     */
    public static class RoutingTableEntry {
        private final String pattern;
        private final List<String> nodeIds;
        private final LoadBalancingStrategy strategy;
        private final Map<String, Double> weights;
        private final long lastUpdated;
        
        public RoutingTableEntry(String pattern, List<String> nodeIds, LoadBalancingStrategy strategy) {
            this.pattern = pattern;
            this.nodeIds = nodeIds != null ? new ArrayList<>(nodeIds) : new ArrayList<>();
            this.strategy = strategy;
            this.weights = new HashMap<>();
            this.lastUpdated = System.currentTimeMillis();
        }
        
        public String getPattern() { return pattern; }
        public List<String> getNodeIds() { return new ArrayList<>(nodeIds); }
        public LoadBalancingStrategy getStrategy() { return strategy; }
        public Map<String, Double> getWeights() { return new HashMap<>(weights); }
        public long getLastUpdated() { return lastUpdated; }
    }
    
    /**
     * Load distribution
     */
    public static class LoadDistribution {
        private final Map<String, Integer> nodeLoads;
        private final Map<String, Double> nodeCapacities;
        private final double totalLoad;
        private final double totalCapacity;
        private final long timestamp;
        
        public LoadDistribution(Map<String, Integer> nodeLoads, Map<String, Double> nodeCapacities) {
            this.nodeLoads = nodeLoads != null ? new HashMap<>(nodeLoads) : new HashMap<>();
            this.nodeCapacities = nodeCapacities != null ? new HashMap<>(nodeCapacities) : new HashMap<>();
            this.totalLoad = this.nodeLoads.values().stream().mapToInt(Integer::intValue).sum();
            this.totalCapacity = this.nodeCapacities.values().stream().mapToDouble(Double::doubleValue).sum();
            this.timestamp = System.currentTimeMillis();
        }
        
        public LoadDistribution() {
            this.nodeLoads = new HashMap<>();
            this.nodeCapacities = new HashMap<>();
            this.totalLoad = 0.0;
            this.totalCapacity = 0.0;
            this.timestamp = System.currentTimeMillis();
        }
        
        public Map<String, Integer> getNodeLoads() { return new HashMap<>(nodeLoads); }
        public Map<String, Double> getNodeCapacities() { return new HashMap<>(nodeCapacities); }
        public double getTotalLoad() { return totalLoad; }
        public double getTotalCapacity() { return totalCapacity; }
        public long getTimestamp() { return timestamp; }
        
        public Integer getNodeLoadInt(String nodeId) { return nodeLoads.get(nodeId); }
        
        // Additional methods for compatibility
        public NodeLoad getNodeLoad(String nodeId) {
            Integer load = nodeLoads.get(nodeId);
            Double capacity = nodeCapacities.get(nodeId);
            if (load != null && capacity != null) {
                return new NodeLoad(nodeId, load, capacity, load / 10);
            }
            return new NodeLoad(nodeId, 0.0, 100.0, 0);
        }
        
        public void addNodeLoad(NodeLoad nodeLoad) {
            nodeLoads.put(nodeLoad.getNodeId(), (int) nodeLoad.getCurrentLoad());
            nodeCapacities.put(nodeLoad.getNodeId(), nodeLoad.getMaxCapacity());
        }
    }
    
    /**
     * Migration plan
     */
    public static class MigrationPlan {
        private final List<MigrationTask> tasks;
        private final String reason;
        private final long estimatedDurationMs;
        private final Map<String, Object> planMetadata;
        private final long timestamp;
        
        public MigrationPlan(List<MigrationTask> tasks, String reason, long estimatedDurationMs) {
            this.tasks = tasks != null ? new ArrayList<>(tasks) : new ArrayList<>();
            this.reason = reason;
            this.estimatedDurationMs = estimatedDurationMs;
            this.planMetadata = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public List<MigrationTask> getTasks() { return new ArrayList<>(tasks); }
        public String getReason() { return reason; }
        public long getEstimatedDurationMs() { return estimatedDurationMs; }
        public Map<String, Object> getPlanMetadata() { return new HashMap<>(planMetadata); }
        public long getTimestamp() { return timestamp; }
        
        public void addTask(MigrationTask task) {
            this.tasks.add(task);
        }
    }
    
    /**
     * Migration task
     */
    public static class MigrationTask {
        private final String taskId;
        private final String sourceNodeId;
        private final String targetNodeId;
        private final List<PatternData> patternsToMigrate;
        private final MigrationType type;
        private final long scheduledTime;
        private final Map<String, Object> taskMetadata;
        private final long timestamp;
        
        public MigrationTask(String sourceNodeId, String targetNodeId, List<PatternData> patternsToMigrate,
                           MigrationType type) {
            this.taskId = "migration-" + System.currentTimeMillis() + "-" + hashCode();
            this.sourceNodeId = sourceNodeId;
            this.targetNodeId = targetNodeId;
            this.patternsToMigrate = patternsToMigrate != null ? new ArrayList<>(patternsToMigrate) : new ArrayList<>();
            this.type = type;
            this.scheduledTime = System.currentTimeMillis();
            this.taskMetadata = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public enum MigrationType {
            MOVE,
            COPY,
            SWAP
        }
        
        public String getTaskId() { return taskId; }
        public String getSourceNodeId() { return sourceNodeId; }
        public String getTargetNodeId() { return targetNodeId; }
        public List<PatternData> getPatternsToMigrate() { return new ArrayList<>(patternsToMigrate); }
        public MigrationType getType() { return type; }
        public long getScheduledTime() { return scheduledTime; }
        public Map<String, Object> getTaskMetadata() { return new HashMap<>(taskMetadata); }
        public long getTimestamp() { return timestamp; }
        
        public String getNodeId() { return sourceNodeId; }
        public int getAmount() { return patternsToMigrate.size(); }
    }
    
    /**
     * Migration result
     */
    public static class MigrationResult {
        private final String taskId;
        private final boolean success;
        private final int migratedPatterns;
        private final int failedPatterns;
        private final String errorMessage;
        private final long migrationTimeMs;
        private final Map<String, Object> resultMetadata;
        private final long timestamp;
        
        public MigrationResult(String taskId, boolean success, int migratedPatterns, int failedPatterns,
                              String errorMessage, long migrationTimeMs) {
            this.taskId = taskId;
            this.success = success;
            this.migratedPatterns = migratedPatterns;
            this.failedPatterns = failedPatterns;
            this.errorMessage = errorMessage;
            this.migrationTimeMs = migrationTimeMs;
            this.resultMetadata = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getTaskId() { return taskId; }
        public boolean isSuccess() { return success; }
        public int getMigratedPatterns() { return migratedPatterns; }
        public int getFailedPatterns() { return failedPatterns; }
        public String getErrorMessage() { return errorMessage; }
        public long getMigrationTimeMs() { return migrationTimeMs; }
        public Map<String, Object> getResultMetadata() { return new HashMap<>(resultMetadata); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Cluster health
     */
    public static class ClusterHealth {
        private final String clusterId;
        private final HealthStatus overallStatus;
        private final Map<String, NodeHealth> nodeHealth;
        private final double healthScore;
        private final Map<String, Object> healthMetrics;
        private final long timestamp;
        
        public ClusterHealth(String clusterId, HealthStatus overallStatus, Map<String, NodeHealth> nodeHealth) {
            this.clusterId = clusterId;
            this.overallStatus = overallStatus;
            this.nodeHealth = nodeHealth != null ? new HashMap<>(nodeHealth) : new HashMap<>();
            this.healthScore = calculateHealthScore();
            this.healthMetrics = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public enum HealthStatus {
            HEALTHY,
            DEGRADED,
            UNHEALTHY,
            CRITICAL
        }
        
        public enum HealthLevel {
            HEALTHY,
            DEGRADED,
            CRITICAL
        }
        
        private double calculateHealthScore() {
            if (nodeHealth.isEmpty()) return 0.0;
            return nodeHealth.values().stream()
                .mapToDouble(NodeHealth::getHealthScore)
                .average()
                .orElse(0.0);
        }
        
        public String getClusterId() { return clusterId; }
        public HealthStatus getOverallStatus() { return overallStatus; }
        public Map<String, NodeHealth> getNodeHealth() { return new HashMap<>(nodeHealth); }
        public double getHealthScore() { return healthScore; }
        public Map<String, Object> getHealthMetrics() { return new HashMap<>(healthMetrics); }
        public long getTimestamp() { return timestamp; }
        
        public boolean isHealthy() {
            return overallStatus == HealthStatus.HEALTHY && healthScore >= 0.8;
        }
    }
    
    /**
     * Node health
     */
    public static class NodeHealth {
        private final String nodeId;
        private final ClusterHealth.HealthStatus status;
        private final double healthScore;
        private final Map<String, Double> metrics;
        private final List<String> issues;
        private final long lastCheck;
        
        public NodeHealth(String nodeId, ClusterHealth.HealthStatus status, double healthScore,
                         Map<String, Double> metrics, List<String> issues) {
            this.nodeId = nodeId;
            this.status = status;
            this.healthScore = healthScore;
            this.metrics = metrics != null ? new HashMap<>(metrics) : new HashMap<>();
            this.issues = issues != null ? new ArrayList<>(issues) : new ArrayList<>();
            this.lastCheck = System.currentTimeMillis();
        }
        
        public String getNodeId() { return nodeId; }
        public ClusterHealth.HealthStatus getStatus() { return status; }
        public double getHealthScore() { return healthScore; }
        public Map<String, Double> getMetrics() { return new HashMap<>(metrics); }
        public List<String> getIssues() { return new ArrayList<>(issues); }
        public long getLastCheck() { return lastCheck; }
        
        public boolean isHealthy() {
            return status == ClusterHealth.HealthStatus.HEALTHY && healthScore >= 0.8;
        }
    }
    
    /**
     * Node metrics
     */
    public static class NodeMetrics {
        private final String nodeId;
        private final double cpuUtilization;
        private final double memoryUtilization;
        private final double responseTime;
        private final double throughput;
        private final int activeConnections;
        private final Map<String, Double> customMetrics;
        private final long timestamp;
        
        public NodeMetrics(String nodeId, double cpuUtilization, double memoryUtilization,
                          double responseTime, double throughput, int activeConnections) {
            this.nodeId = nodeId;
            this.cpuUtilization = cpuUtilization;
            this.memoryUtilization = memoryUtilization;
            this.responseTime = responseTime;
            this.throughput = throughput;
            this.activeConnections = activeConnections;
            this.customMetrics = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getNodeId() { return nodeId; }
        public double getCpuUtilization() { return cpuUtilization; }
        public double getMemoryUtilization() { return memoryUtilization; }
        public double getResponseTime() { return responseTime; }
        public double getThroughput() { return throughput; }
        public int getActiveConnections() { return activeConnections; }
        public Map<String, Double> getCustomMetrics() { return new HashMap<>(customMetrics); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Performance issue
     */
    public static class PerformanceIssue {
        private final String issueId;
        private final String nodeId;
        private final IssueType type;
        private final String description;
        private final double severity;
        private final Map<String, Object> details;
        private final long timestamp;
        
        public enum IssueType {
            HIGH_CPU,
            HIGH_MEMORY,
            SLOW_RESPONSE,
            CONNECTION_OVERLOAD,
            NETWORK_ISSUE,
            DISK_SPACE,
            ERROR_RATE_HIGH
        }
        
        public PerformanceIssue(String nodeId, IssueType type, String description, double severity) {
            this.issueId = "issue-" + System.currentTimeMillis() + "-" + hashCode();
            this.nodeId = nodeId;
            this.type = type;
            this.description = description;
            this.severity = severity;
            this.details = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getIssueId() { return issueId; }
        public String getNodeId() { return nodeId; }
        public IssueType getType() { return type; }
        public String getDescription() { return description; }
        public double getSeverity() { return severity; }
        public Map<String, Object> getDetails() { return new HashMap<>(details); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Scaling recommendation
     */
    public static class ScalingRecommendation {
        private final String recommendationId;
        private final String nodeId;
        private final RecommendationType type;
        private final String description;
        private final int targetSize;
        private final double confidence;
        private final Map<String, Object> reasoning;
        private final long timestamp;
        
        public enum RecommendationType {
            SCALE_UP,
            SCALE_DOWN,
            ADD_NODE,
            REMOVE_NODE,
            REBALANCE,
            MAINTENANCE
        }
        
        public ScalingRecommendation(String nodeId, RecommendationType type, String description,
                                    int targetSize, double confidence) {
            this.recommendationId = "rec-" + System.currentTimeMillis() + "-" + hashCode();
            this.nodeId = nodeId;
            this.type = type;
            this.description = description;
            this.targetSize = targetSize;
            this.confidence = confidence;
            this.reasoning = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRecommendationId() { return recommendationId; }
        public String getNodeId() { return nodeId; }
        public RecommendationType getType() { return type; }
        public String getDescription() { return description; }
        public int getTargetSize() { return targetSize; }
        public double getConfidence() { return confidence; }
        public Map<String, Object> getReasoning() { return new HashMap<>(reasoning); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Scale out request
     */
    public static class ScaleOutRequest {
        private final String requestId;
        private final int nodeCount;
        private final NodeConfiguration nodeConfiguration;
        private final boolean rebalanceLoad;
        private final Map<String, Object> requestMetadata;
        private final long timestamp;
        
        public ScaleOutRequest(String requestId, int nodeCount, NodeConfiguration nodeConfiguration) {
            this(requestId, nodeCount, nodeConfiguration, true);
        }
        
        public ScaleOutRequest(String requestId, int nodeCount, NodeConfiguration nodeConfiguration, boolean rebalanceLoad) {
            this.requestId = requestId;
            this.nodeCount = nodeCount;
            this.nodeConfiguration = nodeConfiguration;
            this.rebalanceLoad = rebalanceLoad;
            this.requestMetadata = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRequestId() { return requestId; }
        public int getNodeCount() { return nodeCount; }
        public NodeConfiguration getNodeConfiguration() { return nodeConfiguration; }
        public boolean isRebalanceLoad() { return rebalanceLoad; }
        public Map<String, Object> getRequestMetadata() { return new HashMap<>(requestMetadata); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Scale out result
     */
    public static class ScaleOutResult {
        private final String requestId;
        private final boolean success;
        private final List<String> addedNodeIds;
        private final String errorMessage;
        private final long scaleOutTimeMs;
        private final Map<String, Object> resultMetadata;
        private final long timestamp;
        
        public ScaleOutResult(String requestId, boolean success, List<String> addedNodeIds,
                             String errorMessage, long scaleOutTimeMs) {
            this.requestId = requestId;
            this.success = success;
            this.addedNodeIds = addedNodeIds != null ? new ArrayList<>(addedNodeIds) : new ArrayList<>();
            this.errorMessage = errorMessage;
            this.scaleOutTimeMs = scaleOutTimeMs;
            this.resultMetadata = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRequestId() { return requestId; }
        public boolean isSuccess() { return success; }
        public List<String> getAddedNodeIds() { return new ArrayList<>(addedNodeIds); }
        public String getErrorMessage() { return errorMessage; }
        public long getScaleOutTimeMs() { return scaleOutTimeMs; }
        public Map<String, Object> getResultMetadata() { return new HashMap<>(resultMetadata); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Scale in request
     */
    public static class ScaleInRequest {
        private final String requestId;
        private final List<String> nodeIds;
        private final int nodeCount;
        private final RemovalStrategy strategy;
        private final boolean graceful;
        private final boolean migrateData;
        private final Map<String, Object> requestMetadata;
        private final long timestamp;
        
        public enum RemovalStrategy {
            LEAST_LOADED,
            OLDEST_FIRST,
            NEWEST_FIRST,
            SPECIFIC_NODES
        }
        
        public ScaleInRequest(String requestId, List<String> nodeIds, RemovalStrategy strategy) {
            this(requestId, nodeIds, strategy, true, true);
        }
        
        public ScaleInRequest(String requestId, List<String> nodeIds, RemovalStrategy strategy, boolean graceful, boolean migrateData) {
            this.requestId = requestId;
            this.nodeIds = nodeIds != null ? new ArrayList<>(nodeIds) : new ArrayList<>();
            this.nodeCount = this.nodeIds.size();
            this.strategy = strategy;
            this.graceful = graceful;
            this.migrateData = migrateData;
            this.requestMetadata = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRequestId() { return requestId; }
        public List<String> getNodeIds() { return new ArrayList<>(nodeIds); }
        public int getNodeCount() { return nodeCount; }
        public RemovalStrategy getStrategy() { return strategy; }
        public RemovalStrategy getRemovalStrategy() { return strategy; }
        public boolean isGraceful() { return graceful; }
        public boolean isMigrateData() { return migrateData; }
        public Map<String, Object> getRequestMetadata() { return new HashMap<>(requestMetadata); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Scale in result
     */
    public static class ScaleInResult {
        private final String requestId;
        private final boolean success;
        private final List<String> removedNodeIds;
        private final String errorMessage;
        private final long scaleInTimeMs;
        private final Map<String, Object> resultMetadata;
        private final long timestamp;
        
        public ScaleInResult(String requestId, boolean success, List<String> removedNodeIds,
                             String errorMessage, long scaleInTimeMs) {
            this.requestId = requestId;
            this.success = success;
            this.removedNodeIds = removedNodeIds != null ? new ArrayList<>(removedNodeIds) : new ArrayList<>();
            this.errorMessage = errorMessage;
            this.scaleInTimeMs = scaleInTimeMs;
            this.resultMetadata = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRequestId() { return requestId; }
        public boolean isSuccess() { return success; }
        public List<String> getRemovedNodeIds() { return new ArrayList<>(removedNodeIds); }
        public String getErrorMessage() { return errorMessage; }
        public long getScaleInTimeMs() { return scaleInTimeMs; }
        public Map<String, Object> getResultMetadata() { return new HashMap<>(resultMetadata); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Rebalancing request
     */
    public static class RebalancingRequest {
        private final String requestId;
        private final RebalancingStrategy strategy;
        private final Map<String, Object> parameters;
        private final long timestamp;
        
        public enum RebalancingStrategy {
            UNIFORM_DISTRIBUTION,
            LOAD_BASED,
            PERFORMANCE_BASED,
            COST_OPTIMIZED
        }
        
        public RebalancingRequest(String requestId, RebalancingStrategy strategy, Map<String, Object> parameters) {
            this.requestId = requestId;
            this.strategy = strategy;
            this.parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRequestId() { return requestId; }
        public RebalancingStrategy getStrategy() { return strategy; }
        public Map<String, Object> getParameters() { return new HashMap<>(parameters); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Rebalancing result
     */
    public static class RebalancingResult {
        private final String requestId;
        private final boolean success;
        private final List<MigrationTask> executedMigrations;
        private final String errorMessage;
        private final long rebalancingTimeMs;
        private final Map<String, Object> resultMetadata;
        private final long timestamp;
        
        public RebalancingResult(String requestId, boolean success, List<MigrationTask> executedMigrations,
                                String errorMessage, long rebalancingTimeMs) {
            this.requestId = requestId;
            this.success = success;
            this.executedMigrations = executedMigrations != null ? new ArrayList<>(executedMigrations) : new ArrayList<>();
            this.errorMessage = errorMessage;
            this.rebalancingTimeMs = rebalancingTimeMs;
            this.resultMetadata = new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRequestId() { return requestId; }
        public boolean isSuccess() { return success; }
        public List<MigrationTask> getExecutedMigrations() { return new ArrayList<>(executedMigrations); }
        public String getErrorMessage() { return errorMessage; }
        public long getRebalancingTimeMs() { return rebalancingTimeMs; }
        public Map<String, Object> getResultMetadata() { return new HashMap<>(resultMetadata); }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Cluster monitoring request
     */
    public static class ClusterMonitoringRequest {
        private final String requestId;
        private final List<String> nodeIds;
        private final List<String> metrics;
        private final long timeWindowMs;
        private final long timestamp;
        
        public ClusterMonitoringRequest(String requestId, List<String> nodeIds, List<String> metrics, long timeWindowMs) {
            this.requestId = requestId;
            this.nodeIds = nodeIds != null ? new ArrayList<>(nodeIds) : new ArrayList<>();
            this.metrics = metrics != null ? new ArrayList<>(metrics) : new ArrayList<>();
            this.timeWindowMs = timeWindowMs;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRequestId() { return requestId; }
        public List<String> getNodeIds() { return new ArrayList<>(nodeIds); }
        public List<String> getMetrics() { return new ArrayList<>(metrics); }
        public long getTimeWindowMs() { return timeWindowMs; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Cluster monitoring result
     */
    public static class ClusterMonitoringResult {
        private final String requestId;
        private final boolean success;
        private final Map<String, NodeMetrics> nodeMetrics;
        private final ClusterHealth clusterHealth;
        private final List<PerformanceIssue> issues;
        private final String errorMessage;
        private final long timestamp;
        
        public ClusterMonitoringResult(String requestId, boolean success, Map<String, NodeMetrics> nodeMetrics,
                                      ClusterHealth clusterHealth, List<PerformanceIssue> issues, String errorMessage) {
            this.requestId = requestId;
            this.success = success;
            this.nodeMetrics = nodeMetrics != null ? new HashMap<>(nodeMetrics) : new HashMap<>();
            this.clusterHealth = clusterHealth;
            this.issues = issues != null ? new ArrayList<>(issues) : new ArrayList<>();
            this.errorMessage = errorMessage;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRequestId() { return requestId; }
        public boolean isSuccess() { return success; }
        public Map<String, NodeMetrics> getNodeMetrics() { return new HashMap<>(nodeMetrics); }
        public ClusterHealth getClusterHealth() { return clusterHealth; }
        public List<PerformanceIssue> getIssues() { return new ArrayList<>(issues); }
        public String getErrorMessage() { return errorMessage; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Cluster manager interface
     */
    public interface ClusterManager {
        Promise<ClusterHealth> getClusterHealth();
        Promise<List<ProcessingNode>> getActiveNodes();
        Promise<Void> addNode(ProcessingNode node);
        Promise<Void> removeNode(String nodeId);
        Promise<NodeHealth> getNodeHealth(String nodeId);
    }
    
    /**
     * Load balancer interface
     */
    public interface LoadBalancer {
        Promise<String> selectNode(String pattern, List<String> availableNodes);
        Promise<Void> updateWeights(Map<String, Double> weights);
        Promise<LoadBalancerMetrics> getMetrics();
    }
    
    /**
     * Consistent hash router interface
     */
    public interface ConsistentHashRouter {
        Promise<String> route(String key, List<String> nodes);
        Promise<Void> addNode(String nodeId);
        Promise<Void> removeNode(String nodeId);
        Promise<Map<String, List<String>>> getDistribution();
    }
    
    /**
     * Failover manager interface
     */
    public interface FailoverManager {
        Promise<String> getFailoverNode(String failedNodeId);
        Promise<Void> handleNodeFailure(String nodeId);
        Promise<Boolean> isNodeHealthy(String nodeId);
        Promise<List<String>> getHealthyNodes();
    }
    
    /**
     * Distributed cache interface
     */
    public interface DistributedCache {
        Promise<Void> put(String key, byte[] value);
        Promise<byte[]> get(String key);
        Promise<Void> remove(String key);
        Promise<Boolean> contains(String key);
        Promise<Map<String, byte[]>> getAll();
        Promise<Void> clear();
    }
    
    /**
     * Cluster metrics (alias for AutoScalingModels.ClusterMetrics)
     */
    public static class ClusterMetrics {
        private final String clusterId;
        private final int currentSize;
        private final double cpuUtilization;
        private final double memoryUtilization;
        private final double requestRate;
        private final double responseTime;
        private final double throughput;
        private final long timestamp;
        private final Map<String, Double> customMetrics;
        
        public ClusterMetrics(String clusterId, int currentSize, double cpuUtilization, 
                            double memoryUtilization, double requestRate, double responseTime, double throughput) {
            this.clusterId = clusterId;
            this.currentSize = currentSize;
            this.cpuUtilization = cpuUtilization;
            this.memoryUtilization = memoryUtilization;
            this.requestRate = requestRate;
            this.responseTime = responseTime;
            this.throughput = throughput;
            this.timestamp = System.currentTimeMillis();
            this.customMetrics = new HashMap<>();
        }
        
        public String getClusterId() { return clusterId; }
        public int getCurrentSize() { return currentSize; }
        public double getCpuUtilization() { return cpuUtilization; }
        public double getMemoryUtilization() { return memoryUtilization; }
        public double getRequestRate() { return requestRate; }
        public double getResponseTime() { return responseTime; }
        public double getThroughput() { return throughput; }
        public long getTimestamp() { return timestamp; }
        public Map<String, Double> getCustomMetrics() { return new HashMap<>(customMetrics); }
        
        // Convenience methods
        public double getAverageCpuUsage() { return cpuUtilization; }
        public double getAverageMemoryUsage() { return memoryUtilization; }
        public double getAverageResponseTime() { return responseTime; }
        public double getTotalThroughput() { return throughput; }
    }
    
    /**
     * Node load information
     */
    public static class NodeLoad {
        private final String nodeId;
        private final double currentLoad;
        private final double maxCapacity;
        private final int patternCount;
        private final long timestamp;
        
        public NodeLoad(String nodeId, double currentLoad, double maxCapacity, int patternCount) {
            this.nodeId = nodeId;
            this.currentLoad = currentLoad;
            this.maxCapacity = maxCapacity;
            this.patternCount = patternCount;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getNodeId() { return nodeId; }
        public double getCurrentLoad() { return currentLoad; }
        public double getMaxCapacity() { return maxCapacity; }
        public int getPatternCount() { return patternCount; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Cluster rebalancing result
     */
    public static class ClusterRebalancingResult {
        private final String requestId;
        private final boolean success;
        private final int nodesMoved;
        private final String errorMessage;
        private final long timestamp;
        
        public ClusterRebalancingResult(String requestId, boolean success, int nodesMoved, String errorMessage) {
            this.requestId = requestId;
            this.success = success;
            this.nodesMoved = nodesMoved;
            this.errorMessage = errorMessage;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRequestId() { return requestId; }
        public boolean isSuccess() { return success; }
        public int getNodesMoved() { return nodesMoved; }
        public String getErrorMessage() { return errorMessage; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Cluster configuration
     */
    public static class ClusterConfiguration {
        private final String clusterId;
        private final int targetSize;
        private final Map<String, Object> settings;
        private final long timestamp;
        
        public ClusterConfiguration(String clusterId, int targetSize, Map<String, Object> settings) {
            this.clusterId = clusterId;
            this.targetSize = targetSize;
            this.settings = settings != null ? new HashMap<>(settings) : new HashMap<>();
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getClusterId() { return clusterId; }
        public int getTargetSize() { return targetSize; }
        public Map<String, Object> getSettings() { return new HashMap<>(settings); }
        public long getTimestamp() { return timestamp; }
        
        // Additional methods needed by ClusterManagementSystem
        public int getMaxNodes() { 
            return targetSize; 
        }
        
        public int getMinNodes() { 
            return 1; // Default minimum nodes
        }
    }
    
    /**
     * Node configuration update result
     */
    public static class NodeConfigurationUpdateResult {
        private final String nodeId;
        private final boolean success;
        private final String errorMessage;
        private final long timestamp;
        
        public NodeConfigurationUpdateResult(String nodeId, boolean success, String errorMessage) {
            this.nodeId = nodeId;
            this.success = success;
            this.errorMessage = errorMessage;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getNodeId() { return nodeId; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Cluster node
     */
    public static class ClusterNode {
        private final String nodeId;
        private final String host;
        private final int port;
        private final NodeStatus status;
        private final Map<String, Object> metadata;
        private final long lastHeartbeat;
        
        public ClusterNode(String nodeId, String host, int port, NodeStatus status, Map<String, Object> metadata) {
            this.nodeId = nodeId;
            this.host = host;
            this.port = port;
            this.status = status;
            this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
            this.lastHeartbeat = System.currentTimeMillis();
        }
        
        public String getId() { return nodeId; }
        public String getNodeId() { return nodeId; }
        public String getHost() { return host; }
        public String getAddress() { return host + ":" + port; }
        public int getPort() { return port; }
        public NodeStatus getStatus() { return status; }
        public Map<String, Object> getMetadata() { return new HashMap<>(metadata); }
        public Map<String, Object> getCapabilities() { return new HashMap<>(metadata); }
        public long getLastHeartbeat() { return lastHeartbeat; }
        
        // Additional methods needed by ClusterManagementSystem
        public NodeMetrics getMetrics() {
            // Create mock metrics for now
            return new NodeMetrics(nodeId, 0.5, 0.3, 10.0, 100.0, 50);
        }
        
        public NodeResourceLimits getResourceLimitsObject() {
            // Create mock resource limits for now
            return new NodeResourceLimits(1000.0, 500.0, 50);
        }
        
        public boolean isHealthy() {
            return status == NodeStatus.ACTIVE;
        }
        
        public void updateConfiguration(ClusterConfiguration config) {
            // Mock implementation - in real scenario this would update node configuration
            // For now, just log the update
            System.out.println("Node " + nodeId + " configuration updated with target size: " + config.getTargetSize());
        }
        
        public boolean cleanup() {
            // Mock implementation - in real scenario this would cleanup node resources
            System.out.println("Node " + nodeId + " cleanup completed");
            return true;
        }
    }
    
    /**
     * Maintenance operation result
     */
    public static class MaintenanceOperationResult {
        private final String operationId;
        private final boolean success;
        private final String errorMessage;
        private final long timestamp;
        
        public MaintenanceOperationResult(String operationId, boolean success, String errorMessage) {
            this.operationId = operationId;
            this.success = success;
            this.errorMessage = errorMessage;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getOperationId() { return operationId; }
        public boolean isSuccess() { return success; }
        public String getErrorMessage() { return errorMessage; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Cluster maintenance result
     */
    public static class ClusterMaintenanceResult {
        private final String requestId;
        private final boolean success;
        private final int nodesProcessed;
        private final String errorMessage;
        private final long timestamp;
        
        public ClusterMaintenanceResult(String requestId, boolean success, int nodesProcessed, String errorMessage) {
            this.requestId = requestId;
            this.success = success;
            this.nodesProcessed = nodesProcessed;
            this.errorMessage = errorMessage;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getRequestId() { return requestId; }
        public boolean isSuccess() { return success; }
        public int getNodesProcessed() { return nodesProcessed; }
        public String getErrorMessage() { return errorMessage; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Configuration update result
     */
    public static class ConfigurationUpdateResult {
        private final String requestId;
        private final boolean success;
        private final ClusterConfiguration previousConfig;
        private final ClusterConfiguration newConfig;
        private final List<String> changes;
        private final double averageResponseTime;
        private final String errorMessage;
        private final long timestamp;
        
        public ConfigurationUpdateResult(String requestId, boolean success, ClusterConfiguration previousConfig, 
                                       ClusterConfiguration newConfig, List<String> changes, double averageResponseTime, String errorMessage, long timestamp) {
            this.requestId = requestId;
            this.success = success;
            this.previousConfig = previousConfig;
            this.newConfig = newConfig;
            this.changes = changes != null ? new ArrayList<>(changes) : new ArrayList<>();
            this.averageResponseTime = averageResponseTime;
            this.errorMessage = errorMessage;
            this.timestamp = timestamp;
        }
        
        public String getRequestId() { return requestId; }
        public boolean isSuccess() { return success; }
        public ClusterConfiguration getPreviousConfig() { return previousConfig; }
        public ClusterConfiguration getNewConfig() { return newConfig; }
        public List<String> getChanges() { return new ArrayList<>(changes); }
        public double getAverageResponseTime() { return averageResponseTime; }
        public String getErrorMessage() { return errorMessage; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Node resource limits
     */
    public static class NodeResourceLimits {
        private final double maxLoad;
        private final double maxMemory;
        private final int maxConnections;
        
        public NodeResourceLimits(double maxLoad, double maxMemory, int maxConnections) {
            this.maxLoad = maxLoad;
            this.maxMemory = maxMemory;
            this.maxConnections = maxConnections;
        }
        
        public double getMaxLoad() { return maxLoad; }
        public double getMaxMemory() { return maxMemory; }
        public int getMaxConnections() { return maxConnections; }
    }
    
    /**
     * Rebalancing operation
     */
    public static class RebalancingOperation {
        private final String operationId;
        private final OperationType type;
        private final String sourceNodeId;
        private final String targetNodeId;
        private final int eventsToMove;
        private final long timestamp;
        
        public RebalancingOperation(String operationId, OperationType type, String sourceNodeId, String targetNodeId, int eventsToMove) {
            this.operationId = operationId;
            this.type = type;
            this.sourceNodeId = sourceNodeId;
            this.targetNodeId = targetNodeId;
            this.eventsToMove = eventsToMove;
            this.timestamp = System.currentTimeMillis();
        }
        
        public String getOperationId() { return operationId; }
        public OperationType getType() { return type; }
        public String getSourceNodeId() { return sourceNodeId; }
        public String getTargetNodeId() { return targetNodeId; }
        public int getEventsToMove() { return eventsToMove; }
        public long getTimestamp() { return timestamp; }
        
        // Additional methods needed by ClusterManagementSystem
        public String getNodeId() { return sourceNodeId; }
        public int getAmount() { return eventsToMove; }
        public List<String> getPatternIds() {
            // Mock pattern IDs for now
            List<String> patternIds = new ArrayList<>();
            for (int i = 0; i < eventsToMove; i++) {
                patternIds.add("pattern-" + (System.currentTimeMillis() + i));
            }
            return patternIds;
        }
        
        public enum OperationType {
            LOAD, OFFLOAD
        }
    }
}
