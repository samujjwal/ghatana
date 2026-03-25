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

/**
 * Global telemetry service manager for YAPPC applications. Provides a singleton instance of the
 * telemetry provider configured for the application context.
 *
 * @doc.type class
 * @doc.purpose Global telemetry service manager for YAPPC applications. Provides a singleton instance of the
 * @doc.layer platform
 * @doc.pattern Manager
 */
public class TelemetryManager {

    private static volatile TelemetryProvider instance;
    private static volatile TelemetryInstrumentation instrumentation;
    private static final Object lock = new Object();

    private TelemetryManager() {
        // Singleton class
    }

    /**
 * Gets the global telemetry provider instance, creating it if necessary. */
    public static TelemetryProvider getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = createProvider();
                }
            }
        }
        return instance;
    }

    /**
 * Gets a telemetry instrumentation instance for the given component. */
    public static TelemetryInstrumentation getInstrumentation(String componentName) {
        if (instrumentation == null) {
            synchronized (lock) {
                if (instrumentation == null) {
                    instrumentation = new TelemetryInstrumentation(getInstance(), componentName);
                }
            }
        }
        return instrumentation;
    }

    /**
     * Initializes telemetry with specific configuration. Call this early in application startup to
     * configure telemetry.
     */
    public static void initialize(TelemetryConfig config) {
        synchronized (lock) {
            if (instance != null) {
                instance.shutdown();
            }

            instance =
                    UnifiedTelemetryProvider.builder()
                            .serviceName(config.getServiceName())
                            .otlpEndpoint(config.getOtlpEndpoint())
                            .tracingEnabled(config.isTracingEnabled())
                            .metricsEnabled(config.isMetricsEnabled())
                            .usageCollectionEnabled(config.isUsageCollectionEnabled())
                            .samplingProbability(config.getSamplingProbability())
                            .build();

            instrumentation = null; // Reset instrumentation to pick up new provider
        }
    }

    /**
 * Initializes telemetry for CLI usage (simple configuration). */
    public static void initializeForCli() {
        initialize(TelemetryConfig.forCli());
    }

    /**
 * Initializes telemetry for service usage (full observability). */
    public static void initializeForService(String serviceName) {
        initialize(TelemetryConfig.forService(serviceName));
    }

    /**
 * Initializes telemetry for build tools (performance focused). */
    public static void initializeForBuildTool(String toolName) {
        initialize(TelemetryConfig.forBuildTool(toolName));
    }

    /**
 * Disables all telemetry. */
    public static void disable() {
        synchronized (lock) {
            if (instance != null) {
                instance.shutdown();
                instance = UnifiedTelemetryProvider.createNoop("yappc-disabled");
            }
        }
    }

    /**
 * Shuts down telemetry and releases resources. Call this during application shutdown. */
    public static void shutdown() {
        synchronized (lock) {
            if (instance != null) {
                instance.shutdown();
                instance = null;
                instrumentation = null;
            }
        }
    }

    /**
 * Checks if telemetry is initialized and active. */
    public static boolean isInitialized() {
        return instance != null && instance.isObservabilityEnabled();
    }

    /**
 * Checks if usage collection is enabled. */
    public static boolean isUsageCollectionEnabled() {
        return instance != null && instance.isUsageCollectionEnabled();
    }

    private static TelemetryProvider createProvider() {
        // Try to determine context and create appropriate provider
        String serviceName = System.getProperty("yappc.service.name");
        if (serviceName != null) {
            return UnifiedTelemetryProvider.createDefault(
                    serviceName,
                    System.getProperty("otel.exporter.otlp.endpoint", "http://localhost:4317"));
        }

        // Check if we're in a CLI context
        String mainClass = System.getProperty("sun.java.command", "");
        if (mainClass.contains("yappc") || mainClass.contains("cli")) {
            return UnifiedTelemetryProvider.createSimple("yappc-cli");
        }

        // Default to environment-based configuration
        TelemetryConfig config = TelemetryConfig.fromEnvironment();
        return UnifiedTelemetryProvider.builder()
                .serviceName(config.getServiceName())
                .otlpEndpoint(config.getOtlpEndpoint())
                .tracingEnabled(config.isTracingEnabled())
                .metricsEnabled(config.isMetricsEnabled())
                .usageCollectionEnabled(config.isUsageCollectionEnabled())
                .samplingProbability(config.getSamplingProbability())
                .build();
    }
}
