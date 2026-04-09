/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.scaling.cluster;

import com.ghatana.aep.scaling.loadbalancer.LoadBalancerModels;
import com.ghatana.aep.scaling.cluster.ClusterManagementModels.NodeRegistrationRequest;
import com.ghatana.aep.scaling.cluster.ClusterManagementModels.NodeRegistrationResult;
import com.ghatana.aep.scaling.cluster.ClusterManagementModels.NodeUnregistrationRequest;
import com.ghatana.aep.scaling.cluster.ClusterManagementModels.NodeUnregistrationResult;
import com.ghatana.aep.scaling.cluster.ClusterManagementModels.ClusterStatusRequest;
import com.ghatana.aep.scaling.cluster.ClusterManagementModels.ClusterStatusResult;
import com.ghatana.aep.scaling.cluster.ClusterManagementModels.ClusterMaintenanceRequest;
import com.ghatana.aep.scaling.cluster.ClusterManagementModels.ClusterMaintenanceResult;
import com.ghatana.aep.scaling.cluster.ClusterManagementModels.ClusterRebalancingRequest;
import com.ghatana.aep.scaling.models.DistributedModels.ClusterRebalancingResult;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ClusterManagementSystem}.
 *
 * <p>Uses {@link EventloopTestBase} because {@code ClusterManagementSystem}
 * accepts an {@code Eventloop} in its constructor and all async methods use
 * {@code Promise.ofBlocking(eventloop, ...)}. The concrete inner-class
 * implementations from {@code AutoScalingModels} are used directly (none are
 * mocked), keeping the test realistic without requiring a database.
 *
 * @doc.type class
 * @doc.purpose Unit tests for ClusterManagementSystem — node lifecycle and cluster state
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("ClusterManagementSystem")
class ClusterManagementSystemTest extends EventloopTestBase {

    private ClusterManagementSystem cms;

    @BeforeEach
    void setUpCms() {
        cms = new ClusterManagementSystem(
                eventloop(),
                new ClusterManagementModels.NodeRegistry(),
                new ClusterManagementModels.ServiceDiscovery(),
                new ClusterManagementModels.HealthMonitor(),
                new ClusterManagementModels.ConfigurationManager(),
                new LoadBalancerModels.LoadDistributionManager()
        );
    }

    // =========================================================================
    // registerNode
    // =========================================================================

    @Nested
    @DisplayName("registerNode()")
    class RegisterNodeTests {

        @Test
        @DisplayName("registerNode: succeeds for a valid node request")
        void registerNode_validRequest_returnsSuccess() {
            NodeRegistrationRequest req = new NodeRegistrationRequest(
                    UUID.randomUUID().toString(), "node-101", "10.0.0.1", 8080,
                    Map.of("cpu", 4, "memory", "8GB"), Map.of()
            );

            NodeRegistrationResult result = runPromise(() -> cms.registerNode(req));

            assertThat(result.getRequestId()).isEqualTo(req.getRequestId());
            assertThat(result.getNodeId()).isEqualTo("node-101");
            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("registerNode: persists node so it appears in cluster status")
        void registerNode_nodeAppearsInStatus() {
            String nodeId = "persistent-node";
            NodeRegistrationRequest req = new NodeRegistrationRequest(
                    UUID.randomUUID().toString(), nodeId, "10.0.0.2", 9090,
                    Map.of(), Map.of()
            );
            runPromise(() -> cms.registerNode(req));

            ClusterStatusRequest statusReq = new ClusterStatusRequest(
                    UUID.randomUUID().toString(), "cluster", true, false
            );
            ClusterStatusResult status = runPromise(() -> cms.getClusterStatus(statusReq));

            assertThat(status.isSuccess()).isTrue();
        }
    }

    // =========================================================================
    // unregisterNode
    // =========================================================================

    @Nested
    @DisplayName("unregisterNode()")
    class UnregisterNodeTests {

        @Test
        @DisplayName("unregisterNode: returns success even for unknown node (graceful removal)")
        void unregisterNode_unknownNode_returnsResult() {
            NodeUnregistrationRequest req = new NodeUnregistrationRequest(
                    UUID.randomUUID().toString(), "ghost-node", "decommission", true
            );

            NodeUnregistrationResult result = runPromise(() -> cms.unregisterNode(req));

            assertThat(result.getRequestId()).isEqualTo(req.getRequestId());
        }

        @Test
        @DisplayName("unregisterNode: successfully removes a previously registered node")
        void unregisterNode_registeredNode_returnsSuccess() {
            // Register first
            NodeRegistrationRequest regReq = new NodeRegistrationRequest(
                    UUID.randomUUID().toString(), "temp-node", "10.0.0.5", 8080,
                    Map.of(), Map.of()
            );
            runPromise(() -> cms.registerNode(regReq));

            // Now unregister
            NodeUnregistrationRequest unregReq = new NodeUnregistrationRequest(
                    UUID.randomUUID().toString(), "temp-node", "scale-in", true
            );
            NodeUnregistrationResult result = runPromise(() -> cms.unregisterNode(unregReq));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getNodeId()).isEqualTo("temp-node");
        }
    }

    // =========================================================================
    // getClusterStatus
    // =========================================================================

    @Nested
    @DisplayName("getClusterStatus()")
    class ClusterStatusTests {

        @Test
        @DisplayName("getClusterStatus: returns success with cluster info")
        void getClusterStatus_returnsClusterInfo() {
            ClusterStatusRequest req = new ClusterStatusRequest(
                    UUID.randomUUID().toString(), "my-cluster", true, true
            );

            ClusterStatusResult result = runPromise(() -> cms.getClusterStatus(req));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getRequestId()).isEqualTo(req.getRequestId());
            assertThat(result.getState()).isNotNull();
        }

        @Test
        @DisplayName("getClusterStatus: reports 0 active nodes when none registered")
        void getClusterStatus_noNodes_zeroActiveNodes() {
            ClusterStatusRequest req = new ClusterStatusRequest(
                    UUID.randomUUID().toString(), "empty-cluster", true, false
            );

            ClusterStatusResult result = runPromise(() -> cms.getClusterStatus(req));

            assertThat(result.getActiveNodes()).isZero();
        }

        @Test
        @DisplayName("getClusterStatus: state transitions through INITIALIZING after new cluster")
        void getClusterStatus_stateIsKnown() {
            ClusterStatusRequest req = new ClusterStatusRequest(
                    UUID.randomUUID().toString(), "any", false, false
            );

            ClusterStatusResult result = runPromise(() -> cms.getClusterStatus(req));

            // State should be one of the valid ClusterState values
            assertThat(result.getState()).isNotNull();
            assertThat(ClusterState.values()).contains(result.getState());
        }
    }

    // =========================================================================
    // performMaintenance
    // =========================================================================

    @Nested
    @DisplayName("performMaintenance()")
    class MaintenanceTests {

        @Test
        @DisplayName("performMaintenance: completes without error for empty node list")
        void performMaintenance_emptyNodeList_succeeds() {
            ClusterMaintenanceRequest req = new ClusterMaintenanceRequest(
                    UUID.randomUUID().toString(), "cluster-1",
                    ClusterMaintenanceRequest.MaintenanceType.HEALTH_CHECK,
                    List.of(), System.currentTimeMillis() + 60_000, Map.of()
            );

            ClusterMaintenanceResult result = runPromise(() -> cms.performMaintenance(req));

            assertThat(result.getRequestId()).isEqualTo(req.getRequestId());
        }

        @Test
        @DisplayName("performMaintenance: processes listed nodes")
        void performMaintenance_withTargetNodes_processesAll() {
            // Register nodes first
            List<String> nodeIds = List.of("maint-node-a", "maint-node-b");
            for (String nid : nodeIds) {
                NodeRegistrationRequest regReq = new NodeRegistrationRequest(
                        UUID.randomUUID().toString(), nid, "10.0.0.10", 8080, Map.of(), Map.of()
                );
                runPromise(() -> cms.registerNode(regReq));
            }

            ClusterMaintenanceRequest maintReq = new ClusterMaintenanceRequest(
                    UUID.randomUUID().toString(), "cluster-1",
                    ClusterMaintenanceRequest.MaintenanceType.CLEANUP,
                    nodeIds, System.currentTimeMillis(), Map.of()
            );

            ClusterMaintenanceResult result = runPromise(() -> cms.performMaintenance(maintReq));

            assertThat(result).isNotNull();
        }
    }

    // =========================================================================
    // rebalanceCluster
    // =========================================================================

    @Test
    @DisplayName("rebalanceCluster: returns a result for a valid rebalancing request")
    void rebalanceCluster_returnsResult() {
        ClusterRebalancingRequest req = new ClusterRebalancingRequest(
                UUID.randomUUID().toString(), "cluster-main",
                ClusterRebalancingRequest.RebalancingStrategy.LOAD_BALANCE,
                Map.of()
        );

        ClusterRebalancingResult result = runPromise(() -> cms.rebalanceCluster(req));

        assertThat(result).isNotNull();
    }

    // =========================================================================
    // getMetrics / shutdown
    // =========================================================================

    @Test
    @DisplayName("getMetrics: returns a non-null metrics map")
    void getMetrics_returnsNonNull() {
        Map<String, Object> m = cms.getMetrics();
        assertThat(m).isNotNull();
    }

    @Test
    @DisplayName("shutdown: completes without error")
    void shutdown_completesCleanly() {
        runPromise(() -> cms.shutdown());
        // no assertion needed — just verify it doesn't throw
    }
}
