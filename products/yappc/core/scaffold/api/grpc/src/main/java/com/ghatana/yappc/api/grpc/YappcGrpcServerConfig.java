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

package com.ghatana.yappc.api.grpc;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration for YAPPC gRPC Server.
 *
 * @doc.type class
 * @doc.purpose gRPC server configuration
 * @doc.layer platform
 * @doc.pattern Builder
 */
public final class YappcGrpcServerConfig {

    private static final int DEFAULT_PORT = 50051;
    private static final int DEFAULT_MAX_MESSAGE_SIZE = 16 * 1024 * 1024; // 16MB

    private final int port;
    private final Path packsPath;
    private final Path workspacePath;
    private final boolean enableReflection;
    private final int maxMessageSize;
    private final boolean enableTls;
    private final String certChainPath;
    private final String privateKeyPath;

    private YappcGrpcServerConfig(Builder builder) {
        this.port = builder.port;
        this.packsPath = builder.packsPath;
        this.workspacePath = builder.workspacePath;
        this.enableReflection = builder.enableReflection;
        this.maxMessageSize = builder.maxMessageSize;
        this.enableTls = builder.enableTls;
        this.certChainPath = builder.certChainPath;
        this.privateKeyPath = builder.privateKeyPath;
    }

    public static YappcGrpcServerConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getPort() {
        return port;
    }

    public Path getPacksPath() {
        return packsPath;
    }

    public Path getWorkspacePath() {
        return workspacePath;
    }

    public boolean isEnableReflection() {
        return enableReflection;
    }

    public int getMaxMessageSize() {
        return maxMessageSize;
    }

    public boolean isEnableTls() {
        return enableTls;
    }

    public String getCertChainPath() {
        return certChainPath;
    }

    public String getPrivateKeyPath() {
        return privateKeyPath;
    }

    public static final class Builder {
        private int port = DEFAULT_PORT;
        private Path packsPath = getDefaultPacksPath();
        private Path workspacePath = Paths.get(System.getProperty("user.dir"));
        private boolean enableReflection = true;
        private int maxMessageSize = DEFAULT_MAX_MESSAGE_SIZE;
        private boolean enableTls = false;
        private String certChainPath;
        private String privateKeyPath;

        private Builder() {}

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder packsPath(Path packsPath) {
            this.packsPath = packsPath;
            return this;
        }

        public Builder workspacePath(Path workspacePath) {
            this.workspacePath = workspacePath;
            return this;
        }

        public Builder enableReflection(boolean enableReflection) {
            this.enableReflection = enableReflection;
            return this;
        }

        public Builder maxMessageSize(int maxMessageSize) {
            this.maxMessageSize = maxMessageSize;
            return this;
        }

        public Builder enableTls(boolean enableTls) {
            this.enableTls = enableTls;
            return this;
        }

        public Builder certChainPath(String certChainPath) {
            this.certChainPath = certChainPath;
            return this;
        }

        public Builder privateKeyPath(String privateKeyPath) {
            this.privateKeyPath = privateKeyPath;
            return this;
        }

        public YappcGrpcServerConfig build() {
            return new YappcGrpcServerConfig(this);
        }

        private static Path getDefaultPacksPath() {
            String envPath = System.getenv("YAPPC_PACKS_PATH");
            if (envPath != null && !envPath.isBlank()) {
                return Paths.get(envPath);
            }
            return Paths.get(System.getProperty("user.home"), ".yappc", "packs");
        }
    }
}
