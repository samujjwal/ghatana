/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.config.registry;

import com.ghatana.platform.config.YamlConfigSource;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for managing configuration sources.
 * Provides organized access to multiple configuration files with hot-reload support.
 *
 * <p>Usage:
 * <pre>{@code
 * ConfigRegistry registry = new ConfigRegistry(Path.of("config"));
 *
 * // Load configurations
 * registry.loadConfiguration("organization", "organization.yaml");
 * registry.loadConfiguration("department", "departments/engineering.yaml");
 *
 * // Access configurations
 * Optional<YamlConfigSource> orgConfig = registry.getConfiguration("organization");
 *
 * // Reload all
 * registry.reloadAll();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Central registry for managing configuration sources with hot-reload
 * @doc.layer platform
 * @doc.pattern Registry
 */
public class ConfigRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConfigRegistry.class);

    private final Path baseDir;
    private final Map<String, YamlConfigSource> configurations;
    private final Map<String, Path> configPaths;

    /**
     * Creates a new configuration registry with a base directory.
     *
     * @param baseDir base directory for configuration files
     */
    public ConfigRegistry(@NotNull Path baseDir) {
        this.baseDir = baseDir;
        this.configurations = new ConcurrentHashMap<>();
        this.configPaths = new ConcurrentHashMap<>();
    }

    /**
     * Loads a configuration file and registers it with the given name.
     *
     * @param name         unique name for this configuration
     * @param relativePath path relative to base directory
     * @return Promise of the loaded configuration source
     */
    @NotNull
    public Promise<YamlConfigSource> loadConfiguration(@NotNull String name, @NotNull String relativePath) {
        Path configPath = baseDir.resolve(relativePath);

        return YamlConfigSource.create(configPath)
                .whenResult(source -> {
                    configurations.put(name, source);
                    configPaths.put(name, configPath);
                    log.info("Loaded configuration '{}' from: {}", name, configPath);
                })
                .whenException(e -> log.error("Failed to load configuration '{}' from: {}",
                        name, configPath, e));
    }

    /**
     * Loads a configuration from an absolute path.
     *
     * @param name         unique name for this configuration
     * @param absolutePath absolute path to configuration file
     * @return Promise of the loaded configuration source
     */
    @NotNull
    public Promise<YamlConfigSource> loadConfigurationAbsolute(@NotNull String name, @NotNull Path absolutePath) {
        return YamlConfigSource.create(absolutePath)
                .whenResult(source -> {
                    configurations.put(name, source);
                    configPaths.put(name, absolutePath);
                    log.info("Loaded configuration '{}' from: {}", name, absolutePath);
                })
                .whenException(e -> log.error("Failed to load configuration '{}' from: {}",
                        name, absolutePath, e));
    }

    /**
     * Loads multiple configuration files in parallel.
     *
     * @param configs map of names to relative paths
     * @return Promise that completes when all configurations are loaded
     */
    @NotNull
    public Promise<Void> loadConfigurations(@NotNull Map<String, String> configs) {
        List<Promise<YamlConfigSource>> promises = new ArrayList<>();
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            promises.add(loadConfiguration(entry.getKey(), entry.getValue()));
        }
        return Promises.all(promises).toVoid();
    }

    /**
     * Gets a configuration by name.
     *
     * @param name configuration name
     * @return Optional configuration source
     */
    @NotNull
    public Optional<YamlConfigSource> getConfiguration(@NotNull String name) {
        return Optional.ofNullable(configurations.get(name));
    }

    /**
     * Gets a configuration by name, throwing an exception if not found.
     *
     * @param name configuration name
     * @return configuration source
     * @throws ConfigRegistryException if configuration not found
     */
    @NotNull
    public YamlConfigSource getConfigurationOrThrow(@NotNull String name) {
        return getConfiguration(name)
                .orElseThrow(() -> new ConfigRegistryException(
                        "Configuration not found: " + name));
    }

    /**
     * Checks if a configuration is registered.
     *
     * @param name configuration name
     * @return true if registered
     */
    public boolean hasConfiguration(@NotNull String name) {
        return configurations.containsKey(name);
    }

    /**
     * Reloads a specific configuration.
     *
     * @param name configuration name
     * @return Promise that completes when reload is done
     */
    @NotNull
    public Promise<Void> reload(@NotNull String name) {
        YamlConfigSource config = configurations.get(name);
        if (config == null) {
            return Promise.ofException(new ConfigRegistryException(
                    "Configuration not found: " + name));
        }

        return config.reload()
                .whenResult(() -> log.info("Reloaded configuration: {}", name))
                .whenException(e -> log.error("Failed to reload configuration: {}", name, e));
    }

    /**
     * Reloads all registered configurations in parallel.
     *
     * @return Promise that completes when all reloads are done
     */
    @NotNull
    public Promise<Void> reloadAll() {
        List<Promise<Void>> promises = new ArrayList<>();
        for (String name : configurations.keySet()) {
            promises.add(reload(name));
        }
        log.info("Reloading {} configurations", configurations.size());
        return Promises.all(promises).toVoid();
    }

    /**
     * Unregisters a configuration.
     *
     * @param name configuration name
     * @return true if configuration was removed
     */
    public boolean unregister(@NotNull String name) {
        boolean removed = configurations.remove(name) != null;
        configPaths.remove(name);
        if (removed) {
            log.info("Unregistered configuration: {}", name);
        }
        return removed;
    }

    /**
     * Clears all registered configurations.
     */
    public void clear() {
        int count = configurations.size();
        configurations.clear();
        configPaths.clear();
        log.info("Cleared {} configurations", count);
    }

    /**
     * Gets all registered configuration names.
     *
     * @return list of configuration names
     */
    @NotNull
    public List<String> getConfigurationNames() {
        return new ArrayList<>(configurations.keySet());
    }

    /**
     * Gets the number of registered configurations.
     *
     * @return configuration count
     */
    public int size() {
        return configurations.size();
    }

    /**
     * Gets the base directory.
     *
     * @return base directory
     */
    @NotNull
    public Path getBaseDir() {
        return baseDir;
    }

    /**
     * Gets the path for a specific configuration.
     *
     * @param name configuration name
     * @return Optional path to the configuration file
     */
    @NotNull
    public Optional<Path> getConfigPath(@NotNull String name) {
        return Optional.ofNullable(configPaths.get(name));
    }

    /**
     * Gets all configuration paths.
     *
     * @return map of names to paths
     */
    @NotNull
    public Map<String, Path> getAllConfigPaths() {
        return new ConcurrentHashMap<>(configPaths);
    }
}
