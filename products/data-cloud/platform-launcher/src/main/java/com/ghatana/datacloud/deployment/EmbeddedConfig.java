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

import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration for embedded Data-Cloud mode.
 *
 * <p>Provides configuration for running Data-Cloud as an in-process library
 * without HTTP server. Designed for:
 * <ul>
 *   <li>AEP with embedded EventCloud (same JVM)</li>
 *   <li>Edge/IoT deployments with local storage</li>
 *   <li>Testing with in-memory storage</li>
 *   <li>Single-node processing applications</li>
 * </ul>
 *
 * <h2>Storage Types</h2>
 * <pre>
 * ┌────────────────┬─────────────────────────────────────────────┐
 * │  Storage Type  │  Description                                │
 * ├────────────────┼─────────────────────────────────────────────┤
 * │  IN_MEMORY     │  Fast, non-persistent (testing, ephemeral)  │
 * │  ROCKS_DB      │  Persistent, embedded key-value (production)│
 * │  SQLITE        │  Persistent, embedded SQL (edge/IoT)        │
 * │  H2            │  Persistent, embedded SQL (pure Java)       │
 * └────────────────┴─────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // For testing (in-memory, minimal)
 * EmbeddedConfig testConfig = EmbeddedConfig.forTesting();
 *
 * // For production (RocksDB, full features)
 * EmbeddedConfig prodConfig = EmbeddedConfig.forProduction("/var/data/events");
 *
 * // For edge/IoT (SQLite, resource-efficient)
 * EmbeddedConfig edgeConfig = EmbeddedConfig.forEdge("/data/events");
 * }</pre>
 *
 * @see DeploymentMode#EMBEDDED
 * @see EmbeddableDataCloud
 * @doc.type record
 * @doc.purpose Embedded mode configuration
 * @doc.layer core
 * @doc.pattern Value Object, Builder
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public record EmbeddedConfig(
        EmbeddedStorageType storageType,
        Path dataDirectory,
        boolean enableAI,
        int maxCacheEntries,
        boolean enableMetrics,
        StreamingConfig streamingConfig) {

    /**
     * Embedded storage backend options.
     */
    public enum EmbeddedStorageType {
        /**
         * In-memory storage, non-persistent.
         * <p>Fast startup, no persistence. Ideal for testing and ephemeral data.
         */
        IN_MEMORY,

        /**
         * RocksDB embedded key-value store.
         * <p>Persistent, high-performance. Recommended for production embedded use.
         */
        ROCKS_DB,

        /**
         * SQLite embedded SQL database.
         * <p>Persistent, lightweight. Ideal for edge/IoT deployments.
         */
        SQLITE,

        /**
         * H2 embedded SQL database (pure Java).
         * <p>Persistent, portable. Good for Java-only environments.
         */
        H2
    }

    /**
     * Streaming/EventCloud configuration for embedded mode.
     */
    public record StreamingConfig(
            int eventBufferSize,
            boolean enableCompression,
            long retentionMillis,
            int maxSubscribers) {

        /**
         * Creates default streaming configuration.
         * <p>10K events buffer, 24h retention, 100 max subscribers.
         *
         * @return default configuration
         */
        public static StreamingConfig defaults() {
            return new StreamingConfig(
                    10_000, // 10K events buffer
                    false, // No compression for in-process
                    86_400_000L, // 24 hour retention
                    100 // Max 100 subscribers
            );
        }

        /**
         * Creates minimal streaming configuration for testing/edge.
         * <p>1K events buffer, 1h retention, 10 max subscribers.
         *
         * @return minimal configuration
         */
        public static StreamingConfig minimal() {
            return new StreamingConfig(
                    1_000, // 1K events buffer
                    false, // No compression
                    3_600_000L, // 1 hour retention
                    10 // Max 10 subscribers
            );
        }

        /**
         * Creates high-throughput streaming configuration.
         * <p>100K events buffer, 7d retention, 1000 max subscribers.
         *
         * @return high-throughput configuration
         */
        public static StreamingConfig highThroughput() {
            return new StreamingConfig(
                    100_000, // 100K events buffer
                    true, // Enable compression
                    604_800_000L, // 7 day retention
                    1_000 // Max 1000 subscribers
            );
        }

        /**
         * Validates configuration values.
         */
        public StreamingConfig {
            if (eventBufferSize < 1) {
                throw new IllegalArgumentException("eventBufferSize must be >= 1");
            }
            if (retentionMillis < 0) {
                throw new IllegalArgumentException("retentionMillis must be >= 0");
            }
            if (maxSubscribers < 1) {
                throw new IllegalArgumentException("maxSubscribers must be >= 1");
            }
        }
    }

    /**
     * Canonical constructor with validation.
     */
    public EmbeddedConfig {
        Objects.requireNonNull(storageType, "storageType is required");
        Objects.requireNonNull(streamingConfig, "streamingConfig is required");

        // Persistent storage requires data directory
        if (storageType != EmbeddedStorageType.IN_MEMORY && dataDirectory == null) {
            throw new IllegalArgumentException(
                    storageType + " requires dataDirectory");
        }

        if (maxCacheEntries < 0) {
            throw new IllegalArgumentException("maxCacheEntries must be >= 0");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Factory Methods
    // ═══════════════════════════════════════════════════════════════

    /**
     * In-memory configuration for testing.
     *
     * <p>Features:
     * <ul>
     *   <li>Non-persistent storage</li>
     *   <li>Fast startup</li>
     *   <li>AI disabled</li>
     *   <li>Metrics disabled</li>
     *   <li>Small cache (1K entries)</li>
     * </ul>
     *
     * @return testing configuration
     */
    public static EmbeddedConfig forTesting() {
        return new EmbeddedConfig(
                EmbeddedStorageType.IN_MEMORY,
                null, // No data directory
                false, // Disable AI
                1_000, // Small cache
                false, // No metrics
                StreamingConfig.minimal());
    }

    /**
     * RocksDB configuration for production embedded deployments.
     *
     * <p>Features:
     * <ul>
     *   <li>Persistent RocksDB storage</li>
     *   <li>AI enabled</li>
     *   <li>Metrics enabled</li>
     *   <li>Large cache (100K entries)</li>
     * </ul>
     *
     * @param dataDir directory for RocksDB data files
     * @return production configuration
     */
    public static EmbeddedConfig forProduction(String dataDir) {
        Objects.requireNonNull(dataDir, "dataDir is required");
        return new EmbeddedConfig(
                EmbeddedStorageType.ROCKS_DB,
                Path.of(dataDir),
                true, // Enable AI
                100_000, // Large cache
                true, // Enable metrics
                StreamingConfig.defaults());
    }

    /**
     * SQLite configuration for edge/IoT deployments.
     *
     * <p>Features:
     * <ul>
     *   <li>Persistent SQLite storage</li>
     *   <li>AI disabled (resource constraints)</li>
     *   <li>Metrics disabled</li>
     *   <li>Moderate cache (10K entries)</li>
     * </ul>
     *
     * @param dataDir directory for SQLite database
     * @return edge configuration
     */
    public static EmbeddedConfig forEdge(String dataDir) {
        Objects.requireNonNull(dataDir, "dataDir is required");
        return new EmbeddedConfig(
                EmbeddedStorageType.SQLITE,
                Path.of(dataDir),
                false, // Disable AI (resource constraints)
                10_000, // Moderate cache
                false, // Disable metrics
                StreamingConfig.minimal());
    }

    /**
     * H2 configuration for pure Java embedded deployments.
     *
     * <p>Features:
     * <ul>
     *   <li>Persistent H2 storage (pure Java)</li>
     *   <li>AI optional</li>
     *   <li>Metrics optional</li>
     * </ul>
     *
     * @param dataDir directory for H2 database
     * @param enableAI whether to enable AI capabilities
     * @return H2 configuration
     */
    public static EmbeddedConfig forH2(String dataDir, boolean enableAI) {
        Objects.requireNonNull(dataDir, "dataDir is required");
        return new EmbeddedConfig(
                EmbeddedStorageType.H2,
                Path.of(dataDir),
                enableAI,
                50_000, // Moderate cache
                enableAI, // Metrics follow AI
                StreamingConfig.defaults());
    }

    /**
     * Development configuration using H2 (pure Java, no native dependencies).
     *
     * <p>Features:
     * <ul>
     *   <li>H2 storage (pure Java, works everywhere)</li>
     *   <li>AI disabled (faster startup)</li>
     *   <li>Metrics enabled</li>
     *   <li>Moderate cache (50K entries)</li>
     * </ul>
     *
     * @param dataDir directory for H2 database
     * @return development configuration
     */
    public static EmbeddedConfig forDevelopment(String dataDir) {
        return forH2(dataDir, false);
    }

    // ═══════════════════════════════════════════════════════════════
    // Builder for Custom Configuration
    // ═══════════════════════════════════════════════════════════════

    /**
     * Creates a builder for custom embedded configuration.
     *
     * @param storageType the storage backend type
     * @return a new builder
     */
    public static Builder builder(EmbeddedStorageType storageType) {
        return new Builder(storageType);
    }

    /**
     * Builder for custom EmbeddedConfig.
     */
    public static final class Builder {
        private final EmbeddedStorageType storageType;
        private Path dataDirectory;
        private boolean enableAI = false;
        private int maxCacheEntries = 10_000;
        private boolean enableMetrics = false;
        private StreamingConfig streamingConfig = StreamingConfig.defaults();

        private Builder(EmbeddedStorageType storageType) {
            this.storageType = Objects.requireNonNull(storageType);
        }

        public Builder dataDirectory(String path) {
            this.dataDirectory = path != null ? Path.of(path) : null;
            return this;
        }

        public Builder dataDirectory(Path path) {
            this.dataDirectory = path;
            return this;
        }

        public Builder enableAI(boolean enable) {
            this.enableAI = enable;
            return this;
        }

        public Builder maxCacheEntries(int size) {
            this.maxCacheEntries = size;
            return this;
        }

        public Builder enableMetrics(boolean enable) {
            this.enableMetrics = enable;
            return this;
        }

        public Builder streamingConfig(StreamingConfig config) {
            this.streamingConfig = Objects.requireNonNull(config);
            return this;
        }

        public EmbeddedConfig build() {
            return new EmbeddedConfig(
                    storageType,
                    dataDirectory,
                    enableAI,
                    maxCacheEntries,
                    enableMetrics,
                    streamingConfig);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Utility Methods
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns true if this configuration uses persistent storage.
     *
     * @return true for ROCKS_DB, SQLITE, H2; false for IN_MEMORY
     */
    public boolean isPersistent() {
        return storageType != EmbeddedStorageType.IN_MEMORY;
    }

    /**
     * Returns a copy with AI enabled/disabled.
     *
     * @param enable whether to enable AI
     * @return new configuration with updated AI setting
     */
    public EmbeddedConfig withAI(boolean enable) {
        return new EmbeddedConfig(
                storageType, dataDirectory, enable,
                maxCacheEntries, enableMetrics, streamingConfig);
    }

    /**
     * Returns a copy with metrics enabled/disabled.
     *
     * @param enable whether to enable metrics
     * @return new configuration with updated metrics setting
     */
    public EmbeddedConfig withMetrics(boolean enable) {
        return new EmbeddedConfig(
                storageType, dataDirectory, enableAI,
                maxCacheEntries, enable, streamingConfig);
    }
}
