package com.ghatana.kernel.core.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.MDC;

import java.util.Objects;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Manages correlation ID lifecycle across requests and spans
 * @doc.layer platform
 * @doc.pattern Utility
 */
public class CorrelationIdContext {
    
    private static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final String CORRELATION_MDC_KEY = "correlationId";
    
    // Thread-local correlation ID
    private static final ThreadLocal<String> CORRELATION_ID = new ThreadLocal<>();
    
    private CorrelationIdContext() {
        // Utility class
    }
    
    /**
     * Generate a new correlation ID in the standard format:
     * {@code <UUID>@<tenant-id>@<domain>}
     */
    public static String generateCorrelationId(String tenantId, String domain) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(domain, "domain cannot be null");
        
        String uuid = UUID.randomUUID().toString();
        return String.format("%s@%s@%s", uuid, tenantId, domain);
    }
    
    /**
     * Set correlation ID in the thread-local context and MDC.
     * 
     * @param correlationId Format: {@code <UUID>@<tenant-id>@<domain>}
     * @throws IllegalArgumentException if format is invalid
     */
    public static void setCorrelationId(String correlationId) {
        if (!isValidCorrelationId(correlationId)) {
            throw new IllegalArgumentException(
                "Invalid correlation ID format. Expected: <UUID>@<tenant-id>@<domain>, got: " + correlationId);
        }
        
        CORRELATION_ID.set(correlationId);
        MDC.put(CORRELATION_MDC_KEY, correlationId);
    }
    
    /**
     * Get the current correlation ID from thread-local context.
     * 
     * @return correlation ID or null if not set
     */
    public static String getCorrelationId() {
        return CORRELATION_ID.get();
    }
    
    /**
     * Get or generate a correlation ID for this request.
     * If one is not set, generates a new one.
     */
    public static String getOrGenerateCorrelationId(String tenantId, String domain) {
        String current = getCorrelationId();
        if (current != null && !current.isEmpty()) {
            return current;
        }
        
        String generated = generateCorrelationId(tenantId, domain);
        setCorrelationId(generated);
        return generated;
    }
    
    /**
     * Clear correlation ID from context.
     * Should be called at the end of request processing.
     */
    public static void clearCorrelationId() {
        CORRELATION_ID.remove();
        MDC.remove(CORRELATION_MDC_KEY);
    }
    
    /**
     * Add correlation ID to an OpenTelemetry span.
     * 
     * @param span the span to annotate
     */
    public static void addToSpan(Span span) {
        String correlationId = getCorrelationId();
        if (correlationId != null) {
            span.setAttribute("correlation.id", correlationId);
            
            // Also extract and add tenant/domain if possible
            String[] parts = correlationId.split("@");
            if (parts.length >= 2) {
                span.setAttribute("correlation.tenant", parts[1]);
            }
            if (parts.length >= 3) {
                span.setAttribute("correlation.domain", parts[2]);
            }
        }
    }
    
    /**
     * Validate correlation ID format: {@code <UUID>@<tenant-id>@<domain>}
     */
    public static boolean isValidCorrelationId(String correlationId) {
        if (correlationId == null || correlationId.isEmpty()) {
            return false;
        }
        
        // Pattern: 36 hex chars + dash + @ + tenant + @ + domain
        // Simplified: at least 2 @ signs and reasonable length
        return correlationId.matches("[a-f0-9\\-]{36}@[a-zA-Z0-9\\-]+@[a-zA-Z0-9\\-]+");
    }
    
    /**
     * Extract tenant ID from correlation ID.
     * 
     * @return tenant ID or null if not in correlation ID
     */
    public static String extractTenantId(String correlationId) {
        if (correlationId == null) {
            return null;
        }
        
        String[] parts = correlationId.split("@");
        return parts.length >= 2 ? parts[1] : null;
    }
    
    /**
     * Extract domain from correlation ID.
     * 
     * @return domain or null if not in correlation ID
     */
    public static String extractDomain(String correlationId) {
        if (correlationId == null) {
            return null;
        }
        
        String[] parts = correlationId.split("@");
        return parts.length >= 3 ? parts[2] : null;
    }
}
