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
 * HTTP/gRPC server configuration for standalone and distributed modes.
 *
 * <p>Configures the network server for Data-Cloud when running in
 * STANDALONE or DISTRIBUTED mode.
 *
 * <h2>Usage Examples</h2>
 * <pre>{@code
 * // Default configuration (port 8080)
 * ServerConfig config = ServerConfig.defaultConfig();
 *
 * // Custom port
 * ServerConfig config = ServerConfig.on(9090);
 *
 * // Full customization
 * ServerConfig config = ServerConfig.builder()
 *     .host("0.0.0.0")
 *     .port(8080)
 *     .workerThreads(8)
 *     .enableGrpc(true)
 *     .build();
 * }</pre>
 *
 * @see DeploymentMode#STANDALONE
 * @see DeploymentMode#DISTRIBUTED
 * @doc.type record
 * @doc.purpose Server configuration for network modes
 * @doc.layer core
 * @doc.pattern Value Object
 *
 * @author Ghatana AI Platform
 * @since 1.0.0
 */
public record ServerConfig(
        String host,
        int port,
        int workerThreads,
        boolean enableGrpc,
        int grpcPort,
        boolean enableTls,
        String tlsCertPath,
        String tlsKeyPath,
        int maxRequestSize,
        long idleTimeoutMillis) {

    /**
     * Default HTTP port.
     */
    public static final int DEFAULT_PORT = 8080;

    /**
     * Default gRPC port.
     */
    public static final int DEFAULT_GRPC_PORT = 9090;

    /**
     * Default worker threads (2x CPU cores).
     */
    public static final int DEFAULT_WORKER_THREADS =
            Runtime.getRuntime().availableProcessors() * 2;

    /**
     * Default max request size (10MB).
     */
    public static final int DEFAULT_MAX_REQUEST_SIZE = 10 * 1024 * 1024;

    /**
     * Default idle timeout (30 seconds).
     */
    public static final long DEFAULT_IDLE_TIMEOUT_MILLIS = 30_000L;

    /**
     * Canonical constructor with validation.
     */
    public ServerConfig {
        Objects.requireNonNull(host, "host is required");

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be 1-65535");
        }
        if (workerThreads < 1) {
            throw new IllegalArgumentException("workerThreads must be >= 1");
        }
        if (enableGrpc && (grpcPort < 1 || grpcPort > 65535)) {
            throw new IllegalArgumentException("grpcPort must be 1-65535");
        }
        if (enableTls) {
            Objects.requireNonNull(tlsCertPath, "tlsCertPath required when TLS enabled");
            Objects.requireNonNull(tlsKeyPath, "tlsKeyPath required when TLS enabled");
        }
        if (maxRequestSize < 1) {
            throw new IllegalArgumentException("maxRequestSize must be >= 1");
        }
        if (idleTimeoutMillis < 0) {
            throw new IllegalArgumentException("idleTimeoutMillis must be >= 0");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Factory Methods
    // ═══════════════════════════════════════════════════════════════

    /**
     * Creates default server configuration.
     *
     * <p>Listens on 0.0.0.0:8080 with gRPC disabled.
     *
     * @return default configuration
     */
    public static ServerConfig defaultConfig() {
        return new ServerConfig(
                "0.0.0.0",
                DEFAULT_PORT,
                DEFAULT_WORKER_THREADS,
                false, // gRPC disabled
                DEFAULT_GRPC_PORT,
                false, // TLS disabled
                null,
                null,
                DEFAULT_MAX_REQUEST_SIZE,
                DEFAULT_IDLE_TIMEOUT_MILLIS);
    }

    /**
     * Creates server configuration on specified port.
     *
     * @param port the HTTP port
     * @return configuration on specified port
     */
    public static ServerConfig on(int port) {
        return new ServerConfig(
                "0.0.0.0",
                port,
                DEFAULT_WORKER_THREADS,
                false,
                DEFAULT_GRPC_PORT,
                false,
                null,
                null,
                DEFAULT_MAX_REQUEST_SIZE,
                DEFAULT_IDLE_TIMEOUT_MILLIS);
    }

    /**
     * Creates server configuration for localhost only.
     *
     * @param port the HTTP port
     * @return localhost-only configuration
     */
    public static ServerConfig localhost(int port) {
        return new ServerConfig(
                "127.0.0.1",
                port,
                DEFAULT_WORKER_THREADS,
                false,
                DEFAULT_GRPC_PORT,
                false,
                null,
                null,
                DEFAULT_MAX_REQUEST_SIZE,
                DEFAULT_IDLE_TIMEOUT_MILLIS);
    }

    /**
     * Creates a builder for custom configuration.
     *
     * @return new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates server configuration from environment variables.
     *
     * <p>Supported environment variables:
     * <ul>
     *   <li>{@code SERVER_HOST} - Host to bind (default: 0.0.0.0)</li>
     *   <li>{@code SERVER_PORT} - HTTP port (default: 8080)</li>
     *   <li>{@code SERVER_WORKER_THREADS} - Worker threads (default: 2x CPU)</li>
     *   <li>{@code SERVER_GRPC_ENABLED} - Enable gRPC (default: false)</li>
     *   <li>{@code SERVER_GRPC_PORT} - gRPC port (default: 9090)</li>
     *   <li>{@code SERVER_TLS_ENABLED} - Enable TLS (default: false)</li>
     *   <li>{@code SERVER_TLS_CERT_PATH} - TLS certificate path</li>
     *   <li>{@code SERVER_TLS_KEY_PATH} - TLS key path</li>
     *   <li>{@code SERVER_MAX_REQUEST_SIZE} - Max request size in bytes</li>
     *   <li>{@code SERVER_IDLE_TIMEOUT_MS} - Idle timeout in milliseconds</li>
     * </ul>
     *
     * @return configuration from environment
     */
    public static ServerConfig fromEnvironment() {
        Builder builder = builder();

        String host = System.getenv("SERVER_HOST");
        if (host != null && !host.isEmpty()) {
            builder.host(host);
        }

        String port = System.getenv("SERVER_PORT");
        if (port != null && !port.isEmpty()) {
            try {
                builder.port(Integer.parseInt(port));
            } catch (NumberFormatException ignored) {
                // Use default
            }
        }

        String workerThreads = System.getenv("SERVER_WORKER_THREADS");
        if (workerThreads != null && !workerThreads.isEmpty()) {
            try {
                builder.workerThreads(Integer.parseInt(workerThreads));
            } catch (NumberFormatException ignored) {
                // Use default
            }
        }

        String grpcEnabled = System.getenv("SERVER_GRPC_ENABLED");
        if ("true".equalsIgnoreCase(grpcEnabled)) {
            builder.enableGrpc(true);
        }

        String grpcPort = System.getenv("SERVER_GRPC_PORT");
        if (grpcPort != null && !grpcPort.isEmpty()) {
            try {
                builder.grpcPort(Integer.parseInt(grpcPort));
            } catch (NumberFormatException ignored) {
                // Use default
            }
        }

        String tlsEnabled = System.getenv("SERVER_TLS_ENABLED");
        if ("true".equalsIgnoreCase(tlsEnabled)) {
            String certPath = System.getenv("SERVER_TLS_CERT_PATH");
            String keyPath = System.getenv("SERVER_TLS_KEY_PATH");
            if (certPath != null && keyPath != null) {
                builder.enableTls(certPath, keyPath);
            }
        }

        String maxRequestSize = System.getenv("SERVER_MAX_REQUEST_SIZE");
        if (maxRequestSize != null && !maxRequestSize.isEmpty()) {
            try {
                builder.maxRequestSize(Integer.parseInt(maxRequestSize));
            } catch (NumberFormatException ignored) {
                // Use default
            }
        }

        String idleTimeout = System.getenv("SERVER_IDLE_TIMEOUT_MS");
        if (idleTimeout != null && !idleTimeout.isEmpty()) {
            try {
                builder.idleTimeoutMillis(Long.parseLong(idleTimeout));
            } catch (NumberFormatException ignored) {
                // Use default
            }
        }

        return builder.build();
    }

    /**
     * Builder for custom ServerConfig.
     */
    public static final class Builder {
        private String host = "0.0.0.0";
        private int port = DEFAULT_PORT;
        private int workerThreads = DEFAULT_WORKER_THREADS;
        private boolean enableGrpc = false;
        private int grpcPort = DEFAULT_GRPC_PORT;
        private boolean enableTls = false;
        private String tlsCertPath = null;
        private String tlsKeyPath = null;
        private int maxRequestSize = DEFAULT_MAX_REQUEST_SIZE;
        private long idleTimeoutMillis = DEFAULT_IDLE_TIMEOUT_MILLIS;

        private Builder() {
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder workerThreads(int threads) {
            this.workerThreads = threads;
            return this;
        }

        public Builder enableGrpc(boolean enable) {
            this.enableGrpc = enable;
            return this;
        }

        public Builder grpcPort(int port) {
            this.grpcPort = port;
            return this;
        }

        public Builder enableTls(String certPath, String keyPath) {
            this.enableTls = true;
            this.tlsCertPath = certPath;
            this.tlsKeyPath = keyPath;
            return this;
        }

        public Builder maxRequestSize(int bytes) {
            this.maxRequestSize = bytes;
            return this;
        }

        public Builder idleTimeoutMillis(long millis) {
            this.idleTimeoutMillis = millis;
            return this;
        }

        public ServerConfig build() {
            return new ServerConfig(
                    host, port, workerThreads, enableGrpc, grpcPort,
                    enableTls, tlsCertPath, tlsKeyPath,
                    maxRequestSize, idleTimeoutMillis);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Utility Methods
    // ═══════════════════════════════════════════════════════════════

    /**
     * Returns a copy with gRPC enabled.
     *
     * @param grpcPort the gRPC port
     * @return new configuration with gRPC enabled
     */
    public ServerConfig withGrpc(int grpcPort) {
        return new ServerConfig(
                host, port, workerThreads, true, grpcPort,
                enableTls, tlsCertPath, tlsKeyPath,
                maxRequestSize, idleTimeoutMillis);
    }

    /**
     * Returns the HTTP base URL for this server.
     *
     * @return base URL (e.g., "http://0.0.0.0:8080")
     */
    public String baseUrl() {
        String scheme = enableTls ? "https" : "http";
        return scheme + "://" + host + ":" + port;
    }

    /**
     * Returns the gRPC address for this server.
     *
     * @return gRPC address or null if gRPC disabled
     */
    public String grpcAddress() {
        return enableGrpc ? host + ":" + grpcPort : null;
    }
}
