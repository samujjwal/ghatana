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

package com.ghatana.datacloud.deployment;

import java.util.List;
import java.util.Objects;

/**
 * Configuration for distributed cluster mode.
 *
 * <p>Configures cluster coordination, partitioning, and replication
 * for Data-Cloud running in DISTRIBUTED mode.
 *
 * <h2>Coordinator Options</h2>
 * <pre>
 * ┌────────────────┬─────────────────────────────────────────────┐
 * │  Coordinator   │  Description                                │
 * ├────────────────┼─────────────────────────────────────────────┤
 * │  ETCD          │  Fast, reliable K/V store (recommended)     │
 * │  CONSUL        │  Service mesh with built-in discovery       │
 * │  ZOOKEEPER     │  Mature, battle-tested coordination         │
 * │  KUBERNETES    │  K8s-native coordination via API            │
 * └────────────────┴─────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // etcd cluster
 * ClusterConfig config = ClusterConfig.etcd(
 *     "etcd-1:2379", "etcd-2:2379", "etcd-3:2379");
 *
 * // With custom settings
 * ClusterConfig config = ClusterConfig.builder(CoordinatorType.ETCD)
 *     .endpoints("etcd-1:2379", "etcd-2:2379")
 *     .partitions(64)
 *     .replicationFactor(3)
 *     .build();
 * }</pre>
 *
 * @see DeploymentMode#DISTRIBUTED
 * @see ClusterCoordinator
 * @doc.type record
 * @doc.purpose Cluster configuration for distributed mode
 * @doc.layer core
 * @doc.pattern Value Object
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public record ClusterConfig(
        CoordinatorType coordinatorType,
        List<String> endpoints,
        String nodeId,
        int partitions,
        int replicationFactor,
        long heartbeatIntervalMillis,
        long electionTimeoutMillis,
        String namespace,
        boolean enableTls,
        String tlsCertPath,
        String tlsKeyPath) {

    /**
     * Cluster coordinator backend type.
     */
    public enum CoordinatorType {
        /**
         * etcd distributed key-value store.
         * <p>Recommended for most deployments.
         */
        ETCD,

        /**
         * HashiCorp Consul.
         * <p>Good for service mesh environments.
         */
        CONSUL,

        /**
         * Apache ZooKeeper.
         * <p>Mature, battle-tested.
         */
        ZOOKEEPER,

        /**
         * Kubernetes API-based coordination.
         * <p>For K8s-native deployments.
         */
        KUBERNETES
    }

    /**
     * Default number of partitions.
     */
    public static final int DEFAULT_PARTITIONS = 64;

    /**
     * Default replication factor.
     */
    public static final int DEFAULT_REPLICATION_FACTOR = 3;

    /**
     * Default heartbeat interval (1 second).
     */
    public static final long DEFAULT_HEARTBEAT_MILLIS = 1000L;

    /**
     * Default election timeout (5 seconds).
     */
    public static final long DEFAULT_ELECTION_TIMEOUT_MILLIS = 5000L;

    /**
     * Canonical constructor with validation.
     */
    public ClusterConfig {
        Objects.requireNonNull(coordinatorType, "coordinatorType is required");
        Objects.requireNonNull(endpoints, "endpoints is required");
        Objects.requireNonNull(nodeId, "nodeId is required");

        if (endpoints.isEmpty()) {
            throw new IllegalArgumentException("endpoints must not be empty");
        }
        if (partitions < 1) {
            throw new IllegalArgumentException("partitions must be >= 1");
        }
        if (replicationFactor < 1) {
            throw new IllegalArgumentException("replicationFactor must be >= 1");
        }
        if (replicationFactor > partitions) {
            throw new IllegalArgumentException(
                    "replicationFactor cannot exceed partitions");
        }
        if (heartbeatIntervalMillis < 100) {
            throw new IllegalArgumentException(
                    "heartbeatIntervalMillis must be >= 100");
        }
        if (electionTimeoutMillis < heartbeatIntervalMillis * 2) {
            throw new IllegalArgumentException(
                    "electionTimeoutMillis must be >= 2x heartbeatIntervalMillis");
        }
        if (enableTls) {
            Objects.requireNonNull(tlsCertPath, "tlsCertPath required when TLS enabled");
            Objects.requireNonNull(tlsKeyPath, "tlsKeyPath required when TLS enabled");
        }

        // Make endpoints immutable
        endpoints = List.copyOf(endpoints);
    }

    // ═══════════════════════════════════════════════════════════════
    // Factory Methods
    // ═══════════════════════════════════════════════════════════════

    /**
     * Creates an etcd cluster configuration.
     *
     * @param endpoints etcd endpoints (host:port)
     * @return etcd cluster configuration
     */
    public static ClusterConfig etcd(String... endpoints) {
        return builder(CoordinatorType.ETCD)
                .endpoints(endpoints)
                .build();
    }

    /**
     * Creates a Consul cluster configuration.
     *
     * @param endpoints Consul endpoints (host:port)
     * @return Consul cluster configuration
     */
    public static ClusterConfig consul(String... endpoints) {
        return builder(CoordinatorType.CONSUL)
                .endpoints(endpoints)
                .build();
    }

    /**
     * Creates a ZooKeeper cluster configuration.
     *
     * @param endpoints ZooKeeper endpoints (host:port)
     * @return ZooKeeper cluster configuration
     */
    public static ClusterConfig zookeeper(String... endpoints) {
        return builder(CoordinatorType.ZOOKEEPER)
                .endpoints(endpoints)
                .build();
    }

    /**
     * Creates a Kubernetes-native cluster configuration.
     *
     * @param namespace Kubernetes namespace
     * @return Kubernetes cluster configuration
     */
    public static ClusterConfig kubernetes(String namespace) {
        return builder(CoordinatorType.KUBERNETES)
                .endpoints("kubernetes.default.svc")
                .namespace(namespace)
                .build();
    }

    /**
     * Creates a builder for custom configuration.
     *
     * @param coordinatorType the coordinator backend type
     * @return new builder
     */
    public static Builder builder(CoordinatorType coordinatorType) {
        return new Builder(coordinatorType);
    }

    /**
     * Builder for custom ClusterConfig.
     */
    public static final class Builder {
        private final CoordinatorType coordinatorType;
        private List<String> endpoints = List.of();
        private String nodeId = generateNodeId();
        private int partitions = DEFAULT_PARTITIONS;
        private int replicationFactor = DEFAULT_REPLICATION_FACTOR;
        private long heartbeatIntervalMillis = DEFAULT_HEARTBEAT_MILLIS;
        private long electionTimeoutMillis = DEFAULT_ELECTION_TIMEOUT_MILLIS;
        private String namespace = "default";
        private boolean enableTls = false;
        private String tlsCertPath = null;
        private String tlsKeyPath = null;

        private Builder(CoordinatorType coordinatorType) {
            this.coordinatorType = Objects.requireNonNull(coordinatorType);
        }

        public Builder endpoints(String... endpoints) {
            this.endpoints = List.of(endpoints);
            return this;
        }

        public Builder endpoints(List<String> endpoints) {
            this.endpoints = endpoints;
            return this;
        }

        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public Builder partitions(int partitions) {
            this.partitions = partitions;
            return this;
        }

        public Builder replicationFactor(int factor) {
            this.replicationFactor = factor;
            return this;
        }

        public Builder heartbeatIntervalMillis(long millis) {
            this.heartbeatIntervalMillis = millis;
            return this;
        }

        public Builder electionTimeoutMillis(long millis) {
            this.electionTimeoutMillis = millis;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder enableTls(String certPath, String keyPath) {
            this.enableTls = true;
            this.tlsCertPath = certPath;
            this.tlsKeyPath = keyPath;
            return this;
        }

        public ClusterConfig build() {
            return new ClusterConfig(
                    coordinatorType, endpoints, nodeId,
                    partitions, replicationFactor,
                    heartbeatIntervalMillis, electionTimeoutMillis,
                    namespace, enableTls, tlsCertPath, tlsKeyPath);
        }

        private static String generateNodeId() {
            String hostname = System.getenv("HOSTNAME");
            if (hostname != null && !hostname.isBlank()) {
                return hostname;
            }
            try {
                return java.net.InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                return "node-" + java.util.UUID.randomUUID().toString().substring(0, 8);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Utility Methods
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns a copy with updated partition count.
     *
     * @param partitions the new partition count
     * @return new configuration with updated partitions
     */
    public ClusterConfig withPartitions(int partitions) {
        return new ClusterConfig(
                coordinatorType, endpoints, nodeId,
                partitions, replicationFactor,
                heartbeatIntervalMillis, electionTimeoutMillis,
                namespace, enableTls, tlsCertPath, tlsKeyPath);
    }

    /**
     * Returns a copy with updated replication factor.
     *
     * @param factor the new replication factor
     * @return new configuration with updated replication
     */
    public ClusterConfig withReplicationFactor(int factor) {
        return new ClusterConfig(
                coordinatorType, endpoints, nodeId,
                partitions, factor,
                heartbeatIntervalMillis, electionTimeoutMillis,
                namespace, enableTls, tlsCertPath, tlsKeyPath);
    }

    /**
     * Returns the first endpoint for simple single-node testing.
     *
     * @return first endpoint
     */
    public String primaryEndpoint() {
        return endpoints.get(0);
    }

    /**
     * Returns true if this is a multi-node cluster.
     *
     * @return true if more than one endpoint
     */
    public boolean isMultiNode() {
        return endpoints.size() > 1;
    }
}
