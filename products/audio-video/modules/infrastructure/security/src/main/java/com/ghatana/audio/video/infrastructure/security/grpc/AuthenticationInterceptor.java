package com.ghatana.audio.video.infrastructure.security.grpc;

import com.ghatana.platform.governance.security.TenantContext;
import com.ghatana.platform.security.auth.AuthenticationProvider;
import com.ghatana.platform.security.auth.Credentials;
import com.ghatana.platform.security.auth.impl.TokenCredentials;
import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @doc.type class
 * @doc.purpose gRPC interceptor for JWT authentication using platform security
 * @doc.layer infrastructure
 * @doc.pattern Interceptor
 */
public class AuthenticationInterceptor implements ServerInterceptor {
    
    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationInterceptor.class);
    
    public static final Metadata.Key<String> AUTHORIZATION_KEY = 
        Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);
    
    public static final Metadata.Key<String> TENANT_ID_KEY = 
        Metadata.Key.of("X-Tenant-ID", Metadata.ASCII_STRING_MARSHALLER);
    
    public static final Context.Key<AuthContext> AUTH_CONTEXT_KEY = 
        Context.key("auth");
    
    private final AuthenticationProvider authenticationProvider;
    private final Set<String> publicMethods;
    
    public AuthenticationInterceptor(AuthenticationProvider authenticationProvider) {
        this(authenticationProvider, Set.of());
    }
    
    public AuthenticationInterceptor(AuthenticationProvider authenticationProvider, 
                                    Set<String> publicMethods) {
        this.authenticationProvider = Objects.requireNonNull(authenticationProvider, 
            "authenticationProvider cannot be null");
        this.publicMethods = publicMethods != null ? Set.copyOf(publicMethods) : Set.of();
    }
    
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        
        String methodName = call.getMethodDescriptor().getFullMethodName();
        
        // Check if public method
        if (isPublicMethod(methodName)) {
            LOG.debug("Public method accessed: {}", methodName);
            return next.startCall(call, headers);
        }
        
        // Extract tenant ID
        String tenantId = extractTenantId(headers);
        
        // Extract and validate token
        String authHeader = headers.get(AUTHORIZATION_KEY);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            LOG.warn("Missing or invalid authorization header for method: {}", methodName);
            call.close(Status.UNAUTHENTICATED.withDescription("Missing authorization"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        
        String token = authHeader.substring(7);
        
        try {
            // Authenticate using platform provider
            Credentials credentials = new TokenCredentials(token);
            var authResult = authenticationProvider.authenticate(credentials);
            
            // Note: In async context, we'd use Promise. Here we need to adapt.
            Optional<com.ghatana.platform.security.model.User> userOpt = authResult.getResult();
            
            if (userOpt.isEmpty() || !userOpt.get().isAuthenticated()) {
                LOG.warn("Authentication failed for method: {}", methodName);
                call.close(Status.UNAUTHENTICATED.withDescription("Invalid token"), new Metadata());
                return new ServerCall.Listener<>() {};
            }
            
            var user = userOpt.get();
            
            // Verify tenant access if tenantId provided
            if (tenantId != null && !tenantId.isEmpty()) {
                // Additional tenant validation could go here
                TenantContext.setCurrentTenantId(tenantId);
            }
            
            // Build auth context
            AuthContext authContext = new AuthContext(
                user.getUserId(),
                tenantId,
                user.getRoles()
            );
            
            Context ctx = Context.current()
                .withValue(AUTH_CONTEXT_KEY, authContext);
            
            try (MDC.MDCCloseable ignored = MDC.putCloseable("userId", user.getUserId())) {
                LOG.debug("Authenticated user {} for method {}", user.getUserId(), methodName);
                return Contexts.interceptCall(ctx, call, headers, next);
            }
            
        } catch (Exception e) {
            LOG.error("Authentication error for method {}: {}", methodName, e.getMessage(), e);
            call.close(Status.INTERNAL.withDescription("Authentication error"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
    }
    
    private boolean isPublicMethod(String methodName) {
        return publicMethods.contains(methodName) ||
               methodName.endsWith("/HealthCheck") ||
               methodName.endsWith("/GetStatus") ||
               methodName.contains("grpc.health.v1.Health/");
    }
    
    private String extractTenantId(Metadata headers) {
        String tenantId = headers.get(TENANT_ID_KEY);
        if (tenantId != null && !tenantId.isBlank()) {
            return tenantId;
        }
        return System.getenv().getOrDefault("DEFAULT_TENANT_ID", "default");
    }
    
    /**
     * Get current auth context from gRPC Context
     */
    public static Optional<AuthContext> getCurrentAuth() {
        return Optional.ofNullable(AUTH_CONTEXT_KEY.get());
    }
    
    /**
     * Authentication context holder
     */
    public record AuthContext(String userId, String tenantId, Set<String> roles) {
        
        public boolean hasRole(String role) {
            return roles != null && roles.contains(role);
        }
        
        public boolean isAdmin() {
            return hasRole("ADMIN");
        }
    }
}
