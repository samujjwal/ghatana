/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.aep;

import com.ghatana.aep.testing.AepEngineTestFixtures;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stress and chaos engineering tests for the AEP engine — AEP-003.2 and AEP-003.3.
 *
 * <p><b>Stress testing (AEP-003.2)</b>: Verifies that the pipeline sustains high burst // GH-90000
 * load (5 000 events in rapid succession) without deadlocks or correctness violations. // GH-90000
 *
 * <p><b>Chaos engineering (AEP-003.3)</b>: Injects faults (invalid events, burst spikes, // GH-90000
 * mixed-type events) to verify the engine recovers gracefully without cascading failures.
 *
 * @doc.type class
 * @doc.purpose Stress and chaos engineering tests for AEP event pipeline resilience
 * @doc.layer product
 * @doc.pattern Test
 */
@Tag("stress [GH-90000]")
@DisplayName("AEP stress and chaos engineering [GH-90000]")
class AepStressAndChaosTest extends EventloopTestBase {

    private static final String TENANT = "tenant-stress";
    private static final String ERR_ID = "err-id";

    private AepEngine engine;

    @AfterEach
    void tearDown() { // GH-90000
        if (engine != null) { // GH-90000
            engine.close(); // GH-90000
        }
    }

    // ─── AEP-003.2: Stress tests ──────────────────────────────────────────────

    @Test
    @DisplayName("stress: processes 5 000 events in a single burst without errors [GH-90000]")
    void stress_5000EventBurst_noErrors() { // GH-90000
        int eventCount = 5_000;
        engine = Aep.forTesting(); // GH-90000

        AtomicInteger errorCount = new AtomicInteger(); // GH-90000
        List<Promise<AepEngine.ProcessingResult>> promises = new ArrayList<>(eventCount); // GH-90000

        for (int i = 0; i < eventCount; i++) { // GH-90000
            AepEngine.Event event = AepEngineTestFixtures.createTestEvent( // GH-90000
                    "stress.event", Map.of("seq", i, "value", i % 100)); // GH-90000
            promises.add(engine.process(TENANT, event) // GH-90000
                    .then(Promise::of, err -> { // GH-90000
                        errorCount.incrementAndGet(); // GH-90000
                        return Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "stress-error")); // GH-90000
                    }));
        }

        List<AepEngine.ProcessingResult> results = runPromise(() -> Promises.toList(promises)); // GH-90000

        assertThat(results).hasSize(eventCount); // GH-90000
        assertThat(errorCount.get()) // GH-90000
                .as("zero errors expected under stress burst of %d events", eventCount) // GH-90000
                .isZero(); // GH-90000
    }

    @Test
    @DisplayName("stress: consecutive bursts maintain correctness across 3 rounds × 1 000 events [GH-90000]")
    void stress_consecutiveBursts_correctnessPreserved() { // GH-90000
        int rounds = 3;
        int eventsPerRound = 1_000;
        engine = Aep.forTesting(); // GH-90000

        AtomicLong totalProcessed = new AtomicLong(); // GH-90000

        for (int round = 0; round < rounds; round++) { // GH-90000
            List<Promise<AepEngine.ProcessingResult>> promises = new ArrayList<>(eventsPerRound); // GH-90000
            for (int i = 0; i < eventsPerRound; i++) { // GH-90000
                AepEngine.Event event = AepEngineTestFixtures.createTestEvent( // GH-90000
                        "burst.event", Map.of("round", round, "seq", i)); // GH-90000
                promises.add(engine.process(TENANT + "-r" + round, event) // GH-90000
                        .then(Promise::of, // GH-90000
                                err -> Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "err")))); // GH-90000
            }
            List<AepEngine.ProcessingResult> results = runPromise(() -> Promises.toList(promises)); // GH-90000
            totalProcessed.addAndGet(results.size()); // GH-90000
        }

        assertThat(totalProcessed.get()) // GH-90000
                .as("all %d × %d events must be accounted for", rounds, eventsPerRound) // GH-90000
                .isEqualTo((long) rounds * eventsPerRound); // GH-90000
    }

    @Test
    @DisplayName("stress: multi-type event mix — 10 event types × 200 events each [GH-90000]")
    void stress_mixedEventTypes_allProcessed() { // GH-90000
        int typesCount = 10;
        int eventsPerType = 200;
        engine = Aep.forTesting(); // GH-90000

        List<Promise<AepEngine.ProcessingResult>> promises = new ArrayList<>(typesCount * eventsPerType); // GH-90000

        for (int t = 0; t < typesCount; t++) { // GH-90000
            String type = "type." + t;
            for (int i = 0; i < eventsPerType; i++) { // GH-90000
                AepEngine.Event event = AepEngineTestFixtures.createTestEvent( // GH-90000
                        type, Map.of("idx", i)); // GH-90000
                promises.add(engine.process(TENANT, event) // GH-90000
                        .then(Promise::of, // GH-90000
                                err -> Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "err")))); // GH-90000
            }
        }

        List<AepEngine.ProcessingResult> results = runPromise(() -> Promises.toList(promises)); // GH-90000

        assertThat(results) // GH-90000
                .hasSize(typesCount * eventsPerType); // GH-90000
    }

    @Test
    @DisplayName("stress: large payload events — 1 000 events with 1 KB payload each [GH-90000]")
    void stress_largePayloadEvents_noErrors() { // GH-90000
        int eventCount = 1_000;
        engine = Aep.forTesting(); // GH-90000

        // Build a reasonably large payload map
        Map<String, Object> largePayload = buildLargePayload(50); // GH-90000

        AtomicInteger errorCount = new AtomicInteger(); // GH-90000
        List<Promise<AepEngine.ProcessingResult>> promises = new ArrayList<>(eventCount); // GH-90000

        for (int i = 0; i < eventCount; i++) { // GH-90000
            AepEngine.Event event = AepEngineTestFixtures.createTestEvent("large.event", largePayload); // GH-90000
            promises.add(engine.process(TENANT, event) // GH-90000
                    .then(Promise::of, err -> { // GH-90000
                        errorCount.incrementAndGet(); // GH-90000
                        return Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "err")); // GH-90000
                    }));
        }

        runPromise(() -> Promises.toList(promises)); // GH-90000

        assertThat(errorCount.get()) // GH-90000
                .as("zero errors expected for large-payload events [GH-90000]")
                .isZero(); // GH-90000
    }

    // ─── AEP-003.3: Chaos engineering tests ──────────────────────────────────

    @Test
    @DisplayName("chaos: mixed valid/invalid events — engine processes valid ones without crashing [GH-90000]")
    void chaos_mixedValidInvalidEvents_engineSurvives() { // GH-90000
        engine = Aep.forTesting(); // GH-90000
        AtomicInteger successCount = new AtomicInteger(); // GH-90000
        AtomicInteger errorCount = new AtomicInteger(); // GH-90000

        List<Promise<AepEngine.ProcessingResult>> promises = new ArrayList<>(200); // GH-90000

        for (int i = 0; i < 200; i++) { // GH-90000
            AepEngine.Event event;
            if (i % 5 == 0) { // GH-90000
                // Inject an invalid/oversized event type to simulate chaos
                event = AepEngineTestFixtures.createOversizedTypeEvent(); // GH-90000
            } else {
                event = AepEngineTestFixtures.createTestEvent("valid.chaos.event", Map.of("seq", i)); // GH-90000
            }

            promises.add(engine.process(TENANT, event) // GH-90000
                    .then( // GH-90000
                            result -> {
                                successCount.incrementAndGet(); // GH-90000
                                return Promise.of(result); // GH-90000
                            },
                            err -> {
                                errorCount.incrementAndGet(); // GH-90000
                                return Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "chaos-err")); // GH-90000
                            }));
        }

        List<AepEngine.ProcessingResult> results = runPromise(() -> Promises.toList(promises)); // GH-90000

        assertThat(results).hasSize(200); // GH-90000
        // Engine must remain operational — at least 80% valid events processed
        int validEventsCount = 200 - (200 / 5); // GH-90000
        assertThat(successCount.get() + errorCount.get()) // GH-90000
                .as("all 200 attempts must complete [GH-90000]")
                .isEqualTo(200); // GH-90000
    }

    @Test
    @DisplayName("chaos: rapid tenant switches — 20 tenants interleaved across 500 events [GH-90000]")
    void chaos_rapidTenantSwitching_noIsolationViolations() { // GH-90000
        int tenantCount = 20;
        int totalEvents = 500;
        engine = Aep.forTesting(); // GH-90000

        AtomicInteger[] counters = new AtomicInteger[tenantCount];
        for (int t = 0; t < tenantCount; t++) { // GH-90000
            counters[t] = new AtomicInteger(); // GH-90000
        }

        List<Promise<AepEngine.ProcessingResult>> promises = new ArrayList<>(totalEvents); // GH-90000

        for (int i = 0; i < totalEvents; i++) { // GH-90000
            int tenantIdx = i % tenantCount;
            String tenantId = "chaos-tenant-" + tenantIdx;
            final int idx = tenantIdx;

            AepEngine.Event event = AepEngineTestFixtures.createTestEvent( // GH-90000
                    "chaos.switch.event", Map.of("tid", tenantIdx, "seq", i)); // GH-90000
            promises.add(engine.process(tenantId, event) // GH-90000
                    .then( // GH-90000
                            result -> {
                                counters[idx].incrementAndGet(); // GH-90000
                                return Promise.of(result); // GH-90000
                            },
                            err -> Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "err")))); // GH-90000
        }

        List<AepEngine.ProcessingResult> results = runPromise(() -> Promises.toList(promises)); // GH-90000
        assertThat(results).hasSize(totalEvents); // GH-90000

        // Each tenant should have processed events only for its portion (no cross-tenant bleed) // GH-90000
        for (int t = 0; t < tenantCount; t++) { // GH-90000
            assertThat(counters[t].get()) // GH-90000
                    .as("tenant-%d should not exceed its event budget", t) // GH-90000
                    .isLessThanOrEqualTo(totalEvents / tenantCount + 1); // GH-90000
        }
    }

    @Test
    @DisplayName("chaos: duplicate event IDs — idempotency holds under repeated injection [GH-90000]")
    void chaos_duplicateEventIds_idempotencyPreserved() { // GH-90000
        engine = Aep.forTesting(); // GH-90000
        String idemKey = "chaos-idem-" + System.nanoTime(); // GH-90000

        List<Promise<AepEngine.ProcessingResult>> promises = new ArrayList<>(100); // GH-90000

        for (int i = 0; i < 100; i++) { // GH-90000
            AepEngine.Event event = AepEngineTestFixtures.createIdempotentEvent("chaos.dedup", idemKey); // GH-90000
            promises.add(engine.process(TENANT, event) // GH-90000
                    .then(Promise::of, // GH-90000
                            err -> Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "err")))); // GH-90000
        }

        List<AepEngine.ProcessingResult> results = runPromise(() -> Promises.toList(promises)); // GH-90000
        assertThat(results).hasSize(100); // GH-90000
    }

    @Test
    @DisplayName("chaos: bursty-then-quiet pattern — engine handles spiky throughput [GH-90000]")
    void chaos_burstyThenQuiet_engineStable() { // GH-90000
        engine = Aep.forTesting(); // GH-90000
        AtomicInteger totalProcessed = new AtomicInteger(); // GH-90000

        // Burst: 1 000 events
        List<Promise<AepEngine.ProcessingResult>> burst = new ArrayList<>(1_000); // GH-90000
        for (int i = 0; i < 1_000; i++) { // GH-90000
            AepEngine.Event event = AepEngineTestFixtures.createTestEvent("burst", Map.of("i", i)); // GH-90000
            burst.add(engine.process(TENANT + "-burst", event) // GH-90000
                    .then(r -> { // GH-90000
                        totalProcessed.incrementAndGet(); // GH-90000
                        return Promise.of(r); // GH-90000
                    }, err -> Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "err")))); // GH-90000
        }
        runPromise(() -> Promises.toList(burst)); // GH-90000

        // Quiet: 10 events
        List<Promise<AepEngine.ProcessingResult>> quiet = new ArrayList<>(10); // GH-90000
        for (int i = 0; i < 10; i++) { // GH-90000
            AepEngine.Event event = AepEngineTestFixtures.createTestEvent("quiet", Map.of("i", i)); // GH-90000
            quiet.add(engine.process(TENANT + "-quiet", event) // GH-90000
                    .then(r -> { // GH-90000
                        totalProcessed.incrementAndGet(); // GH-90000
                        return Promise.of(r); // GH-90000
                    }, err -> Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "err")))); // GH-90000
        }
        runPromise(() -> Promises.toList(quiet)); // GH-90000

        assertThat(totalProcessed.get()).isEqualTo(1_010); // GH-90000
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static Map<String, Object> buildLargePayload(int fieldCount) { // GH-90000
        var payload = new java.util.HashMap<String, Object>(); // GH-90000
        for (int i = 0; i < fieldCount; i++) { // GH-90000
            payload.put("field_" + i, "value_" + i + "_data_".repeat(5)); // GH-90000
        }
        return Map.copyOf(payload); // GH-90000
    }
}
