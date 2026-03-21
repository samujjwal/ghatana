/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.scaling.distributed;

import com.ghatana.aep.scaling.models.DistributedModels;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Processes patterns across distributed nodes.
 */
@Slf4j
public class DistributedPatternProcessor {

    private final Eventloop eventloop;
    private final String processorId;
    private final Map<String, DistributedModels.PartitionAssignment> assignments;

    public DistributedPatternProcessor(String processorId, Eventloop eventloop) {
        this.processorId = processorId;
        this.eventloop = eventloop;
        this.assignments = new ConcurrentHashMap<>();
    }

    /**
     * Distributes a pattern across nodes based on the distribution strategy.
     */
    public Promise<DistributedModels.DataDistributionPlan> distributePattern(
            String patternId,
            DistributedModels.DistributionStrategy strategy,
            List<String> targetNodes) {
        return Promise.ofBlocking(eventloop, () -> {
            String planId = java.util.UUID.randomUUID().toString();

            // Create partition assignments
            List<DistributedModels.PartitionAssignment> partitionAssignments = targetNodes.stream()
                .map(nodeId -> DistributedModels.PartitionAssignment.builder()
                    .partitionId(patternId + "-" + nodeId)
                    .assignedNodeId(nodeId)
                    .status(DistributedModels.PartitionAssignment.AssignmentStatus.PENDING)
                    .assignmentTime(java.time.Instant.now())
                    .build())
                .toList();

            partitionAssignments.forEach(a -> assignments.put(a.getPartitionId(), a));

            return DistributedModels.DataDistributionPlan.builder()
                .planId(planId)
                .clusterId(processorId)
                .assignments(partitionAssignments)
                .strategy(strategy)
                .estimatedMigrationTime(0)
                .requiresDowntime(false)
                .build();
        });
    }

    /**
     * Updates the status of a partition assignment.
     */
    public Promise<Void> updateAssignmentStatus(String partitionId, DistributedModels.PartitionAssignment.AssignmentStatus status) {
        return Promise.ofBlocking(eventloop, () -> {
            DistributedModels.PartitionAssignment assignment = assignments.get(partitionId);
            if (assignment != null) {
                assignment.setStatus(status);
                log.debug("Updated assignment {} status to {}", partitionId, status);
            }
            return null;
        });
    }

    /**
     * Returns current partition assignments.
     */
    public List<DistributedModels.PartitionAssignment> getAssignments() {
        return List.copyOf(assignments.values());
    }

    /**
     * Calculates distribution metrics for the current state.
     */
    public Promise<DistributedModels.DistributionMetrics> calculateMetrics(String clusterId) {
        return Promise.ofBlocking(eventloop, () -> {
            int totalPartitions = assignments.size();
            long activePartitions = assignments.values().stream()
                .filter(a -> a.getStatus() == DistributedModels.PartitionAssignment.AssignmentStatus.ACTIVE)
                .count();

            return DistributedModels.DistributionMetrics.builder()
                .clusterId(clusterId)
                .totalPartitions(totalPartitions)
                .balanceScore(totalPartitions > 0 ? (double) activePartitions / totalPartitions : 0.0)
                .timestamp(java.time.Instant.now())
                .build();
        });
    }

    /**
     * Shuts down the processor.
     */
    public Promise<Void> shutdown() {
        return Promise.ofBlocking(eventloop, () -> {
            log.info("Shutting down DistributedPatternProcessor {}", processorId);
            assignments.clear();
            return null;
        });
    }
}
