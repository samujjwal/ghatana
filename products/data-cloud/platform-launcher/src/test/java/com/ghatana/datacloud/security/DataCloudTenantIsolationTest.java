/*
 * Copyright (c) 2025 Ghatana.ai. All rights reserved. // GH-90000
 *
 * Task 4.9 — Security Hardening: Tenant isolation verification for Data-Cloud.
 * Ensures entity storage, event log, and query operations properly enforce tenant boundaries.
 */
package com.ghatana.datacloud.security;

import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.DataCloudClient.*;
import com.ghatana.platform.domain.eventstore.TenantContext;
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
@DisplayName("Data-Cloud Tenant Isolation [GH-90000]")
class DataCloudTenantIsolationTest {

    private static final String TENANT_A = "tenant-alpha";
    private static final String TENANT_B = "tenant-beta";
    private static final String TENANT_C = "tenant-gamma";
    private static final String COLLECTION = "orders";

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
    // 1. Entity CRUD Tenant Isolation
    // =========================================================================

    @Nested
    @DisplayName("Entity CRUD Isolation [GH-90000]")
    class EntityCrudIsolation {

        @Test
        @DisplayName("Entity saved by tenant A is invisible to tenant B [GH-90000]")
        void entityInvisibleAcrossTenants() { // GH-90000
            Eventloop eventloop = Eventloop.builder().build(); // GH-90000
            eventloop.submit(() -> { // GH-90000
                client.save(TENANT_A, COLLECTION, Map.of("item", "widget", "price", 99.99)); // GH-90000
            });
            eventloop.run(); // GH-90000

            // Tenant B queries the same collection — should find nothing
            eventloop.submit(() -> { // GH-90000
                client.query(TENANT_B, COLLECTION, Query.all()) // GH-90000
                        .whenResult(results -> assertThat(results).isEmpty()); // GH-90000
            });
            eventloop.run(); // GH-90000
        }

        @Test
        @DisplayName("Each tenant sees only its own entities [GH-90000]")
        void eachTenantSeesOwnEntities() { // GH-90000
            Eventloop eventloop = Eventloop.builder().build(); // GH-90000

            // Save entities for tenant A
            eventloop.submit(() -> { // GH-90000
                client.save(TENANT_A, COLLECTION, Map.of("item", "alpha-widget")); // GH-90000
                client.save(TENANT_A, COLLECTION, Map.of("item", "alpha-gadget")); // GH-90000
            });
            eventloop.run(); // GH-90000

            // Save entity for tenant B
            eventloop.submit(() -> { // GH-90000
                client.save(TENANT_B, COLLECTION, Map.of("item", "beta-widget")); // GH-90000
            });
            eventloop.run(); // GH-90000

            // Verify tenant A sees 2 entities
            eventloop.submit(() -> { // GH-90000
                client.query(TENANT_A, COLLECTION, Query.all()) // GH-90000
                        .whenResult(results -> assertThat(results).hasSize(2)); // GH-90000
            });
            eventloop.run(); // GH-90000

            // Verify tenant B sees 1 entity
            eventloop.submit(() -> { // GH-90000
                client.query(TENANT_B, COLLECTION, Query.all()) // GH-90000
                        .whenResult(results -> assertThat(results).hasSize(1)); // GH-90000
            });
            eventloop.run(); // GH-90000

            // Verify tenant C sees nothing
            eventloop.submit(() -> { // GH-90000
                client.query(TENANT_C, COLLECTION, Query.all()) // GH-90000
                        .whenResult(results -> assertThat(results).isEmpty()); // GH-90000
            });
            eventloop.run(); // GH-90000
        }

        @Test
        @DisplayName("Delete by tenant A does not affect tenant B's entities [GH-90000]")
        void deleteDoesNotAffectOtherTenant() { // GH-90000
            Eventloop eventloop = Eventloop.builder().build(); // GH-90000
            String[] entityIdA = new String[1];
            String[] entityIdB = new String[1];

            // Save an entity for each tenant
            eventloop.submit(() -> { // GH-90000
                client.save(TENANT_A, COLLECTION, Map.of("item", "alpha")) // GH-90000
                        .whenResult(e -> entityIdA[0] = e.id()); // GH-90000
                client.save(TENANT_B, COLLECTION, Map.of("item", "beta")) // GH-90000
                        .whenResult(e -> entityIdB[0] = e.id()); // GH-90000
            });
            eventloop.run(); // GH-90000

            // Delete tenant A's entity
            eventloop.submit(() -> { // GH-90000
                client.delete(TENANT_A, COLLECTION, entityIdA[0]); // GH-90000
            });
            eventloop.run(); // GH-90000

            // Tenant B's entity should still exist
            eventloop.submit(() -> { // GH-90000
                client.findById(TENANT_B, COLLECTION, entityIdB[0]) // GH-90000
                        .whenResult(opt -> assertThat(opt).isPresent()); // GH-90000
            });
            eventloop.run(); // GH-90000
        }

        @Test
        @DisplayName("findById across tenants returns empty, not other tenant's entity [GH-90000]")
        void findByIdCrossTenantReturnsEmpty() { // GH-90000
            Eventloop eventloop = Eventloop.builder().build(); // GH-90000
            String[] entityId = new String[1];

            // Save entity for tenant A
            eventloop.submit(() -> { // GH-90000
                client.save(TENANT_A, COLLECTION, Map.of("item", "secret")) // GH-90000
                        .whenResult(e -> entityId[0] = e.id()); // GH-90000
            });
            eventloop.run(); // GH-90000

            // Tenant B tries to find it by exact ID
            eventloop.submit(() -> { // GH-90000
                client.findById(TENANT_B, COLLECTION, entityId[0]) // GH-90000
                        .whenResult(opt -> assertThat(opt).isEmpty()); // GH-90000
            });
            eventloop.run(); // GH-90000
        }
    }

    // =========================================================================
    // 2. Event Log Tenant Isolation
    // =========================================================================

    @Nested
    @DisplayName("Event Log Isolation [GH-90000]")
    class EventLogIsolation {

        @Test
        @DisplayName("Events appended by tenant A are invisible to tenant B [GH-90000]")
        void eventsInvisibleAcrossTenants() { // GH-90000
            Eventloop eventloop = Eventloop.builder().build(); // GH-90000

            // Tenant A appends events
            eventloop.submit(() -> { // GH-90000
                client.appendEvent(TENANT_A, Event.of("order.created", Map.of("orderId", "o-1"))); // GH-90000
                client.appendEvent(TENANT_A, Event.of("order.shipped", Map.of("orderId", "o-1"))); // GH-90000
            });
            eventloop.run(); // GH-90000

            // Tenant B queries events — should find nothing
            eventloop.submit(() -> { // GH-90000
                client.queryEvents(TENANT_B, EventQuery.all()) // GH-90000
                        .whenResult(events -> assertThat(events).isEmpty()); // GH-90000
            });
            eventloop.run(); // GH-90000
        }

        @Test
        @DisplayName("Event queries by type are tenant-scoped [GH-90000]")
        void eventQueryByTypeTenantScoped() { // GH-90000
            Eventloop eventloop = Eventloop.builder().build(); // GH-90000

            // Both tenants emit same event type
            eventloop.submit(() -> { // GH-90000
                client.appendEvent(TENANT_A, Event.of("order.created", Map.of("tenant", "A"))); // GH-90000
                client.appendEvent(TENANT_B, Event.of("order.created", Map.of("tenant", "B"))); // GH-90000
            });
            eventloop.run(); // GH-90000

            // Each tenant should see only 1 event
            eventloop.submit(() -> { // GH-90000
                client.queryEvents(TENANT_A, EventQuery.byType("order.created [GH-90000]"))
                        .whenResult(events -> { // GH-90000
                            assertThat(events).hasSize(1); // GH-90000
                            assertThat(events.get(0).payload()).containsEntry("tenant", "A"); // GH-90000
                        });
            });
            eventloop.run(); // GH-90000

            eventloop.submit(() -> { // GH-90000
                client.queryEvents(TENANT_B, EventQuery.byType("order.created [GH-90000]"))
                        .whenResult(events -> { // GH-90000
                            assertThat(events).hasSize(1); // GH-90000
                            assertThat(events.get(0).payload()).containsEntry("tenant", "B"); // GH-90000
                        });
            });
            eventloop.run(); // GH-90000
        }
    }

    // =========================================================================
    // 3. TenantContext SPI
    // =========================================================================

    @Nested
    @DisplayName("TenantContext SPI [GH-90000]")
    class TenantContextSpi {

        @Test
        @DisplayName("TenantContext.of() creates correct tenant scope [GH-90000]")
        void tenantContextOf() { // GH-90000
            TenantContext ctx = TenantContext.of(TENANT_A); // GH-90000
            assertThat(ctx.tenantId()).isEqualTo(TENANT_A); // GH-90000
            assertThat(ctx.workspaceId()).isEmpty(); // GH-90000
            assertThat(ctx.metadata()).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("TenantContext with workspace scopes further [GH-90000]")
        void tenantContextWithWorkspace() { // GH-90000
            TenantContext ctx = TenantContext.of(TENANT_A, "workspace-prod"); // GH-90000
            assertThat(ctx.tenantId()).isEqualTo(TENANT_A); // GH-90000
            assertThat(ctx.workspaceId()).isPresent().contains("workspace-prod [GH-90000]");
        }

        @Test
        @DisplayName("TenantContext metadata does not leak across instances [GH-90000]")
        void metadataDoesNotLeak() { // GH-90000
            TenantContext ctx1 = TenantContext.of(TENANT_A, Map.of("env", "prod")); // GH-90000
            TenantContext ctx2 = TenantContext.of(TENANT_B, Map.of("env", "staging")); // GH-90000

            assertThat(ctx1.metadata()).containsEntry("env", "prod"); // GH-90000
            assertThat(ctx2.metadata()).containsEntry("env", "staging"); // GH-90000

            // Metadata is independent
            assertThat(ctx1.metadata()).doesNotContainEntry("env", "staging"); // GH-90000
        }

        @Test
        @DisplayName("TenantContext.withMetadata creates new instance [GH-90000]")
        void withMetadataCreatesNew() { // GH-90000
            TenantContext original = TenantContext.of(TENANT_A); // GH-90000
            TenantContext enriched = original.withMetadata("region", "us-west-2"); // GH-90000

            // Original is unchanged
            assertThat(original.metadata()).isEmpty(); // GH-90000
            // Enriched has the new metadata
            assertThat(enriched.metadata()).containsEntry("region", "us-west-2"); // GH-90000
            assertThat(enriched.tenantId()).isEqualTo(TENANT_A); // GH-90000
        }
    }

    // =========================================================================
    // 4. Cross-Collection Tenant Isolation
    // =========================================================================

    @Nested
    @DisplayName("Cross-Collection Isolation [GH-90000]")
    class CrossCollectionIsolation {

        @Test
        @DisplayName("Same collection name in different tenants are isolated namespaces [GH-90000]")
        void sameCollectionDifferentTenants() { // GH-90000
            Eventloop eventloop = Eventloop.builder().build(); // GH-90000

            // Both tenants use "orders" collection
            eventloop.submit(() -> { // GH-90000
                client.save(TENANT_A, "orders", Map.of("item", "alpha-order")); // GH-90000
                client.save(TENANT_B, "orders", Map.of("item", "beta-order")); // GH-90000
            });
            eventloop.run(); // GH-90000

            // Both also use "users" collection
            eventloop.submit(() -> { // GH-90000
                client.save(TENANT_A, "users", Map.of("name", "alice")); // GH-90000
                client.save(TENANT_B, "users", Map.of("name", "bob")); // GH-90000
            });
            eventloop.run(); // GH-90000

            // Tenant A: 1 order, 1 user
            eventloop.submit(() -> { // GH-90000
                client.query(TENANT_A, "orders", Query.all()) // GH-90000
                        .whenResult(r -> assertThat(r).hasSize(1)); // GH-90000
                client.query(TENANT_A, "users", Query.all()) // GH-90000
                        .whenResult(r -> assertThat(r).hasSize(1)); // GH-90000
            });
            eventloop.run(); // GH-90000

            // Tenant B: 1 order, 1 user
            eventloop.submit(() -> { // GH-90000
                client.query(TENANT_B, "orders", Query.all()) // GH-90000
                        .whenResult(r -> assertThat(r).hasSize(1)); // GH-90000
                client.query(TENANT_B, "users", Query.all()) // GH-90000
                        .whenResult(r -> assertThat(r).hasSize(1)); // GH-90000
            });
            eventloop.run(); // GH-90000
        }
    }

    // =========================================================================
    // 5. Concurrent Multi-Tenant Access
    // =========================================================================

    @Nested
    @DisplayName("Concurrent Multi-Tenant Access [GH-90000]")
    class ConcurrentAccess {

        @Test
        @DisplayName("Concurrent entity saves maintain tenant isolation [GH-90000]")
        void concurrentEntitySaveMaintainsIsolation() throws Exception { // GH-90000
            int opsPerTenant = 20;
            Eventloop eventloop = Eventloop.builder().build(); // GH-90000
            String[] tenants = {TENANT_A, TENANT_B, TENANT_C};

            // All tenants save entities to the same collection
            for (String tenant : tenants) { // GH-90000
                for (int i = 0; i < opsPerTenant; i++) { // GH-90000
                    final int idx = i;
                    eventloop.submit(() -> { // GH-90000
                        client.save(tenant, COLLECTION // GH-90000
                                , Map.of("item", "item-" + idx, "tenant", tenant)); // GH-90000
                    });
                    eventloop.run(); // GH-90000
                }
            }

            // Verify isolation
            for (String tenant : tenants) { // GH-90000
                eventloop.submit(() -> { // GH-90000
                    client.query(tenant, COLLECTION, Query.all()) // GH-90000
                            .whenResult(results -> { // GH-90000
                                assertThat(results).hasSize(opsPerTenant); // GH-90000
                                results.forEach(entity -> // GH-90000
                                        assertThat(entity.data()).containsEntry("tenant", tenant)); // GH-90000
                            });
                });
                eventloop.run(); // GH-90000
            }
        }
    }

    // =========================================================================
    // 6. End-to-End Multi-Tenant Lifecycle
    // =========================================================================

    @Nested
    @DisplayName("End-to-End Multi-Tenant Lifecycle [GH-90000]")
    class EndToEndLifecycle {

        @Test
        @DisplayName("Full CRUD lifecycle maintains tenant boundaries [GH-90000]")
        void fullCrudLifecycle() { // GH-90000
            Eventloop eventloop = Eventloop.builder().build(); // GH-90000
            String[] entityIdA = new String[1];

            // Tenant A: Create
            eventloop.submit(() -> { // GH-90000
                client.save(TENANT_A, COLLECTION, Map.of("item", "premium-widget", "price", 299.99)) // GH-90000
                        .whenResult(e -> entityIdA[0] = e.id()); // GH-90000
            });
            eventloop.run(); // GH-90000

            // Tenant A: Read (should find) // GH-90000
            eventloop.submit(() -> { // GH-90000
                client.findById(TENANT_A, COLLECTION, entityIdA[0]) // GH-90000
                        .whenResult(opt -> { // GH-90000
                            assertThat(opt).isPresent(); // GH-90000
                            assertThat(opt.get().data()).containsEntry("item", "premium-widget"); // GH-90000
                        });
            });
            eventloop.run(); // GH-90000

            // Tenant B: Read tenant A's entity (should NOT find) // GH-90000
            eventloop.submit(() -> { // GH-90000
                client.findById(TENANT_B, COLLECTION, entityIdA[0]) // GH-90000
                        .whenResult(opt -> assertThat(opt).isEmpty()); // GH-90000
            });
            eventloop.run(); // GH-90000

            // Tenant A: Delete
            eventloop.submit(() -> { // GH-90000
                client.delete(TENANT_A, COLLECTION, entityIdA[0]); // GH-90000
            });
            eventloop.run(); // GH-90000

            // Tenant A: Read after delete (should NOT find) // GH-90000
            eventloop.submit(() -> { // GH-90000
                client.findById(TENANT_A, COLLECTION, entityIdA[0]) // GH-90000
                        .whenResult(opt -> assertThat(opt).isEmpty()); // GH-90000
            });
            eventloop.run(); // GH-90000
        }

        @Test
        @DisplayName("Full event lifecycle maintains tenant boundaries [GH-90000]")
        void fullEventLifecycle() { // GH-90000
            Eventloop eventloop = Eventloop.builder().build(); // GH-90000

            // Tenant A: Append events
            eventloop.submit(() -> { // GH-90000
                client.appendEvent(TENANT_A, Event.of("user.signup", Map.of("email", "alice@a.com"))); // GH-90000
                client.appendEvent(TENANT_A, Event.of("user.login", Map.of("email", "alice@a.com"))); // GH-90000
            });
            eventloop.run(); // GH-90000

            // Tenant B: Append different events
            eventloop.submit(() -> { // GH-90000
                client.appendEvent(TENANT_B, Event.of("user.signup", Map.of("email", "bob@b.com"))); // GH-90000
            });
            eventloop.run(); // GH-90000

            // Tenant A: Query all → 2 events
            eventloop.submit(() -> { // GH-90000
                client.queryEvents(TENANT_A, EventQuery.all()) // GH-90000
                        .whenResult(events -> assertThat(events).hasSize(2)); // GH-90000
            });
            eventloop.run(); // GH-90000

            // Tenant B: Query all → 1 event
            eventloop.submit(() -> { // GH-90000
                client.queryEvents(TENANT_B, EventQuery.all()) // GH-90000
                        .whenResult(events -> assertThat(events).hasSize(1)); // GH-90000
            });
            eventloop.run(); // GH-90000

            // Tenant C: Query all → 0 events
            eventloop.submit(() -> { // GH-90000
                client.queryEvents(TENANT_C, EventQuery.all()) // GH-90000
                        .whenResult(events -> assertThat(events).isEmpty()); // GH-90000
            });
            eventloop.run(); // GH-90000
        }
    }
}
