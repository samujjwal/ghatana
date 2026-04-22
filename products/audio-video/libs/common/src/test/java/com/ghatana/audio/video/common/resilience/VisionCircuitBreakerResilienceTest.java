package com.ghatana.audio.video.common.resilience;

import com.ghatana.media.resilience.CircuitBreakerVisionEngine;
import com.ghatana.media.vision.api.VisionEngine;
import com.ghatana.media.common.ImageData;
import com.ghatana.media.common.ColorSpace;
import com.ghatana.media.common.ImageFormat;
import com.ghatana.media.common.EngineMetrics;
import com.ghatana.media.vision.api.DetectionResult;
import com.ghatana.media.vision.api.DetectionOptions;
import com.ghatana.platform.resilience.CircuitBreaker;
import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Resilience pattern tests for the Vision circuit breaker (AV-P2-02). // GH-90000
 *
 * <p>Verifies the circuit breaker open → half-open → closed lifecycle and that
 * degraded empty responses are returned when the circuit is open (cascade failure prevention). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Circuit breaker resilience tests for Vision engine cascading failure prevention (AV-P2-02) // GH-90000
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("Vision Engine Circuit Breaker Resilience Tests (AV-P2-02) [GH-90000]")
class VisionCircuitBreakerResilienceTest {

    @Mock
    private VisionEngine mockEngine;

    private Eventloop eventloop;
    private CircuitBreaker circuitBreaker;
    private CircuitBreakerVisionEngine protectedEngine;

    private static final ImageData DUMMY_IMAGE =
            new ImageData(new byte[]{1, 2, 3, 4}, 2, 2, ImageFormat.JPEG, ColorSpace.RGB); // GH-90000

    @BeforeEach
    void setUp() { // GH-90000
        eventloop = Eventloop.create(); // GH-90000

        circuitBreaker = CircuitBreaker.builder("vision-test-circuit [GH-90000]")
                .failureThreshold(3) // GH-90000
                .successThreshold(1) // GH-90000
                .resetTimeout(java.time.Duration.ofMillis(200)) // fast for tests // GH-90000
                .maxBackoff(java.time.Duration.ofSeconds(2)) // GH-90000
                .backoffMultiplier(1.0) // GH-90000
                .build(); // GH-90000

        protectedEngine = new CircuitBreakerVisionEngine(mockEngine, eventloop, circuitBreaker); // GH-90000

        // Default stub for getMetrics (always needed by getMetrics()) // GH-90000
        lenient().when(mockEngine.getMetrics()).thenReturn( // GH-90000
                new EngineMetrics(0, 0, 0.0, 0, 0)); // GH-90000
    }

    @Test
    @DisplayName("Circuit stays CLOSED and delegates to engine on success [GH-90000]")
    void shouldDelegateToEngineWhenCircuitClosed() { // GH-90000
        var emptyResult = new DetectionResult(List.of(), 0, 0, 0L, "ok"); // GH-90000
        when(mockEngine.detect(any(), any())).thenReturn(emptyResult); // GH-90000

        var result = protectedEngine.detect(DUMMY_IMAGE, DetectionOptions.defaults()); // GH-90000

        assertThat(protectedEngine.getCircuitBreakerState()).isEqualTo(CircuitBreaker.State.CLOSED); // GH-90000
        assertThat(result.modelId()).isEqualTo("ok [GH-90000]");
    }

    @Test
    @DisplayName("Circuit opens after reaching failure threshold [GH-90000]")
    void shouldOpenAfterFailureThreshold() { // GH-90000
        when(mockEngine.detect(any(), any())) // GH-90000
                .thenThrow(new RuntimeException("upstream failure #1 [GH-90000]"))
                .thenThrow(new RuntimeException("upstream failure #2 [GH-90000]"))
                .thenThrow(new RuntimeException("upstream failure #3 [GH-90000]"));

        for (int i = 0; i < 3; i++) { // GH-90000
            try {
                protectedEngine.detect(DUMMY_IMAGE, DetectionOptions.defaults()); // GH-90000
            } catch (RuntimeException ignored) { // GH-90000
                // each individual failure propagates — that's expected
            }
        }

        assertThat(protectedEngine.getCircuitBreakerState()).isEqualTo(CircuitBreaker.State.OPEN); // GH-90000
    }

    @Test
    @DisplayName("Returns degraded empty result when circuit is OPEN (cascade prevention) [GH-90000]")
    void shouldReturnDegradedResultWhenCircuitOpen() { // GH-90000
        when(mockEngine.detect(any(), any())) // GH-90000
                .thenThrow(new RuntimeException("service down [GH-90000]"));

        // Trip the circuit
        for (int i = 0; i < 3; i++) { // GH-90000
            try {
                protectedEngine.detect(DUMMY_IMAGE, DetectionOptions.defaults()); // GH-90000
            } catch (RuntimeException ignored) {} // GH-90000
        }
        assertThat(protectedEngine.getCircuitBreakerState()).isEqualTo(CircuitBreaker.State.OPEN); // GH-90000

        // Next call should fast-fail with degraded response (not propagate exception) // GH-90000
        var result = protectedEngine.detect(DUMMY_IMAGE, DetectionOptions.defaults()); // GH-90000

        assertThat(result).isNotNull(); // GH-90000
        assertThat(result.objects()).isEmpty(); // GH-90000
        assertThat(result.modelId()).isEqualTo("degraded [GH-90000]");
    }

    @Test
    @DisplayName("Circuit transitions to CLOSED after successful probe in HALF_OPEN [GH-90000]")
    void shouldRecoverAfterHalfOpenProbeSucceeds() throws InterruptedException { // GH-90000
        when(mockEngine.detect(any(), any())) // GH-90000
                .thenThrow(new RuntimeException("down [GH-90000]"))
                .thenThrow(new RuntimeException("down [GH-90000]"))
                .thenThrow(new RuntimeException("down [GH-90000]"))
                // Recovery call succeeds
                .thenReturn(new DetectionResult(List.of(), 0, 0, 0L, "recovered")); // GH-90000

        for (int i = 0; i < 3; i++) { // GH-90000
            try {
                protectedEngine.detect(DUMMY_IMAGE, DetectionOptions.defaults()); // GH-90000
            } catch (RuntimeException ignored) {} // GH-90000
        }
        assertThat(protectedEngine.getCircuitBreakerState()).isEqualTo(CircuitBreaker.State.OPEN); // GH-90000

        // Wait for reset timeout to expire → circuit becomes HALF_OPEN
        Thread.sleep(300); // GH-90000

        // Single probe call succeeds → circuit closes
        var result = protectedEngine.detect(DUMMY_IMAGE, DetectionOptions.defaults()); // GH-90000

        assertThat(result.modelId()).isEqualTo("recovered [GH-90000]");
        assertThat(protectedEngine.getCircuitBreakerState()).isEqualTo(CircuitBreaker.State.CLOSED); // GH-90000
    }

    @Test
    @DisplayName("getMetrics merges circuit breaker rejections into engine metrics [GH-90000]")
    void shouldMergeCircuitBreakerRejectionsIntoMetrics() { // GH-90000
        when(mockEngine.getMetrics()).thenReturn( // GH-90000
                new EngineMetrics(10, 0, 50.0, 1, 0)); // GH-90000
        when(mockEngine.detect(any(), any())) // GH-90000
                .thenThrow(new RuntimeException("down [GH-90000]"))
                .thenThrow(new RuntimeException("down [GH-90000]"))
                .thenThrow(new RuntimeException("down [GH-90000]"));

        for (int i = 0; i < 3; i++) { // GH-90000
            try {
                protectedEngine.detect(DUMMY_IMAGE, DetectionOptions.defaults()); // GH-90000
            } catch (RuntimeException ignored) {} // GH-90000
        }
        // One additional call after circuit opens → rejection (returns degraded, no exception) // GH-90000
        protectedEngine.detect(DUMMY_IMAGE, DetectionOptions.defaults()); // GH-90000

        EngineMetrics metrics = protectedEngine.getMetrics(); // GH-90000

        assertThat(metrics.requestCount()).isEqualTo(10); // GH-90000
        assertThat(metrics.errorCount()).isGreaterThanOrEqualTo(3); // GH-90000
        assertThat(metrics.activeRequests()).isGreaterThanOrEqualTo(1); // rejections stored here // GH-90000
    }
}





