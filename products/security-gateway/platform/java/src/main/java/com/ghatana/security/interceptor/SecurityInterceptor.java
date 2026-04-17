package com.ghatana.security.interceptor;

import com.ghatana.platform.governance.security.Principal;
import com.ghatana.platform.security.ratelimit.DefaultRateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiter;
import com.ghatana.platform.security.ratelimit.RateLimiterConfig;
import com.ghatana.platform.http.security.filter.TenantExtractor;
import com.ghatana.security.audit.SecurityAuditLogger;
import com.ghatana.platform.security.jwt.JwtTokenProvider;
import com.ghatana.platform.security.rbac.PolicyService;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpMethod;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Interceptor that enforces security policies on incoming HTTP requests.
 * This includes authentication, authorization, and other security-related checks.
 * Migrated to use PolicyService for modern policy-based access control.
 *
 * @doc.type class
 * @doc.purpose HTTP security enforcement — authentication, authorization, rate limiting
 * @doc.layer product
 * @doc.pattern Interceptor
 */
public class SecurityInterceptor {
    private static final Logger logger = LoggerFactory.getLogger(SecurityInterceptor.class);
    private static final HttpHeader CORRELATION_ID_HEADER = HttpHeaders.of("X-Correlation-ID");
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    private final PolicyService policyService;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityAuditLogger auditLogger;
    private final int rateLimit;
    private final RateLimiter rateLimiter;
    private final RateLimiter perUserRateLimiter;

    /**
     * Creates a new SecurityInterceptor with the specified PolicyService.
     *
     * @param policyService the PolicyService to use for authorization checks
     */
    public SecurityInterceptor(PolicyService policyService,
                               JwtTokenProvider jwtTokenProvider,
                               SecurityAuditLogger auditLogger,
                               int rateLimit,
                               int rateLimitWindowInSeconds) {
        this.policyService = policyService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.auditLogger = auditLogger;
        this.rateLimit = rateLimit;
        this.rateLimiter = rateLimit > 0
            ? DefaultRateLimiter.create(
                RateLimiterConfig.builder()
                    .maxRequestsPerMinute(rateLimit)
                    .burstSize(rateLimit)
                    .windowDuration(Duration.ofSeconds(rateLimitWindowInSeconds))
                    .build()
            )
            : null;
        // Per-user rate limiter tracks by authenticated principal ID, shared across all client IPs.
        // Uses the same limits as the IP-level limiter so that a single user cannot circumvent
        // IP-based throttling by rotating IP addresses (e.g., via proxies).
        this.perUserRateLimiter = rateLimit > 0
            ? DefaultRateLimiter.create(
                RateLimiterConfig.builder()
                    .maxRequestsPerMinute(rateLimit)
                    .burstSize(rateLimit)
                    .windowDuration(Duration.ofSeconds(rateLimitWindowInSeconds))
                    .build()
            )
            : null;
    }

    /**
     * Intercepts and processes an HTTP request, applying security checks.
     *
     * @param request the HTTP request to intercept
     * @param next the next handler in the chain
     * @return a promise that will be completed with the HTTP response
     */
    public Promise<HttpResponse> intercept(HttpRequest request, @NotNull NextHandler next) {
        String correlationId = resolveCorrelationId(request);
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);

        Promise<HttpResponse> responsePromise;
        // Skip security checks for OPTIONS requests (preflight CORS)
        if (request.getMethod() == HttpMethod.OPTIONS) {
            responsePromise = next.handle(request);
            return responsePromise
                    .map(response -> withCorrelationHeader(response, correlationId))
                    .whenComplete(($, e) -> MDC.remove(CORRELATION_ID_MDC_KEY));
        }

        String path = request.getPath();
        String method = request.getMethod().toString();

        // Rate limiting check (based on client IP from X-Forwarded-For or remote address header)
        String clientIp = getClientIp(request);
        RateLimiter.AcquireResult rateLimitResult = acquireRateLimit(clientIp);
        if (rateLimitResult != null && !rateLimitResult.allowed()) {
            logger.warn("Rate limit exceeded for {} {} from {}", method, path, clientIp);
            auditLogger.logRateLimitExceeded(clientIp, path, method);
            responsePromise = Promise.of(addRateLimitHeaders(HttpResponse.ofCode(429), rateLimitResult)
                .withHeader(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimitResult.retryAfterSeconds()))
                .build());
            return responsePromise
                .map(response -> withCorrelationHeader(response, correlationId))
                .whenComplete(($, e) -> MDC.remove(CORRELATION_ID_MDC_KEY));
        }

        // Extract user identity and roles from JWT token
        String authHeader = request.getHeader(HttpHeaders.of("Authorization"));
        String userId = null;
        Set<String> userRoles = Set.of();
        if (isPublicEndpoint(path, method)) {
            // Bypass auth but still add security and rate-limit headers
            responsePromise = Promise.of(addRateLimitHeaders(addSecurityHeaders(HttpResponse.ok200()), rateLimitResult).build());
            return responsePromise
                    .map(response -> withCorrelationHeader(response, correlationId))
                    .whenComplete(($, e) -> MDC.remove(CORRELATION_ID_MDC_KEY));
        }
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtTokenProvider.validateToken(token)) {
                userId = jwtTokenProvider.getUserIdFromToken(token).orElse(null);
                userRoles = Set.copyOf(jwtTokenProvider.getRolesFromToken(token));
                auditLogger.logAuthenticationSuccess(userId, "JWT");
            } else {
                auditLogger.logAuthenticationFailure(null, "JWT", "Invalid or expired token");
                responsePromise = Promise.of(addSecurityHeaders(HttpResponse.unauthorized401("Unauthorized")).build());
                return responsePromise
                        .map(response -> withCorrelationHeader(response, correlationId))
                        .whenComplete(($, e) -> MDC.remove(CORRELATION_ID_MDC_KEY));
            }
        } else {
            auditLogger.logAuthenticationFailure(null, "JWT", "Missing or invalid token");
            responsePromise = Promise.of(addSecurityHeaders(HttpResponse.unauthorized401("Unauthorized")).build());
            return responsePromise
                    .map(response -> withCorrelationHeader(response, correlationId))
                    .whenComplete(($, e) -> MDC.remove(CORRELATION_ID_MDC_KEY));
        }

        // Check if the user is authenticated
        if (userId == null || userId.isEmpty()) {
            logger.warn("Unauthenticated access attempt to {} {}", method, path);
            responsePromise = Promise.of(addSecurityHeaders(HttpResponse.unauthorized401("Unauthorized")).build());
            return responsePromise
                    .map(response -> withCorrelationHeader(response, correlationId))
                    .whenComplete(($, e) -> MDC.remove(CORRELATION_ID_MDC_KEY));
        }

        // Per-principal rate limit check — keyed by authenticated userId so that rotating
        // client IPs (proxies, VPNs, mobile networks) cannot defeat the per-IP limit.
        if (perUserRateLimiter != null) {
            RateLimiter.AcquireResult perUserResult = perUserRateLimiter.tryAcquire(userId);
            if (!perUserResult.allowed()) {
                logger.warn("Per-user rate limit exceeded for user {} on {} {}", userId, method, path);
                auditLogger.logRateLimitExceeded(userId, path, method);
                responsePromise = Promise.of(
                    addRateLimitHeaders(HttpResponse.ofCode(429), perUserResult)
                        .withHeader(HttpHeaders.RETRY_AFTER, String.valueOf(perUserResult.retryAfterSeconds()))
                        .build()
                );
                return responsePromise
                    .map(response -> withCorrelationHeader(response, correlationId))
                    .whenComplete(($, e) -> MDC.remove(CORRELATION_ID_MDC_KEY));
            }
        }

        // Check authorization
        String requiredPermission = determineRequiredPermission(request);
        if (requiredPermission != null) {
            // Create a Principal for PolicyService (using "default" as tenantId; extract from header if available)
            String tenantId = TenantExtractor.fromHttpOrDefault(request, "default");
            Principal principal = new Principal(userId, userRoles.stream().toList(), tenantId);

            // Use the request path as the resource (can be customized based on your needs)
            String resource = request.getPath();

            if (!policyService.isAuthorized(principal, requiredPermission, resource)) {
                logger.warn("Unauthorized access attempt by user {} to {} {}", userId, request.getMethod(), request.getPath());
                auditLogger.logAccessDenied(userId, request.getPath(), "Insufficient permissions");
                responsePromise = Promise.of(addSecurityHeaders(HttpResponse.ofCode(403)).build());
                return responsePromise
                        .map(response -> withCorrelationHeader(response, correlationId))
                        .whenComplete(($, e) -> MDC.remove(CORRELATION_ID_MDC_KEY));
            }
        }

        // Success: return 200 OK with security and rate-limit headers
        responsePromise = Promise.of(addRateLimitHeaders(addSecurityHeaders(HttpResponse.ok200()), rateLimitResult).build());
        return responsePromise
                .map(response -> withCorrelationHeader(response, correlationId))
                .whenComplete(($, e) -> MDC.remove(CORRELATION_ID_MDC_KEY));
    }

    private String resolveCorrelationId(HttpRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return correlationId;
    }

    private HttpResponse withCorrelationHeader(HttpResponse response, String correlationId) {
        ByteBuf body = null;
        try {
            body = response.getBody();
        } catch (IllegalStateException ignored) {
            // Response has no body.
        }

        HttpResponse.Builder builder = HttpResponse.ofCode(response.getCode());
        for (Map.Entry<HttpHeader, HttpHeaderValue> entry : response.getHeaders()) {
            builder.withHeader(entry.getKey(), entry.getValue());
        }
        builder.withHeader(CORRELATION_ID_HEADER, correlationId);
        if (body != null && body.readRemaining() > 0) {
            builder.withBody(body);
        }
        return builder.build();
    }

    /**
     * Extracts the user ID from the request (e.g., from JWT token).
     *
     * @param request the HTTP request
     * @return the user ID, or null if not authenticated
     */
    private String extractUserId(HttpRequest request) {
        return request.getHeader(HttpHeaders.of("X-User-Id"));
    }

    /**
     * Extracts the user's roles from the request.
     *
     * @param request the HTTP request
     * @return a set of role names assigned to the user
     */
    private Set<String> extractUserRoles(HttpRequest request) {
        String rolesHeader = request.getHeader(HttpHeaders.of("X-User-Roles"));
        return rolesHeader != null ? Set.of(rolesHeader.split(",")) : Set.of();
    }

    /**
     * Determines the required permission for the given request.
     *
     * @param request the HTTP request
     * @return the required permission, or null if no specific permission is required
     */
    private String determineRequiredPermission(HttpRequest request) {
        String path = request.getPath();
        String method = request.getMethod().toString().toLowerCase();

        // Skip permission check for public endpoints
        if (isPublicEndpoint(path, method)) {
            return null;
        }

        // Map HTTP methods to CRUD operations
        String operation;
        switch (method) {
            case "get":
                operation = "read";
                break;
            case "post":
                operation = "create";
                break;
            case "put":
            case "patch":
                operation = "update";
                break;
            case "delete":
                operation = "delete";
                break;
            default:
                return null;
        }

        // Create a permission string in the format "operation:resource"
        // For example: "read:users", "update:profile", etc.
        String normalized = path;
        if (normalized.startsWith("/api/")) {
            normalized = normalized.substring(4); // strip '/api'
        } else if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        String resource = normalized.replaceAll("/\\d+$", "").replace("/", ":");
        return String.format("%s:%s", operation, resource);
    }

    /**
     * Checks if the endpoint is public and doesn't require authentication.
     *
     * @param path the request path
     * @param method the HTTP method
     * @return true if the endpoint is public, false otherwise
     */
    private boolean isPublicEndpoint(String path, String method) {
        // Add public endpoints that don't require authentication
        return (path.equals("/api/auth/login") && method.equalsIgnoreCase("post")) ||
               path.equals("/api/health") ||
               path.startsWith("/public/");
    }

    /**
     * Adds security headers to the response.
     *
     * @param response the HTTP response
     * @return the response with security headers
     */
    private HttpResponse.Builder addSecurityHeaders(HttpResponse.Builder builder) {
        return builder
                .withHeader(HttpHeaders.of("X-Content-Type-Options"), "nosniff")
                .withHeader(HttpHeaders.of("X-Frame-Options"), "DENY")
                .withHeader(HttpHeaders.of("X-XSS-Protection"), "1; mode=block")
                .withHeader(HttpHeaders.of("Content-Security-Policy"),
                        "default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                                "style-src 'self' 'unsafe-inline'; img-src 'self' data:");
    }

    /**
     * Adds rate limit headers to the response.
     *
     * @param response the HTTP response
     * @param clientIp the client IP address
     * @return the response with rate limit headers
     */
    private HttpResponse.Builder addRateLimitHeaders(
            HttpResponse.Builder builder,
            RateLimiter.AcquireResult rateLimitResult
    ) {
        if (rateLimit <= 0 || rateLimitResult == null) {
            return builder; // Rate limiting disabled
        }
        return builder
                .withHeader(HttpHeaders.of("X-RateLimit-Limit"), String.valueOf(rateLimit))
                .withHeader(HttpHeaders.of("X-RateLimit-Remaining"), String.valueOf(rateLimitResult.remainingTokens()))
                .withHeader(HttpHeaders.of("X-RateLimit-Reset"), String.valueOf(rateLimitResult.resetAtEpochSeconds()));
    }

    private RateLimiter.AcquireResult acquireRateLimit(String clientIp) {
        if (rateLimiter == null) {
            return null;
        }
        return rateLimiter.tryAcquire(clientIp);
    }

    private String getClientIp(HttpRequest request) {
        String forwarded = request.getHeader(HttpHeaders.of("X-Forwarded-For"));
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        try {
            return request.getRemoteAddress() != null ? request.getRemoteAddress().toString() : "unknown";
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }

    /**
     * Functional interface for the next handler in the interceptor chain.
     */
/**
 * Next handler.
 *
 * @doc.type interface
 * @doc.purpose Next handler
 * @doc.layer core
 * @doc.pattern Handler
 */
    @FunctionalInterface
    public interface NextHandler {
        /**
         * Processes the request and returns a promise of the response.
         *
         * @param request the HTTP request to process
         * @return a promise that will be completed with the HTTP response
         */
        Promise<HttpResponse> handle(HttpRequest request);
    }
}
