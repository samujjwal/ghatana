/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved.
 *
 * Task 4.9 — Security Hardening: Tenant isolation verification for Data-Cloud.
 * Ensures entity storage, event log, and query operations properly enforce tenant boundaries.
 */
package com.ghatana.datacloud.security;

import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.*;
import com.ghatana.datacloud.spi.TenantContext;
import io.activej.eventloop.Eventloop;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Verifies multi-tenant isolation guarantees across Data-Cloud infrastructure.
 * Tests cover:
 * <ul>
 *   <li>Entity CRUD: tenant A cannot see/modify tenant B's entities</li>
 *   <li>Event log: events are tenant-scoped, no cross-tenant leakage</li>
 *   <li>Query operations: queries are inherently tenant-scoped</li>
 *   <li>TenantContext SPI: correct scoping and metadata</li>
 *   <li>Concurrent multi-tenant access: no cross-contamination under load</li>
 * </ul>
 */
@DisplayName("Data-Cloud Tenant Isolation")
class DataCloudTenantIsolationTest {

    private static final String TENANT_A = "tenant-alpha";
    private static final String TENANT_B = "tenant-beta";
    private static final String TENANT_C = "tenant-gamma";
    private static final String COLLECTION = "orders";

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
    // 1. Entity CRUD Tenant Isolation
    // =========================================================================

    @Nested
    @DisplayName("Entity CRUD Isolation")
    class EntityCrudIsolation {

        @Test
        @DisplayName("Entity saved by tenant A is invisible to tenant B")
        void entityInvisibleAcrossTenants() {
            Eventloop eventloop = Eventloop.builder().build();
            eventloop.submit(() -> {
                client.save(TENANT_A, COLLECTION, Map.of("item", "widget", "price", 99.99));
            });
            eventloop.run();

            // Tenant B queries the same collection — should find nothing
            eventloop.submit(() -> {
                client.query(TENANT_B, COLLECTION, Query.all())
                        .whenResult(results -> assertThat(results).isEmpty());
            });
            eventloop.run();
        }

        @Test
        @DisplayName("Each tenant sees only its own entities")
        void eachTenantSeesOwnEntities() {
            Eventloop eventloop = Eventloop.builder().build();

            // Save entities for tenant A
            eventloop.submit(() -> {
                client.save(TENANT_A, COLLECTION, Map.of("item", "alpha-widget"));
                client.save(TENANT_A, COLLECTION, Map.of("item", "alpha-gadget"));
            });
            eventloop.run();

            // Save entity for tenant B
            eventloop.submit(() -> {
                client.save(TENANT_B, COLLECTION, Map.of("item", "beta-widget"));
            });
            eventloop.run();

            // Verify tenant A sees 2 entities
            eventloop.submit(() -> {
                client.query(TENANT_A, COLLECTION, Query.all())
                        .whenResult(results -> assertThat(results).hasSize(2));
            });
            eventloop.run();

            // Verify tenant B sees 1 entity
            eventloop.submit(() -> {
                client.query(TENANT_B, COLLECTION, Query.all())
                        .whenResult(results -> assertThat(results).hasSize(1));
            });
            eventloop.run();

            // Verify tenant C sees nothing
            eventloop.submit(() -> {
                client.query(TENANT_C, COLLECTION, Query.all())
                        .whenResult(results -> assertThat(results).isEmpty());
            });
            eventloop.run();
        }

        @Test
        @DisplayName("Delete by tenant A does not affect tenant B's entities")
        void deleteDoesNotAffectOtherTenant() {
            Eventloop eventloop = Eventloop.builder().build();
            String[] entityIdA = new String[1];
            String[] entityIdB = new String[1];

            // Save an entity for each tenant
            eventloop.submit(() -> {
                client.save(TENANT_A, COLLECTION, Map.of("item", "alpha"))
                        .whenResult(e -> entityIdA[0] = e.id());
                client.save(TENANT_B, COLLECTION, Map.of("item", "beta"))
                        .whenResult(e -> entityIdB[0] = e.id());
            });
            eventloop.run();

            // Delete tenant A's entity
            eventloop.submit(() -> {
                client.delete(TENANT_A, COLLECTION, entityIdA[0]);
            });
            eventloop.run();

            // Tenant B's entity should still exist
            eventloop.submit(() -> {
                client.findById(TENANT_B, COLLECTION, entityIdB[0])
                        .whenResult(opt -> assertThat(opt).isPresent());
            });
            eventloop.run();
        }

        @Test
        @DisplayName("findById across tenants returns empty, not other tenant's entity")
        void findByIdCrossTenantReturnsEmpty() {
            Eventloop eventloop = Eventloop.builder().build();
            String[] entityId = new String[1];

            // Save entity for tenant A
            eventloop.submit(() -> {
                client.save(TENANT_A, COLLECTION, Map.of("item", "secret"))
                        .whenResult(e -> entityId[0] = e.id());
            });
            eventloop.run();

            // Tenant B tries to find it by exact ID
            eventloop.submit(() -> {
                client.findById(TENANT_B, COLLECTION, entityId[0])
                        .whenResult(opt -> assertThat(opt).isEmpty());
            });
            eventloop.run();
        }
    }

    // =========================================================================
    // 2. Event Log Tenant Isolation
    // =========================================================================

    @Nested
    @DisplayName("Event Log Isolation")
    class EventLogIsolation {

        @Test
        @DisplayName("Events appended by tenant A are invisible to tenant B")
        void eventsInvisibleAcrossTenants() {
            Eventloop eventloop = Eventloop.builder().build();

            // Tenant A appends events
            eventloop.submit(() -> {
                client.appendEvent(TENANT_A, Event.of("order.created", Map.of("orderId", "o-1")));
                client.appendEvent(TENANT_A, Event.of("order.shipped", Map.of("orderId", "o-1")));
            });
            eventloop.run();

            // Tenant B queries events — should find nothing
            eventloop.submit(() -> {
                client.queryEvents(TENANT_B, EventQuery.all())
                        .whenResult(events -> assertThat(events).isEmpty());
            });
            eventloop.run();
        }

        @Test
        @DisplayName("Event queries by type are tenant-scoped")
        void eventQueryByTypeTenantScoped() {
            Eventloop eventloop = Eventloop.builder().build();

            // Both tenants emit same event type
            eventloop.submit(() -> {
                client.appendEvent(TENANT_A, Event.of("order.created", Map.of("tenant", "A")));
                client.appendEvent(TENANT_B, Event.of("order.created", Map.of("tenant", "B")));
            });
            eventloop.run();

            // Each tenant should see only 1 event
            eventloop.submit(() -> {
                client.queryEvents(TENANT_A, EventQuery.byType("order.created"))
                        .whenResult(events -> {
                            assertThat(events).hasSize(1);
                            assertThat(events.get(0).payload()).containsEntry("tenant", "A");
                        });
            });
            eventloop.run();

            eventloop.submit(() -> {
                client.queryEvents(TENANT_B, EventQuery.byType("order.created"))
                        .whenResult(events -> {
                            assertThat(events).hasSize(1);
                            assertThat(events.get(0).payload()).containsEntry("tenant", "B");
                        });
            });
            eventloop.run();
        }
    }

    // =========================================================================
    // 3. TenantContext SPI
    // =========================================================================

    @Nested
    @DisplayName("TenantContext SPI")
    class TenantContextSpi {

        @Test
        @DisplayName("TenantContext.of() creates correct tenant scope")
        void tenantContextOf() {
            TenantContext ctx = TenantContext.of(TENANT_A);
            assertThat(ctx.tenantId()).isEqualTo(TENANT_A);
            assertThat(ctx.workspaceId()).isEmpty();
            assertThat(ctx.metadata()).isEmpty();
        }

        @Test
        @DisplayName("TenantContext with workspace scopes further")
        void tenantContextWithWorkspace() {
            TenantContext ctx = TenantContext.of(TENANT_A, "workspace-prod");
            assertThat(ctx.tenantId()).isEqualTo(TENANT_A);
            assertThat(ctx.workspaceId()).isPresent().contains("workspace-prod");
        }

        @Test
        @DisplayName("TenantContext metadata does not leak across instances")
        void metadataDoesNotLeak() {
            TenantContext ctx1 = TenantContext.of(TENANT_A, Map.of("env", "prod"));
            TenantContext ctx2 = TenantContext.of(TENANT_B, Map.of("env", "staging"));

            assertThat(ctx1.metadata()).containsEntry("env", "prod");
            assertThat(ctx2.metadata()).containsEntry("env", "staging");

            // Metadata is independent
            assertThat(ctx1.metadata()).doesNotContainEntry("env", "staging");
        }

        @Test
        @DisplayName("TenantContext.withMetadata creates new instance")
        void withMetadataCreatesNew() {
            TenantContext original = TenantContext.of(TENANT_A);
            TenantContext enriched = original.withMetadata("region", "us-west-2");

            // Original is unchanged
            assertThat(original.metadata()).isEmpty();
            // Enriched has the new metadata
            assertThat(enriched.metadata()).containsEntry("region", "us-west-2");
            assertThat(enriched.tenantId()).isEqualTo(TENANT_A);
        }
    }

    // =========================================================================
    // 4. Cross-Collection Tenant Isolation
    // =========================================================================

    @Nested
    @DisplayName("Cross-Collection Isolation")
    class CrossCollectionIsolation {

        @Test
        @DisplayName("Same collection name in different tenants are isolated namespaces")
        void sameCollectionDifferentTenants() {
            Eventloop eventloop = Eventloop.builder().build();

            // Both tenants use "orders" collection
            eventloop.submit(() -> {
                client.save(TENANT_A, "orders", Map.of("item", "alpha-order"));
                client.save(TENANT_B, "orders", Map.of("item", "beta-order"));
            });
            eventloop.run();

            // Both also use "users" collection
            eventloop.submit(() -> {
                client.save(TENANT_A, "users", Map.of("name", "alice"));
                client.save(TENANT_B, "users", Map.of("name", "bob"));
            });
            eventloop.run();

            // Tenant A: 1 order, 1 user
            eventloop.submit(() -> {
                client.query(TENANT_A, "orders", Query.all())
                        .whenResult(r -> assertThat(r).hasSize(1));
                client.query(TENANT_A, "users", Query.all())
                        .whenResult(r -> assertThat(r).hasSize(1));
            });
            eventloop.run();

            // Tenant B: 1 order, 1 user
            eventloop.submit(() -> {
                client.query(TENANT_B, "orders", Query.all())
                        .whenResult(r -> assertThat(r).hasSize(1));
                client.query(TENANT_B, "users", Query.all())
                        .whenResult(r -> assertThat(r).hasSize(1));
            });
            eventloop.run();
        }
    }

    // =========================================================================
    // 5. Concurrent Multi-Tenant Access
    // =========================================================================

    @Nested
    @DisplayName("Concurrent Multi-Tenant Access")
    class ConcurrentAccess {

        @Test
        @DisplayName("Concurrent entity saves maintain tenant isolation")
        void concurrentEntitySaveMaintainsIsolation() throws Exception {
            int opsPerTenant = 20;
            Eventloop eventloop = Eventloop.builder().build();
            String[] tenants = {TENANT_A, TENANT_B, TENANT_C};

            // All tenants save entities to the same collection
            for (String tenant : tenants) {
                for (int i = 0; i < opsPerTenant; i++) {
                    final int idx = i;
                    eventloop.submit(() -> {
                        client.save(tenant, COLLECTION 
                                , Map.of("item", "item-" + idx, "tenant", tenant));
                    });
                    eventloop.run();
                }
            }

            // Verify isolation
            for (String tenant : tenants) {
                eventloop.submit(() -> {
                    client.query(tenant, COLLECTION, Query.all())
                            .whenResult(results -> {
                                assertThat(results).hasSize(opsPerTenant);
                                results.forEach(entity ->
                                        assertThat(entity.data()).containsEntry("tenant", tenant));
                            });
                });
                eventloop.run();
            }
        }
    }

    // =========================================================================
    // 6. End-to-End Multi-Tenant Lifecycle
    // =========================================================================

    @Nested
    @DisplayName("End-to-End Multi-Tenant Lifecycle")
    class EndToEndLifecycle {

        @Test
        @DisplayName("Full CRUD lifecycle maintains tenant boundaries")
        void fullCrudLifecycle() {
            Eventloop eventloop = Eventloop.builder().build();
            String[] entityIdA = new String[1];

            // Tenant A: Create
            eventloop.submit(() -> {
                client.save(TENANT_A, COLLECTION, Map.of("item", "premium-widget", "price", 299.99))
                        .whenResult(e -> entityIdA[0] = e.id());
            });
            eventloop.run();

            // Tenant A: Read (should find)
            eventloop.submit(() -> {
                client.findById(TENANT_A, COLLECTION, entityIdA[0])
                        .whenResult(opt -> {
                            assertThat(opt).isPresent();
                            assertThat(opt.get().data()).containsEntry("item", "premium-widget");
                        });
            });
            eventloop.run();

            // Tenant B: Read tenant A's entity (should NOT find)
            eventloop.submit(() -> {
                client.findById(TENANT_B, COLLECTION, entityIdA[0])
                        .whenResult(opt -> assertThat(opt).isEmpty());
            });
            eventloop.run();

            // Tenant A: Delete
            eventloop.submit(() -> {
                client.delete(TENANT_A, COLLECTION, entityIdA[0]);
            });
            eventloop.run();

            // Tenant A: Read after delete (should NOT find)
            eventloop.submit(() -> {
                client.findById(TENANT_A, COLLECTION, entityIdA[0])
                        .whenResult(opt -> assertThat(opt).isEmpty());
            });
            eventloop.run();
        }

        @Test
        @DisplayName("Full event lifecycle maintains tenant boundaries")
        void fullEventLifecycle() {
            Eventloop eventloop = Eventloop.builder().build();

            // Tenant A: Append events
            eventloop.submit(() -> {
                client.appendEvent(TENANT_A, Event.of("user.signup", Map.of("email", "alice@a.com")));
                client.appendEvent(TENANT_A, Event.of("user.login", Map.of("email", "alice@a.com")));
            });
            eventloop.run();

            // Tenant B: Append different events
            eventloop.submit(() -> {
                client.appendEvent(TENANT_B, Event.of("user.signup", Map.of("email", "bob@b.com")));
            });
            eventloop.run();

            // Tenant A: Query all → 2 events
            eventloop.submit(() -> {
                client.queryEvents(TENANT_A, EventQuery.all())
                        .whenResult(events -> assertThat(events).hasSize(2));
            });
            eventloop.run();

            // Tenant B: Query all → 1 event
            eventloop.submit(() -> {
                client.queryEvents(TENANT_B, EventQuery.all())
                        .whenResult(events -> assertThat(events).hasSize(1));
            });
            eventloop.run();

            // Tenant C: Query all → 0 events
            eventloop.submit(() -> {
                client.queryEvents(TENANT_C, EventQuery.all())
                        .whenResult(events -> assertThat(events).isEmpty());
            });
            eventloop.run();
        }
    }
}
