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

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import io.activej.promise.Promise;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Unified configurable cache implementation supporting both simple and advanced use cases
 * @doc.type class
 * @doc.purpose Unified configurable cache implementation supporting both simple and advanced use cases
 * @doc.layer platform
 * @doc.pattern Manager
 */
public class LocalCacheManager implements YappcCacheManager {

    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool();


    private final Map<String, CacheEntry> memoryCache;
    private final CacheConfiguration config;
    private final AtomicLong hitCount;
    private final AtomicLong missCount;
    private final AtomicLong evictionCount;
    private final ReadWriteLock lock;

    // Default constructor for backward compatibility
    public LocalCacheManager() {
        this(CacheConfiguration.defaultConfig());
    }

    // Path-based constructor for backward compatibility
    public LocalCacheManager(Path cacheDirectory) {
        this(CacheConfiguration.builder().cacheDirectory(cacheDirectory).build());
    }

    // Configuration-based constructor (primary)
    public LocalCacheManager(CacheConfiguration config) {
        this.config = config;
        this.memoryCache = new ConcurrentHashMap<>();
        this.hitCount = config.isEnableStatistics() ? new AtomicLong(0) : null;
        this.missCount = config.isEnableStatistics() ? new AtomicLong(0) : null;
        this.evictionCount = config.isEnableStatistics() ? new AtomicLong(0) : null;
        this.lock = config.isPersistToDisk() ? new ReentrantReadWriteLock() : null;

        if (config.isPersistToDisk()) {
            try {
                Files.createDirectories(config.getCacheDirectory());
            } catch (IOException e) {
                throw new RuntimeException(
                        "Failed to initialize cache directory: " + config.getCacheDirectory(), e);
            }
        }
    }

    @Override
    public Optional<CachedArtifact> get(String key) {
        if (lock != null) {
            lock.readLock().lock();
        }
        try {
            // Check memory cache first
            CacheEntry entry = memoryCache.get(key);
            if (entry != null && !entry.isExpired()) {
                incrementHitCount();
                return Optional.of(entry.artifact);
            }

            // Check disk cache if enabled
            if (config.isPersistToDisk()) {
                Optional<CachedArtifact> diskResult = loadFromDisk(key);
                if (diskResult.isPresent()) {
                    // Promote to memory cache
                    CacheEntry newEntry =
                            new CacheEntry(diskResult.get(), Instant.now(), config.getDefaultTtl());
                    memoryCache.put(key, newEntry);
                    incrementHitCount();
                    return diskResult;
                }
            }

            incrementMissCount();
            return Optional.empty();
        } finally {
            if (lock != null) {
                lock.readLock().unlock();
            }
        }
    }

    @Override
    public Promise<Void> put(String key, CachedArtifact artifact) {
        if (config.isAsyncMode()) {
            return Promise.ofBlocking(BLOCKING_EXECUTOR, () -> putSync(key, artifact));
        } else {
            putSync(key, artifact);
            return Promise.of(null);
        }
    }

    private void putSync(String key, CachedArtifact artifact) {
        if (lock != null) {
            lock.writeLock().lock();
        }
        try {
            CacheEntry entry = new CacheEntry(artifact, Instant.now(), config.getDefaultTtl());

            // Evict old entries if memory limit exceeded
            if (memoryCache.size() >= config.getMaxMemoryEntries()) {
                evictLRU();
            }

            memoryCache.put(key, entry);

            // Persist to disk if enabled
            if (config.isPersistToDisk()) {
                saveToDisk(key, artifact);
            }
        } finally {
            if (lock != null) {
                lock.writeLock().unlock();
            }
        }
    }

    @Override
    public boolean invalidate(String key) {
        if (lock != null) {
            lock.writeLock().lock();
        }
        try {
            boolean removed = memoryCache.remove(key) != null;

            if (config.isPersistToDisk()) {
                Path diskPath = getDiskPath(key);
                try {
                    Files.deleteIfExists(diskPath);
                } catch (IOException e) {
                    // Log warning but don't fail
                }
            }

            return removed;
        } finally {
            if (lock != null) {
                lock.writeLock().unlock();
            }
        }
    }

    @Override
    public void invalidateAll() {
        if (lock != null) {
            lock.writeLock().lock();
        }
        try {
            memoryCache.clear();

            if (config.isPersistToDisk() && Files.exists(config.getCacheDirectory())) {
                try (DirectoryStream<Path> stream =
                        Files.newDirectoryStream(config.getCacheDirectory(), "*.cache")) {
                    for (Path file : stream) {
                        Files.deleteIfExists(file);
                    }
                } catch (IOException e) {
                    // Log warning but don't fail
                }
            }
        } finally {
            if (lock != null) {
                lock.writeLock().unlock();
            }
        }
    }

    @Override
    public CacheStatistics getStatistics() {
        if (!config.isEnableStatistics()) {
            return CacheStatistics.builder()
                    .hitCount(0L)
                    .missCount(0L)
                    .hitRate(0.0)
                    .evictionCount(0L)
                    .estimatedSize(memoryCache.size())
                    .totalLoadTime(Duration.ZERO)
                    .averageLoadPenalty(Duration.ZERO)
                    .diskEntries(0L)
                    .diskSizeBytes(0L)
                    .build();
        }

        long hits = hitCount.get();
        long misses = missCount.get();
        double hitRatio = hits + misses > 0 ? (double) hits / (hits + misses) : 0.0;

        long diskEntries = 0L;
        long diskSizeBytes = 0L;

        if (config.isPersistToDisk() && Files.exists(config.getCacheDirectory())) {
            try (DirectoryStream<Path> stream =
                    Files.newDirectoryStream(config.getCacheDirectory(), "*.cache")) {
                for (Path file : stream) {
                    diskEntries++;
                    try {
                        diskSizeBytes += Files.size(file);
                    } catch (IOException e) {
                        // Ignore file size calculation errors
                    }
                }
            } catch (IOException e) {
                // Ignore directory reading errors
            }
        }

        return CacheStatistics.builder()
                .hitCount(hits)
                .missCount(misses)
                .hitRate(hitRatio)
                .evictionCount(evictionCount.get())
                .estimatedSize(memoryCache.size())
                .totalLoadTime(Duration.ZERO)
                .averageLoadPenalty(Duration.ZERO)
                .diskEntries(diskEntries)
                .diskSizeBytes(diskSizeBytes)
                .build();
    }

    // Factory methods for common configurations
    public static LocalCacheManager createSimple() {
        return new LocalCacheManager(CacheConfiguration.simplifiedConfig());
    }

    public static LocalCacheManager createDefault() {
        return new LocalCacheManager(CacheConfiguration.defaultConfig());
    }

    // Private helper methods
    private void incrementHitCount() {
        if (hitCount != null) {
            hitCount.incrementAndGet();
        }
    }

    private void incrementMissCount() {
        if (missCount != null) {
            missCount.incrementAndGet();
        }
    }

    private void incrementEvictionCount() {
        if (evictionCount != null) {
            evictionCount.incrementAndGet();
        }
    }

    private void evictLRU() {
        // Simple LRU eviction - remove oldest entry
        String oldestKey = null;
        Instant oldestTime = Instant.MAX;

        for (Map.Entry<String, CacheEntry> entry : memoryCache.entrySet()) {
            if (entry.getValue().createdAt.isBefore(oldestTime)) {
                oldestTime = entry.getValue().createdAt;
                oldestKey = entry.getKey();
            }
        }

        if (oldestKey != null) {
            memoryCache.remove(oldestKey);
            incrementEvictionCount();
        }
    }

    private Optional<CachedArtifact> loadFromDisk(String key) {
        Path diskPath = getDiskPath(key);
        if (!Files.exists(diskPath)) {
            return Optional.empty();
        }

        try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(diskPath))) {
            CachedArtifact artifact = (CachedArtifact) ois.readObject();
            return Optional.of(artifact);
        } catch (IOException | ClassNotFoundException e) {
            // Remove corrupted file
            try {
                Files.deleteIfExists(diskPath);
            } catch (IOException ignored) {
            }
            return Optional.empty();
        }
    }

    private void saveToDisk(String key, CachedArtifact artifact) {
        Path diskPath = getDiskPath(key);
        try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(diskPath))) {
            oos.writeObject(artifact);
        } catch (IOException e) {
            // Log warning but don't fail the cache operation
        }
    }

    private Path getDiskPath(String key) {
        String safeKey = key.replaceAll("[^a-zA-Z0-9.-]", "_");
        return config.getCacheDirectory().resolve(safeKey + ".cache");
    }

    private static class CacheEntry {
        final CachedArtifact artifact;
        final Instant createdAt;
        final Duration ttl;

        CacheEntry(CachedArtifact artifact, Instant createdAt, Duration ttl) {
            this.artifact = artifact;
            this.createdAt = createdAt;
            this.ttl = ttl;
        }

        boolean isExpired() {
            return Instant.now().isAfter(createdAt.plus(ttl));
        }
    }
}
