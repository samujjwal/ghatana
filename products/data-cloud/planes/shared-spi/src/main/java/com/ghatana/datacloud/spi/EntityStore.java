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
     * <p><b>DC-19: Batch Semantics</b><br>
     * By default, batch operations allow partial success - some entities may succeed while
     * others fail. The returned BatchResult provides detailed counts and per-item errors.
     * For transactional (all-or-nothing) semantics, implementations must either:
     * <ul>
     *   <li>Throw an exception if any entity fails, rolling back all writes</li>
     *   <li>Or provide a separate transactional batch method with explicit semantics</li>
     * </ul>
     *
     * @param tenant tenant context
     * @param entities entities to save
     * @return promise of batch result with success/failure counts and per-item errors
     */
    Promise<BatchResult<String>> saveBatch(TenantContext tenant, List<Entity> entities);

    /**
     * Find an entity by ID.
     *
     * <p><strong>Deprecated</strong>: Use {@link #findByRef(TenantContext, EntityRef)} for
     * collection-scoped look-up (DC-P0-001). This overload cannot disambiguate entities with the
     * same ID in different collections and will return {@link Optional#empty()} in that case.
     *
     * @param tenant tenant context
     * @param id entity ID
     * @return promise of entity if found
     * @deprecated Use {@link #findByRef(TenantContext, EntityRef)} instead.
     */
    @Deprecated
    Promise<Optional<Entity>> findById(TenantContext tenant, EntityId id);

    /**
     * Find an entity by collection-scoped reference (DC-P0-001).
     *
     * @param tenant tenant context
     * @param ref collection-scoped entity reference
     * @return promise of entity if found
     */
    default Promise<Optional<Entity>> findByRef(TenantContext tenant, EntityRef ref) {
        return Promise.ofException(new UnsupportedOperationException(
            "EntityStore.findByRef must be implemented by provider for collection-scoped identity"));
    }

    /**
     * Find multiple entities by IDs.
     *
     * <p><strong>Deprecated</strong>: Use {@link #findByRefs(TenantContext, List)} for
     * collection-scoped look-up (DC-P0-001).
     *
     * @param tenant tenant context
     * @param ids entity IDs
     * @return promise of found entities
     * @deprecated Use {@link #findByRefs(TenantContext, List)} instead.
     */
    @Deprecated
    Promise<List<Entity>> findByIds(TenantContext tenant, List<EntityId> ids);

    /**
     * Find multiple entities by collection-scoped references (DC-P0-001).
     *
     * @param tenant tenant context
     * @param refs collection-scoped entity references
     * @return promise of found entities
     */
    default Promise<List<Entity>> findByRefs(TenantContext tenant, List<EntityRef> refs) {
        // Default: delegate one by one for backward compatibility.
        return io.activej.promise.Promises.toList(
            refs.stream()
                .map(ref -> findByRef(tenant, ref).map(opt -> opt.orElse(null)))
                .toList()
        ).map(list -> list.stream().filter(java.util.Objects::nonNull).toList());
    }

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
     * <p><strong>Deprecated</strong>: Use {@link #deleteByRef(TenantContext, EntityRef)} for
     * collection-scoped delete (DC-P0-001). This overload cannot safely target a specific
     * collection when the same entity ID exists in multiple collections.
     *
     * @param tenant tenant context
     * @param id entity ID
     * @return promise completing when deleted
     * @deprecated Use {@link #deleteByRef(TenantContext, EntityRef)} instead.
     */
    @Deprecated
    Promise<Void> delete(TenantContext tenant, EntityId id);

    /**
     * Delete an entity by collection-scoped reference (DC-P0-001).
     *
     * @param tenant tenant context
     * @param ref collection-scoped entity reference
     * @return promise completing when deleted
     */
    default Promise<Void> deleteByRef(TenantContext tenant, EntityRef ref) {
        return Promise.ofException(new UnsupportedOperationException(
            "EntityStore.deleteByRef must be implemented by provider for collection-scoped identity"));
    }

    /**
     * Delete multiple entities by IDs.
     *
     * <p><strong>Deprecated</strong>: Use {@link #deleteByRefs(TenantContext, List)} for
     * collection-scoped batch delete (DC-P0-001).
     *
     * <p><b>DC-19: Batch Semantics</b><br>
     * By default, batch operations allow partial success - some entities may succeed while
     * others fail. The returned BatchResult provides detailed counts and per-item errors.
     * For transactional (all-or-nothing) semantics, implementations must either:
     * <ul>
     *   <li>Throw an exception if any entity fails, rolling back all deletes</li>
     *   <li>Or provide a separate transactional batch method with explicit semantics</li>
     * </ul>
     *
     * @param tenant tenant context
     * @param ids entity IDs
     * @return promise of batch result with actual deleted count
     * @deprecated Use {@link #deleteByRefs(TenantContext, List)} instead.
     */
    @Deprecated
    Promise<BatchResult<String>> deleteBatch(TenantContext tenant, List<EntityId> ids);

    /**
     * Delete multiple entities by collection-scoped references (DC-P0-001).
     *
     * @param tenant tenant context
     * @param refs collection-scoped entity references
     * @return promise of batch result with actual deleted count
     */
    default Promise<BatchResult<String>> deleteByRefs(TenantContext tenant, List<EntityRef> refs) {
        return io.activej.promise.Promises.toList(
            refs.stream()
                .map(ref -> deleteByRef(tenant, ref).map(v -> 1, e -> 0))
                .toList()
        ).map(results -> {
            int deleted = results.stream().mapToInt(Integer::intValue).sum();
            return new BatchResult<>(refs.size(), deleted, refs.size() - deleted, List.of());
        });
    }

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
     * <p><strong>Deprecated</strong>: Use {@link #existsByRef(TenantContext, EntityRef)} for
     * collection-scoped existence check (DC-P0-001).
     *
     * @param tenant tenant context
     * @param id entity ID
     * @return promise of existence check
     * @deprecated Use {@link #existsByRef(TenantContext, EntityRef)} instead.
     */
    @Deprecated
    Promise<Boolean> exists(TenantContext tenant, EntityId id);

    /**
     * Check if an entity exists by collection-scoped reference (DC-P0-001).
     *
     * @param tenant tenant context
     * @param ref collection-scoped entity reference
     * @return promise of existence check
     */
    default Promise<Boolean> existsByRef(TenantContext tenant, EntityRef ref) {
        return Promise.ofException(new UnsupportedOperationException(
            "EntityStore.existsByRef must be implemented by provider for collection-scoped identity"));
    }

    /**
     * List all distinct collection names for a tenant.
     *
     * @param tenant tenant context
     * @return promise of distinct collection names
     */
    Promise<List<String>> listCollections(TenantContext tenant);

    // ==================== Supporting Types ====================

    /**
     * Collection-scoped entity reference. Combines collection name and entity ID to uniquely
     * identify an entity within a tenant (DC-P0-001).
     *
     * @param collection the collection the entity belongs to
     * @param entityId   the entity's ID within that collection
     */
    record EntityRef(String collection, EntityId entityId) {

        public EntityRef {
            Objects.requireNonNull(collection, "collection must not be null");
            Objects.requireNonNull(entityId, "entityId must not be null");
        }

        /**
         * Convenience factory.
         *
         * @param collection collection name
         * @param entityId   raw entity id string
         * @return EntityRef instance
         */
        public static EntityRef of(String collection, String entityId) {
            return new EntityRef(collection, EntityId.of(entityId));
        }
    }

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
        int limit,
        String search,
        List<String> projections,
        ConsistencyLevel consistencyLevel,
        String freshnessHint
    ) {
        /** Default page size when no limit is specified. */
        public static final int DEFAULT_LIMIT = 100;

        /**
         * Hard ceiling on limit to protect against runaway queries.
         * Callers requesting more than this will receive an {@link IllegalArgumentException}.
         */
        public static final int MAX_LIMIT = 10_000;

        public QuerySpec {
            Objects.requireNonNull(collection, "collection required");
            filters = filters != null ? List.copyOf(filters) : List.of();
            sorts = sorts != null ? List.copyOf(sorts) : List.of();
            if (offset < 0) offset = 0;
            if (limit < 0) {
                throw new IllegalArgumentException("limit must be >= 0");
            }
            if (limit == 0) limit = DEFAULT_LIMIT;
            if (limit > MAX_LIMIT) {
                throw new IllegalArgumentException(
                    "limit " + limit + " exceeds maximum allowed value " + MAX_LIMIT);
            }
            projections = projections != null ? List.copyOf(projections) : List.of();
            consistencyLevel = consistencyLevel != null ? consistencyLevel : ConsistencyLevel.STRONG;
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String collection;
            private List<Filter> filters = List.of();
            private List<Sort> sorts = List.of();
            private int offset = 0;
            private int limit = DEFAULT_LIMIT;
            private String search;
            private List<String> projections = List.of();
            private ConsistencyLevel consistencyLevel = ConsistencyLevel.STRONG;
            private String freshnessHint;

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

            public Builder search(String search) {
                this.search = search;
                return this;
            }

            public Builder projections(List<String> projections) {
                this.projections = projections;
                return this;
            }

            public Builder consistencyLevel(ConsistencyLevel consistencyLevel) {
                this.consistencyLevel = consistencyLevel;
                return this;
            }

            public Builder freshnessHint(String freshnessHint) {
                this.freshnessHint = freshnessHint;
                return this;
            }

            public QuerySpec build() {
                return new QuerySpec(collection, filters, sorts, offset, limit, search, projections, consistencyLevel, freshnessHint);
            }
        }
    }

    enum ConsistencyLevel {
        STRONG, EVENTUAL, BOUNDED_STALENESS
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

        public static Filter notIn(String field, List<?> values) {
            return new Filter(field, Operator.NOT_IN, values);
        }

        public static Filter isNull(String field) {
            return new Filter(field, Operator.IS_NULL, null);
        }

        public static Filter isNotNull(String field) {
            return new Filter(field, Operator.IS_NOT_NULL, null);
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

}
