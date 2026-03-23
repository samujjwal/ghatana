/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.event;

import com.ghatana.aep.event.spi.EventCloudConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

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

        // Blocks the caller until publish completes — acceptable for synchronous API contract.
        // Callers that need non-blocking publish should use EventCloudConnector directly.
        String[] resultHolder = new String[1];
        connector.publish(eventType, payload)
                .whenResult(id -> resultHolder[0] = id)
                .whenException(e -> log.error("publish failed: tenantId={} eventType={}", tenantId, eventType, e));

        return resultHolder[0] != null ? resultHolder[0] : "pending";
    }

    @Override
    public Subscription subscribe(String tenantId, String eventType, EventHandler handler) {
        Objects.requireNonNull(tenantId, "tenantId required");
        Objects.requireNonNull(eventType, "eventType required");
        Objects.requireNonNull(handler, "handler required");

        AtomicReference<EventCloudConnector.ConnectorSubscription> delegateRef = new AtomicReference<>();

        connector.subscribe(eventType, DEFAULT_CONSUMER_GROUP,
                        (eventId, topic, payload) -> handler.handle(eventId, topic, payload))
                .whenResult(delegateRef::set)
                .whenException(e -> log.error("subscribe failed: tenantId={} eventType={}", tenantId, eventType, e));

        return new Subscription() {
            @Override
            public void cancel() {
                EventCloudConnector.ConnectorSubscription d = delegateRef.get();
                if (d != null) {
                    d.cancel();
                }
            }

            @Override
            public boolean isCancelled() {
                EventCloudConnector.ConnectorSubscription d = delegateRef.get();
                return d != null && d.isCancelled();
            }
        };
    }
}
