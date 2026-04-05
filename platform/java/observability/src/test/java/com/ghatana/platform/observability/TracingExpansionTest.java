package com.ghatana.platform.observability;

import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 3 expansion: Core tracing availability and edge cases.
 * Tests trace initialization defaults and tracer availability.
 *
 * @doc.type class
 * @doc.purpose Core tracing availability verification
 * @doc.layer platform
 * @doc.pattern Test
 */
@DisplayName("Tracing - Phase 3 Expansion")
class TracingExpansionTest {

    @Test
    @DisplayName("Tracer available when tracing disabled returns no-op")
    void tracerWhenDisabled() {
        // Disable tracing explicitly
        Tracing.init(false, null, null);
        
        // Tracer should still be available (no-op fallback)
        Tracer tracer = Tracing.tracer();
        assertThat(tracer).isNotNull();
    }

    @Test
    @DisplayName("Tracer available when enabled with default values")
    void tracerWhenEnabledDefault() {
        // Enable tracing with defaults
        Tracing.init(true, null, null);
        
        // Tracer should be available
        Tracer tracer = Tracing.tracer();
        assertThat(tracer).isNotNull();
    }

    @Test
    @DisplayName("Tracer available when enabled with custom endpoint")
    void tracerWithCustomEndpoint() {
        // Enable with custom endpoint
        Tracing.init(true, "http://localhost:4317", null);
        
        // Tracer should be available
        Tracer tracer = Tracing.tracer();
        assertThat(tracer).isNotNull();
    }

    @Test
    @DisplayName("Tracer available when enabled with custom service name")
    void tracerWithCustomService() {
        // Enable with custom service name
        Tracing.init(true, null, "custom-service");
        
        // Tracer should be available
        Tracer tracer = Tracing.tracer();
        assertThat(tracer).isNotNull();
    }
}
