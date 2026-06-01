package com.ghatana.datacloud.launcher.http;

import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.EntityRepository;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import io.activej.promise.Promise;
import io.activej.promise.Promises;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * @doc.type class
 * @doc.purpose Bridge the launcher EntityStore SPI to the EntityRepository port used by mastery persistence
 * @doc.layer product
 * @doc.pattern Adapter
 */
final class DataCloudEntityRepositoryAdapter implements EntityRepository {

    private final EntityStore entityStore;

    DataCloudEntityRepositoryAdapter(@NotNull EntityStore entityStore) {
        this.entityStore = entityStore;
    }

    @Override
    public Promise<Optional<Entity>> findById(String tenantId, String collectionName, UUID entityId) {
        return entityStore.findByRef(TenantContext.of(tenantId), EntityStore.EntityRef.of(collectionName, entityId.toString()))
                .map(optional -> optional.map(entity -> toDomainEntity(tenantId, entity)));
    }

    @Override
    public Promise<List<Entity>> findAll(
            String tenantId,
            String collectionName,
            Map<String, Object> filter,
            String sort,
            int offset,
            int limit) {
        return entityStore.query(TenantContext.of(tenantId), toQuerySpec(collectionName, filter, sort, offset, limit))
                .map(result -> result.entities().stream()
                        .map(entity -> toDomainEntity(tenantId, entity))
                        .toList());
    }

    @Override
    public Promise<Entity> save(String tenantId, Entity entity) {
        return entityStore.save(TenantContext.of(tenantId), toStoreEntity(entity))
                .map(saved -> toDomainEntity(tenantId, saved));
    }

    @Override
    public Promise<Void> delete(String tenantId, String collectionName, UUID entityId) {
        return entityStore.deleteByRef(TenantContext.of(tenantId), EntityStore.EntityRef.of(collectionName, entityId.toString()));
    }

    @Override
    public Promise<Boolean> exists(String tenantId, String collectionName, UUID entityId) {
        return entityStore.existsByRef(TenantContext.of(tenantId), EntityStore.EntityRef.of(collectionName, entityId.toString()));
    }

    @Override
    public Promise<Long> count(String tenantId, String collectionName) {
        return entityStore.count(TenantContext.of(tenantId), toQuerySpec(collectionName, Map.of(), null, 0, 1));
    }

    @Override
    public Promise<Long> countByFilter(String tenantId, String collectionName, Map<String, Object> filter) {
        return entityStore.count(TenantContext.of(tenantId), toQuerySpec(collectionName, filter, null, 0, 1));
    }

    @Override
    public Promise<List<Entity>> findByQuery(String tenantId, String collectionName, Object querySpec) {
        if (!(querySpec instanceof EntityStore.QuerySpec storeQuerySpec)) {
            return Promise.ofException(new IllegalArgumentException("Expected EntityStore.QuerySpec"));
        }
        return entityStore.query(TenantContext.of(tenantId), storeQuerySpec)
                .map(result -> result.entities().stream()
                        .map(entity -> toDomainEntity(tenantId, entity))
                        .toList());
    }

    @Override
    public Promise<List<Entity>> saveAll(String tenantId, List<Entity> entities) {
        return Promises.toList(entities.stream()
                .map(entity -> save(tenantId, entity))
                .toList());
    }

    @Override
    public Promise<Void> deleteAll(String tenantId, String collectionName, List<UUID> entityIds) {
        return Promises.all(entityIds.stream()
                .map(entityId -> delete(tenantId, collectionName, entityId))
                .toList());
    }

    @Override
    public Promise<Entity> saveWithIdempotency(String tenantId, Entity entity, String idempotencyKey) {
        // This adapter does not support idempotency - delegate to save
        // In production, use JpaEntityRepositoryImpl which has full idempotency support
        return save(tenantId, entity);
    }

    private static EntityStore.QuerySpec toQuerySpec(
            String collectionName,
            Map<String, Object> filter,
            String sort,
            int offset,
            int limit) {
        List<EntityStore.Filter> filters = filter.entrySet().stream()
                .map(entry -> EntityStore.Filter.eq(entry.getKey(), entry.getValue()))
                .toList();
        List<EntityStore.Sort> sorts = sort == null || sort.isBlank()
                ? List.of()
                : List.of(parseSort(sort));
        return EntityStore.QuerySpec.builder()
                .collection(collectionName)
                .filters(filters)
                .sorts(sorts)
                .offset(offset)
                .limit(limit)
                .build();
    }

    private static EntityStore.Sort parseSort(String sort) {
        String[] parts = sort.split(":", 2);
        EntityStore.Direction direction = parts.length > 1 && "DESC".equalsIgnoreCase(parts[1])
                ? EntityStore.Direction.DESC
                : EntityStore.Direction.ASC;
        return new EntityStore.Sort(parts[0], direction);
    }

    private static EntityStore.Entity toStoreEntity(Entity entity) {
        Instant createdAt = entity.getCreatedAt() != null ? entity.getCreatedAt() : Instant.now();
        Instant updatedAt = entity.getUpdatedAt() != null ? entity.getUpdatedAt() : createdAt;
        EntityStore.EntityMetadata metadata = new EntityStore.EntityMetadata(
                createdAt,
                updatedAt,
                Optional.ofNullable(entity.getCreatedBy()),
                Optional.ofNullable(entity.getUpdatedBy()),
                entity.getVersion() != null ? entity.getVersion() : 1);
        return EntityStore.Entity.builder()
                .id(entity.getId().toString())
                .collection(entity.getCollectionName())
                .data(entity.getData())
                .metadata(metadata)
                .build();
    }

    private static Entity toDomainEntity(String tenantId, EntityStore.Entity entity) {
        return Entity.builder()
                .id(UUID.fromString(entity.id().value()))
                .tenantId(tenantId)
                .collectionName(entity.collection())
                .data(entity.data())
                .createdAt(entity.metadata().createdAt())
                .createdBy(entity.metadata().createdBy().orElse(null))
                .updatedAt(entity.metadata().updatedAt())
                .updatedBy(entity.metadata().updatedBy().orElse(null))
                .version((int) entity.metadata().version())
                .active(true)
                .build();
    }
}