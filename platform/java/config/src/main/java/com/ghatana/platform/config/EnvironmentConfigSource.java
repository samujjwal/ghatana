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
 * Configuration source that reads from environment variables.
 *
 * @doc.type class
 * @doc.purpose Configuration source backed by environment variables
 * @doc.layer platform
 * @doc.pattern Service
 */
public class EnvironmentConfigSource implements ConfigSource {
    
    @Override
    @NotNull
    public Optional<String> getString(@NotNull String key) {
        // Convert dot notation to underscore notation
        String envKey = key.replace('.', '_').replace('-', '_').toUpperCase();
        String value = System.getenv(envKey);
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
        return getString(key).map(s -> s.equalsIgnoreCase("true") || s.equals("1"));
    }
    
    @Override
    @NotNull
    public Optional<String[]> getStringArray(@NotNull String key) {
        return getString(key).map(s -> s.split(","));
    }
    
    @Override
    @NotNull
    public Optional<Map<String, String>> getMap(@NotNull String key) {
        // Environment variables don't support nested maps directly
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public <T> Optional<T> getObject(@NotNull String key, @NotNull Class<T> type) {
        // Environment variables don't support object deserialization
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public Optional<ConfigSource> getConfig(@NotNull String key) {
        // Environment variables don't support nested config
        return Optional.empty();
    }
    
    @Override
    @NotNull
    public Map<String, Object> getAll() {
        Map<String, Object> result = new HashMap<>();
        System.getenv().forEach((k, v) -> result.put(k.toLowerCase().replace('_', '.'), v));
        return result;
    }
    
    @Override
    public boolean hasKey(@NotNull String key) {
        String envKey = key.replace('.', '_').replace('-', '_').toUpperCase();
        return System.getenv(envKey) != null;
    }
    
    @Override
    @NotNull
    public String getName() {
        return "environment-variables";
    }
}
