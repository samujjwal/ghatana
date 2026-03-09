/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.config;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration manager that combines multiple configuration sources.
 * 
 * Provides a unified view of configuration properties from multiple sources.
 * Sources are searched in the order they were added.
 *
 * @doc.type class
 * @doc.purpose Central manager for loading and merging configuration sources
 * @doc.layer platform
 * @doc.pattern Service
 */
public class ConfigManager implements ConfigSource {
    
    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);
    
    private final List<ConfigSource> sources;
    private final String name;
    
    /**
     * Creates a new ConfigManager with the specified name.
     *
     * @param name the name of the configuration manager
     */
    public ConfigManager(@NotNull String name) {
        this.name = name;
        this.sources = new ArrayList<>();
    }
    
    /**
     * Creates a new ConfigManager with the specified name and sources.
     *
     * @param name the name of the configuration manager
     * @param sources the configuration sources
     */
    public ConfigManager(@NotNull String name, @NotNull List<ConfigSource> sources) {
        this.name = name;
        this.sources = new ArrayList<>(sources);
    }
    
    /**
     * Adds a configuration source to the manager.
     * Sources are searched in the order they are added.
     *
     * @param source the configuration source
     * @return this configuration manager
     */
    @NotNull
    public ConfigManager addSource(@NotNull ConfigSource source) {
        sources.add(source);
        return this;
    }
    
    /**
     * Adds multiple configuration sources to the manager.
     *
     * @param sources the configuration sources
     * @return this configuration manager
     */
    @NotNull
    public ConfigManager addSources(@NotNull List<ConfigSource> sources) {
        this.sources.addAll(sources);
        return this;
    }
    
    /**
     * Removes a configuration source from the manager.
     *
     * @param source the configuration source
     * @return this configuration manager
     */
    @NotNull
    public ConfigManager removeSource(@NotNull ConfigSource source) {
        sources.remove(source);
        return this;
    }
    
    /**
     * Clears all configuration sources from the manager.
     *
     * @return this configuration manager
     */
    @NotNull
    public ConfigManager clearSources() {
        sources.clear();
        return this;
    }
    
    /**
     * Gets the configuration sources.
     *
     * @return the configuration sources
     */
    @NotNull
    public List<ConfigSource> getSources() {
        return new ArrayList<>(sources);
    }
    
    @Override
    @NotNull
    public Optional<String> getString(@NotNull String key) {
        for (ConfigSource source : sources) {
            Optional<String> value = source.getString(key);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public Optional<Integer> getInt(@NotNull String key) {
        for (ConfigSource source : sources) {
            Optional<Integer> value = source.getInt(key);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public Optional<Long> getLong(@NotNull String key) {
        for (ConfigSource source : sources) {
            Optional<Long> value = source.getLong(key);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public Optional<Double> getDouble(@NotNull String key) {
        for (ConfigSource source : sources) {
            Optional<Double> value = source.getDouble(key);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public Optional<Boolean> getBoolean(@NotNull String key) {
        for (ConfigSource source : sources) {
            Optional<Boolean> value = source.getBoolean(key);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public Optional<String[]> getStringArray(@NotNull String key) {
        for (ConfigSource source : sources) {
            Optional<String[]> value = source.getStringArray(key);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public Optional<Map<String, String>> getMap(@NotNull String key) {
        for (ConfigSource source : sources) {
            Optional<Map<String, String>> value = source.getMap(key);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public <T> Optional<T> getObject(@NotNull String key, @NotNull Class<T> type) {
        for (ConfigSource source : sources) {
            Optional<T> value = source.getObject(key, type);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public Optional<ConfigSource> getConfig(@NotNull String key) {
        for (ConfigSource source : sources) {
            Optional<ConfigSource> value = source.getConfig(key);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public Map<String, Object> getAll() {
        Map<String, Object> result = new HashMap<>();
        // Process sources in reverse order so earlier sources override later ones
        for (int i = sources.size() - 1; i >= 0; i--) {
            ConfigSource source = sources.get(i);
            result.putAll(source.getAll());
        }
        return result;
    }
    
    @Override
    public boolean hasKey(@NotNull String key) {
        for (ConfigSource source : sources) {
            if (source.hasKey(key)) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    @NotNull
    public String getName() {
        return name;
    }
    
    /**
     * Creates a default configuration manager with standard sources.
     * Sources are searched in order: system properties, environment variables, config file (if specified)
     *
     * @param name the name of the configuration manager
     * @param configFilePath the path to the configuration file (optional)
     * @return the configuration manager
     */
    @NotNull
    public static ConfigManager createDefault(@NotNull String name, String configFilePath) {
        ConfigManager manager = new ConfigManager(name);
        
        // Add system properties source
        manager.addSource(new SystemPropertiesConfigSource());
        
        // Add environment variables source
        manager.addSource(new EnvironmentConfigSource());
        
        // Add configuration file source if specified
        if (configFilePath != null && !configFilePath.isEmpty()) {
            try {
                manager.addSource(new FileConfigSource(configFilePath));
            } catch (Exception e) {
                log.warn("Failed to load configuration file: {}", configFilePath, e);
            }
        }
        
        return manager;
    }
    
    /**
     * Creates a default configuration manager with standard sources.
     *
     * @param name the name of the configuration manager
     * @return the configuration manager
     */
    @NotNull
    public static ConfigManager createDefault(@NotNull String name) {
        return createDefault(name, null);
    }
}
