package com.ghatana.refactorer.server.observability;

import com.ghatana.refactorer.server.config.ServerConfig;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Initializes OpenTelemetry and Micrometer observability components. Configures metrics registry

 * and tracing based on configuration.

 *

 * @doc.type class

 * @doc.purpose Configure OTLP exporters and register SDK components for tracing/metrics.

 * @doc.layer product

 * @doc.pattern Factory

 */

public final class OTelInitializer {
    private static final Logger logger = LogManager.getLogger(OTelInitializer.class);
    private static MeterRegistry meterRegistry;
    private static CompositeMeterRegistry compositeRegistry;
    private static PrometheusMeterRegistry prometheusRegistry;
    private static SdkTracerProvider tracerProvider;
    private static OpenTelemetry openTelemetry;
    private static final String SERVICE_NAME = "polyfix-service-server";

    private OTelInitializer() {
        // Utility class
    }

    /**
     * Initializes observability components based on server configuration.
     *
     * @param config server configuration
     */
    public static void initialize(ServerConfig config) {
        logger.info("Initializing observability components...");

        try {
            initializeMetrics(config);
            initializeTracing(config);

            logger.info("Observability components initialized successfully");

        } catch (Exception e) {
            logger.error("Failed to initialize observability components", e);
            // Don't fail startup for observability issues
        }
    }

    /**
     * Initializes Micrometer metrics registry.
     *
     * @param config server configuration
     */
    private static void initializeMetrics(ServerConfig config) {
        if (!config.observability().metricsEnabled()) {
            logger.info("Metrics disabled in configuration");
            meterRegistry = new SimpleMeterRegistry();
            compositeRegistry = null;
            prometheusRegistry = null;
            return;
        }

        compositeRegistry = new CompositeMeterRegistry();
        meterRegistry = compositeRegistry;

        // Always provide a simple registry for in-process observation and unit tests.
        SimpleMeterRegistry simpleRegistry = new SimpleMeterRegistry();
        compositeRegistry.add(simpleRegistry);

        // Configure Prometheus registry for scraping.
        prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        compositeRegistry.add(prometheusRegistry);
        logger.info("Prometheus metrics registry configured");

        String otlpEndpoint = config.observability().otlpEndpoint();
        if (otlpEndpoint != null && !otlpEndpoint.isEmpty()) {
            logger.info("Configuring OTLP metrics exporter with endpoint: {}", otlpEndpoint);
            try {
                OtlpMeterRegistry otlpRegistry =
                        new OtlpMeterRegistry(
                                new ConfigurableOtlpConfig(otlpEndpoint), Clock.SYSTEM);
                compositeRegistry.add(otlpRegistry);
                logger.info("OTLP metrics registry registered successfully");
            } catch (Exception e) {
                logger.error(
                        "Failed to configure OTLP metrics registry, continuing without OTLP", e);
            }
        } else {
            logger.info("No OTLP endpoint configured, skipping OTLP metrics registry");
        }

        // Register common metrics
        registerCommonMetrics();
    }

    /**
     * Initializes OpenTelemetry tracing.
     *
     * @param config server configuration
     */
    private static void initializeTracing(ServerConfig config) {
        if (!config.observability().tracingEnabled()) {
            logger.info("Tracing disabled in configuration");
            setGlobalOpenTelemetry(OpenTelemetry.noop());
            return;
        }

        if (openTelemetry != null) {
            logger.debug("OpenTelemetry tracing already initialized");
            return;
        }

        String otlpEndpoint = config.observability().otlpEndpoint();
        if (otlpEndpoint == null || otlpEndpoint.isBlank()) {
            logger.info("No OTLP endpoint configured for tracing; using noop OpenTelemetry");
            setGlobalOpenTelemetry(OpenTelemetry.noop());
            return;
        }

        try {
            SpanExporter exporter =
                    OtlpGrpcSpanExporter.builder()
                            .setEndpoint(otlpEndpoint)
                            .setTimeout(Duration.ofSeconds(10))
                            .build();

            BatchSpanProcessor processor =
                    BatchSpanProcessor.builder(exporter)
                            .setScheduleDelay(Duration.ofSeconds(5))
                            .build();

            Resource resource =
                    Resource.getDefault()
                            .merge(Resource.builder().put("service.name", SERVICE_NAME).build());

            tracerProvider =
                    SdkTracerProvider.builder()
                            .addSpanProcessor(processor)
                            .setSampler(Sampler.alwaysOn())
                            .setResource(resource)
                            .build();

            openTelemetry = OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).build();

            setGlobalOpenTelemetry(openTelemetry);

            Runtime.getRuntime()
                    .addShutdownHook(
                            new Thread(
                                    () -> {
                                        if (tracerProvider != null) {
                                            tracerProvider.close();
                                        }
                                    }));

            logger.info("OpenTelemetry tracing configured with OTLP endpoint: {}", otlpEndpoint);
        } catch (Exception e) {
            logger.error("Failed to configure OpenTelemetry tracing; falling back to noop", e);
            setGlobalOpenTelemetry(OpenTelemetry.noop());
        }
    }

    /**
 * Registers common application metrics. */
    private static void registerCommonMetrics() {
        if (meterRegistry == null) {
            return;
        }

        // Register common counters and timers
        meterRegistry.counter("polyfix.requests.total", "endpoint", "unknown");
        meterRegistry.counter("polyfix.jobs.created");
        meterRegistry.counter("polyfix.jobs.completed");
        meterRegistry.counter("polyfix.jobs.failed");
        meterRegistry.timer("polyfix.job.duration");
        meterRegistry.timer("polyfix.request.duration", "endpoint", "unknown");

        logger.debug("Common metrics registered");
    }

    /**
     * Gets the configured meter registry.
     *
     * @return MeterRegistry instance
     */
    public static MeterRegistry getMeterRegistry() {
        return meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
    }

    /**
 * Scrapes metrics from the Prometheus registry when available. */
    public static String prometheusScrape() {
        if (prometheusRegistry == null) {
            return "# metrics disabled\n";
        }
        return prometheusRegistry.scrape();
    }

    private static void setGlobalOpenTelemetry(OpenTelemetry telemetry) {
        openTelemetry = telemetry;
        GlobalOpenTelemetry.resetForTest();
        GlobalOpenTelemetry.set(openTelemetry);
    }

    private record ConfigurableOtlpConfig(String endpoint) implements OtlpConfig {
        @Override
        public String url() {
            return endpoint;
        }

        @Override
        public Duration step() {
            return Duration.ofSeconds(30);
        }

        @Override
        public String get(String k) {
            // No additional properties
            return null;
        }
    }
}
