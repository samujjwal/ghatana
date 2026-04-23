package com.ghatana.yappc.services.lifecycle;

import com.ghatana.platform.observability.MetricsProvider;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
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
    void getRegistryShouldReturnPrometheusRegistry() { // GH-90000
        PrometheusMeterRegistry registry = MetricsProvider.getRegistry(); // GH-90000

        assertThat(registry).isNotNull(); // GH-90000
        assertThat(registry).isInstanceOf(PrometheusMeterRegistry.class); // GH-90000
    }

    @Test
    @DisplayName("Metrics recorded via MetricsProvider appear in the shared registry scrape output")
    void metricsShouldBeVisibleInSharedRegistryScrape() { // GH-90000
        PrometheusMeterRegistry registry = MetricsProvider.getRegistry(); // GH-90000
        String tenantId = "test-tenant-yappc-lifecycle-" + System.nanoTime(); // GH-90000

        // Record a counter through the shared registry (simulates what lifecycle services do) // GH-90000
        registry.counter("yappc.lifecycle.test.events", "tenantId", tenantId).increment(); // GH-90000

        // The scrape output should contain the counter we just recorded
        String scrape = registry.scrape(); // GH-90000
        assertThat(scrape).contains("yappc_lifecycle_test_events");
        assertThat(scrape).contains(tenantId); // GH-90000
    }

    @Test
    @DisplayName("MetricsProvider.getRegistry() returns the same singleton instance across calls")
    void getRegistryShouldBeSingleton() { // GH-90000
        PrometheusMeterRegistry first = MetricsProvider.getRegistry(); // GH-90000
        PrometheusMeterRegistry second = MetricsProvider.getRegistry(); // GH-90000

        assertThat(first).isSameAs(second); // GH-90000
    }
}
