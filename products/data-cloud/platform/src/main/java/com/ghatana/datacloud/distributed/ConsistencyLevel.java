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

/**
 * Consistency level for distributed operations.
 *
 * <p>Supports tunable consistency per operation, allowing trade-offs
 * between consistency, availability, and latency based on use case.
 *
 * <h2>CAP Trade-offs</h2>
 * <pre>
 * ┌─────────────────┬─────────────────────┬─────────────────────┐
 * │  Consistency    │  Latency            │  Availability       │
 * ├─────────────────┼─────────────────────┼─────────────────────┤
 * │  STRONG         │  Higher (sync)      │  Lower              │
 * │  QUORUM         │  Medium             │  Medium             │
 * │  ONE            │  Low                │  Higher             │
 * │  EVENTUAL       │  Lowest (async)     │  Highest            │
 * │  LOCAL          │  Lowest             │  Highest            │
 * └─────────────────┴─────────────────────┴─────────────────────┘
 * </pre>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Financial transactions require strong consistency
 * storagePlugin.put(key, value, ConsistencyLevel.STRONG);
 *
 * // Analytics can use eventual consistency for better performance
 * storagePlugin.put(key, value, ConsistencyLevel.EVENTUAL);
 *
 * // Collection-level default
 * Collection orders = Collection.builder()
 *     .name("orders")
 *     .consistencyLevel(ConsistencyLevel.QUORUM)
 *     .build();
 * }</pre>
 *
 * @see com.ghatana.datacloud.deployment.ClusterConfig
 * @doc.type enum
 * @doc.purpose Tunable consistency levels for distributed operations
 * @doc.layer core
 * @doc.pattern Value Object
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public enum ConsistencyLevel {

    /**
     * Strong consistency (linearizable).
     *
     * <p>All replicas must acknowledge before returning success.
     * Guarantees read-after-write consistency.
     *
     * <p><b>Use Cases:</b>
     * <ul>
     *   <li>Financial transactions</li>
     *   <li>Inventory management</li>
     *   <li>User authentication</li>
     * </ul>
     *
     * <p><b>Trade-offs:</b>
     * <ul>
     *   <li>Higher latency</li>
     *   <li>Reduced availability during partitions</li>
     * </ul>
     */
    STRONG(ReplicaRequirement.ALL, true),

    /**
     * Quorum consistency.
     *
     * <p>Majority of replicas (N/2 + 1) must acknowledge.
     * Provides strong consistency with better availability.
     *
     * <p><b>Use Cases:</b>
     * <ul>
     *   <li>Most production workloads</li>
     *   <li>Order processing</li>
     *   <li>Session management</li>
     * </ul>
     *
     * <p><b>Trade-offs:</b>
     * <ul>
     *   <li>Medium latency</li>
     *   <li>Tolerates minority failures</li>
     * </ul>
     */
    QUORUM(ReplicaRequirement.QUORUM, true),

    /**
     * Single replica acknowledgment.
     *
     * <p>Only one replica must acknowledge before returning.
     * Provides low latency with risk of stale reads.
     *
     * <p><b>Use Cases:</b>
     * <ul>
     *   <li>Read-heavy workloads</li>
     *   <li>Caching layers</li>
     *   <li>Non-critical data</li>
     * </ul>
     *
     * <p><b>Trade-offs:</b>
     * <ul>
     *   <li>Low latency</li>
     *   <li>Possible stale reads</li>
     * </ul>
     */
    ONE(ReplicaRequirement.ONE, true),

    /**
     * Eventual consistency (async replication).
     *
     * <p>Write returns after local acknowledgment.
     * Replication happens asynchronously.
     *
     * <p><b>Use Cases:</b>
     * <ul>
     *   <li>Analytics and logs</li>
     *   <li>Audit trails</li>
     *   <li>Metrics collection</li>
     * </ul>
     *
     * <p><b>Trade-offs:</b>
     * <ul>
     *   <li>Lowest latency</li>
     *   <li>Highest availability</li>
     *   <li>No immediate consistency</li>
     * </ul>
     */
    EVENTUAL(ReplicaRequirement.ONE, false),

    /**
     * Local-only (no replication).
     *
     * <p>Write is only acknowledged by the local node.
     * No replication to other nodes.
     *
     * <p><b>Use Cases:</b>
     * <ul>
     *   <li>Ephemeral data</li>
     *   <li>Local caches</li>
     *   <li>Temporary processing</li>
     * </ul>
     *
     * <p><b>Trade-offs:</b>
     * <ul>
     *   <li>Fastest possible</li>
     *   <li>No durability guarantees</li>
     *   <li>Data loss on node failure</li>
     * </ul>
     */
    LOCAL(ReplicaRequirement.NONE, false);

    /**
     * Replica acknowledgment requirements.
     */
    public enum ReplicaRequirement {
        /** No replication required */
        NONE,
        /** At least one replica */
        ONE,
        /** Majority of replicas (N/2 + 1) */
        QUORUM,
        /** All replicas */
        ALL
    }

    private final ReplicaRequirement replicaRequirement;
    private final boolean synchronous;

    ConsistencyLevel(ReplicaRequirement requirement, boolean synchronous) {
        this.replicaRequirement = requirement;
        this.synchronous = synchronous;
    }

    /**
     * Returns the replica acknowledgment requirement.
     *
     * @return replica requirement
     */
    public ReplicaRequirement replicaRequirement() {
        return replicaRequirement;
    }

    /**
     * Returns true if replication is synchronous.
     *
     * @return true for synchronous replication
     */
    public boolean isSynchronous() {
        return synchronous;
    }

    /**
     * Returns true if this level requires waiting for replication.
     *
     * @return true if waiting for replicas
     */
    public boolean requiresReplicationWait() {
        return synchronous && replicaRequirement != ReplicaRequirement.NONE;
    }

    /**
     * Calculates the number of replicas required for a given replication factor.
     *
     * @param replicationFactor total number of replicas
     * @return number of acknowledgments required
     */
    public int requiredAcknowledgments(int replicationFactor) {
        return switch (replicaRequirement) {
            case NONE -> 0;
            case ONE -> 1;
            case QUORUM -> (replicationFactor / 2) + 1;
            case ALL -> replicationFactor;
        };
    }

    /**
     * Returns true if this level is considered strong consistency.
     *
     * @return true for STRONG and QUORUM
     */
    public boolean isStrong() {
        return this == STRONG || this == QUORUM;
    }

    /**
     * Returns true if this level provides read-after-write guarantee.
     *
     * @return true for STRONG and QUORUM
     */
    public boolean hasReadAfterWriteGuarantee() {
        return isStrong();
    }

    /**
     * Returns a consistency level suitable for the given use case.
     *
     * @param useCase the use case category
     * @return recommended consistency level
     */
    public static ConsistencyLevel forUseCase(UseCase useCase) {
        return switch (useCase) {
            case FINANCIAL -> STRONG;
            case TRANSACTIONAL -> QUORUM;
            case GENERAL -> QUORUM;
            case ANALYTICS -> EVENTUAL;
            case LOGGING -> EVENTUAL;
            case CACHING -> LOCAL;
        };
    }

    /**
     * Common use case categories for consistency selection.
     */
    public enum UseCase {
        /** Financial transactions, payments */
        FINANCIAL,
        /** General transactional workloads */
        TRANSACTIONAL,
        /** General purpose operations */
        GENERAL,
        /** Analytics and reporting */
        ANALYTICS,
        /** Logging and audit trails */
        LOGGING,
        /** Caching and temporary data */
        CACHING
    }
}
