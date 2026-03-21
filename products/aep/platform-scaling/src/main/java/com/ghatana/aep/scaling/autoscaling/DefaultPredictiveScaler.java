/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.scaling.autoscaling;

import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.PredictiveScaler;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.PredictiveScalingRecommendation;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingAction;
import com.ghatana.aep.scaling.cluster.ClusterManagementModels.ClusterMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default predictive scaler that uses simple threshold-based heuristics.
 * In production, this would integrate with ML-based forecasting models.
 *
 * @doc.type class
 * @doc.purpose Provides predictive scaling recommendations based on metrics
 * @doc.layer product
 * @doc.pattern Service
 */
public class DefaultPredictiveScaler implements PredictiveScaler {

    private static final Logger log = LoggerFactory.getLogger(DefaultPredictiveScaler.class);

    private static final double CPU_HIGH_THRESHOLD = 0.80;
    private static final double CPU_LOW_THRESHOLD = 0.20;
    private static final double MEMORY_HIGH_THRESHOLD = 0.85;

    private final ConcurrentHashMap<String, Map<String, Object>> forecastModels = new ConcurrentHashMap<>();
    private final DefaultMetricsCollector metricsCollector;

    public DefaultPredictiveScaler(DefaultMetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    @Override
    public PredictiveScalingRecommendation getRecommendation(String clusterId, ClusterMetrics metrics) {
        double cpu = metrics.getCpuUtilization();
        double memory = metrics.getMemoryUtilization();

        if (cpu > CPU_HIGH_THRESHOLD || memory > MEMORY_HIGH_THRESHOLD) {
            int additionalNodes = (int) Math.ceil((cpu - 0.5) * metrics.getTotalNodes());
            additionalNodes = Math.max(1, additionalNodes);
            return new PredictiveScalingRecommendation(
                ScalingAction.Type.SCALE_UP,
                metrics.getActiveNodes() + additionalNodes,
                0.7,
                String.format("High utilization detected: cpu=%.1f%%, mem=%.1f%%",
                    cpu * 100, memory * 100)
            );
        }

        if (cpu < CPU_LOW_THRESHOLD && metrics.getActiveNodes() > 1) {
            return new PredictiveScalingRecommendation(
                ScalingAction.Type.SCALE_DOWN,
                Math.max(1, metrics.getActiveNodes() - 1),
                0.6,
                String.format("Low utilization detected: cpu=%.1f%%", cpu * 100)
            );
        }

        return new PredictiveScalingRecommendation(
            ScalingAction.Type.NO_ACTION,
            metrics.getActiveNodes(),
            0.9,
            "Cluster utilization within normal range"
        );
    }

    @Override
    public List<PredictiveScalingRecommendation> getRecommendationsForAllClusters() {
        return metricsCollector.collectAllClusterMetrics().stream()
            .map(m -> getRecommendation(m.getClusterId(), m))
            .toList();
    }

    @Override
    public void updateForecastingModel(String clusterId, Map<String, Object> trainingData) {
        log.info("Updating forecasting model for cluster={}, dataPoints={}",
            clusterId, trainingData.size());
        forecastModels.put(clusterId, trainingData);
    }
}
