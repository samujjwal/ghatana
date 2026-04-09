/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.config;

import com.ghatana.platform.config.watcher.ConfigReloadWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Watches a runtime configuration file and publishes safe hot-reload updates (AEP-019).
 *
 * <p>Only operationally safe settings are reloaded at runtime: anomaly threshold,
 * async timeout, tracing flag, consent cache TTL, and rate-limit settings.
 * Structural settings such as worker thread counts or event-cloud transport remain
 * startup-only.
 *
 * @doc.type class
 * @doc.purpose Detect and apply safe runtime configuration changes without restart
 * @doc.layer product
 * @doc.pattern Adapter, Observer
 */
public final class AepConfigReloadBridge implements AutoCloseable {

    public static final String CONFIG_PATH_KEY = "hotReloadConfigPath";
    public static final String CHECK_INTERVAL_MS_KEY = "hotReloadCheckIntervalMs";

    public static final String ANOMALY_THRESHOLD_PROPERTY = "aep.anomalyThreshold";
    public static final String ENABLE_TRACING_PROPERTY = "aep.enableTracing";
    public static final String ASYNC_TIMEOUT_MS_PROPERTY = "aep.asyncTimeoutMs";
    public static final String RATE_LIMIT_ENABLED_PROPERTY = "aep.rateLimitEnabled";
    public static final String RATE_LIMIT_MAX_REQUESTS_PROPERTY = "aep.rateLimitMaxRequestsPerMinute";
    public static final String RATE_LIMIT_BURST_PROPERTY = "aep.rateLimitBurstSize";
    public static final String CONSENT_CACHE_TTL_SECONDS_PROPERTY = "aep.consentCacheTtlSeconds";

    private static final Logger log = LoggerFactory.getLogger(AepConfigReloadBridge.class);

    private final Path configPath;
    private final ConfigReloadWatcher watcher;
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private AepConfigReloadBridge(Builder builder) {
        this.configPath = builder.configPath;
        this.watcher = new ConfigReloadWatcher(builder.checkIntervalMs);
    }

    /**
     * Starts watching the configured file.
     */
    public void start() {
        watcher.watchFile(configPath.toString(), ignored -> reloadNow());
    }

    /**
     * Reloads the config file immediately and notifies listeners.
     *
     * @return parsed runtime settings
     */
    public RuntimeTuning reloadNow() {
        try {
            RuntimeTuning tuning = load(configPath);
            listeners.forEach(listener -> listener.onReload(tuning));
            log.info("Reloaded safe runtime configuration from {}", configPath);
            return tuning;
        } catch (Exception e) {
            listeners.forEach(listener -> listener.onError(e));
            throw new IllegalStateException("Failed to reload AEP runtime config from " + configPath, e);
        }
    }

    /**
     * Adds a listener for reload events.
     *
     * @param listener listener to notify
     */
    public void addListener(Listener listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    @Override
    public void close() {
        watcher.close();
    }

    public static Builder builder() {
        return new Builder();
    }

    private static RuntimeTuning load(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        }

        List<String> purposes = new ArrayList<>();
        String purposesValue = properties.getProperty("aep.allowedPurposes");
        if (purposesValue != null && !purposesValue.isBlank()) {
            for (String part : purposesValue.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isBlank()) {
                    purposes.add(trimmed);
                }
            }
        }

        return new RuntimeTuning(
            optionalDouble(properties, ANOMALY_THRESHOLD_PROPERTY),
            optionalBoolean(properties, ENABLE_TRACING_PROPERTY),
            optionalDurationMillis(properties, ASYNC_TIMEOUT_MS_PROPERTY),
            optionalBoolean(properties, RATE_LIMIT_ENABLED_PROPERTY),
            optionalInteger(properties, RATE_LIMIT_MAX_REQUESTS_PROPERTY),
            optionalInteger(properties, RATE_LIMIT_BURST_PROPERTY),
            optionalDurationSeconds(properties, CONSENT_CACHE_TTL_SECONDS_PROPERTY),
            List.copyOf(purposes)
        );
    }

    private static Optional<Boolean> optionalBoolean(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value == null ? Optional.empty() : Optional.of(Boolean.parseBoolean(value));
    }

    private static Optional<Integer> optionalInteger(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value == null ? Optional.empty() : Optional.of(Integer.parseInt(value));
    }

    private static Optional<Double> optionalDouble(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value == null ? Optional.empty() : Optional.of(Double.parseDouble(value));
    }

    private static Optional<Duration> optionalDurationMillis(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value == null ? Optional.empty() : Optional.of(Duration.ofMillis(Long.parseLong(value)));
    }

    private static Optional<Duration> optionalDurationSeconds(Properties properties, String key) {
        String value = properties.getProperty(key);
        return value == null ? Optional.empty() : Optional.of(Duration.ofSeconds(Long.parseLong(value)));
    }

    /**
     * Listener for runtime tuning reloads.
     */
    public interface Listener {
        void onReload(RuntimeTuning tuning);

        default void onError(Exception exception) {
            log.warn("AEP runtime config reload listener error: {}", exception.getMessage(), exception);
        }
    }

    /**
     * Safe runtime settings that can be updated without restarting the engine.
     */
    public record RuntimeTuning(
        Optional<Double> anomalyThreshold,
        Optional<Boolean> tracingEnabled,
        Optional<Duration> asyncTimeout,
        Optional<Boolean> rateLimitEnabled,
        Optional<Integer> rateLimitMaxRequestsPerMinute,
        Optional<Integer> rateLimitBurstSize,
        Optional<Duration> consentCacheTtl,
        List<String> allowedPurposes
    ) {
        public RuntimeTuning {
            anomalyThreshold = anomalyThreshold != null ? anomalyThreshold : Optional.empty();
            tracingEnabled = tracingEnabled != null ? tracingEnabled : Optional.empty();
            asyncTimeout = asyncTimeout != null ? asyncTimeout : Optional.empty();
            rateLimitEnabled = rateLimitEnabled != null ? rateLimitEnabled : Optional.empty();
            rateLimitMaxRequestsPerMinute = rateLimitMaxRequestsPerMinute != null
                ? rateLimitMaxRequestsPerMinute : Optional.empty();
            rateLimitBurstSize = rateLimitBurstSize != null ? rateLimitBurstSize : Optional.empty();
            consentCacheTtl = consentCacheTtl != null ? consentCacheTtl : Optional.empty();
            allowedPurposes = allowedPurposes != null ? List.copyOf(allowedPurposes) : List.of();
        }
    }

    /**
     * Builder for {@link AepConfigReloadBridge}.
     */
    public static final class Builder {
        private Path configPath;
        private long checkIntervalMs = 30_000L;

        private Builder() {
        }

        public Builder configPath(Path configPath) {
            this.configPath = Objects.requireNonNull(configPath, "configPath must not be null");
            return this;
        }

        public Builder checkIntervalMs(long checkIntervalMs) {
            this.checkIntervalMs = checkIntervalMs;
            return this;
        }

        public AepConfigReloadBridge build() {
            Objects.requireNonNull(configPath, "configPath must not be null");
            if (checkIntervalMs < 1L) {
                throw new IllegalArgumentException("checkIntervalMs must be >= 1");
            }
            return new AepConfigReloadBridge(this);
        }
    }
}
