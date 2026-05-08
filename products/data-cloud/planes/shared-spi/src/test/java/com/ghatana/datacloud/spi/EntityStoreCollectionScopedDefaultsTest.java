package com.ghatana.datacloud.spi;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EntityStore collection-scoped default methods")
class EntityStoreCollectionScopedDefaultsTest extends EventloopTestBase {

    private final EntityStore legacyOnlyStore = new EntityStore() {
        @Override
        public Promise<Entity> save(TenantContext tenant, Entity entity) {
            return Promise.of(entity);
        }

        @Override
        public Promise<BatchResult<String>> saveBatch(TenantContext tenant, List<Entity> entities) {
            return Promise.of(BatchResult.success(entities.size()));
        }

        @Override
        public Promise<Optional<Entity>> findById(TenantContext tenant, EntityId id) {
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<List<Entity>> findByIds(TenantContext tenant, List<EntityId> ids) {
            return Promise.of(List.of());
        }

        @Override
        public Promise<QueryResult> query(TenantContext tenant, QuerySpec query) {
            return Promise.of(QueryResult.of(List.of(), 0));
        }

        @Override
        public Promise<Void> delete(TenantContext tenant, EntityId id) {
            return Promise.of(null);
        }

        @Override
        public Promise<BatchResult<String>> deleteBatch(TenantContext tenant, List<EntityId> ids) {
            return Promise.of(BatchResult.success(ids.size()));
        }

        @Override
        public Promise<Long> count(TenantContext tenant, QuerySpec query) {
            return Promise.of(0L);
        }

        @Override
        public Promise<Boolean> exists(TenantContext tenant, EntityId id) {
            return Promise.of(false);
        }

        @Override
        public Promise<List<String>> listCollections(TenantContext tenant) {
            return Promise.of(List.of());
        }
    };

    @Test
    @DisplayName("findByRef fails fast when provider does not implement collection-scoped lookup")
    void findByRefFailsFastByDefault() {
        assertThatThrownBy(() -> runPromise(() -> legacyOnlyStore.findByRef(
            TenantContext.of("tenant-default"),
            EntityStore.EntityRef.of("orders", "id-1"))))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("findByRef");
    }

    @Test
    @DisplayName("deleteByRef fails fast when provider does not implement collection-scoped delete")
    void deleteByRefFailsFastByDefault() {
        assertThatThrownBy(() -> runPromise(() -> legacyOnlyStore.deleteByRef(
            TenantContext.of("tenant-default"),
            EntityStore.EntityRef.of("orders", "id-1"))))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("deleteByRef");
    }

    @Test
    @DisplayName("existsByRef fails fast when provider does not implement collection-scoped existence")
    void existsByRefFailsFastByDefault() {
        assertThatThrownBy(() -> runPromise(() -> legacyOnlyStore.existsByRef(
            TenantContext.of("tenant-default"),
            EntityStore.EntityRef.of("orders", "id-1"))))
            .isInstanceOf(UnsupportedOperationException.class)
            .hasMessageContaining("existsByRef");
    }
}
