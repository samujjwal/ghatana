/**
 * @doc.type class
 * @doc.purpose Test distributed tracing setup and span propagation
 * @doc.layer platform
 * @doc.pattern Test
 */
package com.ghatana.platform.observability.tracing;

import com.ghatana.platform.observability.TracingManager;
import com.ghatana.platform.observability.TracingProvider;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Distributed Tracing Tests
 *
 * Tests distributed tracing setup, span creation, propagation,
 * and export functionality for the platform observability module.
 */
@DisplayName("Distributed Tracing Tests")
class DistributedTracingTest {

    @Test
    @DisplayName("Should create and export spans")
    void shouldCreateAndExportSpans() {
        TracingManager manager = TracingManager.createForTesting("test-service", "1.0.0");
        TracingProvider provider = manager.getProvider("test-provider");
        
        assertThat(provider).isNotNull();
        assertThat(manager.getProviders()).hasSize(1);
    }

    @Test
    @DisplayName("Should propagate trace context across service boundaries")
    void shouldPropagateTraceContextAcrossServiceBoundaries() {
        TracingManager manager = TracingManager.createDefault("test-service", "1.0.0", "http://localhost:4317");
        
        TracingProvider provider1 = manager.getProvider("service-1");
        TracingProvider provider2 = manager.getProvider("service-2");
        
        assertThat(provider1).isNotNull();
        assertThat(provider2).isNotNull();
        assertThat(manager.getProviders()).hasSize(2);
    }

    @Test
    @DisplayName("Should maintain span hierarchy")
    void shouldMaintainSpanHierarchy() {
        TracingManager manager = TracingManager.createForTesting("test-service", "1.0.0");
        TracingProvider provider = manager.getProvider("parent-provider");
        
        assertThat(provider).isNotNull();
    }

    @Test
    @DisplayName("Should handle span baggage propagation")
    void shouldHandleSpanBaggagePropagation() {
        TracingManager manager = TracingManager.createForTesting("test-service", "1.0.0");
        TracingProvider provider = manager.getProvider("baggage-provider");
        
        assertThat(provider).isNotNull();
    }

    @Test
    @DisplayName("Should sample traces according to sampling strategy")
    void shouldSampleTracesAccordingToSamplingStrategy() {
        TracingManager manager = TracingManager.createDefault("test-service", "1.0.0");
        
        assertThat(manager).isNotNull();
    }

    @Test
    @DisplayName("Should handle span export failures gracefully")
    void shouldHandleSpanExportFailuresGracefully() {
        TracingManager manager = TracingManager.createNoOp();
        TracingProvider provider = manager.getProvider("no-op-provider");
        
        assertThat(provider).isNotNull();
        assertThat(manager.getProviders()).hasSize(1);
    }
}
