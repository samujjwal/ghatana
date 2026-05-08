/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.storage;

import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.EntityStoreContractTest;
import com.ghatana.datacloud.spi.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Conformance test for {@link H2SovereignEntityStore}.
 *
 * <p>Runs the full {@link EntityStoreContractTest} harness against the H2-backed sovereign
 * store. Each test receives a fresh store rooted in a {@link TempDir}, ensuring full
 * isolation.
 *
 * @doc.type class
 * @doc.purpose Verifies H2SovereignEntityStore satisfies all EntityStore SPI contracts
 * @doc.layer product
 * @doc.pattern ConformanceTest
 */
@DisplayName("H2SovereignEntityStore — contract conformance")
class H2SovereignEntityStoreContractTest extends EntityStoreContractTest {

    @TempDir
    Path tempDir;

    private final List<H2SovereignEntityStore> openStores = new ArrayList<>();

    @Override
    protected boolean supportsIdOnlyLookup() {
        // H2SovereignEntityStore requires collection context for all lookups (DC-P0-001)
        return false;
    }

    @Override
    protected EntityStore createStore() {
        H2SovereignEntityStore store = new H2SovereignEntityStore(tempDir.resolve("store-" + openStores.size()));
        openStores.add(store);
        return store;
    }

    @Override
    protected TenantContext createTenant(String tenantId) {
        return TenantContext.of(tenantId);
    }

    @AfterEach
    void closeStores() throws Exception {
        for (H2SovereignEntityStore store : openStores) {
            store.close();
        }
        openStores.clear();
    }

    // ─── H2SovereignEntityStore by-design overrides ───────────────────────────
    //
    // H2SovereignEntityStore requires collection context for all lookups (DC-P0-001).
    // Without collection, findById/findByIds/exists return empty/false by design.
    // These tests exercise the equivalent collection-scoped Ref APIs.

    @Nested
    @DisplayName("findById() — collection-scoped (H2Sovereign override)")
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
    @DisplayName("findByIds() — collection-scoped (H2Sovereign override)")
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
    @DisplayName("exists() — collection-scoped (H2Sovereign override)")
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
