package com.ghatana.datacloud.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Structured logger for Data-Cloud operations.
 *
 * <p>Addresses DC-010: Provides consistent, structured logging across all DataCloud
 * components with automatic MDC context management, operation tracing, and
 * tenant-scoped log correlation.
 *
 * <p>Usage:
 * <pre>{@code
 * DataCloudLogger.forTenant("tenant-123")
 *     .operation("append")
 *     .component("event-log")
 *     .logInfo("Event appended successfully");
 *
 * // Operation timing
 * DataCloudLogger.Operation op = DataCloudLogger.startOperation("append", "event-log", "tenant-123");
 * try {
 *     // ... do work ...
 *     op.succeed();
 * } catch (Exception e) {
 *     op.fail(e);
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Structured logger with MDC context, tenant correlation, and operation tracing
 * @doc.layer product
 * @doc.pattern ObservabilityHelper, StructuredLogging
 *
 * @since 2026-03-27
 */
public final class DataCloudLogger {

    private static final Logger ROOT_LOG = LoggerFactory.getLogger("com.ghatana.datacloud");

    // MDC key constants
    public static final String MDC_TENANT_ID = "datacloud.tenantId";
    public static final String MDC_OPERATION = "datacloud.operation";
    public static final String MDC_COMPONENT = "datacloud.component";
    public static final String MDC_TRACE_ID = "datacloud.traceId";

    private final Logger log;
    private final String tenantId;
    private final String component;

    private DataCloudLogger(Logger log, String tenantId, String component) {
        this.log = log;
        this.tenantId = tenantId;
        this.component = component;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Creates a logger scoped to the given tenant ID.
     *
     * @param tenantId the tenant identifier for MDC correlation
     * @return a logger builder for further scoping
     */
    public static Builder forTenant(String tenantId) {
        return new Builder(tenantId);
    }

    /**
     * Creates a component-scoped logger without tenant context.
     *
     * @param component the component name (e.g., "event-log", "agent-registry")
     * @return a new DataCloudLogger
     */
    public static DataCloudLogger forComponent(String component) {
        Objects.requireNonNull(component, "component");
        Logger log = LoggerFactory.getLogger("com.ghatana.datacloud." + component);
        return new DataCloudLogger(log, null, component);
    }

    // ── Logging methods ───────────────────────────────────────────────────────

    /**
     * Logs an INFO message with tenant/component MDC context.
     *
     * @param message the message to log
     */
    public void info(String message) {
        withMdc(() -> log.info(message));
    }

    /**
     * Logs an INFO message with a format string.
     *
     * @param format SLF4J format string
     * @param args   format arguments
     */
    public void info(String format, Object... args) {
        withMdc(() -> log.info(format, args));
    }

    /**
     * Logs a WARN message.
     */
    public void warn(String message) {
        withMdc(() -> log.warn(message));
    }

    /**
     * Logs a WARN message with a format string.
     */
    public void warn(String format, Object... args) {
        withMdc(() -> log.warn(format, args));
    }

    /**
     * Logs an ERROR message.
     */
    public void error(String message) {
        withMdc(() -> log.error(message));
    }

    /**
     * Logs an ERROR message with a throwable.
     */
    public void error(String message, Throwable throwable) {
        withMdc(() -> log.error(message, throwable));
    }

    /**
     * Logs a DEBUG message.
     */
    public void debug(String format, Object... args) {
        if (log.isDebugEnabled()) {
            withMdc(() -> log.debug(format, args));
        }
    }

    /**
     * Logs an operation result with duration and outcome.
     *
     * @param operationName the operation name
     * @param duration      the duration of the operation
     * @param success       whether the operation succeeded
     * @param extraContext  optional extra key-value context pairs
     */
    public void logOperationResult(String operationName, Duration duration, boolean success,
                                   Map<String, String> extraContext) {
        withMdcAndExtra(operationName, extraContext, () -> {
            if (success) {
                log.info("[{}] {} completed in {}ms",
                    component != null ? component : "datacloud",
                    operationName,
                    duration.toMillis());
            } else {
                log.warn("[{}] {} FAILED after {}ms",
                    component != null ? component : "datacloud",
                    operationName,
                    duration.toMillis());
            }
        });
    }

    // ── Operation timing ──────────────────────────────────────────────────────

    /**
     * Starts a timed operation and returns an {@link Operation} handle.
     * Call {@link Operation#succeed()} or {@link Operation#fail(Throwable)} when done.
     *
     * @param operationName the operation name
     * @param component     the component performing the operation
     * @param tenantId      the tenant ID (null for platform-level ops)
     * @return an active Operation
     */
    public static Operation startOperation(String operationName, String component, String tenantId) {
        Logger opLog = LoggerFactory.getLogger("com.ghatana.datacloud." + component);
        DataCloudLogger logger = new DataCloudLogger(opLog, tenantId, component);
        return new Operation(operationName, component, tenantId, logger, Instant.now());
    }

    // ── MDC helpers ───────────────────────────────────────────────────────────

    private void withMdc(Runnable action) {
        pushMdc();
        try {
            action.run();
        } finally {
            popMdc();
        }
    }

    private void withMdcAndExtra(String operation, Map<String, String> extra, Runnable action) {
        pushMdc();
        if (operation != null) MDC.put(MDC_OPERATION, operation);
        if (extra != null) extra.forEach(MDC::put);
        try {
            action.run();
        } finally {
            if (extra != null) extra.keySet().forEach(MDC::remove);
            if (operation != null) MDC.remove(MDC_OPERATION);
            popMdc();
        }
    }

    private void pushMdc() {
        if (tenantId != null) MDC.put(MDC_TENANT_ID, tenantId);
        if (component != null) MDC.put(MDC_COMPONENT, component);
    }

    private void popMdc() {
        if (tenantId != null) MDC.remove(MDC_TENANT_ID);
        if (component != null) MDC.remove(MDC_COMPONENT);
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    /**
     * Builder for DataCloudLogger with tenant context.
     */
    public static final class Builder {
        private final String tenantId;
        private String component = "datacloud";

        private Builder(String tenantId) {
            this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        }

        /**
         * Sets the component name for this logger.
         *
         * @param component the component name
         * @return this builder
         */
        public Builder component(String component) {
            this.component = Objects.requireNonNull(component, "component");
            return this;
        }

        /**
         * Builds a DataCloudLogger.
         *
         * @return new logger instance
         */
        public DataCloudLogger build() {
            Logger log = LoggerFactory.getLogger("com.ghatana.datacloud." + component);
            return new DataCloudLogger(log, tenantId, component);
        }
    }

    // ── Operation ─────────────────────────────────────────────────────────────

    /**
     * Represents a timed DataCloud operation.
     * Holds start time and logs the result when completed.
     */
    public static final class Operation {
        private final String name;
        private final String component;
        private final String tenantId;
        private final DataCloudLogger logger;
        private final Instant startTime;

        private Operation(String name, String component, String tenantId,
                          DataCloudLogger logger, Instant startTime) {
            this.name = name;
            this.component = component;
            this.tenantId = tenantId;
            this.logger = logger;
            this.startTime = startTime;
        }

        /**
         * Marks the operation as successful.
         */
        public void succeed() {
            Duration elapsed = Duration.between(startTime, Instant.now());
            logger.logOperationResult(name, elapsed, true, null);
        }

        /**
         * Marks the operation as failed with the given cause.
         *
         * @param cause the failure cause
         */
        public void fail(Throwable cause) {
            Duration elapsed = Duration.between(startTime, Instant.now());
            logger.withMdc(() ->
                logger.log.error("[{}] {} FAILED after {}ms: {}",
                    component, name, elapsed.toMillis(),
                    cause != null ? cause.getMessage() : "unknown", cause)
            );
        }

        /**
         * Returns the elapsed duration since the operation started.
         */
        public Duration elapsed() {
            return Duration.between(startTime, Instant.now());
        }
    }
}
