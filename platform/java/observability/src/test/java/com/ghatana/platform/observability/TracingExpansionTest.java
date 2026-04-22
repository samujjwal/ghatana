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
@DisplayName("Tracing - Phase 3 Expansion [GH-90000]")
class TracingExpansionTest {

    @Test
    @DisplayName("Tracer available when tracing disabled returns no-op [GH-90000]")
    void tracerWhenDisabled() { // GH-90000
        // Disable tracing explicitly
        Tracing.init(false, null, null); // GH-90000

        // Tracer should still be available (no-op fallback) // GH-90000
        Tracer tracer = Tracing.tracer(); // GH-90000
        assertThat(tracer).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Tracer available when enabled with default values [GH-90000]")
    void tracerWhenEnabledDefault() { // GH-90000
        // Enable tracing with defaults
        Tracing.init(true, null, null); // GH-90000

        // Tracer should be available
        Tracer tracer = Tracing.tracer(); // GH-90000
        assertThat(tracer).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Tracer available when enabled with custom endpoint [GH-90000]")
    void tracerWithCustomEndpoint() { // GH-90000
        // Enable with custom endpoint
        Tracing.init(true, "http://localhost:4317", null); // GH-90000

        // Tracer should be available
        Tracer tracer = Tracing.tracer(); // GH-90000
        assertThat(tracer).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Tracer available when enabled with custom service name [GH-90000]")
    void tracerWithCustomService() { // GH-90000
        // Enable with custom service name
        Tracing.init(true, null, "custom-service"); // GH-90000

        // Tracer should be available
        Tracer tracer = Tracing.tracer(); // GH-90000
        assertThat(tracer).isNotNull(); // GH-90000
    }
}
