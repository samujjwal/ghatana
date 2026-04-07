/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.performance;

import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Async processing throughput enhancer for AEP (AEP-006.3).
 *
 * <p>Provides utilities for batching, bounded-concurrency fan-out, and
 * CPU-bound work delegation to keep the ActiveJ event loop non-blocking.
 * Target: async throughput &gt;1000 ops/sec.
 *
 * <h3>Usage patterns</h3>
 * <pre>{@code
 * // Fan-out with bounded concurrency
 * List<Event> events = ...;
 * Promise<List<Result>> results = AsyncProcessingEnhancer.fanOut(
 *         events, event -> processAsync(event), 50);
 *
 * // CPU-bound work off the event loop
 * Promise<Long> count = AsyncProcessingEnhancer.offload(() -> expensiveCount());
 * }</pre>
 *
 * @doc.type    class
 * @doc.purpose Async throughput utilities: fan-out, batching, off-loop execution
 * @doc.layer   product
 * @doc.pattern Utility, Strategy
 */
public final class AsyncProcessingEnhancer {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncProcessingEnhancer.class);

    private static final Executor DEFAULT_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private final Executor blockingExecutor;
    private final AtomicLong totalOps = new AtomicLong(0);
    private final AtomicLong totalNs  = new AtomicLong(0);

    private AsyncProcessingEnhancer(Builder builder) {
        this.blockingExecutor = builder.executor;
    }

    // ── Fan-out ───────────────────────────────────────────────────────────────

    /**
     * Processes a collection of inputs concurrently with a bounded concurrency
     * limit.  Returns a {@link Promise} that resolves to all results in the same
     * order as the input list.
     *
     * @param inputs      items to process
     * @param processor   async function applied to each item
     * @param maxConcurrency maximum simultaneous in-flight operations
     * @param <I>         input type
     * @param <O>         output type
     * @return promise of ordered results
     */
    public static <I, O> Promise<List<O>> fanOut(
            Collection<I> inputs,
            Function<I, Promise<O>> processor,
            int maxConcurrency) {
        Objects.requireNonNull(inputs, "inputs must not be null");
        Objects.requireNonNull(processor, "processor must not be null");
        if (maxConcurrency <= 0) throw new IllegalArgumentException("maxConcurrency must be positive");

        if (inputs.isEmpty()) return Promise.of(List.of());

        List<I> list = new ArrayList<>(inputs);
        List<Promise<O>> promises = new ArrayList<>(list.size());
        Semaphore semaphore = new Semaphore(maxConcurrency);

        for (I input : list) {
            semaphore.acquireUninterruptibly();
            Promise<O> p = processor.apply(input)
                    .whenComplete((result, error) -> semaphore.release());
            promises.add(p);
        }

        return Promises.toList(promises);
    }

    /**
     * Wraps a CPU-bound or blocking supplier in {@link Promise#ofBlocking} so it
     * runs on a virtual-thread executor instead of the ActiveJ event loop.
     *
     * @param supplier blocking computation
     * @param <T>      result type
     * @return promise backed by an off-loop executor
     */
    public <T> Promise<T> offload(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        long start = System.nanoTime();
        return Promise.ofBlocking(blockingExecutor, supplier::get)
                .whenComplete((r, e) -> {
                    long elapsed = System.nanoTime() - start;
                    totalOps.incrementAndGet();
                    totalNs.addAndGet(elapsed);
                    if (e != null) {
                        LOG.warn("Offloaded task failed: {}", e.getMessage());
                    }
                });
    }

    /**
     * Processes items in micro-batches, passing each batch to the given function.
     *
     * @param items      all items to process
     * @param batchSize  number of items per batch (&gt;0)
     * @param batchFn    function that handles a batch and returns a promise of partial results
     * @param <I>        input type
     * @param <O>        output type
     * @return promise of all results concatenated in order
     */
    public static <I, O> Promise<List<O>> processBatched(
            List<I> items,
            int batchSize,
            Function<List<I>, Promise<List<O>>> batchFn) {
        Objects.requireNonNull(items, "items must not be null");
        Objects.requireNonNull(batchFn, "batchFn must not be null");
        if (batchSize <= 0) throw new IllegalArgumentException("batchSize must be positive");

        if (items.isEmpty()) return Promise.of(List.of());

        List<Promise<List<O>>> batchPromises = new ArrayList<>();
        for (int i = 0; i < items.size(); i += batchSize) {
            List<I> batch = items.subList(i, Math.min(i + batchSize, items.size()));
            batchPromises.add(batchFn.apply(batch));
        }

        return Promises.toList(batchPromises)
                .map(listOfLists -> listOfLists.stream()
                        .flatMap(Collection::stream)
                        .toList());
    }

    // ── Throughput stats ──────────────────────────────────────────────────────

    /**
     * Returns cumulative throughput statistics for off-loaded operations.
     *
     * @return throughput stats
     */
    public ThroughputStats throughputStats() {
        long ops = totalOps.get();
        long ns  = totalNs.get();
        double avgMs = ops == 0 ? 0.0 : (double) ns / ops / 1_000_000.0;
        return new ThroughputStats(ops, avgMs);
    }

    // ── Nested types ──────────────────────────────────────────────────────────

    /**
     * Throughput statistics.
     *
     * @param completedOps total off-loaded operations completed
     * @param avgLatencyMs average latency per operation in milliseconds
     */
    public record ThroughputStats(long completedOps, double avgLatencyMs) {}

    // ── Builder ────────────────────────────────────────────────────────────────

    /** Returns a new builder. */
    public static Builder builder() { return new Builder(); }

    /**
     * Builder for {@link AsyncProcessingEnhancer}.
     */
    public static final class Builder {
        private Executor executor = DEFAULT_EXECUTOR;

        private Builder() {}

        /**
         * Executor used for {@link #offload} calls.
         *
         * @param executor blocking-friendly executor
         * @return this builder
         */
        public Builder executor(Executor executor) {
            this.executor = Objects.requireNonNull(executor, "executor must not be null");
            return this;
        }

        public AsyncProcessingEnhancer build() { return new AsyncProcessingEnhancer(this); }
    }
}

