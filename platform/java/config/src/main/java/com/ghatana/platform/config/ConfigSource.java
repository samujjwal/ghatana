/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.platform.config;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

/**
 * Interface for configuration sources.
 * 
 * Configuration sources provide access to configuration properties
 * from various sources (files, environment, system properties, etc.).
 *
 * @doc.type interface
 * @doc.purpose Source of configuration key-value pairs
 * @doc.layer platform
 * @doc.pattern Service
 */
public interface ConfigSource {
    
    /**
     * Gets a string value for the specified key.
     *
     * @param key the configuration key
     * @return the string value, or empty if not found
     */
    @NotNull
    Optional<String> getString(@NotNull String key);
    
    /**
     * Gets an integer value for the specified key.
     *
     * @param key the configuration key
     * @return the integer value, or empty if not found
     */
    @NotNull
    Optional<Integer> getInt(@NotNull String key);
    
    /**
     * Gets a long value for the specified key.
     *
     * @param key the configuration key
     * @return the long value, or empty if not found
     */
    @NotNull
    Optional<Long> getLong(@NotNull String key);
    
    /**
     * Gets a double value for the specified key.
     *
     * @param key the configuration key
     * @return the double value, or empty if not found
     */
    @NotNull
    Optional<Double> getDouble(@NotNull String key);
    
    /**
     * Gets a boolean value for the specified key.
     *
     * @param key the configuration key
     * @return the boolean value, or empty if not found
     */
    @NotNull
    Optional<Boolean> getBoolean(@NotNull String key);
    
    /**
     * Gets a string array value for the specified key.
     *
     * @param key the configuration key
     * @return the string array value, or empty if not found
     */
    @NotNull
    Optional<String[]> getStringArray(@NotNull String key);
    
    /**
     * Gets a map value for the specified key.
     *
     * @param key the configuration key
     * @return the map value, or empty if not found
     */
    @NotNull
    Optional<Map<String, String>> getMap(@NotNull String key);
    
    /**
     * Gets an object value for the specified key.
     *
     * @param <T> the object type
     * @param key the configuration key
     * @param type the object class
     * @return the object value, or empty if not found
     */
    @NotNull
    <T> Optional<T> getObject(@NotNull String key, @NotNull Class<T> type);
    
    /**
     * Gets a nested configuration source for the specified key.
     *
     * @param key the configuration key
     * @return the nested configuration source, or empty if not found
     */
    @NotNull
    Optional<ConfigSource> getConfig(@NotNull String key);
    
    /**
     * Gets all configuration properties as a map.
     *
     * @return a map of all configuration properties
     */
    @NotNull
    Map<String, Object> getAll();
    
    /**
     * Checks if the configuration source contains the specified key.
     *
     * @param key the configuration key
     * @return true if the key exists
     */
    boolean hasKey(@NotNull String key);
    
    /**
     * Gets the name of this configuration source.
     *
     * @return the source name
     */
    @NotNull
    String getName();
}
