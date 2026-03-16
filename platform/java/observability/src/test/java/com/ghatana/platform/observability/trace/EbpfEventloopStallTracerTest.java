package com.ghatana.platform.observability.trace;

import io.activej.common.time.Stopwatch;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("eBPF Eventloop Stall Tracer Tests")
class EbpfEventloopStallTracerTest {

    private MeterRegistry registry;
    private EbpfEventloopStallTracer tracer;
    
    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        tracer = new EbpfEventloopStallTracer(registry, Duration.ofMillis(50));
    }

    @Test
    void shouldRecordStallWhenThresholdExceeded() throws InterruptedException {
        // Given
        Stopwatch sw = mock(Stopwatch.class);
        when(sw.elapsed(java.util.concurrent.TimeUnit.MILLISECONDS)).thenReturn(100L); // Over 50ms

        // When
        tracer.onUpdateSelectedKeyDuration(sw);

        // Then
        assertThat(registry.counter("eventloop.stall.count").count()).isEqualTo(1.0);
    }

    @Test
    void shouldNotRecordStallWhenBelowThreshold() {
        // Given
        Stopwatch sw = mock(Stopwatch.class);
        when(sw.elapsed(java.util.concurrent.TimeUnit.MILLISECONDS)).thenReturn(20L); // Under 50ms

        // When
        tracer.onUpdateSelectedKeyDuration(sw);

        // Then
        // The counter might not exist yet, or it's 0.
        assertThat(registry.find("eventloop.stall.count").counter())
                .satisfiesAnyOf(
                        counter -> assertThat(counter).isNull(),
                        counter -> assertThat(counter.count()).isEqualTo(0.0)
                );
    }
    
    @Test
    void shouldRecordOverdueTasks() {
        // When
        tracer.onScheduledTaskOverdue(150L, false);
        
        // Then
        assertThat(registry.counter("eventloop.stall.count").count()).isEqualTo(1.0);
    }
}
