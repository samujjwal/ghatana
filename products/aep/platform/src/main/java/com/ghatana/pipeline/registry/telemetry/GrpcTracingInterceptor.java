package com.ghatana.pipeline.registry.telemetry;

import io.activej.inject.annotation.Inject;
import io.activej.inject.annotation.Provides;
import io.grpc.Contexts;
import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;


/**
 * gRPC server interceptor for distributed tracing.
 *
 * <p>Purpose: Extracts trace context from incoming gRPC requests and creates
 * child spans for request processing. Integrates with OpenTelemetry for
 * distributed tracing across service boundaries.</p>
 *
 * @doc.type class
 * @doc.purpose Enables OpenTelemetry distributed tracing for gRPC calls
 * @doc.layer product
 * @doc.pattern Interceptor
 * @since 2.0.0
 */
public class GrpcTracingInterceptor implements ServerInterceptor {
    private static final String GRPC_METHOD = "grpc.method";
    private static final String GRPC_STATUS_CODE = "grpc.status_code";
    private static final String GRPC_ERROR_MESSAGE = "grpc.error_message";

    private final Tracer tracer;
    private final OpenTelemetry openTelemetry;

    @Inject
    public GrpcTracingInterceptor(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer("com.ghatana.pipeline.registry.grpc");
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        // Extract the context from the gRPC metadata
        Context extractedContext = openTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.current(), headers, new TextMapGetter<Metadata>() {
                    @Override
                    public Iterable<String> keys(Metadata carrier) {
                        return () -> carrier.keys().iterator();
                    }

                    @Override
                    public String get(Metadata carrier, String key) {
                        Metadata.Key<String> headerKey = Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER);
                        if (carrier.containsKey(headerKey)) {
                            return carrier.get(headerKey);
                        }
                        return null;
                    }
                });

        String methodName = call.getMethodDescriptor().getFullMethodName();

        // Start a new span for this request
        Span span = tracer.spanBuilder(methodName)
                .setParent(extractedContext)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute(GRPC_METHOD, methodName)
                .startSpan();

        // Create a new context with the current span
        Context contextWithSpan = extractedContext.with(span);

        // Wrap the call to add tracing
        ServerCall<ReqT, RespT> tracingCall = new ForwardingServerCall.SimpleForwardingServerCall<ReqT, RespT>(call) {
            @Override
            public void sendMessage(RespT message) {
                try (Scope ignored = contextWithSpan.makeCurrent()) {
                    super.sendMessage(message);
                }
            }

            @Override
            public void close(Status status, Metadata trailers) {
                try (Scope ignored = contextWithSpan.makeCurrent()) {
                    if (!status.isOk()) {
                        span.setAttribute(GRPC_STATUS_CODE, status.getCode().name());
                        if (status.getDescription() != null) {
                            span.setAttribute(GRPC_ERROR_MESSAGE, status.getDescription());
                        }
                        span.recordException(status.asRuntimeException());
                    }
                    super.close(status, trailers);
                } finally {
                    span.end();
                }
            }
        };

        // Create a listener that will close the span when the call is complete
        return Contexts.interceptCall(
                io.grpc.Context.current(),
                tracingCall,
                headers,
                next);
    }

    @Provides
    public static GrpcTracingInterceptor create(OpenTelemetry openTelemetry) {
        return new GrpcTracingInterceptor(openTelemetry);
    }
}
