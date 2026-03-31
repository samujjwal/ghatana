package com.ghatana.yappc.infrastructure.observability.tracing;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe context for storing and propagating correlation IDs across async boundaries.
 *
 * <p><b>Purpose</b><br>
 * Maintains correlation IDs that span multiple service calls, enabling end-to-end
 * request tracing. Works with ActiveJ's async model by using thread-local storage
 * that is properly propagated across promise chains.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // At entry point (HTTP handler)
 * String correlationId = TracingContext.initialize();
 *
 * // Access anywhere in the call chain
 * String currentId = TracingContext.getCorrelationId();
 *
 * // Cleanup after request completes
 * TracingContext.clear();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Correlation ID context management
 * @doc.layer infrastructure
 * @doc.pattern Context, ThreadLocal
 */
public final class TracingContext {

    private static final String CORRELATION_ID_KEY = "correlation-id";
    public static final String TRACE_ID_KEY = "trace-id";
    public static final String SPAN_ID_KEY = "span-id";

    private static final ThreadLocal<ConcurrentMap<String, String>> context = ThreadLocal.withInitial(ConcurrentHashMap::new);

    private TracingContext() {
        // Utility class
    }

    /**
     * Initializes the tracing context with a new correlation ID.
     *
     * @return the generated correlation ID
     */
    public static String initialize() {
        String correlationId = generateId();
        set(CORRELATION_ID_KEY, correlationId);
        set(TRACE_ID_KEY, correlationId);
        set(SPAN_ID_KEY, generateShortId());
        return correlationId;
    }

    /**
     * Initializes the tracing context with an existing correlation ID.
     *
     * @param correlationId the correlation ID to use
     * @throws NullPointerException if correlationId is null
     */
    public static void initialize(String correlationId) {
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        set(CORRELATION_ID_KEY, correlationId);
        set(TRACE_ID_KEY, correlationId);
        set(SPAN_ID_KEY, generateShortId());
    }

    /**
     * Returns the current correlation ID, or empty if not set.
     *
     * @return optional containing correlation ID
     */
    public static Optional<String> getCorrelationId() {
        return Optional.ofNullable(get(CORRELATION_ID_KEY));
    }

    /**
     * Returns the current correlation ID, generating one if not set.
     *
     * @return correlation ID (existing or newly generated)
     */
    public static String getOrCreateCorrelationId() {
        return getCorrelationId().orElseGet(TracingContext::initialize);
    }

    /**
     * Returns the current trace ID.
     *
     * @return trace ID or null if not set
     */
    public static String getTraceId() {
        return get(TRACE_ID_KEY);
    }

    /**
     * Returns the current span ID.
     *
     * @return span ID or null if not set
     */
    public static String getSpanId() {
        return get(SPAN_ID_KEY);
    }

    /**
     * Sets a new span ID, creating a child span.
     *
     * @return the new span ID
     */
    public static String startSpan() {
        String spanId = generateShortId();
        set(SPAN_ID_KEY, spanId);
        return spanId;
    }

    /**
     * Sets a value in the context.
     *
     * @param key the key
     * @param value the value
     */
    public static void set(String key, String value) {
        context.get().put(key, value);
    }

    /**
     * Gets a value from the context.
     *
     * @param key the key
     * @return the value or null if not present
     */
    public static String get(String key) {
        return context.get().get(key);
    }

    /**
     * Clears the entire tracing context.
     * Should be called at the end of request processing.
     */
    public static void clear() {
        context.remove();
    }

    /**
     * Copies the current context for propagation to another thread.
     *
     * @return copy of current context state
     */
    public static ContextSnapshot capture() {
        return new ContextSnapshot(new ConcurrentHashMap<>(context.get()));
    }

    /**
     * Restores a captured context in the current thread.
     *
     * @param snapshot the context snapshot to restore
     */
    public static void restore(ContextSnapshot snapshot) {
        context.set(new ConcurrentHashMap<>(snapshot.values));
    }

    /**
     * Executes a runnable with the given context snapshot.
     *
     * @param snapshot the context to use
     * @param runnable the code to execute
     */
    public static void withContext(ContextSnapshot snapshot, Runnable runnable) {
        ContextSnapshot previous = capture();
        try {
            restore(snapshot);
            runnable.run();
        } finally {
            restore(previous);
        }
    }

    private static String generateId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String generateShortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Immutable snapshot of tracing context for propagation.
     */
    public static final class ContextSnapshot {
        private final ConcurrentMap<String, String> values;

        private ContextSnapshot(ConcurrentMap<String, String> values) {
            this.values = values;
        }

        /**
         * Returns the correlation ID from the snapshot.
         *
         * @return correlation ID or null
         */
        public String getCorrelationId() {
            return values.get(CORRELATION_ID_KEY);
        }

        /**
         * Returns all values in the snapshot.
         *
         * @return map of context values
         */
        public ConcurrentMap<String, String> getValues() {
            return new ConcurrentHashMap<>(values);
        }
    }
}
