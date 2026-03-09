package com.ghatana.refactorer.server.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Utility class for distributed tracing operations. Provides convenient methods for creating spans,

 * adding attributes, and handling context propagation.

 *

 * @doc.type class

 * @doc.purpose Simplify propagation of tracing identifiers across async boundaries.

 * @doc.layer product

 * @doc.pattern Utility

 */

public final class TracingUtils {
    private static final String SERVICE_NAME = "polyfix-service";
    private static final Tracer tracer = GlobalOpenTelemetry.getTracer(SERVICE_NAME);

    private TracingUtils() {
        // Utility class
    }

    /**
     * Creates a new span with the given name and kind.
     *
     * @param operationName the name of the operation
     * @param spanKind the kind of span
     * @return a new span builder
     */
    public static SpanBuilder spanBuilder(String operationName, SpanKind spanKind) {
        return tracer.spanBuilder(operationName).setSpanKind(spanKind);
    }

    /**
     * Creates a server span for handling incoming requests.
     *
     * @param operationName the name of the operation
     * @return a new span builder configured for server operations
     */
    public static SpanBuilder serverSpan(String operationName) {
        return spanBuilder(operationName, SpanKind.SERVER);
    }

    /**
     * Creates a client span for outgoing requests.
     *
     * @param operationName the name of the operation
     * @return a new span builder configured for client operations
     */
    public static SpanBuilder clientSpan(String operationName) {
        return spanBuilder(operationName, SpanKind.CLIENT);
    }

    /**
     * Creates an internal span for internal operations.
     *
     * @param operationName the name of the operation
     * @return a new span builder configured for internal operations
     */
    public static SpanBuilder internalSpan(String operationName) {
        return spanBuilder(operationName, SpanKind.INTERNAL);
    }

    /**
     * Executes a supplier within a new span.
     *
     * @param spanName the name of the span
     * @param supplier the supplier to execute
     * @param <T> the return type
     * @return the result of the supplier
     */
    public static <T> T withSpan(String spanName, Supplier<T> supplier) {
        return withSpan(spanName, SpanKind.INTERNAL, supplier);
    }

    /**
     * Executes a supplier within a new span with the specified kind.
     *
     * @param spanName the name of the span
     * @param spanKind the kind of span
     * @param supplier the supplier to execute
     * @param <T> the return type
     * @return the result of the supplier
     */
    public static <T> T withSpan(String spanName, SpanKind spanKind, Supplier<T> supplier) {
        Span span = spanBuilder(spanName, spanKind).startSpan();
        Scope scope = span.makeCurrent();
        try {
            T result = supplier.get();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            scope.close();
            span.end();
        }
    }

    /**
     * Executes a callable within a new span.
     *
     * @param spanName the name of the span
     * @param callable the callable to execute
     * @param <T> the return type
     * @return the result of the callable
     * @throws Exception if the callable throws an exception
     */
    public static <T> T withSpan(String spanName, Callable<T> callable) throws Exception {
        return withSpan(spanName, SpanKind.INTERNAL, callable);
    }

    /**
     * Executes a callable within a new span with the specified kind.
     *
     * @param spanName the name of the span
     * @param spanKind the kind of span
     * @param callable the callable to execute
     * @param <T> the return type
     * @return the result of the callable
     * @throws Exception if the callable throws an exception
     */
    public static <T> T withSpan(String spanName, SpanKind spanKind, Callable<T> callable)
            throws Exception {
        Span span = spanBuilder(spanName, spanKind).startSpan();
        Scope scope = span.makeCurrent();
        try {
            T result = callable.call();
            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            scope.close();
            span.end();
        }
    }

    /**
     * Executes a runnable within a new span.
     *
     * @param spanName the name of the span
     * @param runnable the runnable to execute
     */
    public static void withSpan(String spanName, Runnable runnable) {
        withSpan(spanName, SpanKind.INTERNAL, runnable);
    }

    /**
     * Executes a runnable within a new span with the specified kind.
     *
     * @param spanName the name of the span
     * @param spanKind the kind of span
     * @param runnable the runnable to execute
     */
    public static void withSpan(String spanName, SpanKind spanKind, Runnable runnable) {
        Span span = spanBuilder(spanName, spanKind).startSpan();
        Scope scope = span.makeCurrent();
        try {
            runnable.run();
            span.setStatus(StatusCode.OK);
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            scope.close();
            span.end();
        }
    }

    /**
     * Adds common attributes to the current span.
     *
     * @param spanCustomizer function to customize the span
     */
    public static void customizeCurrentSpan(Consumer<Span> spanCustomizer) {
        Span currentSpan = Span.current();
        if (currentSpan.getSpanContext().isValid()) {
            spanCustomizer.accept(currentSpan);
        }
    }

    /**
     * Adds a tag to the current span.
     *
     * @param key the attribute key
     * @param value the attribute value
     */
    public static void addTag(String key, String value) {
        customizeCurrentSpan(span -> span.setAttribute(key, Objects.requireNonNull(value)));
    }

    /**
     * Adds a tag to the current span.
     *
     * @param key the attribute key
     * @param value the attribute value
     */
    public static void addTag(String key, long value) {
        customizeCurrentSpan(span -> span.setAttribute(key, value));
    }

    /**
     * Adds a tag to the current span.
     *
     * @param key the attribute key
     * @param value the attribute value
     */
    public static void addTag(String key, boolean value) {
        customizeCurrentSpan(span -> span.setAttribute(key, value));
    }

    /**
     * Records an exception in the current span.
     *
     * @param exception the exception to record
     */
    public static void recordException(Throwable exception) {
        customizeCurrentSpan(
                span -> {
                    span.recordException(exception);
                    span.setStatus(StatusCode.ERROR, exception.getMessage());
                });
    }

    /**
     * Gets the current trace ID.
     *
     * @return the current trace ID, or empty string if no active span
     */
    public static String getCurrentTraceId() {
        Span currentSpan = Span.current();
        if (currentSpan.getSpanContext().isValid()) {
            return currentSpan.getSpanContext().getTraceId();
        }
        return "";
    }

    /**
     * Gets the current span ID.
     *
     * @return the current span ID, or empty string if no active span
     */
    public static String getCurrentSpanId() {
        Span currentSpan = Span.current();
        if (currentSpan.getSpanContext().isValid()) {
            return currentSpan.getSpanContext().getSpanId();
        }
        return "";
    }

    /**
 * Common span attributes for HTTP operations. */
    public static final class HttpAttributes {
        public static final String HTTP_METHOD = "http.method";
        public static final String HTTP_URL = "http.url";
        public static final String HTTP_STATUS_CODE = "http.status_code";
        public static final String HTTP_USER_AGENT = "http.user_agent";
        public static final String HTTP_REQUEST_SIZE = "http.request.size";
        public static final String HTTP_RESPONSE_SIZE = "http.response.size";

        private HttpAttributes() {
            // Constants class
        }
    }

    /**
 * Common span attributes for database operations. */
    public static final class DbAttributes {
        public static final String DB_SYSTEM = "db.system";
        public static final String DB_NAME = "db.name";
        public static final String DB_STATEMENT = "db.statement";
        public static final String DB_OPERATION = "db.operation";
        public static final String DB_TABLE = "db.sql.table";

        private DbAttributes() {
            // Constants class
        }
    }

    /**
 * Common span attributes for messaging operations. */
    public static final class MessagingAttributes {
        public static final String MESSAGING_SYSTEM = "messaging.system";
        public static final String MESSAGING_DESTINATION = "messaging.destination";
        public static final String MESSAGING_OPERATION = "messaging.operation";
        public static final String MESSAGING_MESSAGE_ID = "messaging.message.id";

        private MessagingAttributes() {
            // Constants class
        }
    }
}
