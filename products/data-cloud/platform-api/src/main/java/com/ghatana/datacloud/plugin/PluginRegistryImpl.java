/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.plugin;

import com.ghatana.platform.observability.MetricsCollector;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of PluginRegistry for managing plugins.
 *
 * @doc.type class
 * @doc.purpose Concrete plugin registry with in-memory storage
 * @doc.layer application
 * @doc.pattern Service Implementation, Registry
 */
public class PluginRegistryImpl implements PluginRegistry {

    private static final Logger log = LoggerFactory.getLogger(PluginRegistryImpl.class);

    private final Map<String, PluginMetadata> plugins = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> configurations = new ConcurrentHashMap<>();
    private final MetricsCollector metrics;

    public PluginRegistryImpl(MetricsCollector metrics) {
        this.metrics = Objects.requireNonNull(metrics, "Metrics required");
    }

    @Override
    public Promise<PluginMetadata> register(PluginMetadata plugin) {
        Objects.requireNonNull(plugin, "Plugin required");
        Objects.requireNonNull(plugin.id(), "Plugin ID required");

        if (plugins.containsKey(plugin.id())) {
            metrics.incrementCounter("plugin.register.conflict", "plugin", plugin.id());
            return Promise.ofException(new IllegalArgumentException("Plugin already exists: " + plugin.id()));
        }

        PluginMetadata stored = new PluginMetadata(
            plugin.id(),
            plugin.name(),
            plugin.description(),
            plugin.version(),
            plugin.tenantId(),
            plugin.type(),
            PluginStatus.REGISTERED,
            plugin.hooks(),
            plugin.dependencies(),
            plugin.manifest(),
            Instant.now(),
            null,
            plugin.registeredBy()
        );

        plugins.put(plugin.id(), stored);
        configurations.put(plugin.id(), new HashMap<>());

        metrics.incrementCounter("plugin.register.success",
            "tenant", plugin.tenantId(), "type", plugin.type().name());
        log.info("Plugin registered: id={}, name={}, tenant={}",
            plugin.id(), plugin.name(), plugin.tenantId());

        return Promise.of(stored);
    }

    @Override
    public Promise<Void> unregister(String pluginId) {
        Objects.requireNonNull(pluginId, "Plugin ID required");

        PluginMetadata removed = plugins.remove(pluginId);
        configurations.remove(pluginId);

        if (removed != null) {
            metrics.incrementCounter("plugin.unregister.success", "plugin", pluginId);
            log.info("Plugin unregistered: id={}", pluginId);
        }

        return Promise.of(null);
    }

    @Override
    public Promise<Optional<PluginMetadata>> getPlugin(String pluginId) {
        Objects.requireNonNull(pluginId, "Plugin ID required");
        return Promise.of(Optional.ofNullable(plugins.get(pluginId)));
    }

    @Override
    public Promise<List<PluginMetadata>> listPlugins(String tenantId, PluginStatus status) {
        Objects.requireNonNull(tenantId, "Tenant ID required");

        List<PluginMetadata> result = plugins.values().stream()
            .filter(p -> p.tenantId().equals(tenantId))
            .filter(p -> status == null || p.status() == status)
            .toList();

        metrics.increment("plugin.list.count", result.size(), Map.of("tenant", tenantId));
        return Promise.of(result);
    }

    @Override
    public Promise<PluginMetadata> activate(String pluginId) {
        Objects.requireNonNull(pluginId, "Plugin ID required");

        PluginMetadata existing = plugins.get(pluginId);
        if (existing == null) {
            metrics.incrementCounter("plugin.activate.error", "plugin", pluginId, "reason", "not_found");
            return Promise.ofException(new IllegalArgumentException("Plugin not found: " + pluginId));
        }

        if (existing.status() == PluginStatus.ACTIVE) {
            return Promise.of(existing);
        }

        PluginMetadata activated = new PluginMetadata(
            existing.id(),
            existing.name(),
            existing.description(),
            existing.version(),
            existing.tenantId(),
            existing.type(),
            PluginStatus.ACTIVE,
            existing.hooks(),
            existing.dependencies(),
            existing.manifest(),
            existing.registeredAt(),
            Instant.now(),
            existing.registeredBy()
        );

        plugins.put(pluginId, activated);
        metrics.incrementCounter("plugin.activate.success", "plugin", pluginId);
        log.info("Plugin activated: id={}", pluginId);

        return Promise.of(activated);
    }

    @Override
    public Promise<PluginMetadata> deactivate(String pluginId) {
        Objects.requireNonNull(pluginId, "Plugin ID required");

        PluginMetadata existing = plugins.get(pluginId);
        if (existing == null) {
            return Promise.ofException(new IllegalArgumentException("Plugin not found: " + pluginId));
        }

        PluginMetadata deactivated = new PluginMetadata(
            existing.id(),
            existing.name(),
            existing.description(),
            existing.version(),
            existing.tenantId(),
            existing.type(),
            PluginStatus.INACTIVE,
            existing.hooks(),
            existing.dependencies(),
            existing.manifest(),
            existing.registeredAt(),
            null,
            existing.registeredBy()
        );

        plugins.put(pluginId, deactivated);
        metrics.incrementCounter("plugin.deactivate.success", "plugin", pluginId);
        log.info("Plugin deactivated: id={}", pluginId);

        return Promise.of(deactivated);
    }

    @Override
    public Promise<Map<String, Object>> getConfiguration(String pluginId) {
        Objects.requireNonNull(pluginId, "Plugin ID required");
        Map<String, Object> config = configurations.getOrDefault(pluginId, Map.of());
        return Promise.of(new HashMap<>(config));
    }

    @Override
    public Promise<Map<String, Object>> updateConfiguration(String pluginId, Map<String, Object> configuration) {
        Objects.requireNonNull(pluginId, "Plugin ID required");
        Objects.requireNonNull(configuration, "Configuration required");

        if (!plugins.containsKey(pluginId)) {
            return Promise.ofException(new IllegalArgumentException("Plugin not found: " + pluginId));
        }

        configurations.put(pluginId, new HashMap<>(configuration));
        metrics.incrementCounter("plugin.config.update", "plugin", pluginId);

        return Promise.of(configuration);
    }

    @Override
    public Promise<HookResult> executeHook(String pluginId, String hookName, Map<String, Object> context) {
        Objects.requireNonNull(pluginId, "Plugin ID required");
        Objects.requireNonNull(hookName, "Hook name required");

        PluginMetadata plugin = plugins.get(pluginId);
        if (plugin == null) {
            return Promise.of(new HookResult(pluginId, hookName, false, null,
                "Plugin not found", 0));
        }

        if (!plugin.hasHook(hookName)) {
            return Promise.of(new HookResult(pluginId, hookName, false, null,
                "Hook not available: " + hookName, 0));
        }

        if (!plugin.isActive()) {
            return Promise.of(new HookResult(pluginId, hookName, false, null,
                "Plugin not active", 0));
        }

        long startTime = System.currentTimeMillis();

        // Simulate hook execution
        try {
            Object result = executeHookLogic(hookName, context);
            long executionTime = System.currentTimeMillis() - startTime;

            metrics.incrementCounter("plugin.hook.success", "plugin", pluginId, "hook", hookName);

            return Promise.of(new HookResult(pluginId, hookName, true, result, null, executionTime));
        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            metrics.incrementCounter("plugin.hook.error", "plugin", pluginId, "hook", hookName);

            return Promise.of(new HookResult(pluginId, hookName, false, null,
                e.getMessage(), executionTime));
        }
    }

    @Override
    public Promise<PluginHealth> getHealth(String pluginId) {
        Objects.requireNonNull(pluginId, "Plugin ID required");

        PluginMetadata plugin = plugins.get(pluginId);
        if (plugin == null) {
            return Promise.of(new PluginHealth(pluginId, false, "NOT_FOUND",
                System.currentTimeMillis(), "Plugin not registered", List.of()));
        }

        boolean healthy = plugin.isActive();
        String status = healthy ? "HEALTHY" : "INACTIVE";
        String message = healthy ? "Plugin is active and operational" : "Plugin is not active";
        List<String> issues = healthy ? List.of() : List.of("Plugin status: " + plugin.status());

        return Promise.of(new PluginHealth(pluginId, healthy, status,
            System.currentTimeMillis(), message, issues));
    }

    private Object executeHookLogic(String hookName, Map<String, Object> context) {
        // Mock implementation - would execute actual plugin logic
        return Map.of("hook", hookName, "processed", true, "contextKeys", context.keySet());
    }
}
