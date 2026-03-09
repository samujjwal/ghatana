package com.ghatana.platform.governance.security;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * gRPC server interceptor that extracts tenant identity from request metadata
 * and establishes {@link TenantContext} for the duration of the RPC call.
 *
 * <h2>Purpose</h2>
 * Provides multi-tenant isolation for gRPC services by:
 * <ul>
 *   <li>Extracting tenant ID from {@code x-tenant-id} metadata key</li>
 *   <li>Optionally extracting principal from {@code x-principal} / {@code x-roles} metadata</li>
 *   <li>Storing tenant info in both gRPC {@link Context} and thread-local {@link TenantContext}</li>
 *   <li>Automatic cleanup when the RPC completes</li>
 * </ul>
 *
 * <h2>Metadata Keys</h2>
 * <ul>
 *   <li>{@code x-tenant-id} — tenant scope (required in strict mode)</li>
 *   <li>{@code x-principal} — principal identity name</li>
 *   <li>{@code x-roles} — comma-separated role list</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Add to gRPC server
 * Server server = ServerBuilder.forPort(8080)
 *     .addService(myService)
 *     .intercept(TenantGrpcInterceptor.lenient())
 *     .build();
 *
 * // Strict mode — rejects calls without x-tenant-id
 * Server strict = ServerBuilder.forPort(8080)
 *     .addService(myService)
 *     .intercept(TenantGrpcInterceptor.strict())
 *     .build();
 *
 * // Access tenant in service implementation
 * String tenantId = TenantContext.getCurrentTenantId();
 * // Or via gRPC Context
 * String tenantId = TenantGrpcInterceptor.TENANT_ID_CTX_KEY.get();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose gRPC interceptor for tenant context propagation
 * @doc.layer core
 * @doc.pattern Interceptor
 * @see TenantContext
 * @see TenantIsolationHttpFilter
 * @see Principal
 */
public final class TenantGrpcInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantGrpcInterceptor.class);

    /** gRPC metadata key for tenant ID. */
    public static final Metadata.Key<String> TENANT_ID_KEY =
            Metadata.Key.of("x-tenant-id", Metadata.ASCII_STRING_MARSHALLER);

    /** gRPC metadata key for principal name. */
    public static final Metadata.Key<String> PRINCIPAL_KEY =
            Metadata.Key.of("x-principal", Metadata.ASCII_STRING_MARSHALLER);

    /** gRPC metadata key for comma-separated roles. */
    public static final Metadata.Key<String> ROLES_KEY =
            Metadata.Key.of("x-roles", Metadata.ASCII_STRING_MARSHALLER);

    /** gRPC Context key for propagating tenant ID within the call. */
    public static final Context.Key<String> TENANT_ID_CTX_KEY =
            Context.key("tenant-id");

    /** gRPC Context key for propagating the Principal within the call. */
    public static final Context.Key<Principal> PRINCIPAL_CTX_KEY =
            Context.key("principal");

    private final boolean strictMode;

    private TenantGrpcInterceptor(boolean strictMode) {
        this.strictMode = strictMode;
    }

    /**
     * Create an interceptor in lenient mode.
     * Calls without {@code x-tenant-id} fall back to "default-tenant".
     *
     * @return lenient interceptor instance
     */
    public static TenantGrpcInterceptor lenient() {
        return new TenantGrpcInterceptor(false);
    }

    /**
     * Create an interceptor in strict mode.
     * Calls without {@code x-tenant-id} are rejected with {@code UNAUTHENTICATED}.
     *
     * @return strict interceptor instance
     */
    public static TenantGrpcInterceptor strict() {
        return new TenantGrpcInterceptor(true);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String tenantId = headers.get(TENANT_ID_KEY);

        if ((tenantId == null || tenantId.isBlank()) && strictMode) {
            log.warn("Rejected gRPC call {}: missing x-tenant-id metadata (strict mode)",
                    call.getMethodDescriptor().getFullMethodName());
            call.close(Status.UNAUTHENTICATED
                    .withDescription("Missing required x-tenant-id metadata"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        String resolvedTenant = (tenantId != null && !tenantId.isBlank())
                ? tenantId.trim()
                : "default-tenant";

        Principal principal = buildPrincipal(headers, resolvedTenant);

        // Set gRPC Context keys
        Context ctx = Context.current()
                .withValue(TENANT_ID_CTX_KEY, resolvedTenant)
                .withValue(PRINCIPAL_CTX_KEY, principal);

        log.debug("gRPC tenant context set: tenantId={}, principal={}, method={}",
                resolvedTenant, principal.getName(),
                call.getMethodDescriptor().getFullMethodName());

        // Set thread-local TenantContext and delegate
        return Contexts.interceptCall(ctx, call, headers, new ServerCallHandler<>() {
            @Override
            public ServerCall.Listener<ReqT> startCall(
                    ServerCall<ReqT, RespT> serverCall,
                    Metadata metadata) {
                // Establish TenantContext on the handler thread
                TenantContext.setCurrentTenantId(resolvedTenant);
                try (var ignored = TenantContext.scope(principal)) {
                    return next.startCall(serverCall, metadata);
                }
            }
        });
    }

    private Principal buildPrincipal(Metadata headers, String tenantId) {
        String name = headers.get(PRINCIPAL_KEY);
        String rolesValue = headers.get(ROLES_KEY);

        String principalName = (name != null && !name.isBlank())
                ? name.trim()
                : "anonymous";

        List<String> roles = (rolesValue != null && !rolesValue.isBlank())
                ? List.of(rolesValue.split(","))
                : List.of();

        return new Principal(principalName, roles, tenantId);
    }
}
