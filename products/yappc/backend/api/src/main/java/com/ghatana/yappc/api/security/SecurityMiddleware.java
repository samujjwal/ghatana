/**
 * Copyright (c) 2025 Ghatana Technologies
 * YAPPC API - Security Middleware
 *
 * Provides authentication and authorization middleware for YAPPC API endpoints.
 * Implements JWT-based authentication and role-based access control.
 */

package com.ghatana.yappc.api.security;

import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.http.AsyncServlet;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import static com.ghatana.yappc.api.security.HttpResponseFactory.*;

/**
 * Security middleware for YAPPC API.
 *
 * <p>All async operations use ActiveJ {@link Promise} — no {@code CompletableFuture}
 * or blocking {@code .get()} calls are used anywhere in the request path.
 *
 * <p>Features:
 * <ul>
 *   <li>JWT token validation</li>
 *   <li>Role-based access control</li>
 *   <li>Tenant isolation</li>
 *   <li>Request authentication</li>
 *   <li>Response security headers</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose JWT authentication and RBAC middleware
 * @doc.layer product
 * @doc.pattern Middleware, Decorator
 */
public class SecurityMiddleware implements AsyncServlet {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityMiddleware.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityConfig securityConfig;
    private final AsyncServlet delegate;

    // Security headers applied to every response
    private static final Map<String, String> SECURITY_HEADERS = Map.of(
            "X-Content-Type-Options", "nosniff",
            "X-Frame-Options", "DENY",
            "X-XSS-Protection", "1; mode=block",
            "Strict-Transport-Security", "max-age=31536000; includeSubDomains",
            "Content-Security-Policy", "default-src 'self'"
    );

    public SecurityMiddleware(@NotNull JwtTokenProvider jwtTokenProvider,
                              @NotNull SecurityConfig securityConfig,
                              @NotNull AsyncServlet delegate) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.securityConfig = securityConfig;
        this.delegate = delegate;
    }

    @Override
    public Promise<HttpResponse> serve(@NotNull HttpRequest request) {
        try {
            if (requiresAuthentication(request)) {
                return authenticateRequest(request)
                        .then(authenticated -> {
                            if (authenticated) {
                                return authorizeRequest(request)
                                        .then(authorized -> {
                                            if (authorized) {
                                                return delegate.serve(request)
                                                        .map(this::addSecurityHeaders);
                                            }
                                            return Promise.of(createErrorResponse(403, "Access denied"));
                                        });
                            }
                            return Promise.of(createErrorResponse(401, "Authentication required"));
                        });
            }

            return delegate.serve(request).map(this::addSecurityHeaders);

        } catch (Exception e) {
            LOG.error("Security middleware error", e);
            return Promise.of(createErrorResponse(500, "Internal server error"));
        }
    }

    /**
     * Checks if the request requires authentication.
     */
    private boolean requiresAuthentication(@NotNull HttpRequest request) {
        String path = request.getRelativePath();

        List<String> publicPaths = Arrays.asList(
                "/health",
                "/metrics",
                "/api/auth/login",
                "/api/auth/register",
                "/api/docs"
        );

        return publicPaths.stream().noneMatch(path::startsWith);
    }

    /**
     * Authenticates the request using JWT token — fully non-blocking.
     *
     * <p>Uses ActiveJ {@link Promise} returned by {@link JwtTokenProvider#validateToken}
     * without blocking the event loop.
     */
    private Promise<Boolean> authenticateRequest(@NotNull HttpRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            LOG.warn("Missing or invalid Authorization header for path: {}", request.getRelativePath());
            return Promise.of(false);
        }

        String token = authHeader.substring(7); // Remove "Bearer " prefix

        // Bridge Promise → Promise without blocking
        return Promise.ofCallback(cb ->
                jwtTokenProvider.validateToken(token).whenComplete((valid, ex) -> {
                    if (ex != null) {
                        LOG.error("Authentication error", ex);
                        cb.set(false);
                    } else if (Boolean.TRUE.equals(valid)) {
                        LOG.debug("Request authenticated for path: {}", request.getRelativePath());
                        request.attach(UserContext.class, jwtTokenProvider.getUserFromToken(token));
                        cb.set(true);
                    } else {
                        LOG.warn("Invalid token for path: {}", request.getRelativePath());
                        cb.set(false);
                    }
                })
        );
    }

    /**
     * Authorizes the request based on user roles and permissions — fully non-blocking.
     */
    private Promise<Boolean> authorizeRequest(@NotNull HttpRequest request) {
        UserContext user = PlatformCompatibility.getAttached(request, UserContext.class);
        if (user == null) {
            return Promise.of(false);
        }

        String path = request.getRelativePath();
        String method = request.getMethod().name();

        if (!isTenantAccessAllowed(user, request)) {
            LOG.warn("Tenant access denied for user {} to path: {}", user.getUserId(), path);
            return Promise.of(false);
        }

        // hasRequiredPermission is synchronous now — returns Promise.of(...)
        return hasRequiredPermission(user, path, method)
                .map(hasPermission -> {
                    if (!hasPermission) {
                        LOG.warn("Access denied for user {} to {} {}", user.getUserId(), method, path);
                    }
                    return hasPermission;
                });
    }

    /**
     * Checks if user has access to the requested tenant.
     */
    private boolean isTenantAccessAllowed(@NotNull UserContext user, @NotNull HttpRequest request) {
        if (user.getRoles().contains("admin")) {
            return true;
        }

        String requestTenant = request.getHeader(HttpHeaders.of("X-Tenant-Id"));
        if (requestTenant != null) {
            return requestTenant.equals(user.getTenantId());
        }

        return true;
    }

    /**
     * Checks if user has the required permission for the given path and method.
     *
     * <p>Returns {@link Promise} (not {@code CompletableFuture}) for consistency
     * with the ActiveJ async model.
     */
    private Promise<Boolean> hasRequiredPermission(@NotNull UserContext user,
                                                   @NotNull String path,
                                                   @NotNull String method) {
        if (user.getRoles().contains("admin")) {
            return Promise.of(true);
        }

        for (String role : user.getRoles()) {
            List<Permission> permissions = securityConfig.getRolePermissions(role);
            for (Permission permission : permissions) {
                if (pathMatches(path, permission.getPathPattern())
                        && methodMatches(method, permission.getMethods())) {
                    return Promise.of(true);
                }
            }
        }

        for (Permission permission : user.getPermissions()) {
            if (pathMatches(path, permission.getPathPattern())
                    && methodMatches(method, permission.getMethods())) {
                return Promise.of(true);
            }
        }

        return Promise.of(false);
    }

    /**
     * Checks if path matches the permission pattern.
     */
    private boolean pathMatches(@NotNull String path, @NotNull String pattern) {
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return path.startsWith(prefix);
        }
        return path.equals(pattern) || path.matches(pattern.replace("*", ".*"));
    }

    /**
     * Checks if HTTP method matches allowed methods.
     */
    private boolean methodMatches(@NotNull String method, @NotNull List<String> allowedMethods) {
        return allowedMethods.isEmpty() || allowedMethods.contains(method);
    }

    /**
     * Adds security headers to the response.
     *
     * @param response the response to augment
     * @return the same response with security headers applied
     */
    private HttpResponse addSecurityHeaders(@NotNull HttpResponse response) {
        HttpResponse.Builder builder = HttpResponse.ofCode(response.getCode());
        for (var entry : response.getHeaders()) {
            builder.withHeader(entry.getKey(), entry.getValue());
        }
        SECURITY_HEADERS.forEach((key, value) ->
                builder.withHeader(HttpHeaders.of(key), value));
        builder.withBody(response.getBody().slice());
        return builder.build();
    }

    /**
     * Creates an error response.
     */
    private HttpResponse createErrorResponse(int status, String message) {
        String escapedMessage = message.replace("\\", "\\\\").replace("\"", "\\\"");
        return PlatformCompatibility.createJsonResponse(
                status,
                "{\"error\":true,\"message\":\"" + escapedMessage + "\",\"timestamp\":" + System.currentTimeMillis() + "}"
        );
    }

    /**
     * Creates a security middleware instance with default configuration.
     */
    public static SecurityMiddleware create(@NotNull JwtTokenProvider jwtTokenProvider,
                                            @NotNull AsyncServlet delegate) {
        SecurityConfig config = new SecurityConfig();
        return new SecurityMiddleware(jwtTokenProvider, config, delegate);
    }
}
