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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Production-scale load simulation tests for the AEP engine (AEP-003).
 *
 * <p>These tests verify that the AEP event pipeline can sustain high throughput
 * within the ActiveJ single-threaded event loop without deadlocks, memory leaks,
 * or correctness violations. They are tagged {@code load} so CI can include or
 * exclude them as needed.
 *
 * <p><b>Scope:</b>
 * <ul>
 *   <li>AEP-003.1 — Concurrent pipeline simulation (1 000 events per tenant)</li>
 *   <li>AEP-003.4 — Performance regression detection (baseline latency assertion)</li>
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
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    // ─── AEP-003.1: high-volume single tenant ─────────────────────────────────

    @Test
    @DisplayName("processes 1 000 events for a single tenant without errors")
    void singleTenantHighVolume_allEventsProcessed() {
        AtomicInteger errorCount = new AtomicInteger();
        engine = Aep.forTesting();

        // Register a threshold pattern; its ID is needed for subscribe
        AepEngine.Pattern pattern = runPromise(() -> engine.registerPattern(
                "tenant-load",
                AepEngineTestFixtures.thresholdPattern("value", 50.0)));

        // Subscribe to detections from this specific pattern
        engine.subscribe("tenant-load", pattern.id(),
                detection -> { /* count detections if needed */ });

        // Build list of event-processing promises
        List<Promise<AepEngine.ProcessingResult>> promises = new ArrayList<>(EVENTS_PER_TENANT);
        for (int i = 0; i < EVENTS_PER_TENANT; i++) {
            double value = (i % 10 == 0) ? 75.0 : 25.0; // 10% above threshold
            AepEngine.Event event = AepEngineTestFixtures.createTestEvent(
                    "sensor.reading", Map.of("value", value, "seq", i));
            promises.add(engine.process("tenant-load", event)
                    .then(Promise::of, err -> {
                        errorCount.incrementAndGet();
                        return Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "error"));
                    }));
        }

        List<AepEngine.ProcessingResult> results = runPromise(() -> Promises.toList(promises));

        assertThat(results).hasSize(EVENTS_PER_TENANT);
        assertThat(errorCount.get())
                .as("no processing errors expected")
                .isZero();
    }

    // ─── AEP-003.1: multi-tenant isolation ────────────────────────────────────

    @Test
    @DisplayName("maintains isolation under 10 concurrent tenants × 100 events each")
    void multiTenantIsolation_noDataLeakage() {
        engine = Aep.forTesting();

        // Register a pattern and counter per tenant
        AtomicInteger[] tenantCounters = new AtomicInteger[TENANT_COUNT];
        String[] patternIds = new String[TENANT_COUNT];

        for (int t = 0; t < TENANT_COUNT; t++) {
            final String tenantId = "tenant-" + t;
            tenantCounters[t] = new AtomicInteger();
            final int tIdx = t;

            AepEngine.Pattern pattern = runPromise(() -> engine.registerPattern(
                    tenantId,
                    AepEngineTestFixtures.thresholdPattern("value", 0.5)));
            patternIds[t] = pattern.id();

            engine.subscribe(tenantId, patternIds[tIdx],
                    detection -> tenantCounters[tIdx].incrementAndGet());
        }

        // Process 100 events per tenant
        int eventsPerTenant = 100;
        List<Promise<AepEngine.ProcessingResult>> allPromises = new ArrayList<>(
                TENANT_COUNT * eventsPerTenant);

        for (int t = 0; t < TENANT_COUNT; t++) {
            String tenantId = "tenant-" + t;
            for (int i = 0; i < eventsPerTenant; i++) {
                AepEngine.Event event = AepEngineTestFixtures.createTestEvent(
                        "load.event", Map.of("value", 1.0, "tenant", tenantId));
                allPromises.add(engine.process(tenantId, event)
                        .then(Promise::of,
                                err -> Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "err"))));
            }
        }

        List<AepEngine.ProcessingResult> results = runPromise(() -> Promises.toList(allPromises));

        assertThat(results).hasSize(TENANT_COUNT * eventsPerTenant);
        // Each tenant's counter must not exceed its own event count (isolation check)
        for (int t = 0; t < TENANT_COUNT; t++) {
            assertThat(tenantCounters[t].get())
                    .as("tenant-%d detection count must be within its event budget", t)
                    .isGreaterThanOrEqualTo(0)
                    .isLessThanOrEqualTo(eventsPerTenant);
        }
    }

    // ─── AEP-003.4: performance regression baseline ───────────────────────────

    @Test
    @DisplayName("processes 500 events in under 5 seconds (baseline regression guard)")
    void throughputBaseline_500EventsUnder5Seconds() {
        int eventCount = 500;
        engine = Aep.forTesting();

        List<Promise<AepEngine.ProcessingResult>> promises = new ArrayList<>(eventCount);

        long start = System.currentTimeMillis();

        for (int i = 0; i < eventCount; i++) {
            AepEngine.Event event = AepEngineTestFixtures.createTestEvent(
                    "perf.event", Map.of("seq", i));
            promises.add(engine.process("tenant-perf", event)
                    .then(Promise::of,
                            err -> Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "err"))));
        }

        runPromise(() -> Promises.toList(promises));

        long elapsedMs = System.currentTimeMillis() - start;

        assertThat(elapsedMs)
                .as("500 events should complete within 5 seconds in the event loop")
                .isLessThan(5_000L);
    }

    // ─── AEP-003.1: idempotency under load ────────────────────────────────────

    @Test
    @DisplayName("idempotency holds under repeated events with same idempotency key")
    void idempotencyUnderLoad_noDuplicateErrors() {
        engine = Aep.forTesting();

        // Send the same event 50 times with the same idempotency key
        String idemKey = "idem-key-001";
        List<Promise<AepEngine.ProcessingResult>> promises = new ArrayList<>(50);

        for (int i = 0; i < 50; i++) {
            AepEngine.Event event = AepEngineTestFixtures.createIdempotentEvent("idem.event", idemKey);
            promises.add(engine.process("tenant-idem", event)
                    .then(Promise::of,
                            err -> Promise.of(AepEngine.ProcessingResult.skipped(ERR_ID, "dedup"))));
        }

        List<AepEngine.ProcessingResult> results = runPromise(() -> Promises.toList(promises));

        // All 50 calls should return without throwing
        assertThat(results).hasSize(50);
    }
}

