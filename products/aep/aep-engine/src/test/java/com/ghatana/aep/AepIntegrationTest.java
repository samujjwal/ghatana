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
 * End-to-end integration tests for the AEP engine processing pipeline (AEP-002).
 *
 * <p>These tests validate coordination between event-cloud ingestion, consent
 * evaluation, pattern matching, tenant isolation, and external delivery.
 *
 * @doc.type class
 * @doc.purpose Verify complete AEP event flow across integrated engine components
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("AEP integration")
class AepIntegrationTest extends EventloopTestBase {

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";

    private AepEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    @DisplayName("processes an event from event-cloud append through delivery and subscriber notification")
    void shouldProcessCompleteEventFlow() {
        AtomicInteger deliveries = new AtomicInteger();
        AtomicReference<String> deliveredPayload = new AtomicReference<>();
        AtomicReference<String> appendedEventId = new AtomicReference<>();
        AtomicReference<String> appendedPayload = new AtomicReference<>();
        List<AepEngine.Detection> subscriberDetections = new ArrayList<>();

        EventDeliveryService deliveryService = EventDeliveryService.withDestinations(
            new EventDeliveryService.EventDestination("capture", (tenantId, eventType, payloadJson, headers) -> {
                deliveries.incrementAndGet();
                deliveredPayload.set(payloadJson);
                return true;
            })
        );

        engine = Aep.create(
            Aep.AepConfig.builder()
                .enableTracing(true)
                .consentCache(java.time.Duration.ofMinutes(1), 100)
                .build(),
            deliveryService
        );

        AepEngine.Pattern pattern = runPromise(() -> engine.registerPattern(
            TENANT_A,
            new AepEngine.PatternDefinition(
                "High reading",
                "Detect threshold exceedance",
                AepEngine.PatternType.THRESHOLD,
                Map.of("field", "reading", "threshold", 50.0)
            )
        ));

        engine.subscribe(TENANT_A, pattern.id(), subscriberDetections::add);
        engine.eventCloud().subscribe(TENANT_A, "sensor.reading", (eventId, eventType, payload) -> {
            appendedEventId.set(eventId);
            appendedPayload.set(new String(payload, StandardCharsets.UTF_8));
        });

        AepEngine.Event event = AepEngine.Event.of(
                "sensor.reading",
                Map.of(
                    "reading", 72.5,
                    "consentStatus", "GRANTED",
                    "allowedPurposes", List.of("event_processing"),
                    "userId", "user-17"
                ))
            .withCorrelationId("corr-123")
            .withIdempotencyKey("idem-123");

        AepEngine.ProcessingResult result = runPromise(() -> engine.process(TENANT_A, event));

        assertThat(result.success()).isTrue();
        assertThat(result.detections()).hasSize(1);
        assertThat(result.metadata()).containsEntry("processed", true);
        assertThat(result.metadata()).containsEntry("correlationId", "corr-123");
        assertThat(result.metadata()).containsEntry("stitchedId", "user-17");
        assertThat(result.metadata()).containsEntry("eventVersion", "1.0");
        assertThat(subscriberDetections).hasSize(1);
        assertThat(deliveries.get()).isEqualTo(1);
        assertThat(deliveredPayload.get()).contains("sensor.reading");
        assertThat(deliveredPayload.get()).contains("tenant-a");
        assertThat(appendedEventId.get()).isNotBlank();
        assertThat(appendedPayload.get()).contains("sensor.reading");
        assertThat(appendedPayload.get()).contains("corr-123");
    }

    @Test
    @DisplayName("rejects cross-tenant pattern subscription and event tenant spoofing")
    void shouldEnforceTenantIsolationAcrossIntegratedOperations() {
        engine = Aep.forTesting();

        AepEngine.Pattern pattern = runPromise(() -> engine.registerPattern(
            TENANT_A,
            new AepEngine.PatternDefinition(
                "Tenant A only",
                null,
                AepEngine.PatternType.CUSTOM,
                Map.of()
            )
        ));

        assertThatThrownBy(() -> engine.subscribe(TENANT_B, pattern.id(), detection -> {}))
            .isInstanceOf(AepTenantException.class)
            .hasMessageContaining("different tenant");

        AepEngine.Event spoofed = new AepEngine.Event(
            "profile.update",
            Map.of("tenantId", TENANT_A),
            Map.of("x-tenant-id", TENANT_A),
            Instant.now()
        );

        assertThatThrownBy(() -> runPromise(() -> engine.process(TENANT_B, spoofed)))
            .isInstanceOf(AepTenantException.class)
            .hasMessageContaining("Cross-tenant event access rejected");
    }

    @Test
    @DisplayName("applies configured rate limiting during event ingestion")
    void shouldApplyRateLimiting() {
        engine = Aep.create(Aep.AepConfig.builder()
            .rateLimiting(1, 1)
            .build());

        AepEngine.Event first = AepEngine.Event.of(
            "click",
            Map.of("consentStatus", "GRANTED", "allowedPurposes", List.of("event_processing"))
        );
        AepEngine.Event second = AepEngine.Event.of(
            "click",
            Map.of("consentStatus", "GRANTED", "allowedPurposes", List.of("event_processing"))
        );

        AepEngine.ProcessingResult firstResult = runPromise(() -> engine.process(TENANT_A, first));
        AepEngine.ProcessingResult secondResult = runPromise(() -> engine.process(TENANT_A, second));

        assertThat(firstResult.success()).isTrue();
        assertThat(secondResult.success()).isFalse();
        assertThat(secondResult.metadata()).containsEntry("skipped", true);
        assertThat((String) secondResult.metadata().get("reason")).contains("Rate limited");
    }
}
