/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
 */
package com.ghatana.datacloud.config;

import com.ghatana.datacloud.config.model.CompiledStorageProfileConfig;
import com.ghatana.datacloud.config.model.CompiledStorageProfileConfig.*;
import com.ghatana.platform.core.exception.ConfigurationException;
import com.ghatana.datacloud.config.model.RawStorageProfileConfig;
import com.ghatana.datacloud.config.model.RawStorageProfileConfig.*;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles raw storage profile YAML configuration into validated, immutable
 * runtime objects. Handles tier classification, latency parsing, and default
 * value resolution.
 *
 * @doc.type class
 * @doc.purpose Compile raw storage profile config to immutable compiled config
 * @doc.layer product
 * @doc.pattern Compiler
 */
public class StorageProfileCompiler {

    private static final Pattern SIZE_PATTERN = Pattern.compile("^(\\d+)(B|KB|MB|GB|TB)$", Pattern.CASE_INSENSITIVE);

    /**
     * Compile a list of raw storage profiles into compiled configurations.
     *
     * @param rawConfig the raw storage profile list config
     * @return list of compiled storage profiles
     * @throws ConfigurationException if compilation fails
     */
    public List<CompiledStorageProfileConfig> compileAll(RawStorageProfileConfig rawConfig) {
        if (rawConfig == null || rawConfig.profiles() == null) {
            return List.of();
        }

        List<CompiledStorageProfileConfig> compiled = new ArrayList<>();
        for (RawStorageProfile profile : rawConfig.profiles()) {
            compiled.add(compile(profile));
        }
        return List.copyOf(compiled);
    }

    /**
     * Compile a single raw storage profile into a compiled configuration.
     *
     * @param raw the raw storage profile
     * @return compiled storage profile
     * @throws ConfigurationException if compilation fails
     */
    public CompiledStorageProfileConfig compile(RawStorageProfile raw) {
        Objects.requireNonNull(raw, "Raw storage profile cannot be null");
        Objects.requireNonNull(raw.name(), "Profile name cannot be null");

        return new CompiledStorageProfileConfig(
                raw.name(),
                raw.displayName() != null ? raw.displayName() : raw.name(),
                compileTier(raw.tier()),
                compileBackend(raw.backend()),
                compileCharacteristics(raw.characteristics()),
                compileDefaults(raw.settings(), raw.defaults()),
                raw.settings() != null ? Map.copyOf(raw.settings()) : Map.of()
        );
    }

    /**
     * Compile storage tier from string.
     *
     * @param tier tier string
     * @return compiled storage tier
     */
    StorageTier compileTier(String tier) {
        if (tier == null || tier.isBlank()) {
            return StorageTier.WARM; // Default
        }

        try {
            return StorageTier.valueOf(tier.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Invalid storage tier: " + tier
                    + ". Valid values: " + Arrays.toString(StorageTier.values()));
        }
    }

    /**
     * Compile backend reference.
     *
     * @param raw raw backend config
     * @return compiled backend config
     */
    CompiledProfileBackend compileBackend(RawProfileBackend raw) {
        if (raw == null) {
            throw new ConfigurationException("Storage profile must specify a backend plugin");
        }

        if (raw.plugin() == null || raw.plugin().isBlank()) {
            throw new ConfigurationException("Backend plugin name cannot be null or empty");
        }

        return new CompiledProfileBackend(
                raw.plugin(),
                raw.overrides() != null ? Map.copyOf(raw.overrides()) : Map.of()
        );
    }

    /**
     * Compile storage characteristics.
     *
     * @param raw raw characteristics
     * @return compiled characteristics
     */
    CompiledCharacteristics compileCharacteristics(RawCharacteristics raw) {
        if (raw == null) {
            // Return sensible defaults
            return new CompiledCharacteristics(
                    LatencyClass.MEDIUM,
                    CostTier.MEDIUM,
                    DurabilityLevel.HIGH,
                    false,
                    false,
                    ConsistencyModel.STRONG
            );
        }

        return new CompiledCharacteristics(
                compileLatencyClass(raw.latencyClass()),
                compileCostTier(raw.costTier()),
                compileDurability(raw.durability()),
                raw.appendOnly() != null && raw.appendOnly(),
                raw.immutable() != null && raw.immutable(),
                compileConsistency(raw.consistency())
        );
    }

    /**
     * Compile latency class from string.
     *
     * @param latencyClass latency class string
     * @return compiled latency class
     */
    LatencyClass compileLatencyClass(String latencyClass) {
        if (latencyClass == null || latencyClass.isBlank()) {
            return LatencyClass.MEDIUM;
        }

        try {
            return LatencyClass.valueOf(latencyClass.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Invalid latency class: " + latencyClass
                    + ". Valid values: " + Arrays.toString(LatencyClass.values()));
        }
    }

    /**
     * Compile cost tier from string.
     *
     * @param costTier cost tier string
     * @return compiled cost tier
     */
    CostTier compileCostTier(String costTier) {
        if (costTier == null || costTier.isBlank()) {
            return CostTier.MEDIUM;
        }

        try {
            return CostTier.valueOf(costTier.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Invalid cost tier: " + costTier
                    + ". Valid values: " + Arrays.toString(CostTier.values()));
        }
    }

    /**
     * Compile durability level from string.
     *
     * @param durability durability string
     * @return compiled durability level
     */
    DurabilityLevel compileDurability(String durability) {
        if (durability == null || durability.isBlank()) {
            return DurabilityLevel.HIGH;
        }

        try {
            return DurabilityLevel.valueOf(durability.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Invalid durability level: " + durability
                    + ". Valid values: " + Arrays.toString(DurabilityLevel.values()));
        }
    }

    /**
     * Compile consistency model from string.
     *
     * @param consistency consistency string
     * @return compiled consistency model
     */
    ConsistencyModel compileConsistency(String consistency) {
        if (consistency == null || consistency.isBlank()) {
            return ConsistencyModel.STRONG;
        }

        try {
            return ConsistencyModel.valueOf(consistency.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Invalid consistency model: " + consistency
                    + ". Valid values: " + Arrays.toString(ConsistencyModel.values()));
        }
    }

    /**
     * Compile profile defaults from settings and explicit defaults.
     *
     * @param settings general settings map
     * @param raw explicit defaults
     * @return compiled defaults
     */
    CompiledProfileDefaults compileDefaults(Map<String, Object> settings, RawProfileDefaults raw) {
        if (raw == null && (settings == null || settings.isEmpty())) {
            return CompiledProfileDefaults.empty();
        }

        // Extract values from both settings map and explicit defaults
        String ttlStr = raw != null ? raw.ttl() : getStringFromSettings(settings, "ttl");
        String maxSizeStr = raw != null ? raw.maxSize() : getStringFromSettings(settings, "maxSize");
        String evictionStr = raw != null ? raw.evictionPolicy() : getStringFromSettings(settings, "evictionPolicy");
        Integer connectionPool = raw != null ? raw.connectionPool() : getIntFromSettings(settings, "connectionPool");
        String indexStrategyStr = raw != null ? raw.indexStrategy() : getStringFromSettings(settings, "indexStrategy");
        String partitioningStr = raw != null ? raw.partitioning() : getStringFromSettings(settings, "partitioning");

        return new CompiledProfileDefaults(
                parseDuration(ttlStr),
                parseSize(maxSizeStr),
                parseEvictionPolicy(evictionStr),
                Optional.ofNullable(connectionPool),
                parseIndexStrategy(indexStrategyStr),
                parsePartitioning(partitioningStr)
        );
    }

    private String getStringFromSettings(Map<String, Object> settings, String key) {
        if (settings == null) {
            return null;
        }
        Object value = settings.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getIntFromSettings(Map<String, Object> settings, String key) {
        if (settings == null) {
            return null;
        }
        Object value = settings.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parse ISO-8601 duration string.
     *
     * @param durationStr duration string (e.g., "PT1H", "P7D")
     * @return optional duration
     */
    Optional<Duration> parseDuration(String durationStr) {
        if (durationStr == null || durationStr.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(Duration.parse(durationStr));
        } catch (Exception e) {
            throw new ConfigurationException("Invalid duration format: " + durationStr
                    + ". Expected ISO-8601 duration (e.g., PT1H, P7D)");
        }
    }

    /**
     * Parse size string to bytes.
     *
     * @param sizeStr size string (e.g., "10GB", "128MB")
     * @return optional size in bytes
     */
    Optional<Long> parseSize(String sizeStr) {
        if (sizeStr == null || sizeStr.isBlank()) {
            return Optional.empty();
        }

        Matcher matcher = SIZE_PATTERN.matcher(sizeStr.trim());
        if (!matcher.matches()) {
            throw new ConfigurationException("Invalid size format: " + sizeStr
                    + ". Expected format: <number><unit> (e.g., 10GB, 128MB)");
        }

        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2).toUpperCase();

        long multiplier = switch (unit) {
            case "B" ->
                1L;
            case "KB" ->
                1024L;
            case "MB" ->
                1024L * 1024L;
            case "GB" ->
                1024L * 1024L * 1024L;
            case "TB" ->
                1024L * 1024L * 1024L * 1024L;
            default ->
                throw new ConfigurationException("Unknown size unit: " + unit);
        };

        return Optional.of(value * multiplier);
    }

    /**
     * Parse eviction policy.
     *
     * @param policyStr policy string
     * @return optional eviction policy
     */
    Optional<CompiledProfileDefaults.EvictionPolicy> parseEvictionPolicy(String policyStr) {
        if (policyStr == null || policyStr.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(CompiledProfileDefaults.EvictionPolicy.valueOf(policyStr.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Invalid eviction policy: " + policyStr
                    + ". Valid values: " + Arrays.toString(CompiledProfileDefaults.EvictionPolicy.values()));
        }
    }

    /**
     * Parse index strategy.
     *
     * @param strategyStr strategy string
     * @return optional index strategy
     */
    Optional<CompiledProfileDefaults.IndexStrategy> parseIndexStrategy(String strategyStr) {
        if (strategyStr == null || strategyStr.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(CompiledProfileDefaults.IndexStrategy.valueOf(strategyStr.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Invalid index strategy: " + strategyStr
                    + ". Valid values: " + Arrays.toString(CompiledProfileDefaults.IndexStrategy.values()));
        }
    }

    /**
     * Parse partitioning strategy.
     *
     * @param partitioningStr partitioning string
     * @return optional partitioning strategy
     */
    Optional<CompiledProfileDefaults.PartitioningStrategy> parsePartitioning(String partitioningStr) {
        if (partitioningStr == null || partitioningStr.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(CompiledProfileDefaults.PartitioningStrategy.valueOf(partitioningStr.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new ConfigurationException("Invalid partitioning strategy: " + partitioningStr
                    + ". Valid values: " + Arrays.toString(CompiledProfileDefaults.PartitioningStrategy.values()));
        }
    }
}
