/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.agent;

import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.EventLogStore.EventEntry;
import com.ghatana.datacloud.spi.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Lightweight event publisher for YAPPC workflow steps.
 *
 * <p>Replaces the deprecated {@code EventCloud} facade with direct
 * {@link EventLogStore} usage from Data Cloud SPI.
 *
 * @doc.type class
 * @doc.purpose Event publishing facade for workflow steps backed by EventLogStore
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EventLogStore eventLogStore;
    private final String defaultTenantId;

    public EventPublisher(EventLogStore eventLogStore, String defaultTenantId) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore required");
        this.defaultTenantId = Objects.requireNonNull(defaultTenantId, "defaultTenantId required");
    }

    /**
     * Publish an event with topic and payload.
     *
     * @param topic   event type/topic
     * @param payload event payload as a map
     * @return promise of void
     */
    public Promise<Void> publish(String topic, Map<String, Object> payload) {
        return publish(topic, defaultTenantId, payload);
    }

    /**
     * Publish an event with topic, tenant ID, and payload.
     *
     * @param topic    event type/topic
     * @param tenantId tenant identifier
     * @param payload  event payload as a map
     * @return promise of void
     */
    public Promise<Void> publish(String topic, String tenantId, Map<String, Object> payload) {
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(payload);
            EventEntry entry = EventEntry.builder()
                .eventType(topic)
                .timestamp(Instant.now())
                .payload(ByteBuffer.wrap(bytes))
                .contentType("application/json")
                .build();

            TenantContext tenant = TenantContext.of(tenantId);
            return eventLogStore.append(tenant, entry)
                .map(offset -> {
                    log.debug("[event-publisher] Published topic={} tenant={} offset={}", topic, tenantId, offset);
                    return (Void) null;
                });
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }

    /**
     * Publish an event with all common parameters.
     *
     * @param topic     event type/topic
     * @param tenantId  tenant identifier
     * @param runId     correlation/run ID
     * @param category  event category
     * @param step      step name
     * @param payload   event payload
     * @param timestamp event timestamp
     * @return promise of void
     */
    public Promise<Void> publish(
            String topic,
            String tenantId,
            String runId,
            String category,
            String step,
            Map<String, Object> payload,
            Instant timestamp) {
        try {
            byte[] bytes = MAPPER.writeValueAsBytes(payload);
            EventEntry entry = EventEntry.builder()
                .eventType(topic)
                .timestamp(timestamp)
                .payload(ByteBuffer.wrap(bytes))
                .contentType("application/json")
                .headers(Map.of("runId", runId, "category", category, "step", step))
                .build();

            TenantContext tenant = TenantContext.of(tenantId);
            return eventLogStore.append(tenant, entry)
                .map(offset -> {
                    log.debug("[event-publisher] Published topic={} tenant={} run={} offset={}",
                        topic, tenantId, runId, offset);
                    return (Void) null;
                });
        } catch (Exception e) {
            return Promise.ofException(e);
        }
    }
}
