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

package com.ghatana.yappc.api;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Configuration for the YAPPC API.
 * Immutable configuration object with sensible defaults.
 *
 * @doc.type record
 * @doc.purpose Configuration container for YAPPC API
 * @doc.layer platform
 * @doc.pattern ValueObject
 */
public final class YappcConfig {

    private final Path packsPath;
    private final Path workspacePath;
    private final boolean enableCache;
    private final boolean enableTelemetry;
    private final int cacheMaxSize;
    private final long cacheTtlSeconds;

    private YappcConfig(Builder builder) {
        this.packsPath = builder.packsPath != null ? builder.packsPath : resolveDefaultPacksPath();
        this.workspacePath = builder.workspacePath != null ? builder.workspacePath : Paths.get(System.getProperty("user.dir"));
        this.enableCache = builder.enableCache;
        this.enableTelemetry = builder.enableTelemetry;
        this.cacheMaxSize = builder.cacheMaxSize;
        this.cacheTtlSeconds = builder.cacheTtlSeconds;
    }

    /**
     * Creates a default configuration.
     *
     * @return Default configuration instance
     */
    public static YappcConfig defaults() {
        return builder().build();
    }

    /**
     * Creates a new configuration builder.
     *
     * @return A new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Resolves the default packs path.
     * Looks in: YAPPC_PACKS_PATH env, ~/.yappc/packs, or classpath.
     */
    private static Path resolveDefaultPacksPath() {
        // Check environment variable
        String envPath = System.getenv("YAPPC_PACKS_PATH");
        if (envPath != null && !envPath.isBlank()) {
            return Paths.get(envPath);
        }

        // Check user home directory
        Path homePacks = Paths.get(System.getProperty("user.home"), ".yappc", "packs");
        if (homePacks.toFile().exists()) {
            return homePacks;
        }

        // Default to current directory packs
        return Paths.get(System.getProperty("user.dir"), "packs");
    }

    // === Getters ===

    public Path getPacksPath() {
        return packsPath;
    }

    public Path getWorkspacePath() {
        return workspacePath;
    }

    public boolean isCacheEnabled() {
        return enableCache;
    }

    public boolean isTelemetryEnabled() {
        return enableTelemetry;
    }

    public int getCacheMaxSize() {
        return cacheMaxSize;
    }

    public long getCacheTtlSeconds() {
        return cacheTtlSeconds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        YappcConfig that = (YappcConfig) o;
        return enableCache == that.enableCache &&
                enableTelemetry == that.enableTelemetry &&
                cacheMaxSize == that.cacheMaxSize &&
                cacheTtlSeconds == that.cacheTtlSeconds &&
                Objects.equals(packsPath, that.packsPath) &&
                Objects.equals(workspacePath, that.workspacePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(packsPath, workspacePath, enableCache, enableTelemetry, cacheMaxSize, cacheTtlSeconds);
    }

    @Override
    public String toString() {
        return "YappcConfig{" +
                "packsPath=" + packsPath +
                ", workspacePath=" + workspacePath +
                ", enableCache=" + enableCache +
                ", enableTelemetry=" + enableTelemetry +
                ", cacheMaxSize=" + cacheMaxSize +
                ", cacheTtlSeconds=" + cacheTtlSeconds +
                '}';
    }

    /**
     * Builder for YappcConfig.
     */
    public static final class Builder {
        private Path packsPath;
        private Path workspacePath;
        private boolean enableCache = true;
        private boolean enableTelemetry = false;
        private int cacheMaxSize = 100;
        private long cacheTtlSeconds = 3600;

        private Builder() {}

        public Builder packsPath(Path packsPath) {
            this.packsPath = packsPath;
            return this;
        }

        public Builder workspacePath(Path workspacePath) {
            this.workspacePath = workspacePath;
            return this;
        }

        public Builder enableCache(boolean enableCache) {
            this.enableCache = enableCache;
            return this;
        }

        public Builder enableTelemetry(boolean enableTelemetry) {
            this.enableTelemetry = enableTelemetry;
            return this;
        }

        public Builder cacheMaxSize(int cacheMaxSize) {
            this.cacheMaxSize = cacheMaxSize;
            return this;
        }

        public Builder cacheTtlSeconds(long cacheTtlSeconds) {
            this.cacheTtlSeconds = cacheTtlSeconds;
            return this;
        }

        public YappcConfig build() {
            return new YappcConfig(this);
        }
    }
}
