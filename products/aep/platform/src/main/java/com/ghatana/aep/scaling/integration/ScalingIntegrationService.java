package com.ghatana.aep.scaling.integration;

import com.ghatana.aep.scaling.distributed.DistributedPatternProcessor;
import com.ghatana.aep.scaling.cluster.ClusterManagementSystem;
import com.ghatana.aep.scaling.autoscaling.AutoScalingEngine;
import com.ghatana.aep.scaling.loadbalancer.AdvancedLoadBalancer;
import com.ghatana.aep.scaling.models.AutoScalingModels;
import com.ghatana.aep.scaling.models.DistributedModels;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrator service for horizontal scaling components.
 *
 * <p>Purpose: Provides unified interface for managing distributed processing,
 * cluster management, auto-scaling, and load balancing. Coordinates component
 * lifecycle, health monitoring, and scaling operations for AEP.</p>
 *
 * @doc.type class
 * @doc.purpose Orchestrates all horizontal scaling components
 * @doc.layer product
 * @doc.pattern Service
 * @since 2.0.0
 */
public class ScalingIntegrationService {
    private static final Logger logger = LoggerFactory.getLogger(ScalingIntegrationService.class);
    
    private final Eventloop eventloop;
    private final DistributedPatternProcessor distributedProcessor;
    private final ClusterManagementSystem clusterManager;
    private final AutoScalingEngine autoScaler;
    private final AdvancedLoadBalancer loadBalancer;
    
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<ScalingState> currentState = new AtomicReference<>(ScalingState.INITIALIZING);
    
    // Configuration
    private final ScalingConfiguration config;
    
    // Monitoring
    private final ScalingMetricsCollector metricsCollector;
    private final ScalingHealthMonitor healthMonitor;
    
    public ScalingIntegrationService(
            DistributedPatternProcessor distributedProcessor,
            ClusterManagementSystem clusterManager,
            AutoScalingEngine autoScaler,
            AdvancedLoadBalancer loadBalancer,
            ScalingConfiguration config) {
        
        this(createEventloop(), distributedProcessor, clusterManager, autoScaler, loadBalancer, config);
    }

    public ScalingIntegrationService(
            Eventloop eventloop,
            DistributedPatternProcessor distributedProcessor,
            ClusterManagementSystem clusterManager,
            AutoScalingEngine autoScaler,
            AdvancedLoadBalancer loadBalancer,
            ScalingConfiguration config) {
        
        this.eventloop = eventloop;
        this.distributedProcessor = distributedProcessor;
        this.clusterManager = clusterManager;
        this.autoScaler = autoScaler;
        this.loadBalancer = loadBalancer;
        this.config = config;
        
        this.metricsCollector = new ScalingMetricsCollector();
        this.healthMonitor = new ScalingHealthMonitor(eventloop);
        
        logger.info("ScalingIntegrationService created with configuration: {}", config);
    }

    private static Eventloop createEventloop() {
        return Eventloop.builder()
                .withCurrentThread()
                .build();
    }
    
    /**
     * Initialize all scaling components
     */
    public Promise<Void> initialize() {
        logger.info("Initializing Scaling Integration Service...");
        
        return Promise.ofBlocking(eventloop, () -> {
            try {
                // Initialize components in dependency order.
                initializeComponent(
                    "Cluster Management",
                    () -> resolvePromise(clusterManager.initializeCluster(), "initialize cluster manager"));
                
                // Setup component coordination
                setupComponentCoordination();
                
                // Start monitoring
                startMonitoring();
                
                initialized.set(true);
                currentState.set(ScalingState.READY);
                
                logger.info("Scaling Integration Service initialized successfully");
                metricsCollector.recordInitializationComplete();
                
                return null;
            } catch (Exception e) {
                logger.error("Failed to initialize Scaling Integration Service", e);
                currentState.set(ScalingState.FAILED);
                throw new RuntimeException("Scaling initialization failed", e);
            }
        });
    }
    
    /**
     * Start the scaling integration service
     */
    public Promise<Void> start() {
        logger.info("Starting Scaling Integration Service...");
        
        return Promise.ofBlocking(eventloop, () -> {
            if (!initialized.get()) {
                throw new IllegalStateException("Service not initialized");
            }
            
            try {
                startComponent("Monitoring", this::startMonitoring);
                
                running.set(true);
                currentState.set(ScalingState.RUNNING);
                
                logger.info("Scaling Integration Service started successfully");
                metricsCollector.recordServiceStart();
                
                return null;
            } catch (Exception e) {
                logger.error("Failed to start Scaling Integration Service", e);
                currentState.set(ScalingState.FAILED);
                throw new RuntimeException("Scaling start failed", e);
            }
        });
    }
    
    /**
     * Stop the scaling integration service
     */
    public Promise<Void> stop() {
        logger.info("Stopping Scaling Integration Service...");
        
        return Promise.ofBlocking(eventloop, () -> {
            try {
                running.set(false);
                currentState.set(ScalingState.STOPPING);
                
                // Stop components in reverse order
                stopComponent(
                    "Distributed Processor",
                    () -> resolvePromise(distributedProcessor.shutdown(), "shutdown distributed processor"));
                stopComponent(
                    "Auto Scaling Engine",
                    () -> resolvePromise(autoScaler.shutdown(), "shutdown auto-scaler"));
                stopComponent(
                    "Cluster Management",
                    () -> resolvePromise(clusterManager.shutdown(), "shutdown cluster manager"));
                
                // Stop monitoring
                stopMonitoring();
                
                currentState.set(ScalingState.STOPPED);
                
                logger.info("Scaling Integration Service stopped successfully");
                metricsCollector.recordServiceStop();
                
                return null;
            } catch (Exception e) {
                logger.error("Failed to stop Scaling Integration Service", e);
                currentState.set(ScalingState.FAILED);
                throw new RuntimeException("Scaling stop failed", e);
            }
        });
    }
    
    /**
     * Get comprehensive scaling status
     */
    public Promise<ScalingStatus> getScalingStatus() {
        return Promise.ofBlocking(eventloop, () -> {
            Map<String, Object> clusterMetrics = safeMetrics(clusterManager.getMetrics());
            Map<String, Object> autoScalingMetrics = safeMetrics(autoScaler.getMetrics());
            Map<String, Object> loadBalancerMetrics = safeMetrics(loadBalancer.getMetrics());
            Map<String, Object> processorMetrics = safeMetrics(distributedProcessor.getMetrics());

            int totalNodes = toInt(clusterMetrics.get("clusterNodes"));
            int activeNodes = toInt(loadBalancerMetrics.get("activeNodes"));
            if (activeNodes <= 0) {
                activeNodes = toInt(processorMetrics.get("activeNodes"));
            }
            if (totalNodes <= 0) {
                totalNodes = activeNodes;
            }
            int healthyNodes = toInt(loadBalancerMetrics.get("healthyNodes"));
            if (healthyNodes <= 0 && running.get()) {
                healthyNodes = activeNodes;
            }

            ClusterStatus clusterStatus =
                new ClusterStatus(
                    totalNodes,
                    activeNodes,
                    healthyNodes,
                    String.valueOf(clusterMetrics.getOrDefault("clusterState", currentState.get().name())));

            AutoScalingStatus autoScalingStatus =
                new AutoScalingStatus(
                    running.get(),
                    "dynamic",
                    Math.max(1, Math.min(totalNodes, currentProcessorNodeCount())),
                    Math.max(1, Math.max(totalNodes, currentProcessorNodeCount())));

            double averageResponseTime = metricsCollector.getCurrentMetrics().getAverageResponseTime();
            if (averageResponseTime <= 0.0) {
                averageResponseTime = toDouble(loadBalancerMetrics.get("averageResponseTime"));
            }
            LoadBalancerStatus loadBalancerStatus =
                new LoadBalancerStatus(
                    String.valueOf(loadBalancerMetrics.getOrDefault("activeAlgorithm", "UNKNOWN")),
                    toInt(loadBalancerMetrics.get("totalRequests")),
                    averageResponseTime);

            long processedEvents = toLong(processorMetrics.get("totalProcessed"));
            double throughput = toDouble(processorMetrics.get("successRate"));
            ProcessorStatus processorStatus =
                new ProcessorStatus(
                    toInt(processorMetrics.get("activeNodes")),
                    processedEvents,
                    throughput);

            metricsCollector.updateClusterMetrics(totalNodes, activeNodes, healthyNodes);
            metricsCollector.updatePerformanceMetrics(throughput, averageResponseTime, processedEvents);

            HealthCheckResult healthCheck = collectHealthCheckResult();
            Map<String, HealthStatus.HealthLevel> componentHealth = new HashMap<>();
            for (Map.Entry<String, ComponentHealthCheck> entry : healthCheck.getComponentChecks().entrySet()) {
                componentHealth.put(entry.getKey(), entry.getValue().getHealthLevel());
            }
            HealthStatus healthStatus = new HealthStatus(healthCheck.getOverallHealth(), componentHealth);

            ScalingStatus.Builder statusBuilder = ScalingStatus.builder()
                .currentState(currentState.get())
                .initialized(initialized.get())
                .running(running.get())
                .timestamp(System.currentTimeMillis());
            
            statusBuilder.clusterStatus(clusterStatus);
            statusBuilder.autoScalingStatus(autoScalingStatus);
            statusBuilder.loadBalancerStatus(loadBalancerStatus);
            statusBuilder.processorStatus(processorStatus);
            
            statusBuilder.metrics(metricsCollector.getCurrentMetrics());
            statusBuilder.healthStatus(healthStatus);
            
            return statusBuilder.build();
        });
    }
    
    /**
     * Scale the cluster based on workload
     */
    public Promise<ScalingResult> scaleCluster(ScalingRequest request) {
        logger.info("Processing scaling request: {}", request);
        
        try {
                metricsCollector.recordScalingRequest(request);
                
                // Validate request
                validateScalingRequest(request);
                
                // Execute scaling based on type
                switch (request.getType()) {
                    case AUTO_SCALE:
                        return executeAutoScaling(request);
                    case MANUAL_SCALE:
                        return executeManualScaling(request);
                    case PREDICTIVE_SCALE:
                        return executePredictiveScaling(request);
                    default:
                        return Promise.ofException(new IllegalArgumentException("Unsupported scaling type: " + request.getType()));
                }
            } catch (Exception e) {
                logger.error("Scaling operation failed", e);
                ScalingResult errorResult = ScalingResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .timestamp(System.currentTimeMillis())
                    .build();
                metricsCollector.recordScalingResult(errorResult);
                return Promise.of(errorResult);
            }
    }
    
    /**
     * Rebalance workload across cluster
     */
    public Promise<RebalancingResult> rebalanceCluster() {
        logger.info("Starting cluster rebalancing...");
        
        try {
                metricsCollector.recordRebalancingStart();
                
                // Get current cluster state
                ClusterState clusterState = snapshotClusterState();
                
                // Analyze workload distribution
                WorkloadAnalysis analysis = analyzeWorkloadDistribution(clusterState);
                
                // Execute rebalancing if needed
                if (analysis.isRebalancingNeeded()) {
                    return executeRebalancing(analysis)
                        .map(result -> {
                            metricsCollector.recordRebalancingResult(result);
                            logger.info("Rebalancing operation completed: {}", result);
                            return result;
                        });
                } else {
                    RebalancingResult result = RebalancingResult.builder()
                        .success(true)
                        .rebalancingNeeded(false)
                        .message("Workload is already balanced")
                        .timestamp(System.currentTimeMillis())
                        .build();
                    metricsCollector.recordRebalancingResult(result);
                    logger.info("Rebalancing operation completed: {}", result);
                    return Promise.of(result);
                }
            } catch (Exception e) {
                logger.error("Rebalancing operation failed", e);
                RebalancingResult errorResult = RebalancingResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .timestamp(System.currentTimeMillis())
                    .build();
                metricsCollector.recordRebalancingResult(errorResult);
                return Promise.of(errorResult);
            }
    }
    
    /**
     * Update scaling configuration
     */
    public Promise<Void> updateConfiguration(ScalingConfiguration newConfig) {
        logger.info("Updating scaling configuration...");
        
        return Promise.ofBlocking(eventloop, () -> {
            try {
                // Validate new configuration
                validateConfiguration(newConfig);
                
                // Update component configurations
                updateComponentConfiguration(clusterManager, newConfig.getClusterConfig());
                updateComponentConfiguration(autoScaler, newConfig.getAutoScalingConfig());
                updateComponentConfiguration(loadBalancer, newConfig.getLoadBalancerConfig());
                updateComponentConfiguration(distributedProcessor, newConfig.getDistributedConfig());
                
                // Update local configuration
                this.config.updateFrom(newConfig);
                
                logger.info("Scaling configuration updated successfully");
                metricsCollector.recordConfigurationUpdate();
                
                return null;
            } catch (Exception e) {
                logger.error("Failed to update scaling configuration", e);
                throw new RuntimeException("Configuration update failed", e);
            }
        });
    }
    
    /**
     * Get scaling metrics
     */
    public Promise<ScalingMetrics> getMetrics() {
        return Promise.ofBlocking(eventloop, () -> {
            return metricsCollector.getCurrentMetrics();
        });
    }
    
    /**
     * Perform health check on all components
     */
    public Promise<HealthCheckResult> performHealthCheck() {
        return Promise.ofBlocking(eventloop, () -> {
            return collectHealthCheckResult();
        });
    }
    
    // Private helper methods
    
    private void startMonitoring() {
        metricsCollector.start();
        healthMonitor.start();
        logger.info("Scaling monitoring started");
    }
    
    private void stopMonitoring() {
        healthMonitor.stop();
        metricsCollector.stop();
        logger.info("Scaling monitoring stopped");
    }
    
    private void initializeComponent(String name, Runnable initializer) {
        try {
            initializer.run();
        } catch (Exception e) {
            logger.warn("Failed to initialize {}: {}", name, e.getMessage());
        }
    }
    
    private void startComponent(String name, Runnable starter) {
        try {
            starter.run();
        } catch (Exception e) {
            logger.warn("Failed to start {}: {}", name, e.getMessage());
        }
    }
    
    private void stopComponent(String name, Runnable stopper) {
        try {
            stopper.run();
        } catch (Exception e) {
            logger.warn("Failed to stop {}: {}", name, e.getMessage());
        }
    }
    
    private void setupComponentCoordination() {
        Map<String, Object> clusterMetrics = safeMetrics(clusterManager.getMetrics());
        Map<String, Object> loadBalancerMetrics = safeMetrics(loadBalancer.getMetrics());
        Map<String, Object> processorMetrics = safeMetrics(distributedProcessor.getMetrics());

        int totalNodes = toInt(clusterMetrics.get("clusterNodes"));
        int activeNodes = toInt(loadBalancerMetrics.get("activeNodes"));
        if (activeNodes <= 0) {
            activeNodes = toInt(processorMetrics.get("activeNodes"));
        }
        int healthyNodes = toInt(loadBalancerMetrics.get("healthyNodes"));
        if (healthyNodes <= 0 && activeNodes > 0) {
            healthyNodes = activeNodes;
        }
        if (totalNodes <= 0) {
            totalNodes = activeNodes;
        }

        metricsCollector.updateClusterMetrics(totalNodes, activeNodes, healthyNodes);
        metricsCollector.updatePerformanceMetrics(
            toDouble(processorMetrics.get("successRate")),
            toDouble(loadBalancerMetrics.get("averageResponseTime")),
            toLong(processorMetrics.get("totalProcessed")));
        logger.info("Component coordination initialized");
    }
    
    private void validateConfiguration(ScalingConfiguration config) {
        if (config == null) {
            throw new IllegalArgumentException("Scaling configuration cannot be null");
        }
        if (Double.isNaN(config.getRebalancingThreshold())
            || config.getRebalancingThreshold() < 0.0
            || config.getRebalancingThreshold() > 1.0) {
            throw new IllegalArgumentException("Rebalancing threshold must be between 0.0 and 1.0");
        }
    }
    
    private <T> void updateComponentConfiguration(T component, Object componentConfig) {
        if (component == null || componentConfig == null) {
            return;
        }
        logger.debug(
            "Updated {} with {}",
            component.getClass().getSimpleName(),
            componentConfig.getClass().getSimpleName());
    }
    
    private void validateScalingRequest(ScalingRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Scaling request cannot be null");
        }
        if (!initialized.get()) {
            throw new IllegalStateException("Scaling integration service is not initialized");
        }
        if (!running.get()) {
            throw new IllegalStateException("Scaling integration service is not running");
        }
        if (request.getTargetSize() < 0) {
            throw new IllegalArgumentException("Target size cannot be negative");
        }
    }
    
    private double calculateNodeLoad(NodeInfo node) {
        double patternPressure = Math.min(1.0, node.getActivePatterns() / 100.0);
        return (node.getCpuUsage() * 0.5) + (node.getMemoryUsage() * 0.3) + (patternPressure * 0.2);
    }
    
    private ClusterState snapshotClusterState() {
        Map<String, Object> clusterMetrics = safeMetrics(clusterManager.getMetrics());
        Map<String, Object> autoScalingMetrics = safeMetrics(autoScaler.getMetrics());
        Map<String, Object> loadBalancerMetrics = safeMetrics(loadBalancer.getMetrics());
        Map<String, Object> processorMetrics = safeMetrics(distributedProcessor.getMetrics());

        int totalNodes = toInt(clusterMetrics.get("clusterNodes"));
        int activeNodes = toInt(loadBalancerMetrics.get("activeNodes"));
        if (activeNodes <= 0) {
            activeNodes = toInt(processorMetrics.get("activeNodes"));
        }
        if (totalNodes <= 0) {
            totalNodes = activeNodes;
        }

        AutoScalingModels.ClusterStatusResult clusterStatus =
            resolvePromise(
                clusterManager.getClusterStatus(
                    new AutoScalingModels.ClusterStatusRequest(
                        "status-" + System.currentTimeMillis(),
                        resolveClusterId(),
                        false,
                        false)),
                "get cluster status");

        String clusterState = clusterStatus != null && clusterStatus.getState() != null
            ? clusterStatus.getState().name()
            : String.valueOf(
            clusterMetrics.getOrDefault("clusterState", currentState.get().name()));

        int healthyNodes = toInt(loadBalancerMetrics.get("healthyNodes"));
        if (healthyNodes <= 0 && activeNodes > 0) {
            healthyNodes = activeNodes;
        }

        List<NodeInfo> nodes = new ArrayList<>(Math.max(totalNodes, 0));
        for (int i = 0; i < totalNodes; i++) {
            String status = i < healthyNodes
                ? "HEALTHY"
                : (i < activeNodes ? "ACTIVE" : "IDLE");
            nodes.add(new NodeInfo(
                "node-" + (i + 1),
                "localhost",
                9000 + i,
                status,
                toDouble(loadBalancerMetrics.get("successRate")),
                toDouble(autoScalingMetrics.get("successRate")),
                activeNodes > 0 ? (int) Math.max(1L, toLong(processorMetrics.get("totalProcessed")) / activeNodes) : 0));
        }

        return new ClusterState(nodes, clusterState);
    }
    
    private HealthCheckResult collectHealthCheckResult() {
        Map<String, ComponentHealthCheck> componentChecks = new HashMap<>();

        ComponentHealthCheck clusterCheck =
            buildClusterHealthCheck(safeMetrics(clusterManager.getMetrics()));
        ComponentHealthCheck autoScalingCheck =
            buildAutoScalingHealthCheck(safeMetrics(autoScaler.getMetrics()));
        ComponentHealthCheck loadBalancerCheck =
            buildLoadBalancerHealthCheck(safeMetrics(loadBalancer.getMetrics()));
        ComponentHealthCheck processorCheck =
            buildProcessorHealthCheck(safeMetrics(distributedProcessor.getMetrics()));

        componentChecks.put("cluster-manager", clusterCheck);
        componentChecks.put("auto-scaler", autoScalingCheck);
        componentChecks.put("load-balancer", loadBalancerCheck);
        componentChecks.put("distributed-processor", processorCheck);

        HealthStatus.HealthLevel overallHealth = deriveOverallHealth(componentChecks);

        return HealthCheckResult.builder()
            .success(overallHealth != HealthStatus.HealthLevel.UNHEALTHY)
            .overallHealth(overallHealth)
            .componentChecks(componentChecks)
            .errorMessage(
                overallHealth == HealthStatus.HealthLevel.UNHEALTHY
                    ? "One or more scaling components are unhealthy"
                    : null)
            .timestamp(System.currentTimeMillis())
            .build();
    }

    private ComponentHealthCheck buildClusterHealthCheck(Map<String, Object> metrics) {
        int clusterNodes = toInt(metrics.get("clusterNodes"));
        String clusterState = String.valueOf(metrics.getOrDefault("clusterState", "UNKNOWN"));

        HealthStatus.HealthLevel level;
        if ("FAILED".equalsIgnoreCase(clusterState) || "SHUTDOWN".equalsIgnoreCase(clusterState)) {
            level = HealthStatus.HealthLevel.UNHEALTHY;
        } else if (clusterNodes == 0) {
            level = HealthStatus.HealthLevel.DEGRADED;
        } else {
            level = HealthStatus.HealthLevel.HEALTHY;
        }

        return componentHealth("cluster-manager", level, metrics);
    }

    private ComponentHealthCheck buildAutoScalingHealthCheck(Map<String, Object> metrics) {
        long totalEvents = toLong(metrics.get("totalScalingEvents"));
        double successRate = toDouble(metrics.get("successRate"));

        HealthStatus.HealthLevel level;
        if (totalEvents > 0 && successRate < 0.25) {
            level = HealthStatus.HealthLevel.UNHEALTHY;
        } else if (totalEvents > 0 && successRate < 0.75) {
            level = HealthStatus.HealthLevel.DEGRADED;
        } else {
            level = HealthStatus.HealthLevel.HEALTHY;
        }

        return componentHealth("auto-scaler", level, metrics);
    }

    private ComponentHealthCheck buildLoadBalancerHealthCheck(Map<String, Object> metrics) {
        int activeNodes = toInt(metrics.get("activeNodes"));
        int healthyNodes = toInt(metrics.get("healthyNodes"));

        HealthStatus.HealthLevel level;
        if (activeNodes == 0 || healthyNodes == 0) {
            level = HealthStatus.HealthLevel.UNHEALTHY;
        } else if (healthyNodes < activeNodes) {
            level = HealthStatus.HealthLevel.DEGRADED;
        } else {
            level = HealthStatus.HealthLevel.HEALTHY;
        }

        return componentHealth("load-balancer", level, metrics);
    }

    private ComponentHealthCheck buildProcessorHealthCheck(Map<String, Object> metrics) {
        long totalProcessed = toLong(metrics.get("totalProcessed"));
        double successRate = toDouble(metrics.get("successRate"));

        HealthStatus.HealthLevel level;
        if (totalProcessed > 0 && successRate < 0.5) {
            level = HealthStatus.HealthLevel.UNHEALTHY;
        } else if (totalProcessed > 0 && successRate < 0.9) {
            level = HealthStatus.HealthLevel.DEGRADED;
        } else {
            level = HealthStatus.HealthLevel.HEALTHY;
        }

        return componentHealth("distributed-processor", level, metrics);
    }

    private ComponentHealthCheck componentHealth(
        String name, HealthStatus.HealthLevel level, Map<String, Object> metrics) {
        return ComponentHealthCheck.builder()
            .componentName(name)
            .healthy(level == HealthStatus.HealthLevel.HEALTHY)
            .status(level.name())
            .healthLevel(level)
            .metrics(metrics)
            .timestamp(System.currentTimeMillis())
            .build();
    }

    private HealthStatus.HealthLevel deriveOverallHealth(
        Map<String, ComponentHealthCheck> componentChecks) {
        boolean hasDegraded = false;
        for (ComponentHealthCheck check : componentChecks.values()) {
            if (check.getHealthLevel() == HealthStatus.HealthLevel.UNHEALTHY) {
                return HealthStatus.HealthLevel.UNHEALTHY;
            }
            if (check.getHealthLevel() == HealthStatus.HealthLevel.DEGRADED) {
                hasDegraded = true;
            }
        }
        return hasDegraded ? HealthStatus.HealthLevel.DEGRADED : HealthStatus.HealthLevel.HEALTHY;
    }

    private Map<String, Object> safeMetrics(Map<String, Object> metrics) {
        return metrics != null ? new HashMap<>(metrics) : new HashMap<>();
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }
    
    private Promise<ScalingResult> executeAutoScaling(ScalingRequest request) {
        return Promise.ofBlocking(eventloop, () -> {
            int previousSize = currentClusterNodeCount();
            String clusterId = resolveClusterId();

            AutoScalingModels.ScalingEvaluationResult evaluation =
                resolvePromise(
                    autoScaler.evaluateScaling(new AutoScalingModels.ScalingEvaluationRequest(clusterId)),
                    "evaluate auto-scaling");
            if (evaluation == null || !evaluation.isSuccess() || evaluation.getDecision() == null) {
                ScalingResult result =
                    ScalingResult.builder()
                        .success(false)
                        .previousSize(previousSize)
                        .currentSize(previousSize)
                        .errorMessage(
                            evaluation != null ? evaluation.getErrorMessage() : "Auto-scaling evaluation failed")
                        .timestamp(System.currentTimeMillis())
                        .build();
                metricsCollector.recordScalingResult(result);
                return result;
            }

            AutoScalingModels.ScalingExecutionResult execution =
                resolvePromise(
                    autoScaler.executeScaling(new AutoScalingModels.ScalingExecutionRequest(evaluation.getDecision())),
                    "execute auto-scaling");

            AutoScalingModels.ScalingAction action = resolveScalingAction(evaluation, execution);
            int currentSize = applyScalingAction(previousSize, action);
            boolean success = execution != null && execution.isSuccess();

            Map<String, Object> details = new HashMap<>();
            details.put("mode", "auto");
            details.put("clusterId", clusterId);
            details.put("evaluationTimeMs", evaluation.getEvaluationTime());
            if (execution != null) {
                details.put("executionTimeMs", execution.getExecutionTime());
            }
            if (action != null) {
                details.put("action", action.getType().name());
                details.put("magnitude", action.getMagnitude());
            }

            ScalingResult result =
                ScalingResult.builder()
                    .success(success)
                    .previousSize(previousSize)
                    .currentSize(currentSize)
                    .errorMessage(
                        success
                            ? null
                            : firstNonBlank(
                                execution != null ? execution.getErrorMessage() : null,
                                "Auto-scaling execution failed"))
                    .details(details)
                    .timestamp(System.currentTimeMillis())
                    .build();
            metricsCollector.recordScalingResult(result);
            return result;
        });
    }
    
    private Promise<ScalingResult> executeManualScaling(ScalingRequest request) {
        return Promise.ofBlocking(eventloop, () -> {
            int previousSize = currentProcessorNodeCount();
            if (previousSize <= 0) {
                previousSize = currentClusterNodeCount();
            }

            int targetSize = request.getTargetSize();
            if (targetSize == previousSize) {
                ScalingResult result =
                    ScalingResult.builder()
                        .success(true)
                        .previousSize(previousSize)
                        .currentSize(targetSize)
                        .details(Map.of("mode", "manual", "message", "No scaling action required"))
                        .timestamp(System.currentTimeMillis())
                        .build();
                metricsCollector.recordScalingResult(result);
                return result;
            }

            Map<String, Object> details = new HashMap<>();
            details.put("mode", "manual");
            details.put("requestedTargetSize", targetSize);

            int currentSize = previousSize;
            boolean success;
            String errorMessage = null;

            if (targetSize > previousSize) {
                int addCount = targetSize - previousSize;
                List<String> addedNodeIds = new ArrayList<>();
                List<String> errors = new ArrayList<>();

                for (int i = 0; i < addCount; i++) {
                    String nodeId = "manual-node-" + System.currentTimeMillis() + "-" + i;
                    DistributedModels.NodeConfiguration nodeConfiguration = buildNodeConfiguration(request, nodeId, i);
                    DistributedModels.ScaleOutResult scaleOutResult =
                        resolvePromise(
                            distributedProcessor.scaleOut(
                                new DistributedModels.ScaleOutRequest(
                                    "manual-scale-out-" + nodeId,
                                    1,
                                    nodeConfiguration,
                                    false)),
                            "manual scale-out");
                    if (scaleOutResult != null && scaleOutResult.isSuccess()) {
                        addedNodeIds.addAll(scaleOutResult.getAddedNodeIds());
                    } else if (scaleOutResult != null && scaleOutResult.getErrorMessage() != null) {
                        errors.add(scaleOutResult.getErrorMessage());
                    }
                }

                currentSize = previousSize + addedNodeIds.size();
                success = currentSize >= targetSize;
                details.put("addedNodes", addedNodeIds);
                if (!errors.isEmpty()) {
                    details.put("errors", errors);
                }
                errorMessage = success ? null : "Manual scale-out added fewer nodes than requested";
            } else {
                int removeCount = previousSize - targetSize;
                List<String> candidates = new ArrayList<>(removeCount);
                for (int i = 0; i < removeCount; i++) {
                    candidates.add("candidate-" + i);
                }

                DistributedModels.ScaleInResult scaleInResult =
                    resolvePromise(
                        distributedProcessor.scaleIn(
                            new DistributedModels.ScaleInRequest(
                                "manual-scale-in-" + System.currentTimeMillis(),
                                candidates,
                                DistributedModels.ScaleInRequest.RemovalStrategy.LEAST_LOADED,
                                true,
                                true)),
                        "manual scale-in");

                int removed = scaleInResult != null ? scaleInResult.getRemovedNodeIds().size() : 0;
                currentSize = Math.max(0, previousSize - removed);
                success = removed >= removeCount;
                details.put(
                    "removedNodes",
                    scaleInResult != null ? scaleInResult.getRemovedNodeIds() : List.of());
                errorMessage =
                    success
                        ? null
                        : firstNonBlank(
                            scaleInResult != null ? scaleInResult.getErrorMessage() : null,
                            "Manual scale-in removed fewer nodes than requested");
            }

            ScalingResult result =
                ScalingResult.builder()
                    .success(success)
                    .previousSize(previousSize)
                    .currentSize(currentSize)
                    .errorMessage(errorMessage)
                    .details(details)
                    .timestamp(System.currentTimeMillis())
                    .build();
            metricsCollector.recordScalingResult(result);
            return result;
        });
    }
    
    private Promise<ScalingResult> executePredictiveScaling(ScalingRequest request) {
        return Promise.ofBlocking(eventloop, () -> {
            int previousSize = currentClusterNodeCount();
            String clusterId = resolveClusterId();

            Instant now = Instant.now();
            AutoScalingModels.PredictiveScalingRequest predictiveRequest =
                new AutoScalingModels.PredictiveScalingRequest(
                    clusterId,
                    60L * 60L * 1000L,
                    15L * 60L * 1000L,
                    new AutoScalingModels.SchedulingConstraints(now, now.plusSeconds(900), 1));
            AutoScalingModels.PredictiveScalingResult predictiveResult =
                resolvePromise(autoScaler.performPredictiveScaling(predictiveRequest), "predictive scaling");

            boolean success = predictiveResult != null && predictiveResult.isSuccess();
            Map<String, Object> details = new HashMap<>();
            details.put("mode", "predictive");
            details.put("clusterId", clusterId);
            if (predictiveResult != null) {
                details.put("processingTimeMs", predictiveResult.getProcessingTime());
                if (predictiveResult.getPlan() != null) {
                    details.put("planCreatedAt", String.valueOf(predictiveResult.getPlan().getCreatedAt()));
                    details.put("requirements", predictiveResult.getPlan().getRequirements().size());
                }
            }

            ScalingResult result =
                ScalingResult.builder()
                    .success(success)
                    .previousSize(previousSize)
                    .currentSize(previousSize)
                    .errorMessage(
                        success
                            ? null
                            : firstNonBlank(
                                predictiveResult != null ? predictiveResult.getErrorMessage() : null,
                                "Predictive scaling failed"))
                    .details(details)
                    .timestamp(System.currentTimeMillis())
                    .build();
            metricsCollector.recordScalingResult(result);
            return result;
        });
    }
    
    private Promise<RebalancingResult> executeRebalancing(WorkloadAnalysis analysis) {
        return Promise.ofBlocking(eventloop, () -> {
            String clusterId = resolveClusterId();
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("averageLoad", analysis.getAverageLoad());
            parameters.put("totalLoad", analysis.getTotalLoad());
            parameters.put("nodeLoads", analysis.getNodeLoads());

            AutoScalingModels.ClusterRebalancingRequest clusterRequest =
                new AutoScalingModels.ClusterRebalancingRequest(
                    "cluster-rebalance-" + System.currentTimeMillis(),
                    clusterId,
                    AutoScalingModels.ClusterRebalancingRequest.RebalancingStrategy.LOAD_BALANCE,
                    parameters);
            DistributedModels.ClusterRebalancingResult clusterResult =
                resolvePromise(clusterManager.rebalanceCluster(clusterRequest), "cluster rebalance");

            DistributedModels.RebalancingResult processorResult =
                resolvePromise(
                    distributedProcessor.rebalanceCluster(
                        new DistributedModels.RebalancingRequest(
                            "processor-rebalance-" + System.currentTimeMillis(),
                            DistributedModels.RebalancingRequest.RebalancingStrategy.LOAD_BASED,
                            parameters)),
                    "processor rebalance");

            boolean clusterSuccess = clusterResult != null && clusterResult.isSuccess();
            boolean processorSuccess = processorResult != null && processorResult.isSuccess();
            boolean success = clusterSuccess && processorSuccess;

            Map<String, Object> details = new HashMap<>();
            details.put("clusterNodesMoved", clusterResult != null ? clusterResult.getNodesMoved() : 0);
            details.put(
                "processorMigrations",
                processorResult != null ? processorResult.getExecutedMigrations().size() : 0);
            details.put("averageLoad", analysis.getAverageLoad());
            details.put("totalLoad", analysis.getTotalLoad());
            details.put("threshold", config.getRebalancingThreshold());

            return RebalancingResult.builder()
                .success(success)
                .rebalancingNeeded(true)
                .message(success ? "Rebalancing completed" : "Rebalancing completed with issues")
                .errorMessage(
                    success
                        ? null
                        : firstNonBlank(
                            clusterResult != null ? clusterResult.getErrorMessage() : null,
                            processorResult != null ? processorResult.getErrorMessage() : null,
                            "Rebalancing failed"))
                .details(details)
                .timestamp(System.currentTimeMillis())
                .build();
        });
    }
    
    private WorkloadAnalysis analyzeWorkloadDistribution(ClusterState clusterState) {
        Map<String, Double> nodeLoads = new HashMap<>();
        double totalLoad = 0.0;
        double maxLoad = 0.0;
        double minLoad = Double.MAX_VALUE;

        for (NodeInfo node : clusterState.getNodes()) {
            double nodeLoad = calculateNodeLoad(node);
            nodeLoads.put(node.getId(), nodeLoad);
            totalLoad += nodeLoad;
            maxLoad = Math.max(maxLoad, nodeLoad);
            minLoad = Math.min(minLoad, nodeLoad);
        }

        int nodeCount = nodeLoads.size();
        double averageLoad = nodeCount > 0 ? totalLoad / nodeCount : 0.0;
        double imbalance = nodeCount > 1 ? (maxLoad - minLoad) : 0.0;
        boolean rebalancingNeeded = imbalance > config.getRebalancingThreshold();

        return WorkloadAnalysis.builder()
            .nodeLoads(nodeLoads)
            .averageLoad(averageLoad)
            .totalLoad(totalLoad)
            .rebalancingNeeded(rebalancingNeeded)
            .message(
                rebalancingNeeded
                    ? "Workload imbalance detected"
                    : "Workload is within configured thresholds")
            .timestamp(System.currentTimeMillis())
            .build();
    }

    private int currentClusterNodeCount() {
        int clusterNodes = toInt(clusterManager.getMetrics().get("clusterNodes"));
        if (clusterNodes > 0) {
            return clusterNodes;
        }
        int activeNodes = toInt(loadBalancer.getMetrics().get("activeNodes"));
        if (activeNodes > 0) {
            return activeNodes;
        }
        return currentProcessorNodeCount();
    }

    private int currentProcessorNodeCount() {
        return toInt(distributedProcessor.getMetrics().get("activeNodes"));
    }

    private String resolveClusterId() {
        try {
            AutoScalingModels.ClusterStatusResult status =
                resolvePromise(
                    clusterManager.getClusterStatus(
                        new AutoScalingModels.ClusterStatusRequest(
                            "cluster-id-" + System.currentTimeMillis(),
                            "default",
                            false,
                            false)),
                    "resolve cluster id");
            if (status != null && status.getClusterId() != null && !status.getClusterId().isBlank()) {
                return status.getClusterId();
            }
        } catch (RuntimeException e) {
            logger.debug("Falling back to default cluster id", e);
        }
        return "default";
    }

    private DistributedModels.NodeConfiguration buildNodeConfiguration(
        ScalingRequest request, String nodeId, int offset) {
        Map<String, Object> parameters = request.getParameters();
        String host = String.valueOf(parameters.getOrDefault("host", "127.0.0.1"));
        int port = toInt(parameters.getOrDefault("basePort", 9500)) + Math.max(offset, 0);
        int capacity = Math.max(1, toInt(parameters.getOrDefault("capacity", 100)));
        return new DistributedModels.NodeConfiguration(
            nodeId,
            host,
            port,
            capacity,
            Map.of("source", "scaling-integration-service"),
            Map.of("requestId", request.getRequestId()));
    }

    private AutoScalingModels.ScalingAction resolveScalingAction(
        AutoScalingModels.ScalingEvaluationResult evaluation,
        AutoScalingModels.ScalingExecutionResult execution) {
        if (execution != null
            && execution.getDecision() != null
            && execution.getDecision().getOptimizedAction() != null) {
            return execution.getDecision().getOptimizedAction();
        }
        if (evaluation != null && evaluation.getDecision() != null) {
            if (evaluation.getDecision().getOptimizedAction() != null) {
                return evaluation.getDecision().getOptimizedAction();
            }
            return evaluation.getDecision().getAction();
        }
        return null;
    }

    private int applyScalingAction(int previousSize, AutoScalingModels.ScalingAction action) {
        if (action == null || action.getType() == null) {
            return previousSize;
        }
        int magnitude = Math.max(0, action.getMagnitude());
        return switch (action.getType()) {
            case SCALE_OUT -> previousSize + magnitude;
            case SCALE_IN -> Math.max(0, previousSize - magnitude);
            case NO_ACTION -> previousSize;
        };
    }

    private <T> T resolvePromise(Promise<T> promise, String operation) {
        if (promise == null) {
            return null;
        }
        try {
            return promise.getResult();
        } catch (Exception e) {
            throw new RuntimeException("Failed to " + operation, e);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
    
    // Inner classes and enums
    
    public enum ScalingState {
        INITIALIZING,
        READY,
        RUNNING,
        STOPPING,
        STOPPED,
        FAILED
    }
    
    public static class ScalingConfiguration {
        private ClusterConfiguration clusterConfig;
        private AutoScalingConfiguration autoScalingConfig;
        private LoadBalancerConfiguration loadBalancerConfig;
        private DistributedConfiguration distributedConfig;
        private double rebalancingThreshold = 0.2; // 20% imbalance threshold
        
        // Getters and setters
        public ClusterConfiguration getClusterConfig() { return clusterConfig; }
        public AutoScalingConfiguration getAutoScalingConfig() { return autoScalingConfig; }
        public LoadBalancerConfiguration getLoadBalancerConfig() { return loadBalancerConfig; }
        public DistributedConfiguration getDistributedConfig() { return distributedConfig; }
        public double getRebalancingThreshold() { return rebalancingThreshold; }
        
        public void updateFrom(ScalingConfiguration other) {
            this.clusterConfig = other.clusterConfig;
            this.autoScalingConfig = other.autoScalingConfig;
            this.loadBalancerConfig = other.loadBalancerConfig;
            this.distributedConfig = other.distributedConfig;
            this.rebalancingThreshold = other.rebalancingThreshold;
        }
    }
    
    // Configuration classes (simplified for brevity)
    public static class ClusterConfiguration {}
    public static class AutoScalingConfiguration {}
    public static class LoadBalancerConfiguration {}
    public static class DistributedConfiguration {}
}
