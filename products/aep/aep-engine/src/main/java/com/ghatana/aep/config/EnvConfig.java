/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.aep.config;

import java.util.Map;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Environment configuration for AEP components.
 */
public class EnvConfig {

    private static final Logger logger = LoggerFactory.getLogger(EnvConfig.class);

    // ── Well-known configuration key constants ──────────────────────────────
    public static final String RABBITMQ_PORT = "rabbitmq.port";
    public static final String REDIS_PORT = "redis.port";
    public static final String AEP_DB_POOL_SIZE = "db.pool.size";
    public static final String AEP_CONSOLIDATION_INTERVAL_HOURS = "consolidation.interval.hours";
    public static final String KAFKA_BOOTSTRAP_SERVERS = "KAFKA_BOOTSTRAP_SERVERS";

    private final Map<String, String> config;
    
    public EnvConfig() {
        this.config = new HashMap<>();
        loadDefaultValues();
    }
    
    public EnvConfig(Map<String, String> initialConfig) {
        this.config = new HashMap<>(initialConfig);
        loadDefaultValues();
    }
    
    private void loadDefaultValues() {
        // Load from system environment
        System.getenv().forEach((key, value) -> {
            if (key.startsWith("AEP_")) {
                String configKey = key.substring(4).toLowerCase().replace('_', '.');
                config.put(configKey, value);
            }
        });
    }
    
    public String get(String key) {
        return config.get(key);
    }
    
    public String get(String key, String defaultValue) {
        return config.getOrDefault(key, defaultValue);
    }
    
    public int getInt(String key, int defaultValue) {
        String value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer config for {}='{}'; using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }
    
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            logger.warn("Invalid boolean config for {}='{}'; using default {}", key, value, defaultValue);
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
    
    public long getLong(String key, long defaultValue) {
        String value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid long config for {}='{}'; using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }
    
    public void set(String key, String value) {
        config.put(key, value);
    }
    
    public boolean contains(String key) {
        return config.containsKey(key);
    }
    
    public Map<String, String> getAll() {
        return new HashMap<>(config);
    }

    /**
     * Creates an EnvConfig instance populated from system environment variables.
     *
     * @return EnvConfig loaded from system environment
     */
    public static EnvConfig fromSystem() {
        return new EnvConfig();
    }

    /**
     * Creates an EnvConfig instance populated from the given map of key/value pairs.
     *
     * <p>The supplied map is copied; mutations after construction have no effect.
     * This factory is intended for testing and programmatic configuration assembly.
     *
     * @param config initial configuration entries (must not be {@code null})
     * @return EnvConfig backed by the given map
     */
    public static EnvConfig fromMap(Map<String, String> config) {
        return new EnvConfig(config);
    }

    /**
     * Gets the Data-Cloud base URL.
     *
     * @return Data-Cloud base URL (default: http://localhost:8085)
     */
    public String aepDcBaseUrl() {
        return get("dc.base.url", System.getenv().getOrDefault("AEP_DC_BASE_URL", "http://localhost:8085"));
    }
    
    @Override
    public String toString() {
        return "EnvConfig{" +
                "config=" + config +
                '}';
    }
}
