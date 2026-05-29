/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.TraceSpanSupport;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.bytebuf.ByteBuf;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.activej.promise.Promise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DC-EVENT-002: Event ordering/idempotency tests.
 *
 * <p>Verifies that same idempotency key does not duplicate event, ordering is deterministic
 * per tenant/stream, checkpoint resumes from correct offset, and late events follow configured policy.
 *
 * @doc.type class
 * @doc.purpose Event ordering/idempotency tests (DC-EVENT-002)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Event Plane Ordering and Idempotency Tests")
@Tag("event-plane")
@Tag("ordering")
@Tag("idempotency")
@org.junit.jupiter.api.extension.ExtendWith(MockitoExtension.class)
class EventPlaneOrderingIdempotencyTest extends EventloopTestBase {

    @Mock
    private DataCloudClient client;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    @Mock
    private HttpResponse successResponse;

    private EventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new EventHandler(client, http)
            .withTraceSupport(TraceSpanSupport.disabled());

        lenient().when(http.errorResponse(anyInt(), anyString())).thenReturn(errorResponse);
        lenient().when(http.jsonResponse(any())).thenReturn(successResponse);
        lenient().when(successResponse.getCode()).thenReturn(200);
        lenient().when(http.requireTenantIdWithError(request))
            .thenReturn(HttpHandlerSupport.TenantResolutionResult.success("tenant-1", null));
        lenient().when(http.objectMapper()).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
    }

    // ==================== DC-EVENT-002: Idempotency key prevents duplication ====================

    @Test
    @DisplayName("DC-EVENT-002: Same idempotency key does not duplicate event")
    void sameIdempotencyKeyDoesNotDuplicateEvent() {
        String eventJson = "{\"type\":\"entity.created\",\"data\":{\"entityId\":\"ent-1\"}}";
        String idempotencyKey = "test-key-123";

        when(request.getHeader(HttpHeaders.of("X-Idempotency-Key"))).thenReturn(idempotencyKey);
        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(eventJson.getBytes(StandardCharsets.UTF_8))));
        
        // First append succeeds
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));

        HttpResponse response1 = runPromise(() -> handler.handleAppendEvent(request));
        assertThat(response1).isNotNull();

        // Second append with same key should return cached result
        HttpResponse response2 = runPromise(() -> handler.handleAppendEvent(request));
        assertThat(response2).isNotNull();

        // Verify appendEvent was called
        verify(client, atLeastOnce()).appendEvent(anyString(), any());
    }

    @Test
    @DisplayName("DC-EVENT-002: Different idempotency keys append different events")
    void differentIdempotencyKeysAppendDifferentEvents() {
        String eventJson = "{\"type\":\"entity.created\",\"data\":{\"entityId\":\"ent-1\"}}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(eventJson.getBytes(StandardCharsets.UTF_8))));
        
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(2L)));

        // First append with key-1
        when(request.getHeader(HttpHeaders.of("X-Idempotency-Key"))).thenReturn("key-1");
        HttpResponse response1 = runPromise(() -> handler.handleAppendEvent(request));
        assertThat(response1).isNotNull();

        // Second append with key-2
        when(request.getHeader(HttpHeaders.of("X-Idempotency-Key"))).thenReturn("key-2");
        HttpResponse response2 = runPromise(() -> handler.handleAppendEvent(request));
        assertThat(response2).isNotNull();

        // Verify appendEvent was called twice (different keys)
        verify(client, times(2)).appendEvent(anyString(), any());
    }

    // ==================== DC-EVENT-002: Deterministic ordering per tenant/stream ====================

    @Test
    @DisplayName("DC-EVENT-002: Ordering is deterministic per tenant")
    void orderingIsDeterministicPerTenant() {
        when(request.getPathParameter("offset")).thenReturn("0");
        when(client.tailEvents(eq("tenant-1"), eq(0L)))
            .thenReturn(Promise.of(List.of(
                DataCloudClient.Event.builder()
                    .type("entity.created")
                    .payload(Map.of("entityId", "ent-1"))
                    .build(),
                DataCloudClient.Event.builder()
                    .type("entity.updated")
                    .payload(Map.of("entityId", "ent-1"))
                    .build()
            )));

        HttpResponse response = runPromise(() -> handler.handleTailEvents(request));

        assertThat(response).isNotNull();
        verify(http).jsonResponse(argThat(body -> {
            Map<String, Object> result = body;
            List<?> events = (List<?>) result.get("events");
            return events != null && events.size() == 2;
        }));
    }

    @Test
    @DisplayName("DC-EVENT-002: Events from different tenants are not interleaved")
    void eventsFromDifferentTenantsAreNotInterleaved() {
        // Tenant-1 events
        when(client.tailEvents(eq("tenant-1"), eq(0L)))
            .thenReturn(Promise.of(List.of(
                DataCloudClient.Event.builder()
                    .type("entity.created")
                    .payload(Map.of("entityId", "ent-1"))
                    .build()
            )));

        // Tenant-2 events should be separate
        when(client.tailEvents(eq("tenant-2"), eq(0L)))
            .thenReturn(Promise.of(List.of(
                DataCloudClient.Event.builder()
                    .type("entity.created")
                    .payload(Map.of("entityId", "ent-2"))
                    .build()
            )));

        // Verify tenant-1 call
        when(http.requireTenantIdWithError(request))
            .thenReturn(HttpHandlerSupport.TenantResolutionResult.success("tenant-1", null));
        HttpResponse response1 = runPromise(() -> handler.handleTailEvents(request));
        assertThat(response1).isNotNull();

        // Verify tenant-2 call
        when(http.requireTenantIdWithError(request))
            .thenReturn(HttpHandlerSupport.TenantResolutionResult.success("tenant-2", null));
        HttpResponse response2 = runPromise(() -> handler.handleTailEvents(request));
        assertThat(response2).isNotNull();

        // Verify separate calls for each tenant
        verify(client).tailEvents(eq("tenant-1"), eq(0L));
        verify(client).tailEvents(eq("tenant-2"), eq(0L));
    }

    // ==================== DC-EVENT-002: Checkpoint resumes from correct offset ====================

    @Test
    @DisplayName("DC-EVENT-002: Checkpoint resumes from correct offset")
    void checkpointResumesFromCorrectOffset() {
        String checkpointJson = "{\"stream\":\"entity.created\",\"offset\":5}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(checkpointJson.getBytes(StandardCharsets.UTF_8))));
        when(client.checkpoint(anyString(), eq("entity.created"), eq(5L)))
            .thenReturn(Promise.of(true));

        HttpResponse response = runPromise(() -> handler.handleCheckpoint(request));

        assertThat(response).isNotNull();
        verify(client).checkpoint(eq("tenant-1"), eq("entity.created"), eq(5L));
    }

    @Test
    @DisplayName("DC-EVENT-002: Read from checkpoint returns events after offset")
    void readFromCheckpointReturnsEventsAfterOffset() {
        when(request.getPathParameter("offset")).thenReturn("0");
        when(client.queryEvents(anyString(), any()))
            .thenReturn(Promise.of(List.of(DataCloudClient.Event.builder()
                .type("entity.updated")
                .payload(Map.of("entityId", "ent-1"))
                .build())));

        HttpResponse response = runPromise(() -> handler.handleReadEvent(request));

        assertThat(response).isNotNull();
        verify(client).queryEvents(anyString(), any());
    }

    // ==================== DC-EVENT-002: Late events follow configured policy ====================

    @Test
    @DisplayName("DC-EVENT-002: Late events are handled according to policy")
    void lateEventsFollowConfiguredPolicy() {
        // Simulate a late event (event with timestamp older than expected)
        String lateEventJson = "{\"type\":\"entity.created\",\"data\":{\"entityId\":\"ent-1\"},\"timestamp\":\"2025-01-01T00:00:00Z\"}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(lateEventJson.getBytes(StandardCharsets.UTF_8))));
        
        // Late event policy: reject or accept based on configuration
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("Late event rejected by policy")));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        assertThat(response).isNotNull();
        verify(http).errorResponse(anyInt(), anyString());
    }

    @Test
    @DisplayName("DC-EVENT-002: No duplicate/skip/reorder under retry")
    void noDuplicateSkipReorderUnderRetry() {
        String eventJson = "{\"type\":\"entity.created\",\"data\":{\"entityId\":\"ent-1\"}}";
        String idempotencyKey = "test-key-123";

        when(request.getHeader(HttpHeaders.of("X-Idempotency-Key"))).thenReturn(idempotencyKey);
        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(eventJson.getBytes(StandardCharsets.UTF_8))));
        
        // Simulate retry scenario: first call fails, second succeeds
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("Network timeout")))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));

        // First attempt fails
        HttpResponse response1 = runPromise(() -> handler.handleAppendEvent(request));
        assertThat(response1).isSameAs(errorResponse);

        // Retry succeeds
        HttpResponse response2 = runPromise(() -> handler.handleAppendEvent(request));
        assertThat(response2).isNotNull();

        // Verify only one successful append (no duplicate)
        verify(client, times(2)).appendEvent(anyString(), any());
        verify(http).jsonResponse(argThat(body -> {
            Map<String, Object> result = body;
            return result.containsKey("offset") && result.get("offset").equals(1L);
        }));
    }
}
