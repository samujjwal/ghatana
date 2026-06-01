/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.storage;

import com.ghatana.datacloud.DataCloud;
import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.EntityStoreContractTest;
import com.ghatana.platform.domain.eventstore.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Conformance test for the in-memory {@link EntityStore} implementation embedded in
 * {@link DataCloud}.
 *
 * <p>Runs the full {@link EntityStoreContractTest} harness against the in-memory store
 * created by {@link DataCloud#forTesting()}.
 *
 * <p><b>Design Note</b>: {@code InMemoryEntityStore} is intentionally a collection-scoped
 * cache. Operations that take only an {@link EntityStore.EntityId} (without collection
 * context) return empty/false by design. Those contract tests are overridden here to
 * exercise the equivalent collection-scoped APIs ({@link EntityStore#findByRef},
 * {@link EntityStore#existsByRef}).
 *
 * @doc.type class
 * @doc.purpose Verifies the in-memory EntityStore satisfies all EntityStore SPI contracts
 * @doc.layer product
 * @doc.pattern ConformanceTest
 */
@DisplayName("InMemoryEntityStore — contract conformance")
class InMemoryEntityStoreContractTest extends EntityStoreContractTest {

    private DataCloudClient client;

    @Override
    protected EntityStore createStore() {
        client = DataCloud.forTesting();
        return client.entityStore();
    }

    @Override
    protected TenantContext createTenant(String tenantId) {
        return TenantContext.of(tenantId);
    }

    @Override
    protected boolean supportsIdOnlyLookup() {
        return false;
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    // ─── InMemoryEntityStore by-design overrides ──────────────────────────────
    //
    // InMemoryEntityStore keys entities by (collection, id). Without collection
    // context, EntityId-only lookups always return empty/false. These tests
    // override the abstract contract to use the collection-scoped Ref APIs.

    @Nested
    @DisplayName("findById() — collection-scoped (InMemory override)")
    class FindByIdOverride {

        @Test
        @DisplayName("returns saved entity via findByRef (collection-scoped)")
        void returnsSavedEntityViaRef() {
            EntityStore.Entity e = entity("ref-1", "items", java.util.Map.of("k", "v"));
            runPromise(() -> store.save(tenant, e));

            Optional<EntityStore.Entity> found = runPromise(() ->
                store.findByRef(tenant, EntityStore.EntityRef.of("items", "ref-1")));
            assertThat(found).isPresent();
            assertThat(found.get().id().value()).isEqualTo("ref-1");
        }

        @Test
        @DisplayName("returns empty via findByRef for unknown entity")
        void returnsEmptyForUnknownRef() {
            Optional<EntityStore.Entity> found = runPromise(() ->
                store.findByRef(tenant, EntityStore.EntityRef.of("items", "unknown")));
            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByIds() — collection-scoped (InMemory override)")
    class FindByIdsOverride {

        @Test
        @DisplayName("returns matching entities via findByRefs")
        void returnsMatchingEntitiesViaRefs() {
            runPromise(() -> store.save(tenant, entity("ids-1", "items", java.util.Map.of())));
            runPromise(() -> store.save(tenant, entity("ids-2", "items", java.util.Map.of())));

            List<EntityStore.Entity> found = runPromise(() ->
                store.findByRefs(tenant, List.of(
                    EntityStore.EntityRef.of("items", "ids-1"),
                    EntityStore.EntityRef.of("items", "ids-2")
                )));
            assertThat(found).hasSize(2);
        }
    }

    @Nested
    @DisplayName("exists() — collection-scoped (InMemory override)")
    class ExistsOverride {

        @Test
        @DisplayName("returns true after save via existsByRef")
        void returnsTrueAfterSaveViaRef() {
            runPromise(() -> store.save(tenant, entity("ex-1", "items", java.util.Map.of())));

            boolean exists = runPromise(() ->
                store.existsByRef(tenant, EntityStore.EntityRef.of("items", "ex-1")));
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("returns false for non-existent entity via existsByRef")
        void returnsFalseForNonExistentViaRef() {
            boolean exists = runPromise(() ->
                store.existsByRef(tenant, EntityStore.EntityRef.of("items", "none")));
            assertThat(exists).isFalse();
        }
    }
}

