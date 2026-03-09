package com.ghatana.platform.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Metrics.
 */
class MetricsTest {

    private MeterRegistry registry;
    private Metrics metrics;
    
    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new Metrics(registry);
    }
    
    @Test
    void testTimer() {
        var timer = metrics.timer("test.timer");
        
        assertNotNull(timer);
        assertEquals("test.timer", timer.getId().getName());
        
        timer.record(() -> {
            // Simulate work
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        assertTrue(timer.count() > 0);
    }
    
    @Test
    void testCounter() {
        var counter = metrics.counter("test.counter");
        
        assertNotNull(counter);
        assertEquals("test.counter", counter.getId().getName());
        
        counter.increment();
        counter.increment();
        
        assertEquals(2, counter.count());
    }
    
    @Test
    void testGetRegistry() {
        MeterRegistry returnedRegistry = metrics.getRegistry();
        
        assertNotNull(returnedRegistry);
        assertSame(registry, returnedRegistry);
    }
}
