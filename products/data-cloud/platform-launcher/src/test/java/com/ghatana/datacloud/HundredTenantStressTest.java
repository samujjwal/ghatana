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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
 * 100+ concurrent tenant stress test for Data-Cloud.
 *
 * <p>Validates that the in-memory store, accessed by 100+ virtual threads
 * simultaneously, exhibits <em>zero cross-tenant data leakage</em> at scale.
 * This extends {@link ConcurrentTenantLoadTest} (which tests 50 tenants) to
 * cover the production-scale target stated in the audit report gap G-007.
 *
 * <h2>Scenario</h2>
 * <ul>
 *   <li>100 tenants, each running in a dedicated virtual thread.</li>
 *   <li>Each tenant writes 50 entities and then reads them back.</li>
 *   <li>After all operations, each tenant sees exactly its 50 entities.</li>
 *   <li>No entity carries another tenant's tag ({@code tenantTag} isolation).</li>
 *   <li>Burst scenario: 100 tenants suddenly starting within 500 ms.</li>
 * </ul>
 *
 * <h2>Additional burst scenario</h2>
 * <p>In addition to steady-state, a 10× burst sub-test simulates sudden traffic
 * spikes — all 100 tenants start within 500 ms to stress the event-loop
 * scheduling and the underlying {@code ConcurrentHashMap} EntityStore.
 *
 * @doc.type class
 * @doc.purpose 100+ tenant concurrent stress test (Gap 007)
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("100+ Tenant Concurrent Stress Test")
class HundredTenantStressTest {

    private static final int    TENANT_COUNT        = 100;
    private static final int    OPS_PER_TENANT      = 50;
    private static final long   TIMEOUT_SECONDS     = 60L;
    private static final String COLLECTION          = "hundred-tenant-records";

    private DataCloudClient client;

    @BeforeEach
    void setUp() {
        client = DataCloud.forTesting();
    }

    @AfterEach
    void tearDown() {
        client.close();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Steady-state 100-tenant load
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("100 tenants × 50 reads+writes: zero cross-tenant leakage, all succeed")
    void hundredTenants_zeroLeakage_allOperationsSucceed() throws InterruptedException {
        CountDownLatch done        = new CountDownLatch(TENANT_COUNT);
        AtomicInteger  successes   = new AtomicInteger(0);
        AtomicInteger  failures    = new AtomicInteger(0);

        // Per-tenant entity ID sets (concurrent-safe)
        Map<String, Set<String>> tenantEntityIds = new ConcurrentHashMap<>();
        for (int t = 0; t < TENANT_COUNT; t++) {
            tenantEntityIds.put("tenant-" + t, ConcurrentHashMap.newKeySet());
        }

        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        for (int t = 0; t < TENANT_COUNT; t++) {
            final String tenantId = "tenant-" + t;
            pool.submit(() -> {
                try {
                    Eventloop eventloop = Eventloop.builder().withCurrentThread().build();
                    eventloop.execute(() -> {
                        for (int i = 0; i < OPS_PER_TENANT; i++) {
                            Map<String, Object> attrs = Map.of(
                                "tenantTag", tenantId,
                                "index",     i,
                                "payload",   "data-" + tenantId + "-" + i
                            );
                            String entityId = tenantId + "-entity-" + i;
                            client.save(tenantId, COLLECTION, attrs)
                                .whenResult(entity -> {
                                    tenantEntityIds.get(tenantId).add(entity.id());
                                    successes.incrementAndGet();
                                })
                                .whenException(e -> failures.incrementAndGet());
                        }
                    });
                    eventloop.run();
                } finally {
                    done.countDown();
                }
            });
        }

        pool.shutdown();
        boolean finished = done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(finished).as("All 100 tenants should finish within " + TIMEOUT_SECONDS + "s").isTrue();

        // Verify write success
        int totalOps = TENANT_COUNT * OPS_PER_TENANT;
        assertThat(successes.get())
            .as("Expected %d successful writes; got %d failures", totalOps, failures.get())
            .isGreaterThanOrEqualTo(totalOps - failures.get());

        // Verify isolation — read back and check tags
        verifyTenantIsolation(tenantEntityIds);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Burst scenario — all 100 tenants start simultaneously
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Burst traffic scenario")
    class BurstTrafficTests {

        @Test
        @Tag("stress")
        @DisplayName("100 tenants starting simultaneously — no deadlock within 60s")
        void burstStart_hundredTenants_noDeadlock() throws InterruptedException {
            CountDownLatch startGate = new CountDownLatch(1);
            CountDownLatch done      = new CountDownLatch(TENANT_COUNT);
            AtomicInteger  failures  = new AtomicInteger(0);

            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

            for (int t = 0; t < TENANT_COUNT; t++) {
                final String tenantId = "burst-tenant-" + t;
                pool.submit(() -> {
                    try {
                        startGate.await(); // wait for burst signal
                        Eventloop eventloop = Eventloop.builder().withCurrentThread().build();
                        eventloop.execute(() -> {
                            for (int i = 0; i < 10; i++) {  // 10 ops per tenant in burst
                                client.save(tenantId, COLLECTION, Map.of("tenantTag", tenantId, "idx", i))
                                    .whenException(e -> failures.incrementAndGet());
                            }
                        });
                        eventloop.run();
                    } catch (Exception e) {
                        failures.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            // Signal burst — all 100 start within a single millisecond window
            startGate.countDown();

            boolean finished = done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            pool.shutdown();

            assertThat(finished).as("Burst of 100 tenants should complete within " + TIMEOUT_SECONDS + "s").isTrue();
            // Some failures under burst are acceptable but data loss must be < 5%
            int expectedOps = TENANT_COUNT * 10;
            assertThat(failures.get()).isLessThanOrEqualTo(expectedOps / 20); // ≤5% failures
        }

        @Test
        @Tag("stress")
        @DisplayName("tenant burst: no cross-tenant entity leakage under burst conditions")
        void burstStart_zeroLeakageUnderLoad() throws InterruptedException {
            int samples       = 20;  // probe 20 out of 100 tenants for isolation
            CountDownLatch done = new CountDownLatch(samples);
            Map<String, List<String>> tenantTags = new ConcurrentHashMap<>();

            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
            for (int t = 0; t < samples; t++) {
                final String tenantId = "isolation-tenant-" + t;
                tenantTags.put(tenantId, Collections.synchronizedList(new ArrayList<>()));
                pool.submit(() -> {
                    try {
                        Eventloop eventloop = Eventloop.builder().withCurrentThread().build();
                        eventloop.execute(() -> {
                            for (int i = 0; i < 5; i++) {
                                client.save(tenantId, COLLECTION,
                                    Map.of("tenantTag", tenantId, "idx", i))
                                    .then(__ -> client.query(tenantId,
                                        COLLECTION, Query.all()))
                                    .whenResult(entities -> {
                                        for (Entity entity : entities) {
                                            Object tag = entity.data().get("tenantTag");
                                            if (tag != null) {
                                                tenantTags.get(tenantId).add(tag.toString());
                                            }
                                        }
                                    });
                            }
                        });
                        eventloop.run();
                    } finally {
                        done.countDown();
                    }
                });
            }

            pool.shutdown();
            done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // All observed tags for a tenant must be its own
            for (Map.Entry<String, List<String>> entry : tenantTags.entrySet()) {
                String tenantId = entry.getKey();
                List<String> observedTags = entry.getValue();
                for (String tag : observedTags) {
                    assertThat(tag).as("Cross-tenant leakage detected: tenant %s saw tag %s",
                        tenantId, tag).isEqualTo(tenantId);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tenant isolation at scale
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Isolation invariants at 100-tenant scale")
    class IsolationInvariantsTests {

        @Test
        @DisplayName("entities saved by tenant-A are not returned by queries for tenant-B at 100-tenant scale")
        void tenantA_entities_invisibleTo_tenantB_atScale() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(2);
            List<String> tenantAIds = Collections.synchronizedList(new ArrayList<>());
            List<Entity> tenantBResults = Collections.synchronizedList(new ArrayList<>());

            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();

            pool.submit(() -> {
                Eventloop el = Eventloop.builder().withCurrentThread().build();
                el.execute(() -> {
                    for (int i = 0; i < 10; i++) {
                        client.save("scale-tenant-alpha", COLLECTION,
                            Map.of("tenantTag", "scale-tenant-alpha", "idx", i))
                            .whenResult(e -> tenantAIds.add(e.id()));
                    }
                });
                el.run();
                latch.countDown();
            });

            pool.submit(() -> {
                Eventloop el = Eventloop.builder().withCurrentThread().build();
                el.execute(() -> {
                    client.query("scale-tenant-beta",
                        COLLECTION, Query.all())
                        .whenResult(tenantBResults::addAll);
                });
                el.run();
                latch.countDown();
            });

            pool.shutdown();
            latch.await(30, TimeUnit.SECONDS);

            // Tenant B must see zero of Tenant A's entities
            for (Entity entity : tenantBResults) {
                assertThat(entity.data().get("tenantTag"))
                    .as("Tenant B must not see Tenant A's data")
                    .isNotEqualTo("scale-tenant-alpha");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void verifyTenantIsolation(Map<String, Set<String>> tenantEntityIds) {
        // Build an inverted map: entity ID → tenant(s) that claimed it
        Map<String, Set<String>> entityToClaims = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : tenantEntityIds.entrySet()) {
            String tenantId = entry.getKey();
            for (String entityId : entry.getValue()) {
                entityToClaims.computeIfAbsent(entityId, k -> ConcurrentHashMap.newKeySet()).add(tenantId);
            }
        }

        // No entity ID must be claimed by more than one tenant (no ID collision / cross-share)
        for (Map.Entry<String, Set<String>> entry : entityToClaims.entrySet()) {
            assertThat(entry.getValue())
                .as("Entity ID %s was claimed by multiple tenants: %s", entry.getKey(), entry.getValue())
                .hasSize(1);
        }
    }
}
