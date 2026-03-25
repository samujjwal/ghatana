/*
 * Copyright (c) 2025 Ghatana Platform Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ghatana.yappc.core.telemetry;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unified telemetry configuration that consolidates settings from multiple sources. Provides
 * environment variable support with sensible defaults for all telemetry components.
 *
 * @doc.type class
 * @doc.purpose Unified telemetry configuration that consolidates settings from multiple sources. Provides
 * @doc.layer platform
 * @doc.pattern Configuration
 */
public class TelemetryConfig {

    private static final Logger log = LoggerFactory.getLogger(TelemetryConfig.class);

    // Environment variable names
    public static final String OTEL_SERVICE_NAME = "OTEL_SERVICE_NAME";
    public static final String OTEL_EXPORTER_OTLP_ENDPOINT = "OTEL_EXPORTER_OTLP_ENDPOINT";
    public static final String OTEL_TRACES_SAMPLER = "OTEL_TRACES_SAMPLER";
    public static final String OTEL_TRACES_SAMPLER_ARG = "OTEL_TRACES_SAMPLER_ARG";
    public static final String OTEL_METRICS_EXPORT_INTERVAL = "OTEL_METRIC_EXPORT_INTERVAL";
    public static final String OTEL_ENABLED = "OTEL_ENABLED";
    public static final String YAPPC_TELEMETRY_ENABLED = "YAPPC_TELEMETRY_ENABLED";
    public static final String DEPLOYMENT_ENV = "DEPLOYMENT_ENV";

    private final String serviceName;
    private final String otlpEndpoint;
    private final boolean tracingEnabled;
    private final boolean metricsEnabled;
    private final boolean usageCollectionEnabled;
    private final double samplingProbability;
    private final Duration metricsExportInterval;
    private final String deploymentEnvironment;

    private TelemetryConfig(Builder builder) {
        this.serviceName = builder.serviceName;
        this.otlpEndpoint = builder.otlpEndpoint;
        this.tracingEnabled = builder.tracingEnabled;
        this.metricsEnabled = builder.metricsEnabled;
        this.usageCollectionEnabled = builder.usageCollectionEnabled;
        this.samplingProbability = builder.samplingProbability;
        this.metricsExportInterval = builder.metricsExportInterval;
        this.deploymentEnvironment = builder.deploymentEnvironment;
    }

    /**
 * Creates a configuration from environment variables with defaults. */
    public static TelemetryConfig fromEnvironment() {
        return fromEnvironment("yappc");
    }

    /**
 * Creates a configuration from environment variables with custom service name. */
    public static TelemetryConfig fromEnvironment(String defaultServiceName) {
        String serviceName = getEnv(OTEL_SERVICE_NAME, defaultServiceName);
        String otlpEndpoint = getEnv(OTEL_EXPORTER_OTLP_ENDPOINT, "http://localhost:4317");
        boolean otelEnabled = getBooleanEnv(OTEL_ENABLED, true);
        boolean telemetryEnabled = getBooleanEnv(YAPPC_TELEMETRY_ENABLED, true);

        // Parse sampling configuration
        String sampler = getEnv(OTEL_TRACES_SAMPLER, "parentbased_always_on");
        double samplingProbability = parseSamplingProbability(sampler);

        // Parse metrics interval
        long intervalMs = getLongEnv(OTEL_METRICS_EXPORT_INTERVAL, 60000L);
        Duration metricsInterval = Duration.ofMillis(intervalMs);

        String deploymentEnv = getEnv(DEPLOYMENT_ENV, "development");

        return builder()
                .serviceName(serviceName)
                .otlpEndpoint(otlpEndpoint)
                .tracingEnabled(otelEnabled)
                .metricsEnabled(otelEnabled)
                .usageCollectionEnabled(telemetryEnabled)
                .samplingProbability(samplingProbability)
                .metricsExportInterval(metricsInterval)
                .deploymentEnvironment(deploymentEnv)
                .build();
    }

    /**
 * Creates a configuration for CLI usage (simple telemetry only). */
    public static TelemetryConfig forCli() {
        return builder()
                .serviceName("yappc-cli")
                .tracingEnabled(false)
                .metricsEnabled(false)
                .usageCollectionEnabled(true)
                .build();
    }

    /**
 * Creates a configuration for service components (full observability). */
    public static TelemetryConfig forService(String serviceName) {
        return fromEnvironment(serviceName);
    }

    /**
 * Creates a configuration for build tools (performance focus). */
    public static TelemetryConfig forBuildTool(String toolName) {
        return builder()
                .serviceName("yappc-" + toolName)
                .tracingEnabled(true)
                .metricsEnabled(true)
                .usageCollectionEnabled(true)
                .samplingProbability(0.1) // Lower sampling for build tools
                .build();
    }

    /**
 * Creates a no-op configuration (all telemetry disabled). */
    public static TelemetryConfig disabled() {
        return builder()
                .serviceName("yappc-noop")
                .tracingEnabled(false)
                .metricsEnabled(false)
                .usageCollectionEnabled(false)
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getServiceName() {
        return serviceName;
    }

    public String getOtlpEndpoint() {
        return otlpEndpoint;
    }

    public boolean isTracingEnabled() {
        return tracingEnabled;
    }

    public boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    public boolean isUsageCollectionEnabled() {
        return usageCollectionEnabled;
    }

    public double getSamplingProbability() {
        return samplingProbability;
    }

    public Duration getMetricsExportInterval() {
        return metricsExportInterval;
    }

    public String getDeploymentEnvironment() {
        return deploymentEnvironment;
    }

    public boolean isObservabilityEnabled() {
        return tracingEnabled || metricsEnabled;
    }

    // Helper methods for environment variable parsing
    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return value != null && !value.trim().isEmpty() ? value.trim() : defaultValue;
    }

    private static boolean getBooleanEnv(String key, boolean defaultValue) {
        String value = System.getenv(key);
        return value != null ? Boolean.parseBoolean(value.trim()) : defaultValue;
    }

    private static long getLongEnv(String key, long defaultValue) {
        String value = System.getenv(key);
        if (value != null) {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException e) {
                log.error("Invalid long value for {}: {}, using default: {}", key, value, defaultValue);
            }
        }
        return defaultValue;
    }

    private static double parseSamplingProbability(String sampler) {
        if (sampler == null) return 1.0;

        switch (sampler.toLowerCase()) {
            case "always_on":
            case "parentbased_always_on":
                return 1.0;
            case "always_off":
            case "parentbased_always_off":
                return 0.0;
            case "traceidratio":
            case "parentbased_traceidratio":
                // Try to get the ratio from OTEL_TRACES_SAMPLER_ARG
                String arg = System.getenv(OTEL_TRACES_SAMPLER_ARG);
                if (arg != null) {
                    try {
                        double ratio = Double.parseDouble(arg.trim());
                        return Math.max(0.0, Math.min(1.0, ratio));
                    } catch (NumberFormatException e) {
                        log.error("Invalid sampling ratio: {}, using 1.0", arg);
                    }
                }
                return 1.0;
            default:
                return 1.0;
        }
    }

    public static class Builder {
        private String serviceName = "yappc";
        private String otlpEndpoint = "http://localhost:4317";
        private boolean tracingEnabled = true;
        private boolean metricsEnabled = true;
        private boolean usageCollectionEnabled = true;
        private double samplingProbability = 1.0;
        private Duration metricsExportInterval = Duration.ofMinutes(1);
        private String deploymentEnvironment = "development";

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder otlpEndpoint(String otlpEndpoint) {
            this.otlpEndpoint = otlpEndpoint;
            return this;
        }

        public Builder tracingEnabled(boolean enabled) {
            this.tracingEnabled = enabled;
            return this;
        }

        public Builder metricsEnabled(boolean enabled) {
            this.metricsEnabled = enabled;
            return this;
        }

        public Builder usageCollectionEnabled(boolean enabled) {
            this.usageCollectionEnabled = enabled;
            return this;
        }

        public Builder samplingProbability(double probability) {
            this.samplingProbability = Math.max(0.0, Math.min(1.0, probability));
            return this;
        }

        public Builder metricsExportInterval(Duration interval) {
            this.metricsExportInterval = interval;
            return this;
        }

        public Builder deploymentEnvironment(String environment) {
            this.deploymentEnvironment = environment;
            return this;
        }

        public TelemetryConfig build() {
            return new TelemetryConfig(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "TelemetryConfig{service=%s, tracing=%b, metrics=%b, usage=%b, sampling=%.2f,"
                        + " env=%s}",
                serviceName,
                tracingEnabled,
                metricsEnabled,
                usageCollectionEnabled,
                samplingProbability,
                deploymentEnvironment);
    }
}
