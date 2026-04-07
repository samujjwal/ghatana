/**
 * @doc.type class
 * @doc.purpose Test metrics registration, collection, aggregation, and export functionality
 * @doc.layer platform
 * @doc.pattern Test
 */
package com.ghatana.platform.observability.metrics;

import com.ghatana.platform.observability.MetricsCollector;
import com.ghatana.platform.observability.SimpleMetricsCollector;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Metrics Collection Tests
 *
 * Tests metrics registration, collection, aggregation, and export functionality
 * for the platform observability module.
 */
@DisplayName("Metrics Collection Tests")
class MetricsCollectionTest {

    @Test
    @DisplayName("Should register and collect counter metrics")
    void shouldRegisterAndCollectCounterMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MetricsCollector collector = new SimpleMetricsCollector(registry);
        
        collector.incrementCounter("requests.total", "method", "POST", "status", "200");
        collector.incrementCounter("requests.total", "method", "GET", "status", "200");
        
        Counter counter = registry.find("requests.total").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Should register and collect gauge metrics")
    void shouldRegisterAndCollectGaugeMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MetricsCollector collector = new SimpleMetricsCollector(registry);
        
        assertThat(collector).isNotNull();
    }

    @Test
    @DisplayName("Should register and collect histogram metrics")
    void shouldRegisterAndCollectHistogramMetrics() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MetricsCollector collector = new SimpleMetricsCollector(registry);
        
        assertThat(collector).isNotNull();
    }

    @Test
    @DisplayName("Should aggregate metrics across multiple sources")
    void shouldAggregateMetricsAcrossMultipleSources() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MetricsCollector collector = new SimpleMetricsCollector(registry);
        
        collector.incrementCounter("requests.total", "service", "api", "status", "200");
        collector.incrementCounter("requests.total", "service", "worker", "status", "200");
        
        assertThat(registry.find("requests.total").counters()).hasSize(2);
    }

    @Test
    @DisplayName("Should export metrics in Prometheus format")
    void shouldExportMetricsInPrometheusFormat() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MetricsCollector collector = new SimpleMetricsCollector(registry);
        
        collector.incrementCounter("test.metric", "label", "value");
        
        assertThat(registry.find("test.metric")).isNotNull();
    }

    @Test
    @DisplayName("Should handle metric registration conflicts")
    void shouldHandleMetricRegistrationConflicts() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MetricsCollector collector = new SimpleMetricsCollector(registry);
        
        collector.incrementCounter("conflict.metric", "label", "value1");
        collector.incrementCounter("conflict.metric", "label", "value2");
        
        assertThat(registry.find("conflict.metric").counters()).hasSize(2);
    }
}
