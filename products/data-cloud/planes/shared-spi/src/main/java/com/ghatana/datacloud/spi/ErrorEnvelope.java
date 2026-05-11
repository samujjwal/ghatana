package com.ghatana.datacloud.spi;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * P1-11: Canonical error envelope for all HTTP responses.
 * 
 * <p>This class provides a standardized error response format used across all handlers
 * to ensure consistent error reporting and debugging information.
 *
 * @doc.type class
 * @doc.purpose Canonical error envelope for HTTP error responses
 * @doc.layer product
 * @doc.pattern Error Envelope
 */
public final class ErrorEnvelope {
    
    private final ErrorDetail error;
    
    private ErrorEnvelope(Builder builder) {
        this.error = new ErrorDetail(
            builder.code,
            builder.message,
            builder.correlationId,
            builder.tenantId,
            builder.surface,
            builder.retryable,
            builder.details != null ? builder.details : Map.of()
        );
    }
    
    public ErrorDetail error() {
        return error;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String code;
        private String message;
        private String correlationId;
        private String tenantId;
        private String surface;
        private Boolean retryable = false;
        private Map<String, Object> details;
        
        public Builder code(String code) {
            this.code = code;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }
        
        public Builder tenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }
        
        public Builder surface(String surface) {
            this.surface = surface;
            return this;
        }
        
        public Builder retryable(boolean retryable) {
            this.retryable = retryable;
            return this;
        }
        
        public Builder details(Map<String, Object> details) {
            this.details = details;
            return this;
        }
        
        public ErrorEnvelope build() {
            Objects.requireNonNull(code, "error code is required");
            Objects.requireNonNull(message, "error message is required");
            return new ErrorEnvelope(this);
        }
    }
    
    public record ErrorDetail(
        String code,
        String message,
        String correlationId,
        String tenantId,
        String surface,
        boolean retryable,
        Map<String, Object> details
    ) {
        public ErrorDetail {
            Objects.requireNonNull(code, "code is required");
            Objects.requireNonNull(message, "message is required");
        }
    }
    
    /**
     * Standard error codes used across the system.
     */
    public static final class ErrorCodes {
        public static final String SURFACE_UNAVAILABLE = "SURFACE_UNAVAILABLE";
        public static final String UNAUTHORIZED = "UNAUTHORIZED";
        public static final String FORBIDDEN = "FORBIDDEN";
        public static final String NOT_FOUND = "NOT_FOUND";
        public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
        public static final String CONFLICT = "CONFLICT";
        public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
        public static final String SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";
        public static final String IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";
        public static final String RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";
        public static final String QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
        public static final String POLICY_VIOLATION = "POLICY_VIOLATION";
        public static final String CONNECTOR_ERROR = "CONNECTOR_ERROR";
        public static final String PIPELINE_ERROR = "PIPELINE_ERROR";
        public static final String ENTITY_ERROR = "ENTITY_ERROR";
        public static final String EVENT_ERROR = "EVENT_ERROR";
        public static final String GOVERNANCE_ERROR = "GOVERNANCE_ERROR";
        public static final String AI_SERVICE_ERROR = "AI_SERVICE_ERROR";
        
        private ErrorCodes() {}
    }
}
