package com.ghatana.datacloud.observability;

import io.activej.promise.Promise;
import org.slf4j.MDC;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Utility for propagating SLF4J MDC context across {@code Promise.ofBlocking}
 * boundaries in ActiveJ-based storage connectors.
 *
 * <p>ActiveJ's {@link Promise#ofBlocking(Executor, io.activej.async.function.BlockingSupplier)}
 * dispatches the blocking computation to a separate thread pool (typically a virtual-thread
 * executor). The calling thread's MDC context — which holds structured logging fields such as
 * {@code requestId}, {@code tenantId}, and {@code traceId} — is <em>not</em> automatically
 * inherited by the worker thread. Without propagation, log statements emitted inside blocking
 * I/O lambdas lose correlation context.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Before (MDC lost on worker thread):
 * return Promise.ofBlocking(executor, () -> performIo());
 *
 * // After (MDC propagated):
 * Map<String, String> mdcSnapshot = MdcPropagation.capture();
 * return Promise.ofBlocking(executor, () -> MdcPropagation.withContext(mdcSnapshot, () -> performIo()));
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * <p>{@code MDC.getCopyOfContextMap()} produces an immutable snapshot; sharing it across
 * threads is safe. The restore/clear pattern in {@link #withContext} is contained to the
 * worker thread and does not affect the caller thread.
 *
 * @doc.type class
 * @doc.purpose MDC correlation-ID propagation across ActiveJ Promise.ofBlocking boundaries
 * @doc.layer product
 * @doc.pattern Utility
 */
public final class MdcPropagation {

    private MdcPropagation() {
        // utility class — not instantiable
    }

    /**
     * Captures a snapshot of the current thread's MDC context map.
     *
     * <p>Returns an empty map (never {@code null}) when no MDC context is active,
     * so callers can safely pass the result to {@link #withContext} without null checks.
     *
     * @return immutable snapshot of the current thread's MDC map
     */
    public static Map<String, String> capture() {
        Map<String, String> ctx = MDC.getCopyOfContextMap();
        return ctx != null ? Collections.unmodifiableMap(ctx) : Collections.emptyMap();
    }

    /**
     * Restores the supplied MDC snapshot on the current thread, invokes {@code supplier},
     * then clears the restored keys regardless of outcome.
     *
     * <p>Only the keys present in {@code snapshot} are set and cleared — any pre-existing
     * MDC state on the worker thread (e.g. from a thread-pool framework) is preserved.
     *
     * @param <T>      return type of the supplier
     * @param snapshot MDC context captured on the originating thread via {@link #capture()}
     * @param supplier the blocking computation to execute with MDC context restored
     * @return the result of {@code supplier}
     * @throws Exception if {@code supplier} throws
     */
    public static <T> T withContext(Map<String, String> snapshot, BlockingSupplier<T> supplier) throws Exception {
        if (snapshot.isEmpty()) {
            return supplier.get();
        }
        snapshot.forEach(MDC::put);
        try {
            return supplier.get();
        } finally {
            snapshot.keySet().forEach(MDC::remove);
        }
    }

    /**
     * Checked supplier for use inside {@code Promise.ofBlocking} lambdas.
     *
     * @param <T> the result type
     */
    @FunctionalInterface
    public interface BlockingSupplier<T> {
        T get() throws Exception;
    }
}
