package com.ghatana.refactorer.server.observability;

import com.ghatana.platform.http.server.response.ResponseBuilder;
import com.ghatana.refactorer.server.auth.RateLimiter;
import com.ghatana.refactorer.server.auth.RoleBasedAccessControl;
import io.activej.http.AsyncServlet;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Comprehensive HTTP request filter that provides observability, security, and
 * rate limiting.
 *
 * Integrates tracing, metrics, structured logging, authentication,
 * authorization, and rate
 *
 * limiting.
 *
 *
 *
 * <p>
 * <strong>Migration Note:</strong> Updated to use {@link RefactorerMetrics}
 * instead of
 *
 * the legacy MetricsRegistry to align with core/observability abstractions.</p>
 *
 *
 *
 * @doc.type class
 *
 * @doc.purpose Apply request logging concerns before requests reach the REST
 * controllers.
 *
 * @doc.layer product
 *
 * @doc.pattern Filter
 *
 */
public final class RequestLoggingFilter implements AsyncServlet {

    private static final StructuredLogger logger
            = StructuredLogger.getLogger(RequestLoggingFilter.class);

    private final AsyncServlet delegate;
    private final RefactorerMetrics refactorerMetrics;
    private final RateLimiter rateLimiter;
    private final RoleBasedAccessControl rbac;
    private final boolean authRequired;
    private final AtomicLong requestCounter = new AtomicLong(0);

    public RequestLoggingFilter(
            AsyncServlet delegate,
            RefactorerMetrics refactorerMetrics,
            RateLimiter rateLimiter,
            RoleBasedAccessControl rbac,
            boolean authRequired) {
        this.delegate = Objects.requireNonNull(delegate);
        this.refactorerMetrics = Objects.requireNonNull(refactorerMetrics);
        this.rateLimiter = rateLimiter;
        this.rbac = rbac;
        this.authRequired = authRequired;
    }

    @Override
    public Promise<HttpResponse> serve(HttpRequest request) throws Exception {
        String requestId = generateRequestId();
        String clientId = getClientId(request);
        String userId = extractUserId(request);
        String method = request.getMethod().toString();
        String uri = request.getPath();
        Instant startTime = Instant.now();

        // Set up tracing and logging context
        String traceId = TracingUtils.getCurrentTraceId();
        String spanId = TracingUtils.getCurrentSpanId();
        StructuredLogger.setTraceContext(traceId, spanId);
        StructuredLogger.setRequestContext(requestId, userId, null);

        // Start metrics timer
        Timer.Sample timerSample = refactorerMetrics.startRequestTimer();

        try {
            // Rate limiting check
            if (rateLimiter != null && !checkRateLimit(clientId, request)) {
                return handleRateLimitExceeded(
                        request, clientId, requestId, timerSample, method, uri);
            }

            // Authentication and authorization check
            if (authRequired && !checkAuthentication(request, userId)) {
                return handleAuthenticationFailed(request, requestId, timerSample, method, uri);
            }

            // Log request start
            logRequestStart(requestId, method, uri, clientId, userId, request);

            // Add request attributes to current span
            TracingUtils.customizeCurrentSpan(
                    span -> {
                        span.setAttribute(TracingUtils.HttpAttributes.HTTP_METHOD, method);
                        span.setAttribute(TracingUtils.HttpAttributes.HTTP_URL, uri);
                        span.setAttribute("request.id", requestId);
                        span.setAttribute("client.id", clientId);
                        if (userId != null) {
                            span.setAttribute("user.id", userId);
                        }
                    });

            // Process the request
            return delegate.serve(request)
                    .then(
                            response -> {
                                // Log successful response
                                long durationMs
                                = Duration.between(startTime, Instant.now()).toMillis();
                                int statusCode = response.getCode();

                                logRequestComplete(
                                        requestId, method, uri, statusCode, durationMs, userId);
                                recordMetrics(timerSample, method, uri, statusCode);
                                HttpResponse newResponse
                                = addResponseHeaders(response, requestId, traceId);

                                return Promise.of(newResponse);
                            })
                    .whenException(
                            throwable -> {
                                // Log error response
                                long durationMs
                                = Duration.between(startTime, Instant.now()).toMillis();
                                int statusCode = 500;

                                logRequestError(
                                        requestId, method, uri, throwable, durationMs, userId);
                                recordMetrics(timerSample, method, uri, statusCode);
                                TracingUtils.recordException(throwable);
                            });

        } catch (Exception e) {
            // Log synchronous errors
            long durationMs = Duration.between(startTime, Instant.now()).toMillis();
            logRequestError(requestId, method, uri, e, durationMs, userId);
            recordMetrics(timerSample, method, uri, 500);
            TracingUtils.recordException(e);
            throw e;
        } finally {
            // Clean up context
            StructuredLogger.clearContext();
        }
    }

    private String generateRequestId() {
        return "req-"
                + requestCounter.incrementAndGet()
                + "-"
                + UUID.randomUUID().toString().substring(0, 8);
    }

    private String getClientId(HttpRequest request) {
        // Try API key first, then IP address
        String apiKey = request.getHeader(HttpHeaders.of("X-API-Key"));
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            return "api:" + apiKey;
        }

        String clientIp = getClientIpAddress(request);
        return "ip:" + clientIp;
    }

    private String getClientIpAddress(HttpRequest request) {
        // Check for forwarded headers first
        String xForwardedFor = request.getHeader(HttpHeaders.of("X-Forwarded-For"));
        if (xForwardedFor != null && !xForwardedFor.trim().isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader(HttpHeaders.of("X-Real-IP"));
        if (xRealIp != null && !xRealIp.trim().isEmpty()) {
            return xRealIp;
        }

        // Fallback to remote address (this might not be available in ActiveJ)
        return "unknown";
    }

    private String extractUserId(HttpRequest request) {
        // Extract user ID from JWT token or other authentication mechanism
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // This would typically involve JWT parsing
            // For now, return a placeholder
            return "user-from-jwt";
        }
        return null;
    }

    private boolean checkRateLimit(String clientId, HttpRequest request) {
        if (rateLimiter == null) {
            return true;
        }

        boolean allowed = rateLimiter.tryAcquire(clientId);
        if (!allowed) {
            RateLimiter.RateLimitState state = rateLimiter.getState(clientId);
            logger.logSecurityEvent(
                    "rate_limit_exceeded",
                    extractUserId(request),
                    getClientIpAddress(request),
                    false,
                    Map.of("clientId", clientId, "state", state.toString()));
        }
        return allowed;
    }

    private boolean checkAuthentication(HttpRequest request, String userId) {
        if (!authRequired) {
            return true;
        }

        // Skip authentication for public endpoints
        String path = request.getPath();
        if (isPublicEndpoint(path)) {
            return true;
        }

        // Check if user is authenticated
        if (userId == null) {
            logger.logSecurityEvent(
                    "authentication_required",
                    null,
                    getClientIpAddress(request),
                    false,
                    Map.of("path", path));
            return false;
        }

        return true;
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/health")
                || path.startsWith("/metrics")
                || path.startsWith("/public/");
    }

    private Promise<HttpResponse> handleRateLimitExceeded(
            HttpRequest request,
            String clientId,
            String requestId,
            Timer.Sample timerSample,
            String method,
            String uri) {
        RateLimiter.RateLimitState state = rateLimiter.getState(clientId);

        HttpResponse response
                = ResponseBuilder.status(429)
                        .header(
                                HttpHeaders.of("X-RateLimit-Limit"),
                                String.valueOf(state.getLimit()))
                        .header(
                                HttpHeaders.of("X-RateLimit-Remaining"),
                                String.valueOf(state.getRemaining()))
                        .header(
                                HttpHeaders.of("X-RateLimit-Reset"),
                                String.valueOf(state.getResetTimeSeconds()))
                        .header(HttpHeaders.of("Retry-After"), String.valueOf(60)) // 1 minute
                        .text("Rate limit exceeded")
                        .build();

        long durationMs = 0; // Immediate response
        logRequestComplete(requestId, method, uri, 429, durationMs, extractUserId(request));
        recordMetrics(timerSample, method, uri, 429);

        return Promise.of(response);
    }

    private Promise<HttpResponse> handleAuthenticationFailed(
            HttpRequest request,
            String requestId,
            Timer.Sample timerSample,
            String method,
            String uri) {
        HttpResponse response
                = ResponseBuilder.unauthorized()
                        .header(HttpHeaders.of("WWW-Authenticate"), "Bearer")
                        .text("Authentication required")
                        .build();

        long durationMs = 0; // Immediate response
        logRequestComplete(requestId, method, uri, 401, durationMs, null);
        recordMetrics(timerSample, method, uri, 401);

        return Promise.of(response);
    }

    private void logRequestStart(
            String requestId,
            String method,
            String uri,
            String clientId,
            String userId,
            HttpRequest request) {
        String userAgent = request.getHeader(HttpHeaders.of("User-Agent"));

        logger.info()
                .message("Request started")
                .field("requestId", requestId)
                .field("method", method)
                .field("uri", uri)
                .field("clientId", clientId)
                .field("userId", userId)
                .field("userAgent", userAgent)
                .field("contentLength", request.getHeader(HttpHeaders.CONTENT_LENGTH))
                .log();
    }

    private void logRequestComplete(
            String requestId,
            String method,
            String uri,
            int statusCode,
            long durationMs,
            String userId) {
        logger.info()
                .message("Request completed")
                .field("requestId", requestId)
                .field("method", method)
                .field("uri", uri)
                .field("statusCode", statusCode)
                .field("durationMs", durationMs)
                .field("userId", userId)
                .log();
    }

    private void logRequestError(
            String requestId,
            String method,
            String uri,
            Throwable error,
            long durationMs,
            String userId) {
        logger.error()
                .message("Request failed")
                .exception(error)
                .field("requestId", requestId)
                .field("method", method)
                .field("uri", uri)
                .field("durationMs", durationMs)
                .field("userId", userId)
                .field("errorType", error.getClass().getSimpleName())
                .log();
    }

    private void recordMetrics(
            Timer.Sample timerSample, String method, String uri, int statusCode) {
        refactorerMetrics.incrementRequests(method, uri, statusCode);
        refactorerMetrics.recordRequestDuration(timerSample, method, uri, statusCode);
    }

    private HttpResponse addResponseHeaders(
            HttpResponse response, String requestId, String traceId) {
        // Create a new response builder with the same status code and body
        HttpResponse.Builder responseBuilder
                = HttpResponse.ofCode(response.getCode()).withBody(response.getBody());

        // Add our custom headers
        responseBuilder.withHeader(HttpHeaders.of("X-Request-ID"), requestId);

        if (traceId != null && !traceId.isEmpty()) {
            responseBuilder.withHeader(HttpHeaders.of("X-Trace-ID"), traceId);
        }

        // Add security headers
        responseBuilder
                .withHeader(HttpHeaders.of("X-Content-Type-Options"), "nosniff")
                .withHeader(HttpHeaders.of("X-Frame-Options"), "DENY")
                .withHeader(HttpHeaders.of("X-XSS-Protection"), "1; mode=block");

        return responseBuilder.build();
    }
}
