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

@DisplayName("eBPF Eventloop Stall Tracer Tests [GH-90000]")
class EbpfEventloopStallTracerTest {

    private MeterRegistry registry;
    private EbpfEventloopStallTracer tracer;

    @BeforeEach
    void setUp() { // GH-90000
        registry = new SimpleMeterRegistry(); // GH-90000
        tracer = new EbpfEventloopStallTracer(registry, Duration.ofMillis(50)); // GH-90000
    }

    @Test
    void shouldRecordStallWhenThresholdExceeded() throws InterruptedException { // GH-90000
        // Given
        Stopwatch sw = mock(Stopwatch.class); // GH-90000
        when(sw.elapsed(java.util.concurrent.TimeUnit.MILLISECONDS)).thenReturn(100L); // Over 50ms // GH-90000

        // When
        tracer.onUpdateSelectedKeyDuration(sw); // GH-90000

        // Then
        assertThat(registry.counter("eventloop.stall.count [GH-90000]").count()).isEqualTo(1.0);
    }

    @Test
    void shouldNotRecordStallWhenBelowThreshold() { // GH-90000
        // Given
        Stopwatch sw = mock(Stopwatch.class); // GH-90000
        when(sw.elapsed(java.util.concurrent.TimeUnit.MILLISECONDS)).thenReturn(20L); // Under 50ms // GH-90000

        // When
        tracer.onUpdateSelectedKeyDuration(sw); // GH-90000

        // Then
        // The counter might not exist yet, or it's 0.
        assertThat(registry.find("eventloop.stall.count [GH-90000]").counter())
                .satisfiesAnyOf( // GH-90000
                        counter -> assertThat(counter).isNull(), // GH-90000
                        counter -> assertThat(counter.count()).isEqualTo(0.0) // GH-90000
                );
    }

    @Test
    void shouldRecordOverdueTasks() { // GH-90000
        // When
        tracer.onScheduledTaskOverdue(150L, false); // GH-90000

        // Then
        assertThat(registry.counter("eventloop.stall.count [GH-90000]").count()).isEqualTo(1.0);
    }
}
