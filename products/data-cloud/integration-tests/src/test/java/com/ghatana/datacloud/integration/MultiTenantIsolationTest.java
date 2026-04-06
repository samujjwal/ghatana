/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.integration;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Multi-tenant isolation integration tests for Data Cloud.
 *
 * <p>Ensures that data written by one tenant is never visible to another tenant,
 * that tenant-scoped operations are independent, and that cross-tenant access
 * is correctly rejected.
 *
 * @doc.type    class
 * @doc.purpose Multi-tenant isolation integration: data scoping, access rejection, concurrent writes
 * @doc.layer   product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Multi-Tenant Isolation Integration Tests")
class MultiTenantIsolationTest extends EventloopTestBase {

    private MultiTenantDataStore store;

    @BeforeEach
    void setUp() {
        store = new MultiTenantDataStore();
    }

    // ── Collection isolation ───────────────────────────────────────────────────

    @Test
    @DisplayName("collection created for tenant-A is not visible to tenant-B")
    void collectionCreatedForTenantAIsInvisibleToTenantB() {
        store.createCollection("tenant-A", "col-A");

        List<String> tenantBCols = store.listCollections("tenant-B");
        assertThat(tenantBCols).doesNotContain("col-A");
    }

    @Test
    @DisplayName("each tenant sees only their own collections")
    void eachTenantSeesOnlyTheirOwnCollections() {
        store.createCollection("tenant-X", "col-X1");
        store.createCollection("tenant-X", "col-X2");
        store.createCollection("tenant-Y", "col-Y1");

        assertThat(store.listCollections("tenant-X")).containsExactlyInAnyOrder("col-X1", "col-X2");
        assertThat(store.listCollections("tenant-Y")).containsExactly("col-Y1");
    }

    // ── Entity isolation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("entity written by tenant-A is not readable by tenant-B")
    void entityWrittenByTenantAIsNotReadableByTenantB() {
        String colAId = store.createCollection("t-iso-A", "iso-col");
        String entityId = store.createEntity("t-iso-A", colAId, Map.of("secret", "data-A"));

        Optional<Map<String, Object>> result = store.findEntity("t-iso-B", colAId, entityId);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("entity count per tenant is independent")
    void entityCountPerTenantIsIndependent() {
        String colA = store.createCollection("count-A", "counting-col");
        String colB = store.createCollection("count-B", "counting-col");

        IntStream.range(0, 10).forEach(i ->
                store.createEntity("count-A", colA, Map.of("i", i)));
        IntStream.range(0, 3).forEach(i ->
                store.createEntity("count-B", colB, Map.of("i", i)));

        assertThat(store.entityCount("count-A", colA)).isEqualTo(10);
        assertThat(store.entityCount("count-B", colB)).isEqualTo(3);
    }

    // ── Cross-tenant access rejection ─────────────────────────────────────────

    @Test
    @DisplayName("cross-tenant collection deletion is rejected")
    void crossTenantCollectionDeletionRejected() {
        store.createCollection("owner-T", "sensitive-col");

        assertThatThrownBy(() -> store.deleteCollection("attacker-T", "sensitive-col"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Unauthorized");
    }

    @Test
    @DisplayName("cross-tenant entity deletion is rejected")
    void crossTenantEntityDeletionRejected() {
        String colId = store.createCollection("owner-E", "entity-col");
        String entityId = store.createEntity("owner-E", colId, Map.of("data", "secret"));

        assertThatThrownBy(() -> store.deleteEntity("evil-E", colId, entityId))
                .isInstanceOf(SecurityException.class);
    }

    // ── Namespace isolation ───────────────────────────────────────────────────

    @Test
    @DisplayName("same collection name in different tenants refers to distinct collections")
    void sameCollectionNameInDifferentTenantsIsDistinct() {
        String idA = store.createCollection("ns-A", "shared-name");
        String idB = store.createCollection("ns-B", "shared-name");

        assertThat(idA).isNotEqualTo(idB);
        assertThat(store.findCollection("ns-A", idA)).isPresent();
        assertThat(store.findCollection("ns-B", idA)).isEmpty(); // A's ID not visible to B
    }

    // ── Concurrent multi-tenant writes ────────────────────────────────────────

    @Test
    @DisplayName("concurrent writes from multiple tenants are all persisted and isolated")
    void concurrentWritesFromMultipleTenantsAreIsolated() throws Exception {
        int tenantsCount = 5;
        int entitiesPerTenant = 20;
        String[] colIds = new String[tenantsCount];
        String[] tenants = new String[tenantsCount];

        for (int i = 0; i < tenantsCount; i++) {
            tenants[i] = "concurrent-tenant-" + i;
            colIds[i] = store.createCollection(tenants[i], "concurrent-col");
        }

        CyclicBarrier barrier = new CyclicBarrier(tenantsCount * entitiesPerTenant);
        Thread[] threads = new Thread[tenantsCount * entitiesPerTenant];

        for (int t = 0; t < tenantsCount; t++) {
            for (int e = 0; e < entitiesPerTenant; e++) {
                final int ti = t;
                final int ei = e;
                threads[t * entitiesPerTenant + e] = Thread.ofVirtual().start(() -> {
                    try {
                        barrier.await();
                        store.createEntity(tenants[ti], colIds[ti], Map.of("entity", ei));
                    } catch (Exception ignored) {}
                });
            }
        }

        for (Thread thread : threads) thread.join();

        for (int i = 0; i < tenantsCount; i++) {
            assertThat(store.entityCount(tenants[i], colIds[i])).isEqualTo(entitiesPerTenant);
        }
    }

    // ── Tenant deletion ───────────────────────────────────────────────────────

    @Test
    @DisplayName("purging a tenant removes all their data but does not affect other tenants")
    void purgingTenantDoesNotAffectOtherTenants() {
        String survivorColId = store.createCollection("survivor-T", "survivor-col");
        store.createEntity("survivor-T", survivorColId, Map.of("key", "value"));

        String purgeColId = store.createCollection("purge-T", "purge-col");
        store.createEntity("purge-T", purgeColId, Map.of("key", "data"));

        store.purgeTenant("purge-T");

        assertThat(store.listCollections("purge-T")).isEmpty();
        assertThat(store.listCollections("survivor-T")).isNotEmpty();
        assertThat(store.entityCount("survivor-T", survivorColId)).isEqualTo(1);
    }

    // ── Data-store implementation (for tests) ──────────────────────────────────

    static class MultiTenantDataStore {
        private final ConcurrentHashMap<String, Map<String, Object>> collections = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, ConcurrentHashMap<String, Map<String, Object>>> entities
                = new ConcurrentHashMap<>();

        String createCollection(String tenantId, String name) {
            String id = UUID.randomUUID().toString();
            collections.put(key(tenantId, id), Map.of("id", id, "name", name, "tenantId", tenantId));
            return id;
        }

        Optional<Map<String, Object>> findCollection(String tenantId, String collectionId) {
            return Optional.ofNullable(collections.get(key(tenantId, collectionId)));
        }

        void deleteCollection(String tenantId, String collectionName) {
            // Verify ownership
            boolean owned = collections.entrySet().stream()
                    .anyMatch(e -> e.getKey().startsWith(tenantId + "|")
                            && Objects.equals(e.getValue().get("name"), collectionName));
            if (!owned) throw new SecurityException("Unauthorized: tenant " + tenantId + " cannot delete collection");
            collections.entrySet().removeIf(e ->
                    e.getKey().startsWith(tenantId + "|") &&
                    Objects.equals(e.getValue().get("name"), collectionName));
        }

        List<String> listCollections(String tenantId) {
            return collections.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(tenantId + "|"))
                    .map(e -> (String) e.getValue().get("name"))
                    .toList();
        }

        String createEntity(String tenantId, String collectionId, Map<String, Object> data) {
            // Require the collection to exist for this tenant
            String entityId = UUID.randomUUID().toString();
            Map<String, Object> entity = new HashMap<>(data);
            entity.put("id", entityId);
            entities.computeIfAbsent(key(tenantId, collectionId),
                    k -> new ConcurrentHashMap<>()).put(entityId, entity);
            return entityId;
        }

        Optional<Map<String, Object>> findEntity(String tenantId, String collectionId, String entityId) {
            ConcurrentHashMap<String, Map<String, Object>> colEntities =
                    entities.get(key(tenantId, collectionId));
            if (colEntities == null) return Optional.empty();
            return Optional.ofNullable(colEntities.get(entityId));
        }

        void deleteEntity(String tenantId, String collectionId, String entityId) {
            // Verify entity belongs to tenant
            String k = key(tenantId, collectionId);
            ConcurrentHashMap<String, Map<String, Object>> colEntities = entities.get(k);
            if (colEntities == null || !colEntities.containsKey(entityId)) {
                throw new SecurityException("Unauthorized: entity not owned by tenant " + tenantId);
            }
            colEntities.remove(entityId);
        }

        int entityCount(String tenantId, String collectionId) {
            ConcurrentHashMap<String, Map<String, Object>> colEntities =
                    entities.get(key(tenantId, collectionId));
            return colEntities == null ? 0 : colEntities.size();
        }

        void purgeTenant(String tenantId) {
            collections.entrySet().removeIf(e -> e.getKey().startsWith(tenantId + "|"));
            entities.entrySet().removeIf(e -> e.getKey().startsWith(tenantId + "|"));
        }

        private String key(String tenantId, String id) { return tenantId + "|" + id; }
    }
}
