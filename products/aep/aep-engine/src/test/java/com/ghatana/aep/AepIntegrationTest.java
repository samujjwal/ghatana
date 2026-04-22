package com.ghatana.aep;

import com.ghatana.aep.delivery.EventDeliveryService;
import com.ghatana.aep.error.AepTenantException;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end integration tests for the AEP engine processing pipeline (AEP-002). // GH-90000
 *
 * <p>These tests validate coordination between event-cloud ingestion, consent
 * evaluation, pattern matching, tenant isolation, and external delivery.
 *
 * @doc.type class
 * @doc.purpose Verify complete AEP event flow across integrated engine components
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AEP integration [GH-90000]")
class AepIntegrationTest extends EventloopTestBase {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    private AepEngine engine;

    @AfterEach
    void tearDown() { // GH-90000
        if (engine != null) { // GH-90000
            engine.close(); // GH-90000
        }
    }

    @Test
    @DisplayName("processes an event from event-cloud append through delivery and subscriber notification [GH-90000]")
    void shouldProcessCompleteEventFlow() { // GH-90000
        AtomicInteger deliveries = new AtomicInteger(); // GH-90000
        AtomicReference<String> deliveredPayload = new AtomicReference<>(); // GH-90000
        AtomicReference<String> appendedEventId = new AtomicReference<>(); // GH-90000
        AtomicReference<String> appendedPayload = new AtomicReference<>(); // GH-90000
        List<AepEngine.Detection> subscriberDetections = new ArrayList<>(); // GH-90000

        EventDeliveryService deliveryService = EventDeliveryService.withDestinations( // GH-90000
            new EventDeliveryService.EventDestination("capture", (tenantId, eventType, payloadJson, headers) -> { // GH-90000
                deliveries.incrementAndGet(); // GH-90000
                deliveredPayload.set(payloadJson); // GH-90000
                return true;
            })
        );

        engine = Aep.create( // GH-90000
            Aep.AepConfig.builder() // GH-90000
                .enableTracing(true) // GH-90000
                .consentCache(java.time.Duration.ofMinutes(1), 100) // GH-90000
                .build(), // GH-90000
            deliveryService
        );

        AepEngine.Pattern pattern = runPromise(() -> engine.registerPattern( // GH-90000
            TENANT_A,
            new AepEngine.PatternDefinition( // GH-90000
                "High reading",
                "Detect threshold exceedance",
                AepEngine.PatternType.THRESHOLD,
                Map.of("field", "reading", "threshold", 50.0) // GH-90000
            )
        ));

        engine.subscribe(TENANT_A, pattern.id(), subscriberDetections::add); // GH-90000
        engine.eventCloud().subscribe(TENANT_A, "sensor.reading", (eventId, eventType, payload) -> { // GH-90000
            appendedEventId.set(eventId); // GH-90000
            appendedPayload.set(new String(payload, StandardCharsets.UTF_8)); // GH-90000
        });

        AepEngine.Event event = AepEngine.Event.of( // GH-90000
                "sensor.reading",
                Map.of( // GH-90000
                    "reading", 72.5,
                    "consentStatus", "GRANTED",
                    "allowedPurposes", List.of("event_processing [GH-90000]"),
                    "userId", "user-17"
                ))
            .withCorrelationId("corr-123 [GH-90000]")
            .withIdempotencyKey("idem-123 [GH-90000]");

        AepEngine.ProcessingResult result = runPromise(() -> engine.process(TENANT_A, event)); // GH-90000

        assertThat(result.success()).isTrue(); // GH-90000
        assertThat(result.detections()).hasSize(1); // GH-90000
        assertThat(result.metadata()).containsEntry("processed", true); // GH-90000
        assertThat(result.metadata()).containsEntry("correlationId", "corr-123"); // GH-90000
        assertThat(result.metadata()).containsEntry("stitchedId", "user-17"); // GH-90000
        assertThat(result.metadata()).containsEntry("eventVersion", "1.0"); // GH-90000
        assertThat(subscriberDetections).hasSize(1); // GH-90000
        assertThat(deliveries.get()).isEqualTo(1); // GH-90000
        assertThat(deliveredPayload.get()).contains("sensor.reading [GH-90000]");
        assertThat(deliveredPayload.get()).contains("tenant-a [GH-90000]");
        assertThat(appendedEventId.get()).isNotBlank(); // GH-90000
        assertThat(appendedPayload.get()).contains("sensor.reading [GH-90000]");
        assertThat(appendedPayload.get()).contains("corr-123 [GH-90000]");
    }

    @Test
    @DisplayName("rejects cross-tenant pattern subscription and event tenant spoofing [GH-90000]")
    void shouldEnforceTenantIsolationAcrossIntegratedOperations() { // GH-90000
        engine = Aep.forTesting(); // GH-90000

        AepEngine.Pattern pattern = runPromise(() -> engine.registerPattern( // GH-90000
            TENANT_A,
            new AepEngine.PatternDefinition( // GH-90000
                "Tenant A only",
                null,
                AepEngine.PatternType.CUSTOM,
                Map.of() // GH-90000
            )
        ));

        assertThatThrownBy(() -> engine.subscribe(TENANT_B, pattern.id(), detection -> {})) // GH-90000
            .isInstanceOf(AepTenantException.class) // GH-90000
            .hasMessageContaining("different tenant [GH-90000]");

        AepEngine.Event spoofed = new AepEngine.Event( // GH-90000
            "profile.update",
            Map.of("tenantId", TENANT_A), // GH-90000
            Map.of("x-tenant-id", TENANT_A), // GH-90000
            Instant.now() // GH-90000
        );

        assertThatThrownBy(() -> runPromise(() -> engine.process(TENANT_B, spoofed))) // GH-90000
            .isInstanceOf(AepTenantException.class) // GH-90000
            .hasMessageContaining("Cross-tenant event access rejected [GH-90000]");
    }

    @Test
    @DisplayName("applies configured rate limiting during event ingestion [GH-90000]")
    void shouldApplyRateLimiting() { // GH-90000
        engine = Aep.create(Aep.AepConfig.builder() // GH-90000
            .rateLimiting(1, 1) // GH-90000
            .build()); // GH-90000

        AepEngine.Event first = AepEngine.Event.of( // GH-90000
            "click",
            Map.of("consentStatus", "GRANTED", "allowedPurposes", List.of("event_processing [GH-90000]"))
        );
        AepEngine.Event second = AepEngine.Event.of( // GH-90000
            "click",
            Map.of("consentStatus", "GRANTED", "allowedPurposes", List.of("event_processing [GH-90000]"))
        );

        AepEngine.ProcessingResult firstResult = runPromise(() -> engine.process(TENANT_A, first)); // GH-90000
        AepEngine.ProcessingResult secondResult = runPromise(() -> engine.process(TENANT_A, second)); // GH-90000

        assertThat(firstResult.success()).isTrue(); // GH-90000
        assertThat(secondResult.success()).isFalse(); // GH-90000
        assertThat(secondResult.metadata()).containsEntry("skipped", true); // GH-90000
        assertThat((String) secondResult.metadata().get("reason [GH-90000]")).contains("Rate limited [GH-90000]");
    }
}
