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
 * {@link EventloopTestBase#runPromise(java.util.concurrent.Callable)}. 
 */
@DisplayName("TriggerListener")
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
    void setUp() { 
        MockitoAnnotations.openMocks(this); 
        when(metrics.timer(anyString())).thenReturn(timer); 
        when(metrics.counter(anyString())).thenReturn(counter); 
        triggerListener = new TriggerListener(executionQueue, metrics); 
    }

    @Test
    @DisplayName("start() marks listener as running; stop() unmarks it")
    void testStartAndStop() { 
        assertFalse(triggerListener.isRunning()); 

        runBlocking(() -> triggerListener.start()); 
        assertTrue(triggerListener.isRunning()); 

        runBlocking(() -> triggerListener.stop()); 
        assertFalse(triggerListener.isRunning()); 
    }

    @Test
    @DisplayName("handlePatternMatch() enqueues with correct idempotency key")
    void testHandlePatternMatch() { 
        triggerListener.start(); 

        when(executionQueue.enqueue(anyString(), anyString(), any(), anyString())) 
                .thenReturn(Promise.of(null)); 

        String tenantId = "default-tenant";
        String pipelineId = "test-pipeline";
        String patternMatchId = "pattern-123";
        Object matchData = new Object(); 

        runPromise(() -> triggerListener.handlePatternMatch(tenantId, pipelineId, patternMatchId, matchData)); 

        String expectedIdempotencyKey = pipelineId + ":" + patternMatchId;
        verify(executionQueue).enqueue(tenantId, pipelineId, matchData, expectedIdempotencyKey); 
        verify(metrics).timer("orch.triggers.received");
        verify(metrics).timer("orch.enqueued");
    }

    @Test
    @DisplayName("handlePatternMatch() is a no-op when listener is not running")
    void testHandlePatternMatchWhenNotRunning() { 
        assertFalse(triggerListener.isRunning()); 

        runPromise(() -> triggerListener.handlePatternMatch("tenant", "pipeline", "match", new Object())); 

        verify(executionQueue, never()).enqueue(anyString(), anyString(), any(), anyString()); 
    }

    @Test
    @DisplayName("idempotency key is constructed as pipelineId:patternMatchId")
    void testIdempotencyKeyGeneration() { 
        triggerListener.start(); 

        when(executionQueue.enqueue(anyString(), anyString(), any(), anyString())) 
                .thenReturn(Promise.of(null)); 

        String tenantId = "default-tenant";
        String pipelineId = "my-pipeline";
        String patternMatchId = "match-456";

        runPromise(() -> triggerListener.handlePatternMatch(tenantId, pipelineId, patternMatchId, "data")); 

        String expectedKey = "my-pipeline:match-456";
        verify(executionQueue).enqueue(tenantId, pipelineId, "data", expectedKey); 
        verify(metrics).timer("orch.triggers.received");
        verify(metrics).timer("orch.enqueued");
    }
}
