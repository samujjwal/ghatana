package com.ghatana.kernel.interaction;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PreferenceServiceAdapter.
 *
 * @doc.type class
 * @doc.purpose Test preference service adapter functionality
 * @doc.layer kernel
 */
@DisplayName("PreferenceServiceAdapter Tests")
class PreferenceServiceAdapterTest {

    @Test
    @DisplayName("Successfully handles preference lookup request")
    void successfullyHandlesPreferenceLookupRequest() {
        Eventloop eventloop = Eventloop.create();
        
        PreferenceServiceAdapter.PreferenceServiceClient mockClient = request -> 
            Promise.of(new PreferenceServiceAdapter.PreferenceServiceResponse(
                "event-1",
                "customer-1",
                "marketing",
                "email",
                true,
                "2026-01-01T00:00:00Z"
            ));

        PreferenceServiceAdapter adapter = new PreferenceServiceAdapter(eventloop, mockClient);

        ProductInteractionRequest<Object> request = createRequest();

        ProductInteractionOutcome<NotificationPreferenceInteractionHandler.NotificationPreference> result = 
            adapter.handle(request).getResult();

        assertEquals(ProductInteractionStatus.SUCCEEDED, result.status());
        assertTrue(result.payload().enabled());
        assertEquals("2026-01-01T00:00:00Z", result.payload().updatedAt());
    }

    @Test
    @DisplayName("Handles service client errors")
    void handlesServiceClientErrors() {
        Eventloop eventloop = Eventloop.create();
        
        PreferenceServiceAdapter.PreferenceServiceClient mockClient = request -> 
            Promise.ofException(new RuntimeException("service unavailable"));

        PreferenceServiceAdapter adapter = new PreferenceServiceAdapter(eventloop, mockClient);

        ProductInteractionRequest<Object> request = createRequest();

        ProductInteractionOutcome<NotificationPreferenceInteractionHandler.NotificationPreference> result = 
            adapter.handle(request).getResult();

        assertEquals(ProductInteractionStatus.FAILED, result.status());
        assertEquals("preference_service_error", result.reasonCode());
    }

    @Test
    @DisplayName("Null eventloop throws exception on construction")
    void nullEventloopThrowsExceptionOnConstruction() {
        PreferenceServiceAdapter.PreferenceServiceClient mockClient = request -> Promise.of(null);
        
        assertThrows(NullPointerException.class, () -> {
            new PreferenceServiceAdapter(null, mockClient);
        });
    }

    @Test
    @DisplayName("Null client throws exception on construction")
    void nullClientThrowsExceptionOnConstruction() {
        Eventloop eventloop = Eventloop.create();
        
        assertThrows(NullPointerException.class, () -> {
            new PreferenceServiceAdapter(eventloop, null);
        });
    }

    private ProductInteractionRequest<Object> createRequest() {
        return new ProductInteractionRequest<>(
            "1.0.0",
            "interaction-1",
            "kernel.notification-preference.v1",
            "1.0.0",
            "provider-1",
            "consumer-1",
            "unit-1",
            "tenant-1",
            "workspace-1",
            "run-1",
            "correlation-1",
            Instant.now(),
            Map.of(
                "customerId", "customer-1",
                "preferenceType", "marketing",
                "channel", "email"
            ),
            "payload"
        );
    }
}
