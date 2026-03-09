package com.ghatana.platform.observability;

import io.opentelemetry.api.trace.Tracer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Tracing.
 */
class TracingTest {

    @Test
    void testTracerReturnsNoOpWhenNotInitialized() {
        Tracer tracer = Tracing.tracer();
        
        assertNotNull(tracer);
    }

    @Test
    void testInitDisabled() {
        // Should not throw
        Tracing.init(false, null, null);
        
        Tracer tracer = Tracing.tracer();
        assertNotNull(tracer);
    }

    @Test
    void testInitWithDefaults() {
        // Should not throw with null values
        Tracing.init(true, null, null);
        
        Tracer tracer = Tracing.tracer();
        assertNotNull(tracer);
    }
}
