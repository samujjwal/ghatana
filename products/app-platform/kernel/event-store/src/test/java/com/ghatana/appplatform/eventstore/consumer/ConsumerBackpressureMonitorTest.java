package com.ghatana.appplatform.eventstore.consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ConsumerBackpressureMonitor}.
 *
 * <p>Tests the backpressure algorithm via the package-private {@code checkBackpressure()}
 * method so we can exercise the logic without running a Kafka broker.
 *
 * @doc.type class
 * @doc.purpose Unit tests for consumer backpressure auto-pause/resume logic
 * @doc.layer product
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConsumerBackpressureMonitor — Unit Tests")
class ConsumerBackpressureMonitorTest {

    @Mock(extraInterfaces = {})
    private EventConsumerBase consumer;

    private ConsumerBackpressureMonitor monitor;

    @AfterEach
    void stopMonitor() {
        if (monitor != null) {
            monitor.stop();
        }
    }

    @Test
    @DisplayName("checkBackpressure_pause — pauses consumer when queue exceeds threshold")
    void pausesWhenQueueExceedsThreshold() {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(100);
        for (int i = 0; i < 80; i++) queue.add("item-" + i);

        monitor = new ConsumerBackpressureMonitor(consumer, queue, 70, 20, 100);
        monitor.checkBackpressure();

        assertThat(monitor.isBackpressureActive()).isTrue();
        verify(consumer).pause();
    }

    @Test
    @DisplayName("checkBackpressure_noAction — does nothing when queue is below pause threshold")
    void noActionBelowPauseThreshold() {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(100);
        for (int i = 0; i < 30; i++) queue.add("item-" + i);

        monitor = new ConsumerBackpressureMonitor(consumer, queue, 70, 20, 100);
        monitor.checkBackpressure();

        assertThat(monitor.isBackpressureActive()).isFalse();
        verifyNoInteractions(consumer);
    }

    @Test
    @DisplayName("checkBackpressure_resume — resumes consumer when queue drains below resumeThreshold")
    void resumesWhenQueueDrainsBelowThreshold() {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(100);
        for (int i = 0; i < 80; i++) queue.add("item-" + i);

        monitor = new ConsumerBackpressureMonitor(consumer, queue, 70, 20, 100);
        // First: activate backpressure
        monitor.checkBackpressure();
        assertThat(monitor.isBackpressureActive()).isTrue();

        // Drain queue below resumeThreshold
        queue.clear();
        for (int i = 0; i < 10; i++) queue.add("item-" + i);

        // Second: deactivate backpressure
        monitor.checkBackpressure();

        assertThat(monitor.isBackpressureActive()).isFalse();
        verify(consumer).resume();
    }

    @Test
    @DisplayName("checkBackpressure_hysteresis — does not resume above resumeThreshold when paused")
    void hysteresisPreventsPrematureResume() {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(100);
        for (int i = 0; i < 80; i++) queue.add("item-" + i);

        monitor = new ConsumerBackpressureMonitor(consumer, queue, 70, 20, 100);
        monitor.checkBackpressure(); // pause activated

        // Drain to 40 — still above resumeThreshold of 20
        queue.clear();
        for (int i = 0; i < 40; i++) queue.add("item-" + i);
        monitor.checkBackpressure(); // should NOT resume

        assertThat(monitor.isBackpressureActive()).isTrue();
        verify(consumer, never()).resume();
    }

    @Test
    @DisplayName("onBackpressureChange_callback — invoked on state transitions")
    void callbackInvokedOnTransitions() {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(100);
        for (int i = 0; i < 80; i++) queue.add("item-" + i);

        AtomicBoolean lastState = new AtomicBoolean();
        monitor = new ConsumerBackpressureMonitor(consumer, queue, 70, 20, 100)
                .withBackpressureChangeCallback(lastState::set);

        monitor.checkBackpressure(); // activate
        assertThat(lastState.get()).isTrue();

        queue.clear();
        monitor.checkBackpressure(); // deactivate
        assertThat(lastState.get()).isFalse();
    }

    @Test
    @DisplayName("constructor_invalidThresholds — throws when resumeThreshold >= pauseThreshold")
    void invalidThresholdsThrow() {
        BlockingQueue<String> queue = new ArrayBlockingQueue<>(100);
        assertThatThrownBy(() ->
            new ConsumerBackpressureMonitor(consumer, queue, 50, 50, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resumeThreshold");
    }
}
