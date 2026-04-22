package com.ghatana.aep.metrics;

import com.ghatana.platform.observability.MetricsProvider;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @doc.type class
 * @doc.purpose Verifies AEP metrics collector wiring against the platform metrics registry
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AEP metrics collector [GH-90000]")
class AepMetricsCollectorTest {

    @Test
    @DisplayName("create uses the shared platform collector instead of a no-op collector [GH-90000]")
    void createShouldRecordIntoSharedRegistry() { // GH-90000
        MeterRegistry registry = MetricsProvider.getRegistry(); // GH-90000
        String tenantId = "aep-metrics-test-tenant";
        double before = readCounter(registry, AepMetricsCollector.EVENTS_PROCESSED, tenantId); // GH-90000

        AepMetricsCollector.create().incrementEventsProcessed(tenantId); // GH-90000

        assertThat(readCounter(registry, AepMetricsCollector.EVENTS_PROCESSED, tenantId)) // GH-90000
            .isEqualTo(before + 1.0d); // GH-90000
    }

    @Test
    @DisplayName("noop does not emit metrics into the shared registry [GH-90000]")
    void noopShouldNotRecordIntoSharedRegistry() { // GH-90000
        MeterRegistry registry = MetricsProvider.getRegistry(); // GH-90000
        String tenantId = "aep-noop-metrics-test-tenant";
        double before = readCounter(registry, AepMetricsCollector.EVENTS_FAILED, tenantId); // GH-90000

        AepMetricsCollector.noop().incrementEventsFailed(tenantId); // GH-90000

        assertThat(readCounter(registry, AepMetricsCollector.EVENTS_FAILED, tenantId)) // GH-90000
            .isEqualTo(before); // GH-90000
    }

    private static double readCounter(MeterRegistry registry, String metricName, String tenantId) { // GH-90000
        Counter counter = registry.find(metricName) // GH-90000
            .tag("tenantId", tenantId) // GH-90000
            .counter(); // GH-90000
        return counter != null ? counter.count() : 0.0d; // GH-90000
    }
}
