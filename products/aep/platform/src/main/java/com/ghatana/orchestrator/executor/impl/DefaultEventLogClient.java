/*
 * Copyright (c) 2024 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.orchestrator.executor.impl;

import com.ghatana.orchestrator.executor.AgentEventEmitter;
import com.ghatana.aep.event.EventCloud;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.core.util.JsonUtils;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Production implementation of {@link AgentEventEmitter.EventLogClient} backed by EventCloud.
 *
 * <p>Serializes agent execution events to JSON and appends them to the EventCloud
 * event log for durable persistence and observability. Events are routed to
 * specific event types based on execution status:
 * <ul>
 *   <li>{@code agent.step.execution} — successful/in-progress step events</li>
 *   <li>{@code agent.step.error} — failed and timed-out step events</li>
 * </ul>
 *
 * <p>Each event payload is enriched with publication metadata ({@code _publishedAt},
 * {@code _version}) before serialization. Byte array payloads bypass serialization
 * for pre-encoded events.
 *
 * <p><b>Thread Safety</b>: This class is thread-safe. All mutable state is managed
 * through atomic counters. The underlying EventCloud implementation must also be
 * thread-safe per its contract.
 *
 * <p><b>Metrics</b>: Tracks published count, failed count, and cumulative latency
 * via atomic counters accessible through getter methods.
 *
 * @doc.type class
 * @doc.purpose Routes agent execution events to EventCloud for durable persistence and observability
 * @doc.layer product
 * @doc.pattern Service, Adapter
 * @since 2.0.0
 */
public class DefaultEventLogClient implements AgentEventEmitter.EventLogClient {

    private static final Logger logger = LoggerFactory.getLogger(DefaultEventLogClient.class);

    /** Event type for agent step execution events (success, retry, cancelled). */
    static final String EVENT_TYPE_AGENT_STEP = "agent.step.execution";

    /** Event type for agent step error events (failed, timeout). */
    static final String EVENT_TYPE_AGENT_ERROR = "agent.step.error";

    /** Schema version for the event envelope. */
    static final String SCHEMA_VERSION = "1.0";

    private final EventCloud eventCloud;
    private final ObjectMapper objectMapper;
    private final Executor executor;

    // ---- metrics counters (thread-safe) ----
    private final AtomicLong publishedCount = new AtomicLong();
    private final AtomicLong failedCount = new AtomicLong();
    private final AtomicLong totalLatencyNanos = new AtomicLong();

    /**
     * Creates a client with the given EventCloud, default Jackson ObjectMapper,
     * and virtual-thread-per-task executor.
     *
     * @param eventCloud the EventCloud backend for event persistence
     * @throws NullPointerException if eventCloud is null
     */
    public DefaultEventLogClient(EventCloud eventCloud) {
        this(eventCloud, createDefaultObjectMapper(), Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Creates a client with the given EventCloud and ObjectMapper,
     * using a virtual-thread-per-task executor.
     *
     * @param eventCloud   the EventCloud backend
     * @param objectMapper Jackson ObjectMapper for JSON serialization
     * @throws NullPointerException if any argument is null
     */
    public DefaultEventLogClient(EventCloud eventCloud, ObjectMapper objectMapper) {
        this(eventCloud, objectMapper, Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Creates a client with full control over all dependencies.
     *
     * @param eventCloud   the EventCloud backend
     * @param objectMapper Jackson ObjectMapper for JSON serialization
     * @param executor     executor for blocking publish operations
     * @throws NullPointerException if any argument is null
     */
    public DefaultEventLogClient(EventCloud eventCloud, ObjectMapper objectMapper, Executor executor) {
        this.eventCloud = Objects.requireNonNull(eventCloud, "eventCloud cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.executor = Objects.requireNonNull(executor, "executor cannot be null");
    }

    /**
     * Publish an agent event to EventCloud.
     *
     * <p>The event is serialized to JSON, enriched with metadata, and appended
     * to EventCloud under the appropriate event type. Events with FAILED or
     * TIMEOUT status are routed to {@value #EVENT_TYPE_AGENT_ERROR}; all others
     * go to {@value #EVENT_TYPE_AGENT_STEP}.
     *
     * @param tenantId tenant identifier (non-null)
     * @param event    event payload — typically a {@code Map<String, Object>} from
     *                 {@link AgentEventEmitter}, but also supports raw {@code byte[]}
     *                 and arbitrary serializable objects
     * @return promise that completes when the event is durably stored
     * @throws NullPointerException if tenantId or event is null
     */
    @Override
    public Promise<Void> publishEvent(String tenantId, Object event) {
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(event, "event cannot be null");

        return Promise.ofBlocking(executor, () -> {
            long startNanos = System.nanoTime();
            try {
                byte[] payload = serializeEvent(event);
                String eventType = resolveEventType(event);

                String eventId = eventCloud.append(tenantId, eventType, payload);

                long elapsedNanos = System.nanoTime() - startNanos;
                publishedCount.incrementAndGet();
                totalLatencyNanos.addAndGet(elapsedNanos);

                if (logger.isInfoEnabled()) {
                    logger.info("Published agent event: tenantId={}, type={}, eventId={}, size={}B, latency={:.2f}ms",
                            tenantId, eventType, eventId, payload.length, elapsedNanos / 1_000_000.0);
                }

                return null;
            } catch (JsonProcessingException e) {
                failedCount.incrementAndGet();
                logger.error("Failed to serialize agent event: tenantId={}, eventClass={}",
                        tenantId, event.getClass().getSimpleName(), e);
                throw new EventPublishException("Event serialization failed for tenant " + tenantId, e);
            } catch (EventPublishException e) {
                throw e; // don't double-wrap
            } catch (Exception e) {
                failedCount.incrementAndGet();
                logger.error("Failed to publish agent event to EventCloud: tenantId={}",
                        tenantId, e);
                throw new EventPublishException("Event publish failed for tenant " + tenantId, e);
            }
        });
    }

    // ==================== Internal Helpers ====================

    /**
     * Serialize the event object to JSON bytes.
     *
     * <p>Handles three cases:
     * <ol>
     *   <li>{@code byte[]} — returned as-is (pre-encoded payload)</li>
     *   <li>{@code Map} — enriched with publication metadata, then serialized</li>
     *   <li>Other objects — serialized directly via Jackson</li>
     * </ol>
     *
     * @param event the event to serialize
     * @return JSON bytes
     * @throws JsonProcessingException if serialization fails
     */
    byte[] serializeEvent(Object event) throws JsonProcessingException {
        if (event instanceof byte[] bytes) {
            return bytes;
        }

        if (event instanceof Map<?, ?> eventMap) {
            Map<String, Object> enriched = new LinkedHashMap<>();
            enriched.put("_publishedAt", Instant.now().toString());
            enriched.put("_schemaVersion", SCHEMA_VERSION);
            eventMap.forEach((k, v) -> enriched.put(String.valueOf(k), v));
            return objectMapper.writeValueAsBytes(enriched);
        }

        return objectMapper.writeValueAsBytes(event);
    }

    /**
     * Resolve the EventCloud event type from the event content.
     *
     * <p>If the event is a Map with a {@code "status"} key whose value is
     * {@code "FAILED"} or {@code "TIMEOUT"}, routes to {@value #EVENT_TYPE_AGENT_ERROR}.
     * Otherwise routes to {@value #EVENT_TYPE_AGENT_STEP}.
     *
     * @param event the event object
     * @return resolved event type string
     */
    String resolveEventType(Object event) {
        if (event instanceof Map<?, ?> map) {
            Object status = map.get("status");
            if (status != null) {
                String statusStr = status.toString();
                if ("FAILED".equals(statusStr) || "TIMEOUT".equals(statusStr)) {
                    return EVENT_TYPE_AGENT_ERROR;
                }
            }
        }
        return EVENT_TYPE_AGENT_STEP;
    }

    // ==================== Metrics ====================

    /** Returns the number of events successfully published. */
    public long getPublishedCount() {
        return publishedCount.get();
    }

    /** Returns the number of failed publish attempts. */
    public long getFailedCount() {
        return failedCount.get();
    }

    /** Returns the average publish latency in milliseconds, or 0.0 if no events published. */
    public double getAverageLatencyMs() {
        long count = publishedCount.get();
        if (count == 0) {
            return 0.0;
        }
        return (totalLatencyNanos.get() / (double) count) / 1_000_000.0;
    }

    /** Resets all metric counters to zero. Useful for testing. */
    public void resetMetrics() {
        publishedCount.set(0);
        failedCount.set(0);
        totalLatencyNanos.set(0);
    }

    // ==================== Exception ====================

    /**
     * Exception thrown when event publishing fails.
     * Wraps serialization errors and EventCloud communication failures.
     */
    public static class EventPublishException extends RuntimeException {
        public EventPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ==================== Factory ====================

    /**
     * Creates a default Jackson ObjectMapper configured for event serialization.
     * Registers JavaTimeModule and disables date-as-timestamp for ISO-8601 output.
     */
    private static ObjectMapper createDefaultObjectMapper() {
        return JsonUtils.getDefaultMapper();
    }
}