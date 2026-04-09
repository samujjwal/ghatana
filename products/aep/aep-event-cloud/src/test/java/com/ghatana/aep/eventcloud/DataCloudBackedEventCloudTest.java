/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.eventcloud;

import com.ghatana.aep.event.EventCloud;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.EventLogStore.EventEntry;
import com.ghatana.datacloud.spi.TenantContext;
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
@DisplayName("DataCloudBackedEventCloud")
@ExtendWith(MockitoExtension.class)
class DataCloudBackedEventCloudTest extends EventloopTestBase {

    @Mock
    private EventLogStore eventLogStore;

    @Captor
    private ArgumentCaptor<EventEntry> entryCaptor;

    @Captor
    private ArgumentCaptor<TenantContext> tenantCaptor;

    private DataCloudBackedEventCloud eventCloud;

    @BeforeEach
    void setUp() {
        eventCloud = new DataCloudBackedEventCloud(eventLogStore);
    }

    @Test
    void shouldAppendEventToEventLogStore() {
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class)))
            .thenReturn(Promise.of(Offset.of("1")));

        byte[] payload = "{\"key\":\"value\"}".getBytes(StandardCharsets.UTF_8);

        // WHEN
        String eventId = eventCloud.append("tenant-1", "order.created", payload);

        // THEN
        assertThat(eventId).isNotNull().isNotBlank();
        verify(eventLogStore).append(tenantCaptor.capture(), entryCaptor.capture());

        TenantContext capturedTenant = tenantCaptor.getValue();
        assertThat(capturedTenant.tenantId()).isEqualTo("tenant-1");

        EventEntry capturedEntry = entryCaptor.getValue();
        assertThat(capturedEntry.eventType()).isEqualTo("order.created");
        assertThat(capturedEntry.eventId()).isNotNull();
    }

    @Test
    void shouldReturnEventIdOnAppend() {
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class)))
            .thenReturn(Promise.of(Offset.of("42")));

        // WHEN
        String eventId = eventCloud.append("t1", "test.event", new byte[]{1, 2, 3});

        // THEN
        assertThat(eventId).isNotBlank();
        // UUID format
        assertThat(eventId).matches(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void shouldCreateSubscriptionWithTenantIsolation() {
        // GIVEN
        when(eventLogStore.getLatestOffset(any(TenantContext.class)))
            .thenReturn(Promise.of(Offset.zero()));
        when(eventLogStore.tail(any(TenantContext.class), any(Offset.class), any()))
            .thenReturn(Promise.of(new EventLogStore.Subscription() {
                @Override public void cancel() {}
                @Override public boolean isCancelled() { return false; }
            }));

        // WHEN
        EventCloud.Subscription sub = eventCloud.subscribe(
            "tenant-1", "order.created",
            (eventId, eventType, payload) -> {});

        // THEN
        assertThat(sub).isNotNull();
        assertThat(sub.isCancelled()).isFalse();

        // Verify tenant isolation
        verify(eventLogStore).getLatestOffset(tenantCaptor.capture());
        assertThat(tenantCaptor.getValue().tenantId()).isEqualTo("tenant-1");
    }

    @Test
    void shouldCancelSubscription() {
        // GIVEN
        when(eventLogStore.getLatestOffset(any(TenantContext.class)))
            .thenReturn(Promise.of(Offset.zero()));
        when(eventLogStore.tail(any(TenantContext.class), any(Offset.class), any()))
            .thenReturn(Promise.of(new EventLogStore.Subscription() {
                private boolean cancelled = false;
                @Override public void cancel() { cancelled = true; }
                @Override public boolean isCancelled() { return cancelled; }
            }));

        EventCloud.Subscription sub = eventCloud.subscribe(
            "tenant-1", "test.event",
            (eventId, eventType, payload) -> {});

        // WHEN
        sub.cancel();

        // THEN
        assertThat(sub.isCancelled()).isTrue();
    }
}
