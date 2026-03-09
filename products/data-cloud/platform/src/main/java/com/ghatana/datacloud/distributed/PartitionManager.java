/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.datacloud.distributed;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service Provider Interface for partition management.
 *
 * <p>Manages data partitioning across nodes for horizontal scaling.
 * Supports consistent hashing, range-based, and custom partitioning
 * strategies.
 *
 * <h2>Partitioning Strategies</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                    Partitioning Strategies                      │
 * ├────────────────┬────────────────────────────────────────────────┤
 * │  CONSISTENT    │  Consistent hashing with virtual nodes         │
 * │  RANGE         │  Range-based partitioning (sorted keys)        │
 * │  HASH          │  Simple hash modulo partitioning               │
 * │  ROUND_ROBIN   │  Even distribution across partitions           │
 * │  CUSTOM        │  User-defined partition function               │
 * └────────────────┴────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Partition Lifecycle</h2>
 * <pre>
 * [Initialized] → [Active] → [Splitting] → [Active]
 *                    ↓            ↓
 *              [Merging] → [Active]
 *                    ↓
 *              [Migrating] → [Active]
 * </pre>
 *
 * @see ClusterCoordinator
 * @doc.type interface
 * @doc.purpose Partition management SPI
 * @doc.layer core
 * @doc.pattern Strategy, Coordinator
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface PartitionManager {

    /**
     * Initializes the partition manager with configuration.
     *
     * @param config partition configuration
     * @return completion promise
     */
    Promise<Void> initialize(PartitionConfig config);

    /**
     * Determines which partition a key belongs to.
     *
     * @param key the record key
     * @return partition ID
     */
    int getPartition(String key);

    /**
     * Determines which partition a key belongs to with tenant isolation.
     *
     * @param tenantId tenant identifier
     * @param key the record key
     * @return partition ID
     */
    int getPartition(String tenantId, String key);

    /**
     * Returns the node responsible for a partition.
     *
     * @param partitionId partition ID
     * @return node info (null if unassigned)
     */
    NodeInfo getOwner(int partitionId);

    /**
     * Returns all replicas for a partition.
     *
     * @param partitionId partition ID
     * @return list of replica nodes (primary first)
     */
    List<NodeInfo> getReplicas(int partitionId);

    /**
     * Returns all partitions owned by a node.
     *
     * @param nodeId node identifier
     * @return set of partition IDs
     */
    Set<Integer> getPartitionsForNode(String nodeId);

    /**
     * Returns total partition count.
     *
     * @return partition count
     */
    int partitionCount();

    /**
     * Returns the current partition assignment map.
     *
     * @return map of partition ID to owner node ID
     */
    Map<Integer, String> getAssignments();

    /**
     * Rebalances partitions across nodes.
     *
     * @return rebalance result
     */
    Promise<RebalanceResult> rebalance();

    /**
     * Splits a partition into two.
     *
     * @param partitionId partition to split
     * @return split result with new partition IDs
     */
    Promise<SplitResult> split(int partitionId);

    /**
     * Merges two adjacent partitions.
     *
     * @param partitionId1 first partition
     * @param partitionId2 second partition
     * @return merge result
     */
    Promise<MergeResult> merge(int partitionId1, int partitionId2);

    /**
     * Registers a listener for partition changes.
     *
     * @param listener the listener
     */
    void addListener(PartitionChangeListener listener);

    /**
     * Removes a listener.
     *
     * @param listener the listener
     */
    void removeListener(PartitionChangeListener listener);

    /**
     * Shuts down the partition manager.
     *
     * @return completion promise
     */
    Promise<Void> shutdown();

    /**
     * Partition configuration.
     *
     * @param partitionCount initial partition count
     * @param replicationFactor number of replicas
     * @param strategy partitioning strategy
     * @param virtualNodes virtual nodes per physical node (for consistent hashing)
     * @param autoRebalance enable automatic rebalancing
     */
    record PartitionConfig(
            int partitionCount,
            int replicationFactor,
            Strategy strategy,
            int virtualNodes,
            boolean autoRebalance
    ) {
        public static PartitionConfig defaultConfig() {
            return new PartitionConfig(256, 3, Strategy.CONSISTENT, 100, true);
        }

        public static PartitionConfig singleNode() {
            return new PartitionConfig(1, 1, Strategy.HASH, 1, false);
        }
    }

    /**
     * Partitioning strategy.
     */
    enum Strategy {
        /** Consistent hashing with virtual nodes */
        CONSISTENT,
        /** Range-based partitioning */
        RANGE,
        /** Simple hash modulo */
        HASH,
        /** Round-robin distribution */
        ROUND_ROBIN,
        /** Custom user-defined */
        CUSTOM
    }

    /**
     * Partition state.
     */
    enum PartitionState {
        /** Partition is initializing */
        INITIALIZING,
        /** Partition is active and serving requests */
        ACTIVE,
        /** Partition is being split */
        SPLITTING,
        /** Partition is being merged */
        MERGING,
        /** Partition is migrating to another node */
        MIGRATING,
        /** Partition is offline */
        OFFLINE
    }

    /**
     * Partition metadata.
     *
     * @param id partition ID
     * @param state current state
     * @param owner primary owner node
     * @param replicas replica nodes
     * @param recordCount estimated record count
     * @param sizeBytes estimated size in bytes
     */
    record PartitionInfo(
            int id,
            PartitionState state,
            String owner,
            List<String> replicas,
            long recordCount,
            long sizeBytes
    ) {
        public boolean isActive() {
            return state == PartitionState.ACTIVE;
        }
    }

    /**
     * Result of rebalance operation.
     *
     * @param success whether rebalance succeeded
     * @param movedPartitions number of partitions moved
     * @param newAssignments new partition assignments
     * @param durationMs operation duration
     */
    record RebalanceResult(
            boolean success,
            int movedPartitions,
            Map<Integer, String> newAssignments,
            long durationMs
    ) {
    }

    /**
     * Result of split operation.
     *
     * @param success whether split succeeded
     * @param originalPartition original partition ID
     * @param newPartition1 first new partition ID
     * @param newPartition2 second new partition ID
     */
    record SplitResult(
            boolean success,
            int originalPartition,
            int newPartition1,
            int newPartition2
    ) {
    }

    /**
     * Result of merge operation.
     *
     * @param success whether merge succeeded
     * @param mergedPartition resulting partition ID
     * @param removedPartition removed partition ID
     */
    record MergeResult(
            boolean success,
            int mergedPartition,
            int removedPartition
    ) {
    }

    /**
     * Listener for partition changes.
     */
    interface PartitionChangeListener {

        /**
         * Called when partition assignment changes.
         *
         * @param partitionId affected partition
         * @param oldOwner previous owner
         * @param newOwner new owner
         */
        void onAssignmentChange(int partitionId, String oldOwner, String newOwner);

        /**
         * Called when partition state changes.
         *
         * @param partitionId affected partition
         * @param oldState previous state
         * @param newState new state
         */
        void onStateChange(int partitionId, PartitionState oldState, PartitionState newState);

        /**
         * Called when partition is split.
         *
         * @param result split result
         */
        default void onSplit(SplitResult result) {
        }

        /**
         * Called when partitions are merged.
         *
         * @param result merge result
         */
        default void onMerge(MergeResult result) {
        }
    }
}
