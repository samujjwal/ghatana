package com.ghatana.auth.security;

import java.util.Objects;

/**
 * Thread-local holder for request-scoped SecurityContext.
 *
 * <p><b>Purpose</b><br>
 * Provides thread-safe access to SecurityContext for current request.
 * Each request thread stores its own context in a ThreadLocal variable.
 *
 * <p><b>Usage Pattern</b><br>
 * <pre>{@code
 * // In JwtAuthenticationFilter after JWT validation:
 * SecurityContext context = SecurityContext.of(userPrincipal, tenantId);
 * SecurityContextHolder.setCurrentContext(context);
 *
 * try {
 *     // ... service code can access context:
 *     SecurityContext ctx = SecurityContextHolder.getCurrentContext();
 *     if (ctx.hasPermission("document.read")) {
 *         // Allow operation
 *     }
 * } finally {
 *     // Always clear after request:
 *     SecurityContextHolder.clearContext();
 * }
 * }</pre>
 *
 * <p><b>Thread Safety</b><br>
 * - ThreadLocal ensures each request thread has isolated context
 * - No synchronization needed - ThreadLocal manages thread isolation
 * - Safe for concurrent requests on multi-threaded server
 *
 * <p><b>Memory Management</b><br>
 * - CRITICAL: Always call clearContext() after request in finally block
 * - Failing to clear can cause context leakage to next request on same thread pool thread
 * - Use try-finally or Java 7 try-with-resources in filters
 *
 * <p><b>Integration Points</b><br>
 * - JwtAuthenticationFilter: Sets context after JWT validation
 * - Service layer: Reads context for authorization checks
 * - Spring/JAX-RS filters: Clear context after request handling
 *
 * @doc.type class
 * @doc.purpose Thread-local SecurityContext holder for requests
 * @doc.layer product
 * @doc.pattern ThreadLocal, Security Pattern
 *
 * @see SecurityContext for context interface
 * @see JwtAuthenticationFilter for integration example
 */
public final class SecurityContextHolder {

    private static final ThreadLocal<SecurityContext> CONTEXT_HOLDER = new ThreadLocal<>();

    private SecurityContextHolder() {
        // Utility class - no instantiation
    }

    /**
     * Sets the SecurityContext for the current thread (request).
     *
     * <p><b>Thread Safety</b><br>
     * This method is thread-safe. Each thread gets its own isolated context.
     *
     * @param context the SecurityContext to set (required)
     * @throws IllegalArgumentException if context is null
     */
    public static void setCurrentContext(SecurityContext context) {
        Objects.requireNonNull(context, "context cannot be null");
        CONTEXT_HOLDER.set(context);
    }

    /**
     * Gets the SecurityContext for the current thread.
     *
     * <p><b>Return Value</b><br>
     * Returns the context if set, or an empty (unauthenticated) context if not set.
     * Never returns null.
     *
     * @return the current SecurityContext (never null)
     */
    public static SecurityContext getCurrentContext() {
        SecurityContext context = CONTEXT_HOLDER.get();
        return context != null ? context : SecurityContext.empty();
    }

    /**
     * Clears the SecurityContext for the current thread.
     *
     * <p><b>CRITICAL</b><br>
     * Always call this in a finally block after request handling to prevent
     * context leakage to subsequent requests on thread pool threads.
     *
     * <pre>{@code
     * try {
     *     SecurityContextHolder.setCurrentContext(context);
     *     // ... handle request
     * } finally {
     *     SecurityContextHolder.clearContext();  // REQUIRED
     * }
     * }</pre>
     */
    public static void clearContext() {
        CONTEXT_HOLDER.remove();
    }

    /**
     * Checks if a SecurityContext is currently set for this thread.
     *
     * @return true if context is set and authenticated, false otherwise
     */
    public static boolean hasContext() {
        SecurityContext context = CONTEXT_HOLDER.get();
        return context != null && context.isAuthenticated();
    }

    /**
     * Gets the context size (for monitoring/testing).
     *
     * <p>Note: This is a helper for testing/monitoring. In production,
     * avoid calling this in request paths for performance.</p>
     *
     * @return "1" if context is set, "0" if not
     */
    static String getContextSize() {
        return CONTEXT_HOLDER.get() != null ? "1" : "0";
    }
}
