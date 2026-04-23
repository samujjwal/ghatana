/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud;

import com.ghatana.datacloud.DataCloudClient.*;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Multi-tenancy isolation tests: verifies that tenant A cannot read, query,
 * or receive events belonging to tenant B through any DataCloud API.
 *
 * <p>All async assertions use {@link EventloopTestBase#runPromise} — never
 * call {@code .getResult()} directly on a Promise.</p> // GH-90000
 *
 * @doc.type class
 * @doc.purpose Tenant boundary enforcement stress tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Multi-Tenancy Isolation Tests")
class MultiTenancyIsolationTest extends EventloopTestBase {

    private static final String TENANT_A = "tenant-alpha";
    private static final String TENANT_B = "tenant-beta";
    private static final String TENANT_C = "tenant-gamma";
    private static final String COLLECTION = "records";

    private DataCloudClient client;

    @BeforeEach
    void setUp() { // GH-90000
        client = DataCloud.forTesting(); // GH-90000
    }

    // =========================================================================
    // Entity Isolation
    // =========================================================================

    @Nested
    @DisplayName("Entity Store Isolation")
    class EntityStoreIsolation {

        @Test
        @DisplayName("entities saved under tenant-A are not visible to tenant-B")
        void entitySavedByTenantA_invisibleToTenantB() { // GH-90000
            runPromise(() -> client.save(TENANT_A, COLLECTION, // GH-90000
                    Map.of("id", "shared-id", "secret", "alpha-value"))); // GH-90000

            Optional<Entity> fromB = runPromise( // GH-90000
                    () -> client.findById(TENANT_B, COLLECTION, "shared-id")); // GH-90000

            assertThat(fromB).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("same entity-id in different tenants stores independent values")
        void sameEntityId_differentTenants_storeIndependently() { // GH-90000
            String sharedId = "entity-common-id";
            runPromise(() -> client.save(TENANT_A, COLLECTION, // GH-90000
                    Map.of("id", sharedId, "owner", "alpha"))); // GH-90000
            runPromise(() -> client.save(TENANT_B, COLLECTION, // GH-90000
                    Map.of("id", sharedId, "owner", "beta"))); // GH-90000

            Optional<Entity> fromA = runPromise( // GH-90000
                    () -> client.findById(TENANT_A, COLLECTION, sharedId)); // GH-90000
            Optional<Entity> fromB = runPromise( // GH-90000
                    () -> client.findById(TENANT_B, COLLECTION, sharedId)); // GH-90000

            assertThat(fromA).isPresent(); // GH-90000
            assertThat(fromA.get().data()).containsEntry("owner", "alpha"); // GH-90000

            assertThat(fromB).isPresent(); // GH-90000
            assertThat(fromB.get().data()).containsEntry("owner", "beta"); // GH-90000
        }

        @Test
        @DisplayName("query for tenant-B returns only tenant-B entities")
        void queryByTenantB_returnsOnlyTenantBEntities() { // GH-90000
            for (int i = 1; i <= 5; i++) { // GH-90000
                int idx = i;
                runPromise(() -> client.save(TENANT_A, COLLECTION, // GH-90000
                        Map.of("id", "a-" + idx, "tenant", "alpha"))); // GH-90000
            }
            for (int i = 1; i <= 3; i++) { // GH-90000
                int idx = i;
                runPromise(() -> client.save(TENANT_B, COLLECTION, // GH-90000
                        Map.of("id", "b-" + idx, "tenant", "beta"))); // GH-90000
            }

            List<Entity> resultsForB = runPromise( // GH-90000
                    () -> client.query(TENANT_B, COLLECTION, Query.all())); // GH-90000

            assertThat(resultsForB).hasSize(3); // GH-90000
            assertThat(resultsForB) // GH-90000
                    .extracting(e -> e.data().get("tenant"))
                    .containsOnly("beta");
        }

        @Test
        @DisplayName("deleting an entity for tenant-A does not affect tenant-B's copy")
        void deleteByTenantA_doesNotAffectTenantB() { // GH-90000
            String id = "shared-del-id";
            runPromise(() -> client.save(TENANT_A, COLLECTION, Map.of("id", id))); // GH-90000
            runPromise(() -> client.save(TENANT_B, COLLECTION, Map.of("id", id))); // GH-90000

            runPromise(() -> client.delete(TENANT_A, COLLECTION, id)); // GH-90000

            Optional<Entity> afterDeleteA = runPromise( // GH-90000
                    () -> client.findById(TENANT_A, COLLECTION, id)); // GH-90000
            Optional<Entity> afterDeleteB = runPromise( // GH-90000
                    () -> client.findById(TENANT_B, COLLECTION, id)); // GH-90000

            assertThat(afterDeleteA).isEmpty(); // GH-90000
            assertThat(afterDeleteB).isPresent(); // GH-90000
        }

        @Test
        @DisplayName("tenant isolation holds across N concurrent tenants")
        void concurrentTenants_allIsolated() { // GH-90000
            int tenantCount = 20;
            for (int i = 0; i < tenantCount; i++) { // GH-90000
                String tenantId = "concurrent-tenant-" + i;
                int tIdx = i;
                runPromise(() -> client.save(tenantId, COLLECTION, // GH-90000
                        Map.of("id", "ent-" + tIdx, "value", "owned-by-" + tIdx))); // GH-90000
            }

            // Each tenant must only see its own entity
            for (int i = 0; i < tenantCount; i++) { // GH-90000
                String tenantId = "concurrent-tenant-" + i;
                int tIdx = i;
                List<Entity> results = runPromise( // GH-90000
                        () -> client.query(tenantId, COLLECTION, Query.all())); // GH-90000
                assertThat(results).hasSize(1); // GH-90000
                assertThat(results.get(0).data()).containsEntry("value", "owned-by-" + tIdx); // GH-90000
            }
        }

        @Test
        @DisplayName("collection namespace isolation: same collection name is scoped per tenant")
        void collectionNameIsolation_sameNameDifferentTenants() { // GH-90000
            String colName = "customer-profiles";
            runPromise(() -> client.save(TENANT_A, colName, Map.of("id", "p1", "tier", "gold"))); // GH-90000
            runPromise(() -> client.save(TENANT_B, colName, Map.of("id", "p2", "tier", "silver"))); // GH-90000

            List<Entity> aResults = runPromise(() -> client.query(TENANT_A, colName, Query.all())); // GH-90000
            List<Entity> bResults = runPromise(() -> client.query(TENANT_B, colName, Query.all())); // GH-90000

            assertThat(aResults).hasSize(1); // GH-90000
            assertThat(aResults.get(0).data()).containsEntry("tier", "gold"); // GH-90000

            assertThat(bResults).hasSize(1); // GH-90000
            assertThat(bResults.get(0).data()).containsEntry("tier", "silver"); // GH-90000
        }
    }

    // =========================================================================
    // Event Log Isolation
    // =========================================================================

    @Nested
    @DisplayName("Event Log Isolation")
    class EventLogIsolation {

        @Test
        @DisplayName("events appended for tenant-A are not visible to tenant-B")
        void eventsForTenantA_invisibleToTenantB() { // GH-90000
            Event event = Event.of("order.created", Map.of("orderId", "A-001")); // GH-90000
            runPromise(() -> client.appendEvent(TENANT_A, event)); // GH-90000

            List<Event> fromB = runPromise( // GH-90000
                    () -> client.queryEvents(TENANT_B, EventQuery.byType("order.created")));

            assertThat(fromB).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("event query for tenant-B returns only tenant-B events")
        void eventQueryByTenantB_returnsOnlyOwnEvents() { // GH-90000
            for (int i = 1; i <= 4; i++) { // GH-90000
                int idx = i;
                runPromise(() -> client.appendEvent(TENANT_A, // GH-90000
                        Event.of("payment.processed", Map.of("ref", "A-" + idx)))); // GH-90000
            }
            for (int i = 1; i <= 2; i++) { // GH-90000
                int idx = i;
                runPromise(() -> client.appendEvent(TENANT_B, // GH-90000
                        Event.of("payment.processed", Map.of("ref", "B-" + idx)))); // GH-90000
            }

            List<Event> fromB = runPromise( // GH-90000
                    () -> client.queryEvents(TENANT_B, EventQuery.byType("payment.processed")));

            assertThat(fromB).hasSize(2); // GH-90000
            fromB.forEach(e -> // GH-90000
                    assertThat(e.payload().get("ref").toString()).startsWith("B-"));
        }

        @Test
        @DisplayName("tenant event tail subscription does not receive other tenants' events")
        void tailSubscription_receivesOnlyOwnTenantEvents() throws InterruptedException { // GH-90000
            AtomicInteger receivedByA = new AtomicInteger(0); // GH-90000
            CountDownLatch latch = new CountDownLatch(3); // GH-90000

            // Tenant A subscribes to its own event stream
            Subscription sub = client.tailEvents(TENANT_A, // GH-90000
                    TailRequest.fromBeginning(), // GH-90000
                    event -> {
                        receivedByA.incrementAndGet(); // GH-90000
                        latch.countDown(); // GH-90000
                    });

            // Inject events for both tenants
            for (int i = 0; i < 3; i++) { // GH-90000
                int idx = i;
                runPromise(() -> client.appendEvent(TENANT_A, // GH-90000
                        Event.of("signal", Map.of("n", idx)))); // GH-90000
            }
            // These should NOT be received by A's subscription
            for (int i = 0; i < 5; i++) { // GH-90000
                int idx = i;
                runPromise(() -> client.appendEvent(TENANT_B, // GH-90000
                        Event.of("signal", Map.of("n", idx)))); // GH-90000
            }

            latch.await(5, TimeUnit.SECONDS); // GH-90000
            sub.cancel(); // GH-90000

            // A must have received exactly its 3 events, not B's 5
            assertThat(receivedByA.get()).isEqualTo(3); // GH-90000
        }
    }

    // =========================================================================
    // Cross-Tenant Data Leakage Guards
    // =========================================================================

    @Nested
    @DisplayName("Cross-Tenant Leakage Guards")
    class CrossTenantLeakageGuards {

        @Test
        @DisplayName("blank tenantId is rejected with IllegalArgumentException")
        void blankTenantId_isRejected() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> client.save("", COLLECTION, Map.of("id", "e1")))) // GH-90000
                    .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }

        @Test
        @DisplayName("null tenantId is rejected with NullPointerException")
        void nullTenantId_isRejected() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                    runPromise(() -> client.save(null, COLLECTION, Map.of("id", "e1")))) // GH-90000
                    .isInstanceOf(NullPointerException.class); // GH-90000
        }

        @Test
        @DisplayName("wildcard tenant injection attempt returns no cross-tenant data")
        void wildcardTenantId_returnsNoData() { // GH-90000
            // Plant data under a legitimate tenant
            runPromise(() -> client.save(TENANT_A, COLLECTION, // GH-90000
                    Map.of("id", "secret", "data", "confidential"))); // GH-90000

            // Attempt to use a wildcard-like tenant string
            List<Entity> results = runPromise( // GH-90000
                    () -> client.query("*", COLLECTION, Query.all())); // GH-90000
            assertThat(results).isEmpty(); // GH-90000

            List<Entity> results2 = runPromise( // GH-90000
                    () -> client.query("%", COLLECTION, Query.all())); // GH-90000
            assertThat(results2).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("three independent tenants see only their own data after interleaved writes")
        void threeTenantsInterleavedWrites_eachSeesOnlyOwn() { // GH-90000
            // Interleave writes from three tenants
            runPromise(() -> client.save(TENANT_A, COLLECTION, Map.of("id", "a1", "v", "A"))); // GH-90000
            runPromise(() -> client.save(TENANT_B, COLLECTION, Map.of("id", "b1", "v", "B"))); // GH-90000
            runPromise(() -> client.save(TENANT_C, COLLECTION, Map.of("id", "c1", "v", "C"))); // GH-90000
            runPromise(() -> client.save(TENANT_A, COLLECTION, Map.of("id", "a2", "v", "A"))); // GH-90000
            runPromise(() -> client.save(TENANT_C, COLLECTION, Map.of("id", "c2", "v", "C"))); // GH-90000
            runPromise(() -> client.save(TENANT_B, COLLECTION, Map.of("id", "b2", "v", "B"))); // GH-90000

            List<Entity> a = runPromise(() -> client.query(TENANT_A, COLLECTION, Query.all())); // GH-90000
            List<Entity> b = runPromise(() -> client.query(TENANT_B, COLLECTION, Query.all())); // GH-90000
            List<Entity> c = runPromise(() -> client.query(TENANT_C, COLLECTION, Query.all())); // GH-90000

            assertThat(a).hasSize(2).allSatisfy(e -> // GH-90000
                    assertThat(e.data().get("v")).isEqualTo("A"));
            assertThat(b).hasSize(2).allSatisfy(e -> // GH-90000
                    assertThat(e.data().get("v")).isEqualTo("B"));
            assertThat(c).hasSize(2).allSatisfy(e -> // GH-90000
                    assertThat(e.data().get("v")).isEqualTo("C"));
        }

        @Test
        @DisplayName("query filter cannot be used to escape tenant scope via field injection")
        void queryFilterInjection_cannotLeakCrossTenantData() { // GH-90000
            // Setup: tenant A has sensitive data
            runPromise(() -> client.save(TENANT_A, COLLECTION, // GH-90000
                    Map.of("id", "priv-1", "classification", "secret"))); // GH-90000

            // Tenant B tries to query with a filter that matches A's data
            // The tenantId scoping must be applied BEFORE the filter
            List<Entity> leaked = runPromise(() -> client.query( // GH-90000
                    TENANT_B,
                    COLLECTION,
                    new Query( // GH-90000
                            List.of(new Filter("classification", "eq", "secret")), // GH-90000
                            List.of(), // GH-90000
                            0,
                            100)));

            assertThat(leaked).isEmpty(); // GH-90000
        }
    }
}
