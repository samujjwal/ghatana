/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.event;

import com.ghatana.aep.event.spi.EventCloudConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Map;

/**
 * Bridges a pluggable {@link EventCloudConnector} to the AEP {@link EventCloud} facade.
 *
 * <p>This adapter is instantiated by {@link AepEventCloudFactory} when
 * {@code EVENT_CLOUD_TRANSPORT} is {@code grpc} or {@code http}.
 *
 * @doc.type class
 * @doc.purpose Adapter from EventCloudConnector SPI to EventCloud facade
 * @doc.layer platform
 * @doc.pattern Adapter
 */
final class ConnectorBackedEventCloud implements EventCloud {

    private static final Logger log = LoggerFactory.getLogger(ConnectorBackedEventCloud.class);
    private static final String DEFAULT_CONSUMER_GROUP = "aep-default";

    private final EventCloudConnector connector;

    ConnectorBackedEventCloud(EventCloudConnector connector) {
        this.connector = Objects.requireNonNull(connector, "connector required");
    }

    @Override
    public String append(String tenantId, String eventType, byte[] payload) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(eventType, "eventType required");
        Objects.requireNonNull(payload, "payload required");

        String eventId = UUID.randomUUID().toString();
        connector.publish(eventType, payload)
                .whenResult(connectorEventId ->
                    log.debug("publish succeeded: tenantId={} eventType={} localEventId={} connectorEventId={}",
                        tenantId, eventType, eventId, connectorEventId))
                .whenException(e -> log.error(
                    "publish failed: tenantId={} eventType={} localEventId={}",
                    tenantId,
                    eventType,
                    eventId,
                    e));

        return eventId;
    }

    @Override
    public Subscription subscribe(String tenantId, String eventType, EventHandler handler) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(eventType, "eventType required");
        Objects.requireNonNull(handler, "handler required");

        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicReference<EventCloudConnector.ConnectorSubscription> delegateRef = new AtomicReference<>();

        connector.subscribe(eventType, DEFAULT_CONSUMER_GROUP,
                        (eventId, topic, payload) -> handler.handle(eventId, topic, payload))
                .whenResult(delegate -> {
                    delegateRef.set(delegate);
                    if (cancelled.get()) {
                        delegate.cancel();
                    }
                })
                .whenException(e -> log.error("subscribe failed: tenantId={} eventType={}", tenantId, eventType, e));

        return new Subscription() {
            @Override
            public void cancel() {
                cancelled.set(true);
                EventCloudConnector.ConnectorSubscription d = delegateRef.get();
                if (d != null) {
                    d.cancel();
                }
            }

            @Override
            public boolean isCancelled() {
                EventCloudConnector.ConnectorSubscription d = delegateRef.get();
                return cancelled.get() || (d != null && d.isCancelled());
            }
        };
    }

    @Override
    public boolean createCheckpoint(String tenantId, String checkpointId, Map<String, Object> metadata) {
        throw new UnsupportedOperationException("Connector-backed EventCloud checkpoint creation is not implemented");
    }

    @Override
    public Map<String, Object> readCheckpoint(String tenantId, String checkpointId) {
        throw new UnsupportedOperationException("Connector-backed EventCloud checkpoint reads are not implemented");
    }

    @Override
    public boolean deleteCheckpoint(String tenantId, String checkpointId) {
        throw new UnsupportedOperationException("Connector-backed EventCloud checkpoint deletion is not implemented");
    }

    @Override
    public List<CheckpointInfo> listCheckpoints(String tenantId) {
        throw new UnsupportedOperationException("Connector-backed EventCloud checkpoint listing is not implemented");
    }

    @Override
    public ReplayStatistics replay(String tenantId, String checkpointId, ReplayMode mode, EventHandler handler) {
        throw new UnsupportedOperationException("Connector-backed EventCloud replay is not implemented");
    }
}
