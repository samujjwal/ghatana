/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.aep.testing;

import com.ghatana.aep.AepEngine;
import com.ghatana.aep.Aep;
import com.ghatana.aep.consent.ConsentService.ConsentDecision;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shared AEP engine test fixtures (AEP-010).
 *
 * <p>Consolidates common {@link AepEngine.Event} builders, consent decisions, and
 * {@link AepEngine.PatternDefinition} factories that were previously duplicated across
 * test classes in {@code aep-engine}, {@code aep-runtime-core}, and the operator tests.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * AepEngine.Event event    = AepEngineTestFixtures.createTestEvent();
 * AepEngine.Event invalid  = AepEngineTestFixtures.createOversizedTypeEvent();
 * ConsentDecision allowed  = AepEngineTestFixtures.allowAll();
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Consolidated AEP engine test fixtures
 * @doc.layer product
 * @doc.pattern Test
 */
public final class AepEngineTestFixtures {

    private AepEngineTestFixtures() {}

    // ─── Events ───────────────────────────────────────────────────────────────

    /**
     * Creates a minimal valid event of type {@code "test.event"}.
     */
    public static AepEngine.Event createTestEvent() {
        return new AepEngine.Event(
            "test.event",
            Map.of("value", 42, "source", "unit-test"),
            Map.of("correlationId", "corr-001"),
            Instant.now()
        );
    }

    /**
     * Creates a valid event of the given type with a simple payload.
     */
    public static AepEngine.Event createTestEvent(String type) {
        return new AepEngine.Event(type, Map.of("value", 1), Map.of(), Instant.now());
    }

    /**
     * Creates a valid event with the supplied type and payload.
     */
    public static AepEngine.Event createTestEvent(String type, Map<String, Object> payload) {
        return new AepEngine.Event(type, payload, Map.of(), Instant.now());
    }

    /**
     * Creates an event whose type exceeds the 256-character limit — expected to fail
     * schema validation.
     */
    public static AepEngine.Event createOversizedTypeEvent() {
        return new AepEngine.Event(
            "x".repeat(257),
            Map.of(),
            Map.of(),
            Instant.now(),
            AepEngine.IdentityContext.empty(),
            AepEngine.ConsentContext.defaultConsent(),
            "1.0",
            Optional.empty()
        );
    }

    /**
     * Creates a valid event with an idempotency key set.
     */
    public static AepEngine.Event createIdempotentEvent(String type, String idempotencyKey) {
        return createTestEvent(type).withIdempotencyKey(idempotencyKey);
    }

    /**
     * Creates a valid event with a correlation ID header.
     */
    public static AepEngine.Event createCorrelatedEvent(String correlationId) {
        return createTestEvent().withCorrelationId(correlationId);
    }

    // ─── Consent decisions ────────────────────────────────────────────────────

    /** Returns an allowed decision with the standard event-processing purpose. */
    public static ConsentDecision allowAll() {
        return ConsentDecision.allow(List.of("event_processing"));
    }

    /** Returns a denied decision with the supplied reason. */
    public static ConsentDecision denyWith(String reason) {
        return ConsentDecision.deny(reason);
    }

    // ─── Pattern definitions ──────────────────────────────────────────────────

    /**
     * Creates a threshold pattern that fires when {@code fieldName} exceeds {@code threshold}.
     */
    public static AepEngine.PatternDefinition thresholdPattern(String fieldName, double threshold) {
        return new AepEngine.PatternDefinition(
            "test-threshold-" + fieldName,
            "Threshold pattern for testing",
            AepEngine.PatternType.THRESHOLD,
            Map.of("field", fieldName, "threshold", threshold)
        );
    }

    /**
     * Creates a sequence pattern for two event types.
     */
    public static AepEngine.PatternDefinition sequencePattern(String first, String second) {
        return new AepEngine.PatternDefinition(
            "test-sequence-" + first + "-" + second,
            "Sequence pattern for testing",
            AepEngine.PatternType.SEQUENCE,
            Map.of("sequence", List.of(first, second))
        );
    }

    // ─── Config ───────────────────────────────────────────────────────────────

    /** Returns the standard fast test config. */
    public static Aep.AepConfig testConfig() {
        return Aep.AepConfig.forTesting();
    }
}
