/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.core.activej.async;

import io.activej.promise.Promise;
import io.activej.reactor.Reactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Bridge utility for converting between ActiveJ Promise and CompletableFuture.
 * 
 * <p>
 * <b>Purpose</b><br>
 * Provides a single, consistent way to:
 * <ul>
 * <li>Run blocking I/O operations off the eventloop</li>
 * <li>Convert between Promise and CompletableFuture</li>
 * <li>Prevent eventloop blocking</li>
 * <li>Maintain async semantics across boundaries</li>
 * </ul>
 * 
 * <p>
 * <b>Usage Examples</b><br>
 * 
 * <pre>{@code
 * // Run blocking operation off eventloop
 * Promise<String> result = AsyncBridge.runBlocking(() -> {
 *     return blockingDatabaseCall();
 * });
 * 
 * // Convert CompletableFuture to Promise
 * CompletableFuture<Data> future = externalService.fetchData();
 * Promise<Data> promise = AsyncBridge.fromFuture(future);
 * 
 * // Convert Promise to CompletableFuture
 * Promise<Result> promise = processAsync();
 * CompletableFuture<Result> future = AsyncBridge.toFuture(promise);
 * }</pre>
 * 
 * @doc.type class
 * @doc.purpose Async concurrency bridge
 * @doc.layer infrastructure
 * @doc.pattern Bridge, Adapter
 */
public final class AsyncBridge {

    private static final Logger log = LoggerFactory.getLogger(AsyncBridge.class);

    // Dedicated executor for blocking operations
    private static final Executor BLOCKING_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "async-bridge-blocking");
        thread.setDaemon(true);
        return thread;
    });

    private AsyncBridge() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Runs a blocking operation on a dedicated executor and returns a Promise.
     * 
     * <p>
     * This method ensures that blocking I/O operations (database calls,
     * file I/O, synchronous HTTP clients, etc.) do not block the ActiveJ eventloop.
     * 
     * @param <T>      the result type
     * @param supplier the blocking operation to execute
     * @return Promise that completes with the result
     */
    public static <T> Promise<T> runBlocking(Supplier<T> supplier) {
        Reactor reactor = Reactor.getCurrentReactor();
        if (reactor == null) {
            log.warn("No reactor in current thread, executing synchronously");
            try {
                return Promise.of(supplier.get());
            } catch (Exception e) {
                return Promise.ofException(e);
            }
        }

        return Promise.ofBlocking(reactor, supplier::get);
    }

    /**
     * Runs a blocking operation with a custom executor.
     * 
     * @param <T>      the result type
     * @param executor the executor to use
     * @param supplier the blocking operation
     * @return Promise that completes with the result
     */
    public static <T> Promise<T> runBlocking(Executor executor, Supplier<T> supplier) {
        Reactor reactor = Reactor.getCurrentReactor();
        if (reactor == null) {
            log.warn("No reactor in current thread, executing synchronously");
            try {
                return Promise.of(supplier.get());
            } catch (Exception e) {
                return Promise.ofException(e);
            }
        }

        return Promise.ofBlocking(executor, supplier::get);
    }

    /**
     * Converts a CompletableFuture to an ActiveJ Promise.
     * 
     * <p>
     * The Promise will complete when the CompletableFuture completes,
     * preserving both success and failure outcomes.
     * 
     * @param <T>    the result type
     * @param future the CompletableFuture to convert
     * @return Promise that mirrors the future's completion
     */
    public static <T> Promise<T> fromFuture(CompletableFuture<T> future) {
        Reactor reactor = Reactor.getCurrentReactor();
        if (reactor == null) {
            log.warn("No reactor in current thread, blocking on future");
            try {
                return Promise.of(future.join());
            } catch (Exception e) {
                return Promise.ofException(e);
            }
        }

        return Promise.ofFuture(reactor, future);
    }

    /**
     * Converts an ActiveJ Promise to a CompletableFuture.
     * 
     * <p>
     * The CompletableFuture will complete when the Promise completes,
     * preserving both success and failure outcomes.
     * 
     * @param <T>     the result type
     * @param promise the Promise to convert
     * @return CompletableFuture that mirrors the promise's completion
     */
    public static <T> CompletableFuture<T> toFuture(Promise<T> promise) {
        CompletableFuture<T> future = new CompletableFuture<>();

        promise.whenComplete((result, error) -> {
            if (error != null) {
                future.completeExceptionally(error);
            } else {
                future.complete(result);
            }
        });

        return future;
    }

    /**
     * Runs a blocking operation and converts the result to CompletableFuture.
     * 
     * @param <T>      the result type
     * @param supplier the blocking operation
     * @return CompletableFuture that completes with the result
     */
    public static <T> CompletableFuture<T> runBlockingToFuture(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, BLOCKING_EXECUTOR);
    }

    /**
     * Gets the default blocking executor used by AsyncBridge.
     * 
     * @return the blocking executor
     */
    public static Executor getBlockingExecutor() {
        return BLOCKING_EXECUTOR;
    }
}
