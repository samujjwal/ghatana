package com.ghatana.statestore.hybrid;

import com.ghatana.statestore.core.StateStore;
import com.ghatana.statestore.core.StateStoreStats;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hybrid state store combining local cache with centralized backing store.
 * 
 * Architecture:
 * - Local store: Fast access (1-5ms), instance-local, volatile on restart
 * - Central store: Distributed (50-100ms), cross-instance, durable
 * 
 * Sync strategies control when local state propagates to central:
 * - IMMEDIATE: Every write (safest, slowest)
 * - BATCHED: Batch N operations (balanced, recommended)
 * - PERIODIC: Every T milliseconds (fastest, eventual)
 * - ON_CHECKPOINT: Manual checkpoints only (max performance)
 * 
 * Read pattern: Local-first with automatic population from central on miss.
 * Write pattern: Configurable via sync strategy.
 * 
 * Per WORLD_CLASS_DESIGN_MASTER.md Section 5.5.
 *
 * @doc.type class
 * @doc.purpose Hybrid local+distributed state store with configurable sync strategies
 * @doc.layer core
 * @doc.pattern Repository
 */
public class HybridStateStore implements StateStore {
    
    private static final Logger logger = LoggerFactory.getLogger(HybridStateStore.class);
    
    private final Eventloop eventloop;
    private final StateStore localStore;
    private final StateStore centralStore;
    private final SyncStrategy syncStrategy;
    
    // Batching configuration
    private final int batchSize;
    private final long syncIntervalMs;
    
    // Pending writes for batched/periodic sync
    private final ConcurrentLinkedQueue<PendingWrite> pendingWrites = new ConcurrentLinkedQueue<>();
    private final AtomicLong pendingWriteCount = new AtomicLong(0);
    
    // Periodic sync scheduler
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> periodicSyncTask;
    
    // Statistics
    private final AtomicLong localHits = new AtomicLong(0);
    private final AtomicLong localMisses = new AtomicLong(0);
    private final AtomicLong syncOperations = new AtomicLong(0);
    private final AtomicLong syncFailures = new AtomicLong(0);
    private final long startTime = System.currentTimeMillis();
    
    private volatile boolean closed = false;
    
    /**
     * Create hybrid store with default configuration (BATCHED, batch=100, interval=1000ms).
     */
    public HybridStateStore(Eventloop eventloop, StateStore localStore, StateStore centralStore) {
        this(eventloop, localStore, centralStore, SyncStrategy.BATCHED, 100, 1000);
    }
    
    /**
     * Create hybrid store with specific sync strategy.
     */
    public HybridStateStore(Eventloop eventloop, StateStore localStore, StateStore centralStore, SyncStrategy syncStrategy) {
        this(eventloop, localStore, centralStore, syncStrategy, 100, 1000);
    }
    
    /**
     * Create hybrid store with full configuration.
     */
    public HybridStateStore(Eventloop eventloop, StateStore localStore, StateStore centralStore,
                           SyncStrategy syncStrategy, int batchSize, long syncIntervalMs) {
        this.eventloop = eventloop;
        this.localStore = localStore;
        this.centralStore = centralStore;
        this.syncStrategy = syncStrategy;
        this.batchSize = batchSize;
        this.syncIntervalMs = syncIntervalMs;
        
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "HybridStateStore-Sync");
            t.setDaemon(true);
            return t;
        });
        
        // Start periodic sync if configured
        if (syncStrategy == SyncStrategy.PERIODIC) {
            startPeriodicSync();
        }
        
        logger.info("HybridStateStore initialized: strategy={}, batchSize={}, syncIntervalMs={}", 
                   syncStrategy, batchSize, syncIntervalMs);
    }
    
    @Override
    public Promise<Void> put(String key, Object value, Optional<Duration> ttl) {
        if (closed) {
            return Promise.ofException(new IllegalStateException("HybridStateStore is closed"));
        }
        
        // Always write to local store first (fast path)
        Promise<Void> localWrite = localStore.put(key, value, ttl);
        
        // Sync to central based on strategy
        switch (syncStrategy) {
            case IMMEDIATE:
                // Sync immediately (slowest but safest)
                // Local write succeeds even if central sync fails (logged error only)
                return localWrite.then(() -> {
                    // Attempt sync but don't fail if central fails
                    centralStore.put(key, value, ttl)
                        .whenComplete((r, error) -> {
                            if (error != null) {
                                logger.error("Failed to sync key to central store: {}", key, error);
                                syncFailures.incrementAndGet();
                            } else {
                                syncOperations.incrementAndGet();
                            }
                        });
                    // Return success regardless of central sync result
                    return Promise.complete();
                });
                
            case BATCHED:
                // Queue for batch sync
                pendingWrites.add(new PendingWrite(key, value, ttl));
                long count = pendingWriteCount.incrementAndGet();
                
                if (count >= batchSize) {
                    flushPendingWrites();
                }
                return localWrite;
                
            case PERIODIC:
                // Queue for periodic sync
                pendingWrites.add(new PendingWrite(key, value, ttl));
                pendingWriteCount.incrementAndGet();
                return localWrite;
                
            case ON_CHECKPOINT:
                // Queue writes, sync only on explicit checkpoint
                pendingWrites.add(new PendingWrite(key, value, ttl));
                pendingWriteCount.incrementAndGet();
                return localWrite;
                
            default:
                return localWrite;
        }
    }
    
    @Override
    public <T> Promise<Optional<T>> get(String key, Class<T> valueType) {
        if (closed) {
            return Promise.ofException(new IllegalStateException("HybridStateStore is closed"));
        }
        
        // Try local first (fast path)
        return localStore.get(key, valueType)
            .then(localValue -> {
                if (localValue.isPresent()) {
                    localHits.incrementAndGet();
                    logger.debug("Local hit for key: {}", key);
                    return Promise.of(localValue);
                }
                
                // Local miss - try central (slower but authoritative)
                localMisses.incrementAndGet();
                logger.debug("Local miss for key: {}, checking central store", key);
                
                return centralStore.get(key, valueType)
                    .then(centralValue -> {
                        if (centralValue.isPresent()) {
                            // Populate local cache for future reads
                            localStore.put(key, centralValue.get(), Optional.empty())
                                .whenComplete((r, e) -> {
                                    if (e != null) {
                                        logger.warn("Failed to populate local cache for key: {}", key, e);
                                    }
                                });
                        }
                        return Promise.of(centralValue);
                    });
            });
    }
    
    @Override
    public Promise<Boolean> exists(String key) {
        if (closed) {
            return Promise.ofException(new IllegalStateException("HybridStateStore is closed"));
        }
        
        // Check local first
        return localStore.exists(key)
            .then(localExists -> {
                if (localExists) {
                    return Promise.of(true);
                }
                // Check central
                return centralStore.exists(key);
            });
    }
    
    @Override
    public Promise<Boolean> delete(String key) {
        if (closed) {
            return Promise.ofException(new IllegalStateException("HybridStateStore is closed"));
        }
        
        // Delete from both stores
        Promise<Boolean> localDelete = localStore.delete(key);
        Promise<Boolean> centralDelete = centralStore.delete(key);
        
        return Promises.toList(localDelete, centralDelete)
            .map(results -> results.stream().anyMatch(deleted -> deleted));
    }
    
    @Override
    public <T> Promise<Map<String, T>> getAll(Set<String> keys, Class<T> valueType) {
        if (keys == null || keys.isEmpty()) {
            return Promise.of(Collections.emptyMap());
        }
        
        if (closed) {
            return Promise.ofException(new IllegalStateException("HybridStateStore is closed"));
        }
        
        // Try local first
        return localStore.getAll(keys, valueType)
            .then(localResults -> {
                Set<String> missingKeys = new HashSet<>(keys);
                missingKeys.removeAll(localResults.keySet());
                
                if (missingKeys.isEmpty()) {
                    // All found locally
                    localHits.addAndGet(keys.size());
                    return Promise.of(localResults);
                }
                
                // Fetch missing from central
                localHits.addAndGet(localResults.size());
                localMisses.addAndGet(missingKeys.size());
                
                return centralStore.getAll(missingKeys, valueType)
                    .map(centralResults -> {
                        // Populate local cache
                        for (Map.Entry<String, T> entry : centralResults.entrySet()) {
                            localStore.put(entry.getKey(), entry.getValue(), Optional.empty())
                                .whenComplete((r, e) -> {
                                    if (e != null) {
                                        logger.warn("Failed to populate cache for key: {}", entry.getKey(), e);
                                    }
                                });
                        }
                        
                        // Merge results
                        Map<String, T> merged = new HashMap<>(localResults);
                        merged.putAll(centralResults);
                        return merged;
                    });
            });
    }
    
    @Override
    public Promise<Long> deleteAll(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return Promise.of(0L);
        }
        
        if (closed) {
            return Promise.ofException(new IllegalStateException("HybridStateStore is closed"));
        }
        
        // Delete from both stores
        return Promises.toList(
            localStore.deleteAll(keys),
            centralStore.deleteAll(keys)
        ).map(results -> results.stream().mapToLong(Long::longValue).max().orElse(0L));
    }
    
    @Override
    public Promise<Set<String>> getKeysByPrefix(String prefix, int limit) {
        if (closed) {
            return Promise.ofException(new IllegalStateException("HybridStateStore is closed"));
        }
        
        // Query both stores and merge (central is authoritative)
        return Promises.toList(
            localStore.getKeysByPrefix(prefix, limit),
            centralStore.getKeysByPrefix(prefix, limit)
        ).map(results -> {
            Set<String> merged = new LinkedHashSet<>(results.get(1)); // Central first
            merged.addAll(results.get(0)); // Add local
            
            // Respect limit
            if (merged.size() > limit) {
                Set<String> limited = new LinkedHashSet<>();
                Iterator<String> iter = merged.iterator();
                for (int i = 0; i < limit && iter.hasNext(); i++) {
                    limited.add(iter.next());
                }
                return limited;
            }
            return merged;
        });
    }
    
    @Override
    public Promise<Boolean> updateTTL(String key, Duration ttl) {
        if (closed) {
            return Promise.ofException(new IllegalStateException("HybridStateStore is closed"));
        }
        
        // Update in both stores
        return Promises.toList(
            localStore.updateTTL(key, ttl),
            centralStore.updateTTL(key, ttl)
        ).map(results -> results.stream().anyMatch(success -> success));
    }
    
    @Override
    public Promise<Optional<Duration>> getRemainingTTL(String key) {
        if (closed) {
            return Promise.ofException(new IllegalStateException("HybridStateStore is closed"));
        }
        
        // Check central (authoritative for TTL)
        return centralStore.getRemainingTTL(key);
    }
    
    @Override
    public Promise<Void> createCheckpoint(String checkpointId) {
        if (closed) {
            return Promise.ofException(new IllegalStateException("HybridStateStore is closed"));
        }
        
        // Flush pending writes before checkpoint (critical for ON_CHECKPOINT strategy)
        Promise<Void> flushPromise = Promise.complete();
        if (syncStrategy == SyncStrategy.BATCHED || 
            syncStrategy == SyncStrategy.PERIODIC ||
            syncStrategy == SyncStrategy.ON_CHECKPOINT) {
            flushPromise = flushPendingWrites();
        }
        
        // Wait for flush, then create checkpoint in both stores
        return flushPromise
            .then(() -> localStore.createCheckpoint(checkpointId))
            .then(() -> centralStore.createCheckpoint(checkpointId));
    }
    
    /**
     * Trigger checkpoint flush for ON_CHECKPOINT sync strategy.
     * This is the integration point with CheckpointCoordinator.
     * 
     * When using ON_CHECKPOINT strategy, state is only synced to central store
     * during explicit checkpoints. This method:
     * 1. Flushes all pending writes to central store
     * 2. Waits for sync completion
     * 3. Returns state size for checkpoint metadata
     * 
     * @param checkpointId Checkpoint identifier for correlation
     * @return State size in bytes for checkpoint tracking
     */
    public Promise<Long> snapshotForCheckpoint(String checkpointId) {
        if (closed) {
            return Promise.ofException(new IllegalStateException("HybridStateStore is closed"));
        }
        
        logger.info("Snapshotting state for checkpoint: {}", checkpointId);
        
        // Flush pending writes
        long pendingCount = pendingWriteCount.get();
        Promise<Void> flushPromise = Promise.complete();
        if (pendingCount > 0) {
            flushPromise = flushPendingWrites();
            logger.debug("Flushed {} pending writes for checkpoint {}", pendingCount, checkpointId);
        }
        
        // Wait for flush to complete, then get storage size
        return flushPromise.then(() ->
            Promises.toList(
                localStore.getStorageSize(),
                centralStore.getStorageSize()
            ).map(sizes -> {
                long total = 0;
                for (Long size : sizes) {
                    total += size;
                }
                return total;
            })
        );
    }
    
    @Override
    public Promise<Void> restoreFromCheckpoint(String checkpointId) {
        if (closed) {
            return Promise.ofException(new IllegalStateException("HybridStateStore is closed"));
        }
        
        // Restore from central (authoritative)
        return centralStore.restoreFromCheckpoint(checkpointId)
            .then(() -> {
                // Clear local cache to avoid stale data
                return localStore.compact();
            });
    }
    
    @Override
    public Promise<Map<String, Instant>> listCheckpoints() {
        if (closed) {
            return Promise.ofException(new IllegalStateException("HybridStateStore is closed"));
        }
        
        // Use central's checkpoint list (authoritative)
        return centralStore.listCheckpoints();
    }
    
    @Override
    public Promise<Boolean> deleteCheckpoint(String checkpointId) {
        if (closed) {
            return Promise.ofException(new IllegalStateException("HybridStateStore is closed"));
        }
        
        // Delete from both stores
        return Promises.toList(
            localStore.deleteCheckpoint(checkpointId),
            centralStore.deleteCheckpoint(checkpointId)
        ).map(results -> results.stream().anyMatch(deleted -> deleted));
    }
    
    @Override
    public Promise<StateStoreStats> getStats() {
        if (closed) {
            return Promise.ofException(new IllegalStateException("HybridStateStore is closed"));
        }
        
        return Promises.toList(
            localStore.getStats(),
            centralStore.getStats()
        ).map(results -> {
            StateStoreStats localStats = results.get(0);
            StateStoreStats centralStats = results.get(1);
            
            long uptime = System.currentTimeMillis() - startTime;
            double hitRate = calculateHitRate();
            
            Map<String, Object> additionalMetrics = new HashMap<>();
            additionalMetrics.put("local_hits", localHits.get());
            additionalMetrics.put("local_misses", localMisses.get());
            additionalMetrics.put("hit_rate", hitRate);
            additionalMetrics.put("sync_operations", syncOperations.get());
            additionalMetrics.put("sync_failures", syncFailures.get());
            additionalMetrics.put("pending_writes", pendingWriteCount.get());
            additionalMetrics.put("sync_strategy", syncStrategy.name());
            
            return new StateStoreStats(
                centralStats.getTotalKeys(),           // Use central as authoritative
                localStats.getTotalSizeBytes() + centralStats.getTotalSizeBytes(),
                centralStats.getKeysWithTtl(),
                centralStats.getExpiredKeys(),
                localStats.getTotalReads() + centralStats.getTotalReads(),
                localStats.getTotalWrites() + centralStats.getTotalWrites(),
                localStats.getTotalDeletes() + centralStats.getTotalDeletes(),
                localStats.getAvgReadLatencyMs(),
                centralStats.getAvgWriteLatencyMs(),
                centralStats.getCheckpointCount(),
                centralStats.getLastCheckpointTime(),
                "Hybrid(" + localStats.getBackendType() + "+" + centralStats.getBackendType() + ")",
                localStats.getMemoryUsageBytes(),
                centralStats.getDiskUsageBytes(),
                !closed && localStats.isHealthy() && centralStats.isHealthy(),
                uptime,
                additionalMetrics
            );
        });
    }
    
    @Override
    public Promise<Long> getStorageSize() {
        if (closed) {
            return Promise.ofException(new IllegalStateException("HybridStateStore is closed"));
        }
        
        return Promises.toList(
            localStore.getStorageSize(),
            centralStore.getStorageSize()
        ).map(results -> results.get(0) + results.get(1));
    }
    
    @Override
    public Promise<Void> compact() {
        if (closed) {
            return Promise.ofException(new IllegalStateException("HybridStateStore is closed"));
        }
        
        // Flush pending writes before compacting
        return flushPendingWrites()
            .then(() -> localStore.compact())
            .then(() -> centralStore.compact());
    }
    
    @Override
    public Promise<Boolean> isHealthy() {
        if (closed) {
            return Promise.of(false);
        }
        
        return Promises.toList(
            localStore.isHealthy(),
            centralStore.isHealthy()
        ).map(results -> results.stream().allMatch(healthy -> healthy));
    }
    
    @Override
    public Promise<Void> close() {
        if (closed) {
            return Promise.of(null);
        }
        
        closed = true;
        
        // Stop periodic sync
        if (periodicSyncTask != null) {
            periodicSyncTask.cancel(false);
        }
        
        // Flush pending writes and wait for completion
        Promise<Void> flushPromise = flushPendingWrites();
        
        // Sequential: flush → close stores → shutdown scheduler
        return flushPromise
            .then(() -> localStore != null ? localStore.close() : Promise.complete())
            .then(() -> centralStore != null ? centralStore.close() : Promise.complete())
            .whenComplete((r, e) -> {
                // Shutdown scheduler AFTER stores are closed
                scheduler.shutdown();
                try {
                    if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                        scheduler.shutdownNow();
                    }
                } catch (InterruptedException ex) {
                    scheduler.shutdownNow();
                    Thread.currentThread().interrupt();
                }
                
                if (e != null) {
                    logger.error("Error closing HybridStateStore", e);
                } else {
                    logger.info("HybridStateStore closed");
                }
            });
    }
    
    // Helper methods
    
    /**
     * Start the periodic sync task.
     * FIXED: Properly observes the Promise result per ADR-0002 guardrails.
     */
    private void startPeriodicSync() {
        periodicSyncTask = scheduler.scheduleAtFixedRate(
            () -> {
                // FIXED: Observe the Promise to ensure errors are logged
                // The Promise runs async work; we subscribe to completion
                // Ensure we run on the eventloop
                eventloop.execute(() -> {
                    flushPendingWrites()
                        .whenComplete((result, error) -> {
                            if (error != null) {
                                logger.error("Periodic flush failed", error);
                                syncFailures.incrementAndGet();
                            }
                        });
                });
            },
            syncIntervalMs,
            syncIntervalMs,
            TimeUnit.MILLISECONDS
        );
        logger.debug("Periodic sync started: interval={}ms", syncIntervalMs);
    }
    
    private Promise<Void> flushPendingWrites() {
        long count = pendingWriteCount.getAndSet(0);
        if (count == 0) {
            return Promise.complete();
        }
        
        List<PendingWrite> batch = new ArrayList<>();
        PendingWrite write;
        while ((write = pendingWrites.poll()) != null) {
            batch.add(write);
        }
        
        if (batch.isEmpty()) {
            return Promise.complete();
        }
        
        logger.debug("Flushing {} pending writes to central store", batch.size());
        
        // Sync all pending writes to central (collect all promises)
        List<Promise<Void>> syncPromises = new ArrayList<>();
        for (PendingWrite pw : batch) {
            Promise<Void> syncPromise = centralStore.put(pw.key, pw.value, pw.ttl)
                .whenComplete((r, e) -> {
                    if (e != null) {
                        logger.error("Failed to sync key to central: {}", pw.key, e);
                        syncFailures.incrementAndGet();
                    } else {
                        syncOperations.incrementAndGet();
                    }
                });
            syncPromises.add(syncPromise);
        }
        
        // Wait for all syncs to complete
        return Promises.all(syncPromises);
    }
    
    private double calculateHitRate() {
        long hits = localHits.get();
        long misses = localMisses.get();
        long total = hits + misses;
        
        if (total == 0) {
            return 0.0;
        }
        
        return (double) hits / total;
    }
    
    /**
     * Represents a pending write operation.
     */
    private static class PendingWrite {
        final String key;
        final Object value;
        final Optional<Duration> ttl;
        
        PendingWrite(String key, Object value, Optional<Duration> ttl) {
            this.key = key;
            this.value = value;
            this.ttl = ttl;
        }
    }
}
