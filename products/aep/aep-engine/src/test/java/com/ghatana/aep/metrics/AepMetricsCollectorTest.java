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
@DisplayName("AEP metrics collector")
class AepMetricsCollectorTest {

    @Test
    @DisplayName("create uses the shared platform collector instead of a no-op collector")
    void createShouldRecordIntoSharedRegistry() {
        MeterRegistry registry = MetricsProvider.getRegistry();
        String tenantId = "aep-metrics-test-tenant";
        double before = readCounter(registry, AepMetricsCollector.EVENTS_PROCESSED, tenantId);

        AepMetricsCollector.create().incrementEventsProcessed(tenantId);

        assertThat(readCounter(registry, AepMetricsCollector.EVENTS_PROCESSED, tenantId))
            .isEqualTo(before + 1.0d);
    }

    @Test
    @DisplayName("noop does not emit metrics into the shared registry")
    void noopShouldNotRecordIntoSharedRegistry() {
        MeterRegistry registry = MetricsProvider.getRegistry();
        String tenantId = "aep-noop-metrics-test-tenant";
        double before = readCounter(registry, AepMetricsCollector.EVENTS_FAILED, tenantId);

        AepMetricsCollector.noop().incrementEventsFailed(tenantId);

        assertThat(readCounter(registry, AepMetricsCollector.EVENTS_FAILED, tenantId))
            .isEqualTo(before);
    }

    private static double readCounter(MeterRegistry registry, String metricName, String tenantId) {
        Counter counter = registry.find(metricName)
            .tag("tenantId", tenantId)
            .counter();
        return counter != null ? counter.count() : 0.0d;
    }
}
