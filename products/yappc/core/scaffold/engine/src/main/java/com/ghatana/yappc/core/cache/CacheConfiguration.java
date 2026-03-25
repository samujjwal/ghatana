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

package com.ghatana.yappc.core.cache;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Configuration for cache managers to support both simple and advanced use cases.
 * @doc.type class
 * @doc.purpose Configuration for cache managers to support both simple and advanced use cases.
 * @doc.layer platform
 * @doc.pattern Configuration
 */
public class CacheConfiguration {

    private final boolean persistToDisk;
    private final Path cacheDirectory;
    private final int maxMemoryEntries;
    private final Duration defaultTtl;
    private final boolean enableStatistics;
    private final boolean asyncMode;

    private CacheConfiguration(Builder builder) {
        this.persistToDisk = builder.persistToDisk;
        this.cacheDirectory = builder.cacheDirectory;
        this.maxMemoryEntries = builder.maxMemoryEntries;
        this.defaultTtl = builder.defaultTtl;
        this.enableStatistics = builder.enableStatistics;
        this.asyncMode = builder.asyncMode;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static CacheConfiguration defaultConfig() {
        return builder().build();
    }

    public static CacheConfiguration simplifiedConfig() {
        return builder().persistToDisk(false).enableStatistics(false).asyncMode(false).build();
    }

    // Getters
    public boolean isPersistToDisk() {
        return persistToDisk;
    }

    public Path getCacheDirectory() {
        return cacheDirectory;
    }

    public int getMaxMemoryEntries() {
        return maxMemoryEntries;
    }

    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    public boolean isEnableStatistics() {
        return enableStatistics;
    }

    public boolean isAsyncMode() {
        return asyncMode;
    }

    public static class Builder {
        private boolean persistToDisk = true;
        private Path cacheDirectory = Path.of(System.getProperty("user.home"), ".yappc", "cache");
        private int maxMemoryEntries = 1000;
        private Duration defaultTtl = Duration.ofHours(24);
        private boolean enableStatistics = true;
        private boolean asyncMode = true;

        public Builder persistToDisk(boolean persistToDisk) {
            this.persistToDisk = persistToDisk;
            return this;
        }

        public Builder cacheDirectory(Path cacheDirectory) {
            this.cacheDirectory = cacheDirectory;
            return this;
        }

        public Builder maxMemoryEntries(int maxMemoryEntries) {
            this.maxMemoryEntries = maxMemoryEntries;
            return this;
        }

        public Builder defaultTtl(Duration defaultTtl) {
            this.defaultTtl = defaultTtl;
            return this;
        }

        public Builder enableStatistics(boolean enableStatistics) {
            this.enableStatistics = enableStatistics;
            return this;
        }

        public Builder asyncMode(boolean asyncMode) {
            this.asyncMode = asyncMode;
            return this;
        }

        public CacheConfiguration build() {
            return new CacheConfiguration(this);
        }
    }
}
