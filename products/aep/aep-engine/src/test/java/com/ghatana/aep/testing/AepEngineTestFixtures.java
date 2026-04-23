/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * Shared AEP engine test fixtures (AEP-010). // GH-90000
 *
 * <p>Consolidates common {@link AepEngine.Event} builders, consent decisions, and
 * {@link AepEngine.PatternDefinition} factories that were previously duplicated across
 * test classes in {@code aep-engine} and the operator tests.
 *
 * <p><b>Usage:</b>
 * <pre>{@code
 * AepEngine.Event event    = AepEngineTestFixtures.createTestEvent(); // GH-90000
 * AepEngine.Event invalid  = AepEngineTestFixtures.createOversizedTypeEvent(); // GH-90000
 * ConsentDecision allowed  = AepEngineTestFixtures.allowAll(); // GH-90000
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Consolidated AEP engine test fixtures
 * @doc.layer product
 * @doc.pattern Test
 */
public final class AepEngineTestFixtures {

    private AepEngineTestFixtures() {} // GH-90000

    // ─── Events ───────────────────────────────────────────────────────────────

    /**
     * Creates a minimal valid event of type {@code "test.event"}.
     */
    public static AepEngine.Event createTestEvent() { // GH-90000
        return new AepEngine.Event( // GH-90000
            "test.event",
            Map.of("value", 42, "source", "unit-test"), // GH-90000
            Map.of("correlationId", "corr-001"), // GH-90000
            Instant.now() // GH-90000
        );
    }

    /**
     * Creates a valid event of the given type with a simple payload.
     */
    public static AepEngine.Event createTestEvent(String type) { // GH-90000
        return new AepEngine.Event(type, Map.of("value", 1), Map.of(), Instant.now()); // GH-90000
    }

    /**
     * Creates a valid event with the supplied type and payload.
     */
    public static AepEngine.Event createTestEvent(String type, Map<String, Object> payload) { // GH-90000
        return new AepEngine.Event(type, payload, Map.of(), Instant.now()); // GH-90000
    }

    /**
     * Creates an event whose type exceeds the 256-character limit — expected to fail
     * schema validation.
     */
    public static AepEngine.Event createOversizedTypeEvent() { // GH-90000
        return new AepEngine.Event( // GH-90000
            "x".repeat(257), // GH-90000
            Map.of(), // GH-90000
            Map.of(), // GH-90000
            Instant.now(), // GH-90000
            AepEngine.IdentityContext.empty(), // GH-90000
            AepEngine.ConsentContext.defaultConsent(), // GH-90000
            "1.0",
            Optional.empty() // GH-90000
        );
    }

    /**
     * Creates a valid event with an idempotency key set.
     */
    public static AepEngine.Event createIdempotentEvent(String type, String idempotencyKey) { // GH-90000
        return createTestEvent(type).withIdempotencyKey(idempotencyKey); // GH-90000
    }

    /**
     * Creates a valid event with a correlation ID header.
     */
    public static AepEngine.Event createCorrelatedEvent(String correlationId) { // GH-90000
        return createTestEvent().withCorrelationId(correlationId); // GH-90000
    }

    // ─── Consent decisions ────────────────────────────────────────────────────

    /** Returns an allowed decision with the standard event-processing purpose. */
    public static ConsentDecision allowAll() { // GH-90000
        return ConsentDecision.allow(List.of("event_processing"));
    }

    /** Returns a denied decision with the supplied reason. */
    public static ConsentDecision denyWith(String reason) { // GH-90000
        return ConsentDecision.deny(reason); // GH-90000
    }

    // ─── Pattern definitions ──────────────────────────────────────────────────

    /**
     * Creates a threshold pattern that fires when {@code fieldName} exceeds {@code threshold}.
     */
    public static AepEngine.PatternDefinition thresholdPattern(String fieldName, double threshold) { // GH-90000
        return new AepEngine.PatternDefinition( // GH-90000
            "test-threshold-" + fieldName,
            "Threshold pattern for testing",
            AepEngine.PatternType.THRESHOLD,
            Map.of("field", fieldName, "threshold", threshold) // GH-90000
        );
    }

    /**
     * Creates a sequence pattern for two event types.
     */
    public static AepEngine.PatternDefinition sequencePattern(String first, String second) { // GH-90000
        return new AepEngine.PatternDefinition( // GH-90000
            "test-sequence-" + first + "-" + second,
            "Sequence pattern for testing",
            AepEngine.PatternType.SEQUENCE,
            Map.of("sequence", List.of(first, second)) // GH-90000
        );
    }

    // ─── Config ───────────────────────────────────────────────────────────────

    /** Returns the standard fast test config. */
    public static Aep.AepConfig testConfig() { // GH-90000
        return Aep.AepConfig.forTesting(); // GH-90000
    }
}
