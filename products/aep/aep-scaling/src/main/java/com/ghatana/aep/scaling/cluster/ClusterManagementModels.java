/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.scaling.cluster;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Models for cluster management operations.
 */
public final class ClusterManagementModels {

    private ClusterManagementModels() {
        // Utility class
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ClusterMetrics {
        private String clusterId;
        private int activeNodes;
        private int totalNodes;
        private double cpuUtilization;
        private double memoryUtilization;
        private long requestThroughput;
        private double averageLatency;
        private Instant timestamp;

        public ClusterMetrics(String clusterId, int activeNodes, int totalNodes,
                            double cpuUtilization, double memoryUtilization, long throughput) {
            this.clusterId = clusterId;
            this.activeNodes = activeNodes;
            this.totalNodes = totalNodes;
            this.cpuUtilization = cpuUtilization;
            this.memoryUtilization = memoryUtilization;
            this.requestThroughput = throughput;
            this.timestamp = Instant.now();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NodeMetrics {
        private String nodeId;
        private String clusterId;
        private String hostname;
        private NodeStatus status;
        private double cpuUsage;
        private double memoryUsage;
        private double diskUsage;
        private long requestsHandled;
        private Instant lastHeartbeat;
        private Map<String, Object> customMetrics;

        public enum NodeStatus {
            HEALTHY,
            WARNING,
            CRITICAL,
            OFFLINE,
            MAINTENANCE
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NodeRegistrationRequest {
        private String nodeId;
        private String hostname;
        private String clusterId;
        private Map<String, Object> capabilities;
        private Map<String, Object> metadata;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NodeRegistrationResult {
        private boolean success;
        private String nodeId;
        private String errorMessage;
        private Instant registrationTime;
        private List<String> assignedPartitions;

        public NodeRegistrationResult(boolean success, String nodeId, String errorMessage) {
            this.success = success;
            this.nodeId = nodeId;
            this.errorMessage = errorMessage;
            this.registrationTime = Instant.now();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NodeUnregistrationRequest {
        private String nodeId;
        private String clusterId;
        private String reason;
        private boolean force;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NodeUnregistrationResult {
        private boolean success;
        private String nodeId;
        private String errorMessage;
        private Instant unregistrationTime;
        private List<String> affectedPartitions;

        public NodeUnregistrationResult(boolean success, String nodeId, String errorMessage) {
            this.success = success;
            this.nodeId = nodeId;
            this.errorMessage = errorMessage;
            this.unregistrationTime = Instant.now();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ClusterStatusRequest {
        private String clusterId;
        private boolean includeNodeDetails;
        private boolean includeMetrics;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ClusterStatusResult {
        private String clusterId;
        private ClusterStatus status;
        private int totalNodes;
        private int healthyNodes;
        private List<NodeMetrics> nodeDetails;
        private ClusterMetrics metrics;
        private Instant timestamp;

        public enum ClusterStatus {
            HEALTHY,
            DEGRADED,
            UNAVAILABLE,
            SCALING,
            MAINTENANCE
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ClusterMaintenanceRequest {
        private String clusterId;
        private String nodeId;
        private MaintenanceType type;
        private String reason;
        private int durationMinutes;

        public enum MaintenanceType {
            NODE_RESTART,
            SOFTWARE_UPDATE,
            HARDWARE_CHECK,
            NETWORK_MAINTENANCE
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ClusterMaintenanceResult {
        private boolean success;
        private String clusterId;
        private String nodeId;
        private String errorMessage;
        private Instant startTime;
        private Instant estimatedEndTime;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ClusterRebalancingRequest {
        private String clusterId;
        private RebalancingStrategy strategy;
        private Map<String, Object> options;

        public enum RebalancingStrategy {
            EVEN_DISTRIBUTION,
            LOAD_BASED,
            LATENCY_OPTIMIZED,
            CUSTOM
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ClusterConfiguration {
        private String clusterId;
        private String clusterName;
        private String region;
        private int minNodes;
        private int maxNodes;
        private Map<String, Object> autoScalingConfig;
        private Map<String, Object> securityConfig;
        private Map<String, Object> networkConfig;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ClusterEvent {
        private String eventId;
        private String clusterId;
        private EventType eventType;
        private String message;
        private Instant timestamp;
        private Map<String, Object> metadata;

        public enum EventType {
            NODE_JOINED,
            NODE_LEFT,
            NODE_FAILED,
            HEALTH_CHECK_FAILED,
            REBALANCING_STARTED,
            REBALANCING_COMPLETED,
            CONFIGURATION_CHANGED
        }
    }
}
