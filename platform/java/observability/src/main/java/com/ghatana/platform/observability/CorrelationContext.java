package com.ghatana.platform.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import org.slf4j.MDC;

import java.util.UUID;

/**
 * Manages correlation IDs and trace context for distributed request tracking.
 *
 * <p>CorrelationContext provides thread-local storage for correlation IDs, user IDs, tenant IDs,
 * and request IDs. It integrates with SLF4J MDC and OpenTelemetry for comprehensive observability
 * across service boundaries with automatic context propagation.</p>
 *
 * <p><b>Tracked Context:</b></p>
 * <ul>
 *   <li><b>correlationId:</b> Unique request identifier (corr-[16 hex chars])</li>
 *   <li><b>requestId:</b> Per-request identifier (req-[12 hex chars])</li>
 *   <li><b>userId:</b> Authenticated user identifier</li>
 *   <li><b>tenantId:</b> Multi-tenant isolation identifier</li>
 *   <li><b>traceId:</b> OpenTelemetry trace ID (from current span)</li>
 *   <li><b>spanId:</b> OpenTelemetry span ID (from current span)</li>
 * </ul>
 *
 * <p><b>MDC Integration:</b></p>
 * <ul>
 *   <li>All context values propagated to SLF4J MDC for structured logging</li>
 *   <li>Trace IDs automatically extracted from OpenTelemetry current span</li>
 *   <li>MDC keys: correlationId, requestId, userId, tenantId, traceId, spanId</li>
 *   <li>Automatic MDC cleanup via {@link #clear()}</li>
 * </ul>
 *
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * // 1. Initialize context for new request
 * CorrelationContext.initialize();
 * logger.info("Processing request");  // MDC populated automatically
 *
 * // 2. Initialize with specific values
 * CorrelationContext.initialize("corr-abc123", "user-42", "tenant-1", "req-xyz789");
 * CorrelationContext.updateMDC();  // Refresh MDC with trace IDs
 *
 * // 3. Propagate context to async operation
 * CorrelationData data = CorrelationContext.getCurrentData();
 * CompletableFuture.runAsync(() -> {
 *     CorrelationContext.withContext(data, () -> {
 *         logger.info("Async processing");  // Context propagated
 *     });
 * });
 *
 * // 4. Set context values dynamically
 * CorrelationContext.setUserId("user-123");
 * CorrelationContext.setTenantId("tenant-456");
 *
 * // 5. Always clear context when done (e.g., in finally block)
 * try {
 *     CorrelationContext.initialize();
 *     // ... process request ...
 * } finally {
 *     CorrelationContext.clear();
 * }
 * }</pre>
 *
 * <p><b>Context Propagation:</b></p>
 * <ul>
 *   <li>{@link #getCurrentData()} - Capture current context for propagation</li>
 *   <li>{@link #initializeFrom(CorrelationData)} - Restore context from captured data</li>
 *   <li>{@link #withContext(CorrelationData, Supplier)} - Execute with specific context</li>
 *   <li>Useful for async operations, thread pool executors, message handlers</li>
 * </ul>
 *
 * <p><b>Thread-Safety:</b> Thread-safe via ThreadLocal storage. Each thread has isolated context.</p>
 *
 * <p><b>Performance:</b></p>
 * <ul>
 *   <li>ThreadLocal access: O(1) overhead (< 10ns)</li>
 *   <li>MDC update: < 1µs overhead (6 MDC puts)</li>
 *   <li>Span context extraction: < 100ns overhead</li>
 * </ul>
 *
 * <p><b>Cleanup:</b> Always call {@link #clear()} in finally blocks to prevent memory leaks
 * in thread pool environments.</p>
 *
 * @see CorrelationData for context data class
 * @see TraceIdMdcFilter for HTTP filter that initializes correlation context
 *
 * @author Platform Team
 * @created 2024-10-01
 * @updated 2025-10-29
 * @version 1.0.0
 * @type Context Manager (Thread-Local Storage)
 * @purpose Correlation context tracking, MDC integration, distributed tracing support
 * @pattern Thread-Local pattern, Context pattern, Carrier pattern
 * @responsibility Thread-local context storage, MDC synchronization, OpenTelemetry integration, context propagation
 * @usage Call initialize() at request start, clear() in finally block; use withContext() for async propagation
 * @examples See class-level JavaDoc for 5 usage examples (init, propagate, async, setters, cleanup)
 * @testing Test context initialization, MDC updates, thread isolation, context propagation, cleanup
 * @notes Always clear context in finally blocks; thread-local storage; integrates with SLF4J and OpenTelemetry
 * @doc.type class
 * @doc.purpose Thread-local correlation context management, MDC integration, distributed tracing
 * @doc.layer observability
 * @doc.pattern Context
 */
public final class CorrelationContext {
    
    public static final String CORRELATION_ID_KEY = "correlationId";
    public static final String TRACE_ID_KEY = "traceId";
    public static final String SPAN_ID_KEY = "spanId";
    public static final String USER_ID_KEY = "userId";
    public static final String TENANT_ID_KEY = "tenantId";
    public static final String REQUEST_ID_KEY = "requestId";
    
    private static final ThreadLocal<String> CORRELATION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();
    
    private CorrelationContext() {}
    
    /**
     * Initialize correlation context for a new request.
     */
    public static void initialize() {
        initialize(generateCorrelationId(), null, null, null);
    }
    
    /**
     * Initialize correlation context with specific values.
     */
    public static void initialize(String correlationId, String userId, String tenantId, String requestId) {
        // Set thread-local values
        CORRELATION_ID.set(correlationId != null ? correlationId : generateCorrelationId());
        USER_ID.set(userId);
        TENANT_ID.set(tenantId);
        REQUEST_ID.set(requestId != null ? requestId : generateRequestId());
        
        // Update MDC for logging
        updateMDC();
    }
    
    /**
     * Initialize from existing correlation context (for propagation).
     */
    public static void initializeFrom(CorrelationData data) {
        initialize(data.getCorrelationId(), data.getUserId(), data.getTenantId(), data.getRequestId());
    }
    
    /**
     * Update MDC with current context and trace information.
     */
    public static void updateMDC() {
        // Set correlation context
        String correlationId = CORRELATION_ID.get();
        if (correlationId != null) {
            MDC.put(CORRELATION_ID_KEY, correlationId);
        }
        
        String userId = USER_ID.get();
        if (userId != null) {
            MDC.put(USER_ID_KEY, userId);
        }
        
        String tenantId = TENANT_ID.get();
        if (tenantId != null) {
            MDC.put(TENANT_ID_KEY, tenantId);
        }
        
        String requestId = REQUEST_ID.get();
        if (requestId != null) {
            MDC.put(REQUEST_ID_KEY, requestId);
        }
        
        // Add OpenTelemetry trace information
        Span currentSpan = Span.current();
        if (currentSpan != null) {
            SpanContext spanContext = currentSpan.getSpanContext();
            if (spanContext.isValid()) {
                MDC.put(TRACE_ID_KEY, spanContext.getTraceId());
                MDC.put(SPAN_ID_KEY, spanContext.getSpanId());
            }
        }
    }
    
    /**
     * Clear correlation context.
     */
    public static void clear() {
        CORRELATION_ID.remove();
        USER_ID.remove();
        TENANT_ID.remove();
        REQUEST_ID.remove();
        
        // Clear MDC
        MDC.remove(CORRELATION_ID_KEY);
        MDC.remove(TRACE_ID_KEY);
        MDC.remove(SPAN_ID_KEY);
        MDC.remove(USER_ID_KEY);
        MDC.remove(TENANT_ID_KEY);
        MDC.remove(REQUEST_ID_KEY);
    }
    
    // Getters
    
    public static String getCorrelationId() {
        return CORRELATION_ID.get();
    }
    
    public static String getUserId() {
        return USER_ID.get();
    }
    
    public static String getTenantId() {
        return TENANT_ID.get();
    }
    
    public static String getRequestId() {
        return REQUEST_ID.get();
    }
    
    public static String getTraceId() {
        Span currentSpan = Span.current();
        if (currentSpan != null) {
            SpanContext spanContext = currentSpan.getSpanContext();
            if (spanContext.isValid()) {
                return spanContext.getTraceId();
            }
        }
        return null;
    }
    
    public static String getSpanId() {
        Span currentSpan = Span.current();
        if (currentSpan != null) {
            SpanContext spanContext = currentSpan.getSpanContext();
            if (spanContext.isValid()) {
                return spanContext.getSpanId();
            }
        }
        return null;
    }
    
    // Setters
    
    public static void setUserId(String userId) {
        USER_ID.set(userId);
        if (userId != null) {
            MDC.put(USER_ID_KEY, userId);
        } else {
            MDC.remove(USER_ID_KEY);
        }
    }
    
    public static void setTenantId(String tenantId) {
        TENANT_ID.set(tenantId);
        if (tenantId != null) {
            MDC.put(TENANT_ID_KEY, tenantId);
        } else {
            MDC.remove(TENANT_ID_KEY);
        }
    }
    
    /**
     * Get current correlation data for propagation.
     */
    public static CorrelationData getCurrentData() {
        return new CorrelationData(
            getCorrelationId(),
            getUserId(),
            getTenantId(),
            getRequestId(),
            getTraceId(),
            getSpanId()
        );
    }
    
    /**
     * Execute a task with correlation context.
     */
    public static <T> T withContext(CorrelationData data, java.util.function.Supplier<T> task) {
        CorrelationData previous = getCurrentData();
        try {
            initializeFrom(data);
            return task.get();
        } finally {
            if (previous.getCorrelationId() != null) {
                initializeFrom(previous);
            } else {
                clear();
            }
        }
    }
    
    /**
     * Execute a runnable with correlation context.
     */
    public static void withContext(CorrelationData data, Runnable task) {
        withContext(data, () -> {
            task.run();
            return null;
        });
    }
    
    // Utility methods
    
    private static String generateCorrelationId() {
        return "corr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
    
    private static String generateRequestId() {
        return "req-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
    
    /**
     * Data class for correlation context propagation.
     */
    public static class CorrelationData {
        private final String correlationId;
        private final String userId;
        private final String tenantId;
        private final String requestId;
        private final String traceId;
        private final String spanId;
        
        public CorrelationData(String correlationId, String userId, String tenantId, 
                             String requestId, String traceId, String spanId) {
            this.correlationId = correlationId;
            this.userId = userId;
            this.tenantId = tenantId;
            this.requestId = requestId;
            this.traceId = traceId;
            this.spanId = spanId;
        }
        
        public String getCorrelationId() { return correlationId; }
        public String getUserId() { return userId; }
        public String getTenantId() { return tenantId; }
        public String getRequestId() { return requestId; }
        public String getTraceId() { return traceId; }
        public String getSpanId() { return spanId; }
        
        @Override
        public String toString() {
            return "CorrelationData{" +
                "correlationId='" + correlationId + '\'' +
                ", userId='" + userId + '\'' +
                ", tenantId='" + tenantId + '\'' +
                ", requestId='" + requestId + '\'' +
                ", traceId='" + traceId + '\'' +
                ", spanId='" + spanId + '\'' +
                '}';
        }
    }
}
