/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud;

import com.ghatana.aep.event.EventCloud;
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
 * Tests for {@link DataCloudBackedEventCloud}.
 */
@DisplayName("DataCloudBackedEventCloud [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class DataCloudBackedEventCloudTest extends EventloopTestBase {

    @Mock
    private EventLogStore eventLogStore;

    @Captor
    private ArgumentCaptor<EventEntry> entryCaptor;

    @Captor
    private ArgumentCaptor<TenantContext> tenantCaptor;

    private DataCloudBackedEventCloud eventCloud;

    @BeforeEach
    void setUp() { // GH-90000
        eventCloud = new DataCloudBackedEventCloud(eventLogStore); // GH-90000
    }

    @Test
    void shouldAppendEventToEventLogStore() { // GH-90000
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class))) // GH-90000
            .thenReturn(Promise.of(Offset.of("1 [GH-90000]")));

        byte[] payload = "{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8); // GH-90000

        // WHEN
        String eventId = eventCloud.append("tenant-1", "order.created", payload); // GH-90000

        // THEN
        assertThat(eventId).isNotNull().isNotBlank(); // GH-90000
        verify(eventLogStore).append(tenantCaptor.capture(), entryCaptor.capture()); // GH-90000

        TenantContext capturedTenant = tenantCaptor.getValue(); // GH-90000
        assertThat(capturedTenant.tenantId()).isEqualTo("tenant-1 [GH-90000]");

        EventEntry capturedEntry = entryCaptor.getValue(); // GH-90000
        assertThat(capturedEntry.eventType()).isEqualTo("order.created [GH-90000]");
        assertThat(capturedEntry.eventId()).isNotNull(); // GH-90000
    }

    @Test
    void shouldReturnEventIdOnAppend() { // GH-90000
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class))) // GH-90000
            .thenReturn(Promise.of(Offset.of("42 [GH-90000]")));

        // WHEN
        String eventId = eventCloud.append("t1", "test.event", new byte[]{1, 2, 3}); // GH-90000

        // THEN
        assertThat(eventId).isNotBlank(); // GH-90000
        // UUID format
        assertThat(eventId).matches( // GH-90000
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void shouldCreateSubscriptionWithTenantIsolation() { // GH-90000
        // GIVEN
        when(eventLogStore.getLatestOffset(any(TenantContext.class))) // GH-90000
            .thenReturn(Promise.of(Offset.zero())); // GH-90000
        when(eventLogStore.tail(any(TenantContext.class), any(Offset.class), any())) // GH-90000
            .thenReturn(Promise.of(new EventLogStore.Subscription() { // GH-90000
                @Override public void cancel() {} // GH-90000
                @Override public boolean isCancelled() { return false; } // GH-90000
            }));

        // WHEN
        EventCloud.Subscription sub = eventCloud.subscribe( // GH-90000
            "tenant-1", "order.created",
            (eventId, eventType, payload) -> {}); // GH-90000

        // THEN
        assertThat(sub).isNotNull(); // GH-90000
        assertThat(sub.isCancelled()).isFalse(); // GH-90000

        // Verify tenant isolation
        verify(eventLogStore).getLatestOffset(tenantCaptor.capture()); // GH-90000
        assertThat(tenantCaptor.getValue().tenantId()).isEqualTo("tenant-1 [GH-90000]");
    }

    @Test
    void shouldCancelSubscription() { // GH-90000
        // GIVEN
        when(eventLogStore.getLatestOffset(any(TenantContext.class))) // GH-90000
            .thenReturn(Promise.of(Offset.zero())); // GH-90000
        when(eventLogStore.tail(any(TenantContext.class), any(Offset.class), any())) // GH-90000
            .thenReturn(Promise.of(new EventLogStore.Subscription() { // GH-90000
                private boolean cancelled = false;
                @Override public void cancel() { cancelled = true; } // GH-90000
                @Override public boolean isCancelled() { return cancelled; } // GH-90000
            }));

        EventCloud.Subscription sub = eventCloud.subscribe( // GH-90000
            "tenant-1", "test.event",
            (eventId, eventType, payload) -> {}); // GH-90000

        // WHEN
        sub.cancel(); // GH-90000

        // THEN
        assertThat(sub.isCancelled()).isTrue(); // GH-90000
    }
}
