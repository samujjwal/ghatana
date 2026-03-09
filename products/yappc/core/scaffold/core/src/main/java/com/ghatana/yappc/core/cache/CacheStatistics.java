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

import java.time.Duration;

/**
 * Day 22: Cache statistics for monitoring and performance analysis. Provides comprehensive metrics
 * about cache usage and performance.
 *
 * @doc.type class
 * @doc.purpose Day 22: Cache statistics for monitoring and performance analysis. Provides comprehensive metrics
 * @doc.layer platform
 * @doc.pattern Component
 */
public class CacheStatistics {

    private final long hitCount;
    private final long missCount;
    private final double hitRate;
    private final long evictionCount;
    private final long estimatedSize;
    private final Duration totalLoadTime;
    private final Duration averageLoadPenalty;
    private final long diskEntries;
    private final long diskSizeBytes;

    private CacheStatistics(Builder builder) {
        this.hitCount = builder.hitCount;
        this.missCount = builder.missCount;
        this.hitRate = builder.hitRate;
        this.evictionCount = builder.evictionCount;
        this.estimatedSize = builder.estimatedSize;
        this.totalLoadTime = builder.totalLoadTime;
        this.averageLoadPenalty = builder.averageLoadPenalty;
        this.diskEntries = builder.diskEntries;
        this.diskSizeBytes = builder.diskSizeBytes;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public long getHitCount() {
        return hitCount;
    }

    public long getMissCount() {
        return missCount;
    }

    public double getHitRate() {
        return hitRate;
    }

    public long getEvictionCount() {
        return evictionCount;
    }

    public long getEstimatedSize() {
        return estimatedSize;
    }

    public Duration getTotalLoadTime() {
        return totalLoadTime;
    }

    public Duration getAverageLoadPenalty() {
        return averageLoadPenalty;
    }

    public long getDiskEntries() {
        return diskEntries;
    }

    public long getDiskSizeBytes() {
        return diskSizeBytes;
    }

    public long getRequestCount() {
        return hitCount + missCount;
    }

    public double getMissRate() {
        return 1.0 - hitRate;
    }

    @Override
    public String toString() {
        return String.format(
                "CacheStatistics{hits=%d, misses=%d, hitRate=%.2f%%, size=%d, diskEntries=%d,"
                        + " diskSize=%d bytes}",
                hitCount, missCount, hitRate * 100, estimatedSize, diskEntries, diskSizeBytes);
    }

    public static class Builder {
        private long hitCount;
        private long missCount;
        private double hitRate;
        private long evictionCount;
        private long estimatedSize;
        private Duration totalLoadTime = Duration.ZERO;
        private Duration averageLoadPenalty = Duration.ZERO;
        private long diskEntries;
        private long diskSizeBytes;

        public Builder hitCount(long hitCount) {
            this.hitCount = hitCount;
            return this;
        }

        public Builder missCount(long missCount) {
            this.missCount = missCount;
            return this;
        }

        public Builder hitRate(double hitRate) {
            this.hitRate = hitRate;
            return this;
        }

        public Builder evictionCount(long evictionCount) {
            this.evictionCount = evictionCount;
            return this;
        }

        public Builder estimatedSize(long estimatedSize) {
            this.estimatedSize = estimatedSize;
            return this;
        }

        public Builder totalLoadTime(Duration totalLoadTime) {
            this.totalLoadTime = totalLoadTime;
            return this;
        }

        public Builder averageLoadPenalty(Duration averageLoadPenalty) {
            this.averageLoadPenalty = averageLoadPenalty;
            return this;
        }

        public Builder diskEntries(long diskEntries) {
            this.diskEntries = diskEntries;
            return this;
        }

        public Builder diskSizeBytes(long diskSizeBytes) {
            this.diskSizeBytes = diskSizeBytes;
            return this;
        }

        public CacheStatistics build() {
            return new CacheStatistics(this);
        }
    }
}
