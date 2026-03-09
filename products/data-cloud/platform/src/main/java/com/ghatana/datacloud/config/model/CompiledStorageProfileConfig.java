/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
 */
package com.ghatana.datacloud.config.model;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Compiled storage profile configuration with validated and type-safe values.
 * Storage profiles define tiered storage characteristics and backend bindings.
 *
 * @doc.type record
 * @doc.purpose Immutable compiled storage profile configuration
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CompiledStorageProfileConfig(
        String name,
        String displayName,
        StorageTier tier,
        CompiledProfileBackend backend,
        CompiledCharacteristics characteristics,
        CompiledProfileDefaults defaults,
        Map<String, Object> additionalSettings
        ) {

    /**
     * Storage tier enumeration for tiered storage management.
     */
    public enum StorageTier {
        /**
         * Ultra-fast access, sub-10ms reads (Redis, Memcached)
         */
        HOT,
        /**
         * Fast access, 10-100ms reads (PostgreSQL, MongoDB)
         */
        WARM,
        /**
         * Analytics optimized, 100ms-1s reads (ClickHouse, Iceberg)
         */
        COOL,
        /**
         * Archive storage, 1s+ reads (S3 Glacier, Azure Archive)
         */
        COLD
    }

    /**
     * Latency classification for storage backends.
     */
    public enum LatencyClass {
        /**
         * < 10ms
         */
        ULTRA_LOW,
        /**
         * 10-100ms
         */
        LOW,
        /**
         * 100ms - 1s
         */
        MEDIUM,
        /**
         * 1s - 10s
         */
        HIGH,
        /**
         * > 10s
         */
        VERY_HIGH
    }

    /**
     * Cost tier for storage backends.
     */
    public enum CostTier {
        VERY_LOW,
        LOW,
        MEDIUM,
        HIGH,
        VERY_HIGH
    }

    /**
     * Durability level for storage backends.
     */
    public enum DurabilityLevel {
        /**
         * Cache-only, no persistence
         */
        LOW,
        /**
         * Single-node persistence
         */
        MEDIUM,
        /**
         * Replicated, multi-node
         */
        HIGH,
        /**
         * Geo-replicated, multi-region
         */
        VERY_HIGH
    }

    /**
     * Consistency model for the storage backend.
     */
    public enum ConsistencyModel {
        EVENTUAL,
        STRONG,
        SESSION,
        CAUSAL
    }

    /**
     * Backend plugin reference for the profile.
     */
    public record CompiledProfileBackend(
            String pluginName,
            Map<String, String> overrides
    ) {
    }

    /**
     * Storage characteristics for the profile.
     */
    public record CompiledCharacteristics(
            LatencyClass latencyClass,
            CostTier costTier,
            DurabilityLevel durability,
            boolean appendOnly,
            boolean immutable,
            ConsistencyModel consistency
    ) {
        /**
         * Check if this profile is suitable for event sourcing.
         *
         * @return true if append-only and high durability
         */
    public boolean isEventSourcingCompatible() {
        return appendOnly
                && (durability == DurabilityLevel.HIGH || durability == DurabilityLevel.VERY_HIGH);
    }

    /**
     * Check if this profile is suitable for caching.
     *
     * @return true if ultra-low latency regardless of durability
     */
    public boolean isCachingProfile() {
        return latencyClass == LatencyClass.ULTRA_LOW;
    }
}

/**
 * Default settings for collections using this profile.
 */
public record CompiledProfileDefaults(
        Optional<Duration> ttl,
        Optional<Long> maxSizeBytes,
        Optional<EvictionPolicy> evictionPolicy,
        Optional<Integer> connectionPool,
        Optional<IndexStrategy> indexStrategy,
        Optional<PartitioningStrategy> partitioning
        ) {

    /**
     * Eviction policies for cache-tier storage.
     */
    public enum EvictionPolicy {
        LRU,
        LFU,
        FIFO,
        TTL,
        RANDOM,
        NONE
    }

    /**
     * Index strategies for storage optimization.
     */
    public enum IndexStrategy {
        /**
         * Optimized for append-only workloads
         */
        APPEND_OPTIMIZED,
        /**
         * Optimized for read-heavy workloads
         */
        READ_OPTIMIZED,
        /**
         * Optimized for write-heavy workloads
         */
        WRITE_OPTIMIZED,
        /**
         * Balanced read/write
         */
        BALANCED,
        /**
         * Custom index configuration
         */
        CUSTOM
    }

    /**
     * Partitioning strategies for large-scale storage.
     */
    public enum PartitioningStrategy {
        /**
         * Partition by aggregate ID
         */
        BY_AGGREGATE,
        /**
         * Partition by time
         */
        BY_TIME,
        /**
         * Partition by both aggregate and time
         */
        BY_AGGREGATE_AND_TIME,
        /**
         * Hash-based partitioning
         */
        HASH,
        /**
         * Range-based partitioning
         */
        RANGE,
        /**
         * No partitioning
         */
        NONE
    }

    /**
     * Create defaults with empty optionals.
     *
     * @return empty defaults
     */
    public static CompiledProfileDefaults empty() {
        return new CompiledProfileDefaults(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
        );
    }
}

/**
 * Check if this profile is appropriate for the given tier requirement.
 *
 * @param requiredTier the minimum tier required
 * @return true if this profile meets or exceeds the tier
 */
public boolean meetsTierRequirement(StorageTier requiredTier) {
        return tier.ordinal() <= requiredTier.ordinal();
    }
    
    /**
     * Check if this profile supports event sourcing workloads.
     *
     * @return true if suitable for event sourcing
     */
    public boolean supportsEventSourcing() {
        return characteristics.isEventSourcingCompatible();
    }
}
