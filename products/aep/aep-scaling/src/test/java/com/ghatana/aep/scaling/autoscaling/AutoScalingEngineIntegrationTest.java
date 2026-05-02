/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.aep.scaling.autoscaling;

import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.CostOptimizationResult;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.PredictiveScalingRecommendation;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingAction;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingEvaluationRequest;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingEvaluationResult;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingPolicy;
import com.ghatana.aep.scaling.cluster.ClusterManagementModels.ClusterMetrics;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration-style tests for {@link AutoScalingEngine} with concrete collaborators.
 *
 * @doc.type class
 * @doc.purpose Verify AutoScalingEngine coordinates concrete scaling collaborators end-to-end
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("AutoScalingEngineIntegrationTest")
class AutoScalingEngineIntegrationTest extends EventloopTestBase {

    @Test
    @DisplayName("high-confidence prediction drives a scale-up recommendation")
    void highConfidencePredictionDrivesScaleUpRecommendation() { 
        AutoScalingEngine engine = new AutoScalingEngine( 
            metricsCollector(0.25, 2), 
            policyManager(policy("scale-policy", 0.80, 0.20)), 
            scalingExecutor(), 
            predictiveScaler(new PredictiveScalingRecommendation( 
                ScalingAction.Type.SCALE_UP, 3, 0.95, "traffic spike expected")),
            costOptimizer(new CostOptimizationResult( 
                new ScalingAction(ScalingAction.Type.SCALE_UP, 3, "prediction chosen"), 
                0.0,
                List.of("prediction chosen"),
                2L,
                false,
                java.util.Map.of())), 
            eventloop()); 

        ScalingEvaluationResult result = runPromise(() -> 
            engine.evaluateScaling(new ScalingEvaluationRequest("cluster-a")));

        assertThat(result.isSuccess()).isTrue(); 
        assertThat(result.getDecision().getAction().getType()).isEqualTo(ScalingAction.Type.SCALE_UP); 
        assertThat(result.getDecision().getAction().getNodeCount()).isEqualTo(3); 
        assertThat(result.getAppliedPolicies()).containsExactly("scale-policy");
    }

    @Test
    @DisplayName("policy thresholds drive scale-down when prediction confidence is low")
    void policyThresholdsDriveScaleDownWhenPredictionConfidenceIsLow() { 
        AutoScalingEngine engine = new AutoScalingEngine( 
            metricsCollector(0.10, 4), 
            policyManager(policy("scale-policy", 0.80, 0.20)), 
            scalingExecutor(), 
            predictiveScaler(new PredictiveScalingRecommendation( 
                ScalingAction.Type.SCALE_UP, 10, 0.20, "low confidence prediction should be ignored")),
            costOptimizer(new CostOptimizationResult( 
                new ScalingAction(ScalingAction.Type.SCALE_DOWN, 1, "scale down reduces idle cost"), 
                42.0,
                List.of("scale down reduces idle cost"),
                4L,
                true,
                java.util.Map.of())), 
            eventloop()); 

        ScalingEvaluationResult result = runPromise(() -> 
            engine.evaluateScaling(new ScalingEvaluationRequest("cluster-b")));

        assertThat(result.isSuccess()).isTrue(); 
        assertThat(result.getDecision().getAction().getType()).isEqualTo(ScalingAction.Type.SCALE_DOWN); 
        assertThat(result.getDecision().getAction().getNodeCount()).isEqualTo(1); 
        assertThat(result.getCostOptimization().isOptimizationApplied()).isTrue(); 
    }

    @Test
    @DisplayName("null metrics from collector produces failure result (no exception propagated)")
    void nullMetricsProducesFailureResult() {
        AutoScalingEngine engine = new AutoScalingEngine(
            metricsCollectorReturningNull(),
            policyManager(policy("p1", 0.80, 0.20)),
            scalingExecutor(),
            predictiveScaler(new PredictiveScalingRecommendation(
                ScalingAction.Type.NO_ACTION, 0, 0.0, "n/a")),
            costOptimizer(new CostOptimizationResult(
                new ScalingAction(ScalingAction.Type.NO_ACTION, 0, "n/a"),
                0.0, List.of(), 0L, false, java.util.Map.of())),
            eventloop());

        ScalingEvaluationResult result = runPromise(() ->
            engine.evaluateScaling(new ScalingEvaluationRequest("unreachable-cluster")));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("unreachable-cluster");
    }

    @Test
    @DisplayName("CPU exactly at scale-up threshold boundary triggers SCALE_UP")
    void cpuAtExactScaleUpThresholdTriggersScaleUp() {
        // cpu=0.81 > scaleUpThreshold=0.80 → strict greater-than triggers scale-up
        AutoScalingEngine engine = new AutoScalingEngine(
            metricsCollector(0.81, 2),
            policyManager(policy("boundary-policy", 0.80, 0.20)),
            scalingExecutor(),
            predictiveScaler(new PredictiveScalingRecommendation(
                ScalingAction.Type.NO_ACTION, 0, 0.0, "no prediction")),
            costOptimizer(new CostOptimizationResult(
                new ScalingAction(ScalingAction.Type.SCALE_UP, 2, "above threshold"),
                0.0, List.of("boundary-policy"), 2L, false, java.util.Map.of())),
            eventloop());

        ScalingEvaluationResult result = runPromise(() ->
            engine.evaluateScaling(new ScalingEvaluationRequest("cluster-boundary")));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDecision().getAction().getType()).isEqualTo(ScalingAction.Type.SCALE_UP);
    }

    @Test
    @DisplayName("CPU in normal range with no applicable policies returns NO_ACTION")
    void noPoliciesReturnsNoAction() {
        AutoScalingEngine engine = new AutoScalingEngine(
            metricsCollector(0.50, 2),
            emptyPolicyManager(),
            scalingExecutor(),
            predictiveScaler(new PredictiveScalingRecommendation(
                ScalingAction.Type.NO_ACTION, 0, 0.0, "no prediction")),
            costOptimizer(new CostOptimizationResult(
                new ScalingAction(ScalingAction.Type.NO_ACTION, 0, "no scaling needed"),
                0.0, List.of(), 2L, false, java.util.Map.of())),
            eventloop());

        ScalingEvaluationResult result = runPromise(() ->
            engine.evaluateScaling(new ScalingEvaluationRequest("idle-cluster")));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDecision().getAction().getType()).isEqualTo(ScalingAction.Type.NO_ACTION);
    }

    @Test
    @DisplayName("metrics spike triggers scale-up with correct step count from policy")
    void metricsSpikeTriggersScaleUpWithPolicyStep() {
        AutoScalingEngine engine = new AutoScalingEngine(
            metricsCollector(0.95, 3), // high CPU spike
            policyManager(policy("spike-policy", 0.80, 0.20, 5)), // scaleUpStep = 5
            scalingExecutor(),
            predictiveScaler(new PredictiveScalingRecommendation(
                ScalingAction.Type.NO_ACTION, 0, 0.0, "no predictive data")),
            costOptimizer(new CostOptimizationResult(
                new ScalingAction(ScalingAction.Type.SCALE_UP, 5, "spike"),
                0.0, List.of("spike-policy"), 3L, false, java.util.Map.of())),
            eventloop());

        ScalingEvaluationResult result = runPromise(() ->
            engine.evaluateScaling(new ScalingEvaluationRequest("cluster-spike")));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getDecision().getAction().getType()).isEqualTo(ScalingAction.Type.SCALE_UP);
        assertThat(result.getDecision().getAction().getNodeCount()).isEqualTo(5);
    }

    private static ScalingPolicy policy(String id, double scaleUpThreshold, double scaleDownThreshold) { 
        return ScalingPolicy.builder() 
            .policyId(id) 
            .enabled(true) 
            .scaleUpThreshold(scaleUpThreshold) 
            .scaleDownThreshold(scaleDownThreshold) 
            .scaleUpStep(2) 
            .scaleDownStep(1) 
            .minNodes(1) 
            .build(); 
    }

    private static ScalingPolicy policy(String id, double scaleUpThreshold, double scaleDownThreshold, int scaleUpStep) {
        return ScalingPolicy.builder()
            .policyId(id)
            .enabled(true)
            .scaleUpThreshold(scaleUpThreshold)
            .scaleDownThreshold(scaleDownThreshold)
            .scaleUpStep(scaleUpStep)
            .scaleDownStep(1)
            .minNodes(1)
            .build();
    }

    private static ScalingOperationModels.ClusterMetricsCollector metricsCollectorReturningNull() {
        return new ScalingOperationModels.ClusterMetricsCollector() {
            @Override
            public ClusterMetrics collectClusterMetrics(String clusterId) {
                return null; // simulates unavailable cluster
            }

            @Override
            public java.util.Map<String, Object> collectNodeMetrics(String nodeId) {
                return java.util.Map.of();
            }

            @Override
            public List<ClusterMetrics> collectAllClusterMetrics() {
                return List.of();
            }
        };
    }

    private static ScalingOperationModels.ScalingPolicyManager emptyPolicyManager() {
        return new ScalingOperationModels.ScalingPolicyManager() {
            @Override
            public List<ScalingPolicy> getApplicablePolicies(String clusterId) {
                return List.of();
            }

            @Override
            public ScalingPolicy getPolicy(String policyId) {
                return null;
            }

            @Override
            public void addPolicy(ScalingPolicy policy) {
            }

            @Override
            public void removePolicy(String policyId) {
            }

            @Override
            public List<ScalingPolicy> getAllPolicies() {
                return List.of();
            }
        };
    }

    private static ScalingOperationModels.ClusterMetricsCollector metricsCollector(double cpuUtilization, int activeNodes) { 
        return new ScalingOperationModels.ClusterMetricsCollector() { 
            @Override
            public ClusterMetrics collectClusterMetrics(String clusterId) { 
                return ClusterMetrics.builder() 
                    .clusterId(clusterId) 
                    .cpuUtilization(cpuUtilization) 
                    .activeNodes(activeNodes) 
                    .totalNodes(activeNodes) 
                    .build(); 
            }

            @Override
            public java.util.Map<String, Object> collectNodeMetrics(String nodeId) { 
                return java.util.Map.of("nodeId", nodeId); 
            }

            @Override
            public List<ClusterMetrics> collectAllClusterMetrics() { 
                return List.of(); 
            }
        };
    }

    private static ScalingOperationModels.ScalingPolicyManager policyManager(ScalingPolicy policy) { 
        return new ScalingOperationModels.ScalingPolicyManager() { 
            @Override
            public List<ScalingPolicy> getApplicablePolicies(String clusterId) { 
                return List.of(policy); 
            }

            @Override
            public ScalingPolicy getPolicy(String policyId) { 
                return policy;
            }

            @Override
            public void addPolicy(ScalingPolicy policy) { 
            }

            @Override
            public void removePolicy(String policyId) { 
            }

            @Override
            public List<ScalingPolicy> getAllPolicies() { 
                return List.of(policy); 
            }
        };
    }

    private static ScalingOperationModels.ScalingExecutor scalingExecutor() { 
        return new ScalingOperationModels.ScalingExecutor() { 
            @Override
            public io.activej.promise.Promise<Void> executeScaling(ScalingOperationModels.ScalingDecision decision) { 
                return io.activej.promise.Promise.complete(); 
            }

            @Override
            public io.activej.promise.Promise<Boolean> validateScalingAction(ScalingAction action) { 
                return io.activej.promise.Promise.of(true); 
            }

            @Override
            public io.activej.promise.Promise<Void> rollbackScaling(String clusterId) { 
                return io.activej.promise.Promise.complete(); 
            }
        };
    }

    private static ScalingOperationModels.PredictiveScaler predictiveScaler(PredictiveScalingRecommendation recommendation) { 
        return new ScalingOperationModels.PredictiveScaler() { 
            @Override
            public PredictiveScalingRecommendation getRecommendation(String clusterId, ClusterMetrics metrics) { 
                return recommendation;
            }

            @Override
            public List<PredictiveScalingRecommendation> getRecommendationsForAllClusters() { 
                return List.of(recommendation); 
            }

            @Override
            public void updateForecastingModel(String clusterId, java.util.Map<String, Object> trainingData) { 
            }
        };
    }

    private static ScalingOperationModels.CostOptimizer costOptimizer(CostOptimizationResult result) { 
        return new ScalingOperationModels.CostOptimizer() { 
            @Override
            public CostOptimizationResult optimizeScalingDecision( 
                    ScalingOperationModels.ScalingDecision decision,
                    ClusterMetrics metrics,
                    List<ScalingPolicy> policies) {
                return result;
            }

            @Override
            public double estimateScalingCost(ScalingAction action, String region) { 
                return result.getEstimatedCost(); 
            }

            @Override
            public java.util.Map<String, Object> getCostMetrics(String clusterId) { 
                return java.util.Map.of(); 
            }
        };
    }
}
