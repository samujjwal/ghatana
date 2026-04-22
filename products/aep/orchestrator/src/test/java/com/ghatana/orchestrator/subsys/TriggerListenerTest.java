package com.ghatana.orchestrator.subsys;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ghatana.orchestrator.queue.ExecutionQueue;
import com.ghatana.platform.observability.Metrics;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link TriggerListener}.
 *
 * <p>All Promise-returning methods are executed within a managed ActiveJ Eventloop via
 * {@link EventloopTestBase#runPromise(java.util.concurrent.Callable)}. // GH-90000
 */
@DisplayName("TriggerListener [GH-90000]")
class TriggerListenerTest extends EventloopTestBase {

    @Mock
    private ExecutionQueue executionQueue;

    @Mock
    private Metrics metrics;

    @Mock
    private Timer timer;

    @Mock
    private Counter counter;

    private TriggerListener triggerListener;

    @BeforeEach
    void setUp() { // GH-90000
        MockitoAnnotations.openMocks(this); // GH-90000
        when(metrics.timer(anyString())).thenReturn(timer); // GH-90000
        when(metrics.counter(anyString())).thenReturn(counter); // GH-90000
        triggerListener = new TriggerListener(executionQueue, metrics); // GH-90000
    }

    @Test
    @DisplayName("start() marks listener as running; stop() unmarks it [GH-90000]")
    void testStartAndStop() { // GH-90000
        assertFalse(triggerListener.isRunning()); // GH-90000

        runBlocking(() -> triggerListener.start()); // GH-90000
        assertTrue(triggerListener.isRunning()); // GH-90000

        runBlocking(() -> triggerListener.stop()); // GH-90000
        assertFalse(triggerListener.isRunning()); // GH-90000
    }

    @Test
    @DisplayName("handlePatternMatch() enqueues with correct idempotency key [GH-90000]")
    void testHandlePatternMatch() { // GH-90000
        triggerListener.start(); // GH-90000

        when(executionQueue.enqueue(anyString(), anyString(), any(), anyString())) // GH-90000
                .thenReturn(Promise.of(null)); // GH-90000

        String tenantId = "default-tenant";
        String pipelineId = "test-pipeline";
        String patternMatchId = "pattern-123";
        Object matchData = new Object(); // GH-90000

        runPromise(() -> triggerListener.handlePatternMatch(tenantId, pipelineId, patternMatchId, matchData)); // GH-90000

        String expectedIdempotencyKey = pipelineId + ":" + patternMatchId;
        verify(executionQueue).enqueue(tenantId, pipelineId, matchData, expectedIdempotencyKey); // GH-90000
        verify(metrics).timer("orch.triggers.received [GH-90000]");
        verify(metrics).timer("orch.enqueued [GH-90000]");
    }

    @Test
    @DisplayName("handlePatternMatch() is a no-op when listener is not running [GH-90000]")
    void testHandlePatternMatchWhenNotRunning() { // GH-90000
        assertFalse(triggerListener.isRunning()); // GH-90000

        runPromise(() -> triggerListener.handlePatternMatch("tenant", "pipeline", "match", new Object())); // GH-90000

        verify(executionQueue, never()).enqueue(anyString(), anyString(), any(), anyString()); // GH-90000
    }

    @Test
    @DisplayName("idempotency key is constructed as pipelineId:patternMatchId [GH-90000]")
    void testIdempotencyKeyGeneration() { // GH-90000
        triggerListener.start(); // GH-90000

        when(executionQueue.enqueue(anyString(), anyString(), any(), anyString())) // GH-90000
                .thenReturn(Promise.of(null)); // GH-90000

        String tenantId = "default-tenant";
        String pipelineId = "my-pipeline";
        String patternMatchId = "match-456";

        runPromise(() -> triggerListener.handlePatternMatch(tenantId, pipelineId, patternMatchId, "data")); // GH-90000

        String expectedKey = "my-pipeline:match-456";
        verify(executionQueue).enqueue(tenantId, pipelineId, "data", expectedKey); // GH-90000
        verify(metrics).timer("orch.triggers.received [GH-90000]");
        verify(metrics).timer("orch.enqueued [GH-90000]");
    }
}
