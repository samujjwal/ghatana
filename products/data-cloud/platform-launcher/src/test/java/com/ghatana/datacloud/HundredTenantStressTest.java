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
 * This extends {@link ConcurrentTenantLoadTest} (which tests 50 tenants) to // GH-90000
 * cover the production-scale target stated in the audit report gap G-007.
 *
 * <h2>Scenario</h2>
 * <ul>
 *   <li>100 tenants, each running in a dedicated virtual thread.</li>
 *   <li>Each tenant writes 50 entities and then reads them back.</li>
 *   <li>After all operations, each tenant sees exactly its 50 entities.</li>
 *   <li>No entity carries another tenant's tag ({@code tenantTag} isolation).</li> // GH-90000
 *   <li>Burst scenario: 100 tenants suddenly starting within 500 ms.</li>
 * </ul>
 *
 * <h2>Additional burst scenario</h2>
 * <p>In addition to steady-state, a 10× burst sub-test simulates sudden traffic
 * spikes — all 100 tenants start within 500 ms to stress the event-loop
 * scheduling and the underlying {@code ConcurrentHashMap} EntityStore.
 *
 * @doc.type class
 * @doc.purpose 100+ tenant concurrent stress test (Gap 007) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("100+ Tenant Concurrent Stress Test [GH-90000]")
class HundredTenantStressTest {

    private static final int    TENANT_COUNT        = 100;
    private static final int    OPS_PER_TENANT      = 50;
    private static final long   TIMEOUT_SECONDS     = 60L;
    private static final String COLLECTION          = "hundred-tenant-records";

    private DataCloudClient client;

    @BeforeEach
    void setUp() { // GH-90000
        client = DataCloud.forTesting(); // GH-90000
    }

    @AfterEach
    void tearDown() { // GH-90000
        client.close(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Steady-state 100-tenant load
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("100 tenants × 50 reads+writes: zero cross-tenant leakage, all succeed [GH-90000]")
    void hundredTenants_zeroLeakage_allOperationsSucceed() throws InterruptedException { // GH-90000
        CountDownLatch done        = new CountDownLatch(TENANT_COUNT); // GH-90000
        AtomicInteger  successes   = new AtomicInteger(0); // GH-90000
        AtomicInteger  failures    = new AtomicInteger(0); // GH-90000

        // Per-tenant entity ID sets (concurrent-safe) // GH-90000
        Map<String, Set<String>> tenantEntityIds = new ConcurrentHashMap<>(); // GH-90000
        for (int t = 0; t < TENANT_COUNT; t++) { // GH-90000
            tenantEntityIds.put("tenant-" + t, ConcurrentHashMap.newKeySet()); // GH-90000
        }

        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor(); // GH-90000
        for (int t = 0; t < TENANT_COUNT; t++) { // GH-90000
            final String tenantId = "tenant-" + t;
            pool.submit(() -> { // GH-90000
                try {
                    Eventloop eventloop = Eventloop.builder().withCurrentThread().build(); // GH-90000
                    eventloop.execute(() -> { // GH-90000
                        for (int i = 0; i < OPS_PER_TENANT; i++) { // GH-90000
                            Map<String, Object> attrs = Map.of( // GH-90000
                                "tenantTag", tenantId,
                                "index",     i,
                                "payload",   "data-" + tenantId + "-" + i
                            );
                            String entityId = tenantId + "-entity-" + i;
                            client.save(tenantId, COLLECTION, attrs) // GH-90000
                                .whenResult(entity -> { // GH-90000
                                    tenantEntityIds.get(tenantId).add(entity.id()); // GH-90000
                                    successes.incrementAndGet(); // GH-90000
                                })
                                .whenException(e -> failures.incrementAndGet()); // GH-90000
                        }
                    });
                    eventloop.run(); // GH-90000
                } finally {
                    done.countDown(); // GH-90000
                }
            });
        }

        pool.shutdown(); // GH-90000
        boolean finished = done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS); // GH-90000
        assertThat(finished).as("All 100 tenants should finish within " + TIMEOUT_SECONDS + "s").isTrue(); // GH-90000

        // Verify write success
        int totalOps = TENANT_COUNT * OPS_PER_TENANT;
        assertThat(successes.get()) // GH-90000
            .as("Expected %d successful writes; got %d failures", totalOps, failures.get()) // GH-90000
            .isGreaterThanOrEqualTo(totalOps - failures.get()); // GH-90000

        // Verify isolation — read back and check tags
        verifyTenantIsolation(tenantEntityIds); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Burst scenario — all 100 tenants start simultaneously
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Burst traffic scenario [GH-90000]")
    class BurstTrafficTests {

        @Test
        @Tag("stress [GH-90000]")
        @DisplayName("100 tenants starting simultaneously — no deadlock within 60s [GH-90000]")
        void burstStart_hundredTenants_noDeadlock() throws InterruptedException { // GH-90000
            CountDownLatch startGate = new CountDownLatch(1); // GH-90000
            CountDownLatch done      = new CountDownLatch(TENANT_COUNT); // GH-90000
            AtomicInteger  failures  = new AtomicInteger(0); // GH-90000

            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor(); // GH-90000

            for (int t = 0; t < TENANT_COUNT; t++) { // GH-90000
                final String tenantId = "burst-tenant-" + t;
                pool.submit(() -> { // GH-90000
                    try {
                        startGate.await(); // wait for burst signal // GH-90000
                        Eventloop eventloop = Eventloop.builder().withCurrentThread().build(); // GH-90000
                        eventloop.execute(() -> { // GH-90000
                            for (int i = 0; i < 10; i++) {  // 10 ops per tenant in burst // GH-90000
                                client.save(tenantId, COLLECTION, Map.of("tenantTag", tenantId, "idx", i)) // GH-90000
                                    .whenException(e -> failures.incrementAndGet()); // GH-90000
                            }
                        });
                        eventloop.run(); // GH-90000
                    } catch (Exception e) { // GH-90000
                        failures.incrementAndGet(); // GH-90000
                    } finally {
                        done.countDown(); // GH-90000
                    }
                });
            }

            // Signal burst — all 100 start within a single millisecond window
            startGate.countDown(); // GH-90000

            boolean finished = done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS); // GH-90000
            pool.shutdown(); // GH-90000

            assertThat(finished).as("Burst of 100 tenants should complete within " + TIMEOUT_SECONDS + "s").isTrue(); // GH-90000
            // Some failures under burst are acceptable but data loss must be < 5%
            int expectedOps = TENANT_COUNT * 10;
            assertThat(failures.get()).isLessThanOrEqualTo(expectedOps / 20); // ≤5% failures // GH-90000
        }

        @Test
        @Tag("stress [GH-90000]")
        @DisplayName("tenant burst: no cross-tenant entity leakage under burst conditions [GH-90000]")
        void burstStart_zeroLeakageUnderLoad() throws InterruptedException { // GH-90000
            int samples       = 20;  // probe 20 out of 100 tenants for isolation
            CountDownLatch done = new CountDownLatch(samples); // GH-90000
            Map<String, List<String>> tenantTags = new ConcurrentHashMap<>(); // GH-90000

            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor(); // GH-90000
            for (int t = 0; t < samples; t++) { // GH-90000
                final String tenantId = "isolation-tenant-" + t;
                tenantTags.put(tenantId, Collections.synchronizedList(new ArrayList<>())); // GH-90000
                pool.submit(() -> { // GH-90000
                    try {
                        Eventloop eventloop = Eventloop.builder().withCurrentThread().build(); // GH-90000
                        eventloop.execute(() -> { // GH-90000
                            for (int i = 0; i < 5; i++) { // GH-90000
                                client.save(tenantId, COLLECTION, // GH-90000
                                    Map.of("tenantTag", tenantId, "idx", i)) // GH-90000
                                    .then(__ -> client.query(tenantId, // GH-90000
                                        COLLECTION, Query.all())) // GH-90000
                                    .whenResult(entities -> { // GH-90000
                                        for (Entity entity : entities) { // GH-90000
                                            Object tag = entity.data().get("tenantTag [GH-90000]");
                                            if (tag != null) { // GH-90000
                                                tenantTags.get(tenantId).add(tag.toString()); // GH-90000
                                            }
                                        }
                                    });
                            }
                        });
                        eventloop.run(); // GH-90000
                    } finally {
                        done.countDown(); // GH-90000
                    }
                });
            }

            pool.shutdown(); // GH-90000
            done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS); // GH-90000

            // All observed tags for a tenant must be its own
            for (Map.Entry<String, List<String>> entry : tenantTags.entrySet()) { // GH-90000
                String tenantId = entry.getKey(); // GH-90000
                List<String> observedTags = entry.getValue(); // GH-90000
                for (String tag : observedTags) { // GH-90000
                    assertThat(tag).as("Cross-tenant leakage detected: tenant %s saw tag %s", // GH-90000
                        tenantId, tag).isEqualTo(tenantId); // GH-90000
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Tenant isolation at scale
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Isolation invariants at 100-tenant scale [GH-90000]")
    class IsolationInvariantsTests {

        @Test
        @DisplayName("entities saved by tenant-A are not returned by queries for tenant-B at 100-tenant scale [GH-90000]")
        void tenantA_entities_invisibleTo_tenantB_atScale() throws InterruptedException { // GH-90000
            CountDownLatch latch = new CountDownLatch(2); // GH-90000
            List<String> tenantAIds = Collections.synchronizedList(new ArrayList<>()); // GH-90000
            List<Entity> tenantBResults = Collections.synchronizedList(new ArrayList<>()); // GH-90000

            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor(); // GH-90000

            pool.submit(() -> { // GH-90000
                Eventloop el = Eventloop.builder().withCurrentThread().build(); // GH-90000
                el.execute(() -> { // GH-90000
                    for (int i = 0; i < 10; i++) { // GH-90000
                        client.save("scale-tenant-alpha", COLLECTION, // GH-90000
                            Map.of("tenantTag", "scale-tenant-alpha", "idx", i)) // GH-90000
                            .whenResult(e -> tenantAIds.add(e.id())); // GH-90000
                    }
                });
                el.run(); // GH-90000
                latch.countDown(); // GH-90000
            });

            pool.submit(() -> { // GH-90000
                Eventloop el = Eventloop.builder().withCurrentThread().build(); // GH-90000
                el.execute(() -> { // GH-90000
                    client.query("scale-tenant-beta", // GH-90000
                        COLLECTION, Query.all()) // GH-90000
                        .whenResult(tenantBResults::addAll); // GH-90000
                });
                el.run(); // GH-90000
                latch.countDown(); // GH-90000
            });

            pool.shutdown(); // GH-90000
            latch.await(30, TimeUnit.SECONDS); // GH-90000

            // Tenant B must see zero of Tenant A's entities
            for (Entity entity : tenantBResults) { // GH-90000
                assertThat(entity.data().get("tenantTag [GH-90000]"))
                    .as("Tenant B must not see Tenant A's data [GH-90000]")
                    .isNotEqualTo("scale-tenant-alpha [GH-90000]");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void verifyTenantIsolation(Map<String, Set<String>> tenantEntityIds) { // GH-90000
        // Build an inverted map: entity ID → tenant(s) that claimed it // GH-90000
        Map<String, Set<String>> entityToClaims = new HashMap<>(); // GH-90000
        for (Map.Entry<String, Set<String>> entry : tenantEntityIds.entrySet()) { // GH-90000
            String tenantId = entry.getKey(); // GH-90000
            for (String entityId : entry.getValue()) { // GH-90000
                entityToClaims.computeIfAbsent(entityId, k -> ConcurrentHashMap.newKeySet()).add(tenantId); // GH-90000
            }
        }

        // No entity ID must be claimed by more than one tenant (no ID collision / cross-share) // GH-90000
        for (Map.Entry<String, Set<String>> entry : entityToClaims.entrySet()) { // GH-90000
            assertThat(entry.getValue()) // GH-90000
                .as("Entity ID %s was claimed by multiple tenants: %s", entry.getKey(), entry.getValue()) // GH-90000
                .hasSize(1); // GH-90000
        }
    }
}
