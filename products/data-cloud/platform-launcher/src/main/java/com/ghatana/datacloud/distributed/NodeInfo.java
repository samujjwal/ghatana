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

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Information about a cluster node.
 *
 * <p>Contains metadata about a node in the distributed cluster
 * including identity, network location, capabilities, and health.
 *
 * @see ClusterCoordinator
 * @doc.type record
 * @doc.purpose Node metadata for cluster coordination
 * @doc.layer core
 * @doc.pattern Value Object
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public record NodeInfo(
        String nodeId,
        String host,
        int port,
        int grpcPort,
        NodeState state,
        Set<String> capabilities,
        Set<Integer> partitions,
        Map<String, String> labels,
        Instant startedAt,
        Instant lastHeartbeat) {

    /**
     * Node state in the cluster.
     */
    public enum NodeState {
        /** Node is starting up */
        STARTING,
        /** Node is healthy and serving requests */
        RUNNING,
        /** Node is draining (preparing to shut down) */
        DRAINING,
        /** Node is shutting down */
        STOPPING,
        /** Node is not responding */
        UNHEALTHY,
        /** Node has left the cluster */
        OFFLINE
    }

    /**
     * Common capability constants.
     */
    public static final String CAPABILITY_STORAGE = "storage";
    public static final String CAPABILITY_STREAMING = "streaming";
    public static final String CAPABILITY_AI = "ai";
    public static final String CAPABILITY_QUERY = "query";

    /**
     * Canonical constructor with validation.
     */
    public NodeInfo {
        Objects.requireNonNull(nodeId, "nodeId is required");
        Objects.requireNonNull(host, "host is required");
        Objects.requireNonNull(state, "state is required");

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be 1-65535");
        }

        // Make collections immutable
        capabilities = capabilities != null ? Set.copyOf(capabilities) : Set.of();
        partitions = partitions != null ? Set.copyOf(partitions) : Set.of();
        labels = labels != null ? Map.copyOf(labels) : Map.of();
    }

    /**
     * Creates a builder for NodeInfo.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for NodeInfo.
     */
    public static final class Builder {
        private String nodeId;
        private String host;
        private int port = 8080;
        private int grpcPort = 9090;
        private NodeState state = NodeState.STARTING;
        private Set<String> capabilities = Set.of();
        private Set<Integer> partitions = Set.of();
        private Map<String, String> labels = Map.of();
        private Instant startedAt = Instant.now();
        private Instant lastHeartbeat = Instant.now();

        private Builder() {
        }

        public Builder nodeId(String nodeId) {
            this.nodeId = nodeId;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder grpcPort(int grpcPort) {
            this.grpcPort = grpcPort;
            return this;
        }

        public Builder state(NodeState state) {
            this.state = state;
            return this;
        }

        public Builder capabilities(String... capabilities) {
            this.capabilities = Set.of(capabilities);
            return this;
        }

        public Builder capabilities(Set<String> capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        public Builder partitions(Integer... partitions) {
            this.partitions = Set.of(partitions);
            return this;
        }

        public Builder partitions(Set<Integer> partitions) {
            this.partitions = partitions;
            return this;
        }

        public Builder labels(Map<String, String> labels) {
            this.labels = labels;
            return this;
        }

        public Builder label(String key, String value) {
            this.labels = Map.of(key, value);
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = startedAt;
            return this;
        }

        public NodeInfo build() {
            return new NodeInfo(
                    nodeId, host, port, grpcPort, state,
                    capabilities, partitions, labels,
                    startedAt, lastHeartbeat);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Utility Methods
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns the HTTP address for this node.
     *
     * @return HTTP address (host:port)
     */
    public String httpAddress() {
        return host + ":" + port;
    }

    /**
     * Returns the gRPC address for this node.
     *
     * @return gRPC address (host:grpcPort)
     */
    public String grpcAddress() {
        return host + ":" + grpcPort;
    }

    /**
     * Returns true if this node is healthy and serving.
     *
     * @return true if RUNNING
     */
    public boolean isHealthy() {
        return state == NodeState.RUNNING;
    }

    /**
     * Returns true if this node is available for new work.
     *
     * @return true if RUNNING (not draining)
     */
    public boolean isAvailable() {
        return state == NodeState.RUNNING;
    }

    /**
     * Returns true if this node has a specific capability.
     *
     * @param capability the capability to check
     * @return true if node has capability
     */
    public boolean hasCapability(String capability) {
        return capabilities.contains(capability);
    }

    /**
     * Returns true if this node owns a specific partition.
     *
     * @param partitionId the partition ID
     * @return true if node owns partition
     */
    public boolean ownsPartition(int partitionId) {
        return partitions.contains(partitionId);
    }

    /**
     * Returns an updated copy with new state.
     *
     * @param newState the new state
     * @return updated NodeInfo
     */
    public NodeInfo withState(NodeState newState) {
        return new NodeInfo(
                nodeId, host, port, grpcPort, newState,
                capabilities, partitions, labels,
                startedAt, Instant.now());
    }

    /**
     * Returns an updated copy with heartbeat timestamp.
     *
     * @return updated NodeInfo with current timestamp
     */
    public NodeInfo withHeartbeat() {
        return new NodeInfo(
                nodeId, host, port, grpcPort, state,
                capabilities, partitions, labels,
                startedAt, Instant.now());
    }
}
