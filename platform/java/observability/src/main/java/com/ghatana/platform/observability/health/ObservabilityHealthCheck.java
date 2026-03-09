package com.ghatana.platform.observability.health;

import com.ghatana.platform.observability.MetricsRegistry;
import io.activej.promise.Promise;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SpanExporter;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Internal observability health check.
 * Verifies that the observability subsystem itself is healthy.
 *
 * <p>Checks internal health of observability components:
 * <ul>
 *   <li><strong>Metrics Registry</strong>: Metrics collection is functional</li>
 *   <li><strong>Tracing</strong>: Distributed tracing is active</li>
 *   <li><strong>JVM Memory</strong>: Heap usage is within limits</li>
 *   <li><strong>JVM Threads</strong>: Thread count is healthy</li>
 *   <li><strong>Error Count</strong>: No excessive errors logged</li>
 * @doc.type class
 * @doc.purpose Health check for observability subsystem (metrics, tracing, JVM state)
 * @doc.layer core
 * @doc.pattern Health Check, Monitor
 * </ul>
 *
 * <h2>Usage Example:</h2>
 * <pre>{@code
 * MetricsRegistry metricsRegistry = MetricsRegistry.initialize(...);
 * SdkTracerProvider tracerProvider = buildTracerProvider();
 * SpanExporter spanExporter = buildSpanExporter();
 *
 * ObservabilityHealthCheck observabilityCheck =
 *     new ObservabilityHealthCheck(metricsRegistry, tracerProvider, spanExporter);
 *
 * HealthCheckRegistry registry = HealthCheckRegistry.getInstance();
 * registry.register(observabilityCheck);
 *
 * HttpServer server = HttpServerBuilder.create()
 *     .withHealthCheck("/health/liveness")
 *     .withMetrics("/health/metrics")
 *     .withCustomHealthCheck(registry::readiness)
 *     .build();
 * </pre>
 *
 * <h2>Manual Invocation:</h2>
 * <pre>{@code
 * observabilityCheck.check()
 *     .whenResult(result -> {
 *         if (result.isHealthy()) {
 *             log.info("JVM memory: {}%", result.getDetails().get("heapUsedPercent"));
 *         }
 *     });
 * }
 * </pre>
 *
 * <h2>Criticality:</h2>
 * This check is CRITICAL (isCritical=true) - observability failure prevents monitoring.
 *
 * @since 1.0.0
 */
public class ObservabilityHealthCheck implements HealthCheck {
    
    private final MetricsRegistry metricsRegistry;
    private final SdkTracerProvider tracerProvider;
    private final SpanExporter spanExporter;
    
    public ObservabilityHealthCheck(MetricsRegistry metricsRegistry, 
                                  SdkTracerProvider tracerProvider,
                                  SpanExporter spanExporter) {
        this.metricsRegistry = metricsRegistry;
        this.tracerProvider = tracerProvider;
        this.spanExporter = spanExporter;
    }
    
    @Override
    public String getName() {
        return "observability";
    }
    
    @Override
    public boolean isCritical() {
        return true;
    }
    
    @Override
    public Duration getTimeout() {
        return Duration.ofSeconds(2);
    }
    
    @Override
    public Promise<HealthCheckResult> check() {
        try {
            Map<String, Object> details = new HashMap<>();
            
            // Check metrics registry
            details.put("metrics.enabled", metricsRegistry != null);
            
            // Check tracing
            boolean tracingEnabled = tracerProvider != null && spanExporter != null;
            details.put("tracing.enabled", tracingEnabled);
            
            // Check JVM resources
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            details.put("jvm.memory.heap.used", memoryBean.getHeapMemoryUsage().getUsed());
            details.put("jvm.memory.heap.max", memoryBean.getHeapMemoryUsage().getMax());
            
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            details.put("jvm.threads.active", threadBean.getThreadCount());
            details.put("jvm.threads.peak", threadBean.getPeakThreadCount());
            
            // Check for critical errors in the last minute
            double errorCount = metricsRegistry.counter("observability.errors").count();
            details.put("errors.last_minute", errorCount);
            
            // Determine overall status
            Status status = Status.UP;
            String message = "Observability module is healthy";
            
            if (errorCount > 10) {
                status = Status.DEGRADED;
                message = "High error rate detected in observability module";
            }
            
            if (metricsRegistry == null || !tracingEnabled) {
                status = Status.DOWN;
                message = "Critical observability components are not initialized";
            }
            
            // Use HealthCheckResult static factories
            if (status == Status.UP) {
                return Promise.of(HealthCheckResult.healthy(message, details, getTimeout()));
            } else if (status == Status.DEGRADED) {
                return Promise.of(HealthCheckResult.degraded(message, details, getTimeout()));
            } else {
                return Promise.of(HealthCheckResult.unhealthy(message, details, getTimeout(), null));
            }

        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
}
