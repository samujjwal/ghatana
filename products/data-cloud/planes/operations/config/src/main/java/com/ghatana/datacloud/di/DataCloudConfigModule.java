/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.di;

import com.ghatana.datacloud.config.CollectionConfigCompiler;
import com.ghatana.datacloud.config.ConfigLoader;
import com.ghatana.datacloud.config.ConfigMetrics;
import com.ghatana.datacloud.config.ConfigRegistry;
import com.ghatana.datacloud.config.ConfigReloadManager;
import com.ghatana.datacloud.config.ConfigValidator;
import com.ghatana.datacloud.config.PluginConfigCompiler;
import com.ghatana.datacloud.config.PolicyConfigCompiler;
import com.ghatana.datacloud.config.StorageProfileCompiler;
import com.ghatana.datacloud.config.reload.GracefulReloadManager;
import com.ghatana.platform.observability.MetricsCollector;
import io.activej.eventloop.Eventloop;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.AbstractModule;

import java.util.concurrent.ExecutorService;

/**
 * ActiveJ DI module for data-cloud configuration infrastructure.
 *
 * <p>Provides the full configuration pipeline — loading, validation, compilation,
 * registry, hot-reload, and metrics:
 * <ul>
 *   <li>{@link ConfigLoader} — async YAML config loading with ActiveJ eventloop</li>
 *   <li>{@link ConfigValidator} — schema validation</li>
 *   <li>{@link ConfigRegistry} — compiled config registry with 4 compilers</li>
 *   <li>{@link ConfigReloadManager} — hot-reload with change detection</li>
 *   <li>{@link GracefulReloadManager} — graceful reload with drain timeout</li>
 *   <li>{@link ConfigMetrics} — config operation metrics</li>
 *   <li>4 config compilers: {@link CollectionConfigCompiler}, {@link PluginConfigCompiler},
 *       {@link StorageProfileCompiler}, {@link PolicyConfigCompiler}</li>
 * </ul>
 *
 * <p><b>Dependencies:</b> Requires external bindings for {@link Eventloop},
 * {@link ExecutorService}, and {@link MetricsCollector}.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * Injector injector = Injector.of(
 *     new ObservabilityModule(),      // provides MetricsCollector
 *     new AepCoreModule(),            // provides Eventloop, ExecutorService
 *     new DataCloudConfigModule()
 * );
 * ConfigRegistry registry = injector.getInstance(ConfigRegistry.class);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose ActiveJ DI module for data-cloud configuration infrastructure
 * @doc.layer product
 * @doc.pattern Module
 * @see ConfigRegistry
 * @see ConfigLoader
 * @see ConfigReloadManager
 */
public class DataCloudConfigModule extends AbstractModule {

    // ═══════════════════════════════════════════════════════════════
    //  Config Compilers (stateless, no-arg)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Provides the config validator.
     *
     * <p>Stateless validator that checks raw config against schema rules.
     *
     * @return config validator
     */
    @Provides
    ConfigValidator configValidator() {
        return new ConfigValidator();
    }

    /**
     * Provides the collection config compiler.
     *
     * <p>Compiles raw collection config into optimized lookup structures.
     *
     * @return collection config compiler
     */
    @Provides
    CollectionConfigCompiler collectionConfigCompiler() {
        return new CollectionConfigCompiler();
    }

    /**
     * Provides the plugin config compiler.
     *
     * <p>Compiles raw plugin definitions into resolved plugin configurations.
     *
     * @return plugin config compiler
     */
    @Provides
    PluginConfigCompiler pluginConfigCompiler() {
        return new PluginConfigCompiler();
    }

    /**
     * Provides the storage profile compiler.
     *
     * <p>Compiles raw storage profiles into tiered storage routing rules.
     *
     * @return storage profile compiler
     */
    @Provides
    StorageProfileCompiler storageProfileCompiler() {
        return new StorageProfileCompiler();
    }

    /**
     * Provides the policy config compiler.
     *
     * <p>Compiles raw policy definitions into executable policy rules.
     *
     * @return policy config compiler
     */
    @Provides
    PolicyConfigCompiler policyConfigCompiler() {
        return new PolicyConfigCompiler();
    }

    // ═══════════════════════════════════════════════════════════════
    //  Config Loading & Registry
    // ═══════════════════════════════════════════════════════════════

    /**
     * Provides the config loader.
     *
     * <p>Loads YAML configuration files asynchronously using the ActiveJ
     * eventloop. The default config base path is {@code config/} relative
     * to the working directory.
     *
     * @param eventloop ActiveJ event loop for async I/O
     * @param executor  blocking executor for file I/O operations
     * @return config loader
     */
    @Provides
    ConfigLoader configLoader(Eventloop eventloop, ExecutorService executor) {
        return new ConfigLoader(eventloop, executor);
    }

    /**
     * Provides the config registry — the central configuration hub.
     *
     * <p>Combines the loader, validator, and all 4 compilers into a single
     * registry that loads, validates, compiles, and serves configuration.
     *
     * @param loader             config file loader
     * @param validator          config schema validator
     * @param collectionCompiler collection config compiler
     * @param pluginCompiler     plugin config compiler
     * @param storageCompiler    storage profile compiler
     * @param policyCompiler     policy config compiler
     * @param metrics            metrics collector for operation tracking
     * @return config registry
     */
    @Provides
    ConfigRegistry configRegistry(
            ConfigLoader loader,
            ConfigValidator validator,
            CollectionConfigCompiler collectionCompiler,
            PluginConfigCompiler pluginCompiler,
            StorageProfileCompiler storageCompiler,
            PolicyConfigCompiler policyCompiler,
            MetricsCollector metrics) {
        return new ConfigRegistry(loader, validator, collectionCompiler,
                pluginCompiler, storageCompiler, policyCompiler, metrics);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Hot-Reload & Metrics
    // ═══════════════════════════════════════════════════════════════

    /**
     * Provides the config reload manager.
     *
     * <p>Watches for configuration changes and triggers reload cycles
     * through the registry. Tracks reload metrics via {@link MetricsCollector}.
     *
     * @param registry config registry to reload
     * @param metrics  metrics collector for reload tracking
     * @return config reload manager
     */
    @Provides
    ConfigReloadManager configReloadManager(ConfigRegistry registry, MetricsCollector metrics) {
        return new ConfigReloadManager(registry, metrics);
    }

    /**
     * Provides config operation metrics.
     *
     * <p>Records load times, validation errors, compilation durations,
     * and reload frequencies.
     *
     * @param metrics platform metrics collector
     * @return config metrics
     */
    @Provides
    ConfigMetrics configMetrics(MetricsCollector metrics) {
        return new ConfigMetrics(metrics);
    }

    /**
     * Provides the graceful reload manager.
     *
     * <p>Implements graceful reload with a 30-second drain timeout to
     * allow in-flight requests to complete before configuration switches.
     *
     * @param registry config registry to reload gracefully
     * @return graceful reload manager
     */
    @Provides
    GracefulReloadManager gracefulReloadManager(ConfigRegistry registry) {
        return new GracefulReloadManager(registry);
    }
}
