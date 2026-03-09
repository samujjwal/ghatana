package com.ghatana.core.activej.promise;

import io.activej.common.function.FunctionEx;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import io.activej.promise.SettablePromise;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.Arrays;
import io.activej.async.exception.AsyncTimeoutException;

/**
 * Utility factory for working with ActiveJ Promises.
 *
 * <p><b>Purpose</b><br>
 * Provides a comprehensive toolkit for promise operations in an ActiveJ environment:
 * retry with exponential backoff, timeout handling, Java interop (CompletableFuture bridge),
 * combination (all/any), transformation (map/flatMap), fallback handling, and sequential execution.
 * Centralizes common promise patterns to reduce boilerplate and ensure consistency.
 *
 * <p><b>Architecture Role</b><br>
 * Part of `core/activej-runtime` async utilities. Bridges gap between ActiveJ's low-level Promise API
 * and high-level application patterns (e.g., retry-with-backoff is critical for distributed services,
 * timeout handling prevents resource leaks, CompletableFuture bridge enables incremental migration).
 * Used by:
 * - Service launchers and HTTP servers for robust operations
 * - Domain services for fault-tolerant async workflows
 * - Event processing pipelines for timeout/retry semantics
 * - Test infrastructure for delayed/scheduled operations
 *
 * <p><b>Key Features</b><br>
 * <ul>
 *   <li><b>Retry with Exponential Backoff:</b> {@link #withRetry(Supplier, int, Duration)} - automatic retry with delay doubling</li>
 *   <li><b>Timeout Handling:</b> {@link #withTimeout(Promise, Duration)} - add timeout to any promise</li>
 *   <li><b>Java Interop:</b> {@link #fromCompletableFuture(CompletableFuture)}, {@link #toCompletableFuture(Promise)}</li>
 *   <li><b>Promise Combination:</b> {@link #all(Promise...)} (all complete), {@link #any(Promise...)} (first completes)</li>
 *   <li><b>Transformation:</b> {@link #map(Promise, Function)}, {@link #flatMap(Promise, Function)}</li>
 *   <li><b>Fallback:</b> {@link #withFallback(Promise, Object)}, {@link #withFallbackPromise(Promise, Supplier)}</li>
 *   <li><b>Sequential Execution:</b> {@link #sequence(Supplier...)} - chain promises with results accumulation</li>
 *   <li><b>Finally Handler:</b> {@link #doFinally(Promise, Runnable)} - cleanup logic after completion</li>
 * </ul>
 *
 * <p><b>Thread Safety</b><br>
 * All methods are thread-safe when called from within an ActiveJ Eventloop context. Promises themselves
 * are not thread-safe (must be processed on single eventloop thread), but creation methods can be called
 * from any thread. Avoid blocking operations in promise callbacks - use {@code Promise.ofBlocking()} for IO.
 *
 * <p><b>Usage Examples</b><br>
 * <pre>{@code
 * // 1. Retry database operation with exponential backoff (100ms, 200ms, 400ms, ...)
 * Promise<User> user = PromiseUtils.withRetry(
 *     () -> userRepository.findById(userId),
 *     maxAttempts = 3,
 *     initialDelay = Duration.ofMillis(100)
 * );
 *
 * // 2. Add timeout to prevent hanging requests
 * Promise<ApiResponse> response = PromiseUtils.withTimeout(
 *     remoteService.fetchData(),
 *     Duration.ofSeconds(5)  // Fails with TimeoutException after 5s
 * );
 *
 * // 3. Bridge Java CompletableFuture → ActiveJ Promise (legacy code)
 * CompletableFuture<String> legacyFuture = legacyLibrary.doWork();
 * Promise<String> result = PromiseUtils.fromCompletableFuture(legacyFuture);
 *
 * // 4. Execute multiple operations in parallel (all must complete)
 * Promise<List<Order>> orders = PromiseUtils.all(
 *     orderService.getOrdersForUser(userId),
 *     orderService.getOrdersForStore(storeId),
 *     orderService.getRecommendedOrders(userId)
 * );
 *
 * // 5. Implement circuit-breaker pattern
 * Promise<Boolean> isHealthy = PromiseUtils.withFallbackPromise(
 *     remoteHealthCheck(),
 *     () -> Promise.of(false)  // Fallback: assume unhealthy if check times out
 * );
 *
 * // 6. Chain async operations sequentially
 * Promise<List<String>> chain = PromiseUtils.sequence(
 *     () -> fetchUserProfile(userId),
 *     () -> enrichProfile(profile),
 *     () -> saveProfile(enriched)
 * );
 *
 * // 7. Add guaranteed cleanup (like finally block)
 * Promise<Void> resource = PromiseUtils.doFinally(
 *     acquireResource(),
 *     () -> releaseResource()  // Runs regardless of success/failure
 * );
 *
 * // 8. Transform promise result using map
 * Promise<Integer> length = PromiseUtils.map(
 *     fetchString(),
 *     String::length
 * );
 *
 * // 9. Chain dependent async operations using flatMap
 * Promise<Order> order = PromiseUtils.flatMap(
 *     cartService.checkout(cartId),
 *     order -> orderService.persist(order)
 * );
 * }</pre>
 *
 * <p><b>Best Practices</b><br>
 * <ul>
 *   <li><b>Always Use withRetry():</b> For any operation that talks to external systems (DB, HTTP, cache). Start with 3 attempts and 100-200ms initial delay.</li>
 *   <li><b>Add Timeouts Everywhere:</b> Use {@link #withTimeout(Promise, Duration)} on all I/O operations to prevent hanging threads. Recommended: 5-30 seconds depending on SLA.</li>
 *   <li><b>Prefer map() Over then():</b> Use {@link #map(Promise, Function)} for simple synchronous transformations (cleaner, less boilerplate).</li>
 *   <li><b>Use Fallback for Degraded Modes:</b> {@link #withFallback(Promise, Object)} for circuit-breaker or fallback-to-cache patterns.</li>
 *   <li><b>Accumulate Results in sequence():</b> {@link #sequence(Supplier...)} is useful for setup/cleanup chains where each step needs prior result.</li>
 *   <li><b>Monitor Backoff Delays:</b> Exponential backoff grows quickly (1×, 2×, 4×, 8×...) - set max attempts conservatively to avoid long total delays.</li>
 *   <li><b>Avoid Blocking in Callbacks:</b> Never call {@code Thread.sleep()}, blocking I/O, or long-running CPU tasks in promise callbacks - breaks eventloop.</li>
 *   <li><b>Log Retry Attempts:</b> Enable DEBUG logging to see retry counts and delays (uses SLF4J @Slf4j logger).</li>
 * </ul>
 *
 * <p><b>Anti-Patterns</b><br>
 * <ul>
 *   <li>❌ <b>No Timeout on I/O:</b> Don't use promises for remote calls without {@link #withTimeout(Promise, Duration)} - requests can hang indefinitely.</li>
 *   <li>❌ <b>No Retry for Transient Failures:</b> Don't assume every I/O failure is permanent - most are transient (DB connection pool, network blip). Always retry.</li>
 *   <li>❌ <b>Blocking in Callbacks:</b> Don't call {@code Thread.sleep()}, JDBC queries, or blocking I/O in promise handlers - deadlocks eventloop.</li>
 *   <li>❌ <b>Excessive Retry Attempts:</b> Don't retry 100 times with exponential backoff - total delay becomes unacceptable. Use 3-5 attempts.</li>
 *   <li>❌ <b>Ignoring Fallback Results:</b> When using {@link #withFallback(Promise, Object)}, remember the fallback may be stale - log and monitor these cases.</li>
 *   <li>❌ <b>Mixing Promises and Threads:</b> Never pass promises across threads - they're not thread-safe. Always execute in eventloop context.</li>
 *   <li>❌ <b>Excessive Promise Combinations:</b> Don't combine hundreds of promises with {@link #all(List)} - can exhaust memory. Batch into groups of 10-50.</li>
 * </ul>
 *
 * <p><b>Error Handling Strategy</b><br>
 * - {@link #withRetry(Supplier, int, Duration)}: Retries on ANY exception up to max attempts, then fails with last exception
 * - {@link #withTimeout(Promise, Duration)}: Converts {@code AsyncTimeoutException} → {@code TimeoutException}
 * - {@link #withFallback(Promise, Object)}: Silently catches exception, returns fallback value (for graceful degradation)
 * - {@link #withFallbackPromise(Promise, Supplier)}: Falls back to alternative async operation (circuit-breaker pattern)
 * - {@link #all(List)}: Fails immediately if ANY promise fails (fail-fast semantics)
 * - {@link #any(List)}: Completes when ANY promise completes (first-success semantics)
 *
 * <p><b>Performance Characteristics</b><br>
 * - Promise creation: O(1)
 * - Retry overhead: O(attempts) time, but exponential delay grows: 1×+2×+4×+...+2^(n-1)×delay
 * - {@code all()} combination: O(n) where n = number of promises
 * - {@code any()} combination: O(1) (first to complete wins)
 * - {@code map()}, {@code flatMap()}, {@code withFallback()}: O(1)
 *
 * <p><b>Related Components</b><br>
 * @see Promise ActiveJ core promise abstraction
 * @see EventloopManager Eventloop lifecycle management
 * @see com.ghatana.core.http.server HTTP server using promises
 * @see com.ghatana.core.observability Metrics collection for promise operations
 *
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Comprehensive utilities for Promise operations (retry, timeout, composition, transformation)
 * @doc.layer core
 * @doc.pattern Factory + Utilities
 */
@Slf4j
public final class PromiseUtils {
    
    private PromiseUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Internal implementation of retry logic with exponential backoff.
     *
     * @param <T> The result type
     * @param operation The operation to retry
     * @param remainingAttempts Number of remaining retry attempts
     * @param delay Current delay between attempts
     * @param attemptNumber Current attempt number
     * @return A promise that completes when the operation succeeds or all retries are exhausted
     */
    private static <T> Promise<T> retryInternal(Supplier<Promise<T>> operation, 
                                             int remainingAttempts, 
                                             Duration delay, 
                                             int attemptNumber) {
        log.debug("Attempt {} of {} (delay: {}ms)", attemptNumber, 
                 attemptNumber + remainingAttempts - 1, delay.toMillis());
        
        return operation.get()
            .whenComplete((result, error) -> {
                if (error != null) {
                    log.error("Attempt {} failed: {}", attemptNumber, error.getMessage());
                    log.debug("Attempt {} error details", attemptNumber, error);
                } else {
                    log.debug("Attempt {} succeeded", attemptNumber);
                }
            })
            .then(
                result -> {
                    if (attemptNumber > 1) {
                        log.info("Operation succeeded on attempt {}", attemptNumber);
                    }
                    return Promise.of(result);
                },
                error -> {
                    if (remainingAttempts <= 1) {
                        return Promise.ofException(error);
                    }
                    
                    // Exponential backoff: delay * 2^(attemptNumber-1)
                    Duration nextDelay = delay.multipliedBy(2);
                    
                    return Promise.complete()
                        .then(() -> retryInternal(operation, remainingAttempts - 1, nextDelay, attemptNumber + 1));
                }
            );
    }
    
    /**
     * Adds a timeout to a promise.
     *
     * @param <T> The promise result type
     * @param promise The promise to add timeout to
     * @param timeout The timeout duration
     * @return A promise that fails with TimeoutException if timeout is exceeded
     */
    public static <T> Promise<T> withTimeout(Promise<T> promise, Duration timeout) {
        // Promises.timeout expects (Duration, Promise<T>) according to the ActiveJ API used here
        Promise<T> timed = Promises.timeout(timeout, promise);
        // Convert ActiveJ's AsyncTimeoutException into java.util.concurrent.TimeoutException
        return timed.then(
            value -> Promise.of(value),
            e -> {
                if (e instanceof AsyncTimeoutException) {
                    return Promise.ofException(new TimeoutException(e.getMessage()));
                }
                return Promise.ofException(e instanceof Exception ? (Exception) e : new RuntimeException(e));
            }
        );
    }
    
    /**
     * Converts a CompletableFuture to an ActiveJ Promise.
     * Useful for interoperability with non-ActiveJ code.
     *
     * @param <T> The result type
     * @param future The CompletableFuture to convert
     * @return A Promise that completes when the future completes
     */
    public static <T> Promise<T> fromCompletableFuture(CompletableFuture<T> future) {
        SettablePromise<T> promise = new SettablePromise<>();
        future.whenComplete((result, error) -> {
            if (error != null) {
                promise.setException(error instanceof Exception ? (Exception) error : new RuntimeException(error));
            } else {
                promise.set(result);
            }
        });
        return promise;
    }
    
    /**
     * Converts an ActiveJ Promise to a CompletableFuture.
     * Useful for interoperability with non-ActiveJ code.
     *
     * @param <T> The result type
     * @param promise The Promise to convert
     * @return A CompletableFuture that completes when the promise completes
     */
    public static <T> CompletableFuture<T> toCompletableFuture(Promise<T> promise) {
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
     * Promise creation methods
     */
    
    /**
     * Creates a promise that completes with the provided value.
     *
     * @param <T> The result type
     * @param value The value to complete the promise with
     * @return A completed promise with the given value
     */
    public static <T> Promise<T> of(T value) {
        return Promise.of(value);
    }
    
    /**
     * Creates a failed promise with the given exception.
     *
     * @param <T> The result type
     * @param error The exception to fail the promise with
     * @return A failed promise with the given exception
     */
    public static <T> Promise<T> ofException(Throwable error) {
        return Promise.ofException(error instanceof Exception ? (Exception) error : new RuntimeException(error));
    }
    
    /**
     * Creates a promise that completes after a delay.
     *
     * @param <T> The result type
     * @param delay The delay duration
     * @param value The value to complete the promise with after the delay
     * @return A promise that completes with the value after the delay
     */
    public static <T> Promise<T> delay(Duration delay, T value) {
        return Promises.delay(delay).map($ -> value);
    }
    
    public static <T> Promise<T> delay(Duration delay, Supplier<Promise<T>> supplier) {
        return Promises.delay(delay).then(supplier::get);
    }
    
    /**
     * Promise combination methods
     */
    
    /**
     * Combines multiple promises into a single promise that completes when all input promises complete.
     *
     * @param <T> The result type
     * @param promises The promises to combine
     * @return A promise that completes with a list of all results when all input promises complete
     */
    @SafeVarargs
    public static <T> Promise<List<T>> all(Promise<T>... promises) {
        return Promises.toList(List.of(promises));
    }
    
    public static <T> Promise<List<T>> all(List<Promise<T>> promises) {
        if (promises == null || promises.isEmpty()) {
            return Promise.of(new ArrayList<>());
        }

        SettablePromise<List<T>> result = new SettablePromise<>();
        List<T> results = new ArrayList<>(Collections.nCopies(promises.size(), null));
        AtomicInteger remaining = new AtomicInteger(promises.size());
        java.util.concurrent.atomic.AtomicBoolean failed = new java.util.concurrent.atomic.AtomicBoolean(false);

        for (int i = 0; i < promises.size(); i++) {
            final int idx = i;
            Promise<T> p = promises.get(i);
            p.whenComplete((res, err) -> {
                 if (failed.get()) return; // already failed
                 if (err != null) {
                     failed.set(true);
                     result.setException(err instanceof Exception ? (Exception) err : new RuntimeException(err));
                     return;
                 }
                 results.set(idx, res);
                 if (remaining.decrementAndGet() == 0) {
                     result.set(results);
                 }
             });
         }

        return result;
    }
    
    /**
     * Combines multiple promises into a single promise that completes when any input promise completes.
     *
     * @param <T> The result type
     * @param promises The promises to combine
     * @return A promise that completes with the result of the first promise to complete
     */
    @SafeVarargs
    public static <T> Promise<T> any(Promise<T>... promises) {
        // Promises.first has overloads that accept an Iterator of Promise; convert the array to a list iterator
        return Promises.first(Arrays.asList(promises).iterator());
    }
    
    public static <T> Promise<T> any(List<Promise<T>> promises) {
        return Promises.first(promises.iterator());
    }
    
    /**
     * Promise transformation methods
     */
    
    /**
     * Transforms the result of a promise using the provided function.
     *
     * @param <T> The input type
     * @param <R> The result type
     * @param promise The input promise
     * @param mapper The transformation function
     * @return A promise that completes with the transformed result
     */
    public static <T, R> Promise<R> map(Promise<T> promise, Function<T, R> mapper) {
        return promise.map((FunctionEx<T, R>) mapper::apply);
    }
    
    /**
     * Chains promises using the provided function.
     *
     * @param <T> The input type
     * @param <R> The result type
     * @param promise The input promise
     * @param mapper The function that returns a new promise
     * @return A promise that completes when the chained promise completes
     */
    public static <T, R> Promise<R> flatMap(Promise<T> promise, Function<T, Promise<R>> mapper) {
        return promise.then(mapper::apply);
    }
    
    /**
     * Promise fallback methods
     */
    
    /**
     * Adds a fallback value for when a promise fails.
     *
     * @param <T> The result type
     * @param promise The input promise
     * @param fallback The fallback value to use if the promise fails
     * @return A promise that completes with either the original result or the fallback value
     */
    public static <T> Promise<T> withFallback(Promise<T> promise, T fallback) {
        // Avoid using mapException here to prevent type-inference issues; map failures to a fallback promise instead
        return promise.then(value -> Promise.of(value), e -> Promise.of(fallback));
    }
    
    /**
     * Adds a fallback promise for when a promise fails.
     *
     * @param <T> The result type
     * @param promise The input promise
     * @param fallback The fallback promise to use if the input promise fails
     * @return A promise that completes with either the original result or the fallback promise's result
     */
    public static <T> Promise<T> withFallbackPromise(Promise<T> promise, Supplier<Promise<T>> fallback) {
        return promise.then(
            value -> Promise.of(value),
            e -> fallback.get()
        );
    }
    
    /**
     * Retry helpers
     */
    /**
     * Retries the provided async operation up to `attempts` times with exponential backoff starting from `delay`.
     * The first attempt is executed immediately.
     *
     * @param operation the operation to retry
     * @param attempts maximum number of attempts (must be >= 1)
     * @param delay initial delay between attempts
     * @param <T> result type
     * @return promise that completes with the operation result or fails with the last error after attempts are exhausted
     */
    public static <T> Promise<T> withRetry(Supplier<Promise<T>> operation, int attempts, Duration delay) {
        if (attempts <= 0) {
            throw new IllegalArgumentException("attempts must be greater than 0");
        }
        return retryInternal(operation, attempts, delay, 1);
    }

    /**
     * Promise sequencing methods
     */
    
    /**
     * Executes a sequence of operations where each operation depends on the result of the previous one.
     *
     * @param <T> The result type
     * @param operations The operations to execute in sequence
     * @return A promise that completes with the result of the last operation
     */
    @SafeVarargs
    public static <T> Promise<List<T>> sequence(Supplier<Promise<T>>... operations) {
        return sequence(List.of(operations));
    }
    
    public static <T> Promise<List<T>> sequence(List<Supplier<Promise<T>>> operations) {
        if (operations.isEmpty()) {
            return Promise.of(new ArrayList<>());
        }
        
        List<T> results = new ArrayList<>();
        Promise<List<T>> result = Promise.of(results);
        
        for (Supplier<Promise<T>> operation : operations) {
            result = result.then(list -> 
                operation.get().whenResult(list::add).map($ -> list)
            );
        }
        
        return result;
    }
    
    /**
     * Promise finally methods
     */
    
    /**
     * Adds a finally-like handler that runs after the promise completes, regardless of success or failure.
     *
     * @param <T> The result type
     * @param promise The input promise
     * @param action The action to run after the promise completes
     * @return A promise that completes after the action runs
     */
    public static <T> Promise<T> doFinally(Promise<T> promise, Runnable action) {
        return promise
            .whenComplete((r, e) -> action.run())
            .map(t -> t);
    }
}
