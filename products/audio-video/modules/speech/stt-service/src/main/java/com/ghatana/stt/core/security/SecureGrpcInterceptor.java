package com.ghatana.stt.core.security;

import com.ghatana.platform.security.port.JwtTokenProvider;
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
import java.util.Set;
import java.util.UUID;

/**
 * gRPC server interceptor for authentication and authorization using centralized security libraries.
 *
 * <p>Replaces the legacy GrpcSecurityInterceptor which used manual crypto (security risk).
 * Now uses:
 * <ul>
 *   <li>{@link JwtTokenProvider} from libs:auth for token validation</li>
 *   <li>{@link SecurityGateway} from libs:security for RBAC enforcement</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose gRPC security interceptor using centralized libraries
 * @doc.layer security
 * @doc.pattern Interceptor
 */
public final class SecureGrpcInterceptor implements ServerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(SecureGrpcInterceptor.class);

    private static final Metadata.Key<String> AUTHORIZATION_KEY =
        Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    private static final Metadata.Key<String> CORRELATION_ID_KEY =
        Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);

    public static final Context.Key<String> USER_ID_KEY = Context.key("userId");
    public static final Context.Key<Set<String>> ROLES_KEY = Context.key("roles");
    public static final Context.Key<String> CORRELATION_KEY = Context.key("correlationId");

    private final JwtTokenProvider tokenProvider;
    private final SecurityGateway securityGateway;
    private final boolean enabled;
    private final Set<String> publicMethods;
    private final String resourceName;

    /**
     * Creates a secure gRPC interceptor using centralized security libraries.
     *
     * @param tokenProvider JWT token provider from libs:auth
     * @param securityGateway Security gateway from libs:security
     * @param enabled whether security is enabled
     * @param resourceName resource name for RBAC (e.g., "stt-service")
     */
    public SecureGrpcInterceptor(
            JwtTokenProvider tokenProvider,
            SecurityGateway securityGateway,
            boolean enabled,
            String resourceName) {
        this.tokenProvider = tokenProvider;
        this.securityGateway = securityGateway;
        this.enabled = enabled;
        this.resourceName = resourceName;
        this.publicMethods = Set.of(
            "grpc.health.v1.Health/Check",
            "grpc.health.v1.Health/Watch",
            "com.ghatana.stt.grpc.STTService/HealthCheck",
            "com.ghatana.stt.grpc.STTService/GetStatus"
        );
        LOG.info("Secure gRPC interceptor initialized, enabled={}, resource={}", enabled, resourceName);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String methodName = call.getMethodDescriptor().getFullMethodName();
        String correlationId = headers.get(CORRELATION_ID_KEY);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }

        // Skip auth for public methods
        if (publicMethods.contains(methodName)) {
            Context ctx = Context.current().withValue(CORRELATION_KEY, correlationId);
            return Contexts.interceptCall(ctx, call, headers, next);
        }

        // Skip if security is disabled (dev mode only)
        if (!enabled) {
            Context ctx = Context.current()
                .withValue(CORRELATION_KEY, correlationId)
                .withValue(USER_ID_KEY, "anonymous")
                .withValue(ROLES_KEY, Set.of("admin"));
            LOG.warn("Security disabled - allowing anonymous access to {}", methodName);
            return Contexts.interceptCall(ctx, call, headers, next);
        }

        // Extract authorization header
        String authHeader = headers.get(AUTHORIZATION_KEY);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            LOG.warn("Missing or invalid authorization header for method: {}", methodName);
            call.close(Status.UNAUTHENTICATED.withDescription("Missing authorization token"), headers);
            return new ServerCall.Listener<>() {};
        }

        String token = authHeader.substring(7);

        // Validate token using centralized JwtTokenProvider (libs:security)
        if (!tokenProvider.validateToken(token)) {
            LOG.warn("JWT validation failed for method {}", methodName);
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid token"), headers);
            return new ServerCall.Listener<>() {};
        }

        String userId = tokenProvider.getUserIdFromToken(token).orElse("unknown");
        List<String> roles = tokenProvider.getRolesFromToken(token);

        // Enforce RBAC using SecurityGateway (libs:security)
        String permission = extractPermission(methodName);
        try {
            boolean allowed = securityGateway.evaluatePolicy(
                userId,
                Set.copyOf(roles),
                resourceName,
                permission
            ).getResult();  // Block for gRPC synchronous call

            if (!allowed) {
                LOG.warn("Access denied for user {} to method {} (requires {}:{})",
                    userId, methodName, resourceName, permission);
                call.close(Status.PERMISSION_DENIED.withDescription("Insufficient permissions"), headers);
                return new ServerCall.Listener<>() {};
            }
        } catch (Exception e) {
            LOG.error("RBAC evaluation failed for method {}: {}", methodName, e.getMessage(), e);
            call.close(Status.INTERNAL.withDescription("Authorization error"), headers);
            return new ServerCall.Listener<>() {};
        }

        // Create context with user info
        Context ctx = Context.current()
            .withValue(CORRELATION_KEY, correlationId)
            .withValue(USER_ID_KEY, userId)
            .withValue(ROLES_KEY, Set.copyOf(roles));

        LOG.debug("Authenticated request: user={}, method={}, correlationId={}",
            userId, methodName, correlationId);

        return Contexts.interceptCall(ctx, call, headers, next);
    }

    /**
     * Extract permission from gRPC method name.
     * Maps method names to RBAC permissions (read, write, admin).
     *
     * @param methodName full gRPC method name
     * @return permission (read, write, admin)
     */
    private String extractPermission(String methodName) {
        String method = methodName.substring(methodName.lastIndexOf('/') + 1);

        // Map methods to permissions
        return switch (method) {
            case "Transcribe", "TranscribeStream", "GetTranscription" -> "read";
            case "CreateModel", "UpdateModel", "DeleteModel" -> "write";
            case "SetConfig", "RestartService" -> "admin";
            default -> "read";  // Default to read permission
        };
    }
}
