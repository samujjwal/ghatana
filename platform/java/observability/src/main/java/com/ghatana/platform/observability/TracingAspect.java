package com.ghatana.platform.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * AspectJ aspect for automatic tracing of {@code @Traced} annotated methods.
 *
 * <p>TracingAspect intercepts method calls annotated with {@code @Traced} and creates
 * OpenTelemetry spans with automatic context propagation, exception handling, and
 * parameter/return value capture.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Automatic span creation for {@code @Traced} methods</li>
 *   <li>Custom span names (default: method name)</li>
 *   <li>SpanKind support (INTERNAL, SERVER, CLIENT, PRODUCER, CONSUMER)</li>
 *   <li>Method parameter capture (opt-in via includeParameters=true)</li>
 *   <li>Return value capture (opt-in via includeReturnValue=true)</li>
 *   <li>Exception recording with stack traces</li>
 *   <li>Automatic scope management (makeCurrent/close)</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * @Traced(value = "process-order", kind = SpanKind.SERVER, includeParameters = true)
 * public Order processOrder(String orderId, UserId userId) {
 *     return orderService.process(orderId);
 * }
 * }</pre>
 *
 * <p><b>Pointcut Matching:</b></p>
 * <ul>
 *   <li>Methods annotated with {@code @Traced}</li>
 *   <li>Classes annotated with {@code @Traced} (all methods traced)</li>
 * </ul>
 *
 * <p><b>Captured Attributes:</b></p>
 * <ul>
 *   <li>method.name - Method name</li>
 *   <li>method.class - Declaring class name</li>
 *   <li>method.param.* - Parameters (if includeParameters=true, simple types only)</li>
 *   <li>method.return - Return value (if includeReturnValue=true, simple types only)</li>
 *   <li>error, error.type, error.message - Exception details (on error)</li>
 * </ul>
 *
 * <p><b>Simple Types:</b> String, Boolean, Character, Byte, Short, Integer, Long, Float, Double,
 * and their primitives. Complex objects are not captured (toString() not called to avoid side effects).</p>
 *
 * <p><b>Performance Considerations:</b></p>
 * <ul>
 *   <li>Aspect weaving adds minimal overhead (< 1µs per method)</li>
 *   <li>Parameter capture has serialization cost (use sparingly)</li>
 *   <li>Return value capture has serialization cost (use sparingly)</li>
 *   <li>Avoid capturing large or complex objects</li>
 * </ul>
 *
 * <p><b>Thread-Safety:</b> Thread-safe via OpenTelemetry context API.</p>
 *
 * @see Traced for annotation documentation
 * @see TracingProvider for span creation abstraction
 *
 * @author Platform Team
 * @created 2024-10-01
 * @updated 2025-10-29
 * @version 1.0.0
 * @type AOP Aspect (AspectJ)
 * @purpose Automatic method-level tracing via AOP interception
 * @pattern Aspect-Oriented Programming pattern, Decorator pattern, Interceptor pattern
 * @responsibility Method interception, span creation, attribute capture, exception handling
 * @usage Annotate methods with @Traced; ensure AspectJ weaving enabled (compile-time or load-time)
 * @examples See class-level JavaDoc for @Traced annotation usage
 * @testing Test annotation processing, span creation, parameter/return capture, exception handling
 * @notes Requires AspectJ weaving; supports method and class-level annotations; simple types only for param/return capture
 */
/**
 * Tracing aspect.
 *
 * @doc.type class
 * @doc.purpose Tracing aspect
 * @doc.layer core
 * @doc.pattern Component
 */
@Aspect
@Slf4j
public class TracingAspect {

    private final TracingProvider tracingProvider;

    /**
     * Creates a new TracingAspect with the specified tracing provider.
     *
     * @param tracingProvider The tracing provider
     */
    public TracingAspect(TracingProvider tracingProvider) {
        this.tracingProvider = tracingProvider;
    }

    /**
     * Intercepts method calls annotated with @Traced and creates spans.
     *
     * @param joinPoint The join point
     * @return The result of the method call
     * @throws Throwable If an error occurs
     */
    @Around("@annotation(com.ghatana.observability.Traced) || @within(com.ghatana.observability.Traced)")
    public Object traceMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        Traced traced = method.getAnnotation(Traced.class);
        if (traced == null) {
            traced = method.getDeclaringClass().getAnnotation(Traced.class);
        }
        
        String spanName = traced.value().isEmpty() ? method.getName() : traced.value();
        SpanKind spanKind = convertSpanKind(traced.kind());
        
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("method.name", method.getName());
        attributes.put("method.class", method.getDeclaringClass().getName());
        
        if (traced.includeParameters()) {
            Object[] args = joinPoint.getArgs();
            String[] parameterNames = signature.getParameterNames();
            
            for (int i = 0; i < args.length; i++) {
                String paramName = parameterNames[i];
                Object paramValue = args[i];
                
                if (paramValue != null && isSimpleType(paramValue.getClass())) {
                    attributes.put("method.param." + paramName, paramValue);
                }
            }
        }
        
        Span span = tracingProvider.spanBuilder(spanName)
                .setSpanKind(spanKind)
                .startSpan();
        
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            setSpanAttribute(span, entry.getKey(), entry.getValue());
        }
        
        try (Scope scope = span.makeCurrent()) {
            Object result = joinPoint.proceed();
            
            if (traced.includeReturnValue() && result != null && isSimpleType(result.getClass())) {
                setSpanAttribute(span, "method.return", result);
            }
            
            return result;
        } catch (Throwable t) {
            span.recordException(t);
            span.setAttribute("error", true);
            span.setAttribute("error.type", t.getClass().getName());
            span.setAttribute("error.message", t.getMessage() != null ? t.getMessage() : "");
            throw t;
        } finally {
            span.end();
        }
    }

    /**
     * Converts a Traced.SpanKind to an OpenTelemetry SpanKind.
     *
     * @param kind The Traced.SpanKind
     * @return The OpenTelemetry SpanKind
     */
    private SpanKind convertSpanKind(Traced.SpanKind kind) {
        switch (kind) {
            case SERVER:
                return SpanKind.SERVER;
            case CLIENT:
                return SpanKind.CLIENT;
            case PRODUCER:
                return SpanKind.PRODUCER;
            case CONSUMER:
                return SpanKind.CONSUMER;
            case INTERNAL:
            default:
                return SpanKind.INTERNAL;
        }
    }

    /**
     * Sets a span attribute.
     *
     * @param span The span
     * @param key The attribute key
     * @param value The attribute value
     */
    private void setSpanAttribute(Span span, String key, Object value) {
        if (value instanceof String) {
            span.setAttribute(key, (String) value);
        } else if (value instanceof Boolean) {
            span.setAttribute(key, (Boolean) value);
        } else if (value instanceof Long) {
            span.setAttribute(key, (Long) value);
        } else if (value instanceof Double) {
            span.setAttribute(key, (Double) value);
        } else if (value instanceof Integer) {
            span.setAttribute(key, ((Integer) value).longValue());
        } else if (value instanceof Float) {
            span.setAttribute(key, ((Float) value).doubleValue());
        } else if (value != null) {
            span.setAttribute(key, value.toString());
        }
    }

    /**
     * Checks if a class is a simple type.
     *
     * @param clazz The class
     * @return true if the class is a simple type, false otherwise
     */
    private boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive() ||
                clazz == String.class ||
                clazz == Boolean.class ||
                clazz == Character.class ||
                clazz == Byte.class ||
                clazz == Short.class ||
                clazz == Integer.class ||
                clazz == Long.class ||
                clazz == Float.class ||
                clazz == Double.class;
    }
}
