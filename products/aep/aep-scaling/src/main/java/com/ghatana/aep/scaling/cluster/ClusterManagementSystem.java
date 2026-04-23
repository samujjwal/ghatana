/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.scaling.cluster;

import com.ghatana.aep.scaling.models.DistributedModels;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.ghatana.aep.scaling.cluster.ClusterManagementModels.*;

/**
 * Manages cluster node lifecycle, health monitoring, and rebalancing.
  * @doc.type class
 * @doc.purpose Provides cluster management system functionality.
 * @doc.layer product
 * @doc.pattern Component
*/
@Slf4j
public class ClusterManagementSystem {

    private final Eventloop eventloop;
    private final Map<String, ClusterState> clusters;
    private final Map<String, NodeState> nodes;

    public ClusterManagementSystem(Eventloop eventloop) {
        this.eventloop = eventloop;
        this.clusters = new ConcurrentHashMap<>();
        this.nodes = new ConcurrentHashMap<>();
    }

    // ==================== Node Registration ====================

    public Promise<NodeRegistrationResult> registerNode(NodeRegistrationRequest request) {
        return Promise.ofBlocking(eventloop, () -> {
            try {
                String nodeId = request.getNodeId();
                if (nodes.containsKey(nodeId)) {
                    return new NodeRegistrationResult(false, nodeId, "Node already registered");
                }

                NodeState state = new NodeState(nodeId, request.getClusterId(), request.getHostname());
                nodes.put(nodeId, state);

                ClusterState cluster = clusters.computeIfAbsent(
                    request.getClusterId(),
                    id -> new ClusterState(id)
                );
                cluster.addNode(nodeId);

                log.info("Node {} registered to cluster {}", nodeId, request.getClusterId());
                return new NodeRegistrationResult(true, nodeId, null);
            } catch (Exception e) {
                log.error("Failed to register node", e);
                return new NodeRegistrationResult(false, request.getNodeId(), e.getMessage());
            }
        });
    }

    public Promise<NodeUnregistrationResult> unregisterNode(NodeUnregistrationRequest request) {
        return Promise.ofBlocking(eventloop, () -> {
            try {
                String nodeId = request.getNodeId();
                NodeState node = nodes.remove(nodeId);
                if (node == null) {
                    return new NodeUnregistrationResult(false, nodeId, "Node not found");
                }

                ClusterState cluster = clusters.get(request.getClusterId());
                if (cluster != null) {
                    cluster.removeNode(nodeId);
                }

                log.info("Node {} unregistered from cluster {}", nodeId, request.getClusterId());
                return new NodeUnregistrationResult(true, nodeId, null);
            } catch (Exception e) {
                log.error("Failed to unregister node", e);
                return new NodeUnregistrationResult(false, request.getNodeId(), e.getMessage());
            }
        });
    }

    // ==================== Cluster Status ====================

    public Promise<ClusterStatusResult> getClusterStatus(ClusterStatusRequest request) {
        return Promise.ofBlocking(eventloop, () -> {
            ClusterState cluster = clusters.get(request.getClusterId());
            if (cluster == null) {
                return ClusterStatusResult.builder()
                    .clusterId(request.getClusterId())
                    .status(ClusterStatusResult.ClusterStatus.UNAVAILABLE)
                    .totalNodes(0)
                    .healthyNodes(0)
                    .timestamp(Instant.now())
                    .build();
            }

            List<NodeMetrics> nodeDetails = request.isIncludeNodeDetails()
                ? cluster.getNodes().stream()
                    .map(nodes::get)
                    .filter(n -> n != null)
                    .map(this::toNodeMetrics)
                    .toList()
                : List.of();

            int healthyNodes = (int) nodeDetails.stream()
                .filter(n -> n.getStatus() == NodeMetrics.NodeStatus.HEALTHY)
                .count();

            return ClusterStatusResult.builder()
                .clusterId(request.getClusterId())
                .status(healthyNodes > 0 ? ClusterStatusResult.ClusterStatus.HEALTHY : ClusterStatusResult.ClusterStatus.DEGRADED)
                .totalNodes(cluster.getNodes().size())
                .healthyNodes(healthyNodes)
                .nodeDetails(nodeDetails)
                .timestamp(Instant.now())
                .build();
        });
    }

    // ==================== Maintenance ====================

    public Promise<ClusterMaintenanceResult> performMaintenance(ClusterMaintenanceRequest request) {
        return Promise.ofBlocking(eventloop, () -> {
            log.info("Starting maintenance on cluster {} node {}: {}",
                request.getClusterId(), request.getNodeId(), request.getType());

            return ClusterMaintenanceResult.builder()
                .success(true)
                .clusterId(request.getClusterId())
                .nodeId(request.getNodeId())
                .startTime(Instant.now())
                .estimatedEndTime(Instant.now().plusSeconds(request.getDurationMinutes() * 60L))
                .build();
        });
    }

    // ==================== Rebalancing ====================

    public Promise<DistributedModels.ClusterRebalancingResult> rebalanceCluster(ClusterRebalancingRequest request) {
        return Promise.ofBlocking(eventloop, () -> {
            ClusterState cluster = clusters.get(request.getClusterId());
            if (cluster == null) {
                return DistributedModels.ClusterRebalancingResult.builder()
                    .clusterId(request.getClusterId())
                    .success(false)
                    .errorMessage("Cluster not found")
                    .build();
            }

            Instant start = Instant.now();
            int nodeCount = cluster.getNodes().size();

            // Simulate rebalancing logic
            try {
                Thread.sleep(100); // Simulate work
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            return DistributedModels.ClusterRebalancingResult.builder()
                .clusterId(request.getClusterId())
                .success(true)
                .nodesRebalanced(nodeCount)
                .partitionsMoved(0)
                .startTime(start)
                .endTime(Instant.now())
                .build();
        });
    }

    // ==================== Metrics ====================

    public Map<String, Object> getMetrics() {
        return Map.of(
            "totalClusters", clusters.size(),
            "totalNodes", nodes.size(),
            "activeNodes", nodes.values().stream().filter(NodeState::isActive).count()
        );
    }

    public Promise<Void> shutdown() {
        return Promise.ofBlocking(eventloop, () -> {
            log.info("Shutting down ClusterManagementSystem");
            clusters.clear();
            nodes.clear();
            return null;
        });
    }

    // ==================== Private Helpers ====================

    private NodeMetrics toNodeMetrics(NodeState state) {
        return NodeMetrics.builder()
            .nodeId(state.getNodeId())
            .clusterId(state.getClusterId())
            .hostname(state.getHostname())
            .status(state.isActive() ? NodeMetrics.NodeStatus.HEALTHY : NodeMetrics.NodeStatus.OFFLINE)
            .lastHeartbeat(state.getLastHeartbeat())
            .build();
    }

    // ==================== Inner Classes ====================

    private static class ClusterState {
        private final List<String> nodeIds;

        ClusterState(String clusterId) {
            this.nodeIds = new java.util.ArrayList<>();
        }

        void addNode(String nodeId) {
            nodeIds.add(nodeId);
        }

        void removeNode(String nodeId) {
            nodeIds.remove(nodeId);
        }

        List<String> getNodes() {
            return List.copyOf(nodeIds);
        }
    }

    private static class NodeState {
        private final String nodeId;
        private final String clusterId;
        private final String hostname;
        private volatile boolean active;
        private volatile Instant lastHeartbeat;

        NodeState(String nodeId, String clusterId, String hostname) {
            this.nodeId = nodeId;
            this.clusterId = clusterId;
            this.hostname = hostname;
            this.active = true;
            this.lastHeartbeat = Instant.now();
        }

        String getNodeId() { return nodeId; }
        String getClusterId() { return clusterId; }
        String getHostname() { return hostname; }
        boolean isActive() { return active; }
        Instant getLastHeartbeat() { return lastHeartbeat; }
    }
}
