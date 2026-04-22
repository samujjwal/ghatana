package com.ghatana.platform.observability;

import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Tracing.
 */
class TracingTest {

    @Test
    void testTracerReturnsNoOpWhenNotInitialized() { // GH-90000
        Tracer tracer = Tracing.tracer(); // GH-90000

        assertNotNull(tracer); // GH-90000
    }

    @Test
    void testInitDisabled() { // GH-90000
        // Should not throw
        Tracing.init(false, null, null); // GH-90000

        Tracer tracer = Tracing.tracer(); // GH-90000
        assertNotNull(tracer); // GH-90000
    }

    @Test
    void testInitWithDefaults() { // GH-90000
        // Should not throw with null values
        Tracing.init(true, null, null); // GH-90000

        Tracer tracer = Tracing.tracer(); // GH-90000
        assertNotNull(tracer); // GH-90000
    }
}
