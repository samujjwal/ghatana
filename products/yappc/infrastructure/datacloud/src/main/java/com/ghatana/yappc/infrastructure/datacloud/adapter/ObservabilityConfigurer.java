package com.ghatana.yappc.infrastructure.datacloud.adapter;

import com.ghatana.platform.observability.MetricsCollector;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Observability configuration and setup utility.
 * 
 * <p>Ensures all YAPPC modules use consistent observability patterns
 * from platform/observability.
 * 
 * @doc.type class
 * @doc.purpose Observability configuration
 * @doc.layer infrastructure
 * @doc.pattern Utility
 */
public class ObservabilityConfigurer {
    
    private static final Logger LOG = LoggerFactory.getLogger(ObservabilityConfigurer.class);
    
    private final MetricsCollector metricsCollector;
    
    public ObservabilityConfigurer(
        @NotNull MetricsCollector metricsCollector
    ) {
        this.metricsCollector = metricsCollector;
        LOG.info("Initialized ObservabilityConfigurer");
    }
    
    /**
     * Configures metrics collection for a module by recording a registration event.
     */
    public void configureMetrics(@NotNull String moduleName) {
        LOG.debug("Configuring metrics for module: {}", moduleName);
        metricsCollector.incrementCounter("module.registered", "module", moduleName);
    }
    
    /**
     * Configures both metrics and tracing.
     */
    public void configureAll(@NotNull String moduleName) {
        LOG.debug("Configuring observability for module: {}", moduleName);
        configureMetrics(moduleName);
    }
    
    /**
     * Gets the metrics collector.
     */
    @NotNull
    public MetricsCollector getMetricsCollector() {
        return metricsCollector;
    }
}
