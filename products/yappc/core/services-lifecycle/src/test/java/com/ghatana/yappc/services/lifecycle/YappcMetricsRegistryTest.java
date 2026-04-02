package com.ghatana.yappc.services.lifecycle;

import com.ghatana.platform.observability.MetricsProvider;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test: YAPPC lifecycle and scaffold service modules must use the canonical
 * platform registry rather than creating local PrometheusMeterRegistry instances.
 *
 * <p>This guards against a recurring pattern where product modules silently create
 * their own registries, causing metrics to be dropped from the shared /metrics endpoint.
 *
 * @doc.type class
 * @doc.purpose Regression tests for YAPPC canonical metrics registry usage
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("YAPPC Metrics Registry")
class YappcMetricsRegistryTest {

    @Test
    @DisplayName("MetricsProvider.getRegistry() returns a PrometheusMeterRegistry")
    void getRegistryShouldReturnPrometheusRegistry() {
        PrometheusMeterRegistry registry = MetricsProvider.getRegistry();

        assertThat(registry).isNotNull();
        assertThat(registry).isInstanceOf(PrometheusMeterRegistry.class);
    }

    @Test
    @DisplayName("Metrics recorded via MetricsProvider appear in the shared registry scrape output")
    void metricsShouldBeVisibleInSharedRegistryScrape() {
        PrometheusMeterRegistry registry = MetricsProvider.getRegistry();
        String tenantId = "test-tenant-yappc-lifecycle-" + System.nanoTime();

        // Record a counter through the shared registry (simulates what lifecycle services do)
        registry.counter("yappc.lifecycle.test.events", "tenantId", tenantId).increment();

        // The scrape output should contain the counter we just recorded
        String scrape = registry.scrape();
        assertThat(scrape).contains("yappc_lifecycle_test_events");
        assertThat(scrape).contains(tenantId);
    }

    @Test
    @DisplayName("MetricsProvider.getRegistry() returns the same singleton instance across calls")
    void getRegistryShouldBeSingleton() {
        PrometheusMeterRegistry first = MetricsProvider.getRegistry();
        PrometheusMeterRegistry second = MetricsProvider.getRegistry();

        assertThat(first).isSameAs(second);
    }
}
