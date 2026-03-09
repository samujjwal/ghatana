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
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Standalone cluster coordinator for single-node deployments.
 *
 * <p>This implementation provides a no-op/local implementation
 * of the ClusterCoordinator interface for embedded and standalone
 * deployment modes where no actual cluster coordination is needed.
 *
 * <h2>Behavior</h2>
 * <ul>
 *   <li>Always reports a single node (self)</li>
 *   <li>Self is always the leader</li>
 *   <li>Locks are in-memory only</li>
 *   <li>Configuration is in-memory only</li>
 *   <li>Watch/event operations are no-ops</li>
 * </ul>
 *
 * @see ClusterCoordinator
 * @see com.ghatana.datacloud.deployment.DeploymentMode
 * @doc.type class
 * @doc.purpose Single-node cluster coordinator
 * @doc.layer core
 * @doc.pattern Null Object, Singleton
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public final class StandaloneClusterCoordinator implements ClusterCoordinator {

    private static final String DEFAULT_NODE_ID = "standalone-node";
    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofMinutes(5);

    private final NodeInfo selfNode;
    private final Map<String, LocalLock> locks;
    private final Map<String, String> configStore;
    private volatile boolean active;

    /**
     * Creates a standalone coordinator with default node ID.
     */
    public StandaloneClusterCoordinator() {
        this(DEFAULT_NODE_ID, "localhost", 8080);
    }

    /**
     * Creates a standalone coordinator with custom node info.
     *
     * @param nodeId node identifier
     * @param host host address
     * @param port port number
     */
    public StandaloneClusterCoordinator(String nodeId, String host, int port) {
        this.selfNode = NodeInfo.builder()
                .nodeId(nodeId)
                .host(host)
                .port(port)
                .grpcPort(port + 1000)
                .state(NodeInfo.NodeState.STARTING)
                .capabilities("storage", "compute", "query")
                .labels(Map.of("deployment.mode", "standalone"))
                .build();
        this.locks = new ConcurrentHashMap<>();
        this.configStore = new ConcurrentHashMap<>();
        this.active = false;
    }

    // ═══════════════════════════════════════════════════════════════
    // Cluster Membership
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Promise<Void> join(NodeInfo nodeInfo) {
        active = true;
        return Promise.complete();
    }

    @Override
    public Promise<Void> leave() {
        active = false;
        return Promise.complete();
    }

    @Override
    public Promise<Set<NodeInfo>> getActiveNodes() {
        if (active) {
            return Promise.of(Set.of(selfNode));
        }
        return Promise.of(Set.of());
    }

    @Override
    public Promise<NodeInfo> getNode(String nodeId) {
        if (active && selfNode.nodeId().equals(nodeId)) {
            return Promise.of(selfNode);
        }
        return Promise.of(null);
    }

    @Override
    public void onMembershipChange(Consumer<MembershipEvent> listener) {
        // No-op - membership never changes in standalone mode
    }

    // ═══════════════════════════════════════════════════════════════
    // Leader Election
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Promise<LeaderLease> electLeader(String resourceGroup) {
        // In standalone mode, self is always the leader
        return Promise.of(new StandaloneLeaderLease(
                resourceGroup,
                selfNode.nodeId(),
                Instant.now().plus(DEFAULT_LEASE_DURATION)
        ));
    }

    @Override
    public Promise<String> getLeader(String resourceGroup) {
        if (!active) {
            return Promise.of(null);
        }
        return Promise.of(selfNode.nodeId());
    }

    @Override
    public void onLeaderChange(String resourceGroup, Consumer<LeaderChangeEvent> listener) {
        // No-op - always the leader in standalone mode
    }

    // ═══════════════════════════════════════════════════════════════
    // Distributed Locking
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Promise<DistributedLock> acquireLock(String lockName, Duration timeout) {
        LocalLock lock = locks.computeIfAbsent(lockName, 
                k -> new LocalLock(k, selfNode.nodeId(), timeout));

        if (lock.tryAcquire(selfNode.nodeId(), timeout)) {
            return Promise.of(lock);
        }
        return Promise.ofException(new IllegalStateException("Lock already held: " + lockName));
    }

    @Override
    public Promise<DistributedLock> tryLock(String lockName) {
        LocalLock lock = locks.computeIfAbsent(lockName, 
                k -> new LocalLock(k, selfNode.nodeId(), Duration.ofMinutes(1)));

        if (lock.tryAcquire(selfNode.nodeId(), Duration.ofMinutes(1))) {
            return Promise.of(lock);
        }
        return Promise.of(null);
    }

    // ═══════════════════════════════════════════════════════════════
    // Configuration Storage
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Promise<String> getConfig(String key) {
        return Promise.of(configStore.get(key));
    }

    @Override
    public Promise<Void> setConfig(String key, String value) {
        configStore.put(key, value);
        return Promise.complete();
    }

    @Override
    public void watchConfig(String key, Consumer<ConfigChangeEvent> listener) {
        // No-op in standalone mode - no external changes possible
    }

    @Override
    public Promise<Map<String, String>> getConfigsByPrefix(String prefix) {
        Map<String, String> result = new ConcurrentHashMap<>();
        configStore.forEach((k, v) -> {
            if (k.startsWith(prefix)) {
                result.put(k, v);
            }
        });
        return Promise.of(result);
    }

    // ═══════════════════════════════════════════════════════════════
    // Health & Lifecycle
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Promise<Boolean> isHealthy() {
        return Promise.of(active);
    }

    @Override
    public Promise<Void> shutdown() {
        active = false;
        locks.clear();
        configStore.clear();
        return Promise.complete();
    }

    /**
     * Returns true if this coordinator is active.
     *
     * @return active state
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Gets the self node info.
     *
     * @return self node info
     */
    public NodeInfo getSelfNode() {
        return selfNode;
    }

    // ═══════════════════════════════════════════════════════════════
    // Inner Classes: Standalone implementations
    // ═══════════════════════════════════════════════════════════════

    /**
     * Standalone leader lease - always the leader.
     */
    private static final class StandaloneLeaderLease implements LeaderLease {
        private final String resourceGroup;
        private final String leaderId;
        private Instant expiresAt;

        StandaloneLeaderLease(String resourceGroup, String leaderId, Instant expiresAt) {
            this.resourceGroup = resourceGroup;
            this.leaderId = leaderId;
            this.expiresAt = expiresAt;
        }

        @Override
        public boolean isLeader() {
            return true; // Always leader in standalone
        }

        @Override
        public String resourceGroup() {
            return resourceGroup;
        }

        @Override
        public String leaderId() {
            return leaderId;
        }

        @Override
        public Instant expiresAt() {
            return expiresAt;
        }

        @Override
        public Promise<LeaderLease> renew() {
            expiresAt = Instant.now().plus(DEFAULT_LEASE_DURATION);
            return Promise.of(this);
        }

        @Override
        public Promise<Void> release() {
            return Promise.complete();
        }
    }

    /**
     * Local in-memory lock implementation.
     */
    private static final class LocalLock implements DistributedLock {
        private final String name;
        private volatile String holder;
        private volatile Instant expiresAt;

        LocalLock(String name, String holder, Duration duration) {
            this.name = name;
            this.holder = null;
            this.expiresAt = Instant.MIN;
        }

        synchronized boolean tryAcquire(String requestor, Duration timeout) {
            if (holder == null || Instant.now().isAfter(expiresAt)) {
                holder = requestor;
                expiresAt = Instant.now().plus(timeout);
                return true;
            }
            return holder.equals(requestor);
        }

        @Override
        public String lockName() {
            return name;
        }

        @Override
        public String holderId() {
            return holder;
        }

        @Override
        public Instant expiresAt() {
            return expiresAt;
        }

        @Override
        public Promise<DistributedLock> extend() {
            synchronized (this) {
                if (holder != null) {
                    expiresAt = expiresAt.plus(Duration.ofMinutes(5));
                    return Promise.of(this);
                }
            }
            return Promise.ofException(new IllegalStateException("Lock not held"));
        }

        @Override
        public Promise<Void> release() {
            synchronized (this) {
                holder = null;
                expiresAt = Instant.MIN;
            }
            return Promise.complete();
        }
    }
}
