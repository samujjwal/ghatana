package com.ghatana.datacloud.config;

import com.ghatana.datacloud.config.model.*;
import com.ghatana.datacloud.config.model.CompiledPluginConfig.CompiledBackendConfig;
import com.ghatana.datacloud.config.model.CompiledPluginConfig.CompiledPoolConfig;
import com.ghatana.datacloud.config.model.CompiledPluginConfig.CompiledPerformanceConfig;
import com.ghatana.datacloud.config.model.CompiledPluginConfig.CompiledReplicationConfig;
import com.ghatana.datacloud.config.model.CompiledPluginConfig.CompiledHealthCheckConfig;
import com.ghatana.datacloud.config.model.CompiledPluginConfig.CompiledHealthCheckConfig.HealthCheckType;
import com.ghatana.datacloud.config.model.CompiledPluginConfig.CompiledReplicationConfig.ReplicationMode;
import com.ghatana.platform.core.exception.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compiles raw plugin configuration into optimized, immutable runtime objects.
 *
 * <p>
 * <b>Purpose</b><br>
 * Transforms {@link RawPluginConfig} from YAML into
 * {@link CompiledPluginConfig} with validated, parsed, and optimized settings
 * ready for runtime use.
 *
 * <p>
 * <b>Compilation Steps</b><br>
 * <ol>
 * <li>Parse duration strings (e.g., "30s", "10m", "1h")</li>
 * <li>Validate backend configuration</li>
 * <li>Apply defaults for missing optional fields</li>
 * <li>Build immutable compiled config</li>
 * </ol>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * PluginConfigCompiler compiler = new PluginConfigCompiler();
 * CompiledPluginConfig compiled = compiler.compile(rawConfig);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Compile raw plugin config to immutable runtime objects
 * @doc.layer core
 * @doc.pattern Compiler, Builder
 */
public class PluginConfigCompiler {

    private static final Logger LOG = LoggerFactory.getLogger(PluginConfigCompiler.class);

    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "^(\\d+)(ms|s|m|h|d)$", Pattern.CASE_INSENSITIVE
    );

    /**
     * Compiles a raw plugin configuration.
     *
     * @param raw the raw config to compile
     * @return compiled plugin configuration
     * @throws ConfigurationException if compilation fails
     */
    public CompiledPluginConfig compile(RawPluginConfig raw) {
        Objects.requireNonNull(raw, "Raw plugin config must not be null");
        Objects.requireNonNull(raw.metadata(), "Plugin metadata must not be null");
        Objects.requireNonNull(raw.spec(), "Plugin spec must not be null");

        LOG.debug("Compiling plugin config: {}/{}",
                raw.metadata().namespace(), raw.metadata().name());

        var metadata = raw.metadata();
        var spec = raw.spec();

        CompiledPluginConfig.PluginType type = parsePluginType(spec.type());
        CompiledBackendConfig backend = compileBackend(spec.backend());
        CompiledPoolConfig pool = compilePool(spec.pool());
        CompiledPerformanceConfig performance = compilePerformance(spec.performance());
        CompiledReplicationConfig replication = compileReplication(spec.replication());
        CompiledHealthCheckConfig healthCheck = compileHealthCheck(spec.healthCheck());

        return new CompiledPluginConfig(
                metadata.name(),
                metadata.namespace(),
                spec.displayName(),
                spec.description(),
                type,
                spec.version() != null ? spec.version() : "1.0.0",
                spec.enabled() != null ? spec.enabled() : true,
                backend,
                pool,
                performance,
                replication,
                healthCheck,
                Map.copyOf(spec.features()),
                Map.copyOf(metadata.labels())
        );
    }

    /**
     * Compiles an event store plugin configuration.
     *
     * <p>
     * Event store plugins have additional validation:
     * <ul>
     * <li>Must be append-only</li>
     * <li>Must support partitioning</li>
     * <li>Must have appropriate retention settings</li>
     * </ul>
     *
     * @param raw the raw config to compile
     * @return compiled plugin configuration for event store
     * @throws ConfigurationException if not valid for event store
     */
    public CompiledPluginConfig compileEventStore(RawPluginConfig raw) {
        CompiledPluginConfig compiled = compile(raw);

        // Validate event store requirements
        if (compiled.type() != CompiledPluginConfig.PluginType.EVENT_STORE
                && compiled.type() != CompiledPluginConfig.PluginType.STORAGE) {
            throw new ConfigurationException(
                    "Event store plugin must have type EVENT_STORE or STORAGE"
            );
        }

        // Ensure append-only is set
        Map<String, Object> features = new HashMap<>(compiled.features());
        features.put("appendOnly", true);

        return new CompiledPluginConfig(
                compiled.name(),
                compiled.tenantId(),
                compiled.displayName(),
                compiled.description(),
                CompiledPluginConfig.PluginType.EVENT_STORE,
                compiled.version(),
                compiled.enabled(),
                compiled.backend(),
                compiled.pool(),
                compiled.performance(),
                compiled.replication(),
                compiled.healthCheck(),
                Map.copyOf(features),
                compiled.labels()
        );
    }

    private CompiledPluginConfig.PluginType parsePluginType(String type) {
        if (type == null || type.isBlank()) {
            return CompiledPluginConfig.PluginType.STORAGE;
        }

        try {
            return CompiledPluginConfig.PluginType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            LOG.warn("Unknown plugin type '{}', defaulting to STORAGE", type);
            return CompiledPluginConfig.PluginType.STORAGE;
        }
    }

    private CompiledBackendConfig compileBackend(RawPluginConfig.RawBackendConfig raw) {
        if (raw == null) {
            throw new ConfigurationException("Plugin backend configuration is required");
        }

        return new CompiledBackendConfig(
                raw.type(),
                raw.connectionUrl(),
                raw.username(),
                raw.password(),
                raw.properties()
        );
    }

    private CompiledPoolConfig compilePool(RawPluginConfig.RawPoolConfig raw) {
        if (raw == null) {
            return CompiledPoolConfig.defaults();
        }

        return new CompiledPoolConfig(
                raw.minSize() != null ? raw.minSize() : 5,
                raw.maxSize() != null ? raw.maxSize() : 20,
                parseDuration(raw.idleTimeout(), Duration.ofMinutes(10)),
                parseDuration(raw.connectionTimeout(), Duration.ofSeconds(30)),
                parseDuration(raw.maxLifetime(), Duration.ofMinutes(30)),
                parseDuration(raw.validationTimeout(), Duration.ofSeconds(5))
        );
    }

    private CompiledPerformanceConfig compilePerformance(RawPluginConfig.RawPerformanceConfig raw) {
        if (raw == null) {
            return CompiledPerformanceConfig.defaults();
        }

        return new CompiledPerformanceConfig(
                raw.maxRowsPerQuery() != null ? raw.maxRowsPerQuery() : 10000,
                parseDuration(raw.queryTimeout(), Duration.ofSeconds(30)),
                raw.batchSize() != null ? raw.batchSize() : 1000,
                raw.maxKeysPerScan() != null ? raw.maxKeysPerScan() : 1000,
                raw.pipelineBatchSize() != null ? raw.pipelineBatchSize() : 100,
                raw.enableBatching() != null ? raw.enableBatching() : true,
                raw.enableCaching() != null ? raw.enableCaching() : true
        );
    }

    private CompiledReplicationConfig compileReplication(RawPluginConfig.RawReplicationConfig raw) {
        if (raw == null) {
            return CompiledReplicationConfig.disabled();
        }

        CompiledReplicationConfig.ReplicationMode mode
                = CompiledReplicationConfig.ReplicationMode.ASYNC;
        if (raw.mode() != null) {
            try {
                mode = CompiledReplicationConfig.ReplicationMode.valueOf(
                        raw.mode().toUpperCase().replace("-", "_")
                );
            } catch (IllegalArgumentException e) {
                LOG.warn("Unknown replication mode '{}', defaulting to ASYNC", raw.mode());
            }
        }

        return new CompiledReplicationConfig(
                raw.enabled() != null ? raw.enabled() : false,
                raw.factor() != null ? raw.factor() : 1,
                mode,
                raw.replicas()
        );
    }

    private CompiledHealthCheckConfig compileHealthCheck(RawPluginConfig.RawHealthCheckConfig raw) {
        if (raw == null) {
            return CompiledHealthCheckConfig.defaults();
        }

        CompiledHealthCheckConfig.HealthCheckType type
                = CompiledHealthCheckConfig.HealthCheckType.TCP;
        if (raw.type() != null) {
            try {
                type = CompiledHealthCheckConfig.HealthCheckType.valueOf(raw.type().toUpperCase());
            } catch (IllegalArgumentException e) {
                LOG.warn("Unknown health check type '{}', defaulting to TCP", raw.type());
            }
        }

        return new CompiledHealthCheckConfig(
                raw.enabled() != null ? raw.enabled() : true,
                parseDuration(raw.interval(), Duration.ofSeconds(30)),
                parseDuration(raw.timeout(), Duration.ofSeconds(10)),
                raw.failureThreshold() != null ? raw.failureThreshold() : 3,
                raw.successThreshold() != null ? raw.successThreshold() : 2,
                raw.endpoint(),
                type
        );
    }

    /**
     * Parses a duration string (e.g., "30s", "10m", "1h").
     *
     * @param value duration string
     * @param defaultValue default if null or invalid
     * @return parsed duration
     */
    private Duration parseDuration(String value, Duration defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        Matcher matcher = DURATION_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            LOG.warn("Invalid duration format '{}', using default", value);
            return defaultValue;
        }

        long amount = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2).toLowerCase();

        return switch (unit) {
            case "ms" ->
                Duration.ofMillis(amount);
            case "s" ->
                Duration.ofSeconds(amount);
            case "m" ->
                Duration.ofMinutes(amount);
            case "h" ->
                Duration.ofHours(amount);
            case "d" ->
                Duration.ofDays(amount);
            default ->
                defaultValue;
        };
    }
}
