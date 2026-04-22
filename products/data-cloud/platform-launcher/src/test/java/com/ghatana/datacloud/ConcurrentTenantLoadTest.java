/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
 * <p>50 virtual threads (one per tenant) each perform 100 writes and 100 reads // GH-90000
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
 * shared (one {@code ConcurrentHashMap} EntityStore) so the test exercises // GH-90000
 * actual concurrent access patterns.</p>
 *
 * @doc.type class
 * @doc.purpose Concurrent multi-tenant load stress test for Data-Cloud entity store
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Concurrent Multi-Tenant Load Test [GH-90000]")
class ConcurrentTenantLoadTest {

    private static final int TENANT_COUNT = 50;
    private static final int OPS_PER_TENANT = 100;
    private static final long TIMEOUT_SECONDS = 30L;
    private static final String COLLECTION = "stress-records";

    /** Shared client — one ConcurrentHashMap EntityStore accessed by all threads. */
    private DataCloudClient client;

    @BeforeEach
    void setUp() { // GH-90000
        client = DataCloud.forTesting(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        client.close(); // GH-90000
    }

    // =========================================================================
    // Load stress test: 50 tenants × 200 ops (100 writes + 100 reads) // GH-90000
    // =========================================================================

    @Test
    @DisplayName("50 concurrent tenants each complete 100 writes + 100 reads with zero cross-tenant leakage [GH-90000]")
    void concurrentTenantsCompleteWithZeroLeakage() throws InterruptedException { // GH-90000
        // ── Setup ─────────────────────────────────────────────────────────────
        CountDownLatch startGun = new CountDownLatch(1);          // burst start // GH-90000
        CountDownLatch doneGate = new CountDownLatch(TENANT_COUNT); // GH-90000

        AtomicInteger successfulWrites = new AtomicInteger(0); // GH-90000
        AtomicInteger successfulReads  = new AtomicInteger(0); // GH-90000
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>()); // GH-90000

        // Per-tenant entity count and leakage tracking (written by each thread) // GH-90000
        Map<String, Integer> entityCountPerTenant = new ConcurrentHashMap<>(); // GH-90000
        Set<String> leakageViolations = ConcurrentHashMap.newKeySet(); // GH-90000

        // ── Virtual thread pool ───────────────────────────────────────────────
        ExecutorService vThreads = Executors.newVirtualThreadPerTaskExecutor(); // GH-90000

        for (int t = 0; t < TENANT_COUNT; t++) { // GH-90000
            final String tenantId = "load-tenant-" + t;

            vThreads.submit(() -> { // GH-90000
                try {
                    startGun.await(); // synchronized burst // GH-90000

                    // Each virtual thread owns its Eventloop for ActiveJ context.
                    Eventloop eventloop = Eventloop.builder().build(); // GH-90000
                    List<String> savedIds = new ArrayList<>(OPS_PER_TENANT); // GH-90000

                    // ── Phase 1: 100 writes ───────────────────────────────────
                    for (int i = 0; i < OPS_PER_TENANT; i++) { // GH-90000
                        final int seq = i;
                        String[] idHolder = {null};

                        eventloop.submit(() -> // GH-90000
                            client.save(tenantId, COLLECTION, // GH-90000
                                    Map.of("seq", seq, // GH-90000
                                           "tenantTag", tenantId,
                                           "value", "payload-" + seq))
                                .whenComplete((entity, ex) -> { // GH-90000
                                    if (ex == null && entity != null) { // GH-90000
                                        idHolder[0] = entity.id(); // GH-90000
                                        successfulWrites.incrementAndGet(); // GH-90000
                                    } else if (ex != null) { // GH-90000
                                        errors.add(ex); // GH-90000
                                    }
                                })
                        );
                        eventloop.run(); // GH-90000

                        if (idHolder[0] != null) { // GH-90000
                            savedIds.add(idHolder[0]); // GH-90000
                        }
                    }

                    // ── Phase 2: 100 reads (findById for each saved entity) ─── // GH-90000
                    for (String savedId : savedIds) { // GH-90000
                        Object[] found = {null};

                        eventloop.submit(() -> // GH-90000
                            client.findById(tenantId, COLLECTION, savedId) // GH-90000
                                .whenComplete((opt, ex) -> { // GH-90000
                                    if (ex == null) { // GH-90000
                                        found[0] = opt;
                                        successfulReads.incrementAndGet(); // GH-90000
                                    } else {
                                        errors.add(ex); // GH-90000
                                    }
                                })
                        );
                        eventloop.run(); // GH-90000
                    }

                    // ── Phase 3: query count + leakage check ─────────────────
                    int[] count = {0};
                    eventloop.submit(() -> // GH-90000
                        client.query(tenantId, COLLECTION, Query.all()) // GH-90000
                            .whenComplete((entities, ex) -> { // GH-90000
                                if (ex == null) { // GH-90000
                                    count[0] = entities.size(); // GH-90000
                                    // detect any entity belonging to another tenant
                                    for (Entity entity : entities) { // GH-90000
                                        Object tag = entity.data().get("tenantTag [GH-90000]");
                                        if (tag != null && !tenantId.equals(tag.toString())) { // GH-90000
                                            leakageViolations.add( // GH-90000
                                                "tenant=" + tenantId
                                                + " saw entity with tenantTag=" + tag
                                                + " entityId=" + entity.id() // GH-90000
                                            );
                                        }
                                    }
                                } else {
                                    errors.add(ex); // GH-90000
                                }
                            })
                    );
                    eventloop.run(); // GH-90000

                    entityCountPerTenant.put(tenantId, count[0]); // GH-90000

                } catch (Exception ex) { // GH-90000
                    errors.add(ex); // GH-90000
                } finally {
                    doneGate.countDown(); // GH-90000
                }
            });
        }

        // ── Fire ─────────────────────────────────────────────────────────────
        startGun.countDown(); // GH-90000

        // ── Wait ─────────────────────────────────────────────────────────────
        boolean allDone = doneGate.await(TIMEOUT_SECONDS, TimeUnit.SECONDS); // GH-90000
        vThreads.shutdown(); // GH-90000
        vThreads.awaitTermination(5, TimeUnit.SECONDS); // GH-90000

        // ── Assertions ───────────────────────────────────────────────────────

        assertThat(allDone) // GH-90000
            .as("All %d tenant threads must complete within %ds", TENANT_COUNT, TIMEOUT_SECONDS) // GH-90000
            .isTrue(); // GH-90000

        assertThat(errors) // GH-90000
            .as("No errors should occur during concurrent load [GH-90000]")
            .isEmpty(); // GH-90000

        assertThat(leakageViolations) // GH-90000
            .as("Zero cross-tenant data leakage violations [GH-90000]")
            .isEmpty(); // GH-90000

        assertThat(successfulWrites.get()) // GH-90000
            .as("All writes must succeed: expected %d", TENANT_COUNT * OPS_PER_TENANT) // GH-90000
            .isEqualTo(TENANT_COUNT * OPS_PER_TENANT); // GH-90000

        // Each tenant must see exactly OPS_PER_TENANT entities in its collection
        entityCountPerTenant.forEach((tenantId, count) -> // GH-90000
            assertThat(count) // GH-90000
                .as("Tenant '%s' must have exactly %d entities", tenantId, OPS_PER_TENANT) // GH-90000
                .isEqualTo(OPS_PER_TENANT) // GH-90000
        );

        assertThat(entityCountPerTenant) // GH-90000
            .as("Entity count map must contain all %d tenants", TENANT_COUNT) // GH-90000
            .hasSize(TENANT_COUNT); // GH-90000
    }

    // =========================================================================
    // Stress test: mixed reads/writes with concurrent tenant registration
    // =========================================================================

    @Test
    @DisplayName("Concurrent saves across 50 tenants produce no entity id collisions [GH-90000]")
    void concurrentSavesProduceNoIdCollisions() throws InterruptedException { // GH-90000
        Set<String> allIds = ConcurrentHashMap.newKeySet(); // GH-90000
        CountDownLatch startGun = new CountDownLatch(1); // GH-90000
        CountDownLatch doneGate = new CountDownLatch(TENANT_COUNT); // GH-90000
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>()); // GH-90000

        ExecutorService vThreads = Executors.newVirtualThreadPerTaskExecutor(); // GH-90000

        for (int t = 0; t < TENANT_COUNT; t++) { // GH-90000
            final String tenantId = "id-tenant-" + t;

            vThreads.submit(() -> { // GH-90000
                try {
                    startGun.await(); // GH-90000
                    Eventloop eventloop = Eventloop.builder().build(); // GH-90000

                    for (int i = 0; i < OPS_PER_TENANT; i++) { // GH-90000
                        final int seq = i;
                        String[] idHolder = {null};

                        eventloop.submit(() -> // GH-90000
                            client.save(tenantId, "id-test", // GH-90000
                                    Map.of("seq", seq, "tenant", tenantId)) // GH-90000
                                .whenComplete((entity, ex) -> { // GH-90000
                                    if (ex == null && entity != null) { // GH-90000
                                        idHolder[0] = entity.id(); // GH-90000
                                    } else if (ex != null) { // GH-90000
                                        errors.add(ex); // GH-90000
                                    }
                                })
                        );
                        eventloop.run(); // GH-90000

                        if (idHolder[0] != null) { // GH-90000
                            allIds.add(idHolder[0]); // GH-90000
                        }
                    }
                } catch (Exception ex) { // GH-90000
                    errors.add(ex); // GH-90000
                } finally {
                    doneGate.countDown(); // GH-90000
                }
            });
        }

        startGun.countDown(); // GH-90000
        boolean done = doneGate.await(TIMEOUT_SECONDS, TimeUnit.SECONDS); // GH-90000
        vThreads.shutdown(); // GH-90000
        vThreads.awaitTermination(5, TimeUnit.SECONDS); // GH-90000

        assertThat(done) // GH-90000
            .as("All threads must finish within %d seconds", TIMEOUT_SECONDS) // GH-90000
            .isTrue(); // GH-90000

        assertThat(errors).as("No errors during concurrent saves [GH-90000]").isEmpty();

        // Every saved entity across all tenants must have a globally unique ID
        // (UUIDs are used, so collisions would be catastrophic) // GH-90000
        assertThat(allIds) // GH-90000
            .as("All entity IDs must be globally unique — no collisions across %d tenants × %d ops", // GH-90000
                TENANT_COUNT, OPS_PER_TENANT)
            .hasSize(TENANT_COUNT * OPS_PER_TENANT); // GH-90000
    }
}
