/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
@DisplayName("DataCloudEventCloudConnector [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class DataCloudEventCloudConnectorTest extends EventloopTestBase {

    @Mock
    private EventLogStore eventLogStore;

    @Captor
    private ArgumentCaptor<EventEntry> entryCaptor;

    @Captor
    private ArgumentCaptor<TenantContext> tenantCaptor;

    private DataCloudEventCloudConnector connector;

    @BeforeEach
    void setUp() { // GH-90000
        connector = new DataCloudEventCloudConnector(eventLogStore, "test-tenant"); // GH-90000
    }

    @Test
    void shouldPublishEventToEventLogStore() { // GH-90000
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class))) // GH-90000
            .thenReturn(Promise.of(Offset.of("1 [GH-90000]")));

        byte[] payload = "{\"order\":123}".getBytes(StandardCharsets.UTF_8); // GH-90000

        // WHEN
        String eventId = runPromise(() -> connector.publish("order.created", payload)); // GH-90000

        // THEN
        assertThat(eventId).isNotBlank(); // GH-90000
        verify(eventLogStore).append(tenantCaptor.capture(), entryCaptor.capture()); // GH-90000

        // Verify tenant
        assertThat(tenantCaptor.getValue().tenantId()).isEqualTo("test-tenant [GH-90000]");

        // Verify event entry
        EventEntry entry = entryCaptor.getValue(); // GH-90000
        assertThat(entry.eventType()).isEqualTo("order.created [GH-90000]");
        assertThat(entry.eventId()).isNotNull(); // GH-90000
    }

    @Test
    void shouldReturnEventIdOnPublish() { // GH-90000
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class))) // GH-90000
            .thenReturn(Promise.of(Offset.of("42 [GH-90000]")));

        // WHEN
        String eventId = runPromise(() -> // GH-90000
            connector.publish("test.topic", "data".getBytes(StandardCharsets.UTF_8))); // GH-90000

        // THEN
        assertThat(eventId).matches( // GH-90000
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void shouldUseCustomTenantId() { // GH-90000
        // GIVEN
        DataCloudEventCloudConnector customConnector =
            new DataCloudEventCloudConnector(eventLogStore, "custom-tenant"); // GH-90000
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class))) // GH-90000
            .thenReturn(Promise.of(Offset.of("1 [GH-90000]")));

        // WHEN
        runPromise(() -> customConnector.publish("topic", new byte[]{1})); // GH-90000

        // THEN
        verify(eventLogStore).append(tenantCaptor.capture(), any()); // GH-90000
        assertThat(tenantCaptor.getValue().tenantId()).isEqualTo("custom-tenant [GH-90000]");
    }

    @Test
    void shouldPublishWithExplicitTenantId() { // GH-90000
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class))) // GH-90000
            .thenReturn(Promise.of(Offset.of("1 [GH-90000]")));

        // WHEN
        String eventId = runPromise(() -> // GH-90000
            connector.publish("explicit-tenant", "topic", new byte[]{1})); // GH-90000

        // THEN
        assertThat(eventId).isNotBlank(); // GH-90000
        verify(eventLogStore).append(tenantCaptor.capture(), any()); // GH-90000
        assertThat(tenantCaptor.getValue().tenantId()).isEqualTo("explicit-tenant [GH-90000]");
    }

    @Test
    void shouldSubscribeToEventLogStore() { // GH-90000
        // GIVEN
        when(eventLogStore.getLatestOffset(any(TenantContext.class))) // GH-90000
            .thenReturn(Promise.of(Offset.zero())); // GH-90000
        when(eventLogStore.tail(any(TenantContext.class), any(Offset.class), any())) // GH-90000
            .thenReturn(Promise.of(new EventLogStore.Subscription() { // GH-90000
                @Override public void cancel() {} // GH-90000
                @Override public boolean isCancelled() { return false; } // GH-90000
            }));

        // WHEN
        EventCloudConnector.ConnectorSubscription sub = runPromise(() -> // GH-90000
            connector.subscribe("order.created", "group-1", // GH-90000
                (eventId, topic, payload) -> {})); // GH-90000

        // THEN
        assertThat(sub).isNotNull(); // GH-90000
        assertThat(sub.isCancelled()).isFalse(); // GH-90000
    }

    @Test
    void shouldCancelConnectorSubscription() { // GH-90000
        // GIVEN
        when(eventLogStore.getLatestOffset(any(TenantContext.class))) // GH-90000
            .thenReturn(Promise.of(Offset.zero())); // GH-90000
        when(eventLogStore.tail(any(TenantContext.class), any(Offset.class), any())) // GH-90000
            .thenReturn(Promise.of(new EventLogStore.Subscription() { // GH-90000
                private boolean cancelled = false;
                @Override public void cancel() { cancelled = true; } // GH-90000
                @Override public boolean isCancelled() { return cancelled; } // GH-90000
            }));

        EventCloudConnector.ConnectorSubscription sub = runPromise(() -> // GH-90000
            connector.subscribe("topic", "group", // GH-90000
                (eventId, topic, payload) -> {})); // GH-90000

        // WHEN
        sub.cancel(); // GH-90000

        // THEN
        assertThat(sub.isCancelled()).isTrue(); // GH-90000
    }

    @Test
    void shouldPropagatePublishFailure() { // GH-90000
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class))) // GH-90000
            .thenReturn(Promise.ofException(new RuntimeException("Store unavailable [GH-90000]")));

        // WHEN/THEN
        try {
            runPromise(() -> connector.publish("topic", new byte[]{1})); // GH-90000
        } catch (Exception e) { // GH-90000
            assertThat(e).hasMessageContaining("Store unavailable [GH-90000]");
        }
        clearFatalError(); // GH-90000
    }
}
