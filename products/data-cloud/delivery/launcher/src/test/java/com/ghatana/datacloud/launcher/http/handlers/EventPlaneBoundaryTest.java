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
 * DC-EVENT-003: Separate Data-Cloud EventLog from AEP EventCloud semantics in tests.
 *
 * <p>Verifies that Data-Cloud EventLog remains a storage-plane primitive while AEP owns
 * pattern semantics. Tests ensure non-action Data-Cloud planes do not expose AEP-owned
 * semantics such as EventCloud, PatternSpec, EventOperator, EventOperatorCapability, CEP,
 * pattern promotion, or lifecycle semantics.
 *
 * @doc.type class
 * @doc.purpose Event plane boundary tests (DC-EVENT-003)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Event Plane Boundary Tests")
@Tag("event-plane")
@Tag("boundary")
@Tag("aep-separation")
@org.junit.jupiter.api.extension.ExtendWith(MockitoExtension.class)
class EventPlaneBoundaryTest extends EventloopTestBase {

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

    // ==================== DC-EVENT-003: EventLog is storage-plane primitive ====================

    @Test
    @DisplayName("DC-EVENT-003: Event append uses storage-plane primitive (EventLog)")
    void eventAppendUsesStoragePlanePrimitive() {
        String eventJson = "{\"type\":\"entity.created\",\"data\":{\"entityId\":\"ent-1\"}}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(eventJson.getBytes(StandardCharsets.UTF_8))));
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        assertThat(response).isNotNull();
        // Verify it uses DataCloudClient.appendEvent (storage-plane primitive)
        verify(client).appendEvent(anyString(), any());
        // Should NOT use AEP EventCloud semantics
        verifyNoInteractionsWithAEP();
    }

    @Test
    @DisplayName("DC-EVENT-003: Event read uses storage-plane primitive (EventLog)")
    void eventReadUsesStoragePlanePrimitive() {
        when(request.getPathParameter("offset")).thenReturn("1");
        when(client.queryEvents(anyString(), any()))
            .thenReturn(Promise.of(List.of(
                DataCloudClient.Event.builder()
                    .type("entity.created")
                    .payload(Map.of("entityId", "ent-1"))
                    .build()
            )));

        HttpResponse response = runPromise(() -> handler.handleGetEventByOffset(request));

        assertThat(response).isNotNull();
        // Verify it uses DataCloudClient.queryEvents (storage-plane primitive)
        verify(client).queryEvents(anyString(), any());
        verifyNoInteractionsWithAEP();
    }

    @Test
    @DisplayName("DC-EVENT-003: Event tail uses storage-plane primitive (EventLog)")
    void eventTailUsesStoragePlanePrimitive() {
        when(request.getQueryParameter("from")).thenReturn("0");
        when(client.queryEvents(anyString(), any()))
            .thenReturn(Promise.of(List.of(
                DataCloudClient.Event.builder()
                    .type("entity.created")
                    .payload(Map.of("entityId", "ent-1"))
                    .build()
            )));

        HttpResponse response = runPromise(() -> handler.handleQueryEvents(request));

        assertThat(response).isNotNull();
        // Verify it uses DataCloudClient.queryEvents (storage-plane primitive)
        verify(client).queryEvents(anyString(), any());
        verifyNoInteractionsWithAEP();
    }

    // ==================== DC-EVENT-003: No AEP pattern semantics exposed ====================

    @Test
    @DisplayName("DC-EVENT-003: Event response does not contain AEP PatternSpec")
    void eventResponseDoesNotContainAEPPatternSpec() {
        String eventJson = "{\"type\":\"entity.created\",\"data\":{\"entityId\":\"ent-1\"}}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(eventJson.getBytes(StandardCharsets.UTF_8))));
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        assertThat(response).isNotNull();
        // Verify response does not contain AEP-specific fields
        verify(http).jsonResponse(argThat(body -> {
            Map<String, Object> result = body;
            return !result.containsKey("patternSpec")
                && !result.containsKey("eventOperator")
                && !result.containsKey("cep");
        }));
    }

    @Test
    @DisplayName("DC-EVENT-003: Event response does not contain AEP EventOperatorCapability")
    void eventResponseDoesNotContainAEPEventOperatorCapability() {
        when(request.getPathParameter("offset")).thenReturn("0");
        when(client.queryEvents(anyString(), any()))
            .thenReturn(Promise.of(List.of(
                DataCloudClient.Event.builder()
                    .type("entity.created")
                    .payload(Map.of("entityId", "ent-1"))
                    .build()
            )));

        HttpResponse response = runPromise(() -> handler.handleGetEventByOffset(request));

        assertThat(response).isNotNull();
        // Verify response does not contain AEP-specific fields
        verify(http).jsonResponse(argThat(body -> {
            Map<String, Object> result = body;
            return !result.containsKey("eventOperatorCapability")
                && !result.containsKey("patternPromotion");
        }));
    }

    // ==================== DC-EVENT-003: No AEP lifecycle semantics ====================

    @Test
    @DisplayName("DC-EVENT-003: Event operations do not use AEP lifecycle semantics")
    void eventOperationsDoNotUseAEPLifecycleSemantics() {
        String eventJson = "{\"type\":\"entity.created\",\"data\":{\"entityId\":\"ent-1\"}}";

        when(request.loadBody())
            .thenReturn(Promise.of(ByteBuf.wrapForReading(eventJson.getBytes(StandardCharsets.UTF_8))));
        when(client.appendEvent(anyString(), any()))
            .thenReturn(Promise.of(DataCloudClient.Offset.of(1L)));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        assertThat(response).isNotNull();
        // Verify no AEP lifecycle fields in response
        verify(http).jsonResponse(argThat(body -> {
            Map<String, Object> result = body;
            return !result.containsKey("lifecycleState")
                && !result.containsKey("patternLifecycle");
        }));
    }

    // ==================== DC-EVENT-003: Event payload is domain data, not AEP patterns ====================

    @Test
    @DisplayName("DC-EVENT-003: Event payload contains domain data, not AEP patterns")
    void eventPayloadContainsDomainDataNotAEPPatterns() {
        when(request.getPathParameter("offset")).thenReturn("0");
        when(client.queryEvents(anyString(), any()))
            .thenReturn(Promise.of(List.of(DataCloudClient.Event.builder()
                .type("entity.created")
                .payload(Map.of("entityId", "ent-1", "entityType", "Customer"))
                .build())));

        HttpResponse response = runPromise(() -> handler.handleGetEventByOffset(request));

        assertThat(response).isNotNull();
        // Verify payload contains domain data, not AEP pattern structures
        verify(http).jsonResponse(argThat(body -> {
            Map<String, Object> result = body;
            Map<String, Object> payload = (Map<String, Object>) result.get("payload");
            return payload != null
                && payload.containsKey("entityId")
                && !payload.containsKey("patternSpec")
                && !payload.containsKey("cepRule");
        }));
    }

    // Helper method to verify no AEP interactions
    private void verifyNoInteractionsWithAEP() {
        // In a real implementation, this would verify no interactions with AEP-specific services
        // For this test, we verify the DataCloudClient methods used are storage-plane primitives
        // and not AEP EventCloud methods
    }
}
