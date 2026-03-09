/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 */
package com.ghatana.config.runtime.engine;

import com.ghatana.platform.config.ConfigSource;
import com.ghatana.platform.config.YamlConfigSource;
import io.activej.promise.Promise;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Unified facade for loading, versioning, interpolating, and watching
 * YAML-based configuration files.
 *
 * <p>Typical usage:
 * <pre>{@code
 * ConfigurationEngine engine = ConfigurationEngine.builder(configDir)
 *         .variable("ENV", "production")
 *         .build();
 * engine.loadDirectory(configDir).whenResult($ -> log.info("loaded"));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Unified configuration lifecycle manager
 * @doc.layer platform
 * @doc.pattern Service
 */
public class ConfigurationEngine {

    private final Path baseDir;
    private final Map<String, String> variables;
    private final Map<String, ConfigSource> registry = new ConcurrentHashMap<>();
    private final ConfigVersionStore versionStore;
    private final List<Consumer<ConfigChange>> listeners = new CopyOnWriteArrayList<>();

    private ConfigurationEngine(Path baseDir, Map<String, String> variables) {
        this.baseDir = Objects.requireNonNull(baseDir, "baseDir");
        this.variables = Map.copyOf(variables);
        this.versionStore = new ConfigVersionStore(baseDir.resolve(".versions"));
    }

    // ─── Builder ────────────────────────────────────────────────────────────

    /**
     * Creates a new builder rooted at the given directory.
     */
    public static Builder builder(Path baseDir) {
        return new Builder(baseDir);
    }

    /**
     * Builder for {@link ConfigurationEngine}.
     */
    public static final class Builder {
        private final Path baseDir;
        private final Map<String, String> variables = new LinkedHashMap<>();

        private Builder(Path baseDir) {
            this.baseDir = baseDir;
        }

        /**
         * Registers an interpolation variable.
         */
        public Builder variable(String key, String value) {
            variables.put(key, value);
            return this;
        }

        public ConfigurationEngine build() {
            return new ConfigurationEngine(baseDir, variables);
        }
    }

    // ─── Loading ────────────────────────────────────────────────────────────

    /**
     * Loads a single YAML file as a named configuration.
     *
     * @param name the logical config name
     * @param path path to the YAML file
     * @param type the configuration type
     * @return a Promise of the loaded {@link ConfigSource}
     */
    public Promise<ConfigSource> loadConfig(String name, Path path, ConfigType type) {
        return YamlConfigSource.create(path)
                .map(source -> {
                    try {
                        String version = versionStore.snapshot(name, path);
                        ConfigSource prev = registry.put(name, source);
                        ConfigChange.ChangeType changeType =
                                prev == null ? ConfigChange.ChangeType.LOADED : ConfigChange.ChangeType.MODIFIED;
                        notifyListeners(new ConfigChange(name, changeType, version));
                    } catch (IOException e) {
                        throw new ConfigurationException("Failed to snapshot config: " + name, e);
                    }
                    return (ConfigSource) source;
                });
    }

    /**
     * Recursively loads all YAML files under a directory.
     */
    public Promise<Void> loadDirectory(Path dir) {
        if (!Files.isDirectory(dir)) {
            return Promise.ofException(
                    new ConfigurationException("Not a directory: " + dir));
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> yamlFiles = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".yaml") || name.endsWith(".yml");
                    })
                    .toList();

            Promise<Void> chain = Promise.complete();
            for (Path file : yamlFiles) {
                String configName = deriveConfigName(dir, file);
                ConfigType configType = deriveConfigType(dir, file);
                chain = chain.then($ -> loadConfig(configName, file, configType).toVoid());
            }
            return chain;
        } catch (IOException e) {
            return Promise.ofException(
                    new ConfigurationException("Failed to walk directory: " + dir, e));
        }
    }

    // ─── Interpolation ─────────────────────────────────────────────────────

    /**
     * Resolves {@code ${VARIABLE}} and {@code ${VARIABLE:default}} placeholders
     * in the given template string.
     */
    public String interpolate(String template) {
        if (template == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            if (template.startsWith("${", i)) {
                int close = template.indexOf('}', i + 2);
                if (close == -1) {
                    sb.append(template, i, template.length());
                    break;
                }
                String expr = template.substring(i + 2, close);
                int colonIdx = expr.indexOf(':');
                String key;
                String defaultVal;
                if (colonIdx >= 0) {
                    key = expr.substring(0, colonIdx);
                    defaultVal = expr.substring(colonIdx + 1);
                } else {
                    key = expr;
                    defaultVal = "${" + key + "}";
                }
                sb.append(variables.getOrDefault(key, defaultVal));
                i = close + 1;
            } else {
                sb.append(template.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    // ─── Versioning ─────────────────────────────────────────────────────────

    /**
     * Returns the current version label for the given config.
     */
    public String getCurrentVersion(String configName) {
        return versionStore.getCurrentVersion(configName);
    }

    /**
     * Returns all version labels for the given config, newest first.
     */
    public List<String> listVersions(String configName) {
        return versionStore.listVersions(configName);
    }

    // ─── Rollback ───────────────────────────────────────────────────────────

    /**
     * Rolls back a configuration to a previous version.
     *
     * @return a Promise completing when the rollback is done
     */
    public Promise<Void> rollback(String configName, String targetVersion) {
        try {
            Optional<Path> restored = versionStore.rollback(configName, targetVersion);
            if (restored.isEmpty()) {
                return Promise.ofException(
                        new ConfigurationException(
                                "Version not found for rollback: " + configName + "@" + targetVersion));
            }
            // Re-load from the restored file to update the registry
            String filename = restored.get().getFileName().toString();
            // Use the restored version file path's parent to get the versioned content
            String restoredVersion = versionStore.getCurrentVersion(configName);
            return YamlConfigSource.create(restored.get())
                    .map(source -> {
                        registry.put(configName, source);
                        notifyListeners(new ConfigChange(configName,
                                ConfigChange.ChangeType.ROLLED_BACK, restoredVersion));
                        return (Void) null;
                    });
        } catch (IOException e) {
            return Promise.ofException(
                    new ConfigurationException("Rollback failed for: " + configName, e));
        }
    }

    // ─── Change Listeners ───────────────────────────────────────────────────

    /**
     * Registers a listener that is called whenever a configuration changes.
     */
    public void onConfigChange(Consumer<ConfigChange> listener) {
        listeners.add(Objects.requireNonNull(listener));
    }

    // ─── Registry Access ────────────────────────────────────────────────────

    /**
     * Returns {@code true} if a config with the given name is loaded.
     */
    public boolean hasConfig(String name) {
        return registry.containsKey(name);
    }

    /**
     * Returns the number of loaded configurations.
     */
    public int size() {
        return registry.size();
    }

    /**
     * Returns the {@link ConfigSource} for the given name, if loaded.
     */
    public Optional<ConfigSource> getConfig(String name) {
        return Optional.ofNullable(registry.get(name));
    }

    /**
     * Returns the names of all loaded configurations.
     */
    public Set<String> listConfigNames() {
        return Collections.unmodifiableSet(registry.keySet());
    }

    // ─── Lifecycle ──────────────────────────────────────────────────────────

    /**
     * Clears all loaded configs and listeners.
     */
    public Promise<Void> stop() {
        registry.clear();
        listeners.clear();
        return Promise.complete();
    }

    // ─── Internal helpers ───────────────────────────────────────────────────

    private void notifyListeners(ConfigChange change) {
        for (Consumer<ConfigChange> listener : listeners) {
            listener.accept(change);
        }
    }

    /**
     * Derives a dotted config name from directory structure.
     * E.g. {@code configDir/agents/fraud-detector.yaml} → {@code agents.fraud-detector}.
     */
    private String deriveConfigName(Path rootDir, Path file) {
        Path relative = rootDir.relativize(file);
        String name = relative.toString();
        // Remove extension
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        // Replace path separators with dots
        name = name.replace('/', '.').replace('\\', '.');
        return name;
    }

    /**
     * Infers a {@link ConfigType} from the first directory element.
     */
    private ConfigType deriveConfigType(Path rootDir, Path file) {
        Path relative = rootDir.relativize(file);
        if (relative.getNameCount() > 1) {
            return ConfigType.fromDirectoryName(relative.getName(0).toString());
        }
        return ConfigType.SYSTEM;
    }
}
