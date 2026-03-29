/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.async;

import io.activej.async.exception.AsyncTimeoutException;
import io.activej.promise.Promise;
import io.activej.promise.Promises;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Common ActiveJ {@link Promise} utilities for AEP (AEP-015).
 *
 * <p>Previously, AEP code used at least three styles for promise composition:
 * {@code .then(...)}, {@code .compose(...)}, and {@code .flatMap(...)}. This class
 * establishes a canonical set of helpers that all AEP components should use for
 * non-trivial async compositions, improving readability and consistency.
 *
 * <p><b>Design contract:</b>
 * <ul>
 *   <li>Prefer {@link #compose} for sequential async chaining.</li>
 *   <li>Prefer {@link #allOf} for parallel fan-out.</li>
 *   <li>Use {@link #mapResult} for pure synchronous transformations.</li>
 *   <li>Do not block the event loop; always return a {@link Promise}.</li>
 * </ul>
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * Promise<String> result = AepAsyncUtils.compose(
 *     fetchUser(userId),
 *     user -> fetchPreferences(user.id())
 * ).then(prefs -> buildResponse(prefs));
 *
 * Promise<List<String>> all = AepAsyncUtils.allOf(List.of(p1, p2, p3));
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Canonical async composition utilities for AEP
 * @doc.layer product
 * @doc.pattern Utility
 * @since 1.1.0
 */
public final class AepAsyncUtils {

    private AepAsyncUtils() {}

    /**
     * Sequentially chains two async operations.
     *
     * <p>Equivalent to {@code first.then(fn)} but named for clarity in AEP code.
     *
     * @param <T>    result type of the first promise
     * @param <U>    result type of the chained promise
     * @param first  promise to evaluate first
     * @param next   function returning the next promise given the first result
     * @return composed promise
     */
    public static <T, U> Promise<U> compose(Promise<T> first, Function<T, Promise<U>> next) {
        return first.then(t -> next.apply(t));
    }

    /**
     * Runs all supplied promises in parallel and collects their results in order.
     *
     * <p>If any promise fails, the resulting promise fails immediately.
     *
     * @param <T>      element type
     * @param promises list of promises to run in parallel
     * @return promise of all results, in the same order as input
     */
    public static <T> Promise<List<T>> allOf(List<Promise<T>> promises) {
        return Promises.toList(promises.stream());
    }

    /**
     * Applies a synchronous transformation to a resolved promise value.
     *
     * <p>Avoids wrapping/unwrapping when the transformation cannot fail.
     *
     * @param <T>    input type
     * @param <U>    output type
     * @param source promise to transform
     * @param mapper pure synchronous mapping function
     * @return promise of the mapped value
     */
    public static <T, U> Promise<U> mapResult(Promise<T> source, Function<T, U> mapper) {
        return source.map(t -> mapper.apply(t));
    }

    /**
     * Immediately returns an already-resolved promise.
     *
     * <p>Use instead of {@code Promise.of(value)} to make the intent explicit.
     *
     * @param <T>   value type
     * @param value the resolved value
     * @return resolved promise
     */
    public static <T> Promise<T> resolved(T value) {
        return Promise.of(value);
    }

    /**
     * Immediately returns a promise that has failed with the given exception.
     *
     * @param <T>   expected result type
     * @param cause the failure cause
     * @return rejected promise
     */
    public static <T> Promise<T> failed(Exception cause) {
        return Promise.ofException(cause);
    }

    /**
     * Adds an explicit timeout to an async operation.
     *
     * @param <T>     result type
     * @param source  source promise
     * @param timeout maximum duration to wait
     * @return promise that fails with {@link TimeoutException} when the timeout expires
     */
    public static <T> Promise<T> withTimeout(Promise<T> source, Duration timeout) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(timeout, "timeout must not be null");

        return Promises.timeout(timeout, source).then(
            Promise::of,
            error -> {
                if (error instanceof AsyncTimeoutException) {
                    return Promise.ofException(new TimeoutException(
                        "Async operation timed out after " + timeout));
                }
                return Promise.ofException(error instanceof Exception exception
                    ? exception
                    : new RuntimeException(error));
            }
        );
    }

    /**
     * Adds an explicit timeout to an async operation with contextual error text.
     *
     * @param <T>     result type
     * @param source  source promise
     * @param timeout maximum duration to wait
     * @param context operation label for diagnostics
     * @return promise that fails with contextual {@link TimeoutException}
     */
    public static <T> Promise<T> withTimeout(Promise<T> source, Duration timeout, String context) {
        Objects.requireNonNull(context, "context must not be null");
        return withTimeout(source, timeout).then(
            Promise::of,
            error -> {
                if (error instanceof TimeoutException) {
                    return Promise.ofException(new TimeoutException(
                        context + " timed out after " + timeout));
                }
                return Promise.ofException(error instanceof Exception exception
                    ? exception
                    : new RuntimeException(error));
            }
        );
    }
}
