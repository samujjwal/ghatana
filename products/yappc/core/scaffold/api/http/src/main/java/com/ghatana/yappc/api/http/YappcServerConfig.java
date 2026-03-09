/*
 * Copyright (c) 2025 Ghatana Platform Contributors
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

package com.ghatana.yappc.api.http;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration for the YAPPC HTTP server.
 *
 * @doc.type class
 * @doc.purpose HTTP server configuration
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class YappcServerConfig {

    private final int port;
    private final String host;
    private final Path packsPath;
    private final Path workspacePath;
    private final boolean enableSwagger;
    private final boolean enableWebSocket;
    private final boolean enableCors;
    private final String corsOrigin;
    private final int maxRequestSize;
    private final int requestTimeoutMs;

    private YappcServerConfig(Builder builder) {
        this.port = builder.port;
        this.host = builder.host;
        this.packsPath = builder.packsPath != null ? builder.packsPath : resolveDefaultPacksPath();
        this.workspacePath = builder.workspacePath != null ? builder.workspacePath : Paths.get(System.getProperty("user.dir"));
        this.enableSwagger = builder.enableSwagger;
        this.enableWebSocket = builder.enableWebSocket;
        this.enableCors = builder.enableCors;
        this.corsOrigin = builder.corsOrigin;
        this.maxRequestSize = builder.maxRequestSize;
        this.requestTimeoutMs = builder.requestTimeoutMs;
    }

    public static YappcServerConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    private static Path resolveDefaultPacksPath() {
        String envPath = System.getenv("YAPPC_PACKS_PATH");
        if (envPath != null && !envPath.isBlank()) {
            return Paths.get(envPath);
        }
        Path homePacks = Paths.get(System.getProperty("user.home"), ".yappc", "packs");
        if (homePacks.toFile().exists()) {
            return homePacks;
        }
        return Paths.get(System.getProperty("user.dir"), "packs");
    }

    public int getPort() { return port; }
    public String getHost() { return host; }
    public Path getPacksPath() { return packsPath; }
    public Path getWorkspacePath() { return workspacePath; }
    public boolean isEnableSwagger() { return enableSwagger; }
    public boolean isEnableWebSocket() { return enableWebSocket; }
    public boolean isEnableCors() { return enableCors; }
    public String getCorsOrigin() { return corsOrigin; }
    public int getMaxRequestSize() { return maxRequestSize; }
    public int getRequestTimeoutMs() { return requestTimeoutMs; }

    public static final class Builder {
        private int port = 8080;
        private String host = "0.0.0.0";
        private Path packsPath;
        private Path workspacePath;
        private boolean enableSwagger = true;
        private boolean enableWebSocket = true;
        private boolean enableCors = true;
        private String corsOrigin = "*";
        private int maxRequestSize = 10 * 1024 * 1024; // 10MB
        private int requestTimeoutMs = 30000;

        private Builder() {}

        public Builder port(int port) { this.port = port; return this; }
        public Builder host(String host) { this.host = host; return this; }
        public Builder packsPath(Path packsPath) { this.packsPath = packsPath; return this; }
        public Builder workspacePath(Path workspacePath) { this.workspacePath = workspacePath; return this; }
        public Builder enableSwagger(boolean enableSwagger) { this.enableSwagger = enableSwagger; return this; }
        public Builder enableWebSocket(boolean enableWebSocket) { this.enableWebSocket = enableWebSocket; return this; }
        public Builder enableCors(boolean enableCors) { this.enableCors = enableCors; return this; }
        public Builder corsOrigin(String corsOrigin) { this.corsOrigin = corsOrigin; return this; }
        public Builder maxRequestSize(int maxRequestSize) { this.maxRequestSize = maxRequestSize; return this; }
        public Builder requestTimeoutMs(int requestTimeoutMs) { this.requestTimeoutMs = requestTimeoutMs; return this; }

        public YappcServerConfig build() {
            return new YappcServerConfig(this);
        }
    }
}
