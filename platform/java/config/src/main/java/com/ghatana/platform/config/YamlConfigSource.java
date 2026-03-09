/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Configuration source that loads configuration from YAML files.
 * Supports nested properties using dot notation and provides ActiveJ Promise-based async loading.
 *
 * <p>Usage:
 * <pre>{@code
 * YamlConfigSource config = new YamlConfigSource(Path.of("config/app.yaml"));
 * Optional<String> dbUrl = config.getString("database.url");
 *
 * // Async creation
 * Promise<YamlConfigSource> configPromise = YamlConfigSource.create(Path.of("config/app.yaml"));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Load configuration properties from YAML files with async support
 * @doc.layer platform
 * @doc.pattern Adapter
 */
public class YamlConfigSource implements ConfigSource {

    private static final Logger log = LoggerFactory.getLogger(YamlConfigSource.class);
    private static final Executor DEFAULT_EXECUTOR = Executors.newCachedThreadPool();

    private final Path configPath;
    private final Map<String, Object> properties;
    private final ObjectMapper yamlMapper;
    private final Executor executor;

    /**
     * Creates a new YAML configuration source from a file path.
     *
     * @param configPath path to the YAML file
     */
    public YamlConfigSource(@NotNull Path configPath) {
        this(configPath, DEFAULT_EXECUTOR);
    }

    /**
     * Creates a new YAML configuration source with custom executor.
     *
     * @param configPath path to the YAML file
     * @param executor   executor for async operations
     */
    public YamlConfigSource(@NotNull Path configPath, @NotNull Executor executor) {
        this.configPath = configPath;
        this.properties = new ConcurrentHashMap<>();
        this.yamlMapper = createYamlMapper();
        this.executor = executor;
        loadSync();
    }

    /**
     * Creates a YAML configuration source asynchronously.
     *
     * @param configPath path to the YAML file
     * @return Promise of YamlConfigSource
     */
    @NotNull
    public static Promise<YamlConfigSource> create(@NotNull Path configPath) {
        return Promise.ofBlocking(DEFAULT_EXECUTOR, () -> new YamlConfigSource(configPath));
    }

    private ObjectMapper createYamlMapper() {
        return new ObjectMapper(new YAMLFactory())
                .registerModule(new JavaTimeModule())
                .findAndRegisterModules();
    }

    private void loadSync() {
        try {
            if (!Files.exists(configPath)) {
                log.warn("Configuration file not found: {}", configPath);
                return;
            }

            String content = Files.readString(configPath);
            @SuppressWarnings("unchecked")
            Map<String, Object> loadedProps = yamlMapper.readValue(content, Map.class);

            if (loadedProps != null) {
                flattenMap("", loadedProps, properties);
            }

            log.info("Loaded {} properties from {}", properties.size(), configPath);
        } catch (IOException e) {
            log.error("Failed to load YAML configuration from: {}", configPath, e);
        }
    }

    /**
     * Reloads the configuration from the file.
     *
     * @return Promise that completes when reload is done
     */
    @NotNull
    public Promise<Void> reload() {
        return Promise.ofBlocking(executor, () -> {
            properties.clear();
            loadSync();
            return null;
        });
    }

    private void flattenMap(String prefix, Map<String, Object> map, Map<String, Object> result) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                flattenMap(key, nestedMap, result);
            } else if (value instanceof List) {
                result.put(key, value);
                List<?> list = (List<?>) value;
                for (int i = 0; i < list.size(); i++) {
                    result.put(key + "[" + i + "]", list.get(i));
                }
            } else {
                result.put(key, value);
            }
        }
    }

    @Override
    @NotNull
    public Optional<String> getString(@NotNull String key) {
        Object value = properties.get(key);
        return value != null ? Optional.of(value.toString()) : Optional.empty();
    }

    @Override
    @NotNull
    public Optional<Integer> getInt(@NotNull String key) {
        return getString(key).map(v -> {
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                log.warn("Cannot parse '{}' as integer for key: {}", v, key);
                return null;
            }
        });
    }

    @Override
    @NotNull
    public Optional<Long> getLong(@NotNull String key) {
        return getString(key).map(v -> {
            try {
                return Long.parseLong(v);
            } catch (NumberFormatException e) {
                log.warn("Cannot parse '{}' as long for key: {}", v, key);
                return null;
            }
        });
    }

    @Override
    @NotNull
    public Optional<Double> getDouble(@NotNull String key) {
        return getString(key).map(v -> {
            try {
                return Double.parseDouble(v);
            } catch (NumberFormatException e) {
                log.warn("Cannot parse '{}' as double for key: {}", v, key);
                return null;
            }
        });
    }

    @Override
    @NotNull
    public Optional<Boolean> getBoolean(@NotNull String key) {
        return getString(key).map(Boolean::parseBoolean);
    }

    @Override
    @NotNull
    public Optional<String[]> getStringArray(@NotNull String key) {
        Object value = properties.get(key);
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;
            return Optional.of(list.stream()
                    .map(Object::toString)
                    .toArray(String[]::new));
        }
        return Optional.empty();
    }

    @Override
    @NotNull
    public Optional<Map<String, String>> getMap(@NotNull String key) {
        Map<String, String> result = new HashMap<>();
        String prefix = key + ".";

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                String subKey = entry.getKey().substring(prefix.length());
                if (!subKey.contains(".")) {
                    result.put(subKey, entry.getValue().toString());
                }
            }
        }

        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    @Override
    @NotNull
    public <T> Optional<T> getObject(@NotNull String key, @NotNull Class<T> type) {
        try {
            Object value = properties.get(key);
            if (value == null) {
                return Optional.empty();
            }

            if (type.isInstance(value)) {
                return Optional.of(type.cast(value));
            }

            String json = yamlMapper.writeValueAsString(value);
            return Optional.of(yamlMapper.readValue(json, type));
        } catch (Exception e) {
            log.warn("Cannot convert value to type {} for key: {}", type.getSimpleName(), key, e);
            return Optional.empty();
        }
    }

    @Override
    @NotNull
    public Optional<ConfigSource> getConfig(@NotNull String key) {
        Map<String, String> nestedMap = getMap(key).orElse(null);
        if (nestedMap == null || nestedMap.isEmpty()) {
            return Optional.empty();
        }

        Map<String, Object> objectMap = new HashMap<>(nestedMap);
        MemoryConfigSource nested = new MemoryConfigSource(configPath + "." + key, objectMap);
        return Optional.of(nested);
    }

    @Override
    @NotNull
    public Map<String, Object> getAll() {
        return new HashMap<>(properties);
    }

    /**
     * Gets the path to the configuration file.
     *
     * @return the configuration file path
     */
    @NotNull
    public Path getConfigPath() {
        return configPath;
    }

    /**
     * Checks if the configuration file exists.
     *
     * @return true if the file exists
     */
    public boolean exists() {
        return Files.exists(configPath);
    }

    @Override
    @NotNull
    public String getName() {
        return configPath.toString();
    }

    @Override
    public boolean hasKey(@NotNull String key) {
        return properties.containsKey(key);
    }
}
