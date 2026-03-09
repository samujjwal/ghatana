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

import java.util.Objects;

/**
 * Unified configuration for Data-Cloud deployment mode.
 *
 * <p>This record encapsulates all configuration needed to deploy Data-Cloud
 * in any of the three supported modes: EMBEDDED, STANDALONE, or DISTRIBUTED.
 *
 * <h2>Mode-Specific Configuration</h2>
 * <pre>
 * ┌─────────────────┬──────────────────┬─────────────────┬────────────────┐
 * │     Field       │    EMBEDDED      │   STANDALONE    │  DISTRIBUTED   │
 * ├─────────────────┼──────────────────┼─────────────────┼────────────────┤
 * │ embeddedConfig  │    Required      │      null       │      null      │
 * │ serverConfig    │      null        │    Required     │    Required    │
 * │ clusterConfig   │      null        │      null       │    Required    │
 * └─────────────────┴──────────────────┴─────────────────┴────────────────┘
 * </pre>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Embedded for testing
 * DeploymentConfig config = DeploymentConfig.embeddedForTesting();
 *
 * // Embedded for production (AEP integration)
 * DeploymentConfig config = DeploymentConfig.embeddedForProduction("/data/events");
 *
 * // Standalone development
 * DeploymentConfig config = DeploymentConfig.standalone();
 *
 * // Distributed production
 * DeploymentConfig config = DeploymentConfig.distributed(
 *     ServerConfig.on(8080),
 *     ClusterConfig.etcd("etcd-1:2379", "etcd-2:2379")
 * );
 * }</pre>
 *
 * @see DeploymentMode
 * @see EmbeddedConfig
 * @see ServerConfig
 * @see ClusterConfig
 * @doc.type record
 * @doc.purpose Unified deployment configuration
 * @doc.layer core
 * @doc.pattern Value Object, Factory
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public record DeploymentConfig(
        DeploymentMode mode,
        EmbeddedConfig embeddedConfig,
        ServerConfig serverConfig,
        ClusterConfig clusterConfig) {

    /**
     * Canonical constructor with validation.
     */
    public DeploymentConfig {
        Objects.requireNonNull(mode, "mode is required");

        // Validate config matches mode
        switch (mode) {
            case EMBEDDED -> {
                if (embeddedConfig == null) {
                    throw new IllegalArgumentException(
                            "EMBEDDED mode requires embeddedConfig");
                }
                if (serverConfig != null) {
                    throw new IllegalArgumentException(
                            "EMBEDDED mode does not use serverConfig");
                }
                if (clusterConfig != null) {
                    throw new IllegalArgumentException(
                            "EMBEDDED mode does not use clusterConfig");
                }
            }
            case STANDALONE -> {
                if (serverConfig == null) {
                    throw new IllegalArgumentException(
                            "STANDALONE mode requires serverConfig");
                }
                if (embeddedConfig != null) {
                    throw new IllegalArgumentException(
                            "STANDALONE mode does not use embeddedConfig");
                }
                if (clusterConfig != null) {
                    throw new IllegalArgumentException(
                            "STANDALONE mode does not use clusterConfig");
                }
            }
            case DISTRIBUTED -> {
                if (serverConfig == null) {
                    throw new IllegalArgumentException(
                            "DISTRIBUTED mode requires serverConfig");
                }
                if (clusterConfig == null) {
                    throw new IllegalArgumentException(
                            "DISTRIBUTED mode requires clusterConfig");
                }
                if (embeddedConfig != null) {
                    throw new IllegalArgumentException(
                            "DISTRIBUTED mode does not use embeddedConfig");
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // EMBEDDED Mode Factory Methods
    // ═══════════════════════════════════════════════════════════════

    /**
     * Creates EMBEDDED mode configuration.
     *
     * @param config the embedded configuration
     * @return deployment configuration for embedded mode
     */
    public static DeploymentConfig embedded(EmbeddedConfig config) {
        return new DeploymentConfig(DeploymentMode.EMBEDDED, config, null, null);
    }

    /**
     * Creates EMBEDDED mode for testing (in-memory storage).
     *
     * @return testing deployment configuration
     */
    public static DeploymentConfig embeddedForTesting() {
        return embedded(EmbeddedConfig.forTesting());
    }

    /**
     * Creates EMBEDDED mode for production (RocksDB storage).
     *
     * @param dataDir directory for persistent storage
     * @return production embedded deployment configuration
     */
    public static DeploymentConfig embeddedForProduction(String dataDir) {
        return embedded(EmbeddedConfig.forProduction(dataDir));
    }

    /**
     * Creates EMBEDDED mode for edge/IoT (SQLite storage).
     *
     * @param dataDir directory for SQLite database
     * @return edge deployment configuration
     */
    public static DeploymentConfig embeddedForEdge(String dataDir) {
        return embedded(EmbeddedConfig.forEdge(dataDir));
    }

    // ═══════════════════════════════════════════════════════════════
    // STANDALONE Mode Factory Methods
    // ═══════════════════════════════════════════════════════════════

    /**
     * Creates STANDALONE mode configuration with custom server settings.
     *
     * @param serverConfig the server configuration
     * @return standalone deployment configuration
     */
    public static DeploymentConfig standalone(ServerConfig serverConfig) {
        return new DeploymentConfig(DeploymentMode.STANDALONE, null, serverConfig, null);
    }

    /**
     * Creates STANDALONE mode with default server settings (port 8080).
     *
     * @return default standalone deployment configuration
     */
    public static DeploymentConfig standalone() {
        return standalone(ServerConfig.defaultConfig());
    }

    /**
     * Creates STANDALONE mode on specified port.
     *
     * @param port the HTTP port
     * @return standalone deployment configuration
     */
    public static DeploymentConfig standalone(int port) {
        return standalone(ServerConfig.on(port));
    }

    // ═══════════════════════════════════════════════════════════════
    // DISTRIBUTED Mode Factory Methods
    // ═══════════════════════════════════════════════════════════════

    /**
     * Creates DISTRIBUTED mode configuration.
     *
     * @param serverConfig the server configuration
     * @param clusterConfig the cluster configuration
     * @return distributed deployment configuration
     */
    public static DeploymentConfig distributed(
            ServerConfig serverConfig,
            ClusterConfig clusterConfig) {
        return new DeploymentConfig(
                DeploymentMode.DISTRIBUTED, null, serverConfig, clusterConfig);
    }

    /**
     * Creates DISTRIBUTED mode with etcd coordination.
     *
     * @param port HTTP port
     * @param etcdEndpoints etcd cluster endpoints
     * @return distributed deployment configuration
     */
    public static DeploymentConfig distributedWithEtcd(
            int port, String... etcdEndpoints) {
        return distributed(
                ServerConfig.on(port),
                ClusterConfig.etcd(etcdEndpoints));
    }

    /**
     * Creates DISTRIBUTED mode with Kubernetes-native coordination.
     *
     * @param port HTTP port
     * @param namespace Kubernetes namespace
     * @return distributed deployment configuration
     */
    public static DeploymentConfig distributedWithKubernetes(
            int port, String namespace) {
        return distributed(
                ServerConfig.on(port),
                ClusterConfig.kubernetes(namespace));
    }

    // ═══════════════════════════════════════════════════════════════
    // Utility Methods
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns true if this is embedded mode.
     *
     * @return true for EMBEDDED mode
     */
    public boolean isEmbedded() {
        return mode == DeploymentMode.EMBEDDED;
    }

    /**
     * Returns true if this is standalone mode.
     *
     * @return true for STANDALONE mode
     */
    public boolean isStandalone() {
        return mode == DeploymentMode.STANDALONE;
    }

    /**
     * Returns true if this is distributed mode.
     *
     * @return true for DISTRIBUTED mode
     */
    public boolean isDistributed() {
        return mode == DeploymentMode.DISTRIBUTED;
    }

    /**
     * Returns true if this mode requires an HTTP server.
     *
     * @return true for STANDALONE and DISTRIBUTED modes
     */
    public boolean requiresServer() {
        return mode.requiresServer();
    }

    /**
     * Returns true if this mode supports horizontal scaling.
     *
     * @return true for DISTRIBUTED mode
     */
    public boolean supportsHorizontalScale() {
        return mode.supportsHorizontalScale();
    }

    /**
     * Returns the server base URL if applicable.
     *
     * @return base URL or null for EMBEDDED mode
     */
    public String baseUrl() {
        return serverConfig != null ? serverConfig.baseUrl() : null;
    }

    /**
     * Returns the node ID for distributed mode.
     *
     * @return node ID or null for non-distributed modes
     */
    public String nodeId() {
        return clusterConfig != null ? clusterConfig.nodeId() : null;
    }
}
