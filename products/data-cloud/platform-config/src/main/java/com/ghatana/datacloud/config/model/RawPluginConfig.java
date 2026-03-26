package com.ghatana.datacloud.config.model;

import java.util.List;
import java.util.Map;

/**
 * Raw plugin configuration as parsed from YAML files.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents the unvalidated, uncompiled plugin configuration that mirrors the
 * YAML structure directly. Supports environment variable interpolation and
 * Vault secret references.
 *
 * <p>
 * <b>YAML Example</b><br>
 * <pre>{@code
 * apiVersion: datacloud.ghatana.com/v1
 * kind: Plugin
 * metadata:
 *   name: postgres-primary
 *   namespace: tenant-123
 * spec:
 *   type: STORAGE
 *   displayName: "Primary PostgreSQL"
 *   backend:
 *     type: postgresql
 *     connectionUrl: ${POSTGRES_URL}
 *     username: ${POSTGRES_USER}
 *     password: ${vault:secrets/postgres/password}
 *   pool:
 *     minSize: 10
 *     maxSize: 100
 *   healthCheck:
 *     enabled: true
 *     interval: 30s
 * }</pre>
 *
 * @doc.type record
 * @doc.purpose Raw YAML-parsed plugin configuration
 * @doc.layer core
 * @doc.pattern Value Object, DTO
 */
public record RawPluginConfig(
        String apiVersion,
        String kind,
        RawPluginMetadata metadata,
        RawPluginSpec spec
        ) {

    /**
     * Plugin metadata section.
     */
    public record RawPluginMetadata(
            String name,
            String namespace,
            Map<String, String> labels,
            Map<String, String> annotations
    ) {
        

    public RawPluginMetadata    {
        labels = labels != null ? Map.copyOf(labels) : Map.of();
        annotations = annotations != null ? Map.copyOf(annotations) : Map.of();
    }
}

/**
 * Plugin spec section.
 */
public record RawPluginSpec(
        String type,
        String displayName,
        String description,
        String version,
        Boolean enabled,
        RawBackendConfig backend,
        RawPoolConfig pool,
        RawPerformanceConfig performance,
        RawReplicationConfig replication,
        RawHealthCheckConfig healthCheck,
        Map<String, Object> features
        ) {

    public RawPluginSpec           {
        features = features != null ? Map.copyOf(features) : Map.of();
    }
}

/**
 * Backend connection configuration.
 */
public record RawBackendConfig(
        String type,
        String connectionUrl,
        String username,
        String password,
        Map<String, String> properties
        ) {

    public RawBackendConfig     {
        properties = properties != null ? Map.copyOf(properties) : Map.of();
    }
}

/**
 * Connection pool configuration.
 */
public record RawPoolConfig(
        Integer minSize,
        Integer maxSize,
        String idleTimeout,
        String connectionTimeout,
        String maxLifetime,
        String validationTimeout
        ) {

}

/**
 * Performance tuning configuration.
 */
public record RawPerformanceConfig(
        Integer maxRowsPerQuery,
        String queryTimeout,
        Integer batchSize,
        Integer maxKeysPerScan,
        Integer pipelineBatchSize,
        Boolean enableBatching,
        Boolean enableCaching
        ) {

}

/**
 * Replication configuration.
 */
public record RawReplicationConfig(
        Boolean enabled,
        Integer factor,
        String mode,
        List<String> replicas
        ) {

    public RawReplicationConfig    {
        replicas = replicas != null ? List.copyOf(replicas) : List.of();
    }
}

/**
 * Health check configuration.
 */
public record RawHealthCheckConfig(
        Boolean enabled,
        String interval,
        String timeout,
        Integer failureThreshold,
        Integer successThreshold,
        String endpoint,
        String type
        ) {

}
}
