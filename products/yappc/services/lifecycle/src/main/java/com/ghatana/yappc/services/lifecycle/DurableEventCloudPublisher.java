/*
 * Copyright (c) 2026 Ghatana Technologies
 * YAPPC Lifecycle Service
 */
package com.ghatana.yappc.services.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.aep.event.EventCloud;
import com.ghatana.yappc.agent.AepEventPublisher;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * Durable, EventCloud-backed implementation of {@link AepEventPublisher}.
 *
 * <p>Replaces {@code AepHttpEventPublisher} (deleted in Phase 1c). Instead of
 * posting events to an AEP HTTP endpoint, this publisher calls
 * {@link EventCloud#append(String, String, byte[])} directly. The active
 * {@code EventCloudConnector} (selected via {@code EVENT_CLOUD_TRANSPORT}) handles
 * all transport details, backpressure, and retries transparently — YAPPC code
 * never manages connection or retry logic.
 *
 * <p>Serialize: {@code Map<String, Object>} payload → UTF-8 JSON bytes →
 * {@link EventCloud#append(String, String, byte[])}.
 *
 * <p>The {@link EventCloud} instance is provided by the DI graph from
 * {@code AepEventCloudFactory.createDefault()} — transport selected via
 * {@code EVENT_CLOUD_TRANSPORT} env var (default: {@code eventlog}).
 *
 * @doc.type class
 * @doc.purpose Durable EventCloud-backed AepEventPublisher; delegates to active connector (Phase 1c)
 * @doc.layer product
 * @doc.pattern Adapter
 * @doc.gaa.lifecycle act
 */
public final class DurableEventCloudPublisher implements AepEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DurableEventCloudPublisher.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EventCloud eventCloud;

    /**
     * Creates a publisher backed by the given {@link EventCloud}.
     *
     * @param eventCloud active EventCloud facade (never {@code null})
     */
    public DurableEventCloudPublisher(EventCloud eventCloud) {
        this.eventCloud = Objects.requireNonNull(eventCloud, "eventCloud must not be null");
    }

    /**
     * Publishes {@code payload} as a JSON-encoded event to the EventCloud.
     *
     * <p>Serializes the payload map to UTF-8 JSON bytes then calls
     * {@link EventCloud#append(String, String, byte[])}. The call is synchronous
     * at the EventCloud API level; durability and retry are handled by the active
     * connector implementation.
     *
     * @param eventType canonical event type string (e.g. {@code "lifecycle.phase.advanced"})
     * @param tenantId  tenant identifier for multi-tenant isolation
     * @param payload   event payload fields
     * @return {@code Promise.complete()} on success; {@code Promise.ofException()} on serialization error
     */
    @Override
    public Promise<Void> publish(String eventType, String tenantId, Map<String, Object> payload) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(payload, "payload");

        try {
            byte[] bytes = MAPPER.writeValueAsBytes(payload);
            eventCloud.append(tenantId, eventType, bytes);
            log.debug("Published event type={} tenant={} bytes={}", eventType, tenantId, bytes.length);
            return Promise.complete();
        } catch (Exception e) {
            log.error("Failed to publish event type={} tenant={}: {}",
                    eventType, tenantId, e.getMessage(), e);
            return Promise.ofException(e);
        }
    }
}
