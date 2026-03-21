/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.scaling.models;

import com.ghatana.aep.scaling.cluster.ClusterManagementModels;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Models for distributed processing and pattern distribution.
 */
public final class DistributedModels {

    private DistributedModels() {
        // Utility class
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DistributedPatternProcessor {
        private String processorId;
        private String patternId;
        private DistributionStrategy strategy;
        private List<String> targetNodes;
        private Map<String, Object> configuration;
        private Instant createdAt;

        public enum DistributionStrategy {
            ROUND_ROBIN,
            HASH_BASED,
            LOAD_AWARE,
            GEO_LOCATION,
            CUSTOM
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ClusterRebalancingResult {
        private String clusterId;
        private boolean success;
        private String errorMessage;
        private int nodesRebalanced;
        private int partitionsMoved;
        private Instant startTime;
        private Instant endTime;
        private Map<String, Object> beforeMetrics;
        private Map<String, Object> afterMetrics;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PartitionAssignment {
        private String partitionId;
        private String assignedNodeId;
        private String previousNodeId;
        private AssignmentStatus status;
        private Instant assignmentTime;
        private Map<String, Object> metadata;

        public enum AssignmentStatus {
            PENDING,
            ACTIVE,
            MIGRATING,
            FAILED
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DataDistributionPlan {
        private String planId;
        private String clusterId;
        private List<PartitionAssignment> assignments;
        private DistributionStrategy strategy;
        private long estimatedMigrationTime;
        private boolean requiresDowntime;
    }

    public enum DistributionStrategy {
        ROUND_ROBIN,
        HASH_BASED,
        LOAD_AWARE,
        GEO_LOCATION,
        CUSTOM
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NodeCapacity {
        private String nodeId;
        private long maxPartitions;
        private long currentPartitions;
        private double capacityUtilization;
        private boolean canAcceptNewPartitions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DistributionMetrics {
        private String clusterId;
        private double balanceScore;
        private int totalPartitions;
        private double averagePartitionsPerNode;
        private double partitionSkew;
        private Instant timestamp;
    }

    // Re-export for backward compatibility
    public static class ClusterRebalancingRequest extends ClusterManagementModels.ClusterRebalancingRequest {}
}
