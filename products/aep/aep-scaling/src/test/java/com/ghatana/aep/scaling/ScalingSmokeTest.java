package com.ghatana.aep.scaling;

import com.ghatana.aep.scaling.autoscaling.DefaultCostOptimizer;
import com.ghatana.aep.scaling.autoscaling.DefaultMetricsCollector;
import com.ghatana.aep.scaling.autoscaling.DefaultScalingExecutor;
import com.ghatana.aep.scaling.autoscaling.DefaultScalingPolicyManager;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingAction;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingDecision;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingEvaluationRequest;
import com.ghatana.aep.scaling.autoscaling.ScalingOperationModels.ScalingPolicy;
import com.ghatana.aep.scaling.cluster.ClusterManagementModels.ClusterMetrics;
import com.ghatana.aep.scaling.cluster.ClusterManagementModels.NodeRegistrationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for AEP Platform Scaling module.
 * Tests stateless services and Lombok model classes.
 *
 * @doc.type class
 * @doc.purpose Smoke tests for scaling services and models
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Platform Scaling — Smoke Tests")
class ScalingSmokeTest {

    // ── Stateless Service Tests ──────────────────────────────────────────

    @Test
    @DisplayName("DefaultMetricsCollector can be instantiated and returns metrics")
    void defaultMetricsCollectorWorks() { // GH-90000
        var collector = new DefaultMetricsCollector(); // GH-90000
        assertNotNull(collector); // GH-90000
        var metrics = collector.collectClusterMetrics("cluster-1");
        assertNotNull(metrics); // GH-90000
        assertEquals("cluster-1", metrics.getClusterId()); // GH-90000
    }

    @Test
    @DisplayName("DefaultScalingPolicyManager can be instantiated")
    void defaultScalingPolicyManagerWorks() { // GH-90000
        var manager = new DefaultScalingPolicyManager(); // GH-90000
        assertNotNull(manager); // GH-90000
    }

    @Test
    @DisplayName("DefaultCostOptimizer can be instantiated")
    void defaultCostOptimizerWorks() { // GH-90000
        var optimizer = new DefaultCostOptimizer(); // GH-90000
        assertNotNull(optimizer); // GH-90000
    }

    @Test
    @DisplayName("DefaultScalingExecutor can be instantiated")
    void defaultScalingExecutorWorks() { // GH-90000
        var executor = new DefaultScalingExecutor(); // GH-90000
        assertNotNull(executor); // GH-90000
    }

    // ── Model Tests ──────────────────────────────────────────────────────

    @Test
    @DisplayName("ScalingEvaluationRequest can be constructed")
    void scalingEvaluationRequestFields() { // GH-90000
        var request = new ScalingEvaluationRequest("cluster-1");
        assertEquals("cluster-1", request.getClusterId()); // GH-90000
        assertNotNull(request.getEvaluationTime()); // GH-90000
    }

    @Test
    @DisplayName("ScalingDecision can be constructed")
    void scalingDecisionFields() { // GH-90000
        var action = new ScalingAction(); // GH-90000
        action.setType(ScalingAction.Type.SCALE_UP); // GH-90000
        action.setNodeCount(3); // GH-90000
        action.setReason("High CPU");

        var decision = new ScalingDecision("cluster-1", action); // GH-90000
        assertEquals("cluster-1", decision.getClusterId()); // GH-90000
        assertEquals(ScalingAction.Type.SCALE_UP, decision.getAction().getType()); // GH-90000
    }

    @Test
    @DisplayName("ScalingPolicy can be built")
    void scalingPolicyBuilder() { // GH-90000
        var policy = ScalingPolicy.builder() // GH-90000
                .policyId("policy-1")
                .name("CPU Scale Up")
                .description("Scale up when CPU > 80%")
                .scaleUpThreshold(0.8) // GH-90000
                .build(); // GH-90000
        assertEquals("policy-1", policy.getPolicyId()); // GH-90000
        assertEquals(0.8, policy.getScaleUpThreshold()); // GH-90000
    }

    @Test
    @DisplayName("ClusterMetrics can be built")
    void clusterMetricsBuilder() { // GH-90000
        var metrics = ClusterMetrics.builder() // GH-90000
                .clusterId("cluster-1")
                .activeNodes(3) // GH-90000
                .totalNodes(5) // GH-90000
                .build(); // GH-90000
        assertEquals("cluster-1", metrics.getClusterId()); // GH-90000
        assertEquals(3, metrics.getActiveNodes()); // GH-90000
    }

    @Test
    @DisplayName("NodeRegistrationRequest can be built")
    void nodeRegistrationRequestBuilder() { // GH-90000
        var request = NodeRegistrationRequest.builder() // GH-90000
                .nodeId("node-1")
                .clusterId("cluster-1")
                .metadata(Map.of("region", "us-east-1")) // GH-90000
                .build(); // GH-90000
        assertEquals("node-1", request.getNodeId()); // GH-90000
        assertEquals("cluster-1", request.getClusterId()); // GH-90000
    }
}
