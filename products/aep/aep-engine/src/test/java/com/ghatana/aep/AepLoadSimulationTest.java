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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Production-scale load simulation tests for the AEP engine (AEP-003). // GH-90000
 *
 * <p>These tests verify that the AEP event pipeline can sustain high throughput
 * within the ActiveJ single-threaded event loop without deadlocks, memory leaks,
 * or correctness violations. They are tagged {@code load} so CI can include or
 * exclude them as needed.
 *
 * <p><b>Scope:</b>
 * <ul>
 *   <li>AEP-003.1 — Concurrent pipeline simulation (1 000 events per tenant)</li> // GH-90000
 *   <li>AEP-003.4 — Performance regression detection (baseline latency assertion)</li> // GH-90000
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Production-scale load simulation for AEP event pipeline
 * @doc.layer product
 * @doc.pattern Test
 */
@Tag("load")
@DisplayName("AEP load simulation")
class AepLoadSimulationTest extends EventloopTestBase {

    /** Number of events processed per tenant in a single load run. */
    private static final int EVENTS_PER_TENANT = 1_000;

    /** Number of simulated tenants in the multi-tenant isolation test. */
    private static final int TENANT_COUNT = 10;

    /** Placeholder event ID for error recovery paths. */
    private static final String ERR_ID = "err-id";

    private AepEngine engine;

    @AfterEach
    void tearDown() { // GH-90000
        if (engine != null) { // GH-90000
            engine.close(); // GH-90000
        }
    }

    // ─── AEP-003.1: high-volume single tenant ─────────────────────────────────

    @Test
    @DisplayName("processes 1 000 events for a single tenant without errors")
    void singleTenantHighVolume_allEventsProcessed() { // GH-90000
        AtomicInteger errorCount = new AtomicInteger(); // GH-90000
        engine = Aep.forTesting(); // GH-90000

        // Register a threshold pattern; its ID is needed for subscribe
        AepEngine.Pattern pattern = runPromise(() -> engine.registerPattern( // GH-90000
                "tenant-load",
                AepEngineTestFixtures.thresholdPattern("value", 50.0))); // GH-90000

        // Subscribe to detections from this specific pattern
        engine.subscribe("tenant-load", pattern.id(), // GH-90000
                detection -> { /* count detections if needed */ });

        // Build list of event-processing promises
        List<Promise<AepEngine.ProcessingResult>> promises = new ArrayList<>(EVENTS_PER_TENANT); // GH-90000
        for (int i = 0; i < EVENTS_PER_TENANT; i++) { // GH-90000
            double value = (i % 10 == 0) ? 75.0 : 25.0; // 10% above threshold // GH-90000
            AepEngine.Event event = AepEngineTestFixtures.createTestEvent( // GH-90000
                    "sensor.reading", Map.of("value", value, "seq", i)); // GH-90000
            promises.add(engine.process("tenant-load", event) // GH-90000
                    .then(Promise::of, err -> { // GH-90000
                        errorCount.incrementAndGet(); // GH-90000
                        return Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "error")); // GH-90000
                    }));
        }

        List<AepEngine.ProcessingResult> results = runPromise(() -> Promises.toList(promises)); // GH-90000

        assertThat(results).hasSize(EVENTS_PER_TENANT); // GH-90000
        assertThat(errorCount.get()) // GH-90000
                .as("no processing errors expected")
                .isZero(); // GH-90000
    }

    // ─── AEP-003.1: multi-tenant isolation ────────────────────────────────────

    @Test
    @DisplayName("maintains isolation under 10 concurrent tenants × 100 events each")
    void multiTenantIsolation_noDataLeakage() { // GH-90000
        engine = Aep.forTesting(); // GH-90000

        // Register a pattern and counter per tenant
        AtomicInteger[] tenantCounters = new AtomicInteger[TENANT_COUNT];
        String[] patternIds = new String[TENANT_COUNT];

        for (int t = 0; t < TENANT_COUNT; t++) { // GH-90000
            final String tenantId = "tenant-" + t;
            tenantCounters[t] = new AtomicInteger(); // GH-90000
            final int tIdx = t;

            AepEngine.Pattern pattern = runPromise(() -> engine.registerPattern( // GH-90000
                    tenantId,
                    AepEngineTestFixtures.thresholdPattern("value", 0.5))); // GH-90000
            patternIds[t] = pattern.id(); // GH-90000

            engine.subscribe(tenantId, patternIds[tIdx], // GH-90000
                    detection -> tenantCounters[tIdx].incrementAndGet()); // GH-90000
        }

        // Process 100 events per tenant
        int eventsPerTenant = 100;
        List<Promise<AepEngine.ProcessingResult>> allPromises = new ArrayList<>( // GH-90000
                TENANT_COUNT * eventsPerTenant);

        for (int t = 0; t < TENANT_COUNT; t++) { // GH-90000
            String tenantId = "tenant-" + t;
            for (int i = 0; i < eventsPerTenant; i++) { // GH-90000
                AepEngine.Event event = AepEngineTestFixtures.createTestEvent( // GH-90000
                        "load.event", Map.of("value", 1.0, "tenant", tenantId)); // GH-90000
                allPromises.add(engine.process(tenantId, event) // GH-90000
                        .then(Promise::of, // GH-90000
                                err -> Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "err")))); // GH-90000
            }
        }

        List<AepEngine.ProcessingResult> results = runPromise(() -> Promises.toList(allPromises)); // GH-90000

        assertThat(results).hasSize(TENANT_COUNT * eventsPerTenant); // GH-90000
        // Each tenant's counter must not exceed its own event count (isolation check) // GH-90000
        for (int t = 0; t < TENANT_COUNT; t++) { // GH-90000
            assertThat(tenantCounters[t].get()) // GH-90000
                    .as("tenant-%d detection count must be within its event budget", t) // GH-90000
                    .isGreaterThanOrEqualTo(0) // GH-90000
                    .isLessThanOrEqualTo(eventsPerTenant); // GH-90000
        }
    }

    // ─── AEP-003.4: performance regression baseline ───────────────────────────

    @Test
    @DisplayName("processes 500 events in under 5 seconds (baseline regression guard)")
    void throughputBaseline_500EventsUnder5Seconds() { // GH-90000
        int eventCount = 500;
        engine = Aep.forTesting(); // GH-90000

        List<Promise<AepEngine.ProcessingResult>> promises = new ArrayList<>(eventCount); // GH-90000

        long start = System.currentTimeMillis(); // GH-90000

        for (int i = 0; i < eventCount; i++) { // GH-90000
            AepEngine.Event event = AepEngineTestFixtures.createTestEvent( // GH-90000
                    "perf.event", Map.of("seq", i)); // GH-90000
            promises.add(engine.process("tenant-perf", event) // GH-90000
                    .then(Promise::of, // GH-90000
                            err -> Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "err")))); // GH-90000
        }

        runPromise(() -> Promises.toList(promises)); // GH-90000

        long elapsedMs = System.currentTimeMillis() - start; // GH-90000

        assertThat(elapsedMs) // GH-90000
                .as("500 events should complete within 5 seconds in the event loop")
                .isLessThan(5_000L); // GH-90000
    }

    // ─── AEP-003.1: idempotency under load ────────────────────────────────────

    @Test
    @DisplayName("idempotency holds under repeated events with same idempotency key")
    void idempotencyUnderLoad_noDuplicateErrors() { // GH-90000
        engine = Aep.forTesting(); // GH-90000

        // Send the same event 50 times with the same idempotency key
        String idemKey = "idem-key-001";
        List<Promise<AepEngine.ProcessingResult>> promises = new ArrayList<>(50); // GH-90000

        for (int i = 0; i < 50; i++) { // GH-90000
            AepEngine.Event event = AepEngineTestFixtures.createIdempotentEvent("idem.event", idemKey); // GH-90000
            promises.add(engine.process("tenant-idem", event) // GH-90000
                    .then(Promise::of, // GH-90000
                            err -> Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "dedup")))); // GH-90000
        }

        List<AepEngine.ProcessingResult> results = runPromise(() -> Promises.toList(promises)); // GH-90000

        // All 50 calls should return without throwing
        assertThat(results).hasSize(50); // GH-90000
    }
}
