/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.kernel.modules.observability.service;

import com.ghatana.kernel.context.KernelContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Generic observability service.
 *
 * <p>Provides metrics collection, distributed tracing, and structured logging
 * capabilities for kernel modules.</p>
 *
 * @doc.type class
 * @doc.purpose Generic observability service - metrics, tracing, logging
 * @doc.layer kernel
 * @doc.pattern Service
 * @author Ghatana Kernel Team
 * @since 1.0.0
 */
public final class ObservabilityService {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityService.class);

    private final KernelContext context;
    private final MeterRegistry meterRegistry;
    private final Executor executor;
    private final Map<String, Timer> timers;
    private final Map<String, Counter> counters;
    private volatile boolean started = false;

    /**
     * Creates a new observability service.
     *
     * @param context the kernel context
     */
    public ObservabilityService(KernelContext context) {
        this.context = context;
        this.meterRegistry = new SimpleMeterRegistry();
        this.executor = context.getExecutor("observability");
        this.timers = new ConcurrentHashMap<>();
        this.counters = new ConcurrentHashMap<>();
    }

    /**
     * Starts the observability service.
     */
    public void start() {
        log.info("Starting observability service");
        started = true;
        log.info("Observability service started with {} meters", meterRegistry.getMeters().size());
    }

    /**
     * Stops the observability service.
     */
    public void stop() {
        log.info("Stopping observability service");
        timers.clear();
        counters.clear();
        meterRegistry.clear();
        started = false;
        log.info("Observability service stopped");
    }

    /**
     * Checks if the service is healthy.
     *
     * @return true if healthy
     */
    public boolean isHealthy() {
        return started;
    }

    /**
     * Records a counter metric.
     *
     * @param name the metric name
     * @param tags the metric tags
     */
    public void counter(String name, String... tags) {
        if (!started) return;

        Counter counter = counters.computeIfAbsent(name, n ->
            Counter.builder(n)
                .tags(tags)
                .register(meterRegistry)
        );
        counter.increment();
    }

    /**
     * Records a timer metric.
     *
     * @param name the metric name
     * @param duration the duration to record
     * @param tags the metric tags
     */
    public void timer(String name, Duration duration, String... tags) {
        if (!started) return;

        Timer timer = timers.computeIfAbsent(name, n ->
            Timer.builder(n)
                .tags(tags)
                .register(meterRegistry)
        );
        timer.record(duration);
    }

    /**
     * Times a function execution.
     *
     * @param name the metric name
     * @param operation the operation to time
     * @param tags the metric tags
     * @param <T> the return type
     * @return the operation result
     */
    public <T> T time(String name, Supplier<T> operation, String... tags) {
        if (!started) {
            return operation.get();
        }

        Timer timer = timers.computeIfAbsent(name, n ->
            Timer.builder(n)
                .tags(tags)
                .register(meterRegistry)
        );

        return timer.record(operation);
    }

    /**
     * Creates a new trace context with correlation ID.
     *
     * @param operationName the operation name
     * @return the trace context
     */
    public TraceContext createTrace(String operationName) {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        String spanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        MDC.put("traceId", traceId);
        MDC.put("spanId", spanId);
        MDC.put("operation", operationName);

        return new TraceContext(traceId, spanId, operationName);
    }

    /**
     * Clears the current trace context.
     */
    public void clearTrace() {
        MDC.clear();
    }

    /**
     * Logs a structured message with current trace context.
     *
     * @param level the log level
     * @param message the message
     * @param fields additional structured fields
     */
    public void logStructured(String level, String message, Map<String, Object> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(level).append("] ").append(message);

        // Add MDC context
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        if (contextMap != null && !contextMap.isEmpty()) {
            sb.append(" context={");
            boolean first = true;
            for (Map.Entry<String, String> entry : contextMap.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
            sb.append("}");
        }

        // Add structured fields
        if (fields != null && !fields.isEmpty()) {
            sb.append(" fields={");
            boolean first = true;
            for (Map.Entry<String, Object> entry : fields.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(entry.getKey()).append("=").append(entry.getValue());
                first = false;
            }
            sb.append("}");
        }

        switch (level.toUpperCase()) {
            case "ERROR" -> log.error(sb.toString());
            case "WARN" -> log.warn(sb.toString());
            case "INFO" -> log.info(sb.toString());
            case "DEBUG" -> log.debug(sb.toString());
            default -> log.info(sb.toString());
        }
    }

    /**
     * Gets the meter registry.
     *
     * @return the meter registry
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    // ==================== Trace Context Record ====================

    /**
     * Trace context for distributed tracing.
     */
    public record TraceContext(
        String traceId,
        String spanId,
        String operationName
    ) {
        /**
         * Creates a child span context.
         *
         * @param childOperationName the child operation name
         * @return the child trace context
         */
        public TraceContext createChild(String childOperationName) {
            String childSpanId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            return new TraceContext(traceId, childSpanId, childOperationName);
        }
    }
}
