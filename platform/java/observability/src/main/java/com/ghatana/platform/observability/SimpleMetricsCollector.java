package com.ghatana.platform.observability;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Simple concrete implementation of MetricsCollector extending BaseMetricsCollector.
 *
 * <p>SimpleMetricsCollector is the default implementation for basic metrics collection.
 * It inherits all functionality from BaseMetricsCollector and can be used directly
 * or via MetricsCollectorFactory.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Counter increments with tags</li>
 *   <li>Error recording</li>
 *   <li>Null-safe operation (no-op if registry is null)</li>
 *   <li>Tag conversion from Map to Micrometer Tags</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * MeterRegistry registry = new SimpleMeterRegistry();
 * MetricsCollector collector = new SimpleMetricsCollector(registry);
 *
 * collector.incrementCounter("requests.total", "method", "POST", "status", "200");
 * collector.recordError("requests.failed", exception, Map.of("tenant", "123"));
 * }</pre>
 *
 * <p><b>Thread-Safety:</b> Thread-safe. Inherits thread-safety from BaseMetricsCollector
 * and MeterRegistry.</p>
 *
 * @see BaseMetricsCollector for base implementation
 * @see MetricsCollectorFactory for recommended creation pattern
 *
 * @doc.type class
 * @doc.purpose Default concrete implementation of MetricsCollector with Micrometer backend
 * @doc.layer core
 * @doc.pattern Implementation
 *
 * @author Platform Team
 * @created 2024-09-15
 * @updated 2025-10-29
 * @version 1.0.0
 * @type Concrete Implementation
 * @purpose Default implementation of MetricsCollector for basic metrics collection
 * @pattern Template Method pattern (inherits behavior from BaseMetricsCollector)
 * @responsibility Inherits counter increments, error recording, tag conversion from BaseMetricsCollector
 * @usage Created via MetricsCollectorFactory.create(registry) or directly via constructor
 * @examples See class-level JavaDoc usage example
 * @testing Test counter increments, error recording, tag handling; inherit tests from BaseMetricsCollector
 * @notes No additional logic beyond BaseMetricsCollector; primary concrete implementation
 */
public class SimpleMetricsCollector extends BaseMetricsCollector {
    
    /**
     * Constructs a SimpleMetricsCollector with the specified MeterRegistry.
     *
     * <p>The registry is used for all metrics operations. If registry is null,
     * metrics operations will no-op (gracefully degrade).</p>
     *
     * @param registry the MeterRegistry to use for metrics storage (may be null)
     */
    public SimpleMetricsCollector(MeterRegistry registry) {
        super(registry);
    }
}
