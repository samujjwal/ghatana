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

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Service Provider Interface for cluster coordination.
 *
 * <p>Provides cluster-wide coordination primitives for distributed
 * Data-Cloud deployments:
 * <ul>
 *   <li>Cluster membership management</li>
 *   <li>Leader election</li>
 *   <li>Distributed locks</li>
 *   <li>Configuration storage</li>
 * </ul>
 *
 * <h2>Implementations</h2>
 * <pre>
 * ┌────────────────────────────────────────────────────────────────┐
 * │  Coordinator       │  Description                              │
 * ├────────────────────┼───────────────────────────────────────────┤
 * │  EtcdCoordinator   │  etcd-based coordination (recommended)    │
 * │  ConsulCoordinator │  Consul-based coordination                │
 * │  ZkCoordinator     │  ZooKeeper-based coordination             │
 * │  K8sCoordinator    │  Kubernetes API-based coordination        │
 * └────────────────────┴───────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * ClusterCoordinator coordinator = EtcdCoordinator.create(config);
 *
 * // Join the cluster
 * coordinator.join(NodeInfo.builder()
 *     .nodeId("node-1")
 *     .host("10.0.0.1")
 *     .port(8080)
 *     .build());
 *
 * // Elect leader for a partition
 * LeaderLease lease = coordinator.electLeader("partition-0")
 *     .getResult();
 *
 * if (lease.isLeader()) {
 *     // Handle partition as leader
 * }
 * }</pre>
 *
 * @see com.ghatana.datacloud.deployment.ClusterConfig
 * @doc.type interface
 * @doc.purpose Cluster coordination SPI
 * @doc.layer spi
 * @doc.pattern Service Provider Interface
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public interface ClusterCoordinator {

    // ═══════════════════════════════════════════════════════════════
    // Cluster Membership
    // ═══════════════════════════════════════════════════════════════

    /**
     * Registers this node with the cluster.
     *
     * <p>Must be called on startup before serving requests.
     * Node will be visible to other members and participate
     * in leader election.
     *
     * @param nodeInfo information about this node
     * @return promise that completes when registered
     */
    Promise<Void> join(NodeInfo nodeInfo);

    /**
     * Deregisters this node from the cluster.
     *
     * <p>Should be called during graceful shutdown.
     * Releases any held leases and leadership.
     *
     * @return promise that completes when deregistered
     */
    Promise<Void> leave();

    /**
     * Gets all active nodes in the cluster.
     *
     * @return set of active node information
     */
    Promise<Set<NodeInfo>> getActiveNodes();

    /**
     * Gets information about a specific node.
     *
     * @param nodeId the node identifier
     * @return node information, or empty if not found
     */
    Promise<NodeInfo> getNode(String nodeId);

    /**
     * Subscribes to membership change events.
     *
     * @param listener callback for membership changes
     */
    void onMembershipChange(Consumer<MembershipEvent> listener);

    // ═══════════════════════════════════════════════════════════════
    // Leader Election
    // ═══════════════════════════════════════════════════════════════

    /**
     * Attempts to become leader for a resource group.
     *
     * <p>Resource groups can represent:
     * <ul>
     *   <li>Partitions (e.g., "partition-0")</li>
     *   <li>Collections (e.g., "collection-orders")</li>
     *   <li>Tenants (e.g., "tenant-123")</li>
     * </ul>
     *
     * @param resourceGroup logical grouping for leadership
     * @return lease with leadership status
     */
    Promise<LeaderLease> electLeader(String resourceGroup);

    /**
     * Gets the current leader for a resource group.
     *
     * @param resourceGroup the resource group
     * @return leader node ID, or null if no leader
     */
    Promise<String> getLeader(String resourceGroup);

    /**
     * Subscribes to leadership changes for a resource group.
     *
     * @param resourceGroup the resource group
     * @param listener callback for leadership changes
     */
    void onLeaderChange(String resourceGroup, Consumer<LeaderChangeEvent> listener);

    // ═══════════════════════════════════════════════════════════════
    // Distributed Locking
    // ═══════════════════════════════════════════════════════════════

    /**
     * Acquires a distributed lock.
     *
     * @param lockName the lock name
     * @param timeout maximum wait time
     * @return lock handle, or empty if timeout
     */
    Promise<DistributedLock> acquireLock(String lockName, Duration timeout);

    /**
     * Tries to acquire a distributed lock without waiting.
     *
     * @param lockName the lock name
     * @return lock handle, or empty if not available
     */
    Promise<DistributedLock> tryLock(String lockName);

    // ═══════════════════════════════════════════════════════════════
    // Configuration Storage
    // ═══════════════════════════════════════════════════════════════

    /**
     * Gets a configuration value.
     *
     * @param key the configuration key
     * @return value, or null if not found
     */
    Promise<String> getConfig(String key);

    /**
     * Sets a configuration value.
     *
     * @param key the configuration key
     * @param value the value
     * @return promise that completes when stored
     */
    Promise<Void> setConfig(String key, String value);

    /**
     * Watches a configuration key for changes.
     *
     * @param key the configuration key
     * @param listener callback for changes
     */
    void watchConfig(String key, Consumer<ConfigChangeEvent> listener);

    /**
     * Gets all configuration values with a prefix.
     *
     * @param prefix the key prefix
     * @return map of key to value
     */
    Promise<Map<String, String>> getConfigsByPrefix(String prefix);

    // ═══════════════════════════════════════════════════════════════
    // Health & Lifecycle
    // ═══════════════════════════════════════════════════════════════

    /**
     * Checks if the coordinator is connected and healthy.
     *
     * @return true if connected and healthy
     */
    Promise<Boolean> isHealthy();

    /**
     * Shuts down the coordinator, releasing all resources.
     *
     * @return promise that completes when shut down
     */
    Promise<Void> shutdown();

    // ═══════════════════════════════════════════════════════════════
    // Event Types
    // ═══════════════════════════════════════════════════════════════

    /**
     * Membership change event.
     */
    record MembershipEvent(
            EventType type,
            NodeInfo node,
            Set<NodeInfo> currentMembers) {

        public enum EventType {
            /** Node joined the cluster */
            JOINED,
            /** Node left the cluster gracefully */
            LEFT,
            /** Node failed/became unresponsive */
            FAILED
        }
    }

    /**
     * Leadership change event.
     */
    record LeaderChangeEvent(
            String resourceGroup,
            String previousLeader,
            String newLeader) {

        /**
         * Returns true if this node became the leader.
         */
        public boolean becameLeader(String nodeId) {
            return nodeId.equals(newLeader);
        }

        /**
         * Returns true if this node lost leadership.
         */
        public boolean lostLeadership(String nodeId) {
            return nodeId.equals(previousLeader) && !nodeId.equals(newLeader);
        }
    }

    /**
     * Configuration change event.
     */
    record ConfigChangeEvent(
            String key,
            String oldValue,
            String newValue) {

        /**
         * Returns true if the value was deleted.
         */
        public boolean isDeleted() {
            return newValue == null;
        }
    }
}
