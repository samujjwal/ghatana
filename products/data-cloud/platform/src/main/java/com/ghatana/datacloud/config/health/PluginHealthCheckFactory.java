/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
 */
package com.ghatana.datacloud.config.health;

import com.ghatana.datacloud.config.ConfigRegistry;
import com.ghatana.datacloud.config.model.CompiledPluginConfig;
import com.ghatana.platform.observability.health.HealthCheck;
import com.ghatana.platform.observability.health.HealthCheck.HealthCheckResult;
import com.ghatana.platform.observability.health.HealthCheckRegistry;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Factory for creating and registering health checks from plugin
 * configurations. Automatically creates appropriate health checks based on
 * plugin backend type.
 *
 * <p>
 * <b>Purpose</b><br>
 * Centralizes health check creation from plugin configs. Supports custom check
 * implementations per backend type (PostgreSQL, Redis, Kafka, S3, etc.).
 *
 * <p>
 * <b>Usage Example</b><br>
 * <pre>{@code
 * PluginHealthCheckFactory factory = new PluginHealthCheckFactory(
 *     configRegistry,
 *     healthCheckRegistry
 * );
 *
 * // Register custom check implementation for PostgreSQL
 * factory.registerCheckImplementation("postgresql", plugin ->
 *     () -> checkPostgresConnection(plugin)
 * );
 *
 * // Create and register health checks for all plugins
 * factory.createAndRegisterAll("tenant-1")
 *     .whenResult(count -> log.info("Registered {} health checks", count));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Factory for creating health checks from plugin configuration
 * @doc.layer product
 * @doc.pattern Factory, Strategy
 */
public class PluginHealthCheckFactory {

    private static final Logger LOG = LoggerFactory.getLogger(PluginHealthCheckFactory.class);

    private final ConfigRegistry configRegistry;
    private final HealthCheckRegistry healthCheckRegistry;

    // Custom check implementations per backend type
    private final Map<String, Function<CompiledPluginConfig, Supplier<Promise<HealthCheckResult>>>> checkImplementations = new ConcurrentHashMap<>();

    // Registered health checks (for cleanup)
    private final Map<String, ConfigDrivenHealthCheck> activeChecks = new ConcurrentHashMap<>();

    /**
     * Creates a new PluginHealthCheckFactory.
     *
     * @param configRegistry the config registry for loading plugins
     * @param healthCheckRegistry the health check registry for registration
     */
    public PluginHealthCheckFactory(
            @NotNull ConfigRegistry configRegistry,
            @NotNull HealthCheckRegistry healthCheckRegistry) {
        this.configRegistry = Objects.requireNonNull(configRegistry, "configRegistry required");
        this.healthCheckRegistry = Objects.requireNonNull(healthCheckRegistry, "healthCheckRegistry required");

        // Register default check implementations
        registerDefaultImplementations();
    }

    /**
     * Registers a custom check implementation for a backend type.
     *
     * @param backendType the backend type (e.g., "postgresql", "redis")
     * @param checkFactory factory that creates the check function from plugin
     * config
     */
    public void registerCheckImplementation(
            @NotNull String backendType,
            @NotNull Function<CompiledPluginConfig, Supplier<Promise<HealthCheckResult>>> checkFactory) {

        checkImplementations.put(backendType.toLowerCase(), checkFactory);
        LOG.info("Registered health check implementation for backend: {}", backendType);
    }

    /**
     * Creates and registers a health check for a specific plugin.
     *
     * @param tenantId tenant identifier
     * @param pluginName plugin name
     * @return Promise containing the created health check, or empty if plugin
     * not found
     */
    public Promise<java.util.Optional<ConfigDrivenHealthCheck>> createAndRegister(
            @NotNull String tenantId,
            @NotNull String pluginName) {

        return configRegistry.getPlugin(tenantId, pluginName)
                .then(optPlugin -> {
                    if (optPlugin.isEmpty()) {
                        LOG.warn("Plugin not found: {}/{}", tenantId, pluginName);
                        return Promise.of(java.util.Optional.<ConfigDrivenHealthCheck>empty());
                    }

                    CompiledPluginConfig plugin = optPlugin.get();

                    if (plugin.healthCheck() == null || !plugin.healthCheck().enabled()) {
                        LOG.debug("Health check disabled for plugin: {}", pluginName);
                        return Promise.of(java.util.Optional.<ConfigDrivenHealthCheck>empty());
                    }

                    ConfigDrivenHealthCheck healthCheck = createHealthCheck(plugin);

                    // Register with health check registry
                    healthCheckRegistry.register(healthCheck);

                    // Track for cleanup
                    String key = tenantId + ":" + pluginName;
                    activeChecks.put(key, healthCheck);

                    LOG.info("Registered health check for plugin: {} (interval={}, timeout={})",
                            pluginName,
                            healthCheck.getInterval(),
                            healthCheck.getTimeout());

                    return Promise.of(java.util.Optional.of(healthCheck));
                });
    }

    /**
     * Creates and registers health checks for all plugins of a tenant.
     *
     * @param tenantId tenant identifier
     * @return Promise containing count of registered health checks
     */
    public Promise<Integer> createAndRegisterAll(@NotNull String tenantId) {
        return configRegistry.getPluginNames(tenantId)
                .then(pluginNames -> {
                    LOG.info("Creating health checks for {} plugins (tenant={})",
                            pluginNames.size(), tenantId);

                    java.util.List<Promise<java.util.Optional<ConfigDrivenHealthCheck>>> promises
                            = pluginNames.stream()
                                    .map(name -> createAndRegister(tenantId, name))
                                    .toList();

                    return Promises.toList(promises)
                            .map(results -> {
                                int count = (int) results.stream()
                                        .filter(java.util.Optional::isPresent)
                                        .count();
                                LOG.info("Registered {} health checks for tenant {}", count, tenantId);
                                return count;
                            });
                });
    }

    /**
     * Unregisters health check for a specific plugin.
     *
     * @param tenantId tenant identifier
     * @param pluginName plugin name
     */
    public void unregister(@NotNull String tenantId, @NotNull String pluginName) {
        String key = tenantId + ":" + pluginName;
        ConfigDrivenHealthCheck check = activeChecks.remove(key);

        if (check != null) {
            healthCheckRegistry.unregister(check.getName());
            LOG.info("Unregistered health check for plugin: {}", pluginName);
        }
    }

    /**
     * Unregisters all health checks for a tenant.
     *
     * @param tenantId tenant identifier
     */
    public void unregisterAll(@NotNull String tenantId) {
        String prefix = tenantId + ":";

        activeChecks.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .toList()
                .forEach(key -> {
                    ConfigDrivenHealthCheck check = activeChecks.remove(key);
                    if (check != null) {
                        healthCheckRegistry.unregister(check.getName());
                    }
                });

        LOG.info("Unregistered all health checks for tenant: {}", tenantId);
    }

    /**
     * Gets an active health check by plugin name.
     *
     * @param tenantId tenant identifier
     * @param pluginName plugin name
     * @return the health check, or null if not registered
     */
    public ConfigDrivenHealthCheck getHealthCheck(String tenantId, String pluginName) {
        return activeChecks.get(tenantId + ":" + pluginName);
    }

    private ConfigDrivenHealthCheck createHealthCheck(CompiledPluginConfig plugin) {
        String backendType = plugin.backend().type().toLowerCase();

        Function<CompiledPluginConfig, Supplier<Promise<HealthCheckResult>>> factory
                = checkImplementations.get(backendType);

        Supplier<Promise<HealthCheckResult>> checkFunction;
        if (factory != null) {
            checkFunction = factory.apply(plugin);
        } else {
            // Default: simple ping check
            checkFunction = () -> createDefaultCheck(plugin);
        }

        return ConfigDrivenHealthCheck.builder()
                .pluginConfig(plugin)
                .checkFunction(checkFunction)
                .name(plugin.name() + "-health")
                .critical(false) // Plugins are readiness checks, not liveness
                .build();
    }

    private Promise<HealthCheckResult> createDefaultCheck(CompiledPluginConfig plugin) {
        // Default implementation - just reports healthy if plugin is loaded
        // Real implementations should check actual connectivity
        return Promise.of(HealthCheckResult.healthy(
                "Plugin loaded",
                Map.of(
                        "plugin", plugin.name(),
                        "type", plugin.type().name(),
                        "backend", plugin.backend().type()
                ),
                java.time.Duration.ZERO));
    }

    private void registerDefaultImplementations() {
        // PostgreSQL - check database connectivity
        registerCheckImplementation("postgresql", plugin -> () -> Promise.of(
                HealthCheckResult.healthy("PostgreSQL check placeholder",
                        Map.of("connectionUrl", plugin.backend() != null ? plugin.backend().connectionUrl() : "unknown"),
                        java.time.Duration.ZERO)
        ));

        // Redis - check cache connectivity
        registerCheckImplementation("redis", plugin -> () -> Promise.of(
                HealthCheckResult.healthy("Redis check placeholder",
                        Map.of("connectionUrl", plugin.backend() != null ? plugin.backend().connectionUrl() : "unknown"),
                        java.time.Duration.ZERO)
        ));

        // Kafka - check broker connectivity
        registerCheckImplementation("kafka", plugin -> () -> Promise.of(
                HealthCheckResult.healthy("Kafka check placeholder",
                        Map.of("bootstrap", plugin.backend() != null ? plugin.backend().connectionUrl() : "unknown"),
                        java.time.Duration.ZERO)
        ));

        // S3 - check bucket accessibility
        registerCheckImplementation("s3", plugin -> () -> Promise.of(
                HealthCheckResult.healthy("S3 check placeholder",
                        Map.of("endpoint", plugin.backend() != null ? plugin.backend().connectionUrl() : "aws"),
                        java.time.Duration.ZERO)
        ));

        // ClickHouse - check database connectivity
        registerCheckImplementation("clickhouse", plugin -> () -> Promise.of(
                HealthCheckResult.healthy("ClickHouse check placeholder",
                        Map.of("connectionUrl", plugin.backend() != null ? plugin.backend().connectionUrl() : "unknown"),
                        java.time.Duration.ZERO)
        ));

        LOG.info("Registered {} default health check implementations", checkImplementations.size());
    }
}
