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
    void defaultMetricsCollectorWorks() { 
        var collector = new DefaultMetricsCollector(); 
        assertNotNull(collector); 
        var metrics = collector.collectClusterMetrics("cluster-1");
        assertNotNull(metrics); 
        assertEquals("cluster-1", metrics.getClusterId()); 
    }

    @Test
    @DisplayName("DefaultScalingPolicyManager can be instantiated")
    void defaultScalingPolicyManagerWorks() { 
        var manager = new DefaultScalingPolicyManager(); 
        assertNotNull(manager); 
    }

    @Test
    @DisplayName("DefaultCostOptimizer can be instantiated")
    void defaultCostOptimizerWorks() { 
        var optimizer = new DefaultCostOptimizer(); 
        assertNotNull(optimizer); 
    }

    @Test
    @DisplayName("DefaultScalingExecutor can be instantiated")
    void defaultScalingExecutorWorks() { 
        var executor = new DefaultScalingExecutor(); 
        assertNotNull(executor); 
    }

    // ── Model Tests ──────────────────────────────────────────────────────

    @Test
    @DisplayName("ScalingEvaluationRequest can be constructed")
    void scalingEvaluationRequestFields() { 
        var request = new ScalingEvaluationRequest("cluster-1");
        assertEquals("cluster-1", request.getClusterId()); 
        assertNotNull(request.getEvaluationTime()); 
    }

    @Test
    @DisplayName("ScalingDecision can be constructed")
    void scalingDecisionFields() { 
        var action = new ScalingAction(); 
        action.setType(ScalingAction.Type.SCALE_UP); 
        action.setNodeCount(3); 
        action.setReason("High CPU");

        var decision = new ScalingDecision("cluster-1", action); 
        assertEquals("cluster-1", decision.getClusterId()); 
        assertEquals(ScalingAction.Type.SCALE_UP, decision.getAction().getType()); 
    }

    @Test
    @DisplayName("ScalingPolicy can be built")
    void scalingPolicyBuilder() { 
        var policy = ScalingPolicy.builder() 
                .policyId("policy-1")
                .name("CPU Scale Up")
                .description("Scale up when CPU > 80%")
                .scaleUpThreshold(0.8) 
                .build(); 
        assertEquals("policy-1", policy.getPolicyId()); 
        assertEquals(0.8, policy.getScaleUpThreshold()); 
    }

    @Test
    @DisplayName("ClusterMetrics can be built")
    void clusterMetricsBuilder() { 
        var metrics = ClusterMetrics.builder() 
                .clusterId("cluster-1")
                .activeNodes(3) 
                .totalNodes(5) 
                .build(); 
        assertEquals("cluster-1", metrics.getClusterId()); 
        assertEquals(3, metrics.getActiveNodes()); 
    }

    @Test
    @DisplayName("NodeRegistrationRequest can be built")
    void nodeRegistrationRequestBuilder() { 
        var request = NodeRegistrationRequest.builder() 
                .nodeId("node-1")
                .clusterId("cluster-1")
                .metadata(Map.of("region", "us-east-1")) 
                .build(); 
        assertEquals("node-1", request.getNodeId()); 
        assertEquals("cluster-1", request.getClusterId()); 
    }
}
