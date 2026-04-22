package com.ghatana.datacloud.entity;

import io.activej.promise.Promise;
import jakarta.persistence.OptimisticLockException;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @doc.type class
 * @doc.purpose Coverage for tenant isolation, optimistic concurrency, soft-delete audit preservation, and DTO/persistence contract behavior
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Entity Repository Coverage Tests")
class EntityRepositoryCoverageTest extends EventloopTestBase {

    @Test
    @DisplayName("DE-1: tenant isolation blocks cross-tenant reads")
    void shouldEnforceTenantIsolation() {
        InMemoryTenantEntityRepository repository = new InMemoryTenantEntityRepository();
        Entity entity = sampleEntity("tenant-a", "orders");

        Entity saved = runPromise(() -> repository.save("tenant-a", entity));

        Optional<Entity> sameTenant = runPromise(() -> repository.findById("tenant-a", "orders", saved.getId()));
        Optional<Entity> otherTenant = runPromise(() -> repository.findById("tenant-b", "orders", saved.getId()));

        assertThat(sameTenant).isPresent();
        assertThat(otherTenant).isEmpty();
    }

    @Test
    @DisplayName("DE-2: optimistic concurrency conflict rejects stale update")
    void shouldRejectStaleVersionUpdate() {
        InMemoryTenantEntityRepository repository = new InMemoryTenantEntityRepository();
        Entity created = runPromise(() -> repository.save("tenant-a", sampleEntity("tenant-a", "orders")));

        Entity staleUpdate = created.toBuilder()
            .version(created.getVersion() - 1)
            .build();

        assertThatThrownBy(() -> runPromise(() -> repository.save("tenant-a", staleUpdate)))
            .isInstanceOf(OptimisticLockException.class)
            .hasMessageContaining("Version conflict");
    }

    @Test
    @DisplayName("DE-3: soft delete preserves audit fields and payload")
    void shouldPreserveAuditTrailOnSoftDelete() {
        InMemoryTenantEntityRepository repository = new InMemoryTenantEntityRepository();
        Entity created = runPromise(() -> repository.save("tenant-a", sampleEntity("tenant-a", "orders")));

        runPromise(() -> repository.delete("tenant-a", "orders", created.getId()));
        Optional<Entity> maybeEntity = repository.findIncludingInactive("tenant-a", "orders", created.getId());

        assertThat(maybeEntity).isPresent();
        Entity deleted = maybeEntity.orElseThrow();
        assertThat(deleted.getActive()).isFalse();
        assertThat(deleted.getUpdatedBy()).isEqualTo("system-soft-delete");
        assertThat(deleted.getData()).containsEntry("status", "NEW");
    }

    @Test
    @DisplayName("DE-4: DTO and persistence model contract stays consistent")
    void shouldKeepDtoPersistenceContract() {
        InMemoryTenantEntityRepository repository = new InMemoryTenantEntityRepository();

        Map<String, Object> dto = new HashMap<>();
        dto.put("orderId", "ORD-100");
        dto.put("customerId", "CUST-77");
        dto.put("status", "NEW");

        Entity entity = Entity.builder()
            .id(UUID.randomUUID())
            .tenantId("tenant-a")
            .collectionName("orders")
            .version(1)
            .active(true)
            .data(dto)
            .createdBy("api-user")
            .build();

        Entity saved = runPromise(() -> repository.save("tenant-a", entity));
        Entity reloaded = runPromise(() -> repository.findById("tenant-a", "orders", saved.getId())).orElseThrow();

        assertThat(DataCloudColumnNames.TENANT_ID).isEqualTo("tenant_id");
        assertThat(DataCloudColumnNames.COLLECTION_NAME).isEqualTo("collection_name");
        assertThat(reloaded.getTenantId()).isEqualTo("tenant-a");
        assertThat(reloaded.getCollectionName()).isEqualTo("orders");
        assertThat(reloaded.getData())
            .containsEntry("orderId", "ORD-100")
            .containsEntry("customerId", "CUST-77")
            .containsEntry("status", "NEW");
    }

    private static Entity sampleEntity(String tenantId, String collection) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "NEW");
        payload.put("amount", 42);

        return Entity.builder()
            .id(UUID.randomUUID())
            .tenantId(tenantId)
            .collectionName(collection)
            .version(1)
            .active(true)
            .data(payload)
            .createdBy("integration-test")
            .build();
    }

    private static final class InMemoryTenantEntityRepository implements EntityRepository {
        private final Map<UUID, Entity> store = new ConcurrentHashMap<>();

        @Override
        public Promise<Optional<Entity>> findById(String tenantId, String collectionName, UUID entityId) {
            return Promise.of(findIncludingInactive(tenantId, collectionName, entityId)
                .filter(entity -> Boolean.TRUE.equals(entity.getActive())));
        }

        Optional<Entity> findIncludingInactive(String tenantId, String collectionName, UUID entityId) {
            Entity entity = store.get(entityId);
            if (entity == null) {
                return Optional.empty();
            }
            if (!tenantId.equals(entity.getTenantId()) || !collectionName.equals(entity.getCollectionName())) {
                return Optional.empty();
            }
            return Optional.of(entity);
        }

        @Override
        public Promise<List<Entity>> findAll(String tenantId, String collectionName, Map<String, Object> filter, String sort, int offset, int limit) {
            List<Entity> result = new ArrayList<>();
            for (Entity entity : store.values()) {
                if (tenantId.equals(entity.getTenantId())
                    && collectionName.equals(entity.getCollectionName())
                    && Boolean.TRUE.equals(entity.getActive())) {
                    result.add(entity);
                }
            }
            return Promise.of(result);
        }

        @Override
        public Promise<Entity> save(String tenantId, Entity entity) {
            if (!tenantId.equals(entity.getTenantId())) {
                return Promise.ofException(new IllegalArgumentException("tenantId mismatch"));
            }

            Entity existing = store.get(entity.getId());
            if (existing != null && !existing.getVersion().equals(entity.getVersion())) {
                return Promise.ofException(new OptimisticLockException("Version conflict"));
            }

            Entity toSave;
            if (existing == null) {
                toSave = entity;
            } else {
                toSave = entity.toBuilder()
                    .version(existing.getVersion() + 1)
                    .build();
            }
            store.put(toSave.getId(), toSave);
            return Promise.of(toSave);
        }

        @Override
        public Promise<Void> delete(String tenantId, String collectionName, UUID entityId) {
            Entity existing = store.get(entityId);
            if (existing != null
                && tenantId.equals(existing.getTenantId())
                && collectionName.equals(existing.getCollectionName())) {
                Entity deleted = existing.toBuilder().build();
                deleted.softDelete("system-soft-delete");
                store.put(entityId, deleted);
            }
            return Promise.complete();
        }

        @Override
        public Promise<Boolean> exists(String tenantId, String collectionName, UUID entityId) {
            return Promise.of(findIncludingInactive(tenantId, collectionName, entityId)
                .filter(entity -> Boolean.TRUE.equals(entity.getActive()))
                .isPresent());
        }

        @Override
        public Promise<Long> count(String tenantId, String collectionName) {
            long count = store.values().stream()
                .filter(entity -> tenantId.equals(entity.getTenantId()))
                .filter(entity -> collectionName.equals(entity.getCollectionName()))
                .filter(entity -> Boolean.TRUE.equals(entity.getActive()))
                .count();
            return Promise.of(count);
        }

        @Override
        public Promise<Long> countByFilter(String tenantId, String collectionName, Map<String, Object> filter) {
            return count(tenantId, collectionName);
        }

        @Override
        public Promise<List<Entity>> findByQuery(String tenantId, String collectionName, Object querySpec) {
            return findAll(tenantId, collectionName, Map.of(), null, 0, Integer.MAX_VALUE);
        }

        @Override
        public Promise<List<Entity>> saveAll(String tenantId, List<Entity> entities) {
            Promise<List<Entity>> promise = Promise.of(new ArrayList<>());
            for (Entity entity : entities) {
                promise = promise.then(saved -> save(tenantId, entity).map(item -> {
                    saved.add(item);
                    return saved;
                }));
            }
            return promise;
        }

        @Override
        public Promise<Void> deleteAll(String tenantId, String collectionName, List<UUID> entityIds) {
            Promise<Void> promise = Promise.complete();
            for (UUID entityId : entityIds) {
                promise = promise.then(() -> delete(tenantId, collectionName, entityId));
            }
            return promise;
        }
    }
}



