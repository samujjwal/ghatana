package com.ghatana.platform.http.server.filter;

import io.activej.http.AsyncServlet;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;

/**
 * Production-grade filter chain abstraction for HTTP request/response interception with Promise-based composition.
 *
 * <p><b>Purpose</b><br>
 * Provides composable filter chain for HTTP request processing, enabling cross-cutting concerns
 * (authentication, logging, metrics, CORS, etc.) to be applied transparently before requests
 * reach servlets. Supports Promise-based async filters with proper exception handling.
 *
 * <p><b>Architecture Role</b><br>
 * Filter chain in core/http/filter for request/response interception.
 * Used by:
 * - HttpServerBuilder - Apply filters to all routes
 * - Security Filters - Authentication, authorization, CORS
 * - Observability Filters - Logging, metrics, tracing
 * - Request Processing - Tenant extraction, validation, enrichment
 *
 * <p><b>Filter Chain Features</b><br>
 * - <b>Composable Filters</b>: Chain multiple filters in registration order
 * - <b>Promise-Based</b>: Async filter execution with ActiveJ Promise
 * - <b>Request Interception</b>: Modify/validate requests before servlet
 * - <b>Response Interception</b>: Modify/enrich responses before sending
 * - <b>Short-Circuit</b>: Filters can return response without calling next
 * - <b>Exception Handling</b>: Per-filter error handling with .whenComplete()
 * - <b>Builder Pattern</b>: Fluent API for filter registration
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // 1. Basic filter chain (logging + authentication)
 * Filter loggingFilter = (request, next) -> {
 *     logger.info("Request: {} {}", request.getMethod(), request.getPath());
 *     return next.serve(request)
 *         .whenComplete((response, error) -> {
 *             if (error == null) {
 *                 logger.info("Response: {}", response.getCode());
 *             } else {
 *                 logger.error("Request failed", error);
 *             }
 *         });
 * };
 *
 * Filter authFilter = (request, next) -> {
 *     String token = request.getHeader("Authorization");
 *     if (token == null || !validateToken(token)) {
 *         return Promise.of(ResponseBuilder.unauthorized()
 *             .json(ErrorResponse.of(401, "INVALID_TOKEN", "Missing or invalid token"))
 *             .build());
 *     }
 *     return next.serve(request);
 * };
 *
 * AsyncServlet servlet = FilterChain.create()
 *     .addFilter(loggingFilter)
 *     .addFilter(authFilter)
 *     .build(baseServlet);
 *
 * // 2. Tenant extraction filter
 * Filter tenantFilter = (request, next) -> {
 *     String tenantId = request.getHeader("X-Tenant-Id");
 *     if (tenantId == null) {
 *         return Promise.of(ResponseBuilder.badRequest()
 *             .json(ErrorResponse.of(400, "MISSING_TENANT", "X-Tenant-Id header required"))
 *             .build());
 *     }
 *     
 *     TenantContext.setCurrentTenant(tenantId);
 *     
 *     return next.serve(request)
 *         .whenComplete(() -> TenantContext.clear());
 * };
 *
 * // 3. Metrics collection filter
 * Filter metricsFilter = (request, next) -> {
 *     Instant start = Instant.now();
 *     
 *     return next.serve(request)
 *         .whenComplete((response, error) -> {
 *             Duration duration = Duration.between(start, Instant.now());
 *             
 *             metrics.recordTimer("http.request.duration",
 *                 duration.toMillis(),
 *                 "method", request.getMethod().name(),
 *                 "status", error == null ? String.valueOf(response.getCode()) : "error");
 *         });
 * };
 *
 * // 4. CORS filter
 * Filter corsFilter = (request, next) -> {
 *     return next.serve(request)
 *         .map(response -> {
 *             return HttpResponse.ofCode(response.getCode())
 *                 .withHeader("Access-Control-Allow-Origin", "*")
 *                 .withHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE")
 *                 .withHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
 *                 .withBody(response.getBody())
 *                 .build();
 *         });
 * };
 *
 * // 5. Request validation filter
 * Filter validationFilter = (request, next) -> {
 *     // Validate content type for POST/PUT
 *     if (request.getMethod() == HttpMethod.POST || request.getMethod() == HttpMethod.PUT) {
 *         String contentType = request.getHeader("Content-Type");
 *         if (contentType == null || !contentType.contains("application/json")) {
 *             return Promise.of(ResponseBuilder.badRequest()
 *                 .json(ErrorResponse.of(400, "INVALID_CONTENT_TYPE", 
 *                     "Content-Type must be application/json"))
 *                 .build());
 *         }
 *     }
 *     
 *     return next.serve(request);
 * };
 *
 * // 6. Rate limiting filter
 * Filter rateLimitFilter = (request, next) -> {
 *     String clientId = extractClientId(request);
 *     
 *     if (!rateLimiter.tryAcquire(clientId)) {
 *         return Promise.of(ResponseBuilder.status(429)  // Too Many Requests
 *             .header("Retry-After", "60")
 *             .json(ErrorResponse.of(429, "RATE_LIMIT_EXCEEDED", "Rate limit exceeded"))
 *             .build());
 *     }
 *     
 *     return next.serve(request);
 * };
 *
 * // 7. Comprehensive security filter chain
 * AsyncServlet secureServlet = FilterChain.create()
 *     .addFilter(new HstsHeaderFilter())  // HSTS enforcement
 *     .addFilter(corsFilter)              // CORS headers
 *     .addFilter(rateLimitFilter)         // Rate limiting
 *     .addFilter(tenantFilter)            // Tenant extraction
 *     .addFilter(authFilter)              // Authentication
 *     .addFilter(validationFilter)        // Request validation
 *     .addFilter(loggingFilter)           // Request logging
 *     .addFilter(metricsFilter)           // Metrics collection
 *     .build(baseServlet);
 *
 * // 8. Conditional filter
 * FilterChain.Builder chainBuilder = FilterChain.create()
 *     .addFilter(loggingFilter);
 * 
 * if (requiresAuth) {
 *     chainBuilder.addFilter(authFilter);
 * }
 * 
 * AsyncServlet servlet = chainBuilder.build(baseServlet);
 * }</pre>
 *
 * <p><b>Filter Execution Order</b><br>
 * Filters execute in registration order:
 * <pre>
 * Request Flow:
 * Client → Filter1 → Filter2 → Filter3 → Servlet → Response
 *          ↓         ↓         ↓         ↓
 *        next()    next()    next()    handle()
 *
 * Response Flow:
 * Client ← Filter1 ← Filter2 ← Filter3 ← Servlet ← Response
 *          ↓         ↓         ↓         ↓
 *       modify    modify    modify    create
 * </pre>
 *
 * <p><b>Short-Circuit Example</b><br>
 * Filter returns response without calling next:
 * <pre>{@code
 * Filter authFilter = (request, next) -> {
 *     if (!isAuthenticated(request)) {
 *         // Short-circuit: return 401 without calling next
 *         return Promise.of(ResponseBuilder.unauthorized()
 *             .json(ErrorResponse.of(401, "UNAUTHORIZED", "Authentication required"))
 *             .build());
 *     }
 *     
 *     // Authenticated: continue chain
 *     return next.serve(request);
 * };
 * }</pre>
 *
 * <p><b>Filter Interface</b><br>
 * <pre>{@code
 * @FunctionalInterface
 * interface Filter {
 *     Promise<HttpResponse> apply(HttpRequest request, AsyncServlet next) throws Exception;
 * }
 * }</pre>
 *
 * <p><b>Common Filter Patterns</b><br>
 * <pre>{@code
 * // Request modification
 * (request, next) -> {
 *     HttpRequest modified = request.withHeader("X-Custom", "value");
 *     return next.serve(modified);
 * }
 *
 * // Response modification
 * (request, next) -> next.serve(request).map(response ->
 *     response.withHeader("X-Custom", "value")
 * )
 *
 * // Exception handling
 * (request, next) -> next.serve(request).mapException(error -> {
 *     logger.error("Request failed", error);
 *     return ResponseBuilder.internalServerError()
 *         .json(ErrorResponse.of(500, "INTERNAL_ERROR", "Request failed"))
 *         .build();
 * })
 *
 * // Async resource cleanup
 * (request, next) -> {
 *     Resource resource = acquireResource();
 *     return next.serve(request).whenComplete(() -> resource.release());
 * }
 * }</pre>
 *
 * <p><b>Best Practices</b><br>
 * - Order filters logically (HSTS → CORS → Rate Limit → Auth → Validation → Logging → Metrics)
 * - Use .whenComplete() for cleanup (tenant context, resources)
 * - Return early from filters (short-circuit) for authentication/validation failures
 * - Keep filters focused (single responsibility)
 * - Log filter execution for debugging
 * - Test filters in isolation and in chains
 * - Handle exceptions gracefully
 * - Avoid expensive operations in filters (keep fast path fast)
 *
 * <p><b>Performance Considerations</b><br>
 * - Filters execute sequentially (registration order)
 * - Each filter adds latency (keep filters fast)
 * - Promise-based execution (no blocking allowed)
 * - Consider filter order (fail fast filters first)
 * - Measure filter overhead with metrics
 *
 * <p><b>Thread Safety</b><br>
 * FilterChain builder is NOT thread-safe (configure from single thread).
 * Built AsyncServlet is thread-safe (handles concurrent requests).
 * Filters must be thread-safe if shared across requests.
 *
 * @see HttpServerBuilder
 * @see HstsHeaderFilter
 * @see io.activej.http.AsyncServlet
 * @see io.activej.promise.Promise
 * @since 1.0.0
 * @doc.type class
 * @doc.purpose Filter chain for HTTP request/response interception
 * @doc.layer core
 * @doc.pattern Chain of Responsibility
 */
public class FilterChain {
    
    /**
     * Filter interface for request/response interception.
     */
    @FunctionalInterface
    public interface Filter {
        /**
         * Applies the filter to the request.
         * 
         * @param request The HTTP request
         * @param next The next servlet in the chain
         * @return A promise of the HTTP response
         * @throws Exception If the underlying servlet throws an exception
         */
        Promise<HttpResponse> apply(HttpRequest request, AsyncServlet next) throws Exception;
    }
    
    private final java.util.List<Filter> filters = new java.util.ArrayList<>();
    
    private FilterChain() {
        // Use create() factory method
    }
    
    /**
     * Creates a new filter chain builder.
     * 
     * @return A new filter chain instance
     */
    public static FilterChain create() {
        return new FilterChain();
    }
    
    /**
     * Adds a filter to the chain.
     * Filters are executed in the order they are added.
     * 
     * @param filter The filter to add
     * @return This filter chain
     */
    public FilterChain addFilter(Filter filter) {
        filters.add(filter);
        return this;
    }
    
    /**
     * Builds the final servlet with all filters applied.
     * 
     * @param baseServlet The base servlet to wrap
     * @return The wrapped servlet with filters
     */
    public AsyncServlet build(AsyncServlet baseServlet) {
        AsyncServlet current = baseServlet;
        
        // Apply filters in reverse order (last filter wraps first)
        for (int i = filters.size() - 1; i >= 0; i--) {
            Filter filter = filters.get(i);
            AsyncServlet next = current;
            current = request -> filter.apply(request, next);
         }

         return current;
     }

    /**
     * Gets the number of filters in the chain.
     * 
     * @return The filter count
     */
    public int getFilterCount() {
        return filters.size();
    }
}
