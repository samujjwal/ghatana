/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud;

import com.ghatana.aep.event.spi.EventCloudCheckpoint;
import com.ghatana.aep.event.spi.EventCloudCheckpointStore;
import com.ghatana.aep.event.spi.EventCloudConnector;
import com.ghatana.aep.event.spi.EventCloudOffset;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * {@link EventCloudConnector} implementation backed by Data-Cloud's {@link EventLogStore}.
 *
 * <p>Selected when {@code EVENT_CLOUD_TRANSPORT=eventlog}. This connector stores events
 * directly in the Data-Cloud event log, providing durable append-only persistence with
 * multi-tenant isolation. It uses the topic name as the event type and routes all
 * operations through the Data-Cloud SPI.
 *
 * <p><strong>Tenant Safety:</strong> Production use requires explicit tenant context.
 * All production event flows must use tenant-aware publish/subscribe APIs.
 * Default tenant usage is removed for production safety.
 *
 * <p><strong>Consumer Group Durability:</strong> Consumer group offsets are persisted via
 * {@link EventCloudCheckpointStore} for replay and recovery. Subscriptions can resume from
 * saved checkpoints or from explicit replay offsets.
 *
 * <p><strong>Event Envelope:</strong> Full Data Cloud event envelope fields are preserved:
 * source, subject, schemaVersion, correlationId, causationId, classification, policyContext,
 * provenance, and traceContext.
 *
 * @doc.type class
 * @doc.purpose EventCloudConnector backed by Data-Cloud EventLogStore with tenant safety and durable checkpoints
 * @doc.layer product
 * @doc.pattern Adapter, SPI Implementation
 */
public final class DataCloudEventCloudConnector implements EventCloudConnector {

    private static final Logger log = LoggerFactory.getLogger(DataCloudEventCloudConnector.class);

    private final EventLogStore eventLogStore;
    private final Optional<EventCloudCheckpointStore> checkpointStore;
    private final Map<String, AtomicLong> processedCounters = new ConcurrentHashMap<>();

    /**
     * Creates a connector with checkpoint support.
     *
     * @param eventLogStore   Data-Cloud event log store
     * @param checkpointStore optional checkpoint store for durable consumer group offsets
     */
    public DataCloudEventCloudConnector(
            EventLogStore eventLogStore,
            EventCloudCheckpointStore checkpointStore) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore required");
        this.checkpointStore = Optional.ofNullable(checkpointStore);
    }

    @Override
    public Promise<String> publish(String topic, byte[] payload) {
        throw new UnsupportedOperationException(
            "Tenant-agnostic publish is not supported. Use publish(tenantId, topic, payload) or " +
            "publish(tenantId, topic, payload, envelope) with explicit tenant context."
        );
    }

    @Override
    public Promise<ConnectorSubscription> subscribe(
            String topic,
            String consumerGroup,
            EventPayloadHandler handler) {
        throw new UnsupportedOperationException(
            "Tenant-agnostic subscribe is not supported. Use subscribe(tenantId, topic, consumerGroup, handler) " +
            "or subscribe(tenantId, topic, consumerGroup, handler, replayOffset) with explicit tenant context."
        );
    }

    /**
     * Publish an event with explicit tenant context and full envelope metadata.
     *
     * @param tenantId       tenant identifier (required for production)
     * @param topic          event type / topic
     * @param payload        event payload bytes
     * @param envelope       optional envelope metadata (source, correlationId, etc.)
     * @return promise of assigned event ID
     */
    public Promise<String> publish(
            String tenantId,
            String topic,
            byte[] payload,
            EventEnvelope envelope) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(topic, "topic required");
        Objects.requireNonNull(payload, "payload required");

        UUID eventId = UUID.randomUUID();
        EventEntry.Builder entryBuilder = EventEntry.builder()
            .eventId(eventId)
            .eventType(topic)
            .payload(ByteBuffer.wrap(payload));

        // Apply envelope metadata if provided
        if (envelope != null) {
            entryBuilder
                .eventVersion(envelope.schemaVersion())
                .correlationId(envelope.correlationId())
                .causationId(envelope.causationId())
                .source(envelope.source())
                .userId(envelope.userId());
            
            // Add custom headers for classification, policyContext, provenance, traceContext
            Map<String, String> headers = new java.util.HashMap<>();
            if (envelope.classification() != null) {
                headers.put("classification", envelope.classification());
            }
            if (envelope.policyContext() != null) {
                headers.put("policyContext", envelope.policyContext());
            }
            if (envelope.provenance() != null) {
                headers.put("provenance", envelope.provenance());
            }
            if (envelope.traceContext() != null) {
                headers.put("traceContext", envelope.traceContext());
            }
            if (envelope.subject() != null) {
                headers.put("subject", envelope.subject());
            }
            entryBuilder.headers(headers);
        }

        EventEntry entry = entryBuilder.build();
        TenantContext tenant = TenantContext.of(tenantId);

        // Emit trace/audit metadata for bridge operations
        String traceId = envelope != null ? envelope.traceContext() : UUID.randomUUID().toString();
        log.info("[event-cloud-connector] Publishing event={} tenant={} topic={} traceId={} correlationId={}",
            eventId, tenantId, topic, traceId, envelope != null ? envelope.correlationId() : "none");

        return eventLogStore.append(tenant, entry)
            .map(offset -> {
                log.debug("[event-cloud-connector] Published event={} tenant={} topic={} offset={} traceId={}",
                    eventId, tenantId, topic, offset, traceId);
                return eventId.toString();
            })
            .then(Promise::of, e -> {
                log.error("[event-cloud-connector] Publish failed tenant={} topic={} traceId={}: {}",
                    tenantId, topic, traceId, e.getMessage(), e);
                return Promise.ofException(e);
            });
    }

    /**
     * Publish an event with explicit tenant context (minimal envelope).
     *
     * @param tenantId tenant identifier
     * @param topic    event type / topic
     * @param payload  event payload bytes
     * @return promise of assigned event ID
     */
    public Promise<String> publish(String tenantId, String topic, byte[] payload) {
        return publish(tenantId, topic, payload, null);
    }

    /**
     * Subscribe with explicit tenant context and optional replay offset.
     *
     * @param tenantId       tenant identifier (required for production)
     * @param topic          event type / topic
     * @param consumerGroup  consumer group identifier for offset management
     * @param handler        event payload handler
     * @param replayOffset   optional offset to start replay from (if not provided, resumes from checkpoint or latest)
     * @param failureCallback optional callback for handler failures with backpressure control
     * @return promise of subscription handle
     */
    public Promise<ConnectorSubscription> subscribe(
            String tenantId,
            String topic,
            String consumerGroup,
            EventPayloadHandler handler,
            Optional<Offset> replayOffset,
            Consumer<Exception> failureCallback) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(topic, "topic required");
        Objects.requireNonNull(consumerGroup, "consumerGroup required");
        Objects.requireNonNull(handler, "handler required");

        TenantContext tenant = TenantContext.of(tenantId);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        String consumerId = tenantId + ":" + consumerGroup + ":" + topic;

        // Determine starting offset: replay offset > checkpoint > latest
        Promise<Offset> startOffsetPromise = replayOffset
            .map(Promise::of)
            .orElseGet(() -> {
                if (checkpointStore.isPresent()) {
                    return checkpointStore.get().load(tenantId, consumerId)
                        .then(checkpointOpt -> {
                            if (checkpointOpt.isPresent()) {
                                EventCloudOffset checkpointOffset = checkpointOpt.get().offset();
                                log.info("[event-cloud-connector] Resuming from checkpoint tenant={} consumer={} offset={}",
                                    tenantId, consumerId, checkpointOffset);
                                return Promise.of(toDataCloudOffset(checkpointOffset));
                            }
                            return eventLogStore.getLatestOffset(tenant);
                        });
                }
                return eventLogStore.getLatestOffset(tenant);
            });

        return startOffsetPromise.then(startOffset ->
            eventLogStore.tail(tenant, startOffset, entry -> {
                if (!cancelled.get() && topic.equals(entry.eventType())) {
                    byte[] data = new byte[entry.payload().remaining()];
                    entry.payload().duplicate().get(data);
                    
                    try {
                        handler.onEvent(
                            entry.eventId().toString(),
                            entry.eventType(),
                            data);
                        
                        // Update processed counter
                        processedCounters.computeIfAbsent(consumerId, k -> new AtomicLong(0)).incrementAndGet();
                        
                        // Periodically checkpoint (every 100 events)
                        if (processedCounters.get(consumerId).get() % 100 == 0 && checkpointStore.isPresent()) {
                            saveCheckpoint(tenantId, consumerId, toEventCloudOffset(startOffset));
                        }
                    } catch (Exception e) {
                        log.error("[event-cloud-connector] Handler failed for event={} tenant={} topic={}: {}",
                            entry.eventId(), tenantId, topic, e.getMessage(), e);
                        // Backpressure: notify failure callback if provided
                        if (failureCallback != null) {
                            failureCallback.accept(e);
                        }
                    }
                }
            }))
            .map(storeSubscription -> new ConnectorSubscription() {
                @Override
                public void cancel() {
                    cancelled.set(true);
                    storeSubscription.cancel();
                    
                    // Save final checkpoint on cancellation
                    if (checkpointStore.isPresent()) {
                        saveCheckpoint(tenantId, consumerId, toEventCloudOffset(startOffset));
                    }
                    
                    log.debug("[event-cloud-connector] Subscription cancelled tenant={} topic={} group={}",
                        tenantId, topic, consumerGroup);
                }

                @Override
                public boolean isCancelled() {
                    return cancelled.get();
                }
            });
    }

    /**
     * Subscribe with explicit tenant context and optional replay offset (no failure callback).
     *
     * @param tenantId       tenant identifier (required for production)
     * @param topic          event type / topic
     * @param consumerGroup  consumer group identifier for offset management
     * @param handler        event payload handler
     * @param replayOffset   optional offset to start replay from (if not provided, resumes from checkpoint or latest)
     * @return promise of subscription handle
     */
    public Promise<ConnectorSubscription> subscribe(
            String tenantId,
            String topic,
            String consumerGroup,
            EventPayloadHandler handler,
            Optional<Offset> replayOffset) {
        return subscribe(tenantId, topic, consumerGroup, handler, replayOffset, null);
    }

    /**
     * Subscribe with explicit tenant context (resumes from checkpoint or latest).
     *
     * @param tenantId      tenant identifier
     * @param topic         event type / topic
     * @param consumerGroup consumer group identifier
     * @param handler       event payload handler
     * @return promise of subscription handle
     */
    public Promise<ConnectorSubscription> subscribe(
            String tenantId,
            String topic,
            String consumerGroup,
            EventPayloadHandler handler) {
        return subscribe(tenantId, topic, consumerGroup, handler, Optional.empty());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Internal helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private void saveCheckpoint(String tenantId, String consumerId, EventCloudOffset offset) {
        EventCloudCheckpoint checkpoint = new EventCloudCheckpoint(
            tenantId,
            consumerId,
            offset,
            Instant.now(),
            Map.of("processed", processedCounters.getOrDefault(consumerId, new AtomicLong(0)).get())
        );
        checkpointStore.get().save(checkpoint)
            .whenResult(v -> log.debug("[event-cloud-connector] Checkpoint saved tenant={} consumer={} offset={}",
                tenantId, consumerId, offset))
            .whenException(e -> log.warn("[event-cloud-connector] Checkpoint save failed tenant={} consumer={}: {}",
                tenantId, consumerId, e.getMessage()));
    }

    private Offset toDataCloudOffset(EventCloudOffset eventCloudOffset) {
        // Convert EventCloudOffset to platform Offset
        return Offset.of(eventCloudOffset.value());
    }

    private EventCloudOffset toEventCloudOffset(Offset offset) {
        // Convert platform Offset to EventCloudOffset
        return new EventCloudOffset(offset.value(), offset.toString());
    }

    /**
     * Event envelope metadata for Data Cloud event fields.
     */
    public record EventEnvelope(
        String source,
        String subject,
        String schemaVersion,
        String correlationId,
        String causationId,
        String classification,
        String policyContext,
        String provenance,
        String traceContext,
        String userId
    ) {}
}
