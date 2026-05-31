/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.pattern.spec;

import com.ghatana.aep.operator.contract.OperatorKind;
import com.ghatana.aep.pattern.lifecycle.PatternLifecycleState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Runtime execution plan for a compiled pattern (P4-02).
 *
 * <p>P4-02: Represents the executable form of a pattern after compilation,
 * including the DAG structure, resolved capabilities, resource allocation,
 * and execution constraints.
 *
 * @doc.type record
 * @doc.purpose Runtime execution plan for compiled patterns with resource allocation and execution constraints
 * @doc.layer product
 * @doc.pattern Model
 */
public record PatternRuntimePlan(
    String patternId,
    String planId,
    PatternLifecycleState lifecycleState,
    PatternDagStructure dagStructure,
    ResourceAllocation resourceAllocation,
    ExecutionConstraints executionConstraints,
    Map<String, Object> metadata
) {
    public PatternRuntimePlan {
        if (patternId == null || patternId.isBlank()) {
            throw new IllegalArgumentException("patternId is required");
        }
        if (planId == null || planId.isBlank()) {
            throw new IllegalArgumentException("planId is required");
        }
        lifecycleState = lifecycleState != null ? lifecycleState : PatternLifecycleState.DRAFT;
        dagStructure = dagStructure != null ? dagStructure : new PatternDagStructure(List.of(), Map.of());
        resourceAllocation = resourceAllocation != null ? resourceAllocation : ResourceAllocation.defaults();
        executionConstraints = executionConstraints != null ? executionConstraints : ExecutionConstraints.defaults();
        metadata = Map.copyOf(metadata != null ? metadata : Map.of());
    }

    /**
     * Check if this plan is ready for execution.
     */
    public boolean isExecutable() {
        return lifecycleState == PatternLifecycleState.ACTIVE ||
               lifecycleState == PatternLifecycleState.SHADOW;
    }

    /**
     * Get the estimated execution time in milliseconds.
     */
    public Optional<Long> estimatedExecutionTimeMs() {
        return resourceAllocation.estimatedExecutionTimeMs();
    }

    /**
     * DAG structure representing the pattern execution graph.
     */
    public record PatternDagStructure(
        List<DagNode> nodes,
        Map<String, List<String>> edges
    ) {
        public PatternDagStructure {
            nodes = List.copyOf(nodes != null ? nodes : List.of());
            edges = Map.copyOf(edges != null ? edges : Map.of());
        }

        public int nodeCount() {
            return nodes.size();
        }

        public int edgeCount() {
            return edges.values().stream().mapToInt(List::size).sum();
        }
    }

    /**
     * A single node in the DAG.
     */
    public record DagNode(
        String nodeId,
        OperatorKind operatorKind,
        String capabilityRef,
        Map<String, Object> parameters,
        List<String> dependencies
    ) {
        public DagNode {
            if (nodeId == null || nodeId.isBlank()) {
                throw new IllegalArgumentException("nodeId is required");
            }
            parameters = Map.copyOf(parameters != null ? parameters : Map.of());
            dependencies = List.copyOf(dependencies != null ? dependencies : List.of());
        }
    }

    /**
     * Resource allocation for the pattern execution.
     */
    public record ResourceAllocation(
        Optional<Integer> maxMemoryMb,
        Optional<Integer> maxCpuCores,
        Optional<Long> estimatedExecutionTimeMs,
        Optional<Integer> maxParallelism
    ) {
        public ResourceAllocation {
            maxMemoryMb = maxMemoryMb != null && maxMemoryMb.isPresent() ? maxMemoryMb : Optional.empty();
            maxCpuCores = maxCpuCores != null && maxCpuCores.isPresent() ? maxCpuCores : Optional.empty();
            estimatedExecutionTimeMs = estimatedExecutionTimeMs != null && estimatedExecutionTimeMs.isPresent() ? estimatedExecutionTimeMs : Optional.empty();
            maxParallelism = maxParallelism != null && maxParallelism.isPresent() ? maxParallelism : Optional.empty();
        }

        public static ResourceAllocation defaults() {
            return new ResourceAllocation(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }

        public static ResourceAllocation withMemory(int memoryMb) {
            return new ResourceAllocation(Optional.of(memoryMb), Optional.empty(), Optional.empty(), Optional.empty());
        }

        public static ResourceAllocation withCpu(int cpuCores) {
            return new ResourceAllocation(Optional.empty(), Optional.of(cpuCores), Optional.empty(), Optional.empty());
        }
    }

    /**
     * Execution constraints for the pattern.
     */
    public record ExecutionConstraints(
        Optional<Long> maxExecutionTimeMs,
        Optional<Integer> maxRetries,
        Optional<Integer> timeoutSeconds,
        boolean requiresDeduplication,
        boolean requiresApproval
    ) {
        public ExecutionConstraints {
            maxExecutionTimeMs = maxExecutionTimeMs != null && maxExecutionTimeMs.isPresent() ? maxExecutionTimeMs : Optional.empty();
            maxRetries = maxRetries != null && maxRetries.isPresent() ? maxRetries : Optional.empty();
            timeoutSeconds = timeoutSeconds != null && timeoutSeconds.isPresent() ? timeoutSeconds : Optional.empty();
        }

        public static ExecutionConstraints defaults() {
            return new ExecutionConstraints(Optional.empty(), Optional.empty(), Optional.empty(), false, false);
        }

        public static ExecutionConstraints withTimeout(int seconds) {
            return new ExecutionConstraints(Optional.empty(), Optional.empty(), Optional.of(seconds), false, false);
        }

        public static ExecutionConstraints withApproval() {
            return new ExecutionConstraints(Optional.empty(), Optional.empty(), Optional.empty(), false, true);
        }
    }
}
