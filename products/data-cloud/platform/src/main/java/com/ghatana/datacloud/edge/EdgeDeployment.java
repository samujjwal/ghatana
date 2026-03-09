/*
 * Copyright (c) 2026 Ghatana.
 * All rights reserved.
 */
package com.ghatana.datacloud.edge;

import io.activej.promise.Promise;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Interface for edge deployment support in Data-Cloud.
 * 
 * <p>Enables lightweight Data-Cloud deployments at the edge with
 * selective sync, offline capabilities, and central coordination.
 *
 * @doc.type interface
 * @doc.purpose Edge deployment abstraction for Data-Cloud
 * @doc.layer core
 * @doc.pattern Gateway
 */
public interface EdgeDeployment {

    /**
     * Edge node modes.
     */
    enum EdgeMode {
        /** Full functionality, connected to central */
        CONNECTED,
        /** Limited functionality, temporarily disconnected */
        DISCONNECTED,
        /** Read-only mode for degraded operation */
        READ_ONLY,
        /** Sync-only mode for data synchronization */
        SYNC_ONLY
    }

    /**
     * Edge node configuration.
     */
    record EdgeConfig(
        String edgeId,
        String edgeName,
        String region,
        String centralUrl,
        EdgeCapabilities capabilities,
        SyncConfig syncConfig,
        ResourceLimits resourceLimits,
        Map<String, String> labels
    ) {
        public static Builder builder(String edgeId) {
            return new Builder(edgeId);
        }

        public static class Builder {
            private final String edgeId;
            private String edgeName;
            private String region = "default";
            private String centralUrl;
            private EdgeCapabilities capabilities = EdgeCapabilities.defaults();
            private SyncConfig syncConfig = SyncConfig.defaults();
            private ResourceLimits resourceLimits = ResourceLimits.defaults();
            private Map<String, String> labels = Map.of();

            public Builder(String edgeId) {
                this.edgeId = edgeId;
                this.edgeName = edgeId;
            }

            public Builder edgeName(String name) {
                this.edgeName = name;
                return this;
            }

            public Builder region(String region) {
                this.region = region;
                return this;
            }

            public Builder centralUrl(String url) {
                this.centralUrl = url;
                return this;
            }

            public Builder capabilities(EdgeCapabilities capabilities) {
                this.capabilities = capabilities;
                return this;
            }

            public Builder syncConfig(SyncConfig config) {
                this.syncConfig = config;
                return this;
            }

            public Builder resourceLimits(ResourceLimits limits) {
                this.resourceLimits = limits;
                return this;
            }

            public Builder labels(Map<String, String> labels) {
                this.labels = labels;
                return this;
            }

            public EdgeConfig build() {
                return new EdgeConfig(
                    edgeId, edgeName, region, centralUrl,
                    capabilities, syncConfig, resourceLimits, labels
                );
            }
        }
    }

    /**
     * Capabilities of an edge node.
     */
    record EdgeCapabilities(
        boolean supportsStorage,
        boolean supportsStreaming,
        boolean supportsAi,
        boolean supportsRouting,
        Set<String> enabledPlugins,
        long maxStorageBytes,
        int maxConcurrentOperations
    ) {
        public static EdgeCapabilities defaults() {
            return new EdgeCapabilities(
                true, true, false, true,
                Set.of(), 1024 * 1024 * 1024L, 100
            );
        }

        public static EdgeCapabilities minimal() {
            return new EdgeCapabilities(
                true, false, false, false,
                Set.of(), 256 * 1024 * 1024L, 10
            );
        }
    }

    /**
     * Sync configuration for edge-central synchronization.
     */
    record SyncConfig(
        Duration syncInterval,
        Duration fullSyncInterval,
        int batchSize,
        boolean bidirectionalSync,
        Set<String> syncFilters,
        ConflictResolution conflictResolution
    ) {
        public static SyncConfig defaults() {
            return new SyncConfig(
                Duration.ofMinutes(1),
                Duration.ofHours(24),
                1000,
                true,
                Set.of(),
                ConflictResolution.CENTRAL_WINS
            );
        }
    }

    /**
     * Conflict resolution strategy for sync conflicts.
     */
    enum ConflictResolution {
        EDGE_WINS,
        CENTRAL_WINS,
        LAST_WRITE_WINS,
        MERGE
    }

    /**
     * Resource limits for edge deployment.
     */
    record ResourceLimits(
        long maxMemoryMb,
        long maxDiskMb,
        double maxCpuPercent,
        int maxConnections
    ) {
        public static ResourceLimits defaults() {
            return new ResourceLimits(512, 2048, 50.0, 50);
        }

        public static ResourceLimits minimal() {
            return new ResourceLimits(128, 512, 25.0, 10);
        }
    }

    /**
     * Edge node status.
     */
    record EdgeStatus(
        String edgeId,
        EdgeMode mode,
        boolean connected,
        Instant lastSyncTime,
        Instant lastHeartbeat,
        SyncStatus syncStatus,
        ResourceUsage resourceUsage,
        List<String> activeOperations,
        Map<String, String> errors
    ) {}

    /**
     * Sync status for edge-central sync.
     */
    record SyncStatus(
        long pendingUpload,
        long pendingDownload,
        long lastSyncDurationMs,
        long totalSynced,
        long conflictsResolved,
        Instant lastFullSyncTime
    ) {}

    /**
     * Current resource usage at edge.
     */
    record ResourceUsage(
        long memoryUsedMb,
        long diskUsedMb,
        double cpuPercent,
        int activeConnections
    ) {}

    /**
     * Edge sync event.
     */
    record EdgeSyncEvent(
        String edgeId,
        Instant timestamp,
        SyncDirection direction,
        long entriesSynced,
        long bytesSynced,
        long conflictsDetected,
        long conflictsResolved,
        Duration duration,
        boolean success,
        String errorMessage
    ) {}

    /**
     * Sync direction.
     */
    enum SyncDirection {
        UPLOAD,    // Edge to Central
        DOWNLOAD,  // Central to Edge
        BIDIRECTIONAL
    }

    // ==================== Lifecycle ====================

    /**
     * Initializes the edge deployment.
     *
     * @param config edge configuration
     * @return promise completed when initialized
     */
    Promise<Void> initialize(EdgeConfig config);

    /**
     * Connects to central Data-Cloud.
     *
     * @return promise completed when connected
     */
    Promise<Void> connect();

    /**
     * Disconnects from central Data-Cloud.
     *
     * @param graceful whether to wait for pending syncs
     * @return promise completed when disconnected
     */
    Promise<Void> disconnect(boolean graceful);

    /**
     * Shuts down the edge deployment.
     *
     * @return promise completed when shut down
     */
    Promise<Void> shutdown();

    // ==================== Mode Management ====================

    /**
     * Gets current edge mode.
     *
     * @return current mode
     */
    EdgeMode getMode();

    /**
     * Sets edge mode.
     *
     * @param mode new mode
     * @return promise completed when mode changed
     */
    Promise<Void> setMode(EdgeMode mode);

    /**
     * Checks if edge is connected to central.
     *
     * @return true if connected
     */
    boolean isConnected();

    // ==================== Synchronization ====================

    /**
     * Triggers immediate sync with central.
     *
     * @param direction sync direction
     * @return promise of sync event
     */
    Promise<EdgeSyncEvent> syncNow(SyncDirection direction);

    /**
     * Performs a full sync (resync all data).
     *
     * @return promise of sync event
     */
    Promise<EdgeSyncEvent> fullSync();

    /**
     * Gets pending sync entries count.
     *
     * @return promise of pending counts (upload, download)
     */
    Promise<Map<SyncDirection, Long>> getPendingSyncCounts();

    /**
     * Registers a sync filter.
     *
     * @param filterId unique filter ID
     * @param filter the filter predicate
     */
    void registerSyncFilter(String filterId, SyncFilter filter);

    /**
     * Unregisters a sync filter.
     *
     * @param filterId the filter ID
     */
    void unregisterSyncFilter(String filterId);

    /**
     * Sync filter interface.
     */
    interface SyncFilter {
        /**
         * Determines if an entry should be synced.
         *
         * @param key the entry key
         * @param metadata entry metadata
         * @param direction sync direction
         * @return true if should sync
         */
        boolean shouldSync(String key, Map<String, String> metadata, SyncDirection direction);
    }

    // ==================== Status & Monitoring ====================

    /**
     * Gets current edge status.
     *
     * @return edge status
     */
    EdgeStatus getStatus();

    /**
     * Gets edge configuration.
     *
     * @return edge config
     */
    EdgeConfig getConfig();

    /**
     * Gets sync history.
     *
     * @param since only include events after this time
     * @param limit maximum events
     * @return promise of sync events
     */
    Promise<List<EdgeSyncEvent>> getSyncHistory(Instant since, int limit);

    // ==================== Data Access ====================

    /**
     * Checks if data is available locally at edge.
     *
     * @param key the data key
     * @return promise of availability
     */
    Promise<Boolean> isDataAvailable(String key);

    /**
     * Prefetches data from central to edge.
     *
     * @param keys keys to prefetch
     * @return promise completed when prefetched
     */
    Promise<Void> prefetch(Set<String> keys);

    /**
     * Evicts data from edge cache.
     *
     * @param keys keys to evict
     * @return promise completed when evicted
     */
    Promise<Void> evict(Set<String> keys);

    /**
     * Gets local data stats.
     *
     * @return promise of stats
     */
    Promise<EdgeDataStats> getDataStats();

    /**
     * Edge data statistics.
     */
    record EdgeDataStats(
        long totalEntries,
        long totalBytes,
        long localOnlyEntries,
        long syncedEntries,
        long staleEntries,
        Map<String, Long> entriesByType
    ) {}

    // ==================== Hooks ====================

    /**
     * Hook interface for edge lifecycle events.
     */
    interface EdgeHook {
        /** Called when edge connects to central */
        default void onConnect() {}
        
        /** Called when edge disconnects from central */
        default void onDisconnect() {}
        
        /** Called before sync starts */
        default void beforeSync(SyncDirection direction) {}
        
        /** Called after sync completes */
        default void afterSync(EdgeSyncEvent event) {}
        
        /** Called when sync fails */
        default void onSyncFailure(SyncDirection direction, Throwable error) {}
        
        /** Called when mode changes */
        default void onModeChange(EdgeMode oldMode, EdgeMode newMode) {}
    }

    /**
     * Registers an edge hook.
     *
     * @param hookId unique hook ID
     * @param hook the hook
     */
    void registerHook(String hookId, EdgeHook hook);

    /**
     * Unregisters an edge hook.
     *
     * @param hookId the hook ID
     */
    void unregisterHook(String hookId);
}
