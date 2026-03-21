/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.scaling.loadbalancer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Models for load balancing operations.
 */
public final class LoadBalancerModels {

    private LoadBalancerModels() {
        // Utility class
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LoadBalancerConfiguration {
        private String balancerId;
        private String name;
        private BalancingAlgorithm algorithm;
        private List<String> backendNodes;
        private HealthCheckConfig healthCheck;
        private Map<String, Object> sessionAffinity;
        private boolean enabled;

        public enum BalancingAlgorithm {
            ROUND_ROBIN,
            LEAST_CONNECTIONS,
            LEAST_RESPONSE_TIME,
            IP_HASH,
            WEIGHTED_ROUND_ROBIN,
            RANDOM
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HealthCheckConfig {
        private String path;
        private int intervalSeconds;
        private int timeoutSeconds;
        private int healthyThreshold;
        private int unhealthyThreshold;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BackendNode {
        private String nodeId;
        private String address;
        private int port;
        private int weight;
        private NodeStatus status;
        private long activeConnections;
        private double responseTime;
        private Instant lastHealthCheck;

        public enum NodeStatus {
            HEALTHY,
            UNHEALTHY,
            DRAINING,
            DISABLED
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RoutingDecision {
        private String requestId;
        private String selectedNodeId;
        private String balancerId;
        private RoutingReason reason;
        private Instant timestamp;

        public enum RoutingReason {
            ROUND_ROBIN,
            LEAST_CONNECTIONS,
            SESSION_AFFINITY,
            HEALTH_CHECK,
            WEIGHTED,
            FALLBACK
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LoadBalancerMetrics {
        private String balancerId;
        private long totalRequests;
        private long successfulRequests;
        private long failedRequests;
        private double averageResponseTime;
        private Map<String, Long> requestsPerNode;
        private Instant timestamp;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LoadBalancerRequest {
        private String requestId;
        private String clientIp;
        private String path;
        private Map<String, String> headers;
        private Map<String, Object> sessionContext;
    }
}
