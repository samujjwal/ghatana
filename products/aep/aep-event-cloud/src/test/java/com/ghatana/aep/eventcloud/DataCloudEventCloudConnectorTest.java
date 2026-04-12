/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud;

import com.ghatana.aep.event.spi.EventCloudConnector;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.EventLogStore.EventEntry;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DataCloudEventCloudConnector}.
 */
@DisplayName("DataCloudEventCloudConnector")
@ExtendWith(MockitoExtension.class)
class DataCloudEventCloudConnectorTest extends EventloopTestBase {

    @Mock
    private EventLogStore eventLogStore;

    @Captor
    private ArgumentCaptor<EventEntry> entryCaptor;

    @Captor
    private ArgumentCaptor<TenantContext> tenantCaptor;

    private DataCloudEventCloudConnector connector;

    @BeforeEach
    void setUp() {
        connector = new DataCloudEventCloudConnector(eventLogStore, "test-tenant");
    }

    @Test
    void shouldPublishEventToEventLogStore() {
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class)))
            .thenReturn(Promise.of(Offset.of("1")));

        byte[] payload = "{\"order\":123}".getBytes(StandardCharsets.UTF_8);

        // WHEN
        String eventId = runPromise(() -> connector.publish("order.created", payload));

        // THEN
        assertThat(eventId).isNotBlank();
        verify(eventLogStore).append(tenantCaptor.capture(), entryCaptor.capture());

        // Verify tenant
        assertThat(tenantCaptor.getValue().tenantId()).isEqualTo("test-tenant");

        // Verify event entry
        EventEntry entry = entryCaptor.getValue();
        assertThat(entry.eventType()).isEqualTo("order.created");
        assertThat(entry.eventId()).isNotNull();
    }

    @Test
    void shouldReturnEventIdOnPublish() {
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class)))
            .thenReturn(Promise.of(Offset.of("42")));

        // WHEN
        String eventId = runPromise(() ->
            connector.publish("test.topic", "data".getBytes(StandardCharsets.UTF_8)));

        // THEN
        assertThat(eventId).matches(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void shouldUseCustomTenantId() {
        // GIVEN
        DataCloudEventCloudConnector customConnector =
            new DataCloudEventCloudConnector(eventLogStore, "custom-tenant");
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class)))
            .thenReturn(Promise.of(Offset.of("1")));

        // WHEN
        runPromise(() -> customConnector.publish("topic", new byte[]{1}));

        // THEN
        verify(eventLogStore).append(tenantCaptor.capture(), any());
        assertThat(tenantCaptor.getValue().tenantId()).isEqualTo("custom-tenant");
    }

    @Test
    void shouldPublishWithExplicitTenantId() {
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class)))
            .thenReturn(Promise.of(Offset.of("1")));

        // WHEN
        String eventId = runPromise(() ->
            connector.publish("explicit-tenant", "topic", new byte[]{1}));

        // THEN
        assertThat(eventId).isNotBlank();
        verify(eventLogStore).append(tenantCaptor.capture(), any());
        assertThat(tenantCaptor.getValue().tenantId()).isEqualTo("explicit-tenant");
    }

    @Test
    void shouldSubscribeToEventLogStore() {
        // GIVEN
        when(eventLogStore.getLatestOffset(any(TenantContext.class)))
            .thenReturn(Promise.of(Offset.zero()));
        when(eventLogStore.tail(any(TenantContext.class), any(Offset.class), any()))
            .thenReturn(Promise.of(new EventLogStore.Subscription() {
                @Override public void cancel() {}
                @Override public boolean isCancelled() { return false; }
            }));

        // WHEN
        EventCloudConnector.ConnectorSubscription sub = runPromise(() ->
            connector.subscribe("order.created", "group-1",
                (eventId, topic, payload) -> {}));

        // THEN
        assertThat(sub).isNotNull();
        assertThat(sub.isCancelled()).isFalse();
    }

    @Test
    void shouldCancelConnectorSubscription() {
        // GIVEN
        when(eventLogStore.getLatestOffset(any(TenantContext.class)))
            .thenReturn(Promise.of(Offset.zero()));
        when(eventLogStore.tail(any(TenantContext.class), any(Offset.class), any()))
            .thenReturn(Promise.of(new EventLogStore.Subscription() {
                private boolean cancelled = false;
                @Override public void cancel() { cancelled = true; }
                @Override public boolean isCancelled() { return cancelled; }
            }));

        EventCloudConnector.ConnectorSubscription sub = runPromise(() ->
            connector.subscribe("topic", "group",
                (eventId, topic, payload) -> {}));

        // WHEN
        sub.cancel();

        // THEN
        assertThat(sub.isCancelled()).isTrue();
    }

    @Test
    void shouldPropagatePublishFailure() {
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class)))
            .thenReturn(Promise.ofException(new RuntimeException("Store unavailable")));

        // WHEN/THEN
        try {
            runPromise(() -> connector.publish("topic", new byte[]{1}));
        } catch (Exception e) {
            assertThat(e).hasMessageContaining("Store unavailable");
        }
        clearFatalError();
    }
}
