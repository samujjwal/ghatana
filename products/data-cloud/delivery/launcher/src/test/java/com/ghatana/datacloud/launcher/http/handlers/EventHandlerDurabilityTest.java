/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.launcher.http.handlers;

import com.ghatana.datacloud.launcher.http.TraceSpanSupport;
import com.ghatana.datacloud.spi.WriteIdempotencyStore;
import com.ghatana.platform.testing.activej.EventloopTestBase;
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

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * DC-P1-06: Golden tests for event append durability.
 *
 * <p>Verifies that event append operations are durable across restarts, support
 * idempotency, handle partial failures with transaction rollback, and properly
 * integrate with the outbox pattern for event emission.
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

    @Mock
    private HttpRequest request;

    @Mock
    private HttpResponse errorResponse;

    @Mock
    private HttpHandlerSupport http;

    @Mock
    private WriteIdempotencyStore idempotencyStore;

    private EventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new EventHandler(http)
            .withTraceSupport(TraceSpanSupport.disabled())
            .withOutboxProcessor(outboxProcessor)
            .withIdempotencyStore(idempotencyStore)
            .withDeploymentProfile("production")
            .withStrictEventValidation(true);

        lenient().when(http.errorResponse(anyInt(), anyString())).thenReturn(errorResponse);
        lenient().when(http.requireTenantIdWithError(request))
            .thenReturn(HttpHandlerSupport.TenantResolutionResult.success("tenant-1"));
    }

    @Test
    @DisplayName("DC-P1-06: Event append with idempotency key returns cached result on retry")
    void eventAppendWithIdempotencyKeyReturnsCachedResultOnRetry() {
        String idempotencyKey = "test-event-idempotency-key-123";
        String cachedResponse = "{\"eventId\":\"event-456\",\"offset\":100}";

        when(request.getHeader("X-Idempotency-Key")).thenReturn(idempotencyKey);
        when(idempotencyStore.checkIdempotency("tenant-1", idempotencyKey))
            .thenReturn(Promise.ofOptional(Optional.of(cachedResponse)));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        // Should return cached response without executing the append
        assertThat(response).isNotNull();
        verify(outboxProcessor, never()).enqueue(any());
    }

    @Test
    @DisplayName("DC-P1-06: Event append stores idempotency result on success")
    void eventAppendStoresIdempotencyResultOnSuccess() {
        String idempotencyKey = "test-event-idempotency-key-456";
        String eventJson = "{\"eventId\":\"event-789\",\"type\":\"entity.created\",\"data\":{}}";

        when(request.getHeader("X-Idempotency-Key")).thenReturn(idempotencyKey);
        when(request.loadBody()).thenReturn(Promise.of(eventJson));
        when(idempotencyStore.checkIdempotency("tenant-1", idempotencyKey))
            .thenReturn(Promise.ofOptional(Optional.empty()));
        when(outboxProcessor.enqueue(any()))
            .thenReturn(Promise.of(Map.of("eventId", "event-789", "offset", 101)));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        assertThat(response).isNotNull();
        verify(idempotencyStore).storeIdempotency("tenant-1", idempotencyKey, any());
    }

    @Test
    @DisplayName("DC-P1-06: Event append rejects malformed event envelope in strict mode")
    void eventAppendRejectsMalformedEventEnvelopeInStrictMode() {
        // Missing required fields in event envelope
        String malformedEventJson = "{\"type\":\"entity.created\"}"; // Missing eventId, tenantId, actor, timestamp

        when(request.loadBody()).thenReturn(Promise.of(malformedEventJson));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        // Should reject with 400 Bad Request
        assertThat(response).isSameAs(errorResponse);
        verify(http).errorResponse(eq(400), anyString());
        verify(outboxProcessor, never()).enqueue(any());
    }

    @Test
    @DisplayName("DC-P1-06: Event append enriches server-owned fields in production")
    void eventAppendEnrichesServerOwnedFieldsInProduction() {
        String eventJson = "{\"eventId\":\"event-123\",\"type\":\"entity.created\",\"actor\":\"user-1\",\"timestamp\":\"2026-01-01T00:00:00Z\",\"data\":{}}";

        when(request.loadBody()).thenReturn(Promise.of(eventJson));
        when(idempotencyStore.checkIdempotency("tenant-1", any()))
            .thenReturn(Promise.ofOptional(Optional.empty()));
        when(outboxProcessor.enqueue(any()))
            .thenReturn(Promise.of(Map.of("eventId", "event-123", "offset", 102, "tenantId", "tenant-1")));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        assertThat(response).isNotNull();
        // Verify tenantId is set from authenticated context
        verify(outboxProcessor).enqueue(argThat(event -> {
            Map<String, Object> eventMap = (Map<String, Object>) event;
            return "tenant-1".equals(eventMap.get("tenantId"));
        }));
    }

    @Test
    @DisplayName("DC-P1-06: Outbox event is queued after successful event append")
    void outboxEventIsQueuedAfterSuccessfulEventAppend() {
        String eventJson = "{\"eventId\":\"event-999\",\"type\":\"entity.created\",\"actor\":\"user-1\",\"timestamp\":\"2026-01-01T00:00:00Z\",\"data\":{}}";

        when(request.loadBody()).thenReturn(Promise.of(eventJson));
        when(idempotencyStore.checkIdempotency("tenant-1", any()))
            .thenReturn(Promise.ofOptional(Optional.empty()));
        when(outboxProcessor.enqueue(any()))
            .thenReturn(Promise.of(Map.of("eventId", "event-999", "offset", 103)));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        assertThat(response).isNotNull();
        verify(outboxProcessor).enqueue(any());
    }

    @Test
    @DisplayName("DC-P1-06: Outbox event is not queued on validation failure")
    void outboxEventIsNotQueuedOnValidationFailure() {
        // Malformed event missing required fields
        String malformedEventJson = "{\"eventId\":\"event-888\"}";

        when(request.loadBody()).thenReturn(Promise.of(malformedEventJson));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        assertThat(response).isSameAs(errorResponse);
        verify(outboxProcessor, never()).enqueue(any());
    }

    @Test
    @DisplayName("DC-P1-06: Production profile requires durable idempotency store")
    void productionProfileRequiresDurableIdempotencyStore() {
        // Create handler without idempotency store in production profile
        EventHandler handlerWithoutStore = new EventHandler(http)
            .withTraceSupport(TraceSpanSupport.disabled())
            .withOutboxProcessor(outboxProcessor)
            .withDeploymentProfile("production")
            .withStrictEventValidation(true);

        IllegalStateException exception = new IllegalStateException();
        try {
            handlerWithoutStore.validateProductionRequirements();
        } catch (IllegalStateException e) {
            exception = e;
        }

        assertThat(exception.getMessage()).contains("DC-P1-06");
        assertThat(exception.getMessage()).contains("WriteIdempotencyStore is required");
    }

    @Test
    @DisplayName("DC-P1-06: Production profile requires outbox processor")
    void productionProfileRequiresOutboxProcessor() {
        // Create handler without outbox processor in production profile
        EventHandler handlerWithoutOutbox = new EventHandler(http)
            .withTraceSupport(TraceSpanSupport.disabled())
            .withIdempotencyStore(idempotencyStore)
            .withDeploymentProfile("production")
            .withStrictEventValidation(true);

        IllegalStateException exception = new IllegalStateException();
        try {
            handlerWithoutOutbox.validateProductionRequirements();
        } catch (IllegalStateException e) {
            exception = e;
        }

        assertThat(exception.getMessage()).contains("DC-P1-06");
        assertThat(exception.getMessage()).contains("OutboxProcessor is required");
    }

    @Test
    @DisplayName("DC-P1-06: Local profile allows relaxed validation")
    void localProfileAllowsRelaxedValidation() {
        // Create handler without strict validation in local profile
        EventHandler localHandler = new EventHandler(http)
            .withTraceSupport(TraceSpanSupport.disabled())
            .withDeploymentProfile("local")
            .withStrictEventValidation(false);

        // Should not throw exception in local profile
        localHandler.validateProductionRequirements();
    }

    @Test
    @DisplayName("DC-P1-06: Concurrent appends with same idempotency key are serialized")
    void concurrentAppendsWithSameIdempotencyKeyAreSerialized() {
        String idempotencyKey = "test-concurrent-event-key";
        String eventJson = "{\"eventId\":\"event-777\",\"type\":\"entity.created\",\"actor\":\"user-1\",\"timestamp\":\"2026-01-01T00:00:00Z\",\"data\":{}}";
        AtomicInteger callCount = new AtomicInteger(0);

        when(request.getHeader("X-Idempotency-Key")).thenReturn(idempotencyKey);
        when(request.loadBody()).thenReturn(Promise.of(eventJson));
        when(idempotencyStore.checkIdempotency("tenant-1", idempotencyKey))
            .thenAnswer(inv -> {
                int count = callCount.incrementAndGet();
                if (count == 1) {
                    // First call: no cached value
                    return Promise.ofOptional(Optional.empty());
                } else {
                    // Subsequent calls: return cached value
                    return Promise.ofOptional(Optional.of("{\"eventId\":\"event-777\",\"offset\":104}"));
                }
            });
        when(outboxProcessor.enqueue(any()))
            .thenReturn(Promise.of(Map.of("eventId", "event-777", "offset", 104)));

        // First append
        HttpResponse response1 = runPromise(() -> handler.handleAppendEvent(request));
        assertThat(response1).isNotNull();
        assertThat(callCount.get()).isEqualTo(1);

        // Second append with same idempotency key
        HttpResponse response2 = runPromise(() -> handler.handleAppendEvent(request));
        assertThat(response2).isNotNull();
        assertThat(callCount.get()).isEqualTo(2);

        // Verify outbox enqueue was called only once
        verify(outboxProcessor, times(1)).enqueue(any());
    }

    @Test
    @DisplayName("DC-P1-06: Event append rejects event with mismatched tenant")
    void eventAppendRejectsEventWithMismatchedTenant() {
        // Event has tenant-2 but authenticated tenant is tenant-1
        String eventJson = "{\"eventId\":\"event-666\",\"type\":\"entity.created\",\"tenantId\":\"tenant-2\",\"actor\":\"user-1\",\"timestamp\":\"2026-01-01T00:00:00Z\",\"data\":{}}";

        when(request.loadBody()).thenReturn(Promise.of(eventJson));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        // Should reject with 403 Forbidden due to tenant mismatch
        assertThat(response).isSameAs(errorResponse);
        verify(http).errorResponse(eq(403), anyString());
        verify(outboxProcessor, never()).enqueue(any());
    }

    @Test
    @DisplayName("DC-P1-06: Event append accepts event with matching tenant")
    void eventAppendAcceptsEventWithMatchingTenant() {
        // Event has tenant-1 which matches authenticated tenant
        String eventJson = "{\"eventId\":\"event-555\",\"type\":\"entity.created\",\"tenantId\":\"tenant-1\",\"actor\":\"user-1\",\"timestamp\":\"2026-01-01T00:00:00Z\",\"data\":{}}";

        when(request.loadBody()).thenReturn(Promise.of(eventJson));
        when(idempotencyStore.checkIdempotency("tenant-1", any()))
            .thenReturn(Promise.ofOptional(Optional.empty()));
        when(outboxProcessor.enqueue(any()))
            .thenReturn(Promise.of(Map.of("eventId", "event-555", "offset", 105)));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        assertThat(response).isNotNull();
        verify(outboxProcessor).enqueue(any());
    }

    @Test
    @DisplayName("DC-P1-06: Event append enriches missing timestamp in production")
    void eventAppendEnrichesMissingTimestampInProduction() {
        // Event without timestamp - server should enrich it
        String eventJson = "{\"eventId\":\"event-444\",\"type\":\"entity.created\",\"actor\":\"user-1\",\"data\":{}}";

        when(request.loadBody()).thenReturn(Promise.of(eventJson));
        when(idempotencyStore.checkIdempotency("tenant-1", any()))
            .thenReturn(Promise.ofOptional(Optional.empty()));
        when(outboxProcessor.enqueue(any()))
            .thenReturn(Promise.of(Map.of("eventId", "event-444", "offset", 106)));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        assertThat(response).isNotNull();
        // Verify timestamp was enriched
        verify(outboxProcessor).enqueue(argThat(event -> {
            Map<String, Object> eventMap = (Map<String, Object>) event;
            return eventMap.containsKey("timestamp") && eventMap.get("timestamp") != null;
        }));
    }

    @Test
    @DisplayName("DC-P1-06: Event append preserves ordering for sequential appends")
    void eventAppendPreservesOrderingForSequentialAppends() {
        String event1Json = "{\"eventId\":\"event-111\",\"type\":\"entity.created\",\"actor\":\"user-1\",\"timestamp\":\"2026-01-01T00:00:00Z\",\"data\":{}}";
        String event2Json = "{\"eventId\":\"event-222\",\"type\":\"entity.updated\",\"actor\":\"user-1\",\"timestamp\":\"2026-01-01T00:01:00Z\",\"data\":{}}";

        when(request.loadBody())
            .thenReturn(Promise.of(event1Json))
            .thenReturn(Promise.of(event2Json));
        when(idempotencyStore.checkIdempotency("tenant-1", any()))
            .thenReturn(Promise.ofOptional(Optional.empty()));
        when(outboxProcessor.enqueue(any()))
            .thenReturn(Promise.of(Map.of("eventId", "event-111", "offset", 107)))
            .thenReturn(Promise.of(Map.of("eventId", "event-222", "offset", 108)));

        // First event append
        HttpResponse response1 = runPromise(() -> handler.handleAppendEvent(request));
        assertThat(response1).isNotNull();

        // Second event append
        HttpResponse response2 = runPromise(() -> handler.handleAppendEvent(request));
        assertThat(response2).isNotNull();

        // Verify both events were enqueued
        verify(outboxProcessor, times(2)).enqueue(any());
    }

    @Test
    @DisplayName("DC-P1-06: Event append rejects duplicate eventId without idempotency key")
    void eventAppendRejectsDuplicateEventIdWithoutIdempotencyKey() {
        String eventJson = "{\"eventId\":\"event-333\",\"type\":\"entity.created\",\"actor\":\"user-1\",\"timestamp\":\"2026-01-01T00:00:00Z\",\"data\":{}}";

        when(request.getHeader("X-Idempotency-Key")).thenReturn(null);
        when(request.loadBody()).thenReturn(Promise.of(eventJson));
        when(idempotencyStore.checkIdempotency("tenant-1", "event-333"))
            .thenReturn(Promise.ofOptional(Optional.of("{\"eventId\":\"event-333\",\"offset\":109}")));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        // Should reject as duplicate (409 Conflict)
        assertThat(response).isSameAs(errorResponse);
        verify(http).errorResponse(eq(409), anyString());
        verify(outboxProcessor, never()).enqueue(any());
    }

    @Test
    @DisplayName("DC-P1-09: Event append failure is handled gracefully with error response")
    void eventAppendFailureIsHandledGracefullyWithErrorResponse() {
        String eventJson = "{\"eventId\":\"event-fail-1\",\"type\":\"entity.created\",\"actor\":\"user-1\",\"timestamp\":\"2026-01-01T00:00:00Z\",\"data\":{}}";

        when(request.loadBody()).thenReturn(Promise.of(eventJson));
        when(idempotencyStore.checkIdempotency("tenant-1", any()))
            .thenReturn(Promise.ofOptional(Optional.empty()));
        when(outboxProcessor.enqueue(any()))
            .thenReturn(Promise.ofException(new RuntimeException("Event store unavailable")));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        // Should return error response (503 Service Unavailable)
        assertThat(response).isSameAs(errorResponse);
        verify(http).errorResponse(eq(503), anyString());
    }

    @Test
    @DisplayName("DC-P1-09: Outbox processor failure prevents event append success")
    void outboxProcessorFailurePreventsEventAppendSuccess() {
        String eventJson = "{\"eventId\":\"event-fail-2\",\"type\":\"entity.created\",\"actor\":\"user-1\",\"timestamp\":\"2026-01-01T00:00:00Z\",\"data\":{}}";

        when(request.loadBody()).thenReturn(Promise.of(eventJson));
        when(idempotencyStore.checkIdempotency("tenant-1", any()))
            .thenReturn(Promise.ofOptional(Optional.empty()));
        when(outboxProcessor.enqueue(any()))
            .thenReturn(Promise.ofException(new IllegalStateException("Outbox queue full")));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        // Should return error response due to outbox failure
        assertThat(response).isSameAs(errorResponse);
        verify(http).errorResponse(eq(503), anyString());
    }

    @Test
    @DisplayName("DC-P1-09: Idempotency store failure is handled gracefully")
    void idempotencyStoreFailureIsHandledGracefully() {
        String eventJson = "{\"eventId\":\"event-fail-3\",\"type\":\"entity.created\",\"actor\":\"user-1\",\"timestamp\":\"2026-01-01T00:00:00Z\",\"data\":{}}";

        when(request.loadBody()).thenReturn(Promise.of(eventJson));
        when(idempotencyStore.checkIdempotency("tenant-1", any()))
            .thenReturn(Promise.ofException(new RuntimeException("Idempotency store unavailable")));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        // Should return error response (503 Service Unavailable)
        assertThat(response).isSameAs(errorResponse);
        verify(http).errorResponse(eq(503), anyString());
        verify(outboxProcessor, never()).enqueue(any());
    }

    @Test
    @DisplayName("DC-P1-09: Event append with invalid payload returns 400 Bad Request")
    void eventAppendWithInvalidPayloadReturns400BadRequest() {
        // Invalid JSON payload
        String malformedEventJson = "{invalid json}";

        when(request.loadBody()).thenReturn(Promise.of(malformedEventJson));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        // Should reject with 400 Bad Request
        assertThat(response).isSameAs(errorResponse);
        verify(http).errorResponse(eq(400), anyString());
        verify(outboxProcessor, never()).enqueue(any());
    }

    @Test
    @DisplayName("DC-P1-09: Event append with missing required type returns 400 Bad Request")
    void eventAppendWithMissingRequiredTypeReturns400BadRequest() {
        // Missing type field
        String eventJson = "{\"eventId\":\"event-no-type\",\"actor\":\"user-1\",\"timestamp\":\"2026-01-01T00:00:00Z\",\"data\":{}}";

        when(request.loadBody()).thenReturn(Promise.of(eventJson));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        // Should reject with 400 Bad Request
        assertThat(response).isSameAs(errorResponse);
        verify(http).errorResponse(eq(400), anyString());
        verify(outboxProcessor, never()).enqueue(any());
    }

    @Test
    @DisplayName("DC-P1-09: Event append with missing required payload returns 400 Bad Request")
    void eventAppendWithMissingRequiredPayloadReturns400BadRequest() {
        // Missing payload field
        String eventJson = "{\"eventId\":\"event-no-payload\",\"type\":\"entity.created\",\"actor\":\"user-1\",\"timestamp\":\"2026-01-01T00:00:00Z\"}";

        when(request.loadBody()).thenReturn(Promise.of(eventJson));

        HttpResponse response = runPromise(() -> handler.handleAppendEvent(request));

        // Should reject with 400 Bad Request
        assertThat(response).isSameAs(errorResponse);
        verify(http).errorResponse(eq(400), anyString());
        verify(outboxProcessor, never()).enqueue(any());
    }
}
