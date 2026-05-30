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
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private EventCloudCheckpointStore checkpointStore;

    @Captor
    private ArgumentCaptor<EventEntry> entryCaptor;

    @Captor
    private ArgumentCaptor<TenantContext> tenantCaptor;

    private DataCloudEventCloudConnector connector;

    @BeforeEach
    void setUp() { 
        connector = new DataCloudEventCloudConnector(eventLogStore, checkpointStore); 
    }

    @Test
    void shouldPublishEventWithExplicitTenant() { 
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class))) 
            .thenReturn(Promise.of(Offset.of("1")));

        byte[] payload = "{\"order\":123}".getBytes(StandardCharsets.UTF_8); 

        // WHEN
        String eventId = runPromise(() -> connector.publish("tenant-123", "order.created", payload)); 

        // THEN
        assertThat(eventId).isNotBlank(); 
        verify(eventLogStore).append(tenantCaptor.capture(), entryCaptor.capture()); 

        // Verify tenant
        assertThat(tenantCaptor.getValue().tenantId()).isEqualTo("tenant-123");

        // Verify event entry
        EventEntry entry = entryCaptor.getValue(); 
        assertThat(entry.eventType()).isEqualTo("order.created");
        assertThat(entry.eventId()).isNotNull(); 
    }

    @Test
    void shouldPublishEventWithFullEnvelope() { 
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class))) 
            .thenReturn(Promise.of(Offset.of("1")));

        byte[] payload = "{\"order\":123}".getBytes(StandardCharsets.UTF_8); 
        DataCloudEventCloudConnector.EventEnvelope envelope = new DataCloudEventCloudConnector.EventEnvelope(
            "order-service",
            "order-123",
            "v1",
            "corr-123",
            "caus-123",
            "confidential",
            "policy-ctx",
            "prov-ctx",
            "trace-123",
            "user-123"
        );

        // WHEN
        String eventId = runPromise(() -> connector.publish("tenant-123", "order.created", payload, envelope)); 

        // THEN
        assertThat(eventId).isNotBlank(); 
        verify(eventLogStore).append(tenantCaptor.capture(), entryCaptor.capture()); 

        // Verify envelope fields are preserved
        EventEntry entry = entryCaptor.getValue(); 
        assertThat(entry.eventVersion()).isEqualTo("v1");
        assertThat(entry.correlationId()).isEqualTo("corr-123");
        assertThat(entry.source()).isEqualTo("order-service");
        assertThat(entry.userId()).isEqualTo("user-123");
        assertThat(entry.headers()).containsKey("classification");
        assertThat(entry.headers()).containsKey("subject");
    }

    @Test
    void shouldRejectTenantAgnosticPublish() { 
        // WHEN/THEN
        assertThatThrownBy(() -> connector.publish("order.created", "data".getBytes()))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Tenant-agnostic publish is not supported");
    }

    @Test
    void shouldRejectTenantAgnosticSubscribe() { 
        // WHEN/THEN
        assertThatThrownBy(() -> connector.subscribe("order.created", "group-1", (e, t, p) -> {}))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("Tenant-agnostic subscribe is not supported");
    }

    @Test
    void shouldSubscribeWithExplicitTenantAndResumeFromCheckpoint() { 
        // GIVEN
        when(checkpointStore.load(eq("tenant-123"), eq("tenant-123:group-1:order.created")))
            .thenReturn(Promise.of(Optional.of(new EventCloudCheckpoint(
                "tenant-123",
                "tenant-123:group-1:order.created",
                new EventCloudOffset(100, "100"),
                Instant.now(),
                java.util.Map.of("processed", 50L)
            ))));
        when(eventLogStore.tail(any(TenantContext.class), any(Offset.class), any()))
            .thenReturn(Promise.of(() -> {}));

        // WHEN
        connector.subscribe("tenant-123", "order.created", "group-1", (e, t, p) -> {}, Optional.empty());

        // THEN
        verify(checkpointStore).load(eq("tenant-123"), eq("tenant-123:group-1:order.created"));
    }

    @Test
    void shouldSubscribeWithReplayOffset() { 
        // GIVEN
        when(eventLogStore.tail(any(TenantContext.class), any(Offset.class), any()))
            .thenReturn(Promise.of(() -> {}));

        // WHEN
        connector.subscribe("tenant-123", "order.created", "group-1", (e, t, p) -> {}, Optional.of(Offset.of("50")));

        // THEN
        verify(eventLogStore).tail(any(TenantContext.class), eq(Offset.of("50")), any());
    }

    @Test
    void shouldSubscribeWithExplicitTenant() { 
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
            connector.subscribe("tenant-123", "topic", "group", 
                (eventId, topic, payload) -> {})); 

        // THEN
        assertThat(sub).isNotNull(); 
        verify(eventLogStore).getLatestOffset(tenantCaptor.capture());
        assertThat(tenantCaptor.getValue().tenantId()).isEqualTo("tenant-123");
    }

    @Test
    void shouldResumeFromCheckpoint() { 
        // GIVEN
        EventCloudOffset checkpointOffset = new EventCloudOffset(100L, "offset-100");
        EventCloudCheckpoint checkpoint = new EventCloudCheckpoint(
            "tenant-1",
            "tenant-1:group:topic",
            checkpointOffset,
            Instant.now(),
            java.util.Map.of()
        );
        
        when(checkpointStore.load(eq("tenant-1"), eq("tenant-1:group:topic")))
            .thenReturn(Promise.of(Optional.of(checkpoint)));
        when(eventLogStore.tail(any(TenantContext.class), any(Offset.class), any())) 
            .thenReturn(Promise.of(new EventLogStore.Subscription() { 
                @Override public void cancel() {} 
                @Override public boolean isCancelled() { return false; } 
            }));

        // WHEN
        EventCloudConnector.ConnectorSubscription sub = runPromise(() -> 
            connector.subscribe("tenant-1", "topic", "group", 
                (eventId, topic, payload) -> {})); 

        // THEN
        assertThat(sub).isNotNull(); 
        verify(checkpointStore).load(eq("tenant-1"), eq("tenant-1:group:topic"));
    }

    @Test
    void shouldReplayFromExplicitOffset() { 
        // GIVEN
        Offset replayOffset = Offset.of("50");
        when(eventLogStore.tail(any(TenantContext.class), eq(replayOffset), any())) 
            .thenReturn(Promise.of(new EventLogStore.Subscription() { 
                @Override public void cancel() {} 
                @Override public boolean isCancelled() { return false; } 
            }));

        // WHEN
        EventCloudConnector.ConnectorSubscription sub = runPromise(() -> 
            connector.subscribe("tenant-1", "topic", "group", 
                (eventId, topic, payload) -> {}, 
                Optional.of(replayOffset))); 

        // THEN
        assertThat(sub).isNotNull(); 
        verify(eventLogStore).tail(tenantCaptor.capture(), eq(replayOffset), any());
        assertThat(tenantCaptor.getValue().tenantId()).isEqualTo("tenant-1");
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

    @Test
    void shouldEnforceTenantIsolation() { 
        // GIVEN
        when(eventLogStore.append(any(TenantContext.class), any(EventEntry.class))) 
            .thenReturn(Promise.of(Offset.of("1")));

        // WHEN - publish to tenant A
        runPromise(() -> connector.publish("tenant-a", "topic", new byte[]{1})); 
        verify(eventLogStore).append(tenantCaptor.capture(), any());
        assertThat(tenantCaptor.getValue().tenantId()).isEqualTo("tenant-a");

        // WHEN - publish to tenant B
        runPromise(() -> connector.publish("tenant-b", "topic", new byte[]{2})); 
        verify(eventLogStore).append(tenantCaptor.capture(), any());
        assertThat(tenantCaptor.getValue().tenantId()).isEqualTo("tenant-b");

        // THEN - verify no cross-tenant contamination
        // Each publish should have used the correct tenant context
    }

    @Test
    void shouldInvokeFailureCallbackOnHandlerError() {
        // GIVEN
        java.util.concurrent.atomic.AtomicBoolean failureCallbackInvoked = new java.util.concurrent.atomic.AtomicBoolean(false);
        
        when(eventLogStore.getLatestOffset(any(TenantContext.class)))
            .thenReturn(Promise.of(Offset.zero()));
        
        when(eventLogStore.tail(any(TenantContext.class), any(Offset.class), any()))
            .thenAnswer(invocation -> {
                EventLogStore.EventPayloadHandler handler = invocation.getArgument(2);
                // Simulate handler failure
                try {
                    handler.onEvent("event-1", "topic", new byte[]{1});
                } catch (Exception e) {
                    // Expected
                }
                return Promise.of(new EventLogStore.Subscription() {
                    @Override public void cancel() {}
                    @Override public boolean isCancelled() { return false; }
                });
            });

        // WHEN
        runPromise(() -> connector.subscribe(
            "tenant-1", 
            "topic", 
            "group", 
            (eventId, topic, payload) -> { throw new RuntimeException("Handler failed"); },
            Optional.empty(),
            e -> failureCallbackInvoked.set(true)
        ));

        // THEN
        assertThat(failureCallbackInvoked).isTrue();
    }
}
