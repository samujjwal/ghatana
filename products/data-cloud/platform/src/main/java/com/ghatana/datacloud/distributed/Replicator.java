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

/**
 * Service Provider Interface for data replication.
 *
 * <p>Manages replication of data across nodes for durability
 * and read scaling. Supports synchronous and asynchronous
 * replication with configurable consistency levels.
 *
 * <h2>Replication Modes</h2>
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │                    Replication Modes                            │
 * ├────────────────┬────────────────────────────────────────────────┤
 * │  SYNC          │  Wait for all replicas before acknowledging    │
 * │  ASYNC         │  Acknowledge immediately, replicate in bg      │
 * │  SEMI_SYNC     │  Wait for quorum before acknowledging          │
 * │  CHAIN         │  Sequential replication through chain          │
 * └────────────────┴────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Replication Flow</h2>
 * <pre>
 *   Write Request
 *        │
 *        ▼
 *   ┌─────────┐
 *   │ Primary │ ─────────────────────┐
 *   └─────────┘                      │
 *        │                           │
 *        ▼                           ▼
 *   ┌─────────┐                ┌─────────┐
 *   │Replica 1│                │Replica 2│
 *   └─────────┘                └─────────┘
 * </pre>
 *
 * @see PartitionManager
 * @see ConsistencyLevel
 * @doc.type interface
 * @doc.purpose Data replication SPI
 * @doc.layer core
 * @doc.pattern Observer, Chain of Responsibility
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface Replicator {

    /**
     * Initializes the replicator with configuration.
     *
     * @param config replication configuration
     * @return completion promise
     */
    Promise<Void> initialize(ReplicationConfig config);

    /**
     * Replicates data to replicas.
     *
     * @param request replication request
     * @return replication result
     */
    Promise<ReplicationResult> replicate(ReplicationRequest request);

    /**
     * Synchronizes a partition from source to target.
     *
     * @param partitionId partition to sync
     * @param sourceNode source node
     * @param targetNode target node
     * @return sync result
     */
    Promise<SyncResult> sync(int partitionId, String sourceNode, String targetNode);

    /**
     * Returns replication lag for a replica.
     *
     * @param nodeId replica node ID
     * @param partitionId partition ID
     * @return lag in milliseconds
     */
    Promise<Long> getLag(String nodeId, int partitionId);

    /**
     * Returns all replica statuses.
     *
     * @return list of replica statuses
     */
    Promise<List<ReplicaStatus>> getReplicaStatuses();

    /**
     * Pauses replication.
     *
     * @return completion promise
     */
    Promise<Void> pause();

    /**
     * Resumes replication.
     *
     * @return completion promise
     */
    Promise<Void> resume();

    /**
     * Returns true if replication is paused.
     *
     * @return paused state
     */
    boolean isPaused();

    /**
     * Shuts down the replicator.
     *
     * @return completion promise
     */
    Promise<Void> shutdown();

    /**
     * Replication configuration.
     *
     * @param mode replication mode
     * @param replicationFactor number of replicas
     * @param syncTimeout sync timeout in milliseconds
     * @param maxRetries max retry attempts
     * @param batchSize batch size for bulk replication
     */
    record ReplicationConfig(
            Mode mode,
            int replicationFactor,
            long syncTimeout,
            int maxRetries,
            int batchSize
    ) {
        public static ReplicationConfig defaultConfig() {
            return new ReplicationConfig(Mode.SEMI_SYNC, 3, 5000, 3, 100);
        }

        public static ReplicationConfig async() {
            return new ReplicationConfig(Mode.ASYNC, 3, 10000, 5, 1000);
        }
    }

    /**
     * Replication mode.
     */
    enum Mode {
        /** Synchronous - wait for all replicas */
        SYNC,
        /** Asynchronous - fire and forget */
        ASYNC,
        /** Semi-synchronous - wait for quorum */
        SEMI_SYNC,
        /** Chain replication */
        CHAIN
    }

    /**
     * Replication request.
     *
     * @param partitionId target partition
     * @param key record key
     * @param data record data
     * @param operation operation type
     * @param consistency required consistency level
     */
    record ReplicationRequest(
            int partitionId,
            String key,
            byte[] data,
            Operation operation,
            ConsistencyLevel consistency
    ) {
        public enum Operation {
            WRITE,
            DELETE,
            BATCH
        }
    }

    /**
     * Replication result.
     *
     * @param success whether replication succeeded
     * @param replicasAcked number of replicas that acknowledged
     * @param replicasFailed number of replicas that failed
     * @param latencyMs replication latency
     */
    record ReplicationResult(
            boolean success,
            int replicasAcked,
            int replicasFailed,
            long latencyMs
    ) {
        public static ReplicationResult success(int acked, long latency) {
            return new ReplicationResult(true, acked, 0, latency);
        }

        public static ReplicationResult partial(int acked, int failed, long latency) {
            return new ReplicationResult(false, acked, failed, latency);
        }
    }

    /**
     * Sync result.
     *
     * @param success whether sync succeeded
     * @param recordsSynced number of records synced
     * @param durationMs sync duration
     */
    record SyncResult(
            boolean success,
            long recordsSynced,
            long durationMs
    ) {
    }

    /**
     * Replica status.
     *
     * @param nodeId replica node ID
     * @param partitionId partition ID
     * @param state replica state
     * @param lagMs replication lag in milliseconds
     * @param lastSyncTime last sync timestamp
     */
    record ReplicaStatus(
            String nodeId,
            int partitionId,
            State state,
            long lagMs,
            java.time.Instant lastSyncTime
    ) {
        public enum State {
            /** Replica is in sync */
            IN_SYNC,
            /** Replica is catching up */
            CATCHING_UP,
            /** Replica is stale */
            STALE,
            /** Replica is offline */
            OFFLINE
        }

        public boolean isHealthy() {
            return state == State.IN_SYNC || state == State.CATCHING_UP;
        }
    }
}
