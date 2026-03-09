package com.ghatana.platform.security.session;

import com.ghatana.platform.observability.CorrelationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Request-scoped context for managing session state and request attributes using ThreadLocal.
 * <p>
 * RequestContext provides thread-local storage for HTTP request information, session state,
 * and custom attributes. It integrates with {@link com.ghatana.observability.CorrelationContext}
 * for distributed tracing correlation.
 * </p>
 * 
 * <h2>Features</h2>
 * <ul>
 *   <li><b>ThreadLocal Storage</b>: Request-scoped data per thread</li>
 *   <li><b>Session Integration</b>: Stores SessionState for current request</li>
 *   <li><b>Request Metadata</b>: Path, method, client IP, user agent</li>
 *   <li><b>Custom Attributes</b>: Key-value storage for request-scoped data</li>
 *   <li><b>Correlation Integration</b>: Links with CorrelationContext (traceId, userId, tenantId)</li>
 *   <li><b>Lifecycle Management</b>: initialize() → use → clear()</li>
 * </ul>
 * 
 * @doc.type class
 * @doc.purpose ThreadLocal context management for HTTP request state and session tracking
 * @doc.layer core
 * @doc.pattern Context Object, ThreadLocal Holder
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // In HTTP filter/servlet
 * try {
 *     // Initialize context at request start
 *     RequestContext context = RequestContext.initialize();
 *     
 *     // Set request metadata
 *     context.setRequestPath("/api/users");
 *     context.setRequestMethod("GET");
 *     context.setClientIp("192.168.1.100");
 *     context.setUserAgent("Mozilla/5.0...");
 *     
 *     // Load and set session
 *     SessionState session = sessionManager.getSession(sessionId).get();
 *     context.setSession(session);
 *     
 *     // Set custom attributes
 *     context.setAttribute("startTime", System.currentTimeMillis());
 *     context.setAttribute("correlationId", UUID.randomUUID());
 *     
 *     // Process request
 *     processRequest();
 *     
 *     // Access context in downstream code
 *     RequestContext current = RequestContext.current();
 *     String path = current.getRequestPath();
 *     SessionState session = current.getSession();
 *     Long startTime = current.getAttribute("startTime");
 *     
 * } finally {
 *     // Clean up context at request end
 *     RequestContext.clear();
 * }
 *
 * // Helper for scoped execution
 * RequestContext.withContext(() -> {
 *     // Code here has initialized RequestContext
 *     RequestContext ctx = RequestContext.current();
 *     ctx.setRequestPath("/api/test");
 *     return "result";
 * });
 * }</pre>
 * 
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li><b>initialize()</b>: Create and bind context to current thread</li>
 *   <li><b>current()</b>: Access context during request processing</li>
 *   <li><b>clear()</b>: Remove context from thread (MUST call in finally block)</li>
 * </ol>
 * 
 * <h2>Correlation Context Integration</h2>
 * When session is set, automatically propagates to CorrelationContext:
 * <ul>
 *   <li>session.userId → CorrelationContext.setUserId()</li>
 *   <li>session.tenantId → CorrelationContext.setTenantId()</li>
 * </ul>
 * 
 * <h2>Thread Safety</h2>
 * <b>WARNING</b>: ThreadLocal-based, NOT safe across threads. Each thread has its own context.
 * Do NOT pass RequestContext across thread boundaries.
 * 
 * <h2>Memory Leak Prevention</h2>
 * <b>CRITICAL</b>: Always call clear() in finally block to prevent memory leaks in thread pools.
 * 
 * <pre>{@code
 * try {
 *     RequestContext.initialize();
 *     // Request processing
 * } finally {
 *     RequestContext.clear();  // MUST call!
 * }
 * }</pre>
 *
 * @author Ghatana Team
 * @version 1.0
 * @since 1.0
 * @stability Stable
 * @completeness 90
 * @testing Unit
 * @thread_safety ThreadLocal (per-thread isolation)
 * @performance O(1) access
 * @see SessionState
 * @see com.ghatana.observability.CorrelationContext
 * @doc.type class
 * @doc.purpose ThreadLocal request-scoped context for session state and custom attributes with correlation integration
 * @doc.layer observability
 * @doc.pattern Context (ThreadLocal holder with lifecycle management)
 */
public class RequestContext {
    
    private static final Logger logger = LoggerFactory.getLogger(RequestContext.class);
    
    private static final ThreadLocal<RequestContext> CONTEXT = new ThreadLocal<>();
    
    private final Map<String, Object> attributes = new HashMap<>();
    private SessionState session;
    private String requestPath;
    private String requestMethod;
    private String clientIp;
    private String userAgent;
    private boolean authenticated;
    
    /**
     * Create a new request context.
     */
    private RequestContext() {
    }
    
    /**
     * Initialize the request context for the current thread.
     */
    public static RequestContext initialize() {
        RequestContext context = new RequestContext();
        CONTEXT.set(context);
        
        // Link with correlation context
        CorrelationContext.initialize();
        
        return context;
    }
    
    /**
     * Get the request context for the current thread.
     */
    public static RequestContext current() {
        RequestContext context = CONTEXT.get();
        if (context == null) {
            throw new IllegalStateException("RequestContext not initialized. Call initialize() first.");
        }
        return context;
    }
    
    /**
     * Get the request context for the current thread, or empty if not initialized.
     */
    public static Optional<RequestContext> currentOptional() {
        return Optional.ofNullable(CONTEXT.get());
    }
    
    /**
     * Clear the request context for the current thread.
     */
    public static void clear() {
        CONTEXT.remove();
        CorrelationContext.clear();
    }
    
    /**
     * Execute a task with a request context.
     */
    public static <T> T withContext(java.util.function.Supplier<T> task) {
        RequestContext previous = CONTEXT.get();
        try {
            if (previous == null) {
                initialize();
            }
            return task.get();
        } finally {
            if (previous == null) {
                clear();
            }
        }
    }
    
    /**
     * Execute a runnable with a request context.
     */
    public static void withContext(Runnable task) {
        withContext(() -> {
            task.run();
            return null;
        });
    }
    
    /**
     * Get the session state.
     */
    public SessionState getSession() {
        return session;
    }
    
    /**
     * Set the session state.
     */
    public void setSession(SessionState session) {
        this.session = session;
        
        // Update correlation context with session information
        if (session != null) {
            if (session.getUserId() != null) {
                CorrelationContext.setUserId(session.getUserId());
            }
            if (session.getTenantId() != null) {
                CorrelationContext.setTenantId(session.getTenantId());
            }
        }
    }
    
    /**
     * Check if a session is present.
     */
    public boolean hasSession() {
        return session != null;
    }
    
    /**
     * Get the request path.
     */
    public String getRequestPath() {
        return requestPath;
    }
    
    /**
     * Set the request path.
     */
    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }
    
    /**
     * Get the request method.
     */
    public String getRequestMethod() {
        return requestMethod;
    }
    
    /**
     * Set the request method.
     */
    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }
    
    /**
     * Get the client IP address.
     */
    public String getClientIp() {
        return clientIp;
    }
    
    /**
     * Set the client IP address.
     */
    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }
    
    /**
     * Get the user agent.
     */
    public String getUserAgent() {
        return userAgent;
    }
    
    /**
     * Set the user agent.
     */
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
    
    /**
     * Check if the request is authenticated.
     */
    public boolean isAuthenticated() {
        return authenticated;
    }
    
    /**
     * Set the authenticated flag.
     */
    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }
    
    /**
     * Get an attribute from the request context.
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name) {
        return (T) attributes.get(name);
    }
    
    /**
     * Set an attribute in the request context.
     */
    public void setAttribute(String name, Object value) {
        attributes.put(name, value);
    }
    
    /**
     * Remove an attribute from the request context.
     */
    public void removeAttribute(String name) {
        attributes.remove(name);
    }
    
    /**
     * Check if the request context contains an attribute.
     */
    public boolean hasAttribute(String name) {
        return attributes.containsKey(name);
    }
    
    /**
     * Get all attribute names in the request context.
     */
    public Iterable<String> getAttributeNames() {
        return attributes.keySet();
    }
}
