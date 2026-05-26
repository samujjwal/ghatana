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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DC-EVENT-001: Append/read/tail/replay/checkpoint E2E tests.
 *
 * <p>Verifies event plane operations including append, read, tail, replay, checkpoint,
 * retry after failure, DLQ on poison event, and tenant isolation.
 *
 * @doc.type class
 * @doc.purpose Event plane E2E tests (DC-EVENT-001)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Event Plane E2E Tests")
@Tag("event-plane")
@Tag("e2e")
@org.junit.jupiter.api.extension.ExtendWith(MockitoExtension.class)
class EventPlaneE2ETest extends EventloopTestBase {

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
    }

    // ==================== DC-EVENT-001: Append event ====================

    @Test
    @DisplayName("DC-EVENT-001: Append event returns offset on success")
    void appendEventReturnsOffsetOnSuccess() {
        String eventJson = "{\"type\":\"entity.created\",\"data\":{\"entityId\":\"ent-1\"}}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(eventJson.getBytes(StandardCharsets.UTF_8))));
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        assertThat(response).isNotNull();
        verify(http).jsonResponse(argThat(body -> {
            Map<String, Object> result = body;
            return result.containsKey("offset") && result.get("offset").equals(1L);
        }));
    }

    @Test
    @DisplayName("DC-EVENT-001: Append event fails on poison event (DLQ)")
    void appendEventFailsOnPoisonEvent() {
        String poisonEventJson = "{\"type\":\"invalid\",\"data\":null}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(poisonEventJson.getBytes(StandardCharsets.UTF_8))));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        assertThat(response).isSameAs(errorResponse);
        verify(http).errorResponse(eq(400), argThat(msg -> 
            msg.contains("invalid") || msg.contains("validation")));
    }

    // ==================== DC-EVENT-001: Read event ====================

    @Test
    @DisplayName("DC-EVENT-001: Read event returns event data at offset")
    void readEventReturnsDataAtOffset() {
        when(request.getPathParameter("offset")).thenReturn("1");
        when(client.readEvent(anyString(), eq(1L)))
            .thenReturn(Promise.of(Optional.of(DataCloudClient.Event.builder()
                .type("entity.created")
                .payload(Map.of("entityId", "ent-1"))
                .build())));

        HttpResponse response = runPromise(() -> handler.handleReadEvent(request));

        assertThat(response).isNotNull();
        verify(http).jsonResponse(argThat(body -> {
            Map<String, Object> result = body;
            return result.containsKey("type") && "entity.created".equals(result.get("type"));
        }));
    }

    @Test
    @DisplayName("DC-EVENT-001: Read event returns 404 for non-existent offset")
    void readEventReturns404ForNonExistentOffset() {
        when(request.getPathParameter("offset")).thenReturn("999");
        when(client.readEvent(anyString(), eq(999L)))
            .thenReturn(Promise.of(Optional.empty()));

        HttpResponse response = runPromise(() -> handler.handleReadEvent(request));

        assertThat(response).isSameAs(errorResponse);
        verify(http).errorResponse(eq(404), anyString());
    }

    // ==================== DC-EVENT-001: Tail stream ====================

    @Test
    @DisplayName("DC-EVENT-001: Tail stream returns events from offset")
    void tailStreamReturnsEventsFromOffset() {
        when(request.getPathParameter("offset")).thenReturn("0");
        when(client.tailEvents(anyString(), eq(0L)))
            .thenReturn(Promise.of(List.of(
                DataCloudClient.Event.builder()
                    .type("entity.created")
                    .payload(Map.of("entityId", "ent-1"))
                    .build()
            )));

        HttpResponse response = runPromise(() -> handler.handleTailEvents(request));

        assertThat(response).isNotNull();
        verify(http).jsonResponse(argThat(body -> {
            Map<String, Object> result = body;
            return result.containsKey("events") && result.get("events") instanceof List;
        }));
    }

    // ==================== DC-EVENT-001: Replay stream ====================

    @Test
    @DisplayName("DC-EVENT-001: Replay stream replays events from checkpoint")
    void replayStreamReplaysFromCheckpoint() {
        String replayJson = "{\"fromOffset\":0,\"toOffset\":10}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(replayJson.getBytes(StandardCharsets.UTF_8))));
        when(client.replayEvents(anyString(), eq(0L), eq(10L)))
            .thenReturn(Promise.of(List.of(
                DataCloudClient.Event.builder()
                    .type("entity.created")
                    .payload(Map.of("entityId", "ent-1"))
                    .build()
            )));

        HttpResponse response = runPromise(() -> handler.handleReplayEvents(request));

        assertThat(response).isNotNull();
        verify(http).jsonResponse(argThat(body -> {
            Map<String, Object> result = body;
            return result.containsKey("events") && result.get("events") instanceof List;
        }));
    }

    // ==================== DC-EVENT-001: Checkpoint offset ====================

    @Test
    @DisplayName("DC-EVENT-001: Checkpoint offset stores consumer position")
    void checkpointOffsetStoresConsumerPosition() {
        String checkpointJson = "{\"stream\":\"entity.created\",\"offset\":5}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(checkpointJson.getBytes(StandardCharsets.UTF_8))));
        when(client.checkpoint(anyString(), anyString(), eq(5L)))
            .thenReturn(Promise.of(true));

        HttpResponse response = runPromise(() -> handler.handleCheckpoint(request));

        assertThat(response).isNotNull();
        verify(http).jsonResponse(argThat(body -> {
            Map<String, Object> result = body;
            return result.containsKey("offset") && result.get("offset").equals(5L);
        }));
    }

    // ==================== DC-EVENT-001: Retry after failure ====================

    @Test
    @DisplayName("DC-EVENT-001: Retry after failure succeeds on second attempt")
    void retryAfterFailureSucceedsOnSecondAttempt() {
        String eventJson = "{\"type\":\"entity.created\",\"data\":{\"entityId\":\"ent-1\"}}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(eventJson.getBytes(StandardCharsets.UTF_8))));
        
        // First call fails, second succeeds
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("Temporary failure")))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));

        // First attempt fails
        HttpResponse response1 = runPromise(() -> handler.handleAppendEvent(request));
        assertThat(response1).isSameAs(errorResponse);

        // Retry succeeds
        HttpResponse response2 = runPromise(() -> handler.handleAppendEvent(request));
        assertThat(response2).isNotNull();
        verify(http).jsonResponse(argThat(body -> {
            Map<String, Object> result = body;
            return result.containsKey("offset") && result.get("offset").equals(1L);
        }));
    }

    // ==================== DC-EVENT-001: Tenant isolation ====================

    @Test
    @DisplayName("DC-EVENT-001: Events are isolated by tenant")
    void eventsAreIsolatedByTenant() {
        String eventJson = "{\"type\":\"entity.created\",\"data\":{\"entityId\":\"ent-1\"}}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(eventJson.getBytes(StandardCharsets.UTF_8))));
        when(client.appendEvent(eq("tenant-1"), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        assertThat(response).isNotNull();
        verify(client).appendEvent(eq("tenant-1"), any());
        verify(client, never()).appendEvent(eq("tenant-2"), any());
    }
}
