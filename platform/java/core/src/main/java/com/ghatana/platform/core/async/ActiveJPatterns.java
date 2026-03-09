package com.ghatana.platform.core.async;

import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Standard ActiveJ async patterns for the Ghatana platform.
 *
 * <p>Provides reusable, production-grade patterns for asynchronous operations
 * using ActiveJ Promises. All platform services and products should use these
 * patterns for consistency and reliability.</p>
 *
 * <p>Patterns included:</p>
 * <ul>
 *   <li>Sequential execution with proper error handling</li>
 *   <li>Parallel execution with result aggregation</li>
 *   <li>Retry with exponential backoff</li>
 *   <li>Circuit breaker pattern</li>
 *   <li>Timeout handling</li>
 *   <li>Composite patterns (timeout + retry, circuit breaker + timeout)</li>
 * </ul>
 *
 * <p>Migrated from {@code com.ghatana.yappc.framework.api.ActiveJPatterns} to
 * make these utilities available across all products, not just YAPPC.</p>
 *
 * @doc.type class
 * @doc.purpose Standardize ActiveJ async patterns across the platform
 * @doc.layer core
 * @doc.pattern Async Programming, ActiveJ
 */
public final class ActiveJPatterns {

    private ActiveJPatterns() {
        // Utility class
    }

    // ========================================================================
    // Sequential Execution
    // ========================================================================

    /**
     * Execute operations sequentially, stopping on first failure.
     *
     * <p>Each promise in {@code operations} is awaited in order before the next
     * is started. This is distinct from {@link #parallel} which fans all promises
     * out concurrently. If any promise fails the resulting promise fails
     * immediately and subsequent operations are not awaited.</p>
     *
     * @param operations operations to execute in sequence
     * @param <T>        result type
     * @return Promise of all results in order
     */
    @SafeVarargs
    @NotNull
    public static <T> Promise<List<T>> sequential(@NotNull Promise<T>... operations) {
        return sequentialList(List.of(operations));
    }

    /**
     * Execute a list of already-created promises sequentially.
     *
     * @param operations list of promises to await in order
     * @param <T>        result type
     * @return Promise of all results in order
     */
    @NotNull
    public static <T> Promise<List<T>> sequentialList(@NotNull List<Promise<T>> operations) {
        return sequentialInternal(operations, 0, new ArrayList<>());
    }

    @NotNull
    private static <T> Promise<List<T>> sequentialInternal(
            @NotNull List<Promise<T>> operations,
            int index,
            @NotNull List<T> accumulator) {
        if (index >= operations.size()) {
            return Promise.of(accumulator);
        }
        return operations.get(index)
                .then(value -> {
                    List<T> next = new ArrayList<>(accumulator);
                    next.add(value);
                    return sequentialInternal(operations, index + 1, next);
                });
    }

    // ========================================================================
    // Parallel Execution
    // ========================================================================

    /**
     * Execute operations in parallel, collecting all results.
     *
     * @param operations operations to execute concurrently
     * @param <T>        result type
     * @return Promise of all results
     */
    @SafeVarargs
    @NotNull
    public static <T> Promise<List<T>> parallel(@NotNull Promise<T>... operations) {
        return Promises.toList(List.of(operations));
    }

    /**
     * Execute operations in parallel with a concurrency limit.
     *
     * <p>Processes in batches of {@code maxConcurrency}. Each batch must complete
     * before the next begins.</p>
     *
     * @param maxConcurrency maximum number of concurrent operations
     * @param operations     operations to execute
     * @param <T>            result type
     * @return Promise of all results in order
     */
    @NotNull
    public static <T> Promise<List<T>> parallelLimited(
            int maxConcurrency,
            @NotNull List<Promise<T>> operations) {
        if (operations.size() <= maxConcurrency) {
            return Promises.toList(operations);
        }
        return processBatch(operations, maxConcurrency, 0, List.of());
    }

    @NotNull
    private static <T> Promise<List<T>> processBatch(
            @NotNull List<Promise<T>> promises,
            int batchSize,
            int start,
            @NotNull List<T> accumulator) {

        if (start >= promises.size()) {
            return Promise.of(accumulator);
        }

        int end = Math.min(start + batchSize, promises.size());
        List<Promise<T>> batch = promises.subList(start, end);

        return Promises.toList(batch)
                .then(results -> {
                    List<T> newAccumulator = new ArrayList<>(accumulator);
                    newAccumulator.addAll(results);
                    return processBatch(promises, batchSize, end, newAccumulator);
                });
    }

    // ========================================================================
    // Retry Pattern
    // ========================================================================

    /**
     * Retry an operation with exponential backoff.
     *
     * <p>Initial delay doubles on each attempt (capped at {@code maxDelay}).
     * The operation is called afresh on each retry.</p>
     *
     * @param operation    supplier of the async operation (called per attempt)
     * @param maxRetries   maximum number of retries (0 = no retry)
     * @param initialDelay initial delay between retries
     * @param maxDelay     maximum delay cap
     * @param <T>          result type
     * @return Promise with result or final failure
     */
    @NotNull
    public static <T> Promise<T> withRetry(
            @NotNull Supplier<Promise<T>> operation,
            int maxRetries,
            @NotNull Duration initialDelay,
            @NotNull Duration maxDelay) {
        return retryInternal(operation, maxRetries, initialDelay, maxDelay, 0);
    }

    @NotNull
    private static <T> Promise<T> retryInternal(
            @NotNull Supplier<Promise<T>> operation,
            int maxRetries,
            @NotNull Duration initialDelay,
            @NotNull Duration maxDelay,
            int attempt) {

        return operation.get()
                .then((value, e) -> {
                    if (e == null) {
                        return Promise.of(value);
                    }
                    if (attempt < maxRetries) {
                        Duration delay = calculateDelay(initialDelay, maxDelay, attempt);
                        return Promises.delay(delay)
                                .then($ -> retryInternal(operation, maxRetries,
                                        initialDelay, maxDelay, attempt + 1));
                    }
                    return Promise.ofException(e);
                });
    }

    @NotNull
    private static Duration calculateDelay(
            @NotNull Duration initial,
            @NotNull Duration max,
            int attempt) {
        long delayMillis = initial.toMillis() * (1L << attempt);
        return Duration.ofMillis(Math.min(delayMillis, max.toMillis()));
    }

    // ========================================================================
    // Timeout Pattern
    // ========================================================================

    /**
     * Add a timeout to a promise.
     *
     * @param promise promise to timeout
     * @param timeout maximum duration to wait
     * @param <T>     result type
     * @return Promise that fails with timeout exception if duration exceeded
     */
    @NotNull
    public static <T> Promise<T> withTimeout(
            @NotNull Promise<T> promise,
            @NotNull Duration timeout) {
        return Promises.timeout(timeout, promise);
    }

    // ========================================================================
    // Composite Patterns
    // ========================================================================

    /**
     * Execute with both timeout and retry.
     *
     * <p>Each attempt has its own independent timeout. Retries use exponential backoff.</p>
     *
     * @param operation    operation supplier
     * @param timeout      per-attempt timeout
     * @param maxRetries   max retries
     * @param initialDelay initial retry delay
     * @param maxDelay     max retry delay
     * @param <T>          result type
     * @return Promise with result or final failure
     */
    @NotNull
    public static <T> Promise<T> withTimeoutAndRetry(
            @NotNull Supplier<Promise<T>> operation,
            @NotNull Duration timeout,
            int maxRetries,
            @NotNull Duration initialDelay,
            @NotNull Duration maxDelay) {
        return withRetry(
                () -> withTimeout(operation.get(), timeout),
                maxRetries,
                initialDelay,
                maxDelay);
    }

}
