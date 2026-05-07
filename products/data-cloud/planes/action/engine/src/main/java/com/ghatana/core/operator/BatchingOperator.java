package com.ghatana.core.operator;

import com.ghatana.platform.domain.event.Event;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.activej.promise.SettablePromise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Operator that batches events for more efficient processing.
 *
 * <p><b>Purpose</b><br>
 * Collects multiple events into batches before processing, reducing per-event
 * overhead and enabling bulk operations. Useful for database writes, API calls,
 * and other operations that benefit from batching.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * BatchingOperator batcher = BatchingOperator.builder()
 *     .operator(bulkWriteOperator)
 *     .batchSize(100)
 *     .maxWaitTime(Duration.ofSeconds(5))
 *     .build();
 *
 * // Events are collected and processed in batches
 * OperatorResult result = batcher.process(event).getResult();
 * }</pre>
 *
 * <p><b>Batching Strategies</b><br>
 * <ul>
 *   <li><b>Size-based:</b> Flush when batch reaches batchSize</li>
 *   <li><b>Time-based:</b> Flush after maxWaitTime even if not full</li>
 *   <li><b>Hybrid:</b> Flush on whichever condition is met first</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * Thread-safe. Multiple threads can add events concurrently.
 *
 * <p><b>Performance</b><br>
 * Target: Reduce overhead by 50-90% for bulk operations
 * Actual: Depends on batch size and operation type
 *
 * <p><b>Trade-offs</b><br>
 * - Increased latency (events wait in batch)
 * - Reduced throughput overhead
 * - Memory usage proportional to batch size
 *
 * @see UnifiedOperator
 * @see OperatorComposer
 * @doc.type class
 * @doc.purpose Event batching for performance optimization
 * @doc.layer core
 * @doc.pattern Decorator
 */
public class BatchingOperator extends AbstractOperator {

    private static final Logger logger = LoggerFactory.getLogger(BatchingOperator.class);
    private static final ScheduledExecutorService flushExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = Thread.ofVirtual().name("aep-batching-flush", 0).unstarted(r);
        thread.setDaemon(true);
        return thread;
    });

    private final UnifiedOperator delegate;
    private final int batchSize;
    private final long maxWaitMillis;
    private final List<PendingEvent> currentBatch;
    private volatile long batchStartTime;
    private ScheduledFuture<?> scheduledFlush;

    /**
     * Create batching operator with builder.
     *
     * @param builder Builder with configuration
     */
    private BatchingOperator(Builder builder) {
        super(
            OperatorId.of("ghatana", "performance", "batching", "1.0.0"),
            OperatorType.STREAM,
            "Batching Operator",
            "Batches events for efficient bulk processing",
            List.of("batching", "performance", "buffering"),
            null
        );
        this.delegate = Objects.requireNonNull(builder.operator, "Operator required");
        this.batchSize = builder.batchSize;
        this.maxWaitMillis = builder.maxWaitTime.toMillis();
        this.currentBatch = new ArrayList<>(batchSize);
        this.batchStartTime = System.currentTimeMillis();
    }

    @Override
    public Promise<OperatorResult> process(Event event) {
        SettablePromise<OperatorResult> resultPromise = new SettablePromise<>();
        boolean flushNow = false;

        synchronized (currentBatch) {
            currentBatch.add(new PendingEvent(event, resultPromise));
            if (currentBatch.size() == 1) {
                scheduleFlush();
            }
            if (shouldFlushBatch()) {
                cancelScheduledFlush();
                flushNow = true;
            }
        }

        if (flushNow) {
            flushAsync();
        }

        return resultPromise;
    }

    /**
     * Check if batch should be flushed.
     *
     * @return true if batch should be flushed
     */
    private boolean shouldFlushBatch() {
        if (currentBatch.size() >= batchSize) {
            logger.debug("Flushing batch: size threshold reached ({} events)", batchSize);
            return true;
        }
        return false;
    }

    /**
     * Flush current batch to delegate operator.
     *
     * @return Promise of batch processing result
     */
    private Promise<OperatorResult> flushBatch() {
        List<PendingEvent> batchToProcess = drainBatch();
        if (batchToProcess.isEmpty()) {
            return Promise.of(OperatorResult.empty());
        }

        logger.info("Flushing batch of {} events", batchToProcess.size());
        return processBatchEvents(batchToProcess)
            .map(results -> {
                resolveBatch(batchToProcess, results);
                return aggregateResults(results);
            })
            .whenException(ex -> failBatch(batchToProcess, ex));
    }

    /**
     * Process list of events through delegate.
     *
     * @param events Events to process
     * @return Promise of combined results
     */
    private Promise<List<OperatorResult>> processBatchEvents(List<PendingEvent> events) {
        List<Promise<OperatorResult>> promises = new ArrayList<>();
        for (PendingEvent pendingEvent : events) {
            promises.add(delegate.process(pendingEvent.event()));
        }
        return Promises.toList(promises);
    }

    /**
     * Force flush of current batch.
     *
     * <p>Useful during shutdown or explicit flush operations.
     *
     * @return Promise of flush result
     */
    public Promise<OperatorResult> flush() {
        return flushBatch();
    }

    @Override
    protected Promise<Void> doInitialize(OperatorConfig config) {
        logger.debug("Initializing batching operator (batchSize={}, maxWait={}ms)",
                    batchSize, maxWaitMillis);
        return delegate.initialize(config);
    }

    @Override
    protected Promise<Void> doStart() {
        logger.info("Starting batching operator");
        return delegate.start();
    }

    @Override
    protected Promise<Void> doStop() {
        logger.info("Stopping batching operator, flushing remaining events");

        // Flush remaining events
        return flush()
            .then(result -> delegate.stop())
            .whenException(ex -> logger.error("Error flushing batch on stop", ex));
    }

    @Override
    public boolean isHealthy() {
        return delegate.isHealthy();
    }

    @Override
    public boolean isStateful() {
        return true;  // Maintains batch state
    }

    @Override
    public Event toEvent() {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("type", "operator.batching");
        payload.put("name", getName());
        payload.put("version", getVersion());
        payload.put("description", getDescription());

        var config = new java.util.HashMap<String, Object>();
        config.put("batchSize", batchSize);
        config.put("maxWaitMillis", maxWaitMillis);
        payload.put("config", config);

        payload.put("capabilities", java.util.List.of("event.batching", "throughput.optimization"));

        var headers = new java.util.HashMap<String, String>();
        headers.put("operatorId", getId().toString());
        headers.put("tenantId", getId().getNamespace());

        return com.ghatana.platform.domain.event.GEvent.builder()
                .type("operator.registered")
                .headers(headers)
                .payload(payload)
                .time(com.ghatana.platform.domain.event.EventTime.now())
                .build();
    }

    /**
     * Get current batch size.
     *
     * @return Number of events in current batch
     */
    public int getCurrentBatchSize() {
        synchronized (currentBatch) {
            return currentBatch.size();
        }
    }

    private void scheduleFlush() {
        cancelScheduledFlush();
        scheduledFlush = flushExecutor.schedule(this::flushAsync, maxWaitMillis, TimeUnit.MILLISECONDS);
    }

    private void cancelScheduledFlush() {
        if (scheduledFlush != null) {
            scheduledFlush.cancel(false);
            scheduledFlush = null;
        }
    }

    private List<PendingEvent> drainBatch() {
        synchronized (currentBatch) {
            if (currentBatch.isEmpty()) {
                return List.of();
            }

            List<PendingEvent> batchToProcess = new ArrayList<>(currentBatch);
            currentBatch.clear();
            cancelScheduledFlush();
            batchStartTime = System.currentTimeMillis();
            return batchToProcess;
        }
    }

    private void flushAsync() {
        flushBatch().whenException(ex -> logger.error("Error flushing batch asynchronously", ex));
    }

    private void resolveBatch(List<PendingEvent> batch, List<OperatorResult> results) {
        for (int i = 0; i < batch.size(); i++) {
            batch.get(i).promise().trySet(results.get(i));
        }
    }

    private void failBatch(List<PendingEvent> batch, Exception ex) {
        OperatorResult failed = OperatorResult.failed(ex.getMessage());
        for (PendingEvent pendingEvent : batch) {
            pendingEvent.promise().trySet(failed);
        }
    }

    private OperatorResult aggregateResults(List<OperatorResult> results) {
        OperatorResult.Builder builder = OperatorResult.builder().success();
        results.forEach(builder::mergeWith);
        return builder.build();
    }

    private record PendingEvent(Event event, SettablePromise<OperatorResult> promise) {}

    /**
     * Builder for BatchingOperator.
     */
    public static class Builder {
        private UnifiedOperator operator;
        private int batchSize = 100;
        private Duration maxWaitTime = Duration.ofSeconds(5);

        public Builder operator(UnifiedOperator operator) {
            this.operator = operator;
            return this;
        }

        public Builder batchSize(int batchSize) {
            if (batchSize <= 0) {
                throw new IllegalArgumentException("batchSize must be positive");
            }
            this.batchSize = batchSize;
            return this;
        }

        public Builder maxWaitTime(Duration maxWaitTime) {
            Objects.requireNonNull(maxWaitTime, "maxWaitTime required");
            if (maxWaitTime.isNegative() || maxWaitTime.isZero()) {
                throw new IllegalArgumentException("maxWaitTime must be positive");
            }
            this.maxWaitTime = maxWaitTime;
            return this;
        }

        public BatchingOperator build() {
            return new BatchingOperator(this);
        }
    }

    /**
     * Create builder for batching operator.
     *
     * @return New builder
     */
    public static Builder builder() {
        return new Builder();
    }
}
