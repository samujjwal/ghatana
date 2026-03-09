package com.ghatana.datacloud.spi;

import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Entity Store SPI - Core storage interface for entities.
 *
 * <p>This is the primary storage interface owned by Data-Cloud.
 * Implementations can be provided for different backends:
 * <ul>
 *   <li>PostgreSQL</li>
 *   <li>Redis</li>
 *   <li>S3</li>
 *   <li>In-memory (for testing)</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Core entity storage abstraction
 * @doc.layer spi
 * @doc.pattern Service Provider Interface, Repository
 * @since 1.0.0
 */
public interface EntityStore {

    // ==================== CRUD Operations ====================

    /**
     * Save an entity (insert or update).
     *
     * @param tenant tenant context
     * @param entity entity to save
     * @return promise of saved entity with generated ID if new
     */
    Promise<Entity> save(TenantContext tenant, Entity entity);

    /**
     * Save multiple entities in batch.
     *
     * @param tenant tenant context
     * @param entities entities to save
     * @return promise of batch result
     */
    Promise<BatchResult> saveBatch(TenantContext tenant, List<Entity> entities);

    /**
     * Find an entity by ID.
     *
     * @param tenant tenant context
     * @param id entity ID
     * @return promise of entity if found
     */
    Promise<Optional<Entity>> findById(TenantContext tenant, EntityId id);

    /**
     * Find multiple entities by IDs.
     *
     * @param tenant tenant context
     * @param ids entity IDs
     * @return promise of found entities
     */
    Promise<List<Entity>> findByIds(TenantContext tenant, List<EntityId> ids);

    /**
     * Query entities with criteria.
     *
     * @param tenant tenant context
     * @param query query specification
     * @return promise of matching entities
     */
    Promise<QueryResult> query(TenantContext tenant, QuerySpec query);

    /**
     * Delete an entity by ID.
     *
     * @param tenant tenant context
     * @param id entity ID
     * @return promise completing when deleted
     */
    Promise<Void> delete(TenantContext tenant, EntityId id);

    /**
     * Delete multiple entities by IDs.
     *
     * @param tenant tenant context
     * @param ids entity IDs
     * @return promise of batch result
     */
    Promise<BatchResult> deleteBatch(TenantContext tenant, List<EntityId> ids);

    /**
     * Count entities matching a query.
     *
     * @param tenant tenant context
     * @param query query specification
     * @return promise of count
     */
    Promise<Long> count(TenantContext tenant, QuerySpec query);

    /**
     * Check if an entity exists.
     *
     * @param tenant tenant context
     * @param id entity ID
     * @return promise of existence check
     */
    Promise<Boolean> exists(TenantContext tenant, EntityId id);

    // ==================== Supporting Types ====================

    /**
     * Entity identifier.
     */
    record EntityId(String value) {
        public EntityId {
            Objects.requireNonNull(value, "value required");
            if (value.isBlank()) {
                throw new IllegalArgumentException("entity ID cannot be blank");
            }
        }

        public static EntityId of(String value) {
            return new EntityId(value);
        }

        public static EntityId random() {
            return new EntityId(java.util.UUID.randomUUID().toString());
        }
    }

    /**
     * Entity data structure.
     */
    record Entity(
        EntityId id,
        String collection,
        Map<String, Object> data,
        EntityMetadata metadata
    ) {
        public Entity {
            Objects.requireNonNull(id, "id required");
            Objects.requireNonNull(collection, "collection required");
            data = data != null ? Map.copyOf(data) : Map.of();
            metadata = metadata != null ? metadata : EntityMetadata.empty();
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private EntityId id = EntityId.random();
            private String collection;
            private Map<String, Object> data = Map.of();
            private EntityMetadata metadata = EntityMetadata.empty();

            public Builder id(EntityId id) {
                this.id = id;
                return this;
            }

            public Builder id(String id) {
                this.id = EntityId.of(id);
                return this;
            }

            public Builder collection(String collection) {
                this.collection = collection;
                return this;
            }

            public Builder data(Map<String, Object> data) {
                this.data = data;
                return this;
            }

            public Builder metadata(EntityMetadata metadata) {
                this.metadata = metadata;
                return this;
            }

            public Entity build() {
                return new Entity(id, collection, data, metadata);
            }
        }
    }

    /**
     * Entity metadata.
     */
    record EntityMetadata(
        java.time.Instant createdAt,
        java.time.Instant updatedAt,
        Optional<String> createdBy,
        Optional<String> updatedBy,
        long version
    ) {
        public EntityMetadata {
            Objects.requireNonNull(createdAt, "createdAt required");
            Objects.requireNonNull(updatedAt, "updatedAt required");
            createdBy = createdBy != null ? createdBy : Optional.empty();
            updatedBy = updatedBy != null ? updatedBy : Optional.empty();
        }

        public static EntityMetadata empty() {
            var now = java.time.Instant.now();
            return new EntityMetadata(now, now, Optional.empty(), Optional.empty(), 1);
        }

        public EntityMetadata withUpdate(String updatedBy) {
            return new EntityMetadata(
                createdAt, java.time.Instant.now(),
                this.createdBy, Optional.ofNullable(updatedBy),
                version + 1
            );
        }
    }

    /**
     * Query specification.
     */
    record QuerySpec(
        String collection,
        List<Filter> filters,
        List<Sort> sorts,
        int offset,
        int limit
    ) {
        public QuerySpec {
            Objects.requireNonNull(collection, "collection required");
            filters = filters != null ? List.copyOf(filters) : List.of();
            sorts = sorts != null ? List.copyOf(sorts) : List.of();
            if (offset < 0) offset = 0;
            if (limit <= 0) limit = 100;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String collection;
            private List<Filter> filters = List.of();
            private List<Sort> sorts = List.of();
            private int offset = 0;
            private int limit = 100;

            public Builder collection(String collection) {
                this.collection = collection;
                return this;
            }

            public Builder filters(List<Filter> filters) {
                this.filters = filters;
                return this;
            }

            public Builder filter(Filter filter) {
                this.filters = List.of(filter);
                return this;
            }

            public Builder sorts(List<Sort> sorts) {
                this.sorts = sorts;
                return this;
            }

            public Builder sort(Sort sort) {
                this.sorts = List.of(sort);
                return this;
            }

            public Builder offset(int offset) {
                this.offset = offset;
                return this;
            }

            public Builder limit(int limit) {
                this.limit = limit;
                return this;
            }

            public QuerySpec build() {
                return new QuerySpec(collection, filters, sorts, offset, limit);
            }
        }
    }

    /**
     * Query filter.
     */
    record Filter(String field, Operator operator, Object value) {
        public Filter {
            Objects.requireNonNull(field, "field required");
            Objects.requireNonNull(operator, "operator required");
        }

        public static Filter eq(String field, Object value) {
            return new Filter(field, Operator.EQ, value);
        }

        public static Filter ne(String field, Object value) {
            return new Filter(field, Operator.NE, value);
        }

        public static Filter gt(String field, Object value) {
            return new Filter(field, Operator.GT, value);
        }

        public static Filter gte(String field, Object value) {
            return new Filter(field, Operator.GTE, value);
        }

        public static Filter lt(String field, Object value) {
            return new Filter(field, Operator.LT, value);
        }

        public static Filter lte(String field, Object value) {
            return new Filter(field, Operator.LTE, value);
        }

        public static Filter like(String field, String pattern) {
            return new Filter(field, Operator.LIKE, pattern);
        }

        public static Filter in(String field, List<?> values) {
            return new Filter(field, Operator.IN, values);
        }
    }

    /**
     * Filter operators.
     */
    enum Operator {
        EQ, NE, GT, GTE, LT, LTE, LIKE, IN, NOT_IN, IS_NULL, IS_NOT_NULL
    }

    /**
     * Sort specification.
     */
    record Sort(String field, Direction direction) {
        public Sort {
            Objects.requireNonNull(field, "field required");
            Objects.requireNonNull(direction, "direction required");
        }

        public static Sort asc(String field) {
            return new Sort(field, Direction.ASC);
        }

        public static Sort desc(String field) {
            return new Sort(field, Direction.DESC);
        }
    }

    /**
     * Sort direction.
     */
    enum Direction {
        ASC, DESC
    }

    /**
     * Query result.
     */
    record QueryResult(
        List<Entity> entities,
        long totalCount,
        boolean hasMore
    ) {
        public QueryResult {
            entities = entities != null ? List.copyOf(entities) : List.of();
        }

        public static QueryResult empty() {
            return new QueryResult(List.of(), 0, false);
        }

        public static QueryResult of(List<Entity> entities) {
            return new QueryResult(entities, entities.size(), false);
        }

        public static QueryResult of(List<Entity> entities, long totalCount) {
            return new QueryResult(entities, totalCount, entities.size() < totalCount);
        }
    }

    /**
     * Batch operation result.
     */
    record BatchResult(
        int totalCount,
        int successCount,
        int failureCount,
        List<BatchError> errors
    ) {
        public BatchResult {
            errors = errors != null ? List.copyOf(errors) : List.of();
        }

        public boolean isFullySuccessful() {
            return failureCount == 0;
        }

        public static BatchResult success(int count) {
            return new BatchResult(count, count, 0, List.of());
        }

        public static BatchResult failure(int count, List<BatchError> errors) {
            return new BatchResult(count, 0, count, errors);
        }
    }

    /**
     * Batch error detail.
     */
    record BatchError(
        int index,
        String entityId,
        String errorCode,
        String errorMessage
    ) {}
}
