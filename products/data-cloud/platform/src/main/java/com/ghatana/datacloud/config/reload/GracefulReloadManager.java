/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
 */
package com.ghatana.datacloud.config.reload;

import com.ghatana.datacloud.config.ConfigRegistry;
import com.ghatana.datacloud.config.ConfigRegistry.ConfigReloadListener;
import com.ghatana.datacloud.config.model.CompiledPluginConfig;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Graceful hot-reload manager for plugin configurations. Ensures zero-downtime
 * configuration updates by draining in-flight requests before switching.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides graceful hot-reload for plugins that maintain state or connections
 * (database pools, cache connections, etc.). Uses a drain-then-swap pattern to
 * ensure no requests are dropped.
 *
 * <p>
 * <b>Hot-Reload Strategy</b><br>
 * <ol>
 * <li>Mark old plugin as draining (stop accepting new requests)</li>
 * <li>Wait for in-flight requests to complete (with timeout)</li>
 * <li>Atomic swap to new configuration</li>
 * <li>Initialize new connections/pools</li>
 * <li>Cleanup old resources</li>
 * </ol>
 *
 * <p>
 * <b>Usage Example</b><br>
 * <pre>{@code
 * GracefulReloadManager manager = new GracefulReloadManager(configRegistry);
 *
 * // Register pool manager for PostgreSQL plugins
 * manager.registerReloadHandler("postgresql", plugin -> drainAndSwapPool(plugin));
 *
 * // Trigger reload (called automatically on config change)
 * manager.reloadPlugin("tenant-1", "postgres-primary")
 *     .whenResult(v -> log.info("Reload complete"));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Graceful hot-reload manager with pool draining for plugins
 * @doc.layer product
 * @doc.pattern Strategy, Observer
 */
public class GracefulReloadManager implements ConfigReloadListener {

    private static final Logger LOG = LoggerFactory.getLogger(GracefulReloadManager.class);

    /**
     * Default drain timeout (30 seconds)
     */
    private static final Duration DEFAULT_DRAIN_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Default time to wait between drain checks (100ms)
     */
    private static final Duration DRAIN_CHECK_INTERVAL = Duration.ofMillis(100);

    private final ConfigRegistry configRegistry;
    private final Duration drainTimeout;

    // Handlers per backend type
    private final Map<String, Function<CompiledPluginConfig, Promise<Void>>> reloadHandlers
            = new ConcurrentHashMap<>();

    // In-flight request tracking per plugin
    private final Map<String, AtomicInteger> inFlightRequests = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> drainingPlugins = new ConcurrentHashMap<>();

    /**
     * Creates a graceful reload manager with default drain timeout.
     *
     * @param configRegistry the config registry to monitor
     */
    public GracefulReloadManager(@NotNull ConfigRegistry configRegistry) {
        this(configRegistry, DEFAULT_DRAIN_TIMEOUT);
    }

    /**
     * Creates a graceful reload manager with custom drain timeout.
     *
     * @param configRegistry the config registry to monitor
     * @param drainTimeout max time to wait for in-flight requests
     */
    public GracefulReloadManager(
            @NotNull ConfigRegistry configRegistry,
            @NotNull Duration drainTimeout) {
        this.configRegistry = Objects.requireNonNull(configRegistry, "configRegistry required");
        this.drainTimeout = Objects.requireNonNull(drainTimeout, "drainTimeout required");

        // Register as reload listener
        configRegistry.registerReloadListener(this);
    }

    /**
     * Registers a reload handler for a specific backend type. Handler is called
     * after drain completes and should initialize new resources.
     *
     * @param backendType the backend type (e.g., "postgresql", "redis")
     * @param handler function that performs the reload for the plugin
     */
    public void registerReloadHandler(
            @NotNull String backendType,
            @NotNull Function<CompiledPluginConfig, Promise<Void>> handler) {

        reloadHandlers.put(backendType.toLowerCase(), handler);
        LOG.info("Registered reload handler for backend type: {}", backendType);
    }

    /**
     * Records the start of a request to a plugin. Must be called before using
     * plugin resources.
     *
     * @param pluginKey tenant:pluginName key
     * @return true if request was accepted, false if plugin is draining
     */
    public boolean acquireRequest(@NotNull String pluginKey) {
        AtomicBoolean draining = drainingPlugins.get(pluginKey);
        if (draining != null && draining.get()) {
            LOG.debug("Rejecting request to draining plugin: {}", pluginKey);
            return false;
        }

        inFlightRequests.computeIfAbsent(pluginKey, k -> new AtomicInteger(0))
                .incrementAndGet();
        return true;
    }

    /**
     * Records the completion of a request to a plugin. Must be called when done
     * using plugin resources.
     *
     * @param pluginKey tenant:pluginName key
     */
    public void releaseRequest(@NotNull String pluginKey) {
        AtomicInteger counter = inFlightRequests.get(pluginKey);
        if (counter != null) {
            int remaining = counter.decrementAndGet();
            if (remaining < 0) {
                counter.set(0); // Guard against mismatched calls
            }
        }
    }

    /**
     * Gets the current in-flight request count for a plugin.
     *
     * @param pluginKey tenant:pluginName key
     * @return number of in-flight requests
     */
    public int getInFlightCount(@NotNull String pluginKey) {
        AtomicInteger counter = inFlightRequests.get(pluginKey);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Checks if a plugin is currently draining.
     *
     * @param pluginKey tenant:pluginName key
     * @return true if draining
     */
    public boolean isDraining(@NotNull String pluginKey) {
        AtomicBoolean draining = drainingPlugins.get(pluginKey);
        return draining != null && draining.get();
    }

    /**
     * Reloads a specific plugin with graceful drain.
     *
     * @param tenantId tenant identifier
     * @param pluginName plugin name
     * @return Promise completing when reload is done
     */
    public Promise<Void> reloadPlugin(@NotNull String tenantId, @NotNull String pluginName) {
        String pluginKey = tenantId + ":" + pluginName;
        LOG.info("Starting graceful reload for plugin: {}", pluginKey);

        // 1. Mark as draining
        drainingPlugins.computeIfAbsent(pluginKey, k -> new AtomicBoolean(false))
                .set(true);

        // 2. Wait for in-flight requests to drain
        return drainInFlightRequests(pluginKey)
                .then(v -> {
                    // 3. Reload configuration from registry
                    return configRegistry.reloadPlugin(tenantId, pluginName);
                })
                .then(v -> {
                    // 4. Get new config and call handler
                    return configRegistry.getPlugin(tenantId, pluginName)
                            .then(optPlugin -> {
                                if (optPlugin.isEmpty()) {
                                    LOG.warn("Plugin not found after reload: {}", pluginKey);
                                    return Promise.complete();
                                }

                                CompiledPluginConfig plugin = optPlugin.get();
                                String backendType = plugin.backend().type().toLowerCase();

                                Function<CompiledPluginConfig, Promise<Void>> handler
                                        = reloadHandlers.get(backendType);

                                if (handler != null) {
                                    LOG.debug("Executing reload handler for {}", pluginKey);
                                    return handler.apply(plugin);
                                }

                                return Promise.complete();
                            });
                })
                .whenComplete(() -> {
                    // 5. Clear draining flag
                    drainingPlugins.computeIfAbsent(pluginKey, k -> new AtomicBoolean(true))
                            .set(false);
                    LOG.info("Completed graceful reload for plugin: {}", pluginKey);
                })
                .whenException(ex -> {
                    // Clear draining flag on error too
                    drainingPlugins.computeIfAbsent(pluginKey, k -> new AtomicBoolean(true))
                            .set(false);
                    LOG.error("Failed graceful reload for plugin: {}", pluginKey, ex);
                });
    }

    private Promise<Void> drainInFlightRequests(String pluginKey) {
        AtomicInteger counter = inFlightRequests.get(pluginKey);
        if (counter == null || counter.get() == 0) {
            LOG.debug("No in-flight requests for {}, proceeding immediately", pluginKey);
            return Promise.complete();
        }

        int initialCount = counter.get();
        LOG.info("Draining {} in-flight requests for {} (timeout={})",
                initialCount, pluginKey, drainTimeout);

        long startTime = System.currentTimeMillis();
        long timeoutMs = drainTimeout.toMillis();

        return drainLoop(pluginKey, counter, startTime, timeoutMs);
    }

    private Promise<Void> drainLoop(String pluginKey, AtomicInteger counter,
            long startTime, long timeoutMs) {
        int remaining = counter.get();

        if (remaining <= 0) {
            LOG.debug("Drain complete for {}", pluginKey);
            return Promise.complete();
        }

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= timeoutMs) {
            LOG.warn("Drain timeout for {} with {} requests still in-flight",
                    pluginKey, remaining);
            // Force proceed - better than hanging indefinitely
            return Promise.complete();
        }

        // Wait and check again
        return Promises.delay(DRAIN_CHECK_INTERVAL)
                .then(v -> drainLoop(pluginKey, counter, startTime, timeoutMs));
    }

    /**
     * ConfigReloadListener callback - called when global reload happens.
     *
     * @param version the new global version
     * @return Promise completing when processing is done
     */
    @Override
    public Promise<Void> onReload(long version) {
        LOG.info("Global config reload notification received (version={})", version);
        // Individual plugin reloads are handled by ConfigRegistry.reloadPlugin
        // This callback is for additional processing after all plugins are reloaded
        return Promise.complete();
    }

    /**
     * Request guard that automatically tracks in-flight requests. Use in
     * try-with-resources pattern.
     */
    public class RequestGuard implements AutoCloseable {

        private final String pluginKey;
        private final boolean acquired;

        public RequestGuard(String tenantId, String pluginName) {
            this.pluginKey = tenantId + ":" + pluginName;
            this.acquired = acquireRequest(pluginKey);
        }

        public boolean isAcquired() {
            return acquired;
        }

        @Override
        public void close() {
            if (acquired) {
                releaseRequest(pluginKey);
            }
        }
    }

    /**
     * Creates a request guard for tracking in-flight requests.
     *
     * @param tenantId tenant identifier
     * @param pluginName plugin name
     * @return request guard (use with try-with-resources)
     */
    public RequestGuard guard(String tenantId, String pluginName) {
        return new RequestGuard(tenantId, pluginName);
    }
}
