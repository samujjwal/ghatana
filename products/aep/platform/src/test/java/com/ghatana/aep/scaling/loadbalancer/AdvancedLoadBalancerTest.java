/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.scaling.loadbalancer;

import com.ghatana.aep.scaling.models.AutoScalingModels.LoadBalancingNode;
import com.ghatana.aep.scaling.models.AutoScalingModels.RoutingResult;
import com.ghatana.aep.scaling.models.AutoScalingModels.WorkloadDistributionResult;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AdvancedLoadBalancer}.
 *
 * <p>Uses {@link EventloopTestBase} because the load balancer's asynchronous
 * methods use {@code Promise.ofBlocking(eventloop, ...)}. The test provides
 * an Eventloop via its constructor and drives promise resolution through
 * {@link #runPromise(java.util.concurrent.Callable)}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for AdvancedLoadBalancer — routing, distribution, node management
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AdvancedLoadBalancer")
class AdvancedLoadBalancerTest extends EventloopTestBase {

    private AdvancedLoadBalancer lb;

    @BeforeEach
    void setUpLb() {
        lb = new AdvancedLoadBalancer(eventloop());
    }

    // =========================================================================
    // routeRequest
    // =========================================================================

    @Nested
    @DisplayName("routeRequest()")
    class RouteRequestTests {

        @Test
        @DisplayName("routeRequest: returns failure when no nodes registered")
        void routeRequest_noNodes_returnsFailure() {
            RoutingResult result = runPromise(() ->
                    lb.routeRequest("req-1", Map.of())
            );

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).isNotBlank();
            assertThat(result.getRequestId()).isEqualTo("req-1");
        }

        @Test
        @DisplayName("routeRequest: succeeds and selects a node when healthy nodes exist")
        void routeRequest_withHealthyNode_returnsSuccess() {
            LoadBalancingNode node = healthyNode("node-1", 0.2, 1.0);
            lb.addNode(node);

            RoutingResult result = runPromise(() ->
                    lb.routeRequest("req-2", Map.of())
            );

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getSelectedNodeId()).isEqualTo("node-1");
            assertThat(result.getRequestId()).isEqualTo("req-2");
        }

        @Test
        @DisplayName("routeRequest: fails when only unhealthy nodes are present")
        void routeRequest_onlyUnhealthyNodes_returnsFailure() {
            lb.addNode(unhealthyNode("sick-node"));

            RoutingResult result = runPromise(() ->
                    lb.routeRequest("req-3", Map.of())
            );

            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        @DisplayName("routeRequest: round-robin distributes across multiple healthy nodes")
        void routeRequest_multipleNodes_roundRobinRotates() {
            lb.addNode(healthyNode("node-a", 0.1, 1.0));
            lb.addNode(healthyNode("node-b", 0.1, 1.0));

            String first  = runPromise(() -> lb.routeRequest("r1", Map.of())).getSelectedNodeId();
            String second = runPromise(() -> lb.routeRequest("r2", Map.of())).getSelectedNodeId();

            // With round-robin, the two requests go to different nodes
            assertThat(first).isNotEqualTo(second);
        }

        @Test
        @DisplayName("routeRequest: includes algorithm name in result")
        void routeRequest_includesAlgorithmName() {
            lb.addNode(healthyNode("n1", 0.0, 1.0));

            RoutingResult result = runPromise(() ->
                    lb.routeRequest("req-alg", Map.of())
            );

            assertThat(result.getAlgorithm()).isNotBlank();
        }
    }

    // =========================================================================
    // distributeWorkload
    // =========================================================================

    @Nested
    @DisplayName("distributeWorkload()")
    class DistributeWorkloadTests {

        @Test
        @DisplayName("distributeWorkload: fails when no nodes available")
        void distributeWorkload_noNodes_returnsFailure() {
            WorkloadDistributionResult result = runPromise(() ->
                    lb.distributeWorkload("dist-1", 100.0, Map.of())
            );

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getAllocations()).isEmpty();
        }

        @Test
        @DisplayName("distributeWorkload: distributes across all healthy nodes")
        void distributeWorkload_withNodes_allocatesWorkload() {
            lb.addNode(healthyNode("n-alpha", 0.0, 100.0));
            lb.addNode(healthyNode("n-beta",  0.0, 100.0));

            WorkloadDistributionResult result = runPromise(() ->
                    lb.distributeWorkload("dist-2", 100.0, Map.of())
            );

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getAllocations()).isNotEmpty();
            // Total allocated should approximate total workload
            double totalAllocated = result.getAllocations().values().stream()
                    .mapToDouble(a -> a.getAllocatedWorkload())
                    .sum();
            assertThat(totalAllocated).isCloseTo(100.0, within(0.01));
        }

        @Test
        @DisplayName("distributeWorkload: returns the distribution ID in result")
        void distributeWorkload_returnsDistributionId() {
            lb.addNode(healthyNode("n1", 0.0, 10.0));

            WorkloadDistributionResult result = runPromise(() ->
                    lb.distributeWorkload("dist-id-99", 10.0, Map.of())
            );

            assertThat(result.getDistributionId()).isEqualTo("dist-id-99");
        }
    }

    // =========================================================================
    // Node management
    // =========================================================================

    @Nested
    @DisplayName("Node management")
    class NodeManagementTests {

        @Test
        @DisplayName("addNode: node is available for routing after registration")
        void addNode_nodeUsedInRouting() {
            lb.addNode(healthyNode("fresh-node", 0.0, 10.0));

            RoutingResult result = runPromise(() ->
                    lb.routeRequest("test", Map.of())
            );

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getSelectedNodeId()).isEqualTo("fresh-node");
        }

        @Test
        @DisplayName("removeNode: removed node is no longer selected for routing")
        void removeNode_nodeNotUsedAfterRemoval() {
            lb.addNode(healthyNode("to-remove", 0.0, 10.0));
            lb.addNode(healthyNode("to-keep",   0.0, 10.0));

            lb.removeNode("to-remove");

            // Multiple requests should never land on the removed node
            for (int i = 0; i < 5; i++) {
                int req = i;
                RoutingResult r = runPromise(() -> lb.routeRequest("r-" + req, Map.of()));
                assertThat(r.getSelectedNodeId()).isNotEqualTo("to-remove");
            }
        }
    }

    // =========================================================================
    // getMetrics
    // =========================================================================

    @Test
    @DisplayName("getMetrics: returns a non-null metrics map")
    void getMetrics_returnsNonNull() {
        Map<String, Object> metrics = lb.getMetrics();
        assertThat(metrics).isNotNull();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static LoadBalancingNode healthyNode(String id, double load, double capacity) {
        return new LoadBalancingNode(id, "127.0.0.1", 8080, load, capacity, true, Map.of());
    }

    private static LoadBalancingNode unhealthyNode(String id) {
        return new LoadBalancingNode(id, "127.0.0.1", 9090, 0.0, 1.0, false, Map.of());
    }
}
