/*
 * Copyright (c) 2025 Ghatana Platforms, Inc. All rights reserved.
 */
package com.ghatana.datacloud.config;

import com.ghatana.datacloud.config.ConfigValidator.ValidationResult;
import com.ghatana.datacloud.config.model.CompiledCollectionConfig;
import com.ghatana.datacloud.config.model.CompiledEventCollectionConfig;
import com.ghatana.datacloud.config.model.CompiledPluginConfig;
import com.ghatana.datacloud.config.model.CompiledPolicyConfig;
import com.ghatana.datacloud.config.model.CompiledRoutingConfig;
import com.ghatana.datacloud.config.model.CompiledStorageProfileConfig;
import com.ghatana.datacloud.config.model.ConfigKey;
import com.ghatana.platform.core.exception.ConfigurationException;
import com.ghatana.datacloud.config.model.RawCollectionConfig;
import com.ghatana.datacloud.config.model.RawPluginConfig;
import com.ghatana.datacloud.config.model.RawPolicyConfig;
import com.ghatana.datacloud.config.model.RawRoutingConfig;
import com.ghatana.datacloud.config.model.RawStorageProfileConfig;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Central registry for compiled data-cloud configurations.
 *
 * <p>
 * <b>Purpose</b><br>
 * Provides a thread-safe, high-performance registry for compiled collection
 * configurations. Supports lazy loading, caching, and hot-reload without
 * service restart.
 *
 * <p>
 * <b>Design Principles</b><br>
 * <ul>
 * <li>Configurations are compiled once and cached indefinitely</li>
 * <li>All cached objects are immutable and thread-safe</li>
 * <li>Hot-reload uses atomic swap for zero-downtime updates</li>
 * <li>Lazy loading defers compilation until first access</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * ConfigRegistry registry = new ConfigRegistry(loader, validator, compiler, metrics);
 *
 * // Get compiled collection (lazy loads if not cached)
 * CompiledCollectionConfig config = registry.getCollection("tenant-1", "users");
 *
 * // Hot-reload a specific collection
 * registry.reload("tenant-1", "users");
 *
 * // Reload all configurations
 * registry.reloadAll();
 * }</pre>
 *
 * <p>
 * <b>Thread Safety</b><br>
 * Thread-safe. Uses ConcurrentHashMap for cache and AtomicReference for version
 * tracking.
 *
 * <p>
 * <b>Observability</b><br>
 * Emits metrics via libs:observability MetricsCollector:
 * <ul>
 * <li>config.cache.hit / config.cache.miss - cache access patterns</li>
 * <li>config.load.duration - time to load and compile configs</li>
 * <li>config.reload.duration - time for hot-reload operations</li>
 * <li>config.validation.failure - validation errors during load</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Central configuration registry with caching, hot-reload, and
 * observability
 * @doc.layer core
 * @doc.pattern Registry
 */
public class ConfigRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigRegistry.class);

    private final ConfigLoader loader;
    private final ConfigValidator validator;
    private final CollectionConfigCompiler compiler;
    private final PluginConfigCompiler pluginCompiler;
    private final StorageProfileCompiler storageProfileCompiler;
    private final PolicyConfigCompiler policyCompiler;
    private final RoutingConfigCompiler routingCompiler;
    private final MetricsCollector metrics;

    // Cache of compiled configurations
    private final ConcurrentHashMap<ConfigKey, CompiledCollectionConfig> collectionCache;
    private final ConcurrentHashMap<ConfigKey, CompiledEventCollectionConfig> eventCollectionCache;
    private final ConcurrentHashMap<ConfigKey, CompiledPluginConfig> pluginCache;
    private final ConcurrentHashMap<ConfigKey, CompiledStorageProfileConfig> storageProfileCache;
    private final ConcurrentHashMap<ConfigKey, CompiledPolicyConfig> policyCache;
    private final ConcurrentHashMap<ConfigKey, CompiledRoutingConfig> routingCache;

    // Version tracking for cache invalidation
    private final AtomicLong globalVersion;
    private final ConcurrentHashMap<ConfigKey, AtomicLong> configVersions;

    // Internal metrics (exposed via getMetrics())
    private final AtomicLong cacheHits;
    private final AtomicLong cacheMisses;
    private final AtomicLong reloadCount;

    /**
     * Creates a new ConfigRegistry with observability support.
     *
     * @param loader the config loader (required)
     * @param validator the config validator (required)
     * @param compiler the collection config compiler (required)
     * @param pluginCompiler the plugin config compiler (required)
     * @param storageProfileCompiler the storage profile compiler (required)
     * @param policyCompiler the policy config compiler (required)
     * @param metrics the metrics collector for observability (required)
     */
    public ConfigRegistry(
            @NotNull ConfigLoader loader,
            @NotNull ConfigValidator validator,
            @NotNull CollectionConfigCompiler compiler,
            @NotNull PluginConfigCompiler pluginCompiler,
            @NotNull StorageProfileCompiler storageProfileCompiler,
            @NotNull PolicyConfigCompiler policyCompiler,
            @NotNull MetricsCollector metrics) {
        this.loader = Objects.requireNonNull(loader, "loader required");
        this.validator = Objects.requireNonNull(validator, "validator required");
        this.compiler = Objects.requireNonNull(compiler, "compiler required");
        this.pluginCompiler = Objects.requireNonNull(pluginCompiler, "pluginCompiler required");
        this.storageProfileCompiler = Objects.requireNonNull(storageProfileCompiler, "storageProfileCompiler required");
        this.policyCompiler = Objects.requireNonNull(policyCompiler, "policyCompiler required");
        this.routingCompiler = new RoutingConfigCompiler();
        this.metrics = Objects.requireNonNull(metrics, "metrics required");

        this.collectionCache = new ConcurrentHashMap<>();
        this.eventCollectionCache = new ConcurrentHashMap<>();
        this.pluginCache = new ConcurrentHashMap<>();
        this.storageProfileCache = new ConcurrentHashMap<>();
        this.policyCache = new ConcurrentHashMap<>();
        this.routingCache = new ConcurrentHashMap<>();
        this.globalVersion = new AtomicLong(0);
        this.configVersions = new ConcurrentHashMap<>();

        this.cacheHits = new AtomicLong(0);
        this.cacheMisses = new AtomicLong(0);
        this.reloadCount = new AtomicLong(0);
    }

    /**
     * Creates a new ConfigRegistry without plugin/storage/policy support
     * (backward compatible).
     *
     * @param loader the config loader (required)
     * @param validator the config validator (required)
     * @param compiler the config compiler (required)
     * @param metrics the metrics collector for observability (required)
     */
    public ConfigRegistry(
            @NotNull ConfigLoader loader,
            @NotNull ConfigValidator validator,
            @NotNull CollectionConfigCompiler compiler,
            @NotNull MetricsCollector metrics) {
        this(loader, validator, compiler, new PluginConfigCompiler(), new StorageProfileCompiler(),
                new PolicyConfigCompiler(), metrics);
    }

    /**
     * Creates a new ConfigRegistry with plugin support (backward compatible).
     *
     * @param loader the config loader (required)
     * @param validator the config validator (required)
     * @param compiler the config compiler (required)
     * @param pluginCompiler the plugin config compiler (required)
     * @param metrics the metrics collector for observability (required)
     */
    public ConfigRegistry(
            @NotNull ConfigLoader loader,
            @NotNull ConfigValidator validator,
            @NotNull CollectionConfigCompiler compiler,
            @NotNull PluginConfigCompiler pluginCompiler,
            @NotNull MetricsCollector metrics) {
        this(loader, validator, compiler, pluginCompiler, new StorageProfileCompiler(),
                new PolicyConfigCompiler(), metrics);
    }

    /**
     * Creates a new ConfigRegistry with plugin and storage profile support.
     *
     * @param loader the config loader (required)
     * @param validator the config validator (required)
     * @param compiler the config compiler (required)
     * @param pluginCompiler the plugin config compiler (required)
     * @param storageProfileCompiler the storage profile compiler (required)
     * @param metrics the metrics collector for observability (required)
     */
    public ConfigRegistry(
            @NotNull ConfigLoader loader,
            @NotNull ConfigValidator validator,
            @NotNull CollectionConfigCompiler compiler,
            @NotNull PluginConfigCompiler pluginCompiler,
            @NotNull StorageProfileCompiler storageProfileCompiler,
            @NotNull MetricsCollector metrics) {
        this(loader, validator, compiler, pluginCompiler, storageProfileCompiler,
                new PolicyConfigCompiler(), metrics);
    }

    // ===== Collection Access =====
    /**
     * Gets a compiled collection configuration.
     * <p>
     * Uses lazy loading: if not cached, loads from source, validates, compiles,
     * and caches.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @return Promise of compiled collection config
     * @throws ConfigNotFoundException if collection doesn't exist
     * @throws ConfigurationException if validation fails
     */
    public Promise<CompiledCollectionConfig> getCollectionAsync(
            @NotNull String tenantId,
            @NotNull String collectionName) {

        ConfigKey key = ConfigKey.collection(tenantId, collectionName);

        // Check cache first
        CompiledCollectionConfig cached = collectionCache.get(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            metrics.incrementCounter("config.cache.hit", "tenant", tenantId, "collection", collectionName);
            LOG.trace("Cache hit: {}", key);
            return Promise.of(cached);
        }

        // Cache miss - load, validate, compile
        cacheMisses.incrementAndGet();
        metrics.incrementCounter("config.cache.miss", "tenant", tenantId, "collection", collectionName);
        LOG.debug("Cache miss, loading: {}", key);

        return loadAndCompileCollection(tenantId, collectionName)
                .whenResult(config -> {
                    collectionCache.put(key, config);
                    configVersions.computeIfAbsent(key, k -> new AtomicLong(0))
                            .incrementAndGet();
                    LOG.info("Loaded and cached collection: {}", key);
                });
    }

    /**
     * Gets a compiled event collection configuration.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @return Promise of compiled event collection config
     */
    public Promise<CompiledEventCollectionConfig> getEventCollectionAsync(
            @NotNull String tenantId,
            @NotNull String collectionName) {

        ConfigKey key = ConfigKey.eventCollection(tenantId, collectionName);

        CompiledEventCollectionConfig cached = eventCollectionCache.get(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            metrics.incrementCounter("config.event.cache.hit", "tenant", tenantId, "collection", collectionName);
            return Promise.of(cached);
        }

        cacheMisses.incrementAndGet();
        metrics.incrementCounter("config.event.cache.miss", "tenant", tenantId, "collection", collectionName);
        return loadAndCompileEventCollection(tenantId, collectionName)
                .whenResult(config -> {
                    eventCollectionCache.put(key, config);
                    configVersions.computeIfAbsent(key, k -> new AtomicLong(0))
                            .incrementAndGet();
                });
    }

    /**
     * Synchronous getter for already-cached collections.
     * <p>
     * Returns null if not cached - use for hot-path access only.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @return cached config or null
     */
    @Nullable
    public CompiledCollectionConfig getCollectionIfCached(
            @NotNull String tenantId,
            @NotNull String collectionName) {

        ConfigKey key = ConfigKey.collection(tenantId, collectionName);
        return collectionCache.get(key);
    }

    // ===== Hot Reload =====
    /**
     * Reloads a specific collection configuration.
     * <p>
     * Uses atomic swap: old config remains available until new config is fully
     * loaded and validated.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @return Promise that completes when reload is done
     */
    public Promise<Void> reloadAsync(
            @NotNull String tenantId,
            @NotNull String collectionName) {

        ConfigKey key = ConfigKey.collection(tenantId, collectionName);
        LOG.info("Reloading configuration: {}", key);

        return loadAndCompileCollection(tenantId, collectionName)
                .then(newConfig -> {
                    // Atomic swap
                    collectionCache.put(key, newConfig);
                    configVersions.computeIfAbsent(key, k -> new AtomicLong(0))
                            .incrementAndGet();
                    reloadCount.incrementAndGet();

                    LOG.info("Reloaded configuration: {} (version {})",
                            key, configVersions.get(key).get());
                    return Promise.complete();
                });
    }

    /**
     * Reloads all cached configurations.
     *
     * @return Promise that completes when all reloads are done
     */
    public Promise<Void> reloadAllAsync() {
        long start = System.nanoTime();
        LOG.info("Reloading all configurations ({} collections)",
                collectionCache.size());

        List<Promise<Void>> reloadPromises = collectionCache.keySet().stream()
                .map(key -> reloadAsync(key.tenantId(), key.name()))
                .toList();

        return Promises.all(reloadPromises)
                .then(v -> {
                    globalVersion.incrementAndGet();
                    long durationMs = (System.nanoTime() - start) / 1_000_000;

                    metrics.recordTimer("config.reload.all.duration", durationMs);
                    metrics.incrementCounter("config.reload.all.success",
                            "count", String.valueOf(collectionCache.size()));

                    LOG.info("All configurations reloaded (global version {}, took {}ms)",
                            globalVersion.get(), durationMs);

                    // Notify listeners
                    return notifyReloadListeners();
                });
    }

    private Promise<Void> notifyReloadListeners() {
        if (reloadListeners.isEmpty()) {
            return Promise.complete();
        }

        List<Promise<Void>> listenerPromises = reloadListeners.stream()
                .map(listener -> listener.onReload(globalVersion.get()))
                .toList();

        return Promises.all(listenerPromises);
    }

    /**
     * Invalidates a cached configuration.
     * <p>
     * Next access will trigger a fresh load.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     */
    public void invalidate(@NotNull String tenantId, @NotNull String collectionName) {
        ConfigKey key = ConfigKey.collection(tenantId, collectionName);
        collectionCache.remove(key);
        eventCollectionCache.remove(ConfigKey.eventCollection(tenantId, collectionName));
        LOG.info("Invalidated configuration: {}", key);
    }

    /**
     * Clears all cached configurations.
     */
    public void invalidateAll() {
        collectionCache.clear();
        eventCollectionCache.clear();
        globalVersion.incrementAndGet();
        LOG.info("All configurations invalidated (global version {})",
                globalVersion.get());
    }

    // ===== New Methods for ConfigAwareCollectionService =====
    /**
     * Gets a compiled collection configuration using ConfigKey.
     *
     * @param key the config key (tenant + collection name)
     * @return Promise containing Optional of compiled config
     */
    public Promise<java.util.Optional<CompiledCollectionConfig>> getCollection(@NotNull ConfigKey key) {
        Objects.requireNonNull(key, "ConfigKey must not be null");

        CompiledCollectionConfig cached = collectionCache.get(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return Promise.of(java.util.Optional.of(cached));
        }

        cacheMisses.incrementAndGet();
        return loadAndCompileCollection(key.tenantId(), key.name())
                .then(config -> {
                    collectionCache.put(key, config);
                    configVersions.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
                    return Promise.of(java.util.Optional.of(config));
                })
                .then(
                        opt -> Promise.of(opt),
                        ex -> Promise.of(java.util.Optional.empty())
                );
    }

    /**
     * Gets a compiled event collection configuration using ConfigKey.
     *
     * @param key the config key (tenant + collection name)
     * @return Promise containing Optional of compiled event config
     */
    public Promise<java.util.Optional<CompiledEventCollectionConfig>> getEventCollection(@NotNull ConfigKey key) {
        Objects.requireNonNull(key, "ConfigKey must not be null");

        ConfigKey eventKey = ConfigKey.eventCollection(key.tenantId(), key.name());
        CompiledEventCollectionConfig cached = eventCollectionCache.get(eventKey);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return Promise.of(java.util.Optional.of(cached));
        }

        cacheMisses.incrementAndGet();
        return loadAndCompileEventCollection(key.tenantId(), key.name())
                .then(config -> {
                    eventCollectionCache.put(eventKey, config);
                    configVersions.computeIfAbsent(eventKey, k -> new AtomicLong(0)).incrementAndGet();
                    return Promise.of(java.util.Optional.of(config));
                })
                .then(
                        opt -> Promise.of(opt),
                        ex -> Promise.of(java.util.Optional.empty())
                );
    }

    /**
     * Gets all collection names for a tenant.
     *
     * @param tenantId the tenant identifier
     * @return Promise containing list of collection names
     */
    public Promise<List<String>> getCollectionNames(@NotNull String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        return loader.listCollectionsAsync(tenantId);
    }

    /**
     * Reloads all configurations (alias for reloadAllAsync).
     *
     * @return Promise that completes when reload is done
     */
    public Promise<Void> reloadAll() {
        return reloadAllAsync();
    }

    /**
     * Gets the current global version number.
     *
     * @return the version number
     */
    public long getVersion() {
        return globalVersion.get();
    }

    // ===== Plugin Access =====
    /**
     * Gets a compiled plugin configuration.
     *
     * @param tenantId tenant identifier
     * @param pluginName plugin name
     * @return Promise containing Optional of compiled plugin config
     */
    public Promise<java.util.Optional<CompiledPluginConfig>> getPlugin(
            @NotNull String tenantId,
            @NotNull String pluginName) {

        ConfigKey key = ConfigKey.plugin(tenantId, pluginName);

        CompiledPluginConfig cached = pluginCache.get(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            metrics.incrementCounter("config.plugin.cache.hit", "tenant", tenantId, "plugin", pluginName);
            return Promise.of(java.util.Optional.of(cached));
        }

        cacheMisses.incrementAndGet();
        metrics.incrementCounter("config.plugin.cache.miss", "tenant", tenantId, "plugin", pluginName);

        return loadAndCompilePlugin(tenantId, pluginName)
                .then(config -> {
                    pluginCache.put(key, config);
                    configVersions.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
                    return Promise.of(java.util.Optional.of(config));
                })
                .then(
                        opt -> Promise.of(opt),
                        ex -> Promise.of(java.util.Optional.empty())
                );
    }

    /**
     * Gets a compiled event store plugin configuration.
     *
     * @param tenantId tenant identifier
     * @param pluginName plugin name
     * @return Promise containing Optional of compiled event store plugin config
     */
    public Promise<java.util.Optional<CompiledPluginConfig>> getEventStorePlugin(
            @NotNull String tenantId,
            @NotNull String pluginName) {

        return getPlugin(tenantId, pluginName)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.of(opt);
                    }

                    CompiledPluginConfig plugin = opt.get();
                    if (plugin.type() != CompiledPluginConfig.PluginType.EVENT_STORE
                            && !plugin.isAppendOnly()) {
                        return Promise.of(java.util.Optional.empty());
                    }

                    return Promise.of(opt);
                });
    }

    /**
     * Gets all plugin names for a tenant.
     *
     * @param tenantId tenant identifier
     * @return Promise containing list of plugin names
     */
    public Promise<List<String>> getPluginNames(@NotNull String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return loader.listPluginsAsync(tenantId);
    }

    /**
     * Reloads a specific plugin configuration.
     *
     * @param tenantId tenant identifier
     * @param pluginName plugin name
     * @return Promise completing when reload is done
     */
    public Promise<Void> reloadPlugin(@NotNull String tenantId, @NotNull String pluginName) {
        ConfigKey key = ConfigKey.plugin(tenantId, pluginName);
        LOG.info("Reloading plugin configuration: {}", key);

        return loadAndCompilePlugin(tenantId, pluginName)
                .then(newConfig -> {
                    pluginCache.put(key, newConfig);
                    configVersions.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
                    reloadCount.incrementAndGet();

                    LOG.info("Reloaded plugin configuration: {} (version {})",
                            key, configVersions.get(key).get());
                    return Promise.complete();
                });
    }

    // ===== Storage Profile Access =====
    /**
     * Gets a compiled storage profile configuration.
     *
     * @param tenantId tenant identifier (use "default" for global profiles)
     * @param profileName storage profile name
     * @return Promise containing Optional of compiled storage profile config
     */
    public Promise<java.util.Optional<CompiledStorageProfileConfig>> getStorageProfile(
            @NotNull String tenantId,
            @NotNull String profileName) {

        ConfigKey key = ConfigKey.storageProfile(tenantId, profileName);

        CompiledStorageProfileConfig cached = storageProfileCache.get(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            metrics.incrementCounter("config.storage-profile.cache.hit", "tenant", tenantId, "profile", profileName);
            return Promise.of(java.util.Optional.of(cached));
        }

        cacheMisses.incrementAndGet();
        metrics.incrementCounter("config.storage-profile.cache.miss", "tenant", tenantId, "profile", profileName);

        return loadAndCompileStorageProfile(tenantId, profileName)
                .then(config -> {
                    storageProfileCache.put(key, config);
                    configVersions.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
                    return Promise.of(java.util.Optional.of(config));
                })
                .then(
                        opt -> Promise.of(opt),
                        ex -> Promise.of(java.util.Optional.empty())
                );
    }

    /**
     * Gets a storage profile suitable for event sourcing.
     *
     * @param tenantId tenant identifier
     * @param profileName profile name
     * @return Promise containing Optional of event-sourcing compatible profile
     */
    public Promise<java.util.Optional<CompiledStorageProfileConfig>> getEventSourcingProfile(
            @NotNull String tenantId,
            @NotNull String profileName) {

        return getStorageProfile(tenantId, profileName)
                .then(opt -> {
                    if (opt.isEmpty()) {
                        return Promise.of(opt);
                    }

                    CompiledStorageProfileConfig profile = opt.get();
                    if (!profile.supportsEventSourcing()) {
                        LOG.warn("Storage profile {} does not support event sourcing (not append-only or low durability)",
                                profileName);
                        return Promise.of(java.util.Optional.empty());
                    }

                    return Promise.of(opt);
                });
    }

    /**
     * Gets all storage profile names for a tenant.
     *
     * @param tenantId the tenant identifier
     * @return Promise containing list of profile names
     */
    public Promise<List<String>> getStorageProfileNames(@NotNull String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return loader.listStorageProfilesAsync(tenantId);
    }

    /**
     * Reloads a specific storage profile configuration.
     *
     * @param tenantId tenant identifier
     * @param profileName profile name
     * @return Promise completing when reload is done
     */
    public Promise<Void> reloadStorageProfile(@NotNull String tenantId, @NotNull String profileName) {
        ConfigKey key = ConfigKey.storageProfile(tenantId, profileName);
        LOG.info("Reloading storage profile configuration: {}", key);

        return loadAndCompileStorageProfile(tenantId, profileName)
                .then(newConfig -> {
                    storageProfileCache.put(key, newConfig);
                    configVersions.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
                    reloadCount.incrementAndGet();

                    LOG.info("Reloaded storage profile configuration: {} (version {})",
                            key, configVersions.get(key).get());
                    return Promise.complete();
                });
    }

    private Promise<CompiledStorageProfileConfig> loadAndCompileStorageProfile(String tenantId, String profileName) {
        long start = System.nanoTime();

        return loader.loadStorageProfileAsync(tenantId, profileName)
                .then(rawProfile -> {
                    // Compile to runtime object
                    CompiledStorageProfileConfig compiled = storageProfileCompiler.compile(rawProfile);

                    long durationMs = (System.nanoTime() - start) / 1_000_000;
                    metrics.recordTimer("config.storage-profile.load.duration", durationMs);
                    metrics.incrementCounter("config.storage-profile.load.success",
                            "tenant", tenantId,
                            "profile", profileName);

                    return Promise.of(compiled);
                })
                .whenException(ex -> {
                    metrics.incrementCounter("config.storage-profile.load.error",
                            "tenant", tenantId,
                            "profile", profileName,
                            "error", ex.getClass().getSimpleName());
                });
    }

    // ===== Policy Access =====
    /**
     * Gets a compiled policy configuration.
     *
     * @param tenantId tenant identifier (use "default" for global policies)
     * @param policyName policy name
     * @return Promise containing Optional of compiled policy config
     */
    public Promise<java.util.Optional<CompiledPolicyConfig>> getPolicy(
            @NotNull String tenantId,
            @NotNull String policyName) {

        ConfigKey key = ConfigKey.policy(tenantId, policyName);

        CompiledPolicyConfig cached = policyCache.get(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            metrics.incrementCounter("config.policy.cache.hit", "tenant", tenantId, "policy", policyName);
            return Promise.of(java.util.Optional.of(cached));
        }

        cacheMisses.incrementAndGet();
        metrics.incrementCounter("config.policy.cache.miss", "tenant", tenantId, "policy", policyName);

        return loadAndCompilePolicy(tenantId, policyName)
                .then(config -> {
                    policyCache.put(key, config);
                    configVersions.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
                    return Promise.of(java.util.Optional.of(config));
                })
                .then(
                        opt -> Promise.of(opt),
                        ex -> Promise.of(java.util.Optional.empty())
                );
    }

    /**
     * Gets all policies that apply to a collection.
     *
     * @param tenantId tenant identifier
     * @param collectionName collection name
     * @param collectionLabels collection labels
     * @return Promise containing list of applicable policies (sorted by
     * priority)
     */
    public Promise<List<CompiledPolicyConfig>> getPoliciesForCollection(
            @NotNull String tenantId,
            @NotNull String collectionName,
            @NotNull Map<String, String> collectionLabels) {

        return getPolicyNames(tenantId)
                .then(policyNames -> {
                    List<Promise<java.util.Optional<CompiledPolicyConfig>>> loadPromises
                            = policyNames.stream()
                                    .map(name -> getPolicy(tenantId, name))
                                    .toList();

                    return Promises.toList(loadPromises)
                            .map(results -> results.stream()
                            .filter(java.util.Optional::isPresent)
                            .map(java.util.Optional::get)
                            .filter(policy -> policy.appliesTo(collectionName, collectionLabels))
                            .sorted((p1, p2) -> Integer.compare(p1.priority(), p2.priority()))
                            .toList());
                });
    }

    /**
     * Gets all policy names for a tenant.
     *
     * @param tenantId the tenant identifier
     * @return Promise containing list of policy names
     */
    public Promise<List<String>> getPolicyNames(@NotNull String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        return loader.listPoliciesAsync(tenantId);
    }

    /**
     * Reloads a specific policy configuration.
     *
     * @param tenantId tenant identifier
     * @param policyName policy name
     * @return Promise completing when reload is done
     */
    public Promise<Void> reloadPolicy(@NotNull String tenantId, @NotNull String policyName) {
        ConfigKey key = ConfigKey.policy(tenantId, policyName);
        LOG.info("Reloading policy configuration: {}", key);

        return loadAndCompilePolicy(tenantId, policyName)
                .then(newConfig -> {
                    policyCache.put(key, newConfig);
                    configVersions.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
                    reloadCount.incrementAndGet();

                    LOG.info("Reloaded policy configuration: {} (version {})",
                            key, configVersions.get(key).get());
                    return Promise.complete();
                });
    }

    private Promise<CompiledPolicyConfig> loadAndCompilePolicy(String tenantId, String policyName) {
        long start = System.nanoTime();

        return loader.loadPolicyAsync(tenantId, policyName)
                .then(rawConfig -> {
                    // Compile to runtime object
                    CompiledPolicyConfig compiled = policyCompiler.compile(rawConfig);

                    long durationMs = (System.nanoTime() - start) / 1_000_000;
                    metrics.recordTimer("config.policy.load.duration", durationMs);
                    metrics.incrementCounter("config.policy.load.success",
                            "tenant", tenantId,
                            "policy", policyName);

                    return Promise.of(compiled);
                })
                .whenException(ex -> {
                    metrics.incrementCounter("config.policy.load.error",
                            "tenant", tenantId,
                            "policy", policyName,
                            "error", ex.getClass().getSimpleName());
                });
    }

    private Promise<CompiledPluginConfig> loadAndCompilePlugin(String tenantId, String pluginName) {
        long start = System.nanoTime();

        return loader.loadPluginAsync(tenantId, pluginName)
                .then(rawConfig -> {
                    // Compile to runtime object
                    CompiledPluginConfig compiled = pluginCompiler.compile(rawConfig);

                    long durationMs = (System.nanoTime() - start) / 1_000_000;
                    metrics.recordTimer("config.plugin.load.duration", durationMs);
                    metrics.incrementCounter("config.plugin.load.success",
                            "tenant", tenantId,
                            "plugin", pluginName);

                    return Promise.of(compiled);
                })
                .whenException(ex -> {
                    metrics.incrementCounter("config.plugin.load.error",
                            "tenant", tenantId,
                            "plugin", pluginName,
                            "error", ex.getClass().getSimpleName());
                });
    }

    /**
     * Registers a listener for configuration reload events.
     *
     * @param listener the reload listener
     */
    public void registerReloadListener(@NotNull ConfigReloadListener listener) {
        Objects.requireNonNull(listener, "listener must not be null");
        reloadListeners.add(listener);
    }

    /**
     * Listener interface for configuration reload events.
     */
    @FunctionalInterface
    public interface ConfigReloadListener {

        /**
         * Called when configurations are reloaded.
         *
         * @param version the new global version
         * @return Promise that completes when listener processing is done
         */
        Promise<Void> onReload(long version);
    }

    private final List<ConfigReloadListener> reloadListeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    // ===== Metrics =====
    /**
     * Gets registry metrics.
     *
     * @return registry metrics
     */
    public RegistryMetrics getMetrics() {
        return new RegistryMetrics(
                collectionCache.size(),
                eventCollectionCache.size(),
                cacheHits.get(),
                cacheMisses.get(),
                reloadCount.get(),
                globalVersion.get()
        );
    }

    /**
     * Registry metrics record.
     */
    public record RegistryMetrics(
            long cachedCollections,
            long cachedEventCollections,
            long cacheHits,
            long cacheMisses,
            long reloads,
            long globalVersion
    ) {
        

    

    

    

    public double hitRate() {
        long total = cacheHits + cacheMisses;
        return total == 0 ? 0.0 : (double) cacheHits / total;
    }
}

// ===== Private Helpers =====
private Promise<CompiledCollectionConfig> loadAndCompileCollection(
            String tenantId, 
            String collectionName) {

        long start = System.nanoTime();

        return loader.loadCollectionAsync(tenantId, collectionName)
            .then(rawConfig -> {
                // Validate
                ValidationResult validation = validator.validate(rawConfig);
                if (!validation.isValid()) {
                    metrics.incrementCounter("config.validation.failure",
                            "tenant", tenantId,
                            "collection", collectionName);
                    return Promise.ofException(new ConfigurationException(
                        String.format("Invalid collection config %s/%s: %s",
                            tenantId, collectionName, validation.errors())));
                }

                // Compile to immutable runtime object
                CompiledCollectionConfig compiled = compiler.compile(rawConfig);

                long durationMs = (System.nanoTime() - start) / 1_000_000;
                metrics.recordTimer("config.load.duration", durationMs);
                metrics.incrementCounter("config.load.success",
                        "tenant", tenantId,
                        "collection", collectionName);

                return Promise.of(compiled);
            })
            .whenException(ex -> {
                metrics.incrementCounter("config.load.error",
                        "tenant", tenantId,
                        "collection", collectionName,
                        "error", ex.getClass().getSimpleName());
            });
    }

    private Promise<CompiledEventCollectionConfig> loadAndCompileEventCollection(
            String tenantId, 
            String collectionName) {

        long start = System.nanoTime();

        return loader.loadCollectionAsync(tenantId, collectionName)
            .then(rawConfig -> {
                // Validate as event collection (validate() handles EVENT type internally)
                ValidationResult validation = validator.validate(rawConfig);
                if (!validation.isValid()) {
                    metrics.incrementCounter("config.event.validation.failure",
                            "tenant", tenantId,
                            "collection", collectionName);
                    return Promise.ofException(new ConfigurationException(
                        String.format("Invalid event collection config %s/%s: %s",
                            tenantId, collectionName, validation.errors())));
                }

                // Compile to event collection
                CompiledEventCollectionConfig compiled = compiler.compileEventCollection(rawConfig);

                long durationMs = (System.nanoTime() - start) / 1_000_000;
                metrics.recordTimer("config.event.load.duration", durationMs);
                metrics.incrementCounter("config.event.load.success",
                        "tenant", tenantId,
                        "collection", collectionName);

                return Promise.of(compiled);
            })
            .whenException(ex -> {
                metrics.incrementCounter("config.event.load.error",
                        "tenant", tenantId,
                        "collection", collectionName,
                        "error", ex.getClass().getSimpleName());
            });
    }

    // ===== Routing Configuration =====

    /**
     * Gets routing configuration for a tenant and collection.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @return Promise of compiled routing config
     */
    public Promise<CompiledRoutingConfig> getRoutingConfig(
            @NotNull String tenantId,
            @NotNull String collectionName) {

        ConfigKey key = ConfigKey.routing(tenantId, collectionName);

        CompiledRoutingConfig cached = routingCache.get(key);
        if (cached != null) {
            cacheHits.incrementAndGet();
            metrics.incrementCounter("config.routing.cache.hit", "tenant", tenantId);
            return Promise.of(cached);
        }

        cacheMisses.incrementAndGet();
        metrics.incrementCounter("config.routing.cache.miss", "tenant", tenantId);

        return loadAndCompileRouting(tenantId, collectionName)
                .then(config -> {
                    routingCache.put(key, config);
                    configVersions.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
                    return Promise.of(config);
                });
    }

    /**
     * Reloads routing configuration.
     *
     * @param tenantId the tenant identifier
     * @param collectionName the collection name
     * @return Promise completing when reload is done
     */
    public Promise<Void> reloadRouting(@NotNull String tenantId, @NotNull String collectionName) {
        ConfigKey key = ConfigKey.routing(tenantId, collectionName);
        LOG.info("Reloading routing configuration: {}", key);

        return loadAndCompileRouting(tenantId, collectionName)
                .then(newConfig -> {
                    routingCache.put(key, newConfig);
                    configVersions.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
                    reloadCount.incrementAndGet();

                    LOG.info("Reloaded routing configuration: {} (version {})",
                            key, configVersions.get(key).get());
                    return Promise.complete();
                });
    }

    private Promise<CompiledRoutingConfig> loadAndCompileRouting(String tenantId, String collectionName) {
        long start = System.nanoTime();

        return loader.loadRoutingAsync(tenantId, collectionName)
                .then(rawConfig -> {
                    CompiledRoutingConfig compiled = routingCompiler.compile(rawConfig);

                    long durationMs = (System.nanoTime() - start) / 1_000_000;
                    metrics.recordTimer("config.routing.load.duration", durationMs);
                    metrics.incrementCounter("config.routing.load.success",
                            "tenant", tenantId,
                            "collection", collectionName);

                    return Promise.of(compiled);
                })
                .whenException(ex -> {
                    metrics.incrementCounter("config.routing.load.error",
                            "tenant", tenantId,
                            "collection", collectionName,
                            "error", ex.getClass().getSimpleName());
                });
    }
}
