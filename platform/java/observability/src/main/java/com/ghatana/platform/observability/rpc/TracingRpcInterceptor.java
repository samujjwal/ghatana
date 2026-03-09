package com.ghatana.platform.observability.rpc;

import io.activej.promise.Promise;
import io.activej.promise.SettablePromise;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;

import java.util.Map;
import java.util.function.Function;

/**
 * Generic RPC interceptor for OpenTelemetry trace propagation.
 * Handles both client and server-side tracing with context propagation.
 * 
 * <p>This is a generic implementation that can be adapted to different RPC frameworks.</p>
 */
public class TracingRpcInterceptor {
    
    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    
    // TextMapGetter for extracting trace context from RPC headers
    private static final TextMapGetter<Map<String, String>> GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
            return carrier != null ? carrier.keySet() : null;
        }
        
        @Override
        public String get(Map<String, String> carrier, String key) {
            return carrier != null ? carrier.get(key) : null;
        }
    };
    
    // TextMapSetter for injecting trace context into RPC headers
    private static final TextMapSetter<Map<String, String>> SETTER = new TextMapSetter<>() {
        @Override
        public void set(Map<String, String> carrier, String key, String value) {
            if (carrier != null && key != null && value != null) {
                carrier.put(key, value);
            }
        }
    };
    
    /**
     * Creates a new TracingRpcInterceptor with the given OpenTelemetry and Tracer instances.
     *
     * @param openTelemetry the OpenTelemetry instance to use for context propagation
     * @param tracer the Tracer to use for creating spans
     */
    public TracingRpcInterceptor(OpenTelemetry openTelemetry, Tracer tracer) {
        this.openTelemetry = openTelemetry;
        this.tracer = tracer != null ? tracer : openTelemetry.getTracer("com.ghatana.observability.rpc");
    }
    
    /**
     * Creates a new TracingRpcInterceptor with the given OpenTelemetry instance.
     * A default tracer will be created using the OpenTelemetry instance.
     *
     * @param openTelemetry the OpenTelemetry instance to use for context propagation and tracing
     */
    public TracingRpcInterceptor(OpenTelemetry openTelemetry) {
        this(openTelemetry, null);
    }
    
    /**
     * Creates a client-side interceptor function for handling outbound RPC calls.
     * 
     * @param <T> the type of the request
     * @param <R> the type of the response
     * @return a function that can be used to wrap RPC client calls with tracing
     */
    public <T, R> Function<T, Promise<R>> clientInterceptor() {
        return request -> {
            // Start client span
            Span span = tracer.spanBuilder("rpc.client.call")
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("rpc.system", "custom")
                .startSpan();
            
            SettablePromise<R> promise = new SettablePromise<>();
            
            try (Scope scope = span.makeCurrent()) {
                // Inject trace context into request headers
                Map<String, String> headers = new java.util.HashMap<>();
                openTelemetry.getPropagators().getTextMapPropagator()
                    .inject(Context.current(), headers, SETTER);
                
                // Add request details to span
                if (request != null) {
                    span.setAttribute("rpc.request.type", request.getClass().getSimpleName());
                }
                
                // In a real implementation, you would make the RPC call here
                // and return a Promise that completes when the response is received
                
                return promise.whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        span.setStatus(StatusCode.ERROR, throwable.getMessage());
                        span.recordException(throwable);
                    } else {
                        span.setStatus(StatusCode.OK);
                        if (result != null) {
                            span.setAttribute("rpc.response.type", result.getClass().getSimpleName());
                        }
                    }
                    span.end();
                });
            } catch (Exception e) {
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, e.getMessage());
                span.end();
                promise.setException(e);
                return promise;
            }
        };
    }
    
    /**
     * Creates a server-side interceptor function for handling inbound RPC calls.
     * 
     * @param <T> the type of the request
     * @param <R> the type of the response
     * @param handler the actual RPC handler to wrap with tracing
     * @return a function that can be used to handle RPC server calls with tracing
     */
    public <T, R> Function<T, Promise<R>> serverHandler(Function<T, Promise<R>> handler) {
        return request -> {
            // Extract trace context from request
            Map<String, String> headers = new java.util.HashMap<>();
            
            Context extractedContext = openTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.current(), headers, GETTER);
            
            // Start server span with extracted context as parent
            String spanName = String.format("rpc.server.%s", 
                request != null ? request.getClass().getSimpleName() : "unknown");
                
            Span span = tracer.spanBuilder(spanName)
                .setParent(extractedContext)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("rpc.system", "custom")
                .startSpan();
                
            if (request != null) {
                span.setAttribute("rpc.request.type", request.getClass().getName());
            }
            
            try (Scope scope = span.makeCurrent()) {
                Promise<R> result = handler.apply(request);
                
                return result.whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        span.setStatus(StatusCode.ERROR, throwable.getMessage());
                        span.recordException(throwable);
                    } else {
                        span.setStatus(StatusCode.OK);
                        if (response != null) {
                            span.setAttribute("rpc.response.type", response.getClass().getSimpleName());
                        }
                    }
                    span.end();
                });
            } catch (Exception e) {
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, e.getMessage());
                span.end();
                return Promise.ofException(e);
            }
        };
    }
    
    /**
     * Extracts trace context from a map of headers.
     * This is a convenience method that can be used by RPC implementations.
     *
     * @param headers the headers containing trace context
     * @return the extracted OpenTelemetry Context
     */
    public Context extractContextFromHeaders(Map<String, String> headers) {
        if (headers == null) {
            return Context.current();
        }
        return openTelemetry.getPropagators().getTextMapPropagator()
            .extract(Context.current(), headers, GETTER);
    }
    
    /**
     * Injects trace context into a map of headers.
     * This is a convenience method that can be used by RPC implementations.
     *
     * @param context the OpenTelemetry Context containing the current span
     * @param headers the headers map to inject trace context into
     */
    public void injectContextToHeaders(Context context, Map<String, String> headers) {
        if (headers != null) {
            openTelemetry.getPropagators().getTextMapPropagator()
                .inject(context, headers, SETTER);
        }
    }
    
    /**
     * Interface for RPC messages that support tracing headers.
     * This can be implemented by RPC message classes that need to carry tracing information.
     *
     * @param <T> the type of the message payload
     
 *
 * @doc.type interface
 * @doc.purpose Tracing capable rpc message
 * @doc.layer core
 * @doc.pattern Interface
*/
    public interface TracingCapableRpcMessage<T> {
        /**
         * Sets the tracing headers on the message.
         *
         * @param headers a map of header key-value pairs
         */
        void setHeaders(Map<String, String> headers);
        
        /**
         * Gets the tracing headers from the message.
         *
         * @return a map of header key-value pairs
         */
        Map<String, String> getHeaders();
    }
    
    /**
     * A simple implementation of TracingCapableRpcMessage that can be used as a base class
     * or wrapper for existing message types.
     *
     * @param <T> the type of the message payload
     */
    public static class SimpleTracingRpcMessage<T> implements TracingCapableRpcMessage<T> {
        private final Map<String, String> headers = new java.util.HashMap<>();
        private final T payload;
        
        /**
         * Creates a new SimpleTracingRpcMessage with the given payload.
         *
         * @param payload the message payload
         */
        public SimpleTracingRpcMessage(T payload) {
            this.payload = payload;
        }
        
        @Override
        public void setHeaders(Map<String, String> headers) {
            if (headers != null) {
                this.headers.putAll(headers);
            }
        }
        
        @Override
        public Map<String, String> getHeaders() {
            return new java.util.HashMap<>(headers);
        }
        
        /**
         * Gets the payload of the message.
         *
         * @return the message payload
         */
        public T getPayload() {
            return payload;
        }
        
        /**
         * Creates a new SimpleTracingRpcMessage with the given payload and headers.
         *
         * @param <T> the type of the payload
         * @param payload the message payload
         * @param headers the tracing headers
         * @return a new SimpleTracingRpcMessage instance
         */
        public static <T> SimpleTracingRpcMessage<T> of(T payload, Map<String, String> headers) {
            SimpleTracingRpcMessage<T> message = new SimpleTracingRpcMessage<>(payload);
            if (headers != null) {
                message.setHeaders(headers);
            }
            return message;
        }
    }
}
