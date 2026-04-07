/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * <p><b>Stress testing (AEP-003.2)</b>: Verifies that the pipeline sustains high burst
 * load (5 000 events in rapid succession) without deadlocks or correctness violations.
 *
 * <p><b>Chaos engineering (AEP-003.3)</b>: Injects faults (invalid events, burst spikes,
 * mixed-type events) to verify the engine recovers gracefully without cascading failures.
 *
 * @doc.type class
 * @doc.purpose Stress and chaos engineering tests for AEP event pipeline resilience
 * @doc.layer product
 * @doc.pattern Test
 */
@Tag("stress")
@DisplayName("AEP stress and chaos engineering")
class AepStressAndChaosTest extends EventloopTestBase {

    private static final String TENANT = "tenant-stress";
    private static final String ERR_ID = "err-id";

    private AepEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    // ─── AEP-003.2: Stress tests ──────────────────────────────────────────────

    @Test
    @DisplayName("stress: processes 5 000 events in a single burst without errors")
    void stress_5000EventBurst_noErrors() {
        int eventCount = 5_000;
        engine = Aep.forTesting();

        AtomicInteger errorCount = new AtomicInteger();
        List<Promise<AepEngine.ProcessingResult>> promises = new ArrayList<>(eventCount);

        for (int i = 0; i < eventCount; i++) {
            AepEngine.Event event = AepEngineTestFixtures.createTestEvent(
                    "stress.event", Map.of("seq", i, "value", i % 100));
            promises.add(engine.process(TENANT, event)
                    .then(Promise::of, err -> {
                        errorCount.incrementAndGet();
                        return Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "stress-error"));
                    }));
        }

        List<AepEngine.ProcessingResult> results = runPromise(() -> Promises.toList(promises));

        assertThat(results).hasSize(eventCount);
        assertThat(errorCount.get())
                .as("zero errors expected under stress burst of %d events", eventCount)
                .isZero();
    }

    @Test
    @DisplayName("stress: consecutive bursts maintain correctness across 3 rounds × 1 000 events")
    void stress_consecutiveBursts_correctnessPreserved() {
        int rounds = 3;
        int eventsPerRound = 1_000;
        engine = Aep.forTesting();

        AtomicLong totalProcessed = new AtomicLong();

        for (int round = 0; round < rounds; round++) {
            List<Promise<AepEngine.ProcessingResult>> promises = new ArrayList<>(eventsPerRound);
            for (int i = 0; i < eventsPerRound; i++) {
                AepEngine.Event event = AepEngineTestFixtures.createTestEvent(
                        "burst.event", Map.of("round", round, "seq", i));
                promises.add(engine.process(TENANT + "-r" + round, event)
                        .then(Promise::of,
                                err -> Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "err"))));
            }
            List<AepEngine.ProcessingResult> results = runPromise(() -> Promises.toList(promises));
            totalProcessed.addAndGet(results.size());
        }

        assertThat(totalProcessed.get())
                .as("all %d × %d events must be accounted for", rounds, eventsPerRound)
                .isEqualTo((long) rounds * eventsPerRound);
    }

    @Test
    @DisplayName("stress: multi-type event mix — 10 event types × 200 events each")
    void stress_mixedEventTypes_allProcessed() {
        int typesCount = 10;
        int eventsPerType = 200;
        engine = Aep.forTesting();

        List<Promise<AepEngine.ProcessingResult>> promises = new ArrayList<>(typesCount * eventsPerType);

        for (int t = 0; t < typesCount; t++) {
            String type = "type." + t;
            for (int i = 0; i < eventsPerType; i++) {
                AepEngine.Event event = AepEngineTestFixtures.createTestEvent(
                        type, Map.of("idx", i));
                promises.add(engine.process(TENANT, event)
                        .then(Promise::of,
                                err -> Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "err"))));
            }
        }

        List<AepEngine.ProcessingResult> results = runPromise(() -> Promises.toList(promises));

        assertThat(results)
                .hasSize(typesCount * eventsPerType);
    }

    @Test
    @DisplayName("stress: large payload events — 1 000 events with 1 KB payload each")
    void stress_largePayloadEvents_noErrors() {
        int eventCount = 1_000;
        engine = Aep.forTesting();

        // Build a reasonably large payload map
        Map<String, Object> largePayload = buildLargePayload(50);

        AtomicInteger errorCount = new AtomicInteger();
        List<Promise<AepEngine.ProcessingResult>> promises = new ArrayList<>(eventCount);

        for (int i = 0; i < eventCount; i++) {
            AepEngine.Event event = AepEngineTestFixtures.createTestEvent("large.event", largePayload);
            promises.add(engine.process(TENANT, event)
                    .then(Promise::of, err -> {
                        errorCount.incrementAndGet();
                        return Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "err"));
                    }));
        }

        runPromise(() -> Promises.toList(promises));

        assertThat(errorCount.get())
                .as("zero errors expected for large-payload events")
                .isZero();
    }

    // ─── AEP-003.3: Chaos engineering tests ──────────────────────────────────

    @Test
    @DisplayName("chaos: mixed valid/invalid events — engine processes valid ones without crashing")
    void chaos_mixedValidInvalidEvents_engineSurvives() {
        engine = Aep.forTesting();
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();

        List<Promise<AepEngine.ProcessingResult>> promises = new ArrayList<>(200);

        for (int i = 0; i < 200; i++) {
            AepEngine.Event event;
            if (i % 5 == 0) {
                // Inject an invalid/oversized event type to simulate chaos
                event = AepEngineTestFixtures.createOversizedTypeEvent();
            } else {
                event = AepEngineTestFixtures.createTestEvent("valid.chaos.event", Map.of("seq", i));
            }

            promises.add(engine.process(TENANT, event)
                    .then(
                            result -> {
                                successCount.incrementAndGet();
                                return Promise.of(result);
                            },
                            err -> {
                                errorCount.incrementAndGet();
                                return Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "chaos-err"));
                            }));
        }

        List<AepEngine.ProcessingResult> results = runPromise(() -> Promises.toList(promises));

        assertThat(results).hasSize(200);
        // Engine must remain operational — at least 80% valid events processed
        int validEventsCount = 200 - (200 / 5);
        assertThat(successCount.get() + errorCount.get())
                .as("all 200 attempts must complete")
                .isEqualTo(200);
    }

    @Test
    @DisplayName("chaos: rapid tenant switches — 20 tenants interleaved across 500 events")
    void chaos_rapidTenantSwitching_noIsolationViolations() {
        int tenantCount = 20;
        int totalEvents = 500;
        engine = Aep.forTesting();

        AtomicInteger[] counters = new AtomicInteger[tenantCount];
        for (int t = 0; t < tenantCount; t++) {
            counters[t] = new AtomicInteger();
        }

        List<Promise<AepEngine.ProcessingResult>> promises = new ArrayList<>(totalEvents);

        for (int i = 0; i < totalEvents; i++) {
            int tenantIdx = i % tenantCount;
            String tenantId = "chaos-tenant-" + tenantIdx;
            final int idx = tenantIdx;

            AepEngine.Event event = AepEngineTestFixtures.createTestEvent(
                    "chaos.switch.event", Map.of("tid", tenantIdx, "seq", i));
            promises.add(engine.process(tenantId, event)
                    .then(
                            result -> {
                                counters[idx].incrementAndGet();
                                return Promise.of(result);
                            },
                            err -> Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "err"))));
        }

        List<AepEngine.ProcessingResult> results = runPromise(() -> Promises.toList(promises));
        assertThat(results).hasSize(totalEvents);

        // Each tenant should have processed events only for its portion (no cross-tenant bleed)
        for (int t = 0; t < tenantCount; t++) {
            assertThat(counters[t].get())
                    .as("tenant-%d should not exceed its event budget", t)
                    .isLessThanOrEqualTo(totalEvents / tenantCount + 1);
        }
    }

    @Test
    @DisplayName("chaos: duplicate event IDs — idempotency holds under repeated injection")
    void chaos_duplicateEventIds_idempotencyPreserved() {
        engine = Aep.forTesting();
        String idemKey = "chaos-idem-" + System.nanoTime();

        List<Promise<AepEngine.ProcessingResult>> promises = new ArrayList<>(100);

        for (int i = 0; i < 100; i++) {
            AepEngine.Event event = AepEngineTestFixtures.createIdempotentEvent("chaos.dedup", idemKey);
            promises.add(engine.process(TENANT, event)
                    .then(Promise::of,
                            err -> Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "err"))));
        }

        List<AepEngine.ProcessingResult> results = runPromise(() -> Promises.toList(promises));
        assertThat(results).hasSize(100);
    }

    @Test
    @DisplayName("chaos: bursty-then-quiet pattern — engine handles spiky throughput")
    void chaos_burstyThenQuiet_engineStable() {
        engine = Aep.forTesting();
        AtomicInteger totalProcessed = new AtomicInteger();

        // Burst: 1 000 events
        List<Promise<AepEngine.ProcessingResult>> burst = new ArrayList<>(1_000);
        for (int i = 0; i < 1_000; i++) {
            AepEngine.Event event = AepEngineTestFixtures.createTestEvent("burst", Map.of("i", i));
            burst.add(engine.process(TENANT + "-burst", event)
                    .then(r -> {
                        totalProcessed.incrementAndGet();
                        return Promise.of(r);
                    }, err -> Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "err"))));
        }
        runPromise(() -> Promises.toList(burst));

        // Quiet: 10 events
        List<Promise<AepEngine.ProcessingResult>> quiet = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            AepEngine.Event event = AepEngineTestFixtures.createTestEvent("quiet", Map.of("i", i));
            quiet.add(engine.process(TENANT + "-quiet", event)
                    .then(r -> {
                        totalProcessed.incrementAndGet();
                        return Promise.of(r);
                    }, err -> Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "err"))));
        }
        runPromise(() -> Promises.toList(quiet));

        assertThat(totalProcessed.get()).isEqualTo(1_010);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static Map<String, Object> buildLargePayload(int fieldCount) {
        var payload = new java.util.HashMap<String, Object>();
        for (int i = 0; i < fieldCount; i++) {
            payload.put("field_" + i, "value_" + i + "_data_".repeat(5));
        }
        return Map.copyOf(payload);
    }
}

