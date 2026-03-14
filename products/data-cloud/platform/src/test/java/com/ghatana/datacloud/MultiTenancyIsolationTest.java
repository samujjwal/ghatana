/*
 * Copyright (c) 2026 Ghatana Inc.
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
 * call {@code .getResult()} directly on a Promise.</p>
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
    void setUp() {
        client = DataCloud.forTesting();
    }

    // =========================================================================
    // Entity Isolation
    // =========================================================================

    @Nested
    @DisplayName("Entity Store Isolation")
    class EntityStoreIsolation {

        @Test
        @DisplayName("entities saved under tenant-A are not visible to tenant-B")
        void entitySavedByTenantA_invisibleToTenantB() {
            runPromise(() -> client.save(TENANT_A, COLLECTION,
                    Map.of("id", "shared-id", "secret", "alpha-value")));

            Optional<Entity> fromB = runPromise(
                    () -> client.findById(TENANT_B, COLLECTION, "shared-id"));

            assertThat(fromB).isEmpty();
        }

        @Test
        @DisplayName("same entity-id in different tenants stores independent values")
        void sameEntityId_differentTenants_storeIndependently() {
            String sharedId = "entity-common-id";
            runPromise(() -> client.save(TENANT_A, COLLECTION,
                    Map.of("id", sharedId, "owner", "alpha")));
            runPromise(() -> client.save(TENANT_B, COLLECTION,
                    Map.of("id", sharedId, "owner", "beta")));

            Optional<Entity> fromA = runPromise(
                    () -> client.findById(TENANT_A, COLLECTION, sharedId));
            Optional<Entity> fromB = runPromise(
                    () -> client.findById(TENANT_B, COLLECTION, sharedId));

            assertThat(fromA).isPresent();
            assertThat(fromA.get().data()).containsEntry("owner", "alpha");

            assertThat(fromB).isPresent();
            assertThat(fromB.get().data()).containsEntry("owner", "beta");
        }

        @Test
        @DisplayName("query for tenant-B returns only tenant-B entities")
        void queryByTenantB_returnsOnlyTenantBEntities() {
            for (int i = 1; i <= 5; i++) {
                int idx = i;
                runPromise(() -> client.save(TENANT_A, COLLECTION,
                        Map.of("id", "a-" + idx, "tenant", "alpha")));
            }
            for (int i = 1; i <= 3; i++) {
                int idx = i;
                runPromise(() -> client.save(TENANT_B, COLLECTION,
                        Map.of("id", "b-" + idx, "tenant", "beta")));
            }

            List<Entity> resultsForB = runPromise(
                    () -> client.query(TENANT_B, COLLECTION, Query.all()));

            assertThat(resultsForB).hasSize(3);
            assertThat(resultsForB)
                    .extracting(e -> e.data().get("tenant"))
                    .containsOnly("beta");
        }

        @Test
        @DisplayName("deleting an entity for tenant-A does not affect tenant-B's copy")
        void deleteByTenantA_doesNotAffectTenantB() {
            String id = "shared-del-id";
            runPromise(() -> client.save(TENANT_A, COLLECTION, Map.of("id", id)));
            runPromise(() -> client.save(TENANT_B, COLLECTION, Map.of("id", id)));

            runPromise(() -> client.delete(TENANT_A, COLLECTION, id));

            Optional<Entity> afterDeleteA = runPromise(
                    () -> client.findById(TENANT_A, COLLECTION, id));
            Optional<Entity> afterDeleteB = runPromise(
                    () -> client.findById(TENANT_B, COLLECTION, id));

            assertThat(afterDeleteA).isEmpty();
            assertThat(afterDeleteB).isPresent();
        }

        @Test
        @DisplayName("tenant isolation holds across N concurrent tenants")
        void concurrentTenants_allIsolated() {
            int tenantCount = 20;
            for (int i = 0; i < tenantCount; i++) {
                String tenantId = "concurrent-tenant-" + i;
                int tIdx = i;
                runPromise(() -> client.save(tenantId, COLLECTION,
                        Map.of("id", "ent-" + tIdx, "value", "owned-by-" + tIdx)));
            }

            // Each tenant must only see its own entity
            for (int i = 0; i < tenantCount; i++) {
                String tenantId = "concurrent-tenant-" + i;
                int tIdx = i;
                List<Entity> results = runPromise(
                        () -> client.query(tenantId, COLLECTION, Query.all()));
                assertThat(results).hasSize(1);
                assertThat(results.get(0).data()).containsEntry("value", "owned-by-" + tIdx);
            }
        }

        @Test
        @DisplayName("collection namespace isolation: same collection name is scoped per tenant")
        void collectionNameIsolation_sameNameDifferentTenants() {
            String colName = "customer-profiles";
            runPromise(() -> client.save(TENANT_A, colName, Map.of("id", "p1", "tier", "gold")));
            runPromise(() -> client.save(TENANT_B, colName, Map.of("id", "p2", "tier", "silver")));

            List<Entity> aResults = runPromise(() -> client.query(TENANT_A, colName, Query.all()));
            List<Entity> bResults = runPromise(() -> client.query(TENANT_B, colName, Query.all()));

            assertThat(aResults).hasSize(1);
            assertThat(aResults.get(0).data()).containsEntry("tier", "gold");

            assertThat(bResults).hasSize(1);
            assertThat(bResults.get(0).data()).containsEntry("tier", "silver");
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
        void eventsForTenantA_invisibleToTenantB() {
            Event event = Event.of("order.created", Map.of("orderId", "A-001"));
            runPromise(() -> client.appendEvent(TENANT_A, event));

            List<Event> fromB = runPromise(
                    () -> client.queryEvents(TENANT_B, EventQuery.byType("order.created")));

            assertThat(fromB).isEmpty();
        }

        @Test
        @DisplayName("event query for tenant-B returns only tenant-B events")
        void eventQueryByTenantB_returnsOnlyOwnEvents() {
            for (int i = 1; i <= 4; i++) {
                int idx = i;
                runPromise(() -> client.appendEvent(TENANT_A,
                        Event.of("payment.processed", Map.of("ref", "A-" + idx))));
            }
            for (int i = 1; i <= 2; i++) {
                int idx = i;
                runPromise(() -> client.appendEvent(TENANT_B,
                        Event.of("payment.processed", Map.of("ref", "B-" + idx))));
            }

            List<Event> fromB = runPromise(
                    () -> client.queryEvents(TENANT_B, EventQuery.byType("payment.processed")));

            assertThat(fromB).hasSize(2);
            fromB.forEach(e ->
                    assertThat(e.payload().get("ref").toString()).startsWith("B-"));
        }

        @Test
        @DisplayName("tenant event tail subscription does not receive other tenants' events")
        void tailSubscription_receivesOnlyOwnTenantEvents() throws InterruptedException {
            AtomicInteger receivedByA = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(3);

            // Tenant A subscribes to its own event stream
            Subscription sub = client.tailEvents(TENANT_A,
                    TailRequest.fromBeginning(),
                    event -> {
                        receivedByA.incrementAndGet();
                        latch.countDown();
                    });

            // Inject events for both tenants
            for (int i = 0; i < 3; i++) {
                int idx = i;
                runPromise(() -> client.appendEvent(TENANT_A,
                        Event.of("signal", Map.of("n", idx))));
            }
            // These should NOT be received by A's subscription
            for (int i = 0; i < 5; i++) {
                int idx = i;
                runPromise(() -> client.appendEvent(TENANT_B,
                        Event.of("signal", Map.of("n", idx))));
            }

            latch.await(5, TimeUnit.SECONDS);
            sub.cancel();

            // A must have received exactly its 3 events, not B's 5
            assertThat(receivedByA.get()).isEqualTo(3);
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
        void blankTenantId_isRejected() {
            assertThatThrownBy(() ->
                    runPromise(() -> client.save("", COLLECTION, Map.of("id", "e1"))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null tenantId is rejected with NullPointerException")
        void nullTenantId_isRejected() {
            assertThatThrownBy(() ->
                    runPromise(() -> client.save(null, COLLECTION, Map.of("id", "e1"))))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("wildcard tenant injection attempt returns no cross-tenant data")
        void wildcardTenantId_returnsNoData() {
            // Plant data under a legitimate tenant
            runPromise(() -> client.save(TENANT_A, COLLECTION,
                    Map.of("id", "secret", "data", "confidential")));

            // Attempt to use a wildcard-like tenant string
            List<Entity> results = runPromise(
                    () -> client.query("*", COLLECTION, Query.all()));
            assertThat(results).isEmpty();

            List<Entity> results2 = runPromise(
                    () -> client.query("%", COLLECTION, Query.all()));
            assertThat(results2).isEmpty();
        }

        @Test
        @DisplayName("three independent tenants see only their own data after interleaved writes")
        void threeTenantsInterleavedWrites_eachSeesOnlyOwn() {
            // Interleave writes from three tenants
            runPromise(() -> client.save(TENANT_A, COLLECTION, Map.of("id", "a1", "v", "A")));
            runPromise(() -> client.save(TENANT_B, COLLECTION, Map.of("id", "b1", "v", "B")));
            runPromise(() -> client.save(TENANT_C, COLLECTION, Map.of("id", "c1", "v", "C")));
            runPromise(() -> client.save(TENANT_A, COLLECTION, Map.of("id", "a2", "v", "A")));
            runPromise(() -> client.save(TENANT_C, COLLECTION, Map.of("id", "c2", "v", "C")));
            runPromise(() -> client.save(TENANT_B, COLLECTION, Map.of("id", "b2", "v", "B")));

            List<Entity> a = runPromise(() -> client.query(TENANT_A, COLLECTION, Query.all()));
            List<Entity> b = runPromise(() -> client.query(TENANT_B, COLLECTION, Query.all()));
            List<Entity> c = runPromise(() -> client.query(TENANT_C, COLLECTION, Query.all()));

            assertThat(a).hasSize(2).allSatisfy(e ->
                    assertThat(e.data().get("v")).isEqualTo("A"));
            assertThat(b).hasSize(2).allSatisfy(e ->
                    assertThat(e.data().get("v")).isEqualTo("B"));
            assertThat(c).hasSize(2).allSatisfy(e ->
                    assertThat(e.data().get("v")).isEqualTo("C"));
        }

        @Test
        @DisplayName("query filter cannot be used to escape tenant scope via field injection")
        void queryFilterInjection_cannotLeakCrossTenantData() {
            // Setup: tenant A has sensitive data
            runPromise(() -> client.save(TENANT_A, COLLECTION,
                    Map.of("id", "priv-1", "classification", "secret")));

            // Tenant B tries to query with a filter that matches A's data
            // The tenantId scoping must be applied BEFORE the filter
            List<Entity> leaked = runPromise(() -> client.query(
                    TENANT_B,
                    COLLECTION,
                    new Query(
                            List.of(new Filter("classification", "eq", "secret")),
                            List.of(),
                            0,
                            100)));

            assertThat(leaked).isEmpty();
        }
    }
}
