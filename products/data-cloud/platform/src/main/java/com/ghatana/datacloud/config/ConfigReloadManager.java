/*
 * Copyright (c) 2025 Ghatana Platforms, Inc. All rights reserved.
 */
package com.ghatana.datacloud.config;

import com.ghatana.datacloud.config.model.ConfigKey;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages graceful hot-reload of configurations with versioned atomic swap.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides zero-downtime configuration reloading with:
 * <ul>
 * <li>Versioned snapshots for rollback capability</li>
 * <li>Atomic swap to prevent partial updates</li>
 * <li>Graceful transition with configurable drain period</li>
 * <li>Metrics for reload latency and success/failure tracking</li>
 * </ul>
 *
 * <p>
 * <b>Hot-Reload Process</b><br>
 * <pre>
 * 1. Load new configuration from source (YAML/remote)
 * 2. Validate new configuration
 * 3. Compile to runtime objects
 * 4. Create new snapshot with incremented version
 * 5. Atomic swap: replace old snapshot with new
 * 6. Emit metrics and log reload event
 * </pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe. Uses AtomicReference for snapshot swapping and AtomicBoolean to
 * prevent concurrent reloads.
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ConfigReloadManager reloadManager = new ConfigReloadManager(registry, metrics);
 *
 * // Trigger reload for specific config
 * reloadManager.reloadAsync("tenant-1", "users")
 *     .whenResult(snapshot -> log.info("Reloaded to version {}", snapshot.version()))
 *     .whenException(e -> log.error("Reload failed", e));
 *
 * // Reload all configs for a tenant
 * reloadManager.reloadAllAsync("tenant-1");
 *
 * // Get current reload status
 * ReloadStatus status = reloadManager.getStatus();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Graceful hot-reload manager with versioned atomic swap
 * @doc.layer core
 * @doc.pattern Manager
 */
public class ConfigReloadManager {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigReloadManager.class);

    // Metric names
    private static final String METRIC_RELOAD_DURATION = "config.reload.duration";
    private static final String METRIC_RELOAD_SUCCESS = "config.reload.success";
    private static final String METRIC_RELOAD_FAILURE = "config.reload.failure";

    private final ConfigRegistry registry;
    private final MetricsCollector metrics;

    // Reload state
    private final AtomicReference<ReloadSnapshot> currentSnapshot;
    private final AtomicBoolean reloadInProgress;
    private final AtomicLong totalReloads;
    private final AtomicLong failedReloads;

    // History for rollback (last N snapshots)
    private final ConcurrentHashMap<Long, ReloadSnapshot> snapshotHistory;
    private static final int MAX_HISTORY_SIZE = 5;

    /**
     * Creates a new ConfigReloadManager.
     *
     * @param registry the config registry to manage
     * @param metrics the metrics collector for observability
     */
    public ConfigReloadManager(
            @NotNull ConfigRegistry registry,
            @NotNull MetricsCollector metrics) {
        this.registry = Objects.requireNonNull(registry, "registry required");
        this.metrics = Objects.requireNonNull(metrics, "metrics required");

        this.currentSnapshot = new AtomicReference<>(ReloadSnapshot.initial());
        this.reloadInProgress = new AtomicBoolean(false);
        this.totalReloads = new AtomicLong(0);
        this.failedReloads = new AtomicLong(0);
        this.snapshotHistory = new ConcurrentHashMap<>();

        // Store initial snapshot in history
        snapshotHistory.put(0L, currentSnapshot.get());
    }

    /**
     * Reloads a specific collection configuration asynchronously.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @return promise of the new reload snapshot
     */
    public Promise<ReloadSnapshot> reloadCollectionAsync(String tenantId, String collectionName) {
        if (!reloadInProgress.compareAndSet(false, true)) {
            return Promise.ofException(new ReloadInProgressException(
                    "Reload already in progress, please wait"));
        }

        Instant startTime = Instant.now();
        ConfigKey key = ConfigKey.collection(tenantId, collectionName);

        return registry.reloadAsync(tenantId, collectionName)
                .map(v -> {
                    long newVersion = currentSnapshot.get().version() + 1;
                    ReloadSnapshot newSnapshot = new ReloadSnapshot(
                            newVersion,
                            Instant.now(),
                            Map.of(key, newVersion),
                            ReloadStatus.SUCCESS,
                            null);

                    // Atomic swap
                    currentSnapshot.getAndSet(newSnapshot);

                    // Store in history
                    storeInHistory(newSnapshot);

                    // Update metrics
                    Duration reloadDuration = Duration.between(startTime, Instant.now());
                    recordReloadSuccess(key, reloadDuration);

                    LOG.info("Reloaded collection {} for tenant {} to version {} in {}ms",
                            collectionName, tenantId, newVersion, reloadDuration.toMillis());

                    return newSnapshot;
                })
                .whenException(e -> {
                    recordReloadFailure(key, e);
                    LOG.error("Failed to reload collection {} for tenant {}: {}",
                            collectionName, tenantId, e.getMessage());
                })
                .whenComplete(() -> reloadInProgress.set(false));
    }

    /**
     * Reloads a specific plugin configuration asynchronously.
     *
     * @param tenantId the tenant identifier
     * @param pluginName the plugin name
     * @return promise of the new reload snapshot
     */
    public Promise<ReloadSnapshot> reloadPluginAsync(String tenantId, String pluginName) {
        if (!reloadInProgress.compareAndSet(false, true)) {
            return Promise.ofException(new ReloadInProgressException(
                    "Reload already in progress, please wait"));
        }

        Instant startTime = Instant.now();
        ConfigKey key = ConfigKey.plugin(tenantId, pluginName);

        return registry.reloadPlugin(tenantId, pluginName)
                .map(v -> {
                    long newVersion = currentSnapshot.get().version() + 1;
                    ReloadSnapshot newSnapshot = new ReloadSnapshot(
                            newVersion,
                            Instant.now(),
                            Map.of(key, newVersion),
                            ReloadStatus.SUCCESS,
                            null);

                    currentSnapshot.getAndSet(newSnapshot);
                    storeInHistory(newSnapshot);

                    Duration reloadDuration = Duration.between(startTime, Instant.now());
                    recordReloadSuccess(key, reloadDuration);

                    LOG.info("Reloaded plugin {} for tenant {} to version {} in {}ms",
                            pluginName, tenantId, newVersion, reloadDuration.toMillis());

                    return newSnapshot;
                })
                .whenException(e -> {
                    recordReloadFailure(key, e);
                    LOG.error("Failed to reload plugin {} for tenant {}: {}",
                            pluginName, tenantId, e.getMessage());
                })
                .whenComplete(() -> reloadInProgress.set(false));
    }

    /**
     * Reloads a specific routing configuration asynchronously.
     *
     * @param tenantId the tenant identifier
     * @param routingName the routing config name
     * @return promise of the new reload snapshot
     */
    public Promise<ReloadSnapshot> reloadRoutingAsync(String tenantId, String routingName) {
        if (!reloadInProgress.compareAndSet(false, true)) {
            return Promise.ofException(new ReloadInProgressException(
                    "Reload already in progress, please wait"));
        }

        Instant startTime = Instant.now();
        ConfigKey key = ConfigKey.routing(tenantId, routingName);

        return registry.reloadRouting(tenantId, routingName)
                .map(v -> {
                    long newVersion = currentSnapshot.get().version() + 1;
                    ReloadSnapshot newSnapshot = new ReloadSnapshot(
                            newVersion,
                            Instant.now(),
                            Map.of(key, newVersion),
                            ReloadStatus.SUCCESS,
                            null);

                    currentSnapshot.getAndSet(newSnapshot);
                    storeInHistory(newSnapshot);

                    Duration reloadDuration = Duration.between(startTime, Instant.now());
                    recordReloadSuccess(key, reloadDuration);

                    LOG.info("Reloaded routing {} for tenant {} to version {} in {}ms",
                            routingName, tenantId, newVersion, reloadDuration.toMillis());

                    return newSnapshot;
                })
                .whenException(e -> {
                    recordReloadFailure(key, e);
                    LOG.error("Failed to reload routing {} for tenant {}: {}",
                            routingName, tenantId, e.getMessage());
                })
                .whenComplete(() -> reloadInProgress.set(false));
    }

    /**
     * Reloads all configurations asynchronously.
     *
     * @return promise of the new reload snapshot
     */
    public Promise<ReloadSnapshot> reloadAllAsync() {
        if (!reloadInProgress.compareAndSet(false, true)) {
            return Promise.ofException(new ReloadInProgressException(
                    "Reload already in progress, please wait"));
        }

        Instant startTime = Instant.now();

        return registry.reloadAllAsync()
                .map(v -> {
                    long newVersion = currentSnapshot.get().version() + 1;
                    ReloadSnapshot newSnapshot = new ReloadSnapshot(
                            newVersion,
                            Instant.now(),
                            Map.of(),
                            ReloadStatus.SUCCESS,
                            "Reloaded all configurations");

                    currentSnapshot.getAndSet(newSnapshot);
                    storeInHistory(newSnapshot);

                    Duration reloadDuration = Duration.between(startTime, Instant.now());
                    metrics.recordTimer(METRIC_RELOAD_DURATION, reloadDuration.toMillis(),
                            "scope", "all");
                    metrics.incrementCounter(METRIC_RELOAD_SUCCESS,
                            "scope", "all");
                    totalReloads.incrementAndGet();

                    LOG.info("Reloaded all configurations to version {} in {}ms",
                            newVersion, reloadDuration.toMillis());

                    return newSnapshot;
                })
                .whenException(e -> {
                    metrics.incrementCounter(METRIC_RELOAD_FAILURE,
                            "scope", "all");
                    failedReloads.incrementAndGet();
                    LOG.error("Failed to reload all configurations: {}", e.getMessage());
                })
                .whenComplete(() -> reloadInProgress.set(false));
    }

    /**
     * Rolls back to a previous snapshot version.
     *
     * @param targetVersion the version to rollback to
     * @return promise indicating success
     */
    public Promise<ReloadSnapshot> rollbackToVersion(long targetVersion) {
        ReloadSnapshot targetSnapshot = snapshotHistory.get(targetVersion);
        if (targetSnapshot == null) {
            return Promise.ofException(new IllegalArgumentException(
                    "Snapshot version " + targetVersion + " not found in history"));
        }

        // Note: This only updates the snapshot reference.
        // Full rollback would require re-loading configs from versioned storage.
        LOG.warn("Rollback to version {} requested. Note: This is a metadata-only rollback.",
                targetVersion);

        ReloadSnapshot rollbackSnapshot = new ReloadSnapshot(
                currentSnapshot.get().version() + 1,
                Instant.now(),
                targetSnapshot.configVersions(),
                ReloadStatus.ROLLED_BACK,
                "Rolled back from version " + currentSnapshot.get().version() + " to " + targetVersion);

        currentSnapshot.set(rollbackSnapshot);
        storeInHistory(rollbackSnapshot);

        return Promise.of(rollbackSnapshot);
    }

    /**
     * Gets the current reload snapshot.
     *
     * @return the current snapshot
     */
    public ReloadSnapshot getCurrentSnapshot() {
        return currentSnapshot.get();
    }

    /**
     * Gets the reload status.
     *
     * @return the current reload status
     */
    public ReloadStatusInfo getStatus() {
        ReloadSnapshot snapshot = currentSnapshot.get();
        return new ReloadStatusInfo(
                snapshot.version(),
                snapshot.timestamp(),
                snapshot.status(),
                reloadInProgress.get(),
                totalReloads.get(),
                failedReloads.get(),
                getAvailableRollbackVersions());
    }

    /**
     * Gets available rollback versions.
     *
     * @return list of available versions
     */
    public List<Long> getAvailableRollbackVersions() {
        return snapshotHistory.keySet().stream()
                .sorted()
                .toList();
    }

    /**
     * Checks if a reload is currently in progress.
     *
     * @return true if reload is in progress
     */
    public boolean isReloadInProgress() {
        return reloadInProgress.get();
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================
    private void storeInHistory(ReloadSnapshot snapshot) {
        snapshotHistory.put(snapshot.version(), snapshot);

        // Prune old snapshots if history exceeds max size
        if (snapshotHistory.size() > MAX_HISTORY_SIZE) {
            snapshotHistory.keySet().stream()
                    .sorted()
                    .limit(snapshotHistory.size() - MAX_HISTORY_SIZE)
                    .forEach(snapshotHistory::remove);
        }
    }

    private void recordReloadSuccess(ConfigKey key, Duration duration) {
        metrics.recordTimer(METRIC_RELOAD_DURATION, duration.toMillis(),
                "type", key.type().name(), "name", key.name());
        metrics.incrementCounter(METRIC_RELOAD_SUCCESS,
                "type", key.type().name(), "name", key.name());
        totalReloads.incrementAndGet();
    }

    private void recordReloadFailure(ConfigKey key, Throwable e) {
        metrics.incrementCounter(METRIC_RELOAD_FAILURE,
                "type", key.type().name(), "name", key.name(), "error", e.getClass().getSimpleName());
        failedReloads.incrementAndGet();
    }

    // =========================================================================
    // Inner records
    // =========================================================================
    /**
     * Snapshot of a reload operation.
     */
    public record ReloadSnapshot(
            long version,
            Instant timestamp,
            Map<ConfigKey, Long> configVersions,
            ReloadStatus status,
            String message) {
        /**
         * Creates the initial snapshot.
         */
    public static ReloadSnapshot initial() {
        return new ReloadSnapshot(0L, Instant.now(), Map.of(), ReloadStatus.INITIAL, null);
    }
}

/**
 * Status of reload operations.
 */
public enum ReloadStatus {
    INITIAL,
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILURE,
    ROLLED_BACK
}

/**
 * Detailed reload status information.
 */
public record ReloadStatusInfo(
        long currentVersion,
        Instant lastReloadTime,
        ReloadStatus lastReloadStatus,
        boolean reloadInProgress,
        long totalReloads,
        long failedReloads,
        List<Long> availableRollbackVersions) {

}

/**
 * Exception thrown when a reload is already in progress.
 */
public static class ReloadInProgressException extends RuntimeException {

    public ReloadInProgressException(String message) {
        super(message);
    }
}
}
