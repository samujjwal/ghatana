package com.ghatana.orchestrator.subsys;

import com.ghatana.orchestrator.queue.ExecutionQueue;
import com.ghatana.platform.observability.Metrics;
import io.activej.promise.Promise;
import io.activej.service.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TriggerListener subscribes to PatternMatch events from EventLog and enqueues
 * pipeline execution jobs in the orchestrator.
 * 
 * Day 25 Implementation: Pattern match to pipeline execution bridge
 */
public class TriggerListener implements Service {

    private final ExecutionQueue executionQueue;
    private final Metrics metrics;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public TriggerListener(ExecutionQueue executionQueue, Metrics metrics) {
        this.executionQueue = executionQueue;
        this.metrics = metrics;
    }

    @Override
    public java.util.concurrent.CompletableFuture<?> start() {
        if (isRunning.compareAndSet(false, true)) {
            // Set up EventLog subscription for pattern match events
            // In production, this would connect to EventLog service and subscribe to:
            // - Event type: "PatternMatch"
            // - Stream: "pattern-engine-matches"
            // - Consumer group: "orchestrator-triggers"
            
            // For now, we mark as started and ready to receive events
            // The subscription would be:
            // eventLogClient.subscribe("pattern-engine-matches", "PatternMatch", 
            //     event -> handlePatternMatch(event));
            
            metrics.counter("trigger_listener.started").increment();
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    @Override
    public java.util.concurrent.CompletableFuture<?> stop() {
        if (isRunning.compareAndSet(true, false)) {
            // Clean up EventLog subscription
            // In production: eventLogClient.unsubscribe(subscriptionId);
            // This ensures graceful shutdown and no message loss
            
            metrics.counter("trigger_listener.stopped").increment();
            return java.util.concurrent.CompletableFuture.completedFuture(null);
        }
        return java.util.concurrent.CompletableFuture.completedFuture(null);
    }

    /**
     * Check if the listener is running.
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * Handle incoming pattern match event.
     * Creates idempotency key from pipelineId and patternMatchId.
     */
    public Promise<Void> handlePatternMatch(String tenantId, String pipelineId, String patternMatchId, Object matchData) {
        if (!isRunning.get()) {
            return Promise.ofException(new IllegalStateException("TriggerListener is not running"));
        }

        // Create idempotency key: {pipelineId}:{patternMatchId}
        String idempotencyKey = pipelineId + ":" + patternMatchId;
        
        return executionQueue.enqueue(tenantId, pipelineId, matchData, idempotencyKey)
                .whenResult(() -> {
                    // Record successful trigger
                    metrics.timer("orch.triggers.received").record(0L, java.util.concurrent.TimeUnit.MILLISECONDS);
                    metrics.timer("orch.enqueued").record(0L, java.util.concurrent.TimeUnit.MILLISECONDS);
                })
                .whenException(error -> {
                    // Log failed enqueue attempt (would use proper audit logging in real implementation)
                    System.err.println("Failed to enqueue pipeline execution: " + error.getMessage());
                });
    }


}
