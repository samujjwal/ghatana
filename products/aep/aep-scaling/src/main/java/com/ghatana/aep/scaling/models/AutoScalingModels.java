/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.scaling.models;

import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels;
import com.ghatana.aep.scaling.cluster.ClusterManagementModels;

/**
 * Container class for all auto-scaling related models.
 * Provides a unified access point for scaling model classes.
 */
public final class AutoScalingModels {

    private AutoScalingModels() {
        // Utility class - prevent instantiation
    }

    // Re-export operation models for convenience
    public static class ScalingEvaluationRequest extends ScalingOperationModels.ScalingEvaluationRequest {
        public ScalingEvaluationRequest() { super(); }
        public ScalingEvaluationRequest(String clusterId) { super(clusterId); }
    }
    public static class ScalingEvaluationResult extends ScalingOperationModels.ScalingEvaluationResult {}
    public static class ScalingDecision extends ScalingOperationModels.ScalingDecision {
        public ScalingDecision() { super(); }
        public ScalingDecision(String clusterId, ScalingAction action) { super(clusterId, action); }
    }
    public static class ScalingAction extends ScalingOperationModels.ScalingAction {
        public ScalingAction() { super(); }
        public ScalingAction(Type type, int nodeCount, String reason) { super(type, nodeCount, reason); }
    }
    public static class ScalingPolicy extends ScalingOperationModels.ScalingPolicy {}
    public static class PredictiveScalingRecommendation extends ScalingOperationModels.PredictiveScalingRecommendation {}
    public static class CostOptimizationResult extends ScalingOperationModels.CostOptimizationResult {}
    public static class ClusterMetrics extends ClusterManagementModels.ClusterMetrics {}
    public static class NodeMetrics extends ClusterManagementModels.NodeMetrics {}
    public static class ScalingEvent extends ScalingOperationModels.ScalingEvent {}

    /**
     * Factory methods for common scaling model instances
     */
    public static class Factory {
        public static ScalingEvaluationRequest createEvaluationRequest(String clusterId) {
            return new ScalingEvaluationRequest();
        }

        public static ScalingDecision createDecision(String clusterId, ScalingAction action) {
            return new ScalingDecision(clusterId, action);
        }

        public static ScalingAction createScaleUpAction(int nodeCount, String reason) {
            return new ScalingAction(ScalingAction.Type.SCALE_UP, nodeCount, reason);
        }

        public static ScalingAction createScaleDownAction(int nodeCount, String reason) {
            return new ScalingAction(ScalingAction.Type.SCALE_DOWN, nodeCount, reason);
        }

        public static ScalingAction createNoAction(String reason) {
            return new ScalingAction(ScalingAction.Type.NO_ACTION, 0, reason);
        }
    }
}
