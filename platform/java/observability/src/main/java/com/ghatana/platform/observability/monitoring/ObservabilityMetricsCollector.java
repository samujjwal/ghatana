package com.ghatana.platform.observability.monitoring;

import com.ghatana.platform.observability.MetricsRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects and reports internal metrics for the observability module.
 * Self-monitoring for observability infrastructure (JVM, metrics, tracing).
 *
 * <p>Tracks internal health of observability:
 * <ul>
 *   <li><strong>JVM Memory</strong>: Heap used/max</li>
 *   <li><strong>JVM Threads</strong>: Live/peak thread counts</li>
 *   <li><strong>Metrics Registry</strong>: Number of registered metrics</li>
 *   <li><strong>Error Tracking</strong>: Internal observability errors</li>
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * ObservabilityMetricsCollector collector = new ObservabilityMetricsCollector(
 *     metricsRegistry,
 *     tracerProvider,
 *     spanExporter
 * );
 * 
 * // Bind to registry
 * collector.bindTo(meterRegistry);
 * 
 * // Record errors
 * try {
 *     // Observability operation
 * } catch (Exception e) {
 *     collector.recordError("metrics_export");
 * }
 * }</pre>
 *
 * @since 1.0.0
 
 *
 * @doc.type class
 * @doc.purpose Observability metrics collector
 * @doc.layer core
 * @doc.pattern Component
*/
public class ObservabilityMetricsCollector implements MeterBinder, AutoCloseable {
    
    private final MetricsRegistry metricsRegistry;
    private final SdkTracerProvider tracerProvider;
    private final SpanExporter spanExporter;
    private final AtomicLong lastErrorCount = new AtomicLong(0);
    
    public ObservabilityMetricsCollector(MetricsRegistry metricsRegistry,
                                       SdkTracerProvider tracerProvider,
                                       SpanExporter spanExporter) {
        this.metricsRegistry = metricsRegistry;
        this.tracerProvider = tracerProvider;
        this.spanExporter = spanExporter;
    }
    
    @Override
    public void bindTo(MeterRegistry registry) {
        // Track JVM metrics
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        
        // Memory usage
        Gauge.builder("observability.jvm.memory.used", memoryBean, bean -> (double) bean.getHeapMemoryUsage().getUsed())
            .description("Current used heap memory in bytes")
            .baseUnit("bytes")
            .register(registry);

        Gauge.builder("observability.jvm.memory.max", memoryBean, bean -> (double) bean.getHeapMemoryUsage().getMax())
            .description("Max available heap memory in bytes")
            .baseUnit("bytes")
            .register(registry);

        // Thread usage
        Gauge.builder("observability.jvm.threads.live", threadBean, tb -> (double) tb.getThreadCount())
            .description("Current number of live threads")
            .register(registry);

        Gauge.builder("observability.jvm.threads.peak", threadBean, tb -> (double) tb.getPeakThreadCount())
            .description("Peak live thread count since JVM start")
            .register(registry);
            
        // Track internal metrics
        Gauge.builder("observability.metrics.registry.size", metricsRegistry, r -> (double) r.getMeters().size())
            .description("Number of registered metrics")
            .register(registry);
            
        // Track error rates
        registry.gauge("observability.errors", 
                      Tags.of("type", "internal"),
                      lastErrorCount,
                      AtomicLong::get);
    }
    
    /**
     * Record an error in the observability module.
     */
    public void recordError(String type) {
        metricsRegistry.counter("observability.errors", "type", type).increment();
        lastErrorCount.incrementAndGet();
    }
    
    @Override
    public void close() {
        // Clean up resources if needed
    }
}
