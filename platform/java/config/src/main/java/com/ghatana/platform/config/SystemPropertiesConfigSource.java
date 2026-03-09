/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.config;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration source that reads from system properties.
 *
 * @doc.type class
 * @doc.purpose Configuration source backed by Java system properties
 * @doc.layer platform
 * @doc.pattern Service
 */
public class SystemPropertiesConfigSource implements ConfigSource {
    
    @Override
    @NotNull
    public Optional<String> getString(@NotNull String key) {
        String value = System.getProperty(key);
        return Optional.ofNullable(value);
    }
    
    @Override
    @NotNull
    public Optional<Integer> getInt(@NotNull String key) {
        return getString(key).map(Integer::parseInt);
    }
    
    @Override
    @NotNull
    public Optional<Long> getLong(@NotNull String key) {
        return getString(key).map(Long::parseLong);
    }
    
    @Override
    @NotNull
    public Optional<Double> getDouble(@NotNull String key) {
        return getString(key).map(Double::parseDouble);
    }
    
    @Override
    @NotNull
    public Optional<Boolean> getBoolean(@NotNull String key) {
        return getString(key).map(Boolean::parseBoolean);
    }
    
    @Override
    @NotNull
    public Optional<String[]> getStringArray(@NotNull String key) {
        return getString(key).map(s -> s.split(","));
    }
    
    @Override
    @NotNull
    public Optional<Map<String, String>> getMap(@NotNull String key) {
        // System properties don't support nested maps directly
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public <T> Optional<T> getObject(@NotNull String key, @NotNull Class<T> type) {
        // System properties don't support object deserialization
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public Optional<ConfigSource> getConfig(@NotNull String key) {
        // System properties don't support nested config
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public Map<String, Object> getAll() {
        Map<String, Object> result = new HashMap<>();
        System.getProperties().forEach((k, v) -> result.put(k.toString(), v));
        return result;
    }
    
    @Override
    public boolean hasKey(@NotNull String key) {
        return System.getProperty(key) != null;
    }
    
    @Override
    @NotNull
    public String getName() {
        return "system-properties";
    }
}
