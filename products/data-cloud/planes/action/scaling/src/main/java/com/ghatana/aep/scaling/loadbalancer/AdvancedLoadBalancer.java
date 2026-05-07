/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.scaling.loadbalancer;

import com.ghatana.aep.scaling.loadbalancer.LoadBalancerModels.*;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Advanced load balancer with multiple algorithms and health checking.
  * @doc.type class
 * @doc.purpose Provides advanced load balancer functionality.
 * @doc.layer product
 * @doc.pattern Component
*/
@Slf4j
public class AdvancedLoadBalancer {

    private final Eventloop eventloop;
    private final String balancerId;
    private final LoadBalancerConfiguration configuration;
    private final Map<String, BackendNode> nodes;
    private final AtomicInteger roundRobinCounter;
    private final AtomicLong totalRequests;
    private final AtomicLong successfulRequests;

    public AdvancedLoadBalancer(String balancerId, LoadBalancerConfiguration config, Eventloop eventloop) {
        this.balancerId = balancerId;
        this.configuration = config;
        this.eventloop = eventloop;
        this.nodes = new ConcurrentHashMap<>();
        this.roundRobinCounter = new AtomicInteger(0);
        this.totalRequests = new AtomicLong(0);
        this.successfulRequests = new AtomicLong(0);

        // Initialize nodes
        for (String nodeId : config.getBackendNodes()) {
            nodes.put(nodeId, BackendNode.builder()
                .nodeId(nodeId)
                .status(BackendNode.NodeStatus.HEALTHY)
                .weight(1)
                .build());
        }
    }

    /**
     * Routes a request to an appropriate backend node.
     */
    public Promise<RoutingDecision> routeRequest(LoadBalancerRequest request) {
        return Promise.ofBlocking(eventloop, () -> {
            totalRequests.incrementAndGet();

            BackendNode selectedNode = selectNode(request);
            if (selectedNode == null) {
                log.warn("No healthy nodes available for balancer {}", balancerId);
                return RoutingDecision.builder()
                    .requestId(request.getRequestId())
                    .balancerId(balancerId)
                    .reason(RoutingDecision.RoutingReason.FALLBACK)
                    .timestamp(java.time.Instant.now())
                    .build();
            }

            selectedNode.setActiveConnections(selectedNode.getActiveConnections() + 1);
            successfulRequests.incrementAndGet();

            return RoutingDecision.builder()
                .requestId(request.getRequestId())
                .selectedNodeId(selectedNode.getNodeId())
                .balancerId(balancerId)
                .reason(mapRoutingReason(configuration.getAlgorithm()))
                .timestamp(java.time.Instant.now())
                .build();
        });
    }

    /**
     * Updates the health status of a backend node.
     */
    public Promise<Void> updateNodeHealth(String nodeId, boolean healthy) {
        return Promise.ofBlocking(eventloop, () -> {
            BackendNode node = nodes.get(nodeId);
            if (node != null) {
                node.setStatus(healthy ? BackendNode.NodeStatus.HEALTHY : BackendNode.NodeStatus.UNHEALTHY);
                node.setLastHealthCheck(java.time.Instant.now());
                log.debug("Updated health status for node {}: {}", nodeId, healthy ? "HEALTHY" : "UNHEALTHY");
            }
            return null;
        });
    }

    /**
     * Adds a new backend node to the load balancer.
     */
    public Promise<Void> addNode(String nodeId, String address, int port) {
        return Promise.ofBlocking(eventloop, () -> {
            nodes.put(nodeId, BackendNode.builder()
                .nodeId(nodeId)
                .address(address)
                .port(port)
                .status(BackendNode.NodeStatus.HEALTHY)
                .weight(1)
                .build());
            log.info("Added node {} to load balancer {}", nodeId, balancerId);
            return null;
        });
    }

    /**
     * Removes a backend node from the load balancer.
     */
    public Promise<Void> removeNode(String nodeId) {
        return Promise.ofBlocking(eventloop, () -> {
            BackendNode node = nodes.remove(nodeId);
            if (node != null) {
                node.setStatus(BackendNode.NodeStatus.DRAINING);
                log.info("Removed node {} from load balancer {}", nodeId, balancerId);
            }
            return null;
        });
    }

    /**
     * Returns current metrics for the load balancer.
     */
    public LoadBalancerMetrics getMetrics() {
        return LoadBalancerMetrics.builder()
            .balancerId(balancerId)
            .totalRequests(totalRequests.get())
            .successfulRequests(successfulRequests.get())
            .failedRequests(totalRequests.get() - successfulRequests.get())
            .timestamp(java.time.Instant.now())
            .build();
    }

    /**
     * Returns a list of all backend nodes.
     */
    public List<BackendNode> getNodes() {
        return List.copyOf(nodes.values());
    }

    // ==================== Private Helpers ====================

    private BackendNode selectNode(LoadBalancerRequest request) {
        List<BackendNode> healthyNodes = nodes.values().stream()
            .filter(n -> n.getStatus() == BackendNode.NodeStatus.HEALTHY)
            .toList();

        if (healthyNodes.isEmpty()) {
            return null;
        }

        return switch (configuration.getAlgorithm()) {
            case ROUND_ROBIN -> selectRoundRobin(healthyNodes);
            case LEAST_CONNECTIONS -> selectLeastConnections(healthyNodes);
            case RANDOM -> selectRandom(healthyNodes);
            case WEIGHTED_ROUND_ROBIN -> selectWeightedRoundRobin(healthyNodes);
            default -> selectRoundRobin(healthyNodes);
        };
    }

    private BackendNode selectRoundRobin(List<BackendNode> healthyNodes) {
        int index = roundRobinCounter.getAndIncrement() % healthyNodes.size();
        return healthyNodes.get(index);
    }

    private BackendNode selectLeastConnections(List<BackendNode> healthyNodes) {
        return healthyNodes.stream()
            .min(java.util.Comparator.comparingLong(BackendNode::getActiveConnections))
            .orElse(healthyNodes.get(0));
    }

    private BackendNode selectRandom(List<BackendNode> healthyNodes) {
        int index = (int) (Math.random() * healthyNodes.size());
        return healthyNodes.get(index);
    }

    private BackendNode selectWeightedRoundRobin(List<BackendNode> healthyNodes) {
        // Simplified weighted round-robin
        int totalWeight = healthyNodes.stream().mapToInt(BackendNode::getWeight).sum();
        int selected = (int) (Math.random() * totalWeight);
        int current = 0;
        for (BackendNode node : healthyNodes) {
            current += node.getWeight();
            if (selected < current) {
                return node;
            }
        }
        return healthyNodes.get(0);
    }

    private RoutingDecision.RoutingReason mapRoutingReason(LoadBalancerConfiguration.BalancingAlgorithm algorithm) {
        return switch (algorithm) {
            case ROUND_ROBIN -> RoutingDecision.RoutingReason.ROUND_ROBIN;
            case LEAST_CONNECTIONS -> RoutingDecision.RoutingReason.LEAST_CONNECTIONS;
            case WEIGHTED_ROUND_ROBIN -> RoutingDecision.RoutingReason.WEIGHTED;
            default -> RoutingDecision.RoutingReason.ROUND_ROBIN;
        };
    }
}
