package com.ghatana.kernel.interaction;

import io.activej.eventloop.Eventloop;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConsentServiceAdapter.
 *
 * @doc.type class
 * @doc.purpose Test consent service adapter functionality
 * @doc.layer kernel
 */
@DisplayName("ConsentServiceAdapter Tests")
class ConsentServiceAdapterTest {

    @Test
    @DisplayName("Successfully handles granted consent request")
    void successfullyHandlesGrantedConsentRequest() {
        Eventloop eventloop = Eventloop.create();
        
        ConsentServiceAdapter.ConsentServiceClient mockClient = request -> 
            Promise.of(new ConsentServiceAdapter.ConsentServiceResponse(
                "event-1",
                "subject-1",
                true,
                "medical",
                "2026-01-01",
                "2026-12-31",
                null
            ));

        ConsentServiceAdapter adapter = new ConsentServiceAdapter(eventloop, mockClient);

        ProductInteractionRequest<Object> request = createRequest();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatus> result = 
            adapter.handle(request).getResult();

        assertEquals(ProductInteractionStatus.SUCCEEDED, result.status());
        assertTrue(result.payload().granted());
        assertEquals("subject-1", result.payload().subjectId());
        assertEquals("medical", result.payload().consentType());
    }

    @Test
    @DisplayName("Successfully handles denied consent request")
    void successfullyHandlesDeniedConsentRequest() {
        Eventloop eventloop = Eventloop.create();
        
        ConsentServiceAdapter.ConsentServiceClient mockClient = request -> 
            Promise.of(new ConsentServiceAdapter.ConsentServiceResponse(
                "event-1",
                "subject-1",
                false,
                "medical",
                "2026-01-01",
                null,
                "patient revoked consent"
            ));

        ConsentServiceAdapter adapter = new ConsentServiceAdapter(eventloop, mockClient);

        ProductInteractionRequest<Object> request = createRequest();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatus> result = 
            adapter.handle(request).getResult();

        assertEquals(ProductInteractionStatus.BLOCKED, result.status());
        assertEquals("consent_denied", result.reasonCode());
    }

    @Test
    @DisplayName("Handles service client errors")
    void handlesServiceClientErrors() {
        Eventloop eventloop = Eventloop.create();
        
        ConsentServiceAdapter.ConsentServiceClient mockClient = request -> 
            Promise.ofException(new RuntimeException("service unavailable"));

        ConsentServiceAdapter adapter = new ConsentServiceAdapter(eventloop, mockClient);

        ProductInteractionRequest<Object> request = createRequest();

        ProductInteractionOutcome<ConsentStatusInteractionHandler.ConsentStatus> result = 
            adapter.handle(request).getResult();

        assertEquals(ProductInteractionStatus.FAILED, result.status());
        assertEquals("consent_service_error", result.reasonCode());
    }

    @Test
    @DisplayName("Null eventloop throws exception on construction")
    void nullEventloopThrowsExceptionOnConstruction() {
        ConsentServiceAdapter.ConsentServiceClient mockClient = request -> Promise.of(null);
        
        assertThrows(NullPointerException.class, () -> {
            new ConsentServiceAdapter(null, mockClient);
        });
    }

    @Test
    @DisplayName("Null client throws exception on construction")
    void nullClientThrowsExceptionOnConstruction() {
        Eventloop eventloop = Eventloop.create();
        
        assertThrows(NullPointerException.class, () -> {
            new ConsentServiceAdapter(eventloop, null);
        });
    }

    private ProductInteractionRequest<Object> createRequest() {
        return new ProductInteractionRequest<>(
            "1.0.0",
            "interaction-1",
            "kernel.consent-status.v1",
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
                "subjectId", "subject-1",
                "resourceId", "resource-1",
                "action", "read"
            ),
            "payload"
        );
    }
}
