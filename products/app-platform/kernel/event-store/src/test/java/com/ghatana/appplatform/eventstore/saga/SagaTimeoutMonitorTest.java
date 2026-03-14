package com.ghatana.appplatform.eventstore.saga;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SagaTimeoutMonitor}.
 *
 * @doc.type class
 * @doc.purpose Unit tests for saga step timeout detection and compensation
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SagaTimeoutMonitor — Unit Tests")
class SagaTimeoutMonitorTest {

    @Mock
    private SagaStore sagaStore;

    @Mock
    private SagaOrchestrator orchestrator;

    private SagaTimeoutMonitor monitor;

    @AfterEach
    void stopMonitor() {
        if (monitor != null && monitor.isRunning()) {
            monitor.stop();
        }
    }

    private static SagaInstance pendingSaga(String id) {
        return new SagaInstance(id, "payment-saga", 1, "t-1", "order-1",
                SagaState.STEP_PENDING, 0, 0, null,
                Instant.now().minusSeconds(600), Instant.now().minusSeconds(600));
    }

    @Test
    @DisplayName("checkForTimeouts — calls onStepFailed for each timed-out saga")
    void checkForTimeoutsTriggersCompensation() {
        SagaInstance timedOut1 = pendingSaga("saga-1");
        SagaInstance timedOut2 = pendingSaga("saga-2");
        when(sagaStore.findTimedOutInstances(any(Instant.class)))
                .thenReturn(List.of(timedOut1, timedOut2));

        monitor = new SagaTimeoutMonitor(sagaStore, orchestrator,
                Duration.ofMinutes(5), Duration.ofSeconds(30));
        monitor.checkForTimeouts();

        verify(orchestrator).onStepFailed(eq("saga-1"), contains("timed out"));
        verify(orchestrator).onStepFailed(eq("saga-2"), contains("timed out"));
    }

    @Test
    @DisplayName("checkForTimeouts — no compensation when no timed-out sagas")
    void checkForTimeoutsNoOpWhenEmpty() {
        when(sagaStore.findTimedOutInstances(any(Instant.class))).thenReturn(List.of());

        monitor = new SagaTimeoutMonitor(sagaStore, orchestrator,
                Duration.ofMinutes(5), Duration.ofSeconds(30));
        monitor.checkForTimeouts();

        verifyNoInteractions(orchestrator);
    }

    @Test
    @DisplayName("checkForTimeouts — store exception is logged, orchestrator not called")
    void checkForTimeoutsStoreErrorSuppressed() {
        when(sagaStore.findTimedOutInstances(any(Instant.class)))
                .thenThrow(new RuntimeException("DB error"));

        monitor = new SagaTimeoutMonitor(sagaStore, orchestrator,
                Duration.ofMinutes(5), Duration.ofSeconds(30));
        monitor.checkForTimeouts(); // must not propagate

        verifyNoInteractions(orchestrator);
    }

    @Test
    @DisplayName("checkForTimeouts — orchestrator error on one saga does not block others")
    void checkForTimeoutsOrchestratorErrorIsolated() {
        SagaInstance s1 = pendingSaga("saga-err");
        SagaInstance s2 = pendingSaga("saga-ok");
        when(sagaStore.findTimedOutInstances(any(Instant.class))).thenReturn(List.of(s1, s2));
        doThrow(new RuntimeException("orchestrator fail"))
                .when(orchestrator).onStepFailed(eq("saga-err"), any());

        monitor = new SagaTimeoutMonitor(sagaStore, orchestrator,
                Duration.ofMinutes(5), Duration.ofSeconds(30));
        monitor.checkForTimeouts();

        verify(orchestrator).onStepFailed(eq("saga-ok"), any());
    }

    @Test
    @DisplayName("start_and_stop — lifecycle flags are correct")
    void startAndStopLifecycle() throws InterruptedException {
        monitor = new SagaTimeoutMonitor(sagaStore, orchestrator,
                Duration.ofMinutes(5), Duration.ofSeconds(60));
        when(sagaStore.findTimedOutInstances(any())).thenReturn(List.of());

        assertThat(monitor.isRunning()).isFalse();
        monitor.start();
        assertThat(monitor.isRunning()).isTrue();
        monitor.stop();
        assertThat(monitor.isRunning()).isFalse();
    }

    @Test
    @DisplayName("start_duplicateCall — second start is silently ignored")
    void startDuplicateCallIgnored() {
        monitor = new SagaTimeoutMonitor(sagaStore, orchestrator,
                Duration.ofMinutes(5), Duration.ofSeconds(60));
        when(sagaStore.findTimedOutInstances(any())).thenReturn(List.of());

        monitor.start();
        monitor.start(); // must not throw
        assertThat(monitor.isRunning()).isTrue();
    }
}
