/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.scaling.autoscaling;

import com.ghatana.aep.scaling.cluster.ClusterManagementModels.ClusterMetrics;
import io.activej.promise.Promise;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Models for scaling operations and auto-scaling engine.
 */
public final class ScalingOperationModels {

    private ScalingOperationModels() {
        // Utility class
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScalingEvaluationRequest {
        private String clusterId;
        private Instant evaluationTime;
        private Map<String, Object> context;

        public ScalingEvaluationRequest(String clusterId) {
            this.clusterId = clusterId;
            this.evaluationTime = Instant.now();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScalingEvaluationResult {
        private boolean success;
        private String errorMessage;
        private ScalingDecision decision;
        private long evaluationTime;
        private List<String> appliedPolicies;
        private PredictiveScalingRecommendation prediction;
        private CostOptimizationResult costOptimization;

        public boolean isSuccess() {
            return success;
        }

        public ScalingDecision getDecision() {
            return decision;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public long getEvaluationTime() {
            return evaluationTime;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScalingDecision {
        private String clusterId;
        private ScalingAction action;
        private String reason;
        private Instant timestamp;
        private double confidence;

        public ScalingDecision(String clusterId, ScalingAction action) {
            this.clusterId = clusterId;
            this.action = action;
            this.timestamp = Instant.now();
            this.confidence = 0.0;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScalingAction {
        public enum Type {
            SCALE_UP,
            SCALE_DOWN,
            NO_ACTION,
            MAINTENANCE
        }

        private Type type;
        private int nodeCount;
        private String reason;
        private Map<String, Object> metadata;

        public ScalingAction(Type type, int nodeCount, String reason) {
            this.type = type;
            this.nodeCount = nodeCount;
            this.reason = reason;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScalingPolicy {
        private String policyId;
        private String name;
        private String description;
        private double scaleUpThreshold;
        private double scaleDownThreshold;
        private int minNodes;
        private int maxNodes;
        private int scaleUpStep;
        private int scaleDownStep;
        private boolean enabled;
        private List<String> applicableClusters;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictiveScalingRecommendation {
        private ScalingAction.Type recommendedAction;
        private int recommendedNodeCount;
        private double confidence;
        private String reason;
        private Map<String, Object> forecastData;

        public PredictiveScalingRecommendation(ScalingAction.Type action, int count, double confidence, String reason) {
            this.recommendedAction = action;
            this.recommendedNodeCount = count;
            this.confidence = confidence;
            this.reason = reason;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CostOptimizationResult {
        private ScalingAction optimizedAction;
        private double estimatedCost;
        private List<String> optimizationNotes;
        private long currentNodeCount;
        private boolean optimizationApplied;
        private Map<String, Object> costMetrics;

        public CostOptimizationResult(ScalingAction action, Double cost, List<String> notes, 
                                     Long nodeCount, Boolean applied, Map<String, Object> metrics) {
            this.optimizedAction = action;
            this.estimatedCost = cost != null ? cost : 0.0;
            this.optimizationNotes = notes != null ? notes : List.of();
            this.currentNodeCount = nodeCount != null ? nodeCount : 0;
            this.optimizationApplied = applied != null ? applied : false;
            this.costMetrics = metrics;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ScalingEvent {
        private String eventId;
        private String clusterId;
        private Instant timestamp;
        private ScalingAction action;
        private ScalingEventType eventType;
        private String message;
        private boolean successful;
        private Map<String, Object> metadata;

        public enum ScalingEventType {
            EVALUATION,
            SCALE_UP,
            SCALE_DOWN,
            POLICY_VIOLATION,
            ERROR
        }
    }

    // ==================== Service Interfaces ====================

    public interface ClusterMetricsCollector {
        ClusterMetrics collectClusterMetrics(String clusterId);
        Map<String, Object> collectNodeMetrics(String nodeId);
        List<ClusterMetrics> collectAllClusterMetrics();
    }

    public interface ScalingPolicyManager {
        List<ScalingPolicy> getApplicablePolicies(String clusterId);
        ScalingPolicy getPolicy(String policyId);
        void addPolicy(ScalingPolicy policy);
        void removePolicy(String policyId);
        List<ScalingPolicy> getAllPolicies();
    }

    public interface ScalingExecutor {
        Promise<Void> executeScaling(ScalingDecision decision);
        Promise<Boolean> validateScalingAction(ScalingAction action);
        Promise<Void> rollbackScaling(String clusterId);
    }

    public interface PredictiveScaler {
        PredictiveScalingRecommendation getRecommendation(String clusterId, ClusterMetrics metrics);
        List<PredictiveScalingRecommendation> getRecommendationsForAllClusters();
        void updateForecastingModel(String clusterId, Map<String, Object> trainingData);
    }

    public interface CostOptimizer {
        CostOptimizationResult optimizeScalingDecision(ScalingDecision decision, 
                                                        ClusterMetrics metrics, 
                                                        List<ScalingPolicy> policies);
        double estimateScalingCost(ScalingAction action, String region);
        Map<String, Object> getCostMetrics(String clusterId);
    }
}
