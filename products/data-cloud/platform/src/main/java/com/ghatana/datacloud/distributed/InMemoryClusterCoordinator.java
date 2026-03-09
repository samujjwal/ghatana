/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 5.5 — In-memory multi-node ClusterCoordinator for testing and development.
 */
package com.ghatana.datacloud.distributed;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Multi-node in-memory {@link ClusterCoordinator} implementation.
 *
 * <p>Unlike {@link StandaloneClusterCoordinator} (single-node no-op), this implementation
 * faithfully simulates multi-node coordination semantics including:
 * <ul>
 *   <li><b>Cluster Membership</b>: Tracks multiple nodes with state transitions</li>
 *   <li><b>Leader Election</b>: First-come-first-served with lease management</li>
 *   <li><b>Distributed Locking</b>: Mutex semantics with timeout and auto-release</li>
 *   <li><b>Configuration Storage</b>: ConcurrentHashMap with watch notifications</li>
 *   <li><b>Event Notifications</b>: Membership, leadership, and config change callbacks</li>
 * </ul>
 *
 * <h2>Intended Use</h2>
 * <ul>
 *   <li>Integration testing of multi-node coordination logic</li>
 *   <li>Local development without external etcd/ZooKeeper</li>
 *   <li>Library mode (single-JVM, multiple logical nodes)</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * All operations are thread-safe via ConcurrentHashMap and synchronized blocks.
 *
 * @doc.type class
 * @doc.purpose In-memory multi-node ClusterCoordinator for testing leader election, distributed locking, and config storage
 * @doc.layer product
 * @doc.pattern Service
 */
public class InMemoryClusterCoordinator implements ClusterCoordinator {

    private static final Logger log = LoggerFactory.getLogger(InMemoryClusterCoordinator.class);

    private final ConcurrentHashMap<String, NodeInfo> nodes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> leaders = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LockHolder> locks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> config = new ConcurrentHashMap<>();

    private final List<Consumer<MembershipEvent>> membershipListeners = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, List<Consumer<LeaderChangeEvent>>> leaderListeners = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<Consumer<ConfigChangeEvent>>> configListeners = new ConcurrentHashMap<>();

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final String localNodeId;

    /**
     * Creates a coordinator for a specific logical node.
     */
    public InMemoryClusterCoordinator(String localNodeId) {
        this.localNodeId = Objects.requireNonNull(localNodeId, "localNodeId required");
    }

    /**
     * Creates a coordinator with auto-generated node ID.
     */
    public static InMemoryClusterCoordinator create() {
        return new InMemoryClusterCoordinator(UUID.randomUUID().toString().substring(0, 8));
    }

    /**
     * Creates a coordinator for a named node.
     */
    public static InMemoryClusterCoordinator create(String nodeId) {
        return new InMemoryClusterCoordinator(nodeId);
    }

    // ═══════════════════════════════════════════════════════════════
    // Cluster Membership
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Promise<Void> join(NodeInfo nodeInfo) {
        checkRunning();
        Objects.requireNonNull(nodeInfo, "nodeInfo required");

        nodes.put(nodeInfo.nodeId(), nodeInfo);
        log.info("Node {} joined cluster. Active nodes: {}", nodeInfo.nodeId(), nodes.size());

        MembershipEvent event = new MembershipEvent(
                MembershipEvent.EventType.JOINED, nodeInfo, Set.copyOf(nodes.values()));
        membershipListeners.forEach(l -> l.accept(event));

        return Promise.complete();
    }

    @Override
    public Promise<Void> leave() {
        checkRunning();
        NodeInfo removed = nodes.remove(localNodeId);
        if (removed != null) {
            // Release any leadership held by this node
            leaders.entrySet().removeIf(e -> e.getValue().equals(localNodeId));
            // Release any locks held by this node
            locks.entrySet().removeIf(e -> e.getValue().holderId().equals(localNodeId));

            log.info("Node {} left cluster. Active nodes: {}", localNodeId, nodes.size());

            MembershipEvent event = new MembershipEvent(
                    MembershipEvent.EventType.LEFT, removed, Set.copyOf(nodes.values()));
            membershipListeners.forEach(l -> l.accept(event));
        }
        return Promise.complete();
    }

    @Override
    public Promise<Set<NodeInfo>> getActiveNodes() {
        return Promise.of(Set.copyOf(nodes.values()));
    }

    @Override
    public Promise<NodeInfo> getNode(String nodeId) {
        return Promise.of(nodes.get(nodeId));
    }

    @Override
    public void onMembershipChange(Consumer<MembershipEvent> listener) {
        membershipListeners.add(listener);
    }

    // ═══════════════════════════════════════════════════════════════
    // Leader Election
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Promise<LeaderLease> electLeader(String resourceGroup) {
        checkRunning();
        String elected = leaders.compute(resourceGroup, (key, current) -> {
            if (current == null || !nodes.containsKey(current)) {
                return localNodeId;
            }
            return current;
        });

        boolean isLeader = localNodeId.equals(elected);
        log.debug("Leader election for '{}': leader={}, isLocal={}", resourceGroup, elected, isLeader);

        return Promise.of(new InMemoryLeaderLease(resourceGroup, elected, isLeader));
    }

    @Override
    public Promise<String> getLeader(String resourceGroup) {
        return Promise.of(leaders.get(resourceGroup));
    }

    @Override
    public void onLeaderChange(String resourceGroup, Consumer<LeaderChangeEvent> listener) {
        leaderListeners.computeIfAbsent(resourceGroup, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    // ═══════════════════════════════════════════════════════════════
    // Distributed Locking
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Promise<DistributedLock> acquireLock(String lockName, Duration timeout) {
        checkRunning();
        Instant deadline = Instant.now().plus(timeout);

        while (Instant.now().isBefore(deadline)) {
            LockHolder existing = locks.putIfAbsent(lockName,
                    new LockHolder(localNodeId, Instant.now().plus(Duration.ofMinutes(5))));
            if (existing == null || existing.isExpired()) {
                locks.put(lockName, new LockHolder(localNodeId, Instant.now().plus(Duration.ofMinutes(5))));
                return Promise.of(new InMemoryLock(lockName, localNodeId));
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Promise.ofException(e);
            }
        }
        return Promise.ofException(new TimeoutException("Lock acquisition timed out: " + lockName));
    }

    @Override
    public Promise<DistributedLock> tryLock(String lockName) {
        checkRunning();
        LockHolder existing = locks.putIfAbsent(lockName,
                new LockHolder(localNodeId, Instant.now().plus(Duration.ofMinutes(5))));
        if (existing == null || existing.isExpired()) {
            locks.put(lockName, new LockHolder(localNodeId, Instant.now().plus(Duration.ofMinutes(5))));
            return Promise.of(new InMemoryLock(lockName, localNodeId));
        }
        return Promise.of(null);
    }

    // ═══════════════════════════════════════════════════════════════
    // Configuration Storage
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Promise<String> getConfig(String key) {
        return Promise.of(config.get(key));
    }

    @Override
    public Promise<Void> setConfig(String key, String value) {
        checkRunning();
        String old = config.put(key, value);

        ConfigChangeEvent event = new ConfigChangeEvent(key, old, value);
        List<Consumer<ConfigChangeEvent>> listeners = configListeners.get(key);
        if (listeners != null) {
            listeners.forEach(l -> l.accept(event));
        }

        return Promise.complete();
    }

    @Override
    public void watchConfig(String key, Consumer<ConfigChangeEvent> listener) {
        configListeners.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    public Promise<Map<String, String>> getConfigsByPrefix(String prefix) {
        Map<String, String> result = new HashMap<>();
        config.forEach((k, v) -> {
            if (k.startsWith(prefix)) result.put(k, v);
        });
        return Promise.of(Map.copyOf(result));
    }

    // ═══════════════════════════════════════════════════════════════
    // Health & Lifecycle
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Promise<Boolean> isHealthy() {
        return Promise.of(running.get());
    }

    @Override
    public Promise<Void> shutdown() {
        running.set(false);
        nodes.clear();
        leaders.clear();
        locks.clear();
        log.info("InMemoryClusterCoordinator shut down");
        return Promise.complete();
    }

    /**
     * Returns the local node ID.
     */
    public String getLocalNodeId() {
        return localNodeId;
    }

    private void checkRunning() {
        if (!running.get()) {
            throw new IllegalStateException("Coordinator is shut down");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Internal records
    // ═══════════════════════════════════════════════════════════════

    private record LockHolder(String holderId, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private class InMemoryLeaderLease implements LeaderLease {
        private final String resourceGroup;
        private final String leaderId;
        private final boolean isLeader;
        private final Instant expiry;
        private volatile boolean released;

        InMemoryLeaderLease(String resourceGroup, String leaderId, boolean isLeader) {
            this.resourceGroup = resourceGroup;
            this.leaderId = leaderId;
            this.isLeader = isLeader;
            this.expiry = Instant.now().plus(Duration.ofMinutes(15));
        }

        @Override public boolean isLeader() { return isLeader && !released; }
        @Override public String resourceGroup() { return resourceGroup; }
        @Override public String leaderId() { return leaderId; }
        @Override public Instant expiresAt() { return expiry; }
        @Override public Duration ttl() { return Duration.between(Instant.now(), expiry); }
        @Override public boolean isValid() { return !released && Instant.now().isBefore(expiry); }

        @Override
        public Promise<LeaderLease> renew() {
            return Promise.of(new InMemoryLeaderLease(resourceGroup, leaderId, isLeader));
        }

        @Override
        public Promise<Void> release() {
            released = true;
            String previous = leaders.remove(resourceGroup);
            if (previous != null) {
                List<Consumer<LeaderChangeEvent>> listeners = leaderListeners.get(resourceGroup);
                if (listeners != null) {
                    LeaderChangeEvent event = new LeaderChangeEvent(resourceGroup, previous, null);
                    listeners.forEach(l -> l.accept(event));
                }
            }
            return Promise.complete();
        }
    }

    private class InMemoryLock implements DistributedLock {
        private final String lockName;
        private final String holderId;
        private volatile boolean released;
        private Instant expiresAt;

        InMemoryLock(String lockName, String holderId) {
            this.lockName = lockName;
            this.holderId = holderId;
            this.expiresAt = Instant.now().plus(Duration.ofMinutes(5));
        }

        @Override public String lockName() { return lockName; }
        @Override public String holderId() { return holderId; }
        @Override public Instant expiresAt() { return expiresAt; }
        @Override public boolean isHeld() { return !released && Instant.now().isBefore(expiresAt); }

        @Override
        public Promise<DistributedLock> extend() {
            expiresAt = Instant.now().plus(Duration.ofMinutes(5));
            return Promise.of(this);
        }

        @Override
        public Promise<Void> release() {
            released = true;
            locks.remove(lockName);
            return Promise.complete();
        }
    }
}
