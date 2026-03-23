/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.scaling.autoscaling;

import com.ghatana.aep.scaling.models.AutoScalingModels;
import com.ghatana.aep.scaling.cluster.ClusterManagementModels.ClusterMetrics;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.CostOptimizationResult;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.PredictiveScalingRecommendation;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingAction;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingEvaluationRequest;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingEvaluationResult;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.MetricsCollector;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingPolicyManager;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingExecutor;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.PredictiveScaler;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.CostOptimizer;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AutoScalingEngine}.
 *
 * <p>Uses {@link EventloopTestBase} to drive async promise resolution. The
 * engine's internal eventloop is injected via the package-private constructor
 * so that {@code Promise.ofBlocking(eventloop, ...)} callbacks are processed
 * by the test-managed eventloop thread.
 *
 * <p>All infrastructure dependencies ({@link MetricsCollector}, {@link ScalingPolicyManager},
 * {@link ScalingExecutor}, {@link PredictiveScaler}, {@link CostOptimizer}) are mocked with
 * Mockito so tests are deterministic and in-process.
 *
 * @doc.type class
 * @doc.purpose Unit tests for AutoScalingEngine — evaluation, metrics, and lifecycle
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AutoScalingEngine")
class AutoScalingEngineTest extends EventloopTestBase {

    private static final String CLUSTER_ID = "cluster-test";

    @Mock MetricsCollector metricsCollector;
    @Mock ScalingPolicyManager policyManager;
    @Mock ScalingExecutor scalingExecutor;
    @Mock PredictiveScaler predictiveScaler;
    @Mock CostOptimizer costOptimizer;

    private AutoScalingEngine engine;

    @BeforeEach
    void setUpEngine() {
        engine = new AutoScalingEngine(
                metricsCollector, policyManager, scalingExecutor,
                predictiveScaler, costOptimizer,
                eventloop()    // inject test eventloop
        );
    }

    // =========================================================================
    // evaluateScaling
    // =========================================================================

    @Nested
    @DisplayName("evaluateScaling()")
    class EvaluateScalingTests {

        @BeforeEach
        void stubMocks() {
            ClusterMetrics metrics = new ClusterMetrics(CLUSTER_ID, 5, 5, 40.0, 50.0, 100);

            lenient().when(metricsCollector.collectClusterMetrics(CLUSTER_ID)).thenReturn(metrics);
            lenient().when(policyManager.getApplicablePolicies(CLUSTER_ID)).thenReturn(List.of());

            PredictiveScalingRecommendation pred = new PredictiveScalingRecommendation(
                    ScalingAction.Type.NO_ACTION, 0, 0.9, "Stable workload");
            lenient().when(predictiveScaler.getRecommendation(eq(CLUSTER_ID), any())).thenReturn(pred);

            CostOptimizationResult costResult = new CostOptimizationResult(
                    null, null, List.of(), 5L, true, null);
            lenient().when(costOptimizer.optimizeScalingDecision(any(), any(), any())).thenReturn(costResult);
        }

        @Test
        @DisplayName("evaluateScaling: returns success result for a valid cluster request")
        void evaluateScaling_validRequest_returnsSuccess() {
            ScalingEvaluationRequest req = new ScalingEvaluationRequest(CLUSTER_ID);

            ScalingEvaluationResult result = runPromise(() -> engine.evaluateScaling(req));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getDecision()).isNotNull();
            assertThat(result.getDecision().getClusterId()).isEqualTo(CLUSTER_ID);
        }

        @Test
        @DisplayName("evaluateScaling: collects cluster metrics and queries applicable policies")
        void evaluateScaling_invokesMetricsAndPolicyDependencies() {
            ScalingEvaluationRequest req = new ScalingEvaluationRequest(CLUSTER_ID);

            runPromise(() -> engine.evaluateScaling(req));

            verify(metricsCollector).collectClusterMetrics(CLUSTER_ID);
            verify(policyManager).getApplicablePolicies(CLUSTER_ID);
        }

        @Test
        @DisplayName("evaluateScaling: queries predictive scaler with current metrics")
        void evaluateScaling_invokesPredicativeScaler() {
            runPromise(() -> engine.evaluateScaling(new ScalingEvaluationRequest(CLUSTER_ID)));

            verify(predictiveScaler).getRecommendation(eq(CLUSTER_ID), any(ClusterMetrics.class));
        }

        @Test
        @DisplayName("evaluateScaling: requests cost optimization for the computed action")
        void evaluateScaling_invokesCostOptimizer() {
            runPromise(() -> engine.evaluateScaling(new ScalingEvaluationRequest(CLUSTER_ID)));

            verify(costOptimizer).optimizeScalingDecision(any(), any(), any());
        }

        @Test
        @DisplayName("evaluateScaling: returns failure result when MetricsCollector throws")
        void evaluateScaling_whenMetricsCollectorThrows_returnsFailure() {
            when(metricsCollector.collectClusterMetrics(CLUSTER_ID))
                    .thenThrow(new RuntimeException("metrics unavailable"));

            ScalingEvaluationResult result = runPromise(() ->
                    engine.evaluateScaling(new ScalingEvaluationRequest(CLUSTER_ID))
            );

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).isNotBlank();
        }

        @Test
        @DisplayName("evaluateScaling: evaluation time is non-negative")
        void evaluateScaling_evaluationTimeNonNegative() {
            ScalingEvaluationResult result = runPromise(() ->
                    engine.evaluateScaling(new ScalingEvaluationRequest(CLUSTER_ID))
            );

            assertThat(result.getEvaluationTime()).isGreaterThanOrEqualTo(0L);
        }
    }

    // =========================================================================
    // getMetrics (synchronous)
    // =========================================================================

    @Test
    @DisplayName("getMetrics: returns a non-null map with expected keys")
    void getMetrics_returnsExpectedStructure() {
        Map<String, Object> metrics = engine.getMetrics();

        assertThat(metrics).isNotNull().isNotEmpty();
        assertThat(metrics).containsKey("totalScalingEvents");
        assertThat(metrics).containsKey("successfulScalingEvents");
        assertThat(metrics).containsKey("successRate");
        assertThat(metrics).containsKey("activeScalingStates");
    }

    @Test
    @DisplayName("getMetrics: initial state has zero scaling events")
    void getMetrics_initialState_zeroEvents() {
        Map<String, Object> metrics = engine.getMetrics();
        assertThat(((Number) metrics.get("totalScalingEvents")).longValue()).isZero();
        assertThat(((Number) metrics.get("successfulScalingEvents")).longValue()).isZero();
    }

    // =========================================================================
    // shutdown
    // =========================================================================

    @Test
    @DisplayName("shutdown: completes without error")
    void shutdown_completesCleanly() {
        runPromise(() -> engine.shutdown());
        // verify no exception — implicit pass
    }
}
