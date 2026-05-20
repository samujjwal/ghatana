/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.launcher.http.TraceSpanSupport;
import com.ghatana.datacloud.spi.WriteIdempotencyStore;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DC-P1-06: Golden tests for event append durability.
 *
 * <p>Verifies that event append operations support idempotency, handle partial
 * failures, and enforce production requirements for durable idempotency stores.
 *
 * @doc.type class
 * @doc.purpose Golden tests for event append durability (DC-P1-06)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("EventHandler Durability Golden Tests")
@Tag("durability")
@Tag("production")
@ExtendWith(MockitoExtension.class)
class EventHandlerDurabilityTest extends EventloopTestBase {

    @Mock private DataCloudClient client;
    @Mock private HttpHandlerSupport http;
    @Mock private HttpRequest request;
    @Mock private HttpResponse errorResponse;
    @Mock private HttpResponse successResponse;
    @Mock private WriteIdempotencyStore idempotencyStore;

    private EventHandler handler;

    /** A minimal valid event JSON: has type, payload (as data), and actor. */
    private static final String VALID_EVENT_JSON =
        "{\"type\":\"entity.created\",\"data\":{\"key\":\"value\"},\"actor\":\"user-1\"}";

    @BeforeEach
    void setUp() {
        handler = new EventHandler(client, http)
            .withTraceSupport(TraceSpanSupport.disabled())
            .withIdempotencyStore(idempotencyStore)
            .withDeploymentProfile("local");

        lenient().when(http.errorResponse(anyInt(), anyString())).thenReturn(errorResponse);
        lenient().when(http.jsonResponse(any())).thenReturn(successResponse);
        lenient().when(http.objectMapper()).thenReturn(new ObjectMapper());
        lenient().when(http.requireTenantIdWithError(request))
            .thenReturn(HttpHandlerSupport.TenantResolutionResult.success("tenant-1", null));
        lenient().when(request.getHeader(HttpHeaders.of("X-Idempotency-Key"))).thenReturn(null);
    }

    @Test
    @DisplayName("DC-P1-06: Idempotency key hit returns cached result without re-appending event")
    void idempotencyKeyHitReturnsCachedResultWithoutReAppendingEvent() {
        String idempotencyKey = "event-idempotency-key-123";
        when(request.getHeader(HttpHeaders.of("X-Idempotency-Key"))).thenReturn(idempotencyKey);
        when(idempotencyStore.get("tenant-1", "events:append", idempotencyKey))
            .thenReturn(Optional.of(Map.of("offset", 100L, "type", "entity.created")));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        assertThat(response).isNotNull();
        verify(client, never()).appendEvent(anyString(), any());
    }

    @Test
    @DisplayName("DC-P1-06: Idempotency key miss processes event and stores result")
    void idempotencyKeyMissProcessesEventAndStoresResult() {
        String idempotencyKey = "event-idempotency-key-456";
        when(request.getHeader(HttpHeaders.of("X-Idempotency-Key"))).thenReturn(idempotencyKey);
        when(idempotencyStore.get("tenant-1", "events:append", idempotencyKey))
            .thenReturn(Optional.empty());
        when(request.loadBody()).thenReturn(
            Promise.of(ByteBuf.wrapForReading(
                "{\"type\":\"entity.created\",\"payload\":{\"key\":\"v\"},\"actor\":\"user-1\"}".getBytes(StandardCharsets.UTF_8))));
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        assertThat(response).isNotNull();
        verify(client).appendEvent(eq("tenant-1"), any());
        verify(idempotencyStore).put(eq("tenant-1"), eq("events:append"), eq(idempotencyKey), any());
    }

    @Test
    @DisplayName("DC-P1-06: Successful event append without idempotency key calls client")
    void successfulEventAppendWithoutIdempotencyKeyCallsClient() {
        when(request.loadBody()).thenReturn(
            Promise.of(ByteBuf.wrapForReading(
                "{\"type\":\"entity.created\",\"payload\":{\"k\":\"v\"},\"actor\":\"user-1\"}".getBytes(StandardCharsets.UTF_8))));
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(2L)));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        assertThat(response).isNotNull();
        verify(client).appendEvent(eq("tenant-1"), any());
        verify(idempotencyStore, never()).put(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("DC-P1-06: Missing event type field returns 400")
    void missingEventTypeFieldReturns400() {
        when(request.loadBody()).thenReturn(
            Promise.of(ByteBuf.wrapForReading(
                "{\"data\":{\"key\":\"value\"},\"actor\":\"user-1\"}".getBytes(StandardCharsets.UTF_8))));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        assertThat(response).isSameAs(errorResponse);
        verify(http).errorResponse(eq(400), anyString());
        verify(client, never()).appendEvent(anyString(), any());
    }

    @Test
    @DisplayName("DC-P1-06: Missing payload field returns 400")
    void missingPayloadFieldReturns400() {
        when(request.loadBody()).thenReturn(
            Promise.of(ByteBuf.wrapForReading(
                "{\"type\":\"entity.created\",\"actor\":\"user-1\"}".getBytes(StandardCharsets.UTF_8))));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        assertThat(response).isSameAs(errorResponse);
        verify(http).errorResponse(eq(400), anyString());
        verify(client, never()).appendEvent(anyString(), any());
    }

    @Test
    @DisplayName("DC-P1-06: Client failure returns error response")
    void clientFailureReturnsErrorResponse() {
        when(request.loadBody()).thenReturn(
            Promise.of(ByteBuf.wrapForReading(
                "{\"type\":\"entity.created\",\"payload\":{\"k\":\"v\"},\"actor\":\"user-1\"}".getBytes(StandardCharsets.UTF_8))));
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.ofException(new RuntimeException("Event store unavailable")));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        assertThat(response).isSameAs(errorResponse);
        verify(idempotencyStore, never()).put(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("DC-P1-06: Production profile requires durable idempotency store")
    void productionProfileRequiresDurableIdempotencyStore() {
        EventHandler handlerWithoutStore = new EventHandler(client, http)
            .withTraceSupport(TraceSpanSupport.disabled())
            .withDeploymentProfile("production");

        assertThatThrownBy(handlerWithoutStore::validateProductionRequirements)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DC-P1-06")
            .hasMessageContaining("WriteIdempotencyStore is required");
    }

    @Test
    @DisplayName("DC-P1-06: Local profile allows missing idempotency store")
    void localProfileAllowsMissingIdempotencyStore() {
        EventHandler localHandler = new EventHandler(client, http)
            .withTraceSupport(TraceSpanSupport.disabled())
            .withDeploymentProfile("local");

        localHandler.validateProductionRequirements();
    }

    @Test
    @DisplayName("DC-P1-06: Production profile with idempotency store passes validation")
    void productionProfileWithIdempotencyStorePassesValidation() {
        EventHandler productionHandler = new EventHandler(client, http)
            .withTraceSupport(TraceSpanSupport.disabled())
            .withIdempotencyStore(idempotencyStore)
            .withDeploymentProfile("production");

        productionHandler.validateProductionRequirements();
    }

    @Test
    @DisplayName("DC-P1-06: Concurrent appends with same idempotency key — second returns cached")
    void concurrentAppendsWithSameIdempotencyKeySecondReturnsCached() {
        String idempotencyKey = "concurrent-event-key";
        byte[] body = "{\"type\":\"entity.created\",\"payload\":{\"k\":\"v\"},\"actor\":\"u\"}".getBytes(StandardCharsets.UTF_8);
        when(request.getHeader(HttpHeaders.of("X-Idempotency-Key"))).thenReturn(idempotencyKey);
        when(idempotencyStore.get("tenant-1", "events:append", idempotencyKey))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(Map.of("offset", 10L, "type", "entity.created")));
        when(request.loadBody()).thenReturn(Promise.of(ByteBuf.wrapForReading(body)));
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(10L)));

        HttpResponse response1 = runPromise(() -> handler.handleAppendEvent(request));
        assertThat(response1).isNotNull();

        HttpResponse response2 = runPromise(() -> handler.handleAppendEvent(request));
        assertThat(response2).isNotNull();

        verify(client, times(1)).appendEvent(anyString(), any());
    }
}
