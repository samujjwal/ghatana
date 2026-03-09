package com.ghatana.aep.scaling.integration;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Scaling status and model classes for the scaling integration layer.
 *
 * <p>Purpose: Contains immutable value objects representing scaling system
 * status, metrics, health information, and component states. Used for
 * reporting and monitoring scaling operations.</p>
 *
 * @doc.type class
 * @doc.purpose Container for scaling status model classes
 * @doc.layer product
 * @doc.pattern ValueObject
 * @since 2.0.0
 */
class ScalingStatus {
    private final ScalingIntegrationService.ScalingState currentState;
    private final boolean initialized;
    private final boolean running;
    private final long timestamp;
    private final ClusterStatus clusterStatus;
    private final AutoScalingStatus autoScalingStatus;
    private final LoadBalancerStatus loadBalancerStatus;
    private final ProcessorStatus processorStatus;
    private final ScalingMetrics metrics;
    private final HealthStatus healthStatus;
    
    private ScalingStatus(Builder builder) {
        this.currentState = builder.currentState;
        this.initialized = builder.initialized;
        this.running = builder.running;
        this.timestamp = builder.timestamp;
        this.clusterStatus = builder.clusterStatus;
        this.autoScalingStatus = builder.autoScalingStatus;
        this.loadBalancerStatus = builder.loadBalancerStatus;
        this.processorStatus = builder.processorStatus;
        this.metrics = builder.metrics;
        this.healthStatus = builder.healthStatus;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public ScalingIntegrationService.ScalingState getCurrentState() { return currentState; }
    public boolean isInitialized() { return initialized; }
    public boolean isRunning() { return running; }
    public long getTimestamp() { return timestamp; }
    public ClusterStatus getClusterStatus() { return clusterStatus; }
    public AutoScalingStatus getAutoScalingStatus() { return autoScalingStatus; }
    public LoadBalancerStatus getLoadBalancerStatus() { return loadBalancerStatus; }
    public ProcessorStatus getProcessorStatus() { return processorStatus; }
    public ScalingMetrics getMetrics() { return metrics; }
    public HealthStatus getHealthStatus() { return healthStatus; }
    
    @Override
    public String toString() {
        return "ScalingStatus{" +
                "currentState=" + currentState +
                ", initialized=" + initialized +
                ", running=" + running +
                ", timestamp=" + timestamp +
                '}';
    }
    
    public static class Builder {
        private ScalingIntegrationService.ScalingState currentState;
        private boolean initialized;
        private boolean running;
        private long timestamp;
        private ClusterStatus clusterStatus;
        private AutoScalingStatus autoScalingStatus;
        private LoadBalancerStatus loadBalancerStatus;
        private ProcessorStatus processorStatus;
        private ScalingMetrics metrics;
        private HealthStatus healthStatus;
        
        public Builder currentState(ScalingIntegrationService.ScalingState currentState) {
            this.currentState = currentState;
            return this;
        }
        
        public Builder initialized(boolean initialized) {
            this.initialized = initialized;
            return this;
        }
        
        public Builder running(boolean running) {
            this.running = running;
            return this;
        }
        
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder clusterStatus(ClusterStatus clusterStatus) {
            this.clusterStatus = clusterStatus;
            return this;
        }
        
        public Builder autoScalingStatus(AutoScalingStatus autoScalingStatus) {
            this.autoScalingStatus = autoScalingStatus;
            return this;
        }
        
        public Builder loadBalancerStatus(LoadBalancerStatus loadBalancerStatus) {
            this.loadBalancerStatus = loadBalancerStatus;
            return this;
        }
        
        public Builder processorStatus(ProcessorStatus processorStatus) {
            this.processorStatus = processorStatus;
            return this;
        }
        
        public Builder metrics(ScalingMetrics metrics) {
            this.metrics = metrics;
            return this;
        }
        
        public Builder healthStatus(HealthStatus healthStatus) {
            this.healthStatus = healthStatus;
            return this;
        }
        
        public ScalingStatus build() {
            return new ScalingStatus(this);
        }
    }
}

/**
 * Scaling request for cluster operations
 */
class ScalingRequest {
    enum ScalingType {
        AUTO_SCALE,
        MANUAL_SCALE,
        PREDICTIVE_SCALE
    }
    
    private final ScalingType type;
    private final int targetSize;
    private final Map<String, Object> parameters;
    private final long timestamp;
    private final String requestId;
    
    public ScalingRequest(ScalingType type, int targetSize, Map<String, Object> parameters) {
        this.type = type;
        this.targetSize = targetSize;
        this.parameters = parameters != null ? new HashMap<>(parameters) : new HashMap<>();
        this.timestamp = System.currentTimeMillis();
        this.requestId = "req-" + timestamp + "-" + hashCode();
    }
    
    public ScalingType getType() { return type; }
    public int getTargetSize() { return targetSize; }
    public Map<String, Object> getParameters() { return new HashMap<>(parameters); }
    public long getTimestamp() { return timestamp; }
    public String getRequestId() { return requestId; }
    
    @Override
    public String toString() {
        return "ScalingRequest{" +
                "type=" + type +
                ", targetSize=" + targetSize +
                ", requestId='" + requestId + '\'' +
                '}';
    }
}

/**
 * Result of scaling operations
 */
class ScalingResult {
    private final boolean success;
    private final String errorMessage;
    private final int previousSize;
    private final int currentSize;
    private final long timestamp;
    private final Map<String, Object> details;
    
    private ScalingResult(Builder builder) {
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
        this.previousSize = builder.previousSize;
        this.currentSize = builder.currentSize;
        this.timestamp = builder.timestamp;
        this.details = builder.details != null ? new HashMap<>(builder.details) : new HashMap<>();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public int getPreviousSize() { return previousSize; }
    public int getCurrentSize() { return currentSize; }
    public long getTimestamp() { return timestamp; }
    public Map<String, Object> getDetails() { return new HashMap<>(details); }
    
    @Override
    public String toString() {
        return "ScalingResult{" +
                "success=" + success +
                ", previousSize=" + previousSize +
                ", currentSize=" + currentSize +
                '}';
    }
    
    public static class Builder {
        private boolean success;
        private String errorMessage;
        private int previousSize;
        private int currentSize;
        private long timestamp = System.currentTimeMillis();
        private Map<String, Object> details;
        
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public Builder message(String message) {
            this.errorMessage = message;
            return this;
        }
        
        public Builder previousSize(int previousSize) {
            this.previousSize = previousSize;
            return this;
        }
        
        public Builder currentSize(int currentSize) {
            this.currentSize = currentSize;
            return this;
        }
        
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }
        
        public ScalingResult build() {
            return new ScalingResult(this);
        }
    }
}

/**
 * Result of rebalancing operations
 */
class RebalancingResult {
    private final boolean success;
    private final boolean rebalancingNeeded;
    private final String message;
    private final long timestamp;
    private final Map<String, Object> details;
    
    private RebalancingResult(Builder builder) {
        this.success = builder.success;
        this.rebalancingNeeded = builder.rebalancingNeeded;
        this.message = builder.message;
        this.timestamp = builder.timestamp;
        this.details = builder.details != null ? new HashMap<>(builder.details) : new HashMap<>();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public boolean isSuccess() { return success; }
    public boolean isRebalancingNeeded() { return rebalancingNeeded; }
    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }
    public Map<String, Object> getDetails() { return new HashMap<>(details); }
    
    @Override
    public String toString() {
        return "RebalancingResult{" +
                "success=" + success +
                ", rebalancingNeeded=" + rebalancingNeeded +
                ", message='" + message + '\'' +
                '}';
    }
    
    public static class Builder {
        private boolean success;
        private boolean rebalancingNeeded;
        private String message;
        private long timestamp = System.currentTimeMillis();
        private Map<String, Object> details;
        
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder rebalancingNeeded(boolean rebalancingNeeded) {
            this.rebalancingNeeded = rebalancingNeeded;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.message = errorMessage;
            return this;
        }
        
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }
        
        public RebalancingResult build() {
            return new RebalancingResult(this);
        }
    }
}

/**
 * Workload analysis results
 */
class WorkloadAnalysis {
    private final Map<String, Double> nodeLoads;
    private final double averageLoad;
    private final double totalLoad;
    private final boolean rebalancingNeeded;
    private final String message;
    private final long timestamp;
    
    private WorkloadAnalysis(Builder builder) {
        this.nodeLoads = builder.nodeLoads;
        this.averageLoad = builder.averageLoad;
        this.totalLoad = builder.totalLoad;
        this.rebalancingNeeded = builder.rebalancingNeeded;
        this.message = builder.message;
        this.timestamp = builder.timestamp;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public Map<String, Double> getNodeLoads() { return new HashMap<>(nodeLoads); }
    public double getAverageLoad() { return averageLoad; }
    public double getTotalLoad() { return totalLoad; }
    public boolean isRebalancingNeeded() { return rebalancingNeeded; }
    public String getMessage() { return message; }
    public long getTimestamp() { return timestamp; }
    
    @Override
    public String toString() {
        return "WorkloadAnalysis{" +
                "averageLoad=" + averageLoad +
                ", totalLoad=" + totalLoad +
                ", rebalancingNeeded=" + rebalancingNeeded +
                '}';
    }
    
    public static class Builder {
        private Map<String, Double> nodeLoads;
        private double averageLoad;
        private double totalLoad;
        private boolean rebalancingNeeded;
        private String message;
        private long timestamp = System.currentTimeMillis();
        
        public Builder nodeLoads(Map<String, Double> nodeLoads) {
            this.nodeLoads = nodeLoads;
            return this;
        }
        
        public Builder averageLoad(double averageLoad) {
            this.averageLoad = averageLoad;
            return this;
        }
        
        public Builder totalLoad(double totalLoad) {
            this.totalLoad = totalLoad;
            return this;
        }
        
        public Builder rebalancingNeeded(boolean rebalancingNeeded) {
            this.rebalancingNeeded = rebalancingNeeded;
            return this;
        }
        
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public WorkloadAnalysis build() {
            return new WorkloadAnalysis(this);
        }
    }
}

/**
 * Cluster status information
 */
class ClusterStatus {
    private final int totalNodes;
    private final int activeNodes;
    private final int healthyNodes;
    private final String clusterState;
    private final long timestamp;
    
    public ClusterStatus(int totalNodes, int activeNodes, int healthyNodes, String clusterState) {
        this.totalNodes = totalNodes;
        this.activeNodes = activeNodes;
        this.healthyNodes = healthyNodes;
        this.clusterState = clusterState;
        this.timestamp = System.currentTimeMillis();
    }
    
    public int getTotalNodes() { return totalNodes; }
    public int getActiveNodes() { return activeNodes; }
    public int getHealthyNodes() { return healthyNodes; }
    public String getClusterState() { return clusterState; }
    public long getTimestamp() { return timestamp; }
}

/**
 * Auto-scaling status information
 */
class AutoScalingStatus {
    private final boolean enabled;
    private final String currentPolicy;
    private final int minNodes;
    private final int maxNodes;
    private final long timestamp;
    
    public AutoScalingStatus(boolean enabled, String currentPolicy, int minNodes, int maxNodes) {
        this.enabled = enabled;
        this.currentPolicy = currentPolicy;
        this.minNodes = minNodes;
        this.maxNodes = maxNodes;
        this.timestamp = System.currentTimeMillis();
    }
    
    public boolean isEnabled() { return enabled; }
    public String getCurrentPolicy() { return currentPolicy; }
    public int getMinNodes() { return minNodes; }
    public int getMaxNodes() { return maxNodes; }
    public long getTimestamp() { return timestamp; }
}

/**
 * Load balancer status information
 */
class LoadBalancerStatus {
    private final String algorithm;
    private final int totalRequests;
    private final double averageResponseTime;
    private final long timestamp;
    
    public LoadBalancerStatus(String algorithm, int totalRequests, double averageResponseTime) {
        this.algorithm = algorithm;
        this.totalRequests = totalRequests;
        this.averageResponseTime = averageResponseTime;
        this.timestamp = System.currentTimeMillis();
    }
    
    public String getAlgorithm() { return algorithm; }
    public int getTotalRequests() { return totalRequests; }
    public double getAverageResponseTime() { return averageResponseTime; }
    public long getTimestamp() { return timestamp; }
}

/**
 * Processor status information
 */
class ProcessorStatus {
    private final int activePatterns;
    private final long processedEvents;
    private final double throughput;
    private final long timestamp;
    
    public ProcessorStatus(int activePatterns, long processedEvents, double throughput) {
        this.activePatterns = activePatterns;
        this.processedEvents = processedEvents;
        this.throughput = throughput;
        this.timestamp = System.currentTimeMillis();
    }
    
    public int getActivePatterns() { return activePatterns; }
    public long getProcessedEvents() { return processedEvents; }
    public double getThroughput() { return throughput; }
    public long getTimestamp() { return timestamp; }
}

/**
 * Health status information
 */
class HealthStatus {
    enum HealthLevel {
        HEALTHY,
        DEGRADED,
        UNHEALTHY
    }
    
    private final HealthLevel overallHealth;
    private final Map<String, HealthLevel> componentHealth;
    private final long timestamp;
    
    public HealthStatus(HealthLevel overallHealth, Map<String, HealthLevel> componentHealth) {
        this.overallHealth = overallHealth;
        this.componentHealth = componentHealth != null ? new HashMap<>(componentHealth) : new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }
    
    public HealthLevel getOverallHealth() { return overallHealth; }
    public Map<String, HealthLevel> getComponentHealth() { return new HashMap<>(componentHealth); }
    public long getTimestamp() { return timestamp; }
}
