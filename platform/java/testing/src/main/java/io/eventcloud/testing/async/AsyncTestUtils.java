package com.ghatana.platform.testing.async;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Utilities for testing asynchronous code.
 *
 * @doc.type class
 * @doc.purpose Async test utilities for awaiting futures, conditions, and scheduled tasks
 * @doc.layer platform
 * @doc.pattern Utility
 */
public final class AsyncTestUtils {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private AsyncTestUtils() {
        // Utility class
    }

    /**
     * Wait for a CompletableFuture to complete with a timeout.
     *
     * @param future  the future to wait for
     * @param timeout the maximum time to wait
     * @param <T>     the type of the future result
     * @return the result of the future
     * @throws RuntimeException if the future completes exceptionally or times out
     */
    public static <T> T await(CompletableFuture<T> future, Duration timeout) {
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for future", e);
        } catch (TimeoutException e) {
            throw new RuntimeException("Timed out waiting for future to complete", e);
        } catch (ExecutionException e) {
            // Unwrap the cause and throw it directly to preserve the original exception type and message
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            } else if (e.getCause() != null) {
                throw new RuntimeException(e.getCause());
            } else {
                throw new RuntimeException("Future completed exceptionally");
            }
        }
    }

    /**
     * Wait for a condition to become true with a timeout and polling interval.
     *
     * @param condition the condition to wait for
     * @param timeout   the maximum time to wait
     * @param interval  the polling interval
     * @throws RuntimeException if the condition doesn't become true within the timeout
     */
    public static void await(Supplier<Boolean> condition, Duration timeout, Duration interval) {
        long endTime = System.currentTimeMillis() + timeout.toMillis();
        
        while (System.currentTimeMillis() < endTime) {
            if (Boolean.TRUE.equals(condition.get())) {
                return;
            }
            try {
                Thread.sleep(interval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting for condition", e);
            }
        }
        
        throw new RuntimeException("Condition not met within " + timeout);
    }

    /**
     * Wait for a condition to become true with a default 5-second timeout and 100ms interval.
     *
     * @param condition the condition to wait for
     * @throws RuntimeException if the condition doesn't become true within the timeout
     */
    public static void await(Supplier<Boolean> condition) {
        await(condition, Duration.ofSeconds(5), Duration.ofMillis(100));
    }

    /**
     * Create a CompletableFuture that completes after the specified delay.
     *
     * @param value the value to complete with
     * @param delay the delay before completing the future
     * @param <T>   the type of the value
     * @return a CompletableFuture that completes after the delay
     */
    public static <T> CompletableFuture<T> delayedFuture(T value, Duration delay) {
        CompletableFuture<T> future = new CompletableFuture<>();
        scheduler.schedule(
            () -> future.complete(value),
            delay.toMillis(),
            TimeUnit.MILLISECONDS
        );
        return future;
    }

    /**
     * Create a CompletableFuture that completes exceptionally after the specified delay.
     *
     * @param throwable the exception to complete with
     * @param delay     the delay before completing the future
     * @param <T>       the type of the future
     * @return a CompletableFuture that completes exceptionally after the delay
     */
    public static <T> CompletableFuture<T> delayedFailure(Throwable throwable, Duration delay) {
        CompletableFuture<T> future = new CompletableFuture<>();
        scheduler.schedule(
            () -> future.completeExceptionally(throwable),
            delay.toMillis(),
            TimeUnit.MILLISECONDS
        );
        return future;
    }
}
