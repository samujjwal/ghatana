package com.ghatana.datacloud.launcher.observability;

import com.ghatana.platform.observability.TracingConfiguration;

/**
 * Data Cloud specific tracing configuration.
 *
 * <p>Extends platform TracingConfiguration with Data Cloud-specific settings
 * for distributed tracing backend configuration.</p>
 *
 * <p><b>Configuration:</b></p>
 * <ul>
 *   <li>Service Name: "data-cloud"</li>
 *   <li>Environment: Configured via DATACLOUD_PROFILE env var</li>
 *   <li>OTLP Endpoint: Configured via OTEL_EXPORTER_OTLP_ENDPOINT env var (default: http://localhost:4317)</li>
 *   <li>Sampling: Environment-specific (dev: 100%, staging: 10%, prod: 1%)</li>
 * </ul>
 *
 * <p><b>Environment Variables:</b></p>
 * <ul>
 *   <li>DATACLOUD_PROFILE: deployment environment (dev, staging, prod)</li>
 *   <li>OTEL_EXPORTER_OTLP_ENDPOINT: OTLP collector endpoint</li>
 *   <li>OTEL_SERVICE_NAME: service name override</li>
 *   <li>OTEL_SERVICE_VERSION: service version override</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * // Initialize tracing at application startup
 * TracingConfiguration.TracingConfig config = DataCloudTracingConfig.fromEnvironment();
 * OpenTelemetry otel = TracingConfiguration.initialize(config);
 *
 * // Access tracer
 * Tracer tracer = TracingConfiguration.getTracer();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Data Cloud-specific tracing configuration (CROSS-P1-2)
 * @doc.layer product
 * @doc.pattern Configuration
 */
public final class DataCloudTracingConfig {

    private static final String DEFAULT_SERVICE_NAME = "data-cloud";
    private static final String DEFAULT_SERVICE_VERSION = "1.0.0";
    private static final String DEFAULT_OTLP_ENDPOINT = "http://localhost:4317";
    private static final String DEFAULT_ENVIRONMENT = "development";

    private DataCloudTracingConfig() {
        // Utility class
    }

    /**
     * Creates tracing configuration from environment variables.
     *
     * <p>Reads configuration from environment variables with sensible defaults:</p>
     * <ul>
     *   <li>DATACLOUD_PROFILE → environment (default: development)</li>
     *   <li>OTEL_EXPORTER_OTLP_ENDPOINT → OTLP endpoint (default: http://localhost:4317)</li>
     *   <li>OTEL_SERVICE_NAME → service name (default: data-cloud)</li>
     *   <li>OTEL_SERVICE_VERSION → service version (default: 1.0.0)</li>
     * </ul>
     *
     * @return tracing configuration with environment-based settings
     */
    public static TracingConfiguration.TracingConfig fromEnvironment() {
        String environment = System.getenv("DATACLOUD_PROFILE");
        if (environment == null || environment.isBlank()) {
            environment = DEFAULT_ENVIRONMENT;
        }

        String otlpEndpoint = System.getenv("OTEL_EXPORTER_OTLP_ENDPOINT");
        if (otlpEndpoint == null || otlpEndpoint.isBlank()) {
            otlpEndpoint = DEFAULT_OTLP_ENDPOINT;
        }

        String serviceName = System.getenv("OTEL_SERVICE_NAME");
        if (serviceName == null || serviceName.isBlank()) {
            serviceName = DEFAULT_SERVICE_NAME;
        }

        String serviceVersion = System.getenv("OTEL_SERVICE_VERSION");
        if (serviceVersion == null || serviceVersion.isBlank()) {
            serviceVersion = DEFAULT_SERVICE_VERSION;
        }

        return TracingConfiguration.TracingConfig.builder()
                .enabled(true)
                .serviceName(serviceName)
                .serviceVersion(serviceVersion)
                .environment(environment)
                .otlpEndpoint(otlpEndpoint)
                .build();
    }

    /**
     * Creates tracing configuration for development environment.
     *
     * <p>Development configuration uses 100% sampling for full trace visibility.</p>
     *
     * @return tracing configuration for development
     */
    public static TracingConfiguration.TracingConfig forDevelopment() {
        return TracingConfiguration.TracingConfig.builder()
                .enabled(true)
                .serviceName(DEFAULT_SERVICE_NAME)
                .serviceVersion(DEFAULT_SERVICE_VERSION)
                .environment("development")
                .otlpEndpoint(DEFAULT_OTLP_ENDPOINT)
                .build();
    }

    /**
     * Creates tracing configuration for staging environment.
     *
     * <p>Staging configuration uses 10% sampling for cost-effective tracing.</p>
     *
     * @param otlpEndpoint OTLP collector endpoint
     * @return tracing configuration for staging
     */
    public static TracingConfiguration.TracingConfig forStaging(String otlpEndpoint) {
        return TracingConfiguration.TracingConfig.builder()
                .enabled(true)
                .serviceName(DEFAULT_SERVICE_NAME)
                .serviceVersion(DEFAULT_SERVICE_VERSION)
                .environment("staging")
                .otlpEndpoint(otlpEndpoint != null ? otlpEndpoint : DEFAULT_OTLP_ENDPOINT)
                .build();
    }

    /**
     * Creates tracing configuration for production environment.
     *
     * <p>Production configuration uses 1% sampling for cost-effective tracing at scale.</p>
     *
     * @param otlpEndpoint OTLP collector endpoint
     * @param serviceVersion service version
     * @return tracing configuration for production
     */
    public static TracingConfiguration.TracingConfig forProduction(String otlpEndpoint, String serviceVersion) {
        return TracingConfiguration.TracingConfig.builder()
                .enabled(true)
                .serviceName(DEFAULT_SERVICE_NAME)
                .serviceVersion(serviceVersion != null ? serviceVersion : DEFAULT_SERVICE_VERSION)
                .environment("production")
                .otlpEndpoint(otlpEndpoint != null ? otlpEndpoint : DEFAULT_OTLP_ENDPOINT)
                .build();
    }

    /**
     * Creates no-op tracing configuration.
     *
     * <p>Use this to disable tracing entirely (zero overhead).</p>
     *
     * @return disabled tracing configuration
     */
    public static TracingConfiguration.TracingConfig disabled() {
        return TracingConfiguration.TracingConfig.builder()
                .enabled(false)
                .serviceName(DEFAULT_SERVICE_NAME)
                .serviceVersion(DEFAULT_SERVICE_VERSION)
                .environment(DEFAULT_ENVIRONMENT)
                .otlpEndpoint(DEFAULT_OTLP_ENDPOINT)
                .build();
    }
}
