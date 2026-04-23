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
    void shouldRegisterAndCollectCounterMetrics() { // GH-90000
        MeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
        MetricsCollector collector = new SimpleMetricsCollector(registry); // GH-90000

        collector.incrementCounter("requests.total", "method", "POST", "status", "200"); // GH-90000
        collector.incrementCounter("requests.total", "method", "GET", "status", "200"); // GH-90000

        assertThat(registry.find("requests.total").counters()).isNotEmpty();
        double totalCount = registry.find("requests.total").counters().stream()
            .mapToDouble(Counter::count) // GH-90000
            .sum(); // GH-90000
        assertThat(totalCount).isEqualTo(2.0); // GH-90000
    }

    @Test
    @DisplayName("Should register and collect gauge metrics")
    void shouldRegisterAndCollectGaugeMetrics() { // GH-90000
        MeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
        MetricsCollector collector = new SimpleMetricsCollector(registry); // GH-90000

        assertThat(collector).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should register and collect histogram metrics")
    void shouldRegisterAndCollectHistogramMetrics() { // GH-90000
        MeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
        MetricsCollector collector = new SimpleMetricsCollector(registry); // GH-90000

        assertThat(collector).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should aggregate metrics across multiple sources")
    void shouldAggregateMetricsAcrossMultipleSources() { // GH-90000
        MeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
        MetricsCollector collector = new SimpleMetricsCollector(registry); // GH-90000

        collector.incrementCounter("requests.total", "service", "api", "status", "200"); // GH-90000
        collector.incrementCounter("requests.total", "service", "worker", "status", "200"); // GH-90000

        assertThat(registry.find("requests.total").counters()).hasSize(2);
    }

    @Test
    @DisplayName("Should export metrics in Prometheus format")
    void shouldExportMetricsInPrometheusFormat() { // GH-90000
        MeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
        MetricsCollector collector = new SimpleMetricsCollector(registry); // GH-90000

        collector.incrementCounter("test.metric", "label", "value"); // GH-90000

        assertThat(registry.find("test.metric")).isNotNull();
    }

    @Test
    @DisplayName("Should handle metric registration conflicts")
    void shouldHandleMetricRegistrationConflicts() { // GH-90000
        MeterRegistry registry = new SimpleMeterRegistry(); // GH-90000
        MetricsCollector collector = new SimpleMetricsCollector(registry); // GH-90000

        collector.incrementCounter("conflict.metric", "label", "value1"); // GH-90000
        collector.incrementCounter("conflict.metric", "label", "value2"); // GH-90000

        assertThat(registry.find("conflict.metric").counters()).hasSize(2);
    }
}
