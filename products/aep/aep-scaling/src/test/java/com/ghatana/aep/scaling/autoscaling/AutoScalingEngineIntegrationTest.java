/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
    void highConfidencePredictionDrivesScaleUpRecommendation() { // GH-90000
        AutoScalingEngine engine = new AutoScalingEngine( // GH-90000
            metricsCollector(0.25, 2), // GH-90000
            policyManager(policy("scale-policy", 0.80, 0.20)), // GH-90000
            scalingExecutor(), // GH-90000
            predictiveScaler(new PredictiveScalingRecommendation( // GH-90000
                ScalingAction.Type.SCALE_UP, 3, 0.95, "traffic spike expected")),
            costOptimizer(new CostOptimizationResult( // GH-90000
                new ScalingAction(ScalingAction.Type.SCALE_UP, 3, "prediction chosen"), // GH-90000
                0.0,
                List.of("prediction chosen"),
                2L,
                false,
                java.util.Map.of())), // GH-90000
            eventloop()); // GH-90000

        ScalingEvaluationResult result = runPromise(() -> // GH-90000
            engine.evaluateScaling(new ScalingEvaluationRequest("cluster-a")));

        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.getDecision().getAction().getType()).isEqualTo(ScalingAction.Type.SCALE_UP); // GH-90000
        assertThat(result.getDecision().getAction().getNodeCount()).isEqualTo(3); // GH-90000
        assertThat(result.getAppliedPolicies()).containsExactly("scale-policy");
    }

    @Test
    @DisplayName("policy thresholds drive scale-down when prediction confidence is low")
    void policyThresholdsDriveScaleDownWhenPredictionConfidenceIsLow() { // GH-90000
        AutoScalingEngine engine = new AutoScalingEngine( // GH-90000
            metricsCollector(0.10, 4), // GH-90000
            policyManager(policy("scale-policy", 0.80, 0.20)), // GH-90000
            scalingExecutor(), // GH-90000
            predictiveScaler(new PredictiveScalingRecommendation( // GH-90000
                ScalingAction.Type.SCALE_UP, 10, 0.20, "low confidence prediction should be ignored")),
            costOptimizer(new CostOptimizationResult( // GH-90000
                new ScalingAction(ScalingAction.Type.SCALE_DOWN, 1, "scale down reduces idle cost"), // GH-90000
                42.0,
                List.of("scale down reduces idle cost"),
                4L,
                true,
                java.util.Map.of())), // GH-90000
            eventloop()); // GH-90000

        ScalingEvaluationResult result = runPromise(() -> // GH-90000
            engine.evaluateScaling(new ScalingEvaluationRequest("cluster-b")));

        assertThat(result.isSuccess()).isTrue(); // GH-90000
        assertThat(result.getDecision().getAction().getType()).isEqualTo(ScalingAction.Type.SCALE_DOWN); // GH-90000
        assertThat(result.getDecision().getAction().getNodeCount()).isEqualTo(1); // GH-90000
        assertThat(result.getCostOptimization().isOptimizationApplied()).isTrue(); // GH-90000
    }

    private static ScalingPolicy policy(String id, double scaleUpThreshold, double scaleDownThreshold) { // GH-90000
        return ScalingPolicy.builder() // GH-90000
            .policyId(id) // GH-90000
            .enabled(true) // GH-90000
            .scaleUpThreshold(scaleUpThreshold) // GH-90000
            .scaleDownThreshold(scaleDownThreshold) // GH-90000
            .scaleUpStep(2) // GH-90000
            .scaleDownStep(1) // GH-90000
            .minNodes(1) // GH-90000
            .build(); // GH-90000
    }

    private static ScalingOperationModels.ClusterMetricsCollector metricsCollector(double cpuUtilization, int activeNodes) { // GH-90000
        return new ScalingOperationModels.ClusterMetricsCollector() { // GH-90000
            @Override
            public ClusterMetrics collectClusterMetrics(String clusterId) { // GH-90000
                return ClusterMetrics.builder() // GH-90000
                    .clusterId(clusterId) // GH-90000
                    .cpuUtilization(cpuUtilization) // GH-90000
                    .activeNodes(activeNodes) // GH-90000
                    .totalNodes(activeNodes) // GH-90000
                    .build(); // GH-90000
            }

            @Override
            public java.util.Map<String, Object> collectNodeMetrics(String nodeId) { // GH-90000
                return java.util.Map.of("nodeId", nodeId); // GH-90000
            }

            @Override
            public List<ClusterMetrics> collectAllClusterMetrics() { // GH-90000
                return List.of(); // GH-90000
            }
        };
    }

    private static ScalingOperationModels.ScalingPolicyManager policyManager(ScalingPolicy policy) { // GH-90000
        return new ScalingOperationModels.ScalingPolicyManager() { // GH-90000
            @Override
            public List<ScalingPolicy> getApplicablePolicies(String clusterId) { // GH-90000
                return List.of(policy); // GH-90000
            }

            @Override
            public ScalingPolicy getPolicy(String policyId) { // GH-90000
                return policy;
            }

            @Override
            public void addPolicy(ScalingPolicy policy) { // GH-90000
            }

            @Override
            public void removePolicy(String policyId) { // GH-90000
            }

            @Override
            public List<ScalingPolicy> getAllPolicies() { // GH-90000
                return List.of(policy); // GH-90000
            }
        };
    }

    private static ScalingOperationModels.ScalingExecutor scalingExecutor() { // GH-90000
        return new ScalingOperationModels.ScalingExecutor() { // GH-90000
            @Override
            public io.activej.promise.Promise<Void> executeScaling(ScalingOperationModels.ScalingDecision decision) { // GH-90000
                return io.activej.promise.Promise.complete(); // GH-90000
            }

            @Override
            public io.activej.promise.Promise<Boolean> validateScalingAction(ScalingAction action) { // GH-90000
                return io.activej.promise.Promise.of(true); // GH-90000
            }

            @Override
            public io.activej.promise.Promise<Void> rollbackScaling(String clusterId) { // GH-90000
                return io.activej.promise.Promise.complete(); // GH-90000
            }
        };
    }

    private static ScalingOperationModels.PredictiveScaler predictiveScaler(PredictiveScalingRecommendation recommendation) { // GH-90000
        return new ScalingOperationModels.PredictiveScaler() { // GH-90000
            @Override
            public PredictiveScalingRecommendation getRecommendation(String clusterId, ClusterMetrics metrics) { // GH-90000
                return recommendation;
            }

            @Override
            public List<PredictiveScalingRecommendation> getRecommendationsForAllClusters() { // GH-90000
                return List.of(recommendation); // GH-90000
            }

            @Override
            public void updateForecastingModel(String clusterId, java.util.Map<String, Object> trainingData) { // GH-90000
            }
        };
    }

    private static ScalingOperationModels.CostOptimizer costOptimizer(CostOptimizationResult result) { // GH-90000
        return new ScalingOperationModels.CostOptimizer() { // GH-90000
            @Override
            public CostOptimizationResult optimizeScalingDecision( // GH-90000
                    ScalingOperationModels.ScalingDecision decision,
                    ClusterMetrics metrics,
                    List<ScalingPolicy> policies) {
                return result;
            }

            @Override
            public double estimateScalingCost(ScalingAction action, String region) { // GH-90000
                return result.getEstimatedCost(); // GH-90000
            }

            @Override
            public java.util.Map<String, Object> getCostMetrics(String clusterId) { // GH-90000
                return java.util.Map.of(); // GH-90000
            }
        };
    }
}
