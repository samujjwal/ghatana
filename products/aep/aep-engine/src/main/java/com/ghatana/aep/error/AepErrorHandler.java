/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.error;

import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * Standardised error-handling utilities for the AEP engine (AEP-011).
 *
 * <p>Previously, error handling across AEP components was inconsistent:
 * some code used {@code Promise.ofException(...)}, some threw exceptions
 * directly, and others returned error result objects. This class provides
 * a canonical set of helpers so that all AEP code follows a unified pattern.
 *
 * <p><b>Design contract:</b>
 * <ul>
 *   <li>Non-recoverable programming errors (null arguments, closed engine) are still
 *       signalled by throwing directly, as they represent bugs rather than operational
 *       failures.</li>
 *   <li>Operational failures (network errors, unavailable dependencies, transient
 *       failures) are returned as failed {@link Promise} instances so the async
 *       pipeline can handle them gracefully.</li>
 *   <li>All errors are logged with structured context before being propagated.</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Consistent Promise-based error handling for AEP
 * @doc.layer product
 * @doc.pattern Utility
 * @since 1.1.0
 */
public final class AepErrorHandler {

    private static final Logger logger = LoggerFactory.getLogger(AepErrorHandler.class);

    private AepErrorHandler() {}

    /**
     * Wraps a {@link Throwable} as a failed {@link Promise}, logging the error at ERROR level.
     *
     * @param <T>       expected result type
     * @param context   human-readable description of what was being attempted (e.g. the tenant ID
     *                  or operation name)
     * @param operation short label for the operation that failed (e.g. {@code "consent.eval"})
     * @param error     the throwable to wrap; must not be {@code null}
     * @return a Promise that is already resolved with the given error
     */
    public static <T> Promise<T> handle(String context, String operation, Throwable error) {
        logger.error("AEP error in operation={} context={}: {}", operation, context, error.getMessage(), error);
        Exception wrapped = (error instanceof Exception ex) ? ex : new RuntimeException(error);
        return Promise.ofException(wrapped);
    }

    /**
     * Executes the supplied async {@code operation}, catching any synchronous exception thrown
     * during invocation and converting it into a failed {@link Promise}.
     *
     * <p>This helper ensures that code that might throw on entry (before returning a Promise)
     * is safely handled uniformly:
     * <pre>{@code
     * return AepErrorHandler.run("tenant-abc", "pattern.match",
     *     () -> someService.compute(event));
     * }</pre>
     *
     * @param <T>       result type
     * @param context   human-readable context label
     * @param operation operation label for structured logging
     * @param operation promise-returning operation; must not be {@code null}
     * @return the Promise from the supplier, or a failed Promise if the supplier throws
     */
    public static <T> Promise<T> run(String context, String operation,
                                     Supplier<Promise<T>> supplier) {
        try {
            return supplier.get()
                .whenException(e -> logger.error(
                    "AEP async failure in operation={} context={}: {}",
                    operation, context, e.getMessage(), e));
        } catch (Exception e) {
            return handle(context, operation, e);
        }
    }

    /**
     * Logs a warning (non-fatal) for an operational condition that the caller is handling.
     *
     * @param context   context label
     * @param operation operation label
     * @param message   warning message template (SLF4J format)
     * @param args      substitution arguments for the message template
     */
    public static void warn(String context, String operation, String message, Object... args) {
        logger.warn("AEP warning in operation={} context={}: " + message, prepend(context, operation, args));
    }

    // -------------------------------------------------------------------------

    private static Object[] prepend(String context, String operation, Object[] args) {
        Object[] combined = new Object[args.length + 2];
        combined[0] = context;
        combined[1] = operation;
        System.arraycopy(args, 0, combined, 2, args.length);
        return combined;
    }
}
