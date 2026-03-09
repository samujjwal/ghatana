/*
 * Copyright (c) 2026 Ghatana.
 * All rights reserved.
 */
package com.ghatana.datacloud.edge;

import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lightweight edge Data-Cloud deployment implementation.
 * 
 * <p>Provides a resource-efficient Data-Cloud instance for edge locations
 * with selective synchronization, offline operation, and automatic
 * reconnection to central Data-Cloud.
 *
 * <p>Key features:
 * <ul>
 *   <li>Lightweight footprint for edge devices</li>
 *   <li>Offline-first operation with local storage</li>
 *   <li>Selective sync with configurable filters</li>
 *   <li>Automatic conflict resolution</li>
 *   <li>Background sync with backoff</li>
 *   <li>Resource-constrained operation</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Lightweight edge deployment for Data-Cloud
 * @doc.layer core
 * @doc.pattern Gateway, Observer
 */
public class LightweightEdgeDeployment implements EdgeDeployment {

    private static final Logger logger = LoggerFactory.getLogger(LightweightEdgeDeployment.class);

    // State
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicReference<EdgeMode> mode = new AtomicReference<>(EdgeMode.DISCONNECTED);
    private EdgeConfig config;

    // Executors
    private ScheduledExecutorService scheduler;
    private ExecutorService syncExecutor;

    // Local storage (in-memory for demo - would use RocksDB in production)
    private final ConcurrentMap<String, LocalEntry> localStorage = new ConcurrentHashMap<>();
    
    // Pending sync queues
    private final BlockingQueue<String> pendingUpload = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> pendingDownload = new LinkedBlockingQueue<>();

    // Sync state
    private Instant lastSyncTime;
    private Instant lastFullSyncTime;
    private Instant lastHeartbeat;
    private long totalSynced = 0;
    private long conflictsResolved = 0;

    // Filters and hooks
    private final ConcurrentMap<String, SyncFilter> syncFilters = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, EdgeHook> hooks = new ConcurrentHashMap<>();

    // Sync history
    private final ConcurrentLinkedQueue<EdgeSyncEvent> syncHistory = new ConcurrentLinkedQueue<>();
    private static final int MAX_SYNC_HISTORY = 100;

    // Metrics
    private MeterRegistry meterRegistry;
    private Counter syncSuccessCounter;
    private Counter syncFailureCounter;
    private Counter conflictCounter;

    // Central connection (simulated - would use HTTP/gRPC in production)
    private CentralConnection centralConnection;

    /**
     * Local storage entry with sync metadata.
     */
    private record LocalEntry(
        String key,
        byte[] data,
        long version,
        Instant modifiedAt,
        Instant syncedAt,
        boolean dirty,
        Map<String, String> metadata
    ) {}

    /**
     * Interface for central Data-Cloud connection.
     */
    public interface CentralConnection {
        Promise<Boolean> connect(String centralUrl, String edgeId);
        Promise<Void> disconnect();
        Promise<Boolean> isConnected();
        Promise<List<SyncEntry>> fetchUpdates(Instant since, int limit);
        Promise<Boolean> pushUpdates(List<SyncEntry> entries);
        Promise<Void> sendHeartbeat(EdgeStatus status);
    }

    /**
     * Entry for synchronization.
     */
    public record SyncEntry(
        String key,
        byte[] data,
        long version,
        Instant modifiedAt,
        Map<String, String> metadata,
        boolean deleted
    ) {}

    /**
     * Creates a new LightweightEdgeDeployment.
     */
    public LightweightEdgeDeployment() {
        // Default constructor - initialization via initialize()
    }

    /**
     * Creates a new LightweightEdgeDeployment with central connection.
     *
     * @param centralConnection connection to central Data-Cloud
     */
    public LightweightEdgeDeployment(CentralConnection centralConnection) {
        this.centralConnection = centralConnection;
    }

    // ==================== Lifecycle ====================

    @Override
    public Promise<Void> initialize(EdgeConfig config) {
        if (initialized.compareAndSet(false, true)) {
            this.config = Objects.requireNonNull(config, "config");
            
            logger.info("Initializing edge deployment: id={}, region={}", 
                config.edgeId(), config.region());

            // Initialize executors with resource limits
            int threads = Math.min(2, Runtime.getRuntime().availableProcessors());
            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "edge-scheduler-" + config.edgeId());
                t.setDaemon(true);
                return t;
            });
            this.syncExecutor = Executors.newFixedThreadPool(threads, r -> {
                Thread t = new Thread(r, "edge-sync-" + config.edgeId());
                t.setDaemon(true);
                return t;
            });

            // Initialize metrics if registry available
            if (meterRegistry != null) {
                initializeMetrics();
            }

            // Schedule periodic tasks
            scheduleSyncTask();
            scheduleHeartbeat();
            scheduleResourceCheck();

            mode.set(EdgeMode.DISCONNECTED);
            logger.info("Edge deployment initialized: {}", config.edgeId());
        }
        return Promise.complete();
    }

    /**
     * Sets the meter registry for metrics.
     *
     * @param registry the Micrometer registry
     */
    public void setMeterRegistry(MeterRegistry registry) {
        this.meterRegistry = registry;
        if (initialized.get()) {
            initializeMetrics();
        }
    }

    /**
     * Sets the central connection.
     *
     * @param connection the central connection
     */
    public void setCentralConnection(CentralConnection connection) {
        this.centralConnection = connection;
    }

    private void initializeMetrics() {
        syncSuccessCounter = Counter.builder("datacloud.edge.sync.success")
            .tag("edgeId", config.edgeId())
            .register(meterRegistry);
        syncFailureCounter = Counter.builder("datacloud.edge.sync.failure")
            .tag("edgeId", config.edgeId())
            .register(meterRegistry);
        conflictCounter = Counter.builder("datacloud.edge.conflicts")
            .tag("edgeId", config.edgeId())
            .register(meterRegistry);

        Gauge.builder("datacloud.edge.pending.upload", pendingUpload, BlockingQueue::size)
            .tag("edgeId", config.edgeId())
            .register(meterRegistry);
        Gauge.builder("datacloud.edge.pending.download", pendingDownload, BlockingQueue::size)
            .tag("edgeId", config.edgeId())
            .register(meterRegistry);
        Gauge.builder("datacloud.edge.local.entries", localStorage, ConcurrentMap::size)
            .tag("edgeId", config.edgeId())
            .register(meterRegistry);
    }

    @Override
    public Promise<Void> connect() {
        if (!initialized.get()) {
            return Promise.ofException(new IllegalStateException("Edge not initialized"));
        }

        if (centralConnection == null) {
            logger.warn("No central connection configured - operating in standalone mode");
            mode.set(EdgeMode.DISCONNECTED);
            return Promise.complete();
        }

        logger.info("Connecting to central: {}", config.centralUrl());

        return Promise.ofBlocking(syncExecutor, () -> {
            try {
                Boolean result = centralConnection.connect(config.centralUrl(), config.edgeId()).getResult();
                if (Boolean.TRUE.equals(result)) {
                    connected.set(true);
                    mode.set(EdgeMode.CONNECTED);
                    lastHeartbeat = Instant.now();
                    
                    // Notify hooks
                    hooks.values().forEach(h -> {
                        try {
                            h.onConnect();
                        } catch (Exception e) {
                            logger.warn("Hook onConnect failed", e);
                        }
                    });

                    // Trigger initial sync
                    syncNow(SyncDirection.BIDIRECTIONAL);

                    logger.info("Connected to central Data-Cloud");
                } else {
                    logger.warn("Failed to connect to central");
                    mode.set(EdgeMode.DISCONNECTED);
                }
            } catch (Exception e) {
                logger.error("Error connecting to central", e);
                mode.set(EdgeMode.DISCONNECTED);
                throw e;
            }
            return null;
        });
    }

    @Override
    public Promise<Void> disconnect(boolean graceful) {
        if (!connected.get()) {
            return Promise.complete();
        }

        logger.info("Disconnecting from central (graceful={})", graceful);

        return Promise.ofBlocking(syncExecutor, () -> {
            if (graceful) {
                // Flush pending uploads
                try {
                    syncNow(SyncDirection.UPLOAD).getResult();
                } catch (Exception e) {
                    logger.warn("Error flushing during graceful disconnect", e);
                }
            }

            if (centralConnection != null) {
                centralConnection.disconnect().getResult();
            }

            EdgeMode oldMode = mode.get();
            connected.set(false);
            mode.set(EdgeMode.DISCONNECTED);

            // Notify hooks
            hooks.values().forEach(h -> {
                try {
                    h.onDisconnect();
                } catch (Exception e) {
                    logger.warn("Hook onDisconnect failed", e);
                }
            });

            logger.info("Disconnected from central");
            return null;
        });
    }

    @Override
    public Promise<Void> shutdown() {
        logger.info("Shutting down edge deployment: {}", config != null ? config.edgeId() : "uninitialized");

        return disconnect(true).then(() -> {
            if (scheduler != null) {
                scheduler.shutdown();
            }
            if (syncExecutor != null) {
                syncExecutor.shutdown();
            }

            try {
                if (scheduler != null && !scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
                if (syncExecutor != null && !syncExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    syncExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            initialized.set(false);
            logger.info("Edge deployment shut down");
            return Promise.complete();
        });
    }

    // ==================== Mode Management ====================

    @Override
    public EdgeMode getMode() {
        return mode.get();
    }

    @Override
    public Promise<Void> setMode(EdgeMode newMode) {
        EdgeMode oldMode = mode.getAndSet(newMode);
        if (oldMode != newMode) {
            logger.info("Edge mode changed: {} -> {}", oldMode, newMode);
            hooks.values().forEach(h -> {
                try {
                    h.onModeChange(oldMode, newMode);
                } catch (Exception e) {
                    logger.warn("Hook onModeChange failed", e);
                }
            });
        }
        return Promise.complete();
    }

    @Override
    public boolean isConnected() {
        return connected.get();
    }

    // ==================== Synchronization ====================

    @Override
    public Promise<EdgeSyncEvent> syncNow(SyncDirection direction) {
        if (!initialized.get()) {
            return Promise.ofException(new IllegalStateException("Edge not initialized"));
        }

        logger.debug("Starting sync: direction={}", direction);

        // Notify hooks
        hooks.values().forEach(h -> {
            try {
                h.beforeSync(direction);
            } catch (Exception e) {
                logger.warn("Hook beforeSync failed", e);
            }
        });

        return Promise.ofBlocking(syncExecutor, () -> {
            Instant startTime = Instant.now();
            long entriesSynced = 0;
            long bytesSynced = 0;
            long conflictsDetected = 0;
            long conflictsResolvedLocal = 0;
            String errorMessage = null;
            boolean success = true;

            try {
                if (!connected.get() || centralConnection == null) {
                    // Offline - queue changes for later
                    logger.debug("Offline sync - changes queued");
                    return createSyncEvent(direction, 0, 0, 0, 0, startTime, true, null);
                }

                // Upload
                if (direction == SyncDirection.UPLOAD || direction == SyncDirection.BIDIRECTIONAL) {
                    SyncResult uploadResult = performUpload();
                    entriesSynced += uploadResult.entriesSynced;
                    bytesSynced += uploadResult.bytesSynced;
                }

                // Download
                if (direction == SyncDirection.DOWNLOAD || direction == SyncDirection.BIDIRECTIONAL) {
                    SyncResult downloadResult = performDownload();
                    entriesSynced += downloadResult.entriesSynced;
                    bytesSynced += downloadResult.bytesSynced;
                    conflictsDetected += downloadResult.conflictsDetected;
                    conflictsResolvedLocal += downloadResult.conflictsResolved;
                }

                lastSyncTime = Instant.now();
                totalSynced += entriesSynced;
                conflictsResolved += conflictsResolvedLocal;

                if (syncSuccessCounter != null) {
                    syncSuccessCounter.increment();
                }

            } catch (Exception e) {
                success = false;
                errorMessage = e.getMessage();
                logger.error("Sync failed", e);

                if (syncFailureCounter != null) {
                    syncFailureCounter.increment();
                }

                // Notify hooks
                for (EdgeHook hook : hooks.values()) {
                    try {
                        hook.onSyncFailure(direction, e);
                    } catch (Exception he) {
                        logger.warn("Hook onSyncFailure failed", he);
                    }
                }
            }

            EdgeSyncEvent event = createSyncEvent(
                direction, entriesSynced, bytesSynced,
                conflictsDetected, conflictsResolvedLocal,
                startTime, success, errorMessage
            );

            recordSyncHistory(event);

            // Notify hooks
            for (EdgeHook hook : hooks.values()) {
                try {
                    hook.afterSync(event);
                } catch (Exception e) {
                    logger.warn("Hook afterSync failed", e);
                }
            }

            return event;
        });
    }

    private record SyncResult(long entriesSynced, long bytesSynced, long conflictsDetected, long conflictsResolved) {}

    private SyncResult performUpload() throws Exception {
        List<SyncEntry> toUpload = new ArrayList<>();
        int batchSize = config.syncConfig().batchSize();
        long bytesSynced = 0;

        // Collect dirty entries
        for (LocalEntry entry : localStorage.values()) {
            if (entry.dirty() && shouldSync(entry.key(), entry.metadata(), SyncDirection.UPLOAD)) {
                toUpload.add(new SyncEntry(
                    entry.key(),
                    entry.data(),
                    entry.version(),
                    entry.modifiedAt(),
                    entry.metadata(),
                    false
                ));
                bytesSynced += entry.data() != null ? entry.data().length : 0;

                if (toUpload.size() >= batchSize) {
                    break;
                }
            }
        }

        if (toUpload.isEmpty()) {
            return new SyncResult(0, 0, 0, 0);
        }

        // Push to central
        Boolean success = centralConnection.pushUpdates(toUpload).getResult();
        if (Boolean.TRUE.equals(success)) {
            // Mark as synced
            for (SyncEntry entry : toUpload) {
                LocalEntry local = localStorage.get(entry.key());
                if (local != null) {
                    localStorage.put(entry.key(), new LocalEntry(
                        local.key(),
                        local.data(),
                        local.version(),
                        local.modifiedAt(),
                        Instant.now(),
                        false,
                        local.metadata()
                    ));
                }
            }
            return new SyncResult(toUpload.size(), bytesSynced, 0, 0);
        }

        throw new RuntimeException("Upload failed");
    }

    private SyncResult performDownload() throws Exception {
        List<SyncEntry> updates = centralConnection.fetchUpdates(lastSyncTime, config.syncConfig().batchSize()).getResult();
        
        if (updates == null || updates.isEmpty()) {
            return new SyncResult(0, 0, 0, 0);
        }

        long bytesSynced = 0;
        long conflictsDetected = 0;
        long conflictsResolvedCount = 0;

        for (SyncEntry remote : updates) {
            if (!shouldSync(remote.key(), remote.metadata(), SyncDirection.DOWNLOAD)) {
                continue;
            }

            LocalEntry local = localStorage.get(remote.key());
            
            if (local != null && local.dirty()) {
                // Conflict!
                conflictsDetected++;
                SyncEntry resolved = resolveConflict(local, remote);
                if (resolved != null) {
                    applyUpdate(resolved);
                    conflictsResolvedCount++;
                }
            } else if (!remote.deleted()) {
                applyUpdate(remote);
                bytesSynced += remote.data() != null ? remote.data().length : 0;
            } else {
                // Handle deletion
                localStorage.remove(remote.key());
            }
        }

        if (conflictCounter != null && conflictsDetected > 0) {
            conflictCounter.increment(conflictsDetected);
        }

        return new SyncResult(updates.size(), bytesSynced, conflictsDetected, conflictsResolvedCount);
    }

    private void applyUpdate(SyncEntry entry) {
        localStorage.put(entry.key(), new LocalEntry(
            entry.key(),
            entry.data(),
            entry.version(),
            entry.modifiedAt(),
            Instant.now(),
            false,
            entry.metadata()
        ));
    }

    private SyncEntry resolveConflict(LocalEntry local, SyncEntry remote) {
        ConflictResolution resolution = config.syncConfig().conflictResolution();
        
        return switch (resolution) {
            case EDGE_WINS -> new SyncEntry(
                local.key(), local.data(), local.version(),
                local.modifiedAt(), local.metadata(), false
            );
            case CENTRAL_WINS -> remote;
            case LAST_WRITE_WINS -> local.modifiedAt().isAfter(remote.modifiedAt())
                ? new SyncEntry(local.key(), local.data(), local.version(), 
                    local.modifiedAt(), local.metadata(), false)
                : remote;
            case MERGE -> mergeEntries(local, remote);
        };
    }

    private SyncEntry mergeEntries(LocalEntry local, SyncEntry remote) {
        // Simple merge - take remote data but keep local metadata
        Map<String, String> mergedMetadata = new HashMap<>(remote.metadata());
        mergedMetadata.putAll(local.metadata());
        
        return new SyncEntry(
            remote.key(),
            remote.data(),
            Math.max(local.version(), remote.version()) + 1,
            Instant.now(),
            mergedMetadata,
            false
        );
    }

    private boolean shouldSync(String key, Map<String, String> metadata, SyncDirection direction) {
        for (SyncFilter filter : syncFilters.values()) {
            if (!filter.shouldSync(key, metadata, direction)) {
                return false;
            }
        }
        return true;
    }

    private EdgeSyncEvent createSyncEvent(
            SyncDirection direction,
            long entriesSynced,
            long bytesSynced,
            long conflictsDetected,
            long conflictsResolved,
            Instant startTime,
            boolean success,
            String errorMessage) {
        return new EdgeSyncEvent(
            config.edgeId(),
            Instant.now(),
            direction,
            entriesSynced,
            bytesSynced,
            conflictsDetected,
            conflictsResolved,
            Duration.between(startTime, Instant.now()),
            success,
            errorMessage
        );
    }

    private void recordSyncHistory(EdgeSyncEvent event) {
        syncHistory.offer(event);
        while (syncHistory.size() > MAX_SYNC_HISTORY) {
            syncHistory.poll();
        }
    }

    @Override
    public Promise<EdgeSyncEvent> fullSync() {
        logger.info("Starting full sync for edge: {}", config.edgeId());
        lastSyncTime = null; // Reset to sync everything
        lastFullSyncTime = Instant.now();
        return syncNow(SyncDirection.BIDIRECTIONAL);
    }

    @Override
    public Promise<Map<SyncDirection, Long>> getPendingSyncCounts() {
        long dirtyCount = localStorage.values().stream()
            .filter(LocalEntry::dirty)
            .count();
        
        return Promise.of(Map.of(
            SyncDirection.UPLOAD, dirtyCount,
            SyncDirection.DOWNLOAD, (long) pendingDownload.size()
        ));
    }

    @Override
    public void registerSyncFilter(String filterId, SyncFilter filter) {
        syncFilters.put(filterId, filter);
        logger.info("Registered sync filter: {}", filterId);
    }

    @Override
    public void unregisterSyncFilter(String filterId) {
        syncFilters.remove(filterId);
        logger.info("Unregistered sync filter: {}", filterId);
    }

    // ==================== Status & Monitoring ====================

    @Override
    public EdgeStatus getStatus() {
        long pendingUp = localStorage.values().stream().filter(LocalEntry::dirty).count();
        
        return new EdgeStatus(
            config != null ? config.edgeId() : "uninitialized",
            mode.get(),
            connected.get(),
            lastSyncTime,
            lastHeartbeat,
            new SyncStatus(
                pendingUp,
                pendingDownload.size(),
                0, // Would track actual sync duration
                totalSynced,
                conflictsResolved,
                lastFullSyncTime
            ),
            new ResourceUsage(
                getUsedMemoryMb(),
                getUsedDiskMb(),
                getCpuPercent(),
                0 // Would track actual connections
            ),
            List.of(),
            Map.of()
        );
    }

    @Override
    public EdgeConfig getConfig() {
        return config;
    }

    @Override
    public Promise<List<EdgeSyncEvent>> getSyncHistory(Instant since, int limit) {
        List<EdgeSyncEvent> filtered = syncHistory.stream()
            .filter(e -> since == null || e.timestamp().isAfter(since))
            .sorted(Comparator.comparing(EdgeSyncEvent::timestamp).reversed())
            .limit(limit)
            .toList();
        return Promise.of(filtered);
    }

    private long getUsedMemoryMb() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    private long getUsedDiskMb() {
        // Estimate based on local storage
        return localStorage.values().stream()
            .mapToLong(e -> e.data() != null ? e.data().length : 0)
            .sum() / (1024 * 1024);
    }

    private double getCpuPercent() {
        // Would use OS metrics in production
        return 0.0;
    }

    // ==================== Data Access ====================

    @Override
    public Promise<Boolean> isDataAvailable(String key) {
        return Promise.of(localStorage.containsKey(key));
    }

    @Override
    public Promise<Void> prefetch(Set<String> keys) {
        if (!connected.get() || centralConnection == null) {
            return Promise.ofException(new IllegalStateException("Not connected"));
        }

        logger.info("Prefetching {} keys", keys.size());
        
        return Promise.ofBlocking(syncExecutor, () -> {
            for (String key : keys) {
                if (!localStorage.containsKey(key)) {
                    pendingDownload.offer(key);
                }
            }
            // Trigger sync
            syncNow(SyncDirection.DOWNLOAD);
            return null;
        });
    }

    @Override
    public Promise<Void> evict(Set<String> keys) {
        logger.info("Evicting {} keys", keys.size());
        for (String key : keys) {
            LocalEntry entry = localStorage.get(key);
            if (entry != null && !entry.dirty()) {
                localStorage.remove(key);
            }
        }
        return Promise.complete();
    }

    @Override
    public Promise<EdgeDataStats> getDataStats() {
        long totalEntries = localStorage.size();
        long totalBytes = localStorage.values().stream()
            .mapToLong(e -> e.data() != null ? e.data().length : 0)
            .sum();
        long localOnly = localStorage.values().stream()
            .filter(LocalEntry::dirty)
            .count();
        long synced = localStorage.values().stream()
            .filter(e -> !e.dirty() && e.syncedAt() != null)
            .count();
        long stale = localStorage.values().stream()
            .filter(e -> e.syncedAt() != null && 
                e.syncedAt().isBefore(Instant.now().minus(Duration.ofHours(24))))
            .count();

        return Promise.of(new EdgeDataStats(
            totalEntries,
            totalBytes,
            localOnly,
            synced,
            stale,
            Map.of() // Would categorize by type
        ));
    }

    // ==================== Hooks ====================

    @Override
    public void registerHook(String hookId, EdgeHook hook) {
        hooks.put(hookId, hook);
        logger.info("Registered edge hook: {}", hookId);
    }

    @Override
    public void unregisterHook(String hookId) {
        hooks.remove(hookId);
        logger.info("Unregistered edge hook: {}", hookId);
    }

    // ==================== Local Data Operations ====================

    /**
     * Stores data locally.
     *
     * @param key the key
     * @param data the data
     * @param metadata optional metadata
     * @return promise completed when stored
     */
    public Promise<Void> storeLocal(String key, byte[] data, Map<String, String> metadata) {
        LocalEntry existing = localStorage.get(key);
        long version = existing != null ? existing.version() + 1 : 1;
        
        localStorage.put(key, new LocalEntry(
            key,
            data,
            version,
            Instant.now(),
            null,
            true, // dirty - needs sync
            metadata != null ? metadata : Map.of()
        ));
        
        return Promise.complete();
    }

    /**
     * Gets data locally.
     *
     * @param key the key
     * @return promise of data or null
     */
    public Promise<byte[]> getLocal(String key) {
        LocalEntry entry = localStorage.get(key);
        return Promise.of(entry != null ? entry.data() : null);
    }

    /**
     * Deletes data locally.
     *
     * @param key the key
     * @return promise of whether deleted
     */
    public Promise<Boolean> deleteLocal(String key) {
        LocalEntry removed = localStorage.remove(key);
        return Promise.of(removed != null);
    }

    // ==================== Scheduled Tasks ====================

    private void scheduleSyncTask() {
        if (scheduler != null && config != null) {
            Duration interval = config.syncConfig().syncInterval();
            scheduler.scheduleAtFixedRate(
                () -> {
                    if (connected.get() && mode.get() == EdgeMode.CONNECTED) {
                        try {
                            syncNow(SyncDirection.BIDIRECTIONAL);
                        } catch (Exception e) {
                            logger.warn("Scheduled sync failed", e);
                        }
                    }
                },
                interval.toMillis(),
                interval.toMillis(),
                TimeUnit.MILLISECONDS
            );
        }
    }

    private void scheduleHeartbeat() {
        if (scheduler != null && centralConnection != null) {
            scheduler.scheduleAtFixedRate(
                () -> {
                    if (connected.get()) {
                        try {
                            centralConnection.sendHeartbeat(getStatus());
                            lastHeartbeat = Instant.now();
                        } catch (Exception e) {
                            logger.warn("Heartbeat failed - checking connection", e);
                            checkConnection();
                        }
                    }
                },
                30000,
                30000,
                TimeUnit.MILLISECONDS
            );
        }
    }

    private void scheduleResourceCheck() {
        if (scheduler != null && config != null) {
            scheduler.scheduleAtFixedRate(
                this::checkResourceLimits,
                60000,
                60000,
                TimeUnit.MILLISECONDS
            );
        }
    }

    private void checkConnection() {
        if (centralConnection == null) {
            return;
        }

        try {
            Boolean isConnected = centralConnection.isConnected().getResult();
            if (!Boolean.TRUE.equals(isConnected)) {
                connected.set(false);
                mode.set(EdgeMode.DISCONNECTED);
                
                // Attempt reconnection
                scheduler.schedule(() -> {
                    try {
                        connect();
                    } catch (Exception e) {
                        logger.warn("Reconnection failed", e);
                    }
                }, 30, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            logger.warn("Connection check failed", e);
        }
    }

    private void checkResourceLimits() {
        if (config == null) {
            return;
        }

        ResourceLimits limits = config.resourceLimits();
        long usedMemory = getUsedMemoryMb();
        long usedDisk = getUsedDiskMb();

        if (usedMemory > limits.maxMemoryMb() * 0.9) {
            logger.warn("Memory usage high: {}MB / {}MB", usedMemory, limits.maxMemoryMb());
            evictStaleData();
        }

        if (usedDisk > limits.maxDiskMb() * 0.9) {
            logger.warn("Disk usage high: {}MB / {}MB", usedDisk, limits.maxDiskMb());
            evictStaleData();
        }
    }

    private void evictStaleData() {
        // Evict oldest synced entries
        localStorage.entrySet().stream()
            .filter(e -> !e.getValue().dirty() && e.getValue().syncedAt() != null)
            .sorted(Comparator.comparing(e -> e.getValue().syncedAt()))
            .limit(localStorage.size() / 10) // Evict 10%
            .forEach(e -> localStorage.remove(e.getKey()));
    }
}
