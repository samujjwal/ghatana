/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.config;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * In-memory implementation of {@link ConfigSource} backed by a Map.
 * Used internally by {@link YamlConfigSource} for nested configuration sections,
 * and useful for testing.
 *
 * @doc.type class
 * @doc.purpose In-memory config source for nested sections and testing
 * @doc.layer platform
 * @doc.pattern Adapter
 */
public class MemoryConfigSource implements ConfigSource {

    private final String name;
    private final Map<String, Object> properties;

    /**
     * Creates a new in-memory config source.
     *
     * @param name       source name
     * @param properties initial properties
     */
    public MemoryConfigSource(@NotNull String name, @NotNull Map<String, Object> properties) {
        this.name = name;
        this.properties = new HashMap<>(properties);
    }

    @Override
    @NotNull
    public String getName() {
        return name;
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
            try { return Integer.parseInt(v); } catch (NumberFormatException e) { return null; }
        });
    }

    @Override
    @NotNull
    public Optional<Long> getLong(@NotNull String key) {
        return getString(key).map(v -> {
            try { return Long.parseLong(v); } catch (NumberFormatException e) { return null; }
        });
    }

    @Override
    @NotNull
    public Optional<Double> getDouble(@NotNull String key) {
        return getString(key).map(v -> {
            try { return Double.parseDouble(v); } catch (NumberFormatException e) { return null; }
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
            return Optional.of(list.stream().map(Object::toString).toArray(String[]::new));
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
        Object value = properties.get(key);
        if (value != null && type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    @Override
    @NotNull
    public Optional<ConfigSource> getConfig(@NotNull String key) {
        Map<String, String> nested = getMap(key).orElse(null);
        if (nested == null || nested.isEmpty()) return Optional.empty();
        return Optional.of(new MemoryConfigSource(name + "." + key, new HashMap<>(nested)));
    }

    @Override
    @NotNull
    public Map<String, Object> getAll() {
        return new HashMap<>(properties);
    }

    @Override
    public boolean hasKey(@NotNull String key) {
        return properties.containsKey(key);
    }
}
