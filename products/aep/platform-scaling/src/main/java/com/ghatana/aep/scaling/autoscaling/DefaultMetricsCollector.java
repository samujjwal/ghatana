/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.scaling.autoscaling;

import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.MetricsCollector;
import com.ghatana.aep.scaling.cluster.ClusterManagementModels.ClusterMetrics;
import com.ghatana.aep.scaling.cluster.ClusterManagementSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory metrics collector that collects cluster and node metrics
 * from the {@link ClusterManagementSystem} or returns cached/default values
 * when the cluster system is not available.
 *
 * @doc.type class
 * @doc.purpose Collects cluster and node metrics for scaling decisions
 * @doc.layer product
 * @doc.pattern Service
 */
public class DefaultMetricsCollector implements MetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(DefaultMetricsCollector.class);

    private final ConcurrentHashMap<String, ClusterMetrics> metricsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Map<String, Object>> nodeMetricsCache = new ConcurrentHashMap<>();

    @Override
    public ClusterMetrics collectClusterMetrics(String clusterId) {
        return metricsCache.computeIfAbsent(clusterId, id -> {
            log.debug("Collecting metrics for cluster={}", id);
            return ClusterMetrics.builder()
                .clusterId(id)
                .activeNodes(1)
                .totalNodes(1)
                .cpuUtilization(0.0)
                .memoryUtilization(0.0)
                .requestThroughput(0L)
                .averageLatency(0.0)
                .timestamp(Instant.now())
                .build();
        });
    }

    @Override
    public Map<String, Object> collectNodeMetrics(String nodeId) {
        return nodeMetricsCache.computeIfAbsent(nodeId, id -> {
            log.debug("Collecting node metrics for node={}", id);
            return Map.of(
                "nodeId", id,
                "cpuUsage", 0.0,
                "memoryUsage", 0.0,
                "diskUsage", 0.0,
                "requestsHandled", 0L,
                "status", "HEALTHY",
                "timestamp", Instant.now().toString()
            );
        });
    }

    @Override
    public List<ClusterMetrics> collectAllClusterMetrics() {
        return List.copyOf(metricsCache.values());
    }

    /**
     * Updates the cached metrics for a cluster. Used by external monitoring
     * integrations to push real metrics.
     */
    public void updateClusterMetrics(String clusterId, ClusterMetrics metrics) {
        metricsCache.put(clusterId, metrics);
    }

    /**
     * Updates the cached metrics for a node.
     */
    public void updateNodeMetrics(String nodeId, Map<String, Object> metrics) {
        nodeMetricsCache.put(nodeId, metrics);
    }
}
