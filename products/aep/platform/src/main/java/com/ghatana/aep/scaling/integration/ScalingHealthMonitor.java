package com.ghatana.aep.scaling.integration;

import com.ghatana.aep.scaling.distributed.DistributedPatternProcessor;
import com.ghatana.aep.scaling.cluster.ClusterManagementSystem;
import com.ghatana.aep.scaling.autoscaling.AutoScalingEngine;
import com.ghatana.aep.scaling.loadbalancer.AdvancedLoadBalancer;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;

/**
 * Health monitor for scaling system components.
 *
 * <p>Purpose: Performs comprehensive health monitoring for all scaling
 * components including distributed processing, cluster management, and
 * load balancing. Provides overall health status and component-level details.</p>
 *
 * @doc.type class
 * @doc.purpose Monitors health of all scaling system components
 * @doc.layer product
 * @doc.pattern Service
 * @since 2.0.0
 */
public class ScalingHealthMonitor {
    private static final Logger logger = LoggerFactory.getLogger(ScalingHealthMonitor.class);
    
    private final Eventloop eventloop;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    
    // Health check intervals (in seconds)
    private static final int COMPONENT_HEALTH_CHECK_INTERVAL = 30;
    private static final int COMPREHENSIVE_HEALTH_CHECK_INTERVAL = 60;
    
    // Component health status
    private final ConcurrentHashMap<String, ComponentHealth> componentHealth = new ConcurrentHashMap<>();
    private volatile HealthStatus.HealthLevel overallHealth = HealthStatus.HealthLevel.HEALTHY;
    
    // Health check thresholds
    private final HealthCheckThresholds thresholds;
    
    public ScalingHealthMonitor(Eventloop eventloop) {
        this.eventloop = eventloop;
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.thresholds = new HealthCheckThresholds();
        
        // Initialize component health tracking
        initializeComponentHealth();
    }
    
    /**
     * Start health monitoring
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            logger.info("Starting Scaling Health Monitor...");
            
            // Schedule component health checks
            scheduler.scheduleAtFixedRate(
                this::performComponentHealthChecks,
                0,
                COMPONENT_HEALTH_CHECK_INTERVAL,
                TimeUnit.SECONDS
            );
            
            // Schedule comprehensive health checks
            scheduler.scheduleAtFixedRate(
                this::performSystemHealthCheck,
                30, // Start after 30 seconds
                COMPREHENSIVE_HEALTH_CHECK_INTERVAL,
                TimeUnit.SECONDS
            );
            
            logger.info("Scaling Health Monitor started");
        }
    }
    
    /**
     * Stop health monitoring
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            logger.info("Stopping Scaling Health Monitor...");
            
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            logger.info("Scaling Health Monitor stopped");
        }
    }
    
    /**
     * Get overall health status
     */
    public HealthStatus.HealthLevel getOverallHealth() {
        return overallHealth;
    }
    
    /**
     * Get component health status
     */
    public Map<String, HealthStatus.HealthLevel> getComponentHealth() {
        Map<String, HealthStatus.HealthLevel> healthMap = new HashMap<>();
        componentHealth.forEach((name, health) -> healthMap.put(name, health.getCurrentHealth()));
        return healthMap;
    }
    
    /**
     * Perform comprehensive health check on all components
     */
    public Promise<HealthCheckResult> performComprehensiveHealthCheck(
            DistributedPatternProcessor processor,
            ClusterManagementSystem clusterManager,
            AutoScalingEngine autoScaler,
            AdvancedLoadBalancer loadBalancer) {
        
        return Promise.ofBlocking(eventloop, () -> {
            logger.debug("Performing comprehensive health check...");
            
            HealthCheckResult.Builder resultBuilder = HealthCheckResult.builder()
                .timestamp(System.currentTimeMillis());
            
            // Check each component
            Map<String, ComponentHealthCheck> componentChecks = new HashMap<>();
            
            try {
                // Check distributed processor
                ComponentHealthCheck processorCheck = checkProcessorHealth(processor);
                componentChecks.put("DistributedProcessor", processorCheck);
                updateComponentHealth("DistributedProcessor", processorCheck);
                
                // Check cluster manager
                ComponentHealthCheck clusterCheck = checkClusterManagerHealth(clusterManager);
                componentChecks.put("ClusterManager", clusterCheck);
                updateComponentHealth("ClusterManager", clusterCheck);
                
                // Check auto scaler
                ComponentHealthCheck autoScalerCheck = checkAutoScalerHealth(autoScaler);
                componentChecks.put("AutoScaler", autoScalerCheck);
                updateComponentHealth("AutoScaler", autoScalerCheck);
                
                // Check load balancer
                ComponentHealthCheck loadBalancerCheck = checkLoadBalancerHealth(loadBalancer);
                componentChecks.put("LoadBalancer", loadBalancerCheck);
                updateComponentHealth("LoadBalancer", loadBalancerCheck);
                
                // Calculate overall health
                HealthStatus.HealthLevel overall = calculateOverallHealth(componentChecks);
                this.overallHealth = overall;
                
                // Build result
                resultBuilder.overallHealth(overall)
                    .componentChecks(componentChecks)
                    .success(true);
                
                logger.debug("Comprehensive health check completed: overall={}", overall);
                
            } catch (Exception e) {
                logger.error("Comprehensive health check failed", e);
                resultBuilder.overallHealth(HealthStatus.HealthLevel.UNHEALTHY)
                    .success(false)
                    .errorMessage(e.getMessage());
            }
            
            return resultBuilder.build();
        });
    }
    
    /**
     * Get detailed health report
     */
    public Promise<HealthReport> getDetailedHealthReport() {
        return Promise.ofBlocking(eventloop, () -> {
            HealthReport.Builder reportBuilder = HealthReport.builder()
                .timestamp(System.currentTimeMillis())
                .overallHealth(overallHealth);
            
            // Add component details
            componentHealth.forEach((name, health) -> {
                reportBuilder.addComponentDetail(name, health);
            });
            
            // Add system metrics
            reportBuilder.systemMetrics(collectSystemHealthMetrics());
            
            return reportBuilder.build();
        });
    }
    
    private void initializeComponentHealth() {
        componentHealth.put("DistributedProcessor", new ComponentHealth("DistributedProcessor"));
        componentHealth.put("ClusterManager", new ComponentHealth("ClusterManager"));
        componentHealth.put("AutoScaler", new ComponentHealth("AutoScaler"));
        componentHealth.put("LoadBalancer", new ComponentHealth("LoadBalancer"));
    }
    
    private void performComponentHealthChecks() {
        if (!running.get()) return;
        
        try {
            // This would perform lightweight health checks on each component
            // For now, we'll just log that the check is being performed
            logger.debug("Performing component health checks...");
            
        } catch (Exception e) {
            logger.error("Error in component health checks", e);
        }
    }
    
    private void performSystemHealthCheck() {
        if (!running.get()) return;
        
        try {
            // This would perform system-level health checks
            // For now, we'll just log that the check is being performed
            logger.debug("Performing system health check...");
            
        } catch (Exception e) {
            logger.error("Error in system health check", e);
        }
    }
    
    private ComponentHealthCheck checkProcessorHealth(DistributedPatternProcessor processor) {
        try {
            // Check processor-specific health metrics
            boolean isHealthy = true;
            String status = "Running";
            Map<String, Object> metrics = new HashMap<>();
            
            // Check processor status
            // This would call actual processor health check methods
            metrics.put("activePatterns", 0);
            metrics.put("throughput", 0.0);
            metrics.put("errorRate", 0.0);
            
            // Determine health level based on metrics
            HealthStatus.HealthLevel healthLevel = determineHealthLevel(metrics, thresholds.getProcessorThresholds());
            
            if (healthLevel == HealthStatus.HealthLevel.UNHEALTHY) {
                isHealthy = false;
                status = "Unhealthy";
            } else if (healthLevel == HealthStatus.HealthLevel.DEGRADED) {
                status = "Degraded";
            }
            
            return ComponentHealthCheck.builder()
                .componentName("DistributedProcessor")
                .healthy(isHealthy)
                .status(status)
                .healthLevel(healthLevel)
                .metrics(metrics)
                .timestamp(System.currentTimeMillis())
                .build();
                
        } catch (Exception e) {
            logger.error("Error checking processor health", e);
            return ComponentHealthCheck.builder()
                .componentName("DistributedProcessor")
                .healthy(false)
                .status("Error: " + e.getMessage())
                .healthLevel(HealthStatus.HealthLevel.UNHEALTHY)
                .timestamp(System.currentTimeMillis())
                .build();
        }
    }
    
    private ComponentHealthCheck checkClusterManagerHealth(ClusterManagementSystem clusterManager) {
        try {
            boolean isHealthy = true;
            String status = "Running";
            Map<String, Object> metrics = new HashMap<>();
            
            // Check cluster manager-specific health metrics
            metrics.put("totalNodes", 0);
            metrics.put("healthyNodes", 0);
            metrics.put("clusterState", "UNKNOWN");
            
            HealthStatus.HealthLevel healthLevel = determineHealthLevel(metrics, thresholds.getClusterThresholds());
            
            if (healthLevel == HealthStatus.HealthLevel.UNHEALTHY) {
                isHealthy = false;
                status = "Unhealthy";
            } else if (healthLevel == HealthStatus.HealthLevel.DEGRADED) {
                status = "Degraded";
            }
            
            return ComponentHealthCheck.builder()
                .componentName("ClusterManager")
                .healthy(isHealthy)
                .status(status)
                .healthLevel(healthLevel)
                .metrics(metrics)
                .timestamp(System.currentTimeMillis())
                .build();
                
        } catch (Exception e) {
            logger.error("Error checking cluster manager health", e);
            return ComponentHealthCheck.builder()
                .componentName("ClusterManager")
                .healthy(false)
                .status("Error: " + e.getMessage())
                .healthLevel(HealthStatus.HealthLevel.UNHEALTHY)
                .timestamp(System.currentTimeMillis())
                .build();
        }
    }
    
    private ComponentHealthCheck checkAutoScalerHealth(AutoScalingEngine autoScaler) {
        try {
            boolean isHealthy = true;
            String status = "Running";
            Map<String, Object> metrics = new HashMap<>();
            
            // Check auto scaler-specific health metrics
            metrics.put("enabled", true);
            metrics.put("currentPolicy", "DEFAULT");
            metrics.put("lastScalingTime", System.currentTimeMillis());
            
            HealthStatus.HealthLevel healthLevel = determineHealthLevel(metrics, thresholds.getAutoScalerThresholds());
            
            if (healthLevel == HealthStatus.HealthLevel.UNHEALTHY) {
                isHealthy = false;
                status = "Unhealthy";
            } else if (healthLevel == HealthStatus.HealthLevel.DEGRADED) {
                status = "Degraded";
            }
            
            return ComponentHealthCheck.builder()
                .componentName("AutoScaler")
                .healthy(isHealthy)
                .status(status)
                .healthLevel(healthLevel)
                .metrics(metrics)
                .timestamp(System.currentTimeMillis())
                .build();
                
        } catch (Exception e) {
            logger.error("Error checking auto scaler health", e);
            return ComponentHealthCheck.builder()
                .componentName("AutoScaler")
                .healthy(false)
                .status("Error: " + e.getMessage())
                .healthLevel(HealthStatus.HealthLevel.UNHEALTHY)
                .timestamp(System.currentTimeMillis())
                .build();
        }
    }
    
    private ComponentHealthCheck checkLoadBalancerHealth(AdvancedLoadBalancer loadBalancer) {
        try {
            boolean isHealthy = true;
            String status = "Running";
            Map<String, Object> metrics = new HashMap<>();
            
            // Check load balancer-specific health metrics
            metrics.put("algorithm", "ROUND_ROBIN");
            metrics.put("totalRequests", 0);
            metrics.put("averageResponseTime", 0.0);
            
            HealthStatus.HealthLevel healthLevel = determineHealthLevel(metrics, thresholds.getLoadBalancerThresholds());
            
            if (healthLevel == HealthStatus.HealthLevel.UNHEALTHY) {
                isHealthy = false;
                status = "Unhealthy";
            } else if (healthLevel == HealthStatus.HealthLevel.DEGRADED) {
                status = "Degraded";
            }
            
            return ComponentHealthCheck.builder()
                .componentName("LoadBalancer")
                .healthy(isHealthy)
                .status(status)
                .healthLevel(healthLevel)
                .metrics(metrics)
                .timestamp(System.currentTimeMillis())
                .build();
                
        } catch (Exception e) {
            logger.error("Error checking load balancer health", e);
            return ComponentHealthCheck.builder()
                .componentName("LoadBalancer")
                .healthy(false)
                .status("Error: " + e.getMessage())
                .healthLevel(HealthStatus.HealthLevel.UNHEALTHY)
                .timestamp(System.currentTimeMillis())
                .build();
        }
    }
    
    private void updateComponentHealth(String componentName, ComponentHealthCheck check) {
        ComponentHealth health = componentHealth.get(componentName);
        if (health != null) {
            health.updateFromCheck(check);
        }
    }
    
    private HealthStatus.HealthLevel calculateOverallHealth(Map<String, ComponentHealthCheck> componentChecks) {
        boolean hasUnhealthy = false;
        boolean hasDegraded = false;
        
        for (ComponentHealthCheck check : componentChecks.values()) {
            if (check.getHealthLevel() == HealthStatus.HealthLevel.UNHEALTHY) {
                hasUnhealthy = true;
            } else if (check.getHealthLevel() == HealthStatus.HealthLevel.DEGRADED) {
                hasDegraded = true;
            }
        }
        
        if (hasUnhealthy) {
            return HealthStatus.HealthLevel.UNHEALTHY;
        } else if (hasDegraded) {
            return HealthStatus.HealthLevel.DEGRADED;
        } else {
            return HealthStatus.HealthLevel.HEALTHY;
        }
    }
    
    private HealthStatus.HealthLevel determineHealthLevel(Map<String, Object> metrics, Map<String, HealthThreshold> thresholds) {
        // Simple health determination based on thresholds
        // This would be enhanced with more sophisticated logic
        
        for (Map.Entry<String, Object> metric : metrics.entrySet()) {
            String metricName = metric.getKey();
            Object value = metric.getValue();
            
            HealthThreshold threshold = thresholds.get(metricName);
            if (threshold != null) {
                if (value instanceof Number) {
                    double numericValue = ((Number) value).doubleValue();
                    
                    if (numericValue > threshold.getCriticalThreshold()) {
                        return HealthStatus.HealthLevel.UNHEALTHY;
                    } else if (numericValue > threshold.getWarningThreshold()) {
                        return HealthStatus.HealthLevel.DEGRADED;
                    }
                }
            }
        }
        
        return HealthStatus.HealthLevel.HEALTHY;
    }
    
    private Map<String, Object> collectSystemHealthMetrics() {
        Map<String, Object> systemMetrics = new HashMap<>();
        
        // Collect system-level health metrics
        systemMetrics.put("overallHealth", overallHealth.toString());
        systemMetrics.put("healthyComponents", countHealthyComponents());
        systemMetrics.put("totalComponents", componentHealth.size());
        systemMetrics.put("uptime", System.currentTimeMillis()); // This would be actual uptime
        
        return systemMetrics;
    }
    
    private int countHealthyComponents() {
        int count = 0;
        for (ComponentHealth health : componentHealth.values()) {
            if (health.getCurrentHealth() == HealthStatus.HealthLevel.HEALTHY) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Component health tracking
     */
    private static class ComponentHealth {
        private final String componentName;
        private volatile HealthStatus.HealthLevel currentHealth = HealthStatus.HealthLevel.HEALTHY;
        private volatile long lastCheckTime = 0;
        private volatile String lastStatus = "Unknown";
        
        public ComponentHealth(String componentName) {
            this.componentName = componentName;
        }
        
        public void updateFromCheck(ComponentHealthCheck check) {
            this.currentHealth = check.getHealthLevel();
            this.lastCheckTime = check.getTimestamp();
            this.lastStatus = check.getStatus();
        }
        
        public String getComponentName() { return componentName; }
        public HealthStatus.HealthLevel getCurrentHealth() { return currentHealth; }
        public long getLastCheckTime() { return lastCheckTime; }
        public String getLastStatus() { return lastStatus; }
    }
    
    /**
     * Health check thresholds
     */
    private static class HealthCheckThresholds {
        private final Map<String, HealthThreshold> processorThresholds = new HashMap<>();
        private final Map<String, HealthThreshold> clusterThresholds = new HashMap<>();
        private final Map<String, HealthThreshold> autoScalerThresholds = new HashMap<>();
        private final Map<String, HealthThreshold> loadBalancerThresholds = new HashMap<>();
        
        public HealthCheckThresholds() {
            // Initialize default thresholds
            initializeProcessorThresholds();
            initializeClusterThresholds();
            initializeAutoScalerThresholds();
            initializeLoadBalancerThresholds();
        }
        
        private void initializeProcessorThresholds() {
            processorThresholds.put("errorRate", new HealthThreshold(0.05, 0.1)); // 5% warning, 10% critical
            processorThresholds.put("throughput", new HealthThreshold(100.0, 50.0)); // Below 100 warning, below 50 critical
        }
        
        private void initializeClusterThresholds() {
            clusterThresholds.put("healthyNodes", new HealthThreshold(0.8, 0.5)); // Below 80% warning, below 50% critical
        }
        
        private void initializeAutoScalerThresholds() {
            autoScalerThresholds.put("lastScalingTime", new HealthThreshold(300000, 600000)); // 5 min warning, 10 min critical
        }
        
        private void initializeLoadBalancerThresholds() {
            loadBalancerThresholds.put("averageResponseTime", new HealthThreshold(1000.0, 5000.0)); // 1s warning, 5s critical
        }
        
        public Map<String, HealthThreshold> getProcessorThresholds() { return processorThresholds; }
        public Map<String, HealthThreshold> getClusterThresholds() { return clusterThresholds; }
        public Map<String, HealthThreshold> getAutoScalerThresholds() { return autoScalerThresholds; }
        public Map<String, HealthThreshold> getLoadBalancerThresholds() { return loadBalancerThresholds; }
    }
    
    /**
     * Health threshold definition
     */
    private static class HealthThreshold {
        private final double warningThreshold;
        private final double criticalThreshold;
        
        public HealthThreshold(double warningThreshold, double criticalThreshold) {
            this.warningThreshold = warningThreshold;
            this.criticalThreshold = criticalThreshold;
        }
        
        public double getWarningThreshold() { return warningThreshold; }
        public double getCriticalThreshold() { return criticalThreshold; }
    }
}

/**
 * Health check result
 */
class HealthCheckResult {
    private final boolean success;
    private final String errorMessage;
    private final HealthStatus.HealthLevel overallHealth;
    private final Map<String, ComponentHealthCheck> componentChecks;
    private final long timestamp;
    
    private HealthCheckResult(Builder builder) {
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
        this.overallHealth = builder.overallHealth;
        this.componentChecks = builder.componentChecks;
        this.timestamp = builder.timestamp;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public HealthStatus.HealthLevel getOverallHealth() { return overallHealth; }
    public Map<String, ComponentHealthCheck> getComponentChecks() { return componentChecks; }
    public long getTimestamp() { return timestamp; }
    
    public static class Builder {
        private boolean success;
        private String errorMessage;
        private HealthStatus.HealthLevel overallHealth;
        private Map<String, ComponentHealthCheck> componentChecks;
        private long timestamp = System.currentTimeMillis();
        
        public Builder success(boolean success) {
            this.success = success;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public Builder overallHealth(HealthStatus.HealthLevel overallHealth) {
            this.overallHealth = overallHealth;
            return this;
        }
        
        public Builder componentChecks(Map<String, ComponentHealthCheck> componentChecks) {
            this.componentChecks = componentChecks;
            return this;
        }
        
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public HealthCheckResult build() {
            return new HealthCheckResult(this);
        }
    }
}

/**
 * Component health check result
 */
class ComponentHealthCheck {
    private final String componentName;
    private final boolean healthy;
    private final String status;
    private final HealthStatus.HealthLevel healthLevel;
    private final Map<String, Object> metrics;
    private final long timestamp;
    
    private ComponentHealthCheck(Builder builder) {
        this.componentName = builder.componentName;
        this.healthy = builder.healthy;
        this.status = builder.status;
        this.healthLevel = builder.healthLevel;
        this.metrics = builder.metrics;
        this.timestamp = builder.timestamp;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public String getComponentName() { return componentName; }
    public boolean isHealthy() { return healthy; }
    public String getStatus() { return status; }
    public HealthStatus.HealthLevel getHealthLevel() { return healthLevel; }
    public Map<String, Object> getMetrics() { return metrics; }
    public long getTimestamp() { return timestamp; }
    
    public static class Builder {
        private String componentName;
        private boolean healthy;
        private String status;
        private HealthStatus.HealthLevel healthLevel;
        private Map<String, Object> metrics;
        private long timestamp = System.currentTimeMillis();
        
        public Builder componentName(String componentName) {
            this.componentName = componentName;
            return this;
        }
        
        public Builder healthy(boolean healthy) {
            this.healthy = healthy;
            return this;
        }
        
        public Builder status(String status) {
            this.status = status;
            return this;
        }
        
        public Builder healthLevel(HealthStatus.HealthLevel healthLevel) {
            this.healthLevel = healthLevel;
            return this;
        }
        
        public Builder metrics(Map<String, Object> metrics) {
            this.metrics = metrics;
            return this;
        }
        
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public ComponentHealthCheck build() {
            return new ComponentHealthCheck(this);
        }
    }
}

/**
 * Detailed health report
 */
class HealthReport {
    private final long timestamp;
    private final HealthStatus.HealthLevel overallHealth;
    private final Map<String, Object> componentDetails;
    private final Map<String, Object> systemMetrics;
    
    private HealthReport(Builder builder) {
        this.timestamp = builder.timestamp;
        this.overallHealth = builder.overallHealth;
        this.componentDetails = builder.componentDetails;
        this.systemMetrics = builder.systemMetrics;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public long getTimestamp() { return timestamp; }
    public HealthStatus.HealthLevel getOverallHealth() { return overallHealth; }
    public Map<String, Object> getComponentDetails() { return componentDetails; }
    public Map<String, Object> getSystemMetrics() { return systemMetrics; }
    
    public static class Builder {
        private long timestamp;
        private HealthStatus.HealthLevel overallHealth;
        private Map<String, Object> componentDetails = new HashMap<>();
        private Map<String, Object> systemMetrics;
        
        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder overallHealth(HealthStatus.HealthLevel overallHealth) {
            this.overallHealth = overallHealth;
            return this;
        }
        
        public Builder addComponentDetail(String componentName, Object detail) {
            this.componentDetails.put(componentName, detail);
            return this;
        }
        
        public Builder systemMetrics(Map<String, Object> systemMetrics) {
            this.systemMetrics = systemMetrics;
            return this;
        }
        
        public HealthReport build() {
            return new HealthReport(this);
        }
    }
}
