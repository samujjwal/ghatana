package com.ghatana.audiovideo.observability;

import com.ghatana.platform.observability.MetricsCollector;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

/**
 * @doc.type class
 * @doc.purpose Unit tests for AudioVideoHealthService gRPC health checking (AV-P1-06) // GH-90000
 * @doc.layer test
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("AudioVideoHealthService Tests (AV-P1-06) [GH-90000]")
class AudioVideoHealthServiceTest {

    @Mock
    private MetricsCollector metricsCollector;

    private AudioVideoHealthService healthService;

    @BeforeEach
    void setUp() { // GH-90000
        healthService = new AudioVideoHealthService("stt-service", metricsCollector); // GH-90000
        lenient().doNothing().when(metricsCollector).incrementCounter(anyString(), any(String[].class)); // GH-90000
    }

    @Test
    @DisplayName("Returns SERVING when no checks registered [GH-90000]")
    void shouldReturnServingWithNoChecks() { // GH-90000
        CapturingObserver observer = new CapturingObserver(); // GH-90000

        healthService.check(HealthCheckRequest.newBuilder().build(), observer); // GH-90000

        assertThat(observer.responses).hasSize(1); // GH-90000
        assertThat(observer.responses.get(0).getStatus()) // GH-90000
                .isEqualTo(HealthCheckResponse.ServingStatus.SERVING); // GH-90000
    }

    @Test
    @DisplayName("Returns SERVING when all checks pass [GH-90000]")
    void shouldReturnServingWhenAllChecksPass() { // GH-90000
        healthService.registerCheck("channel", () -> true); // GH-90000
        healthService.registerCheck("model-loaded", () -> true); // GH-90000

        CapturingObserver observer = new CapturingObserver(); // GH-90000
        healthService.check(HealthCheckRequest.newBuilder().build(), observer); // GH-90000

        assertThat(observer.responses.get(0).getStatus()) // GH-90000
                .isEqualTo(HealthCheckResponse.ServingStatus.SERVING); // GH-90000
    }

    @Test
    @DisplayName("Returns NOT_SERVING when any check fails [GH-90000]")
    void shouldReturnNotServingWhenAnyCheckFails() { // GH-90000
        healthService.registerCheck("channel", () -> true); // GH-90000
        healthService.registerCheck("model-loaded", () -> false); // failing // GH-90000

        CapturingObserver observer = new CapturingObserver(); // GH-90000
        healthService.check(HealthCheckRequest.newBuilder().build(), observer); // GH-90000

        assertThat(observer.responses.get(0).getStatus()) // GH-90000
                .isEqualTo(HealthCheckResponse.ServingStatus.NOT_SERVING); // GH-90000
    }

    @Test
    @DisplayName("Returns NOT_SERVING when check throws exception [GH-90000]")
    void shouldReturnNotServingWhenCheckThrows() { // GH-90000
        healthService.registerCheck("crashing-check", () -> { // GH-90000
            throw new RuntimeException("dependency unavailable [GH-90000]");
        });

        CapturingObserver observer = new CapturingObserver(); // GH-90000
        healthService.check(HealthCheckRequest.newBuilder().build(), observer); // GH-90000

        assertThat(observer.responses.get(0).getStatus()) // GH-90000
                .isEqualTo(HealthCheckResponse.ServingStatus.NOT_SERVING); // GH-90000
    }

    @Test
    @DisplayName("Returns NOT_SERVING after setNotServing() is called [GH-90000]")
    void shouldReturnNotServingAfterShutdown() { // GH-90000
        healthService.registerCheck("all-good", () -> true); // GH-90000

        healthService.setNotServing(); // GH-90000

        CapturingObserver observer = new CapturingObserver(); // GH-90000
        healthService.check(HealthCheckRequest.newBuilder().build(), observer); // GH-90000

        assertThat(observer.responses.get(0).getStatus()) // GH-90000
                .isEqualTo(HealthCheckResponse.ServingStatus.NOT_SERVING); // GH-90000
    }

    @Test
    @DisplayName("Emits metric counter on every Check call [GH-90000]")
    void shouldEmitMetricOnEveryCheck() { // GH-90000
        CapturingObserver observer = new CapturingObserver(); // GH-90000
        healthService.check(HealthCheckRequest.newBuilder().build(), observer); // GH-90000

        verify(metricsCollector).incrementCounter( // GH-90000
                "av.health.check", "service", "stt-service", "status", "serving");
    }

    @Test
    @DisplayName("getCheckResults returns correct status map [GH-90000]")
    void shouldReturnCorrectCheckResults() { // GH-90000
        healthService.registerCheck("ok-check", () -> true); // GH-90000
        healthService.registerCheck("fail-check", () -> false); // GH-90000

        Map<String, Boolean> results = healthService.getCheckResults(); // GH-90000

        assertThat(results).containsEntry("ok-check", true); // GH-90000
        assertThat(results).containsEntry("fail-check", false); // GH-90000
    }

    // ── helpers ────────────────────────────────────────────────────────────

    private static class CapturingObserver implements StreamObserver<HealthCheckResponse> {
        final List<HealthCheckResponse> responses = new ArrayList<>(); // GH-90000
        Throwable error;
        boolean completed;

        @Override
        public void onNext(HealthCheckResponse value) { // GH-90000
            responses.add(value); // GH-90000
        }

        @Override
        public void onError(Throwable t) { // GH-90000
            error = t;
        }

        @Override
        public void onCompleted() { // GH-90000
            completed = true;
        }
    }
}

