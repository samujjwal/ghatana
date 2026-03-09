package com.ghatana.orchestrator.subsys;

import com.ghatana.orchestrator.queue.ExecutionQueue;
import com.ghatana.platform.observability.Metrics;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;

/**
 * Day 25 Implementation: Unit tests for TriggerListener
 */
class TriggerListenerTest {

    @Mock private ExecutionQueue executionQueue;
    @Mock private Metrics metrics;
    @Mock private Timer timer;
    @Mock private Counter counter;

    private TriggerListener triggerListener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(metrics.timer(anyString())).thenReturn(timer);
        when(metrics.counter(anyString())).thenReturn(counter);
        triggerListener = new TriggerListener(executionQueue, metrics);
    }

    @Test
    void testStartAndStop() {
        assertFalse(triggerListener.isRunning());

        CompletableFuture<?> startResult = triggerListener.start();
        assertNotNull(startResult);
        assertTrue(triggerListener.isRunning());

        CompletableFuture<?> stopResult = triggerListener.stop();
        assertNotNull(stopResult);
        assertFalse(triggerListener.isRunning());
    }

    @Test
    void testHandlePatternMatch() {
        // Start the listener
        triggerListener.start();
        
        // Mock successful enqueue
        when(executionQueue.enqueue(anyString(), anyString(), any(), anyString()))
                .thenReturn(Promise.of(null));

        // Handle pattern match
        String tenantId = "default-tenant";
        String pipelineId = "test-pipeline";
        String patternMatchId = "pattern-123";
        Object matchData = new Object();
        
        Promise<Void> result = triggerListener.handlePatternMatch(tenantId, pipelineId, patternMatchId, matchData);

        assertNotNull(result);
        
        // Verify enqueue was called with idempotency key
        String expectedIdempotencyKey = pipelineId + ":" + patternMatchId;
        verify(executionQueue).enqueue(tenantId, pipelineId, matchData, expectedIdempotencyKey);
        verify(metrics).timer("orch.triggers.received");
        verify(metrics).timer("orch.enqueued");
    }

    @Test
    void testHandlePatternMatchWhenNotRunning() {
        // Don't start the listener
        assertFalse(triggerListener.isRunning());
        
        Promise<Void> result = triggerListener.handlePatternMatch("tenant", "pipeline", "match", new Object());

        assertNotNull(result);
        // Should not call enqueue when not running
        verify(executionQueue, never()).enqueue(anyString(), anyString(), any(), anyString());
    }

    @Test
    void testIdempotencyKeyGeneration() {
        triggerListener.start();
        
        when(executionQueue.enqueue(anyString(), anyString(), any(), anyString()))
                .thenReturn(Promise.of(null));

        String tenantId = "default-tenant";
        String pipelineId = "my-pipeline";
        String patternMatchId = "match-456";
        
        triggerListener.handlePatternMatch(tenantId, pipelineId, patternMatchId, "data");

        String expectedKey = "my-pipeline:match-456";
        verify(executionQueue).enqueue(tenantId, pipelineId, "data", expectedKey);
        verify(metrics).timer("orch.triggers.received");
        verify(metrics).timer("orch.enqueued");
    }
}
