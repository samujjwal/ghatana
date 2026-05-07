/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.spi;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Abstract contract test harness for {@link EntityStore} implementations.
 *
 * <p>Concrete test subclasses must:
 * <ol>
 *   <li>Extend this class</li>
 *   <li>Implement {@link #createStore()} to return the SUT</li>
 *   <li>Implement {@link #createTenant(String)} to return an appropriately scoped {@link TenantContext}</li>
 * </ol>
 *
 * <p>Example concrete subclass:
 * <pre>{@code
 * class InMemoryEntityStoreContractTest extends EntityStoreContractTest {
 *     @Override protected EntityStore createStore() { return new InMemoryEntityStore(); }
 *     @Override protected TenantContext createTenant(String id) { return TenantContext.of(id); }
 * }
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Abstract contract harness ensuring all EntityStore implementations satisfy SPI contracts
 * @doc.layer spi
 * @doc.pattern ContractTest
 */
public abstract class EntityStoreContractTest extends EventloopTestBase {

    protected EntityStore store;
    protected TenantContext tenant;

    // ─── Hook Methods ─────────────────────────────────────────────────────────

    /**
     * Creates the EntityStore implementation under test.
     * Called once per test method in {@link BeforeEach}.
     */
    protected abstract EntityStore createStore();

    /**
     * Creates a tenant context for the given logical tenant ID.
     */
    protected abstract TenantContext createTenant(String tenantId);

    @BeforeEach
    void setUpContract() {
        store = createStore();
        tenant = createTenant("test-tenant");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    protected EntityStore.Entity entity(String id, String collection, Map<String, Object> data) {
        return EntityStore.Entity.builder()
                .id(id)
                .collection(collection)
                .data(data)
                .build();
    }

    protected EntityStore.EntityId id(String value) {
        return EntityStore.EntityId.of(value);
    }

    // ─── save ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("save()")
    class Save {

        @Test
        @DisplayName("saves entity and returns it with the assigned ID")
        void savesEntityAndReturnsIt() {
            EntityStore.Entity e = entity("save-1", "items", Map.of("name", "Alpha"));
            EntityStore.Entity saved = runPromise(() -> store.save(tenant, e));

            assertThat(saved.id().value()).isEqualTo("save-1");
            assertThat(saved.collection()).isEqualTo("items");
            assertThat(saved.data()).containsEntry("name", "Alpha");
        }

        @Test
        @DisplayName("overwrites existing entity on second save with same ID")
        void overwritesEntityOnSecondSave() {
            EntityStore.Entity v1 = entity("overwrite-1", "items", Map.of("v", 1));
            EntityStore.Entity v2 = entity("overwrite-1", "items", Map.of("v", 2));

            runPromise(() -> store.save(tenant, v1));
            EntityStore.Entity result = runPromise(() -> store.save(tenant, v2));

            Optional<EntityStore.Entity> found = runPromise(() -> store.findById(tenant, id("overwrite-1")));
            assertThat(found).isPresent();
            assertThat(found.get().data()).containsEntry("v", 2);
        }
    }

    // ─── saveBatch ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("saveBatch()")
    class SaveBatch {

        @Test
        @DisplayName("saves multiple entities in batch and all are retrievable")
        void savesMultipleEntitiesInBatch() {
            List<EntityStore.Entity> entities = List.of(
                    entity("batch-1", "items", Map.of("n", 1)),
                    entity("batch-2", "items", Map.of("n", 2)),
                    entity("batch-3", "items", Map.of("n", 3))
            );

            BatchResult<String> result = runPromise(() -> store.saveBatch(tenant, entities));
            assertThat(result.successCount()).isEqualTo(3);

            Optional<EntityStore.Entity> found = runPromise(() -> store.findById(tenant, id("batch-2")));
            assertThat(found).isPresent();
        }

        @Test
        @DisplayName("empty batch returns zero success count")
        void emptyBatchReturnsZeroCount() {
            BatchResult<String> result = runPromise(() -> store.saveBatch(tenant, List.of()));
            assertThat(result.successCount()).isZero();
        }
    }

    // ─── findById ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("returns empty for unknown ID")
        void returnsEmptyForUnknownId() {
            Optional<EntityStore.Entity> result = runPromise(() -> store.findById(tenant, id("unknown-999")));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns saved entity by ID")
        void returnsSavedEntityById() {
            runPromise(() -> store.save(tenant, entity("found-1", "col", Map.of("x", "y"))));

            Optional<EntityStore.Entity> found = runPromise(() -> store.findById(tenant, id("found-1")));
            assertThat(found).isPresent();
            assertThat(found.get().data()).containsEntry("x", "y");
        }
    }

    // ─── findByIds ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findByIds()")
    class FindByIds {

        @Test
        @DisplayName("returns all matching entities for known IDs")
        void returnsMatchingEntities() {
            runPromise(() -> store.save(tenant, entity("fi-1", "col", Map.of("k", 1))));
            runPromise(() -> store.save(tenant, entity("fi-2", "col", Map.of("k", 2))));

            List<EntityStore.Entity> found = runPromise(() ->
                    store.findByIds(tenant, List.of(id("fi-1"), id("fi-2"), id("fi-missing"))));

            assertThat(found).hasSize(2);
            assertThat(found).extracting(e -> e.id().value()).containsExactlyInAnyOrder("fi-1", "fi-2");
        }

        @Test
        @DisplayName("returns empty list for empty ID list")
        void returnsEmptyForEmptyIds() {
            List<EntityStore.Entity> found = runPromise(() -> store.findByIds(tenant, List.of()));
            assertThat(found).isEmpty();
        }
    }

    // ─── delete ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("deletes entity so subsequent findById returns empty")
        void deletedEntityIsNotFound() {
            runPromise(() -> store.save(tenant, entity("del-1", "col", Map.of())));
            runPromise(() -> store.delete(tenant, id("del-1")));

            Optional<EntityStore.Entity> found = runPromise(() -> store.findById(tenant, id("del-1")));
            assertThat(found).isEmpty();
        }

        @Test
        @DisplayName("delete on non-existent ID completes without error")
        void deleteNonExistentIsIdempotent() {
            // Should not throw
            runPromise(() -> store.delete(tenant, id("never-existed")));
        }
    }

    // ─── exists ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("exists()")
    class Exists {

        @Test
        @DisplayName("returns false for non-existent entity")
        void returnsFalseForNonExistentEntity() {
            Boolean exists = runPromise(() -> store.exists(tenant, id("no-such-entity")));
            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("returns true after saving entity")
        void returnsTrueAfterSave() {
            runPromise(() -> store.save(tenant, entity("exists-1", "col", Map.of())));
            Boolean exists = runPromise(() -> store.exists(tenant, id("exists-1")));
            assertThat(exists).isTrue();
        }
    }

    // ─── Tenant isolation ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("tenant isolation")
    class TenantIsolation {

        @Test
        @DisplayName("entity saved in one tenant is not visible in another")
        void entitySavedInTenantAIsNotVisibleFromTenantB() {
            TenantContext tenantA = createTenant("tenant-alpha");
            TenantContext tenantB = createTenant("tenant-beta");

            runPromise(() -> store.save(tenantA, entity("shared-id", "col", Map.of("owner", "alpha"))));

            Optional<EntityStore.Entity> fromB = runPromise(() -> store.findById(tenantB, id("shared-id")));
            assertThat(fromB).isEmpty();
        }
    }

    // ─── Null safety ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("null safety")
    class NullSafety {

        @Test
        @DisplayName("save with null tenant throws NullPointerException")
        void saveWithNullTenantThrows() {
            assertThatThrownBy(() -> runPromise(() -> store.save(null, entity("x", "col", Map.of()))))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("save with null entity throws NullPointerException")
        void saveWithNullEntityThrows() {
            assertThatThrownBy(() -> runPromise(() -> store.save(tenant, null)))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // ─── Helper ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    protected <T> T runPromise(java.util.concurrent.Callable<io.activej.promise.Promise<T>> supplier) {
        try {
            return supplier.call().getResult();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
