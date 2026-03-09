package com.ghatana.aep.scaling.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Collector for scaling-related metrics and statistics.
 *
 * <p>Purpose: Tracks and aggregates scaling operation metrics including
 * throughput, response times, node health, and rebalancing statistics.
 * Provides real-time metrics access and historical data for analysis.</p>
 *
 * @doc.type class
 * @doc.purpose Collects and manages scaling operation metrics
 * @doc.layer product
 * @doc.pattern Service
 * @since 2.0.0
 */
public class ScalingMetricsCollector {
    private static final Logger logger = LoggerFactory.getLogger(ScalingMetricsCollector.class);
    
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // Service metrics
    private final AtomicLong initializationTime = new AtomicLong(0);
    private final AtomicLong serviceStartTime = new AtomicLong(0);
    private final AtomicLong serviceStopTime = new AtomicLong(0);
    
    // Scaling operation metrics
    private final AtomicLong scalingRequests = new AtomicLong(0);
    private final AtomicLong successfulScalingOps = new AtomicLong(0);
    private final AtomicLong failedScalingOps = new AtomicLong(0);
    private final AtomicReference<Double> averageScalingTime = new AtomicReference<>(0.0);
    
    // Rebalancing metrics
    private final AtomicLong rebalancingOperations = new AtomicLong(0);
    private final AtomicLong successfulRebalancingOps = new AtomicLong(0);
    private final AtomicLong failedRebalancingOps = new AtomicLong(0);
    private final AtomicReference<Double> averageRebalancingTime = new AtomicReference<>(0.0);
    
    // Performance metrics
    private final AtomicReference<Double> currentThroughput = new AtomicReference<>(0.0);
    private final AtomicReference<Double> averageResponseTime = new AtomicReference<>(0.0);
    private final AtomicLong totalEventsProcessed = new AtomicLong(0);
    
    // Cluster metrics
    private final AtomicLong totalNodes = new AtomicLong(0);
    private final AtomicLong activeNodes = new AtomicLong(0);
    private final AtomicLong healthyNodes = new AtomicLong(0);
    
    // Configuration metrics
    private final AtomicLong configurationUpdates = new AtomicLong(0);
    
    // Historical data
    private final ConcurrentHashMap<String, MetricHistory> metricHistories = new ConcurrentHashMap<>();
    
    public ScalingMetricsCollector() {
        this.scheduler = Executors.newScheduledThreadPool(2);
    }
    
    /**
     * Start metrics collection
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting Scaling Metrics Collector...");
            
            // Schedule periodic metrics collection
            scheduler.scheduleAtFixedRate(
                this::collectSystemMetrics,
                0,
                30, // Collect every 30 seconds
                TimeUnit.SECONDS
            );
            
            // Schedule periodic history cleanup
            scheduler.scheduleAtFixedRate(
                this::cleanupOldHistory,
                1,
                1, // Cleanup every hour
                TimeUnit.HOURS
            );
            
            logger.info("Scaling Metrics Collector started");
        }
    }
    
    /**
     * Stop metrics collection
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping Scaling Metrics Collector...");
            
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            logger.info("Scaling Metrics Collector stopped");
        }
    }
    
    /**
     * Get current metrics
     */
    public ScalingMetrics getCurrentMetrics() {
        return ScalingMetrics.builder()
            .timestamp(System.currentTimeMillis())
            .initializationTime(initializationTime.get())
            .serviceStartTime(serviceStartTime.get())
            .serviceStopTime(serviceStopTime.get())
            .scalingRequests(scalingRequests.get())
            .successfulScalingOps(successfulScalingOps.get())
            .failedScalingOps(failedScalingOps.get())
            .averageScalingTime(averageScalingTime.get())
            .rebalancingOperations(rebalancingOperations.get())
            .successfulRebalancingOps(successfulRebalancingOps.get())
            .failedRebalancingOps(failedRebalancingOps.get())
            .averageRebalancingTime(averageRebalancingTime.get())
            .currentThroughput(currentThroughput.get())
            .averageResponseTime(averageResponseTime.get())
            .totalEventsProcessed(totalEventsProcessed.get())
            .totalNodes(totalNodes.get())
            .activeNodes(activeNodes.get())
            .healthyNodes(healthyNodes.get())
            .configurationUpdates(configurationUpdates.get())
            .build();
    }
    
    /**
     * Record initialization completion
     */
    public void recordInitializationComplete() {
        initializationTime.set(System.currentTimeMillis());
        logger.debug("Recorded initialization completion time");
    }
    
    /**
     * Record service start
     */
    public void recordServiceStart() {
        serviceStartTime.set(System.currentTimeMillis());
        logger.debug("Recorded service start time");
    }
    
    /**
     * Record service stop
     */
    public void recordServiceStop() {
        serviceStopTime.set(System.currentTimeMillis());
        logger.debug("Recorded service stop time");
    }
    
    /**
     * Record scaling request
     */
    public void recordScalingRequest(ScalingRequest request) {
        scalingRequests.incrementAndGet();
        logger.debug("Recorded scaling request: {}", request.getRequestId());
    }
    
    /**
     * Record scaling result
     */
    public void recordScalingResult(ScalingResult result) {
        if (result.isSuccess()) {
            successfulScalingOps.incrementAndGet();
        } else {
            failedScalingOps.incrementAndGet();
        }
        
        // Update average scaling time
        updateAverageTime(averageScalingTime, result.getTimestamp() - result.getTimestamp());
        
        logger.debug("Recorded scaling result: success={}", result.isSuccess());
    }
    
    /**
     * Record rebalancing start
     */
    public void recordRebalancingStart() {
        rebalancingOperations.incrementAndGet();
        logger.debug("Recorded rebalancing start");
    }
    
    /**
     * Record rebalancing result
     */
    public void recordRebalancingResult(RebalancingResult result) {
        if (result.isSuccess()) {
            successfulRebalancingOps.incrementAndGet();
        } else {
            failedRebalancingOps.incrementAndGet();
        }
        
        // Update average rebalancing time
        updateAverageTime(averageRebalancingTime, result.getTimestamp() - result.getTimestamp());
        
        logger.debug("Recorded rebalancing result: success={}", result.isSuccess());
    }
    
    /**
     * Record configuration update
     */
    public void recordConfigurationUpdate() {
        configurationUpdates.incrementAndGet();
        logger.debug("Recorded configuration update");
    }
    
    /**
     * Update cluster metrics
     */
    public void updateClusterMetrics(int total, int active, int healthy) {
        totalNodes.set(total);
        activeNodes.set(active);
        healthyNodes.set(healthy);
        logger.debug("Updated cluster metrics: total={}, active={}, healthy={}", total, active, healthy);
    }
    
    /**
     * Update performance metrics
     */
    public void updatePerformanceMetrics(double throughput, double responseTime, long eventsProcessed) {
        currentThroughput.set(throughput);
        averageResponseTime.set(responseTime);
        totalEventsProcessed.set(eventsProcessed);
        logger.debug("Updated performance metrics: throughput={}, responseTime={}, events={}", 
                    throughput, responseTime, eventsProcessed);
    }
    
    /**
     * Get metric history for a specific metric
     */
    public MetricHistory getMetricHistory(String metricName) {
        return metricHistories.get(metricName);
    }
    
    private void collectSystemMetrics() {
        try {
            long timestamp = System.currentTimeMillis();
            
            // Collect various system metrics
            double currentThroughput = calculateThroughput();
            double avgResponseTime = calculateAverageResponseTime();
            int activeNodes = getActiveNodeCount();
            
            // Update current values
            this.currentThroughput.set(currentThroughput);
            this.averageResponseTime.set(avgResponseTime);
            this.activeNodes.set(activeNodes);
            
            // Store in history
            storeMetricInHistory("throughput", timestamp, currentThroughput);
            storeMetricInHistory("responseTime", timestamp, avgResponseTime);
            storeMetricInHistory("activeNodes", timestamp, activeNodes);
            
        } catch (Exception e) {
            logger.error("Error collecting system metrics", e);
        }
    }
    
    private void cleanupOldHistory() {
        try {
            long cutoffTime = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24); // Keep 24 hours
            
            metricHistories.values().forEach(history -> {
                history.removeOldEntries(cutoffTime);
            });
            
            logger.debug("Cleaned up old metric history");
        } catch (Exception e) {
            logger.error("Error cleaning up metric history", e);
        }
    }
    
    private void updateAverageTime(AtomicReference<Double> averageTime, double newTime) {
        // Simple exponential moving average
        double alpha = 0.1; // Smoothing factor
        double current = averageTime.get();
        double updated = alpha * newTime + (1 - alpha) * current;
        averageTime.set(updated);
    }
    
    private void storeMetricInHistory(String metricName, long timestamp, double value) {
        MetricHistory history = metricHistories.computeIfAbsent(metricName, k -> new MetricHistory(metricName));
        history.addEntry(timestamp, value);
    }
    
    private double calculateThroughput() {
        // This would be implemented based on actual system metrics
        // For now, return a placeholder value
        return currentThroughput.get();
    }
    
    private double calculateAverageResponseTime() {
        // This would be implemented based on actual system metrics
        // For now, return a placeholder value
        return averageResponseTime.get();
    }
    
    private int getActiveNodeCount() {
        // This would be implemented based on actual cluster state
        // For now, return a placeholder value
        return (int) activeNodes.get();
    }
    
    /**
     * Metric history storage
     */
    public static class MetricHistory {
        private final String metricName;
        private final ConcurrentHashMap<Long, Double> entries = new ConcurrentHashMap<>();
        
        public MetricHistory(String metricName) {
            this.metricName = metricName;
        }
        
        public void addEntry(long timestamp, double value) {
            entries.put(timestamp, value);
        }
        
        public void removeOldEntries(long cutoffTime) {
            entries.entrySet().removeIf(entry -> entry.getKey() < cutoffTime);
        }
        
        public String getMetricName() { return metricName; }
        public ConcurrentHashMap<Long, Double> getEntries() { return new ConcurrentHashMap<>(entries); }
    }
}

/**
 * Scaling metrics data class
 */
class ScalingMetrics {
    private final long timestamp;
    private final long initializationTime;
    private final long serviceStartTime;
    private final long serviceStopTime;
    private final long scalingRequests;
    private final long successfulScalingOps;
    private final long failedScalingOps;
    private final double averageScalingTime;
    private final long rebalancingOperations;
    private final long successfulRebalancingOps;
    private final long failedRebalancingOps;
    private final double averageRebalancingTime;
    private final double currentThroughput;
    private final double averageResponseTime;
    private final long totalEventsProcessed;
    private final long totalNodes;
    private final long activeNodes;
    private final long healthyNodes;
    private final long configurationUpdates;
    
    private ScalingMetrics(Builder builder) {
        this.timestamp = builder.timestamp;
        this.initializationTime = builder.initializationTime;
        this.serviceStartTime = builder.serviceStartTime;
        this.serviceStopTime = builder.serviceStopTime;
        this.scalingRequests = builder.scalingRequests;
        this.successfulScalingOps = builder.successfulScalingOps;
        this.failedScalingOps = builder.failedScalingOps;
        this.averageScalingTime = builder.averageScalingTime;
        this.rebalancingOperations = builder.rebalancingOperations;
        this.successfulRebalancingOps = builder.successfulRebalancingOps;
        this.failedRebalancingOps = builder.failedRebalancingOps;
        this.averageRebalancingTime = builder.averageRebalancingTime;
        this.currentThroughput = builder.currentThroughput;
        this.averageResponseTime = builder.averageResponseTime;
        this.totalEventsProcessed = builder.totalEventsProcessed;
        this.totalNodes = builder.totalNodes;
        this.activeNodes = builder.activeNodes;
        this.healthyNodes = builder.healthyNodes;
        this.configurationUpdates = builder.configurationUpdates;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    // Getters
    public long getTimestamp() { return timestamp; }
    public long getInitializationTime() { return initializationTime; }
    public long getServiceStartTime() { return serviceStartTime; }
    public long getServiceStopTime() { return serviceStopTime; }
    public long getScalingRequests() { return scalingRequests; }
    public long getSuccessfulScalingOps() { return successfulScalingOps; }
    public long getFailedScalingOps() { return failedScalingOps; }
    public double getAverageScalingTime() { return averageScalingTime; }
    public long getRebalancingOperations() { return rebalancingOperations; }
    public long getSuccessfulRebalancingOps() { return successfulRebalancingOps; }
    public long getFailedRebalancingOps() { return failedRebalancingOps; }
    public double getAverageRebalancingTime() { return averageRebalancingTime; }
    public double getCurrentThroughput() { return currentThroughput; }
    public double getAverageResponseTime() { return averageResponseTime; }
    public long getTotalEventsProcessed() { return totalEventsProcessed; }
    public long getTotalNodes() { return totalNodes; }
    public long getActiveNodes() { return activeNodes; }
    public long getHealthyNodes() { return healthyNodes; }
    public long getConfigurationUpdates() { return configurationUpdates; }
    
    @Override
    public String toString() {
        return "ScalingMetrics{" +
                "timestamp=" + timestamp +
                ", scalingRequests=" + scalingRequests +
                ", successfulScalingOps=" + successfulScalingOps +
                ", currentThroughput=" + currentThroughput +
                '}';
    }
    
    public static class Builder {
        private long timestamp;
        private long initializationTime;
        private long serviceStartTime;
        private long serviceStopTime;
        private long scalingRequests;
        private long successfulScalingOps;
        private long failedScalingOps;
        private double averageScalingTime;
        private long rebalancingOperations;
        private long successfulRebalancingOps;
        private long failedRebalancingOps;
        private double averageRebalancingTime;
        private double currentThroughput;
        private double averageResponseTime;
        private long totalEventsProcessed;
        private long totalNodes;
        private long activeNodes;
        private long healthyNodes;
        private long configurationUpdates;
        
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder initializationTime(long initializationTime) {
            this.initializationTime = initializationTime;
            return this;
        }
        
        public Builder serviceStartTime(long serviceStartTime) {
            this.serviceStartTime = serviceStartTime;
            return this;
        }
        
        public Builder serviceStopTime(long serviceStopTime) {
            this.serviceStopTime = serviceStopTime;
            return this;
        }
        
        public Builder scalingRequests(long scalingRequests) {
            this.scalingRequests = scalingRequests;
            return this;
        }
        
        public Builder successfulScalingOps(long successfulScalingOps) {
            this.successfulScalingOps = successfulScalingOps;
            return this;
        }
        
        public Builder failedScalingOps(long failedScalingOps) {
            this.failedScalingOps = failedScalingOps;
            return this;
        }
        
        public Builder averageScalingTime(double averageScalingTime) {
            this.averageScalingTime = averageScalingTime;
            return this;
        }
        
        public Builder rebalancingOperations(long rebalancingOperations) {
            this.rebalancingOperations = rebalancingOperations;
            return this;
        }
        
        public Builder successfulRebalancingOps(long successfulRebalancingOps) {
            this.successfulRebalancingOps = successfulRebalancingOps;
            return this;
        }
        
        public Builder failedRebalancingOps(long failedRebalancingOps) {
            this.failedRebalancingOps = failedRebalancingOps;
            return this;
        }
        
        public Builder averageRebalancingTime(double averageRebalancingTime) {
            this.averageRebalancingTime = averageRebalancingTime;
            return this;
        }
        
        public Builder currentThroughput(double currentThroughput) {
            this.currentThroughput = currentThroughput;
            return this;
        }
        
        public Builder averageResponseTime(double averageResponseTime) {
            this.averageResponseTime = averageResponseTime;
            return this;
        }
        
        public Builder totalEventsProcessed(long totalEventsProcessed) {
            this.totalEventsProcessed = totalEventsProcessed;
            return this;
        }
        
        public Builder totalNodes(long totalNodes) {
            this.totalNodes = totalNodes;
            return this;
        }
        
        public Builder activeNodes(long activeNodes) {
            this.activeNodes = activeNodes;
            return this;
        }
        
        public Builder healthyNodes(long healthyNodes) {
            this.healthyNodes = healthyNodes;
            return this;
        }
        
        public Builder configurationUpdates(long configurationUpdates) {
            this.configurationUpdates = configurationUpdates;
            return this;
        }
        
        public ScalingMetrics build() {
            return new ScalingMetrics(this);
        }
    }
}
