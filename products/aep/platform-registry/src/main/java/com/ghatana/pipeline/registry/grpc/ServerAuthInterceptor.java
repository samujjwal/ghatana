package com.ghatana.pipeline.registry.grpc;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * gRPC server interceptor for authentication context extraction.
 *
 * <p>Purpose: Extracts tenant and user identifiers from gRPC request metadata
 * headers and propagates them via gRPC Context to downstream service handlers.
 * Enables multi-tenant request processing across the service layer.</p>
 *
 * @doc.type class
 * @doc.purpose Extracts authentication context from gRPC metadata headers
 * @doc.layer product
 * @doc.pattern Interceptor
 * @since 2.0.0
 */
public class ServerAuthInterceptor implements ServerInterceptor {
    public static final Context.Key<String> TENANT_ID_KEY = Context.key("x-tenant-id");
    public static final Context.Key<String> USER_ID_KEY = Context.key("x-user-id");

    private static final Metadata.Key<String> TENANT_HEADER =
            Metadata.Key.of("x-tenant-id", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> USER_HEADER =
            Metadata.Key.of("x-user-id", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String tenantId = headers.get(TENANT_HEADER);
        String userId = headers.get(USER_HEADER);

        Context ctx = Context.current()
                .withValue(TENANT_ID_KEY, tenantId)
                .withValue(USER_ID_KEY, userId);

        return Contexts.interceptCall(ctx, call, headers, next);
    }
}
