package com.ghatana.yappc.plugin;

import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Canonical async-pattern utilities for YAPPC ActiveJ-based code.
 *
 * <p>Provides standardised wrappers for common async concerns so that product
 * modules do not hand-roll their own retry / timeout / fallback logic:
 *
 * <ul>
 *   <li>{@link #withRetry} — exponential-back-off promise retry
 *   <li>{@link #withTimeout} — deadline-bounded promise execution
 *   <li>{@link #withFallback} — supply a fallback value on failure
 *   <li>{@link #withBlocking} — run blocking I/O off the event loop
 * </ul>
 *
 * <p>All methods are pure combinators: they do not start event loops, bind
 * executors, or mutate global state.
 *
 * <p>Usage example:
 * <pre>{@code
 * Promise<String> result = ActiveJPatterns.withRetry(
 *     () -> callExternalService(),
 *     3,
 *     Duration.ofMillis(200));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Canonical async-pattern utilities for ActiveJ Promise flows
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class ActiveJPatterns {

    private static final Logger log = LoggerFactory.getLogger(ActiveJPatterns.class);

    private ActiveJPatterns() {
        // Utility class — no instances
    }

    // =========================================================================
    // withRetry
    // =========================================================================

    /**
     * Retry a promise-producing {@code supplier} up to {@code maxAttempts}
     * times with exponential back-off starting at {@code baseDelay}.
     *
     * <p>The first attempt is made immediately. On failure the next attempt is
     * scheduled after {@code baseDelay}, then {@code baseDelay * 2}, and so on
     * (capped at 30 s). All retry scheduling is done via
     * {@link io.activej.promise.Promises#delay(long, Promise)}.
     *
     * @param supplier    produces the promise to retry
     * @param maxAttempts maximum number of total attempts (must be ≥ 1)
     * @param baseDelay   initial back-off delay
     * @param <T>         the result type
     * @return a promise that resolves to the first successful result, or fails
     *         with the last error after {@code maxAttempts} are exhausted
     */
    public static <T> Promise<T> withRetry(
            Supplier<Promise<T>> supplier,
            int maxAttempts,
            Duration baseDelay) {

        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }

        return retryLoop(supplier, 1, maxAttempts, baseDelay.toMillis());
    }

    private static <T> Promise<T> retryLoop(
            Supplier<Promise<T>> supplier,
            int attempt,
            int maxAttempts,
            long delayMs) {

        return supplier.get().then(
                Promise::of,
                e -> {
                    log.debug("Attempt {}/{} failed: {}", attempt, maxAttempts, e.getMessage());
                    if (attempt >= maxAttempts) {
                        return Promise.ofException(e);
                    }
                    long nextDelay = Math.min(delayMs * (1L << (attempt - 1)), 30_000L);
                    return io.activej.promise.Promises.delay(nextDelay,
                            retryLoop(supplier, attempt + 1, maxAttempts, delayMs));
                });
    }

    // =========================================================================
    // withTimeout
    // =========================================================================

    /**
     * Enforce a deadline on a promise. If the promise does not complete within
     * {@code timeout}, it is cancelled and an exception is raised.
     *
     * <p>Uses the ActiveJ event-loop scheduler; must be called from within an
     * ActiveJ event-loop context.
     *
     * @param promise the promise to guard
     * @param timeout maximum allowed duration
     * @param label   descriptive name for the operation used in the error message
     * @param <T>     the result type
     * @return a new promise that fails with {@link java.util.concurrent.TimeoutException}
     *         if the deadline is exceeded
     */
    public static <T> Promise<T> withTimeout(
            Promise<T> promise,
            Duration timeout,
            String label) {

        return Promises.timeout(timeout, promise)
                .mapException(e -> {
                    if (e instanceof io.activej.async.exception.AsyncTimeoutException) {
                        return new java.util.concurrent.TimeoutException(
                                "Operation '" + label + "' timed out after " + timeout.toMillis() + "ms");
                    }
                    return e;
                });
    }

    // =========================================================================
    // withFallback
    // =========================================================================

    /**
     * Return a fallback value when a promise fails, converting the failure to a
     * successful resolution.
     *
     * <p>The {@code fallbackSupplier} is invoked only on failure; it should not
     * throw — if it does the new exception replaces the original one.
     *
     * @param promise          the primary promise
     * @param fallbackSupplier produces the fallback value on failure
     * @param <T>              the result type
     * @return a promise that always resolves (never fails)
     */
    public static <T> Promise<T> withFallback(
            Promise<T> promise,
            Function<Throwable, T> fallbackSupplier) {

        return promise.then(
                Promise::of,
                e -> {
                    try {
                        return Promise.of(fallbackSupplier.apply(e));
                    } catch (Exception fallbackEx) {
                        log.warn("Fallback supplier threw: {}", fallbackEx.getMessage());
                        return Promise.ofException(fallbackEx);
                    }
                });
    }

    // =========================================================================
    // withBlocking
    // =========================================================================

    /**
     * Run a blocking operation on the supplied {@code executor} and return an
     * ActiveJ {@link Promise}.
     *
     * <p>This method wraps {@link Promise#ofBlocking(Executor, io.activej.common.function.RunnableEx)}
     * with a strongly-typed return signature.
     *
     * <p>Never call this method from within a blocking operation — nesting
     * {@code withBlocking} calls will exhaust the thread pool.
     *
     * @param executor the executor that will run the blocking call (e.g. a
     *                 bounded thread pool, never the event-loop thread)
     * @param task     the blocking computation
     * @param <T>      the result type
     * @return a promise that resolves on the event loop when the task completes
     */
    public static <T> Promise<T> withBlocking(
            Executor executor,
            CheckedSupplier<T> task) {

        return Promise.ofBlocking(executor, task::get);
    }

    // =========================================================================
    // Functional Interface
    // =========================================================================

    /** Checked supplier for use with {@link #withBlocking}. */
    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Exception;
    }
}
