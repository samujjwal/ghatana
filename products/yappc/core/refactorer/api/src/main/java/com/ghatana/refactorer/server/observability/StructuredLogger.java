package com.ghatana.refactorer.server.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

/**
 * Utility for structured logging with consistent field names and correlation IDs. Provides

 * convenient methods for logging with structured data that can be easily parsed and analyzed.

 *

 * @doc.type class

 * @doc.purpose Emit JSON-friendly logs enriched with tenant/request context.

 * @doc.layer product

 * @doc.pattern Logging Adapter

 */

public final class StructuredLogger {
    private static final ObjectMapper objectMapper = JsonUtils.getDefaultMapper();
    private static final String TRACE_ID_KEY = "traceId";
    private static final String SPAN_ID_KEY = "spanId";
    private static final String REQUEST_ID_KEY = "requestId";
    private static final String USER_ID_KEY = "userId";
    private static final String TENANT_ID_KEY = "tenantId";
    private static final String OPERATION_KEY = "operation";
    private static final String DURATION_MS_KEY = "durationMs";
    private static final String ERROR_TYPE_KEY = "errorType";
    private static final String ERROR_MESSAGE_KEY = "errorMessage";

    private final Logger logger;

    public StructuredLogger(Class<?> clazz) {
        this.logger = LogManager.getLogger(clazz);
    }

    public StructuredLogger(String name) {
        this.logger = LogManager.getLogger(name);
    }

    /**
     * Creates a structured logger for the given class.
     *
     * @param clazz the class to create a logger for
     * @return a new structured logger
     */
    public static StructuredLogger getLogger(Class<?> clazz) {
        return new StructuredLogger(clazz);
    }

    /**
     * Creates a structured logger with the given name.
     *
     * @param name the logger name
     * @return a new structured logger
     */
    public static StructuredLogger getLogger(String name) {
        return new StructuredLogger(name);
    }

    /**
     * Sets the trace context in the logging MDC.
     *
     * @param traceId the trace ID
     * @param spanId the span ID
     */
    public static void setTraceContext(String traceId, String spanId) {
        if (traceId != null && !traceId.isEmpty()) {
            ThreadContext.put(TRACE_ID_KEY, traceId);
        }
        if (spanId != null && !spanId.isEmpty()) {
            ThreadContext.put(SPAN_ID_KEY, spanId);
        }
    }

    /**
     * Sets the request context in the logging MDC.
     *
     * @param requestId the request ID
     * @param userId the user ID (optional)
     * @param tenantId the tenant ID (optional)
     */
    public static void setRequestContext(String requestId, String userId, String tenantId) {
        if (requestId != null && !requestId.isEmpty()) {
            ThreadContext.put(REQUEST_ID_KEY, requestId);
        }
        if (userId != null && !userId.isEmpty()) {
            ThreadContext.put(USER_ID_KEY, userId);
        }
        if (tenantId != null && !tenantId.isEmpty()) {
            ThreadContext.put(TENANT_ID_KEY, tenantId);
        }
    }

    /**
 * Clears the trace and request context from the logging MDC. */
    public static void clearContext() {
        ThreadContext.remove(TRACE_ID_KEY);
        ThreadContext.remove(SPAN_ID_KEY);
        ThreadContext.remove(REQUEST_ID_KEY);
        ThreadContext.remove(USER_ID_KEY);
        ThreadContext.remove(TENANT_ID_KEY);
    }

    /**
     * Logs an info message with structured data.
     *
     * @param message the log message
     * @param data the structured data
     */
    public void info(String message, Map<String, Object> data) {
        logger.info("{} {}", message, toJsonString(data));
    }

    /**
     * Logs an info message with operation context.
     *
     * @param operation the operation name
     * @param message the log message
     * @param data additional structured data
     */
    public void infoOperation(String operation, String message, Map<String, Object> data) {
        Map<String, Object> enrichedData = new HashMap<>(data);
        enrichedData.put(OPERATION_KEY, operation);
        enrichedData.put("timestamp", Instant.now().toString());
        info(message, enrichedData);
    }

    /**
     * Logs a debug message with structured data.
     *
     * @param message the log message
     * @param data the structured data
     */
    public void debug(String message, Map<String, Object> data) {
        if (logger.isDebugEnabled()) {
            logger.debug("{} {}", message, toJsonString(data));
        }
    }

    /**
     * Logs a warning message with structured data.
     *
     * @param message the log message
     * @param data the structured data
     */
    public void warn(String message, Map<String, Object> data) {
        logger.warn("{} {}", message, toJsonString(data));
    }

    /**
     * Logs an error message with structured data.
     *
     * @param message the log message
     * @param data the structured data
     */
    public void error(String message, Map<String, Object> data) {
        logger.error("{} {}", message, toJsonString(data));
    }

    /**
     * Logs an error message with exception and structured data.
     *
     * @param message the log message
     * @param exception the exception
     * @param data additional structured data
     */
    public void error(String message, Throwable exception, Map<String, Object> data) {
        Map<String, Object> enrichedData = new HashMap<>(data);
        enrichedData.put(ERROR_TYPE_KEY, exception.getClass().getSimpleName());
        enrichedData.put(ERROR_MESSAGE_KEY, exception.getMessage());
        logger.error("{} {}", message, toJsonString(enrichedData), exception);
    }

    /**
     * Logs a request start event.
     *
     * @param method the HTTP method
     * @param uri the request URI
     * @param userAgent the user agent
     */
    public void logRequestStart(String method, String uri, String userAgent) {
        Map<String, Object> data =
                Map.of(
                        "event",
                        "request_start",
                        "method",
                        method,
                        "uri",
                        uri,
                        "userAgent",
                        Objects.requireNonNullElse(userAgent, "unknown"));
        info("Request started", data);
    }

    /**
     * Logs a request completion event.
     *
     * @param method the HTTP method
     * @param uri the request URI
     * @param statusCode the response status code
     * @param durationMs the request duration in milliseconds
     */
    public void logRequestComplete(String method, String uri, int statusCode, long durationMs) {
        Map<String, Object> data =
                Map.of(
                        "event",
                        "request_complete",
                        "method",
                        method,
                        "uri",
                        uri,
                        "statusCode",
                        statusCode,
                        DURATION_MS_KEY,
                        durationMs);
        info("Request completed", data);
    }

    /**
     * Logs a job lifecycle event.
     *
     * @param jobId the job ID
     * @param jobType the job type
     * @param event the lifecycle event (started, completed, failed)
     * @param additionalData additional data specific to the event
     */
    public void logJobEvent(
            String jobId, String jobType, String event, Map<String, Object> additionalData) {
        Map<String, Object> data = new HashMap<>(additionalData);
        data.put("event", "job_" + event);
        data.put("jobId", jobId);
        data.put("jobType", jobType);
        data.put("timestamp", Instant.now().toString());
        info("Job " + event, data);
    }

    /**
     * Logs a security event.
     *
     * @param event the security event type
     * @param userId the user ID (if available)
     * @param ipAddress the client IP address
     * @param success whether the operation was successful
     * @param additionalData additional security-related data
     */
    public void logSecurityEvent(
            String event,
            String userId,
            String ipAddress,
            boolean success,
            Map<String, Object> additionalData) {
        Map<String, Object> data = new HashMap<>(additionalData);
        data.put("event", "security_" + event);
        data.put("userId", Objects.requireNonNullElse(userId, "anonymous"));
        data.put("ipAddress", Objects.requireNonNullElse(ipAddress, "unknown"));
        data.put("success", success);
        data.put("timestamp", Instant.now().toString());

        if (success) {
            info("Security event: " + event, data);
        } else {
            warn("Security event failed: " + event, data);
        }
    }

    /**
     * Logs a performance metric.
     *
     * @param operation the operation name
     * @param durationMs the duration in milliseconds
     * @param additionalMetrics additional performance metrics
     */
    public void logPerformance(
            String operation, long durationMs, Map<String, Object> additionalMetrics) {
        Map<String, Object> data = new HashMap<>(additionalMetrics);
        data.put("event", "performance");
        data.put(OPERATION_KEY, operation);
        data.put(DURATION_MS_KEY, durationMs);
        data.put("timestamp", Instant.now().toString());
        info("Performance metric", data);
    }

    /**
     * Logs an audit event for compliance tracking.
     *
     * @param action the action performed
     * @param resource the resource affected
     * @param userId the user who performed the action
     * @param success whether the action was successful
     * @param additionalData additional audit data
     */
    public void logAuditEvent(
            String action,
            String resource,
            String userId,
            boolean success,
            Map<String, Object> additionalData) {
        Map<String, Object> data = new HashMap<>(additionalData);
        data.put("event", "audit");
        data.put("action", action);
        data.put("resource", resource);
        data.put("userId", Objects.requireNonNullElse(userId, "system"));
        data.put("success", success);
        data.put("timestamp", Instant.now().toString());

        // Audit events are always logged at INFO level for compliance
        info("Audit: " + action + " on " + resource, data);
    }

    /**
 * Builder for creating structured log entries. */
    public static class LogBuilder {
        private final Map<String, Object> data = new HashMap<>();
        private final StructuredLogger logger;
        private final String level;
        private String message;
        private Throwable exception;

        private LogBuilder(StructuredLogger logger, String level) {
            this.logger = logger;
            this.level = level;
        }

        public LogBuilder message(String message) {
            this.message = message;
            return this;
        }

        public LogBuilder exception(Throwable exception) {
            this.exception = exception;
            return this;
        }

        public LogBuilder field(String key, Object value) {
            this.data.put(key, value);
            return this;
        }

        public LogBuilder operation(String operation) {
            return field(OPERATION_KEY, operation);
        }

        public LogBuilder duration(long durationMs) {
            return field(DURATION_MS_KEY, durationMs);
        }

        public LogBuilder userId(String userId) {
            return field(USER_ID_KEY, userId);
        }

        public LogBuilder tenantId(String tenantId) {
            return field(TENANT_ID_KEY, tenantId);
        }

        public void log() {
            if (message == null) {
                throw new IllegalStateException("Message is required");
            }

            switch (level.toLowerCase()) {
                case "debug" -> logger.debug(message, data);
                case "info" -> {
                    if (exception != null) {
                        logger.error(message, exception, data);
                    } else {
                        logger.info(message, data);
                    }
                }
                case "warn" -> logger.warn(message, data);
                case "error" -> {
                    if (exception != null) {
                        logger.error(message, exception, data);
                    } else {
                        logger.error(message, data);
                    }
                }
                default -> logger.info(message, data);
            }
        }
    }

    /**
     * Creates a log builder for info level.
     *
     * @return a new log builder
     */
    public LogBuilder info() {
        return new LogBuilder(this, "info");
    }

    /**
     * Creates a log builder for debug level.
     *
     * @return a new log builder
     */
    public LogBuilder debug() {
        return new LogBuilder(this, "debug");
    }

    /**
     * Creates a log builder for warn level.
     *
     * @return a new log builder
     */
    public LogBuilder warn() {
        return new LogBuilder(this, "warn");
    }

    /**
     * Creates a log builder for error level.
     *
     * @return a new log builder
     */
    public LogBuilder error() {
        return new LogBuilder(this, "error");
    }

    private String toJsonString(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return data.toString();
        }
    }
}
