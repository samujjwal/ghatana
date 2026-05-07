package com.ghatana.datacloud.config.model;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Compiled plugin configuration with validated, immutable settings.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents a validated, compiled plugin configuration that's ready for
 * runtime use. Plugins include storage backends, connectors, processors, and
 * integrations.
 *
 * <p>
 * <b>Plugin Types</b><br>
 * <ul>
 * <li>STORAGE - Database backends (PostgreSQL, Redis, S3, etc.)</li>
 * <li>EVENT_STORE - Append-only event storage with event sourcing support</li>
 * <li>CONNECTOR - External system integrations</li>
 * <li>PROCESSOR - Data processing pipelines</li>
 * <li>CACHE - Caching layers</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * CompiledPluginConfig plugin = configRegistry.getPlugin("tenant-1", "postgres-primary");
 * DataSource ds = plugin.createDataSource();
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Compiled plugin configuration for runtime use
 * @doc.layer core
 * @doc.pattern Value Object, Immutable
 */
public record CompiledPluginConfig(
        String name,
        String tenantId,
        String displayName,
        String description,
        PluginType type,
        String version,
        boolean enabled,
        CompiledBackendConfig backend,
        CompiledPoolConfig pool,
        CompiledPerformanceConfig performance,
        CompiledReplicationConfig replication,
        CompiledHealthCheckConfig healthCheck,
        Map<String, Object> features,
        Map<String, String> labels
        ) {

    /**
     * Plugin type enumeration.
     */
    public enum PluginType {
        STORAGE,
        EVENT_STORE,
        CONNECTOR,
        PROCESSOR,
        CACHE,
        QUEUE,
        SEARCH
    }

    /**
     * Storage tier enumeration.
     */
    public enum StorageTier {
        HOT, // Low latency, high cost
        WARM, // Balanced
        COLD, // High latency, low cost
        ARCHIVE // Long-term storage
    }

    /**
     * Gets a feature value by key.
     *
     * @param key feature key
     * @return Optional containing the feature value
     */
    public Optional<Object> getFeature(String key) {
        return Optional.ofNullable(features.get(key));
    }

    /**
     * Checks if plugin supports append-only mode (for event stores).
     *
     * @return true if append-only
     */
    public boolean isAppendOnly() {
        return Boolean.TRUE.equals(features.get("appendOnly"));
    }

    /**
     * Gets the storage tier for this plugin.
     *
     * @return storage tier or HOT by default
     */
    public StorageTier getTier() {
        Object tier = features.get("tier");
        if (tier instanceof String s) {
            try {
                return StorageTier.valueOf(s.toUpperCase());
            } catch (IllegalArgumentException e) {
                return StorageTier.HOT;
            }
        }
        return StorageTier.HOT;
    }

    /**
     * Backend connection configuration.
     */
    public record CompiledBackendConfig(
            String type,
            String connectionUrl,
            String username,
            String password,
            Map<String, String> properties
    ) {
        /**
         * Gets a connection property.
         *
         * @param key property key
         * @return Optional containing the property value
         */
    public Optional<String> getProperty(String key) {
        return Optional.ofNullable(properties.get(key));
    }
}

/**
 * Connection pool configuration.
 */
public record CompiledPoolConfig(
        int minSize,
        int maxSize,
        Duration idleTimeout,
        Duration connectionTimeout,
        Duration maxLifetime,
        Duration validationTimeout
        ) {

    /**
     * Creates a default pool config.
     *
     * @return default pool config
     */
    public static CompiledPoolConfig defaults() {
        return new CompiledPoolConfig(
                5, // minSize
                20, // maxSize
                Duration.ofMinutes(10), // idleTimeout
                Duration.ofSeconds(30), // connectionTimeout
                Duration.ofMinutes(30), // maxLifetime
                Duration.ofSeconds(5) // validationTimeout
        );
    }
}

/**
 * Performance tuning configuration.
 */
public record CompiledPerformanceConfig(
        int maxRowsPerQuery,
        Duration queryTimeout,
        int batchSize,
        int maxKeysPerScan,
        int pipelineBatchSize,
        boolean enableBatching,
        boolean enableCaching
        ) {

    /**
     * Creates a default performance config.
     *
     * @return default performance config
     */
    public static CompiledPerformanceConfig defaults() {
        return new CompiledPerformanceConfig(
                10000, // maxRowsPerQuery
                Duration.ofSeconds(30), // queryTimeout
                1000, // batchSize
                1000, // maxKeysPerScan
                100, // pipelineBatchSize
                true, // enableBatching
                true // enableCaching
        );
    }
}

/**
 * Replication configuration.
 */
public record CompiledReplicationConfig(
        boolean enabled,
        int factor,
        ReplicationMode mode,
        List<String> replicas
        ) {

    /**
     * Replication mode.
     */
    public enum ReplicationMode {
        SYNC, // Synchronous replication
        ASYNC, // Asynchronous replication
        SEMI_SYNC   // Semi-synchronous (quorum-based)
    }

    /**
     * Creates a disabled replication config.
     *
     * @return disabled replication config
     */
    public static CompiledReplicationConfig disabled() {
        return new CompiledReplicationConfig(false, 1, ReplicationMode.ASYNC, List.of());
    }
}

/**
 * Health check configuration for plugins.
 */
public record CompiledHealthCheckConfig(
        boolean enabled,
        Duration interval,
        Duration timeout,
        int failureThreshold,
        int successThreshold,
        String endpoint,
        HealthCheckType type
        ) {

    /**
     * Health check type.
     */
    public enum HealthCheckType {
        TCP, // TCP connection check
        HTTP, // HTTP endpoint check
        SQL, // SQL query check
        CUSTOM      // Custom health check implementation
    }

    /**
     * Creates a default health check config.
     *
     * @return default health check config
     */
    public static CompiledHealthCheckConfig defaults() {
        return new CompiledHealthCheckConfig(
                true, // enabled
                Duration.ofSeconds(30), // interval
                Duration.ofSeconds(10), // timeout
                3, // failureThreshold
                2, // successThreshold
                null, // endpoint
                HealthCheckType.TCP // type
        );
    }
}
}
