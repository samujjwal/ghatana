/*
 * Copyright (c) 2026 Ghatana.
 * All rights reserved.
 */
package com.ghatana.core.runtime;

import io.activej.promise.Promise;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Central registry for all executor pools in the Ghatana platform.
 * 
 * <p>Provides shared executor instances for different workload types:
 * <ul>
 *   <li><b>Blocking Executor:</b> For I/O-bound operations (database, file, network)</li>
 *   <li><b>CPU Executor:</b> For CPU-bound computations</li>
 *   <li><b>Scheduled Executor:</b> For delayed and periodic tasks</li>
 * </ul>
 * 
 * <p><b>Usage:</b>
 * <pre>{@code
 * // Inject via constructor
 * public MyService(ExecutorRegistry executorRegistry) {
 *     this.executor = executorRegistry.blocking();
 * }
 * 
 * // Use with Promise.ofBlocking
 * public Promise<Result> fetchData() {
 *     return Promise.ofBlocking(executorRegistry.blocking(), () -> {
 *         return database.query();
 *     });
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Central registry for all executor pools
 * @doc.layer platform
 * @doc.pattern Registry, Singleton
 */
@Slf4j
public class ExecutorRegistry {

    private static final int DEFAULT_BLOCKING_THREADS = Runtime.getRuntime().availableProcessors() * 4;
    private static final int DEFAULT_CPU_THREADS = Runtime.getRuntime().availableProcessors();
    private static final int DEFAULT_SCHEDULED_THREADS = 2;

    private final ExecutorService blockingExecutor;
    private final ExecutorService cpuExecutor;
    private final ScheduledExecutorService scheduledExecutor;

    private volatile boolean shutdown = false;

    /**
     * Creates an ExecutorRegistry with default thread pool sizes.
     */
    public ExecutorRegistry() {
        this(DEFAULT_BLOCKING_THREADS, DEFAULT_CPU_THREADS, DEFAULT_SCHEDULED_THREADS);
    }

    /**
     * Creates an ExecutorRegistry with custom thread pool sizes.
     *
     * @param blockingThreads Number of threads for blocking I/O operations
     * @param cpuThreads Number of threads for CPU-bound operations
     * @param scheduledThreads Number of threads for scheduled tasks
     */
    public ExecutorRegistry(int blockingThreads, int cpuThreads, int scheduledThreads) {
        this.blockingExecutor = Executors.newFixedThreadPool(
            blockingThreads,
            new NamedThreadFactory("ghatana-blocking")
        );
        this.cpuExecutor = Executors.newFixedThreadPool(
            cpuThreads,
            new NamedThreadFactory("ghatana-cpu")
        );
        this.scheduledExecutor = Executors.newScheduledThreadPool(
            scheduledThreads,
            new NamedThreadFactory("ghatana-scheduled")
        );

        log.info("ExecutorRegistry initialized: blocking={}, cpu={}, scheduled={}", 
            blockingThreads, cpuThreads, scheduledThreads);
    }

    /**
     * Returns the executor for blocking I/O operations.
     * 
     * <p>Use this for:
     * <ul>
     *   <li>Database queries</li>
     *   <li>File I/O</li>
     *   <li>Network calls to external services</li>
     *   <li>Redis/cache operations</li>
     * </ul>
     *
     * @return Executor for blocking operations
     */
    public Executor blocking() {
        checkNotShutdown();
        return blockingExecutor;
    }

    /**
     * Returns the executor for CPU-bound operations.
     * 
     * <p>Use this for:
     * <ul>
     *   <li>Computation-heavy tasks</li>
     *   <li>JSON/XML parsing of large documents</li>
     *   <li>Cryptographic operations</li>
     *   <li>Data transformations</li>
     * </ul>
     *
     * @return Executor for CPU-bound operations
     */
    public Executor cpu() {
        checkNotShutdown();
        return cpuExecutor;
    }

    /**
     * Returns the scheduled executor for delayed and periodic tasks.
     * 
     * <p>Use this for:
     * <ul>
     *   <li>Timeouts</li>
     *   <li>Periodic health checks</li>
     *   <li>Delayed retries</li>
     *   <li>Cache expiration</li>
     * </ul>
     *
     * @return ScheduledExecutorService for timed tasks
     */
    public ScheduledExecutorService scheduled() {
        checkNotShutdown();
        return scheduledExecutor;
    }

    /**
     * Creates a Promise that completes after the specified delay.
     * 
     * <p>Use instead of Thread.sleep() in async code:
     * <pre>{@code
     * // ❌ WRONG - blocks the event loop
     * Thread.sleep(1000);
     * 
     * // ✅ CORRECT - non-blocking delay
     * executorRegistry.delay(Duration.ofSeconds(1))
     *     .then(v -> continueProcessing());
     * }</pre>
     *
     * @param duration The delay duration
     * @return Promise that completes after the delay
     */
    public Promise<Void> delay(Duration duration) {
        checkNotShutdown();
        return Promise.ofCallback(callback -> {
            scheduledExecutor.schedule(
                () -> callback.accept(null, null),
                duration.toMillis(),
                TimeUnit.MILLISECONDS
            );
        });
    }

    /**
     * Wraps a blocking operation in a Promise using the blocking executor.
     * 
     * <p>Convenience method equivalent to:
     * <pre>{@code
     * Promise.ofBlocking(executorRegistry.blocking(), supplier);
     * }</pre>
     *
     * @param supplier The blocking operation to execute
     * @param <T> The result type
     * @return Promise that completes with the result
     */
    public <T> Promise<T> runBlocking(java.util.function.Supplier<T> supplier) {
        checkNotShutdown();
        return Promise.ofBlocking(blockingExecutor, supplier::get);
    }

    /**
     * Wraps a CPU-bound operation in a Promise using the CPU executor.
     *
     * @param supplier The CPU-bound operation to execute
     * @param <T> The result type
     * @return Promise that completes with the result
     */
    public <T> Promise<T> runCpu(java.util.function.Supplier<T> supplier) {
        checkNotShutdown();
        return Promise.ofBlocking(cpuExecutor, supplier::get);
    }

    /**
     * Gracefully shuts down all executors.
     * 
     * @param timeout Maximum time to wait for tasks to complete
     * @return Promise that completes when shutdown is finished
     */
    public Promise<Void> shutdown(Duration timeout) {
        if (shutdown) {
            return Promise.complete();
        }
        shutdown = true;

        log.info("Shutting down ExecutorRegistry...");

        blockingExecutor.shutdown();
        cpuExecutor.shutdown();
        scheduledExecutor.shutdown();

        return Promise.ofBlocking(Runnable::run, () -> {
            try {
                long timeoutMs = timeout.toMillis();
                long perExecutorTimeout = timeoutMs / 3;

                if (!blockingExecutor.awaitTermination(perExecutorTimeout, TimeUnit.MILLISECONDS)) {
                    log.warn("Blocking executor did not terminate gracefully, forcing shutdown");
                    blockingExecutor.shutdownNow();
                }
                if (!cpuExecutor.awaitTermination(perExecutorTimeout, TimeUnit.MILLISECONDS)) {
                    log.warn("CPU executor did not terminate gracefully, forcing shutdown");
                    cpuExecutor.shutdownNow();
                }
                if (!scheduledExecutor.awaitTermination(perExecutorTimeout, TimeUnit.MILLISECONDS)) {
                    log.warn("Scheduled executor did not terminate gracefully, forcing shutdown");
                    scheduledExecutor.shutdownNow();
                }

                log.info("ExecutorRegistry shutdown complete");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Shutdown interrupted, forcing immediate shutdown");
                blockingExecutor.shutdownNow();
                cpuExecutor.shutdownNow();
                scheduledExecutor.shutdownNow();
            }
            return null;
        });
    }

    /**
     * Checks if the registry has been shut down.
     *
     * @return true if shutdown has been initiated
     */
    public boolean isShutdown() {
        return shutdown;
    }

    private void checkNotShutdown() {
        if (shutdown) {
            throw new IllegalStateException("ExecutorRegistry has been shut down");
        }
    }

    /**
     * Named thread factory for better debugging and monitoring.
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        NamedThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
            t.setDaemon(true);
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }
}
