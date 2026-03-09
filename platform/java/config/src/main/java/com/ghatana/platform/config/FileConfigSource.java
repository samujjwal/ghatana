/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration source that reads from a HOCON/JSON configuration file.
 *
 * @doc.type class
 * @doc.purpose Configuration source backed by file system
 * @doc.layer platform
 * @doc.pattern Service
 */
public class FileConfigSource implements ConfigSource {
    
    private final Config config;
    private final String filePath;
    
    /**
     * Creates a new FileConfigSource from the specified file path.
     *
     * @param filePath the path to the configuration file
     */
    public FileConfigSource(@NotNull String filePath) {
        this.filePath = filePath;
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("Configuration file not found: " + filePath);
        }
        this.config = ConfigFactory.parseFile(file);
    }
    
    @Override
    @NotNull
    public Optional<String> getString(@NotNull String key) {
        if (config.hasPath(key)) {
            return Optional.of(config.getString(key));
        }
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public Optional<Integer> getInt(@NotNull String key) {
        if (config.hasPath(key)) {
            return Optional.of(config.getInt(key));
        }
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public Optional<Long> getLong(@NotNull String key) {
        if (config.hasPath(key)) {
            return Optional.of(config.getLong(key));
        }
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public Optional<Double> getDouble(@NotNull String key) {
        if (config.hasPath(key)) {
            return Optional.of(config.getDouble(key));
        }
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public Optional<Boolean> getBoolean(@NotNull String key) {
        if (config.hasPath(key)) {
            return Optional.of(config.getBoolean(key));
        }
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public Optional<String[]> getStringArray(@NotNull String key) {
        if (config.hasPath(key)) {
            List<String> list = config.getStringList(key);
            return Optional.of(list.toArray(new String[0]));
        }
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public Optional<Map<String, String>> getMap(@NotNull String key) {
        if (config.hasPath(key)) {
            Config subConfig = config.getConfig(key);
            Map<String, String> map = new HashMap<>();
            subConfig.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().unwrapped().toString()));
            return Optional.of(map);
        }
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public <T> Optional<T> getObject(@NotNull String key, @NotNull Class<T> type) {
        // Typesafe config doesn't support direct object mapping
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public Optional<ConfigSource> getConfig(@NotNull String key) {
        if (config.hasPath(key)) {
            return Optional.of(new SubConfigSource(config.getConfig(key), key));
        }
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public Map<String, Object> getAll() {
        Map<String, Object> result = new HashMap<>();
        config.entrySet().forEach(e -> result.put(e.getKey(), e.getValue().unwrapped()));
        return result;
    }
    
    @Override
    public boolean hasKey(@NotNull String key) {
        return config.hasPath(key);
    }
    
    @Override
    @NotNull
    public String getName() {
        return "file:" + filePath;
    }
    
    /**
     * Inner class for nested configuration.
     */
    private static class SubConfigSource implements ConfigSource {
        private final Config config;
        private final String prefix;
        
        SubConfigSource(@NotNull Config config, @NotNull String prefix) {
            this.config = config;
            this.prefix = prefix;
        }
        
        @Override
        @NotNull
        public Optional<String> getString(@NotNull String key) {
            if (config.hasPath(key)) {
                return Optional.of(config.getString(key));
            }
            return Optional.empty();
        }
        
        @Override
        @NotNull
        public Optional<Integer> getInt(@NotNull String key) {
            if (config.hasPath(key)) {
                return Optional.of(config.getInt(key));
            }
            return Optional.empty();
        }
        
        @Override
        @NotNull
        public Optional<Long> getLong(@NotNull String key) {
            if (config.hasPath(key)) {
                return Optional.of(config.getLong(key));
            }
            return Optional.empty();
        }
        
        @Override
        @NotNull
        public Optional<Double> getDouble(@NotNull String key) {
            if (config.hasPath(key)) {
                return Optional.of(config.getDouble(key));
            }
            return Optional.empty();
        }
        
        @Override
        @NotNull
        public Optional<Boolean> getBoolean(@NotNull String key) {
            if (config.hasPath(key)) {
                return Optional.of(config.getBoolean(key));
            }
            return Optional.empty();
        }
        
        @Override
        @NotNull
        public Optional<String[]> getStringArray(@NotNull String key) {
            if (config.hasPath(key)) {
                List<String> list = config.getStringList(key);
                return Optional.of(list.toArray(new String[0]));
            }
            return Optional.empty();
        }
        
        @Override
        @NotNull
        public Optional<Map<String, String>> getMap(@NotNull String key) {
            if (config.hasPath(key)) {
                Config subConfig = config.getConfig(key);
                Map<String, String> map = new HashMap<>();
                subConfig.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().unwrapped().toString()));
                return Optional.of(map);
            }
            return Optional.empty();
        }
        
        @Override
        @NotNull
        public <T> Optional<T> getObject(@NotNull String key, @NotNull Class<T> type) {
            return Optional.empty();
        }
        
        @Override
        @NotNull
        public Optional<ConfigSource> getConfig(@NotNull String key) {
            if (config.hasPath(key)) {
                return Optional.of(new SubConfigSource(config.getConfig(key), prefix + "." + key));
            }
            return Optional.empty();
        }
        
        @Override
        @NotNull
        public Map<String, Object> getAll() {
            Map<String, Object> result = new HashMap<>();
            config.entrySet().forEach(e -> result.put(e.getKey(), e.getValue().unwrapped()));
            return result;
        }
        
        @Override
        public boolean hasKey(@NotNull String key) {
            return config.hasPath(key);
        }
        
        @Override
        @NotNull
        public String getName() {
            return "subconfig:" + prefix;
        }
    }
}
