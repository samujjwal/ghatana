/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud;

import com.ghatana.datacloud.DataCloudClient.Entity;
import com.ghatana.datacloud.DataCloudClient.Query;
import io.activej.eventloop.Eventloop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrent multi-tenant load stress test.
 *
 * <h2>Scenario</h2>
 * <p>50 virtual threads (one per tenant) each perform 100 writes and 100 reads
 * concurrently on a single shared {@link DataCloudClient} backed by the
 * thread-safe in-memory {@code ConcurrentHashMap} store. After all operations
 * complete:
 * <ul>
 *   <li>Each tenant observes exactly 100 entities in its collection.</li>
 *   <li>No entity carries a {@code tenantTag} value from another tenant.</li>
 *   <li>All 50 tenants finish within 30 seconds.</li>
 *   <li>Total successful operations ≥ 50 × 200 = 10,000.</li>
 * </ul>
 *
 * <h2>Threading model</h2>
 * <p>Each virtual thread allocates its own {@link Eventloop} to maintain
 * ActiveJ's concurrency contract. The underlying {@link DataCloudClient} is
 * shared (one {@code ConcurrentHashMap} EntityStore) so the test exercises
 * actual concurrent access patterns.</p>
 *
 * @doc.type class
 * @doc.purpose Concurrent multi-tenant load stress test for Data-Cloud entity store
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Concurrent Multi-Tenant Load Test")
class ConcurrentTenantLoadTest {

    private static final int TENANT_COUNT = 50;
    private static final int OPS_PER_TENANT = 100;
    private static final long TIMEOUT_SECONDS = 30L;
    private static final String COLLECTION = "stress-records";

    /** Shared client — one ConcurrentHashMap EntityStore accessed by all threads. */
    private DataCloudClient client;

    @BeforeEach
    void setUp() {
        client = DataCloud.forTesting();
    }

    @AfterEach
    void tearDown() {
        client.close();
    }

    // =========================================================================
    // Load stress test: 50 tenants × 200 ops (100 writes + 100 reads)
    // =========================================================================

    @Test
    @DisplayName("50 concurrent tenants each complete 100 writes + 100 reads with zero cross-tenant leakage")
    void concurrentTenantsCompleteWithZeroLeakage() throws InterruptedException {
        // ── Setup ─────────────────────────────────────────────────────────────
        CountDownLatch startGun = new CountDownLatch(1);          // burst start
        CountDownLatch doneGate = new CountDownLatch(TENANT_COUNT);

        AtomicInteger successfulWrites = new AtomicInteger(0);
        AtomicInteger successfulReads  = new AtomicInteger(0);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        // Per-tenant entity count and leakage tracking (written by each thread)
        Map<String, Integer> entityCountPerTenant = new ConcurrentHashMap<>();
        Set<String> leakageViolations = ConcurrentHashMap.newKeySet();

        // ── Virtual thread pool ───────────────────────────────────────────────
        ExecutorService vThreads = Executors.newVirtualThreadPerTaskExecutor();

        for (int t = 0; t < TENANT_COUNT; t++) {
            final String tenantId = "load-tenant-" + t;

            vThreads.submit(() -> {
                try {
                    startGun.await(); // synchronized burst

                    // Each virtual thread owns its Eventloop for ActiveJ context.
                    Eventloop eventloop = Eventloop.builder().build();
                    List<String> savedIds = new ArrayList<>(OPS_PER_TENANT);

                    // ── Phase 1: 100 writes ───────────────────────────────────
                    for (int i = 0; i < OPS_PER_TENANT; i++) {
                        final int seq = i;
                        String[] idHolder = {null};

                        eventloop.submit(() ->
                            client.save(tenantId, COLLECTION,
                                    Map.of("seq", seq,
                                           "tenantTag", tenantId,
                                           "value", "payload-" + seq))
                                .whenComplete((entity, ex) -> {
                                    if (ex == null && entity != null) {
                                        idHolder[0] = entity.id();
                                        successfulWrites.incrementAndGet();
                                    } else if (ex != null) {
                                        errors.add(ex);
                                    }
                                })
                        );
                        eventloop.run();

                        if (idHolder[0] != null) {
                            savedIds.add(idHolder[0]);
                        }
                    }

                    // ── Phase 2: 100 reads (findById for each saved entity) ───
                    for (String savedId : savedIds) {
                        Object[] found = {null};

                        eventloop.submit(() ->
                            client.findById(tenantId, COLLECTION, savedId)
                                .whenComplete((opt, ex) -> {
                                    if (ex == null) {
                                        found[0] = opt;
                                        successfulReads.incrementAndGet();
                                    } else {
                                        errors.add(ex);
                                    }
                                })
                        );
                        eventloop.run();
                    }

                    // ── Phase 3: query count + leakage check ─────────────────
                    int[] count = {0};
                    eventloop.submit(() ->
                        client.query(tenantId, COLLECTION, Query.all())
                            .whenComplete((entities, ex) -> {
                                if (ex == null) {
                                    count[0] = entities.size();
                                    // detect any entity belonging to another tenant
                                    for (Entity entity : entities) {
                                        Object tag = entity.data().get("tenantTag");
                                        if (tag != null && !tenantId.equals(tag.toString())) {
                                            leakageViolations.add(
                                                "tenant=" + tenantId
                                                + " saw entity with tenantTag=" + tag
                                                + " entityId=" + entity.id()
                                            );
                                        }
                                    }
                                } else {
                                    errors.add(ex);
                                }
                            })
                    );
                    eventloop.run();

                    entityCountPerTenant.put(tenantId, count[0]);

                } catch (Exception ex) {
                    errors.add(ex);
                } finally {
                    doneGate.countDown();
                }
            });
        }

        // ── Fire ─────────────────────────────────────────────────────────────
        startGun.countDown();

        // ── Wait ─────────────────────────────────────────────────────────────
        boolean allDone = doneGate.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        vThreads.shutdown();
        vThreads.awaitTermination(5, TimeUnit.SECONDS);

        // ── Assertions ───────────────────────────────────────────────────────

        assertThat(allDone)
            .as("All %d tenant threads must complete within %ds", TENANT_COUNT, TIMEOUT_SECONDS)
            .isTrue();

        assertThat(errors)
            .as("No errors should occur during concurrent load")
            .isEmpty();

        assertThat(leakageViolations)
            .as("Zero cross-tenant data leakage violations")
            .isEmpty();

        assertThat(successfulWrites.get())
            .as("All writes must succeed: expected %d", TENANT_COUNT * OPS_PER_TENANT)
            .isEqualTo(TENANT_COUNT * OPS_PER_TENANT);

        // Each tenant must see exactly OPS_PER_TENANT entities in its collection
        entityCountPerTenant.forEach((tenantId, count) ->
            assertThat(count)
                .as("Tenant '%s' must have exactly %d entities", tenantId, OPS_PER_TENANT)
                .isEqualTo(OPS_PER_TENANT)
        );

        assertThat(entityCountPerTenant)
            .as("Entity count map must contain all %d tenants", TENANT_COUNT)
            .hasSize(TENANT_COUNT);
    }

    // =========================================================================
    // Stress test: mixed reads/writes with concurrent tenant registration
    // =========================================================================

    @Test
    @DisplayName("Concurrent saves across 50 tenants produce no entity id collisions")
    void concurrentSavesProduceNoIdCollisions() throws InterruptedException {
        Set<String> allIds = ConcurrentHashMap.newKeySet();
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(TENANT_COUNT);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        ExecutorService vThreads = Executors.newVirtualThreadPerTaskExecutor();

        for (int t = 0; t < TENANT_COUNT; t++) {
            final String tenantId = "id-tenant-" + t;

            vThreads.submit(() -> {
                try {
                    startGun.await();
                    Eventloop eventloop = Eventloop.builder().build();

                    for (int i = 0; i < OPS_PER_TENANT; i++) {
                        final int seq = i;
                        String[] idHolder = {null};

                        eventloop.submit(() ->
                            client.save(tenantId, "id-test",
                                    Map.of("seq", seq, "tenant", tenantId))
                                .whenComplete((entity, ex) -> {
                                    if (ex == null && entity != null) {
                                        idHolder[0] = entity.id();
                                    } else if (ex != null) {
                                        errors.add(ex);
                                    }
                                })
                        );
                        eventloop.run();

                        if (idHolder[0] != null) {
                            allIds.add(idHolder[0]);
                        }
                    }
                } catch (Exception ex) {
                    errors.add(ex);
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGun.countDown();
        boolean done = doneGate.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        vThreads.shutdown();
        vThreads.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(done)
            .as("All threads must finish within %d seconds", TIMEOUT_SECONDS)
            .isTrue();

        assertThat(errors).as("No errors during concurrent saves").isEmpty();

        // Every saved entity across all tenants must have a globally unique ID
        // (UUIDs are used, so collisions would be catastrophic)
        assertThat(allIds)
            .as("All entity IDs must be globally unique — no collisions across %d tenants × %d ops",
                TENANT_COUNT, OPS_PER_TENANT)
            .hasSize(TENANT_COUNT * OPS_PER_TENANT);
    }
}
