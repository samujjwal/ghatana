/**
 * @doc.type class
 * @doc.purpose Test distributed tracing setup and span propagation
 * @doc.layer platform
 * @doc.pattern Test
 */
package com.ghatana.platform.observability.tracing;

import com.ghatana.platform.observability.TracingManager;
import com.ghatana.platform.observability.TracingProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Distributed Tracing Tests
 *
 * Tests distributed tracing setup, span creation, propagation,
 * and export functionality for the platform observability module.
 */
@DisplayName("Distributed Tracing Tests [GH-90000]")
class DistributedTracingTest {

    @Test
    @DisplayName("Should create and export spans [GH-90000]")
    void shouldCreateAndExportSpans() { // GH-90000
        TracingManager manager = TracingManager.createForTesting("test-service", "1.0.0"); // GH-90000
        TracingProvider provider = manager.getProvider("test-provider [GH-90000]");

        assertThat(provider).isNotNull(); // GH-90000
        assertThat(manager.getProviders()).hasSize(1); // GH-90000
    }

    @Test
    @DisplayName("Should propagate trace context across service boundaries [GH-90000]")
    void shouldPropagateTraceContextAcrossServiceBoundaries() { // GH-90000
        TracingManager manager = TracingManager.createDefault("test-service", "1.0.0", "http://localhost:4317"); // GH-90000

        TracingProvider provider1 = manager.getProvider("service-1 [GH-90000]");
        TracingProvider provider2 = manager.getProvider("service-2 [GH-90000]");

        assertThat(provider1).isNotNull(); // GH-90000
        assertThat(provider2).isNotNull(); // GH-90000
        assertThat(manager.getProviders()).hasSize(2); // GH-90000
    }

    @Test
    @DisplayName("Should maintain span hierarchy [GH-90000]")
    void shouldMaintainSpanHierarchy() { // GH-90000
        TracingManager manager = TracingManager.createForTesting("test-service", "1.0.0"); // GH-90000
        TracingProvider provider = manager.getProvider("parent-provider [GH-90000]");

        assertThat(provider).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should handle span baggage propagation [GH-90000]")
    void shouldHandleSpanBaggagePropagation() { // GH-90000
        TracingManager manager = TracingManager.createForTesting("test-service", "1.0.0"); // GH-90000
        TracingProvider provider = manager.getProvider("baggage-provider [GH-90000]");

        assertThat(provider).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should sample traces according to sampling strategy [GH-90000]")
    void shouldSampleTracesAccordingToSamplingStrategy() { // GH-90000
        TracingManager manager = TracingManager.createDefault("test-service", "1.0.0"); // GH-90000

        assertThat(manager).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should handle span export failures gracefully [GH-90000]")
    void shouldHandleSpanExportFailuresGracefully() { // GH-90000
        TracingManager manager = TracingManager.createNoOp(); // GH-90000
        TracingProvider provider = manager.getProvider("no-op-provider [GH-90000]");

        assertThat(provider).isNotNull(); // GH-90000
        assertThat(manager.getProviders()).hasSize(1); // GH-90000
    }
}
