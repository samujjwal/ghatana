package com.ghatana.platform.observability;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import io.opentelemetry.sdk.trace.samplers.Sampler;

import java.util.Objects;

/**
 * Configuration for the observability module.
 *
 * <p>ObservabilityConfig loads settings from configuration files, environment variables,
 * and system properties using Typesafe Config. It aggregates metrics, tracing, and
 * monitoring configurations.</p>
 *
 * <p><b>Configuration Hierarchy:</b></p>
 * <pre>
 * 1. System properties (-Dconfig.file, -Dconfig.resource)
 * 2. application.conf (user-provided)
 * 3. reference.conf (bundled with library)
 * </pre>
 *
 * <p><b>Configuration Structure:</b></p>
 * <pre>
 * observability {
 *   metrics {
 *     exporter = "prometheus"          # prometheus, statsd, etc.
 *     endpoint = "http://localhost:9090"
 *     export-interval-ms = 60000       # 60 seconds
 *   }
 * @doc.type class
 * @doc.purpose Typesafe Config wrapper for observability metrics, tracing, and monitoring settings
 * @doc.layer core
 * @doc.pattern Configuration, Settings Holder
 *   tracing {
 *     enabled = true
 *     exporter = "otlp"                # jaeger, zipkin, otlp
 *     endpoint = "http://localhost:4317"
 *   }
 *   monitoring {
 *     enabled = true
 *     port = 8081                       # Monitoring server port
 *     endpoint = "/metrics"             # Base path
 *     expose-jvm-metrics = true
 *   }
 * }
 * </pre>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * ObservabilityConfig config = ObservabilityConfig.load();
 * MetricsConfig metricsConfig = config.getMetricsConfig();
 * TracingConfig tracingConfig = config.getTracingConfig();
 * }</pre>
 *
 * @see com.typesafe.config.Config for Typesafe Config documentation
 *
 * @author Platform Team
 * @created 2024-10-10
 * @updated 2025-10-29
 * @version 1.0.0
 * @type Configuration (Value Object)
 * @purpose Centralized configuration for metrics, tracing, and monitoring with Typesafe Config integration
 * @pattern Configuration pattern (immutable value objects)
 * @responsibility Load and aggregate observability configuration from files/environment
 * @usage Call ObservabilityConfig.load() to load from application.conf/reference.conf
 * @examples See class-level JavaDoc for configuration structure and usage
 * @testing Use Config.parseString() for inline config in tests
 * @notes Immutable value objects; configuration loaded once at startup
 */
public class ObservabilityConfig {
    
    private final MetricsConfig metricsConfig;
    private final TracingConfig tracingConfig;
    private final MonitoringConfig monitoringConfig;
    private final ServiceConfig serviceConfig;
    
    public ObservabilityConfig(MetricsConfig metricsConfig, 
                             TracingConfig tracingConfig,
                             MonitoringConfig monitoringConfig) {
        this(metricsConfig, tracingConfig, monitoringConfig, ServiceConfig.defaultConfig());
    }

    public ObservabilityConfig(MetricsConfig metricsConfig, 
                             TracingConfig tracingConfig,
                             MonitoringConfig monitoringConfig,
                             ServiceConfig serviceConfig) {
        this.metricsConfig = Objects.requireNonNull(metricsConfig);
        this.tracingConfig = Objects.requireNonNull(tracingConfig);
        this.monitoringConfig = Objects.requireNonNull(monitoringConfig);
        this.serviceConfig = Objects.requireNonNull(serviceConfig);
    }
    
    /**
     * Load configuration from the environment.
     */
    public static ObservabilityConfig load() {
        // Load configuration with fallbacks:
        // 1. System properties (-Dconfig.file, -Dconfig.resource, etc.)
        // 2. application.conf
        // 3. reference.conf (bundled with the library)
        Config config = ConfigFactory.load();
        
        // Extract configuration sections
        MetricsConfig metricsConfig = MetricsConfig.from(config.getConfig("observability.metrics"));
        TracingConfig tracingConfig = TracingConfig.from(config.getConfig("observability.tracing"));
        MonitoringConfig monitoringConfig = MonitoringConfig.from(config.getConfig("observability.monitoring"));
        ServiceConfig serviceConfig = config.hasPath("observability.service")
                ? ServiceConfig.from(config.getConfig("observability.service"))
                : ServiceConfig.defaultConfig();
        
        return new ObservabilityConfig(metricsConfig, tracingConfig, monitoringConfig, serviceConfig);
    }
    
    public MetricsConfig getMetricsConfig() {
        return metricsConfig;
    }
    
    public TracingConfig getTracingConfig() {
        return tracingConfig;
    }
    
    public MonitoringConfig getMonitoringConfig() {
        return monitoringConfig;
    }

    public ServiceConfig getServiceConfig() {
        return serviceConfig;
    }
    
    /**
     * Metrics configuration.
     */
    public static class MetricsConfig {
        private final String exporterType; // prometheus, statsd, etc.
        private final String endpoint;     // Where to export metrics
        private final int exportIntervalMs; // How often to export metrics
        
        public MetricsConfig(String exporterType, String endpoint, int exportIntervalMs) {
            this.exporterType = exporterType;
            this.endpoint = endpoint;
            this.exportIntervalMs = exportIntervalMs;
        }
        
        public static MetricsConfig from(Config config) {
            return new MetricsConfig(
                config.getString("exporter"),
                config.getString("endpoint"),
                config.getInt("export-interval-ms")
            );
        }
        
        // Getters...
        public String getExporterType() { return exporterType; }
        public String getEndpoint() { return endpoint; }
        public int getExportIntervalMs() { return exportIntervalMs; }
    }
    
    /**
     * Tracing configuration.
     */
    public static class TracingConfig {
        private final boolean enabled;
        private final String exporterType; // jaeger, zipkin, otlp, etc.
        private final String endpoint;     // Where to export traces
        private final Sampler sampler;     // Sampling strategy
        
        public TracingConfig(boolean enabled, String exporterType, String endpoint, Sampler sampler) {
            this.enabled = enabled;
            this.exporterType = exporterType;
            this.endpoint = endpoint;
            this.sampler = sampler;
        }
        
        public static TracingConfig from(Config config) {
            return new TracingConfig(
                config.getBoolean("enabled"),
                config.getString("exporter"),
                config.getString("endpoint"),
                Sampler.alwaysOn() // Default to always sample for now
            );
        }
        
        // Getters...
        public boolean isEnabled() { return enabled; }
        public String getExporterType() { return exporterType; }
        public String getEndpoint() { return endpoint; }
        public Sampler getSampler() { return sampler; }
    }
    
    /**
     * Monitoring configuration.
     */
    public static class MonitoringConfig {
        private final boolean enabled;
        private final int port;            // Port for the monitoring server
        private final String endpoint;      // Base path for monitoring endpoints
        private final boolean exposeJvmMetrics; // Whether to expose JVM metrics
        
        public MonitoringConfig(boolean enabled, int port, String endpoint, boolean exposeJvmMetrics) {
            this.enabled = enabled;
            this.port = port;
            this.endpoint = endpoint;
            this.exposeJvmMetrics = exposeJvmMetrics;
        }
        
        public static MonitoringConfig from(Config config) {
            return new MonitoringConfig(
                config.getBoolean("enabled"),
                config.getInt("port"),
                config.getString("endpoint"),
                config.getBoolean("expose-jvm-metrics")
            );
        }
        
        // Getters...
        public boolean isEnabled() { return enabled; }
        public int getPort() { return port; }
        public String getEndpoint() { return endpoint; }
        public boolean shouldExposeJvmMetrics() { return exposeJvmMetrics; }
    }

    /**
     * Service metadata configuration (service name, environment, version).
     *
     * <p>Used by ObservabilityModule to initialize MetricsRegistry with
     * per-service tags while keeping backward-compatible defaults when
     * configuration is absent.</p>
     */
    public static class ServiceConfig {
        private final String serviceName;
        private final String environment;
        private final String version;

        public ServiceConfig(String serviceName, String environment, String version) {
            this.serviceName = serviceName;
            this.environment = environment;
            this.version = version;
        }

        public static ServiceConfig from(Config config) {
            return new ServiceConfig(
                    config.getString("name"),
                    config.getString("environment"),
                    config.getString("version")
            );
        }

        public static ServiceConfig defaultConfig() {
            return new ServiceConfig("eventcloud", "local", "0.0.0");
        }

        public String getServiceName() {
            return serviceName;
        }

        public String getEnvironment() {
            return environment;
        }

        public String getVersion() {
            return version;
        }
    }
}
