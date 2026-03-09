package com.ghatana.core.operator;

import com.ghatana.platform.domain.domain.event.Event;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    private final UnifiedOperator delegate;
    private final int batchSize;
    private final long maxWaitMillis;
    private final List<Event> currentBatch;
    private volatile long batchStartTime;

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
        synchronized (currentBatch) {
            currentBatch.add(event);

            // Check if we should flush
            boolean shouldFlush = shouldFlushBatch();

            if (shouldFlush) {
                return flushBatch();
            } else {
                // Event added to batch, return success
                // Note: In production, this should trigger async flush
                return Promise.of(OperatorResult.empty());
            }
        }
    }

    /**
     * Check if batch should be flushed.
     *
     * @return true if batch should be flushed
     */
    private boolean shouldFlushBatch() {
        // Size-based flush
        if (currentBatch.size() >= batchSize) {
            logger.debug("Flushing batch: size threshold reached ({} events)", batchSize);
            return true;
        }

        // Time-based flush
        long elapsedMillis = System.currentTimeMillis() - batchStartTime;
        if (elapsedMillis >= maxWaitMillis) {
            logger.debug("Flushing batch: time threshold reached ({}ms)", elapsedMillis);
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
        if (currentBatch.isEmpty()) {
            return Promise.of(OperatorResult.empty());
        }

        // Copy current batch
        List<Event> batchToProcess = new ArrayList<>(currentBatch);
        currentBatch.clear();
        batchStartTime = System.currentTimeMillis();

        logger.info("Flushing batch of {} events", batchToProcess.size());

        // Process batch through delegate
        return processBatchEvents(batchToProcess);
    }

    /**
     * Process list of events through delegate.
     *
     * @param events Events to process
     * @return Promise of combined results
     */
    private Promise<OperatorResult> processBatchEvents(List<Event> events) {
        // Process all events through delegate
        List<Promise<OperatorResult>> promises = new ArrayList<>();
        for (Event event : events) {
            promises.add(delegate.process(event));
        }

        // Wait for all to complete and combine results
        return Promises.toList(promises)
            .map(results -> {
                List<Event> allOutputs = new ArrayList<>();
                boolean allSuccess = true;

                for (OperatorResult result : results) {
                    if (result.isSuccess()) {
                        allOutputs.addAll(result.getOutputEvents());
                    } else {
                        allSuccess = false;
                    }
                }

                if (allSuccess) {
                    return OperatorResult.of(allOutputs);
                } else {
                    return OperatorResult.failed("Some batch operations failed");
                }
            });
    }

    /**
     * Force flush of current batch.
     *
     * <p>Useful during shutdown or explicit flush operations.
     *
     * @return Promise of flush result
     */
    public Promise<OperatorResult> flush() {
        synchronized (currentBatch) {
            return flushBatch();
        }
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

        return com.ghatana.platform.domain.domain.event.GEvent.builder()
                .type("operator.registered")
                .headers(headers)
                .payload(payload)
                .time(com.ghatana.platform.domain.domain.event.EventTime.now())
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

