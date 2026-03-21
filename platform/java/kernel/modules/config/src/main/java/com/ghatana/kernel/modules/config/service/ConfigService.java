/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.config.service;

import com.ghatana.kernel.context.KernelContext;
import com.ghatana.platform.config.ConfigManager;
import com.ghatana.platform.config.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/**
 * Generic configuration service.
 *
 * <p>Wraps the platform config manager and provides kernel-specific
 * functionality including tenant isolation and hierarchical resolution.
 * This service contains NO finance-specific logic.</p>
 *
 * @doc.type class
 * @doc.purpose Generic configuration service - tenant isolation, hierarchical resolution
 * @doc.layer kernel
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);

    private final KernelContext context;
    private final ConfigManager platformConfigManager;
    private final Map<String, ConfigManager> tenantConfigManagers;
    private final Map<String, CopyOnWriteArrayList<ConfigSource>> tenantConfigSources;
    private final Executor executor;
    private volatile boolean started = false;

    /**
     * Creates a new configuration service.
     *
     * @param context the kernel context
     * @param platformConfigManager the platform config manager
     */
    public ConfigService(KernelContext context, ConfigManager platformConfigManager) {
        this.context = context;
        this.platformConfigManager = platformConfigManager;
        this.tenantConfigManagers = new ConcurrentHashMap<>();
        this.tenantConfigSources = new ConcurrentHashMap<>();
        this.executor = context.getExecutor("config");
    }

    /**
     * Starts the configuration service.
     */
    public void start() {
        log.info("Starting configuration service");
        started = true;
        log.info("Configuration service started");
    }

    /**
     * Stops the configuration service.
     */
    public void stop() {
        log.info("Stopping configuration service");
        tenantConfigManagers.clear();
        tenantConfigSources.clear();
        started = false;
        log.info("Configuration service stopped");
    }

    /**
     * Checks if the service is healthy.
     *
     * @return true if healthy
     */
    public boolean isHealthy() {
        return started;
    }

    /**
     * Gets a string configuration value.
     *
     * @param key the configuration key
     * @return optional containing the value
     */
    public Optional<String> getString(String key) {
        if (key == null) {
            throw new NullPointerException("key cannot be null");
        }
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return platformConfigManager.getString(key);
    }

    /**
     * Gets a string configuration value with default.
     *
     * @param key the configuration key
     * @param defaultValue the default value
     * @return the configuration value or default
     */
    public String getString(String key, String defaultValue) {
        return getString(key).orElse(defaultValue);
    }

    /**
     * Gets an integer configuration value.
     *
     * @param key the configuration key
     * @return optional containing the value
     */
    public Optional<Integer> getInt(String key) {
        if (key == null) {
            throw new NullPointerException("key cannot be null");
        }
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return platformConfigManager.getInt(key);
    }

    /**
     * Gets an integer configuration value with default.
     *
     * @param key the configuration key
     * @param defaultValue the default value
     * @return the configuration value or default
     */
    public int getInt(String key, int defaultValue) {
        return getInt(key).orElse(defaultValue);
    }

    /**
     * Gets a long configuration value.
     *
     * @param key the configuration key
     * @return optional containing the value
     */
    public Optional<Long> getLong(String key) {
        if (key == null) {
            throw new NullPointerException("key cannot be null");
        }
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return platformConfigManager.getLong(key);
    }

    /**
     * Gets a long configuration value with default.
     *
     * @param key the configuration key
     * @param defaultValue the default value
     * @return the configuration value or default
     */
    public long getLong(String key, long defaultValue) {
        return getLong(key).orElse(defaultValue);
    }

    /**
     * Gets a double configuration value.
     *
     * @param key the configuration key
     * @return optional containing the value
     */
    public Optional<Double> getDouble(String key) {
        if (key == null) {
            throw new NullPointerException("key cannot be null");
        }
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return platformConfigManager.getDouble(key);
    }

    /**
     * Gets a double configuration value with default.
     *
     * @param key the configuration key
     * @param defaultValue the default value
     * @return the configuration value or default
     */
    public double getDouble(String key, double defaultValue) {
        return getDouble(key).orElse(defaultValue);
    }

    /**
     * Gets a boolean configuration value.
     *
     * @param key the configuration key
     * @return optional containing the value
     */
    public Optional<Boolean> getBoolean(String key) {
        if (key == null) {
            throw new NullPointerException("key cannot be null");
        }
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return platformConfigManager.getBoolean(key);
    }

    /**
     * Gets a boolean configuration value with default.
     *
     * @param key the configuration key
     * @param defaultValue the default value
     * @return the configuration value or default
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        return getBoolean(key).orElse(defaultValue);
    }

    /**
     * Gets a string array configuration value.
     *
     * @param key the configuration key
     * @return optional containing the value
     */
    public Optional<String[]> getStringArray(String key) {
        return platformConfigManager.getStringArray(key);
    }

    /**
     * Gets a map configuration value.
     *
     * @param key the configuration key
     * @return optional containing the value
     */
    public Optional<Map<String, String>> getMap(String key) {
        return platformConfigManager.getMap(key);
    }

    /**
     * Gets an object configuration value.
     *
     * @param key the configuration key
     * @param type the expected type
     * @param <T> the type parameter
     * @return optional containing the value
     */
    public <T> Optional<T> getObject(String key, Class<T> type) {
        return platformConfigManager.getObject(key, type);
    }

    /**
     * Gets all configuration values.
     *
     * @return map of all configuration values
     */
    public Map<String, Object> getAll() {
        return platformConfigManager.getAll();
    }

    /**
     * Checks if a configuration key exists.
     *
     * @param key the configuration key
     * @return true if the key exists
     */
    public boolean hasKey(String key) {
        return platformConfigManager.hasKey(key);
    }

    /**
     * Gets a tenant-specific configuration value.
     *
     * @param tenantId the tenant identifier
     * @param key the configuration key
     * @return optional containing the value
     */
    public Optional<String> getTenantString(String tenantId, String key) {
        return getTenantConfigManager(tenantId).getString(key);
    }

    /**
     * Gets a tenant-specific configuration value with default.
     *
     * @param tenantId the tenant identifier
     * @param key the configuration key
     * @param defaultValue the default value
     * @return the configuration value or default
     */
    public String getTenantString(String tenantId, String key, String defaultValue) {
        return getTenantString(tenantId, key).orElse(defaultValue);
    }

    /**
     * Gets a tenant-specific integer configuration value.
     *
     * @param tenantId the tenant identifier
     * @param key the configuration key
     * @return optional containing the value
     */
    public Optional<Integer> getTenantInt(String tenantId, String key) {
        return getTenantConfigManager(tenantId).getInt(key);
    }

    /**
     * Gets a tenant-specific integer configuration value with default.
     *
     * @param tenantId the tenant identifier
     * @param key the configuration key
     * @param defaultValue the default value
     * @return the configuration value or default
     */
    public int getTenantInt(String tenantId, String key, int defaultValue) {
        return getTenantInt(tenantId, key).orElse(defaultValue);
    }

    /**
     * Gets a tenant-specific boolean configuration value.
     *
     * @param tenantId the tenant identifier
     * @param key the configuration key
     * @return optional containing the value
     */
    public Optional<Boolean> getTenantBoolean(String tenantId, String key) {
        return getTenantConfigManager(tenantId).getBoolean(key);
    }

    /**
     * Gets a tenant-specific boolean configuration value with default.
     *
     * @param tenantId the tenant identifier
     * @param key the configuration key
     * @param defaultValue the default value
     * @return the configuration value or default
     */
    public boolean getTenantBoolean(String tenantId, String key, boolean defaultValue) {
        return getTenantBoolean(tenantId, key).orElse(defaultValue);
    }

    /**
     * Adds a configuration source for a tenant.
     *
     * @param tenantId the tenant identifier
     * @param source the configuration source
     */
    public void addTenantConfigSource(String tenantId, ConfigSource source) {
        tenantConfigSources
            .computeIfAbsent(tenantId, ignored -> new CopyOnWriteArrayList<>())
            .add(source);
        tenantConfigManagers.put(tenantId, createTenantConfigManager(tenantId));
        log.debug("Added config source for tenant: {}", tenantId);
    }

    /**
     * Gets the platform config manager.
     *
     * @return the platform config manager
     */
    public ConfigManager getPlatformConfigManager() {
        return platformConfigManager;
    }

    // ==================== Private Methods ====================

    private ConfigManager getTenantConfigManager(String tenantId) {
        return tenantConfigManagers.computeIfAbsent(tenantId, this::createTenantConfigManager);
    }

    private ConfigManager createTenantConfigManager(String tenantId) {
        ConfigManager tenantConfig = new ConfigManager("tenant-" + tenantId);
        tenantConfigSources.getOrDefault(tenantId, new CopyOnWriteArrayList<>())
            .forEach(tenantConfig::addSource);
        platformConfigManager.getSources().forEach(tenantConfig::addSource);
        return tenantConfig;
    }
}
