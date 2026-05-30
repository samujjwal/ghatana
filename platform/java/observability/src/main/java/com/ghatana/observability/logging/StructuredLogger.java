package com.ghatana.observability.logging;

import com.ghatana.observability.correlation.CorrelationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;

/**
 * Structured logger for emitting structured logs, metrics, traces, and audit events.
 *
 * <p><b>Purpose</b><br>
 * Provides a unified logging interface that emits structured logs with correlation
 * context, metrics, traces, and audit events for observability across async workflows.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * StructuredLogger logger = StructuredLogger.getLogger(MyClass.class);
 * CorrelationContext context = CorrelationContext.builder()
 *     .correlationId("req-123")
 *     .tenantId("tenant-456")
 *     .build();
 *
 * logger.info(context, "Operation started", Map.of("operation", "sync"));
 * logger.error(context, "Operation failed", Map.of("error", "timeout"));
 * logger.audit(context, "DATA_ACCESS", "User accessed sensitive data");
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Structured logging with correlation context
 * @doc.layer platform
 * @doc.pattern Structured Logging
 */
public class StructuredLogger {

    private final Logger logger;
    private final String componentName;

    private StructuredLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
        this.componentName = clazz.getSimpleName();
    }

    /**
     * Gets a structured logger for the given class.
     */
    public static StructuredLogger getLogger(Class<?> clazz) {
        return new StructuredLogger(clazz);
    }

    /**
     * Logs an info message with correlation context.
     */
    public void info(CorrelationContext context, String message, Map<String, Object> fields) {
        withMDC(context, () -> {
            logger.info(formatMessage(message, fields));
        });
    }

    /**
     * Logs a warning message with correlation context.
     */
    public void warn(CorrelationContext context, String message, Map<String, Object> fields) {
        withMDC(context, () -> {
            logger.warn(formatMessage(message, fields));
        });
    }

    /**
     * Logs an error message with correlation context.
     */
    public void error(CorrelationContext context, String message, Map<String, Object> fields) {
        withMDC(context, () -> {
            logger.error(formatMessage(message, fields));
        });
    }

    /**
     * Logs an error message with correlation context and exception.
     */
    public void error(CorrelationContext context, String message, Map<String, Object> fields, Throwable throwable) {
        withMDC(context, () -> {
            logger.error(formatMessage(message, fields), throwable);
        });
    }

    /**
     * Logs a debug message with correlation context.
     */
    public void debug(CorrelationContext context, String message, Map<String, Object> fields) {
        withMDC(context, () -> {
            logger.debug(formatMessage(message, fields));
        });
    }

    /**
     * Logs a metric event.
     */
    public void metric(CorrelationContext context, String metricName, double value, Map<String, String> tags) {
        withMDC(context, () -> {
            logger.info("METRIC: {}={} | tags={}", metricName, value, tags);
        });
    }

    /**
     * Logs a trace event.
     */
    public void trace(CorrelationContext context, String spanName, Map<String, Object> attributes) {
        withMDC(context, () -> {
            logger.info("TRACE: span={} | attributes={}", spanName, attributes);
        });
    }

    /**
     * Logs an audit event.
     */
    public void audit(CorrelationContext context, String eventType, String details) {
        withMDC(context, () -> {
            logger.info("AUDIT: type={} | details={}", eventType, details);
        });
    }

    /**
     * Logs a security event.
     */
    public void security(CorrelationContext context, String securityEvent, String details) {
        withMDC(context, () -> {
            logger.warn("SECURITY: event={} | details={}", securityEvent, details);
        });
    }

    /**
     * Logs a degradation event (explicit degraded runtime truth).
     */
    public void degradation(CorrelationContext context, String component, String degradationType, String details) {
        withMDC(context, () -> {
            logger.error("DEGRADATION: component={} | type={} | details={}", component, degradationType, details);
        });
    }

    /**
     * Executes a block with MDC context populated from correlation context.
     */
    private void withMDC(CorrelationContext context, Runnable block) {
        Map<String, String> previousMDC = MDC.getCopyOfContextMap();
        try {
            if (context != null) {
                MDC.setContextMap(context.toMap());
                MDC.put("component", componentName);
            }
            block.run();
        } finally {
            if (previousMDC != null) {
                MDC.setContextMap(previousMDC);
            } else {
                MDC.clear();
            }
        }
    }

    /**
     * Formats a message with structured fields.
     */
    private String formatMessage(String message, Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return message;
        }
        StringBuilder sb = new StringBuilder(message);
        sb.append(" | ");
        fields.forEach((key, value) -> sb.append(key).append("=").append(value).append(", "));
        // Remove trailing comma and space
        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2);
        }
        return sb.toString();
    }
}
