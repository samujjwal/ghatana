/*
 * Copyright (c) 2025 Ghatana.
 * All rights reserved.
 */
package com.ghatana.datacloud.config.health;

import com.ghatana.datacloud.config.model.CompiledPluginConfig;
import com.ghatana.datacloud.config.model.CompiledPluginConfig.CompiledHealthCheckConfig;
import com.ghatana.platform.observability.health.HealthCheck;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Config-driven health check that uses plugin configuration to parameterize
 * health checking. Bridges plugin configuration with libs:observability
 * HealthCheck interface.
 *
 * <p>
 * <b>Purpose</b><br>
 * Creates health checks dynamically from plugin YAML configuration. Allows
 * operators to configure health check parameters (interval, timeout, failure
 * threshold) without code changes.
 *
 * <p>
 * <b>Usage Example</b><br>
 * <pre>{@code
 * // Load plugin config
 * CompiledPluginConfig plugin = registry.getPlugin("tenant", "postgres").get();
 *
 * // Create health check from config
 * HealthCheck healthCheck = new ConfigDrivenHealthCheck(
 *     plugin,
 *     () -> checkDatabaseConnection() // Custom check logic
 * );
 *
 * // Register with health check registry
 * healthCheckRegistry.register(healthCheck);
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Config-driven health check bridging plugin config to HealthCheck
 * interface
 * @doc.layer product
 * @doc.pattern Adapter
 */
public class ConfigDrivenHealthCheck implements HealthCheck {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigDrivenHealthCheck.class);

    private final CompiledPluginConfig pluginConfig;
    private final Supplier<Promise<HealthCheckResult>> checkFunction;
    private final String checkName;
    private final boolean critical;

    // Failure tracking for threshold-based degradation
    private volatile int consecutiveFailures = 0;

    /**
     * Creates a config-driven health check.
     *
     * @param pluginConfig the plugin configuration with health settings
     * @param checkFunction the actual health check logic
     */
    public ConfigDrivenHealthCheck(
            @NotNull CompiledPluginConfig pluginConfig,
            @NotNull Supplier<Promise<HealthCheckResult>> checkFunction) {
        this(pluginConfig, checkFunction, pluginConfig.name() + "-health", false);
    }

    /**
     * Creates a config-driven health check with custom name.
     *
     * @param pluginConfig the plugin configuration with health settings
     * @param checkFunction the actual health check logic
     * @param checkName custom check name
     * @param critical whether this is a critical (liveness) check
     */
    public ConfigDrivenHealthCheck(
            @NotNull CompiledPluginConfig pluginConfig,
            @NotNull Supplier<Promise<HealthCheckResult>> checkFunction,
            @NotNull String checkName,
            boolean critical) {
        this.pluginConfig = Objects.requireNonNull(pluginConfig, "pluginConfig required");
        this.checkFunction = Objects.requireNonNull(checkFunction, "checkFunction required");
        this.checkName = Objects.requireNonNull(checkName, "checkName required");
        this.critical = critical;
    }

    @Override
    public Promise<HealthCheckResult> check() {
        CompiledHealthCheckConfig healthConfig = pluginConfig.healthCheck();

        if (!healthConfig.enabled()) {
            LOG.trace("Health check disabled for plugin: {}", pluginConfig.name());
            return Promise.of(HealthCheckResult.healthy("Health check disabled"));
        }

        long startTime = System.nanoTime();

        return checkFunction.get()
                .then(result -> {
                    Duration duration = Duration.ofNanos(System.nanoTime() - startTime);

                    if (result.isHealthy()) {
                        consecutiveFailures = 0;
                        return Promise.of(enrichResult(result, duration));
                    }

                    // Track consecutive failures
                    consecutiveFailures++;
                    int threshold = healthConfig.failureThreshold();

                    if (consecutiveFailures >= threshold) {
                        LOG.warn("Health check {} exceeded failure threshold ({}/{})",
                                checkName, consecutiveFailures, threshold);
                        return Promise.of(HealthCheckResult.unhealthy(
                                String.format("Failed %d consecutive times: %s",
                                        consecutiveFailures, result.getMessage()),
                                enrichDetails(result, duration),
                                duration, null));
                    }

                    // Below threshold - report as degraded
                    return Promise.of(HealthCheckResult.degraded(
                            String.format("Failing (%d/%d): %s",
                                    consecutiveFailures, threshold, result.getMessage()),
                            enrichDetails(result, duration),
                            duration));
                })
                .then(
                        r -> Promise.of(r),
                        ex -> {
                            consecutiveFailures++;
                            Duration duration = Duration.ofNanos(System.nanoTime() - startTime);
                            LOG.error("Health check {} threw exception", checkName, ex);
                            return Promise.of(HealthCheckResult.unhealthy(
                                    "Exception: " + ex.getMessage(),
                                    Map.of("plugin", pluginConfig.name()),
                                    duration, ex));
                        }
                );
    }

    @Override
    public String getName() {
        return checkName;
    }

    @Override
    public Duration getTimeout() {
        return pluginConfig.healthCheck().timeout();
    }

    @Override
    public boolean isCritical() {
        return critical;
    }

    /**
     * Gets the health check interval from plugin configuration. Can be used by
     * schedulers to know how often to run this check.
     *
     * @return configured check interval
     */
    public Duration getInterval() {
        return pluginConfig.healthCheck().interval();
    }

    /**
     * Gets the failure threshold from plugin configuration.
     *
     * @return number of failures before reporting unhealthy
     */
    public int getFailureThreshold() {
        return pluginConfig.healthCheck().failureThreshold();
    }

    /**
     * Gets the current consecutive failure count.
     *
     * @return number of consecutive failures
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    /**
     * Resets the failure counter (e.g., after manual intervention).
     */
    public void resetFailures() {
        consecutiveFailures = 0;
        LOG.info("Reset failure counter for health check: {}", checkName);
    }

    private HealthCheckResult enrichResult(HealthCheckResult result, Duration duration) {
        Map<String, Object> details = enrichDetails(result, duration);
        return new HealthCheckResult(
                result.getStatus(),
                result.getMessage(),
                details,
                duration,
                result.getError());
    }

    private Map<String, Object> enrichDetails(HealthCheckResult result, Duration duration) {
        Map<String, Object> original = result.getDetails();
        java.util.Map<String, Object> enriched = new java.util.HashMap<>(original);
        enriched.put("plugin", pluginConfig.name());
        enriched.put("pluginType", pluginConfig.type().name());
        enriched.put("checkDurationMs", duration.toMillis());
        enriched.put("consecutiveFailures", consecutiveFailures);
        return Map.copyOf(enriched);
    }

    /**
     * Builder for creating config-driven health checks.
     */
    public static class Builder {

        private CompiledPluginConfig pluginConfig;
        private Supplier<Promise<HealthCheckResult>> checkFunction;
        private String checkName;
        private boolean critical = false;

        public Builder pluginConfig(CompiledPluginConfig config) {
            this.pluginConfig = config;
            return this;
        }

        public Builder checkFunction(Supplier<Promise<HealthCheckResult>> function) {
            this.checkFunction = function;
            return this;
        }

        public Builder name(String name) {
            this.checkName = name;
            return this;
        }

        public Builder critical(boolean critical) {
            this.critical = critical;
            return this;
        }

        public ConfigDrivenHealthCheck build() {
            Objects.requireNonNull(pluginConfig, "pluginConfig required");
            Objects.requireNonNull(checkFunction, "checkFunction required");

            String name = checkName != null ? checkName : pluginConfig.name() + "-health";
            return new ConfigDrivenHealthCheck(pluginConfig, checkFunction, name, critical);
        }
    }

    /**
     * Creates a builder for fluent construction.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
}
