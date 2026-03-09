package com.ghatana.platform.observability;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for marking methods and classes for automatic distributed tracing.
 *
 * <p>Traced enables automatic span creation and context propagation for annotated methods
 * and classes. Can be processed by AOP frameworks (AspectJ, Spring AOP) or annotation
 * processors for compile-time or runtime span instrumentation.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * @Traced(value = "process-order", kind = SpanKind.SERVER)
 * public void processOrder(Order order) {
 *     // Span created automatically with name "process-order"
 *     // SpanKind.SERVER for incoming request handling
 * }
 *
 * @Traced(includeParameters = true, includeReturnValue = true)
 * public OrderResult validateOrder(Order order) {
 *     // Span includes method parameters and return value as attributes
 *     return new OrderResult();
 * }
 *
 * @Traced  // Uses method name as span name
 * public void sendNotification() {
 *     // Span named "sendNotification" with SpanKind.INTERNAL (default)
 * }
 * }</pre>
 *
 * <p><b>Attributes:</b></p>
 * <ul>
 *   <li><b>value</b>: Span name (defaults to method name if not specified)</li>
 *   <li><b>kind</b>: SpanKind (SERVER, CLIENT, PRODUCER, CONSUMER, INTERNAL)</li>
 *   <li><b>includeParameters</b>: Capture method parameters as span attributes</li>
 *   <li><b>includeReturnValue</b>: Capture method return value as span attribute</li>
 * </ul>
 *
 * <p><b>Processing:</b> This annotation is typically processed by {@link TracingAspect}
 * for AspectJ-based instrumentation or custom annotation processors.</p>
 *
 * <p><b>Performance:</b> Span creation overhead is minimal (< 1µs). Parameter/return value
 * capture incurs serialization overhead; use sparingly for critical paths.</p>
 *
 * @see TracingAspect for AspectJ-based processing
 * @see TracingProvider for manual span creation
 *
 * @author Platform Team
 * @created 2024-10-05
 * @updated 2025-10-29
 * @version 1.0.0
 * @type Annotation (Declarative Tracing)
 * @purpose Declarative tracing configuration for methods and classes
 * @pattern Annotation pattern, Decorator pattern (via AOP)
 * @responsibility Declare tracing intent (span name, kind, parameter/return capture)
 * @usage Annotate methods/classes requiring automatic tracing; processed by TracingAspect or custom processors
 * @examples See class-level JavaDoc usage example
 * @testing Test annotation processing, span creation, parameter/return value capture in integration tests
 * @notes Retention=RUNTIME for reflection-based processing; Target=METHOD,TYPE
 *
 * @doc.type interface
 * @doc.purpose Annotation for declarative distributed tracing on methods and classes
 * @doc.layer platform
 * @doc.pattern Annotation
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Traced {

    /**
     * The name of the span.
     *
     * <p>If not specified (empty string), the annotated method name will be used.</p>
     *
     * @return the span name (empty string for default)
     */
    String value() default "";

    /**
     * The kind of the span.
     *
     * <p>SpanKind indicates the role of the span in a trace:
     * <ul>
     *   <li>INTERNAL: Default for internal operations</li>
     *   <li>SERVER: Incoming RPC/HTTP requests</li>
     *   <li>CLIENT: Outgoing RPC/HTTP/database calls</li>
     *   <li>PRODUCER: Message publication</li>
     *   <li>CONSUMER: Message consumption</li>
     * </ul>
     *
     * @return the span kind (default: INTERNAL)
     */
    SpanKind kind() default SpanKind.INTERNAL;

    /**
     * Whether to include method parameters in the span attributes.
     *
     * <p><b>Warning:</b> Enabling this may capture sensitive data (passwords, tokens).
     * Use with caution and ensure proper redaction for sensitive parameters.</p>
     *
     * @return true to include method parameters, false otherwise (default: false)
     */
    boolean includeParameters() default false;

    /**
     * Whether to include the method return value in the span attributes.
     *
     * <p><b>Warning:</b> Enabling this may capture sensitive data or large objects.
     * Performance impact depends on return value serialization cost.</p>
     *
     * @return true to include the return value, false otherwise (default: false)
     */
    boolean includeReturnValue() default false;

    /**
     * The kind of the span.
     */
    enum SpanKind {
        /**
         * Default value. Indicates that the span is used internally.
         */
        INTERNAL,

        /**
         * Indicates that the span covers server-side handling of an RPC or other remote request.
         */
        SERVER,

        /**
         * Indicates that the span covers the client-side wrapper around an RPC or other remote request.
         */
        CLIENT,

        /**
         * Indicates that the span describes producer sending a message to a broker.
         * Unlike client and server, there is no direct critical path latency relationship between producer and consumer spans.
         */
        PRODUCER,

        /**
         * Indicates that the span describes consumer receiving a message from a broker.
         * Unlike client and server, there is no direct critical path latency relationship between producer and consumer spans.
         */
        CONSUMER
    }
}
