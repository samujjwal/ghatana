package com.ghatana.datacloud;

import com.ghatana.datacloud.spi.EntityStore;
// Architectural decision (DC-DRY-002): Data Cloud uses its own SPI EventLogStore
// (com.ghatana.datacloud.spi.EventLogStore) as the canonical contract for this product layer.
// Use EventLogStoreAdapters to bridge with com.ghatana.platform.domain.eventstore.EventLogStore
// when interoperating with platform-layer consumers.
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.entity.storage.FilterCriteria;
import io.activej.promise.Promise;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Data-Cloud Client Interface - All data operations.
 *
 * <p>This is the primary interface for interacting with Data-Cloud.
 * It provides 11 methods as specified in the architecture:
 * <ul>
 *   <li>Entity operations (4 methods)</li>
 *   <li>Event operations (3 methods)</li>
 *   <li>Lifecycle (1 method)</li>
 * </ul>
 *
 * @doc.type interface
 * @doc.purpose Primary Data-Cloud interface for data operations
 * @doc.layer api
 * @doc.pattern Facade
 * @since 1.0.0
 */
public interface DataCloudClient extends AutoCloseable {

    // ==================== Entity Operations (4 methods) ====================

    /**
     * Save an entity (insert or update).
     *
     * @param tenantId tenant identifier
     * @param collection collection name
     * @param data entity data
     * @return promise of saved entity
     */
    Promise<Entity> save(String tenantId, String collection, Map<String, Object> data);

    /**
     * Find an entity by ID.
     *
     * @param tenantId tenant identifier
     * @param collection collection name
     * @param id entity ID
     * @return promise of entity if found
     */
    Promise<Optional<Entity>> findById(String tenantId, String collection, String id);

    /**
     * Query entities with criteria.
     *
     * @param tenantId tenant identifier
     * @param collection collection name
     * @param query query specification
     * @return promise of matching entities
     */
    Promise<List<Entity>> query(String tenantId, String collection, Query query);

    /**
     * Delete an entity by ID.
     *
     * @param tenantId tenant identifier
     * @param collection collection name
     * @param id entity ID
     * @return promise completing when deleted
     */
    Promise<Void> delete(String tenantId, String collection, String id);

    /**
     * List all entities in a collection.
     *
     * @param collection collection name
     * @param tenantId tenant identifier
     * @return promise of list of entities
     */
    default Promise<List<Entity>> listEntities(String collection, String tenantId) {
        return query(tenantId, collection, Query.all());
    }

    /**
     * Get a single entity by ID.
     *
     * @param collection collection name
     * @param id entity ID
     * @param tenantId tenant identifier
     * @return promise of entity
     */
    default Promise<Entity> getEntity(String collection, String id, String tenantId) {
        return findById(tenantId, collection, id)
            .then(opt -> opt.map(Promise::of).orElseGet(() -> Promise.ofException(
                new IllegalArgumentException("Entity not found: " + id))));
    }

    /**
     * Create a new entity.
     *
     * @param collection collection name
     * @param id entity ID
     * @param data entity data
     * @param tenantId tenant identifier
     * @return promise of created entity
     */
    default Promise<Entity> createEntity(String collection, String id, Map<String, Object> data, String tenantId) {
        return save(tenantId, collection, data);
    }

    /**
     * Update an existing entity.
     *
     * @param collection collection name
     * @param id entity ID
     * @param data entity data
     * @param tenantId tenant identifier
     * @return promise of updated entity
     */
    default Promise<Entity> updateEntity(String collection, String id, Map<String, Object> data, String tenantId) {
        return save(tenantId, collection, data);
    }

    /**
     * Delete an entity by ID.
     *
     * @param collection collection name
     * @param id entity ID
     * @param tenantId tenant identifier
     * @return promise completing when deleted
     */
    default Promise<Void> deleteEntity(String collection, String id, String tenantId) {
        return delete(tenantId, collection, id);
    }

    // ==================== Event Operations (3 methods) ====================

    /**
     * Append an event to the event log.
     *
     * @param tenantId tenant identifier
     * @param event event to append
     * @return promise of assigned offset
     */
    Promise<Offset> appendEvent(String tenantId, Event event);

    /**
     * Query events with criteria.
     *
     * @param tenantId tenant identifier
     * @param query event query specification
     * @return promise of matching events
     */
    Promise<List<Event>> queryEvents(String tenantId, EventQuery query);

    /**
     * Tail events from a specific offset.
     *
     * @param tenantId tenant identifier
     * @param request tail request
     * @param handler event handler
     * @return subscription for cancellation
     */
    Subscription tailEvents(String tenantId, TailRequest request, Consumer<Event> handler);

    /**
     * Read a single event at the given offset.
     *
     * <p>Convenience method built on {@link #queryEvents}. Returns the first event
     * at or after {@code offset}, or empty if no such event exists.
     *
     * @param tenantId   tenant identifier
     * @param offset     log offset to read from
     * @return promise of the event at that offset, or empty
     */
    default Promise<Optional<Event>> readEvent(String tenantId, long offset) {
        return queryEvents(tenantId, EventQuery.fromOffset(offset))
            .map(events -> events.isEmpty() ? Optional.empty() : Optional.of(events.get(0)));
    }

    /**
     * Poll for events starting from {@code fromOffset} (non-streaming variant).
     *
     * <p>Returns all currently available events from the given offset.
     * Unlike the callback-based {@link #tailEvents(String, TailRequest, Consumer)}, this
     * method returns a bounded list suitable for pagination or polling loops.
     *
     * @param tenantId   tenant identifier
     * @param fromOffset log offset to start from (inclusive)
     * @return promise of events available from that offset
     */
    default Promise<List<Event>> tailEvents(String tenantId, long fromOffset) {
        return queryEvents(tenantId, EventQuery.fromOffset(fromOffset));
    }

    /**
     * Replay events between two offsets.
     *
     * @param tenantId   tenant identifier
     * @param fromOffset start offset (inclusive)
     * @param toOffset   end offset (inclusive upper bound)
     * @return promise of events in that range
     */
    default Promise<List<Event>> replayEvents(String tenantId, long fromOffset, long toOffset) {
        int limit = (int) Math.min(toOffset - fromOffset + 1, 1000);
        return queryEvents(tenantId, new EventQuery(List.of(), null, null, Offset.of(fromOffset), limit));
    }

    /**
     * Store a consumer checkpoint (commit offset) for a named stream.
     *
     * <p>Default implementation is a no-op that returns {@code true}. Override
     * in concrete implementations to durably persist checkpoints.
     *
     * @param tenantId tenant identifier
     * @param stream   stream / event-type identifier
     * @param offset   offset to commit
     * @return promise of {@code true} when the checkpoint is stored
     */
    default Promise<Boolean> checkpoint(String tenantId, String stream, long offset) {
        return Promise.of(true);
    }

    // ==================== Lifecycle (1 method) ====================

    /**
     * Close the client and release resources.
     */
    @Override
    void close();

    // ==================== Access to Stores ====================

    /**
     * Get the underlying EntityStore.
     *
     * @return entity store instance
     */
    EntityStore entityStore();

    /**
     * Get the underlying EventLogStore.
     *
     * @return event log store instance
     */
    EventLogStore eventLogStore();

    // ==================== Supporting Types ====================

    /**
     * Entity data structure.
     */
    record Entity(
        String id,
        String collection,
        Map<String, Object> data,
        java.time.Instant createdAt,
        java.time.Instant updatedAt,
        long version
    ) {
        public Entity {
            java.util.Objects.requireNonNull(id, "id required");
            java.util.Objects.requireNonNull(collection, "collection required");
            data = data != null ? Map.copyOf(data) : Map.of();
            createdAt = createdAt != null ? createdAt : java.time.Instant.now();
            updatedAt = updatedAt != null ? updatedAt : createdAt;
        }

        public static Entity of(String id, String collection, Map<String, Object> data) {
            return new Entity(id, collection, data, null, null, 1);
        }
    }

    /**
     * Query specification.
     * DC-10: Aligned with QuerySpec canonical model for query unification
     * across DataCloudClient.Query, EntityStore.QuerySpec, OpenAPI, UI, and SDK.
     */
    record Query(
        List<Filter> filters,
        List<Sort> sorts,
        int offset,
        int limit,
        List<String> projections,
        java.time.Instant timeWindowStart,
        java.time.Instant timeWindowEnd,
        Map<String, String> metadata,
        String consistencyLevel,
        String freshnessHint
    ) {
        public Query {
            filters = filters != null ? List.copyOf(filters) : List.of();
            sorts = sorts != null ? List.copyOf(sorts) : List.of();
            if (offset < 0) offset = 0;
            if (limit <= 0) limit = 100;
            projections = projections != null ? List.copyOf(projections) : List.of();
            metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
            consistencyLevel = consistencyLevel != null ? consistencyLevel : "STRONG";
        }

        public static Query all() {
            return new Query(List.of(), List.of(), 0, 100, List.of(), null, null, Map.of(), "STRONG", null);
        }

        public static Query limit(int limit) {
            return new Query(List.of(), List.of(), 0, limit, List.of(), null, null, Map.of(), "STRONG", null);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private List<Filter> filters = List.of();
            private List<Sort> sorts = List.of();
            private int offset = 0;
            private int limit = 100;
            private List<String> projections = List.of();
            private java.time.Instant timeWindowStart;
            private java.time.Instant timeWindowEnd;
            private Map<String, String> metadata = Map.of();
            private String consistencyLevel = "STRONG";
            private String freshnessHint;

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

            public Builder offset(int offset) {
                this.offset = offset;
                return this;
            }

            public Builder limit(int limit) {
                this.limit = limit;
                return this;
            }

            public Builder projections(List<String> projections) {
                this.projections = projections;
                return this;
            }

            public Builder projection(String... fields) {
                this.projections = List.of(fields);
                return this;
            }

            public Builder timeWindow(java.time.Instant start, java.time.Instant end) {
                this.timeWindowStart = start;
                this.timeWindowEnd = end;
                return this;
            }

            public Builder metadata(String key, String value) {
                Map<String, String> newMap = new java.util.LinkedHashMap<>(this.metadata);
                newMap.put(key, value);
                this.metadata = newMap;
                return this;
            }

            public Builder consistencyLevel(String consistencyLevel) {
                this.consistencyLevel = consistencyLevel;
                return this;
            }

            public Builder freshnessHint(String freshnessHint) {
                this.freshnessHint = freshnessHint;
                return this;
            }

            public Query build() {
                return new Query(filters, sorts, offset, limit, projections, timeWindowStart, timeWindowEnd, metadata, consistencyLevel, freshnessHint);
            }
        }
    }

    /**
     * Query filter.
     */
    record Filter(String field, FilterCriteria.Operator operator, Object value) {
        public static Filter eq(String field, Object value) {
            return new Filter(field, FilterCriteria.Operator.EQ, value);
        }

        public static Filter ne(String field, Object value) {
            return new Filter(field, FilterCriteria.Operator.NE, value);
        }

        public static Filter gt(String field, Object value) {
            return new Filter(field, FilterCriteria.Operator.GT, value);
        }

        public static Filter gte(String field, Object value) {
            return new Filter(field, FilterCriteria.Operator.GTE, value);
        }

        public static Filter lt(String field, Object value) {
            return new Filter(field, FilterCriteria.Operator.LT, value);
        }

        public static Filter lte(String field, Object value) {
            return new Filter(field, FilterCriteria.Operator.LTE, value);
        }

        public static Filter like(String field, String pattern) {
            return new Filter(field, FilterCriteria.Operator.LIKE, pattern);
        }

        public static Filter in(String field, List<?> values) {
            return new Filter(field, FilterCriteria.Operator.IN, values);
        }
    }

    /**
     * Sort specification.
     */
    record Sort(String field, boolean ascending) {
        public static Sort asc(String field) {
            return new Sort(field, true);
        }

        public static Sort desc(String field) {
            return new Sort(field, false);
        }
    }

    /**
     * Canonical event envelope (DC-P0-003).
     *
     * <p>Required fields: {@code type}, {@code source}.
     * All other fields are optional and default to empty/safe values.
     */
    record Event(
        String type,
        Map<String, Object> payload,
        Map<String, String> headers,
        java.time.Instant timestamp,
        // DC-P0-003: canonical envelope fields
        Optional<String> source,
        Optional<String> subjectType,
        Optional<String> subjectId,
        Optional<String> schemaVersion,
        Optional<String> correlationId,
        Optional<String> causationId,
        Optional<String> actor,
        Optional<String> classification,
        Optional<String> policyContext,
        Optional<String> provenance,
        Optional<String> traceContext
    ) {
        public Event {
            java.util.Objects.requireNonNull(type, "type required");
            payload       = payload       != null ? Map.copyOf(payload)   : Map.of();
            headers       = headers       != null ? Map.copyOf(headers)   : Map.of();
            timestamp     = timestamp     != null ? timestamp             : java.time.Instant.now();
            source        = source        != null ? source                : Optional.empty();
            subjectType   = subjectType   != null ? subjectType           : Optional.empty();
            subjectId     = subjectId     != null ? subjectId             : Optional.empty();
            schemaVersion = schemaVersion != null ? schemaVersion         : Optional.empty();
            correlationId = correlationId != null ? correlationId         : Optional.empty();
            causationId   = causationId   != null ? causationId           : Optional.empty();
            actor         = actor         != null ? actor                 : Optional.empty();
            classification = classification != null ? classification      : Optional.empty();
            policyContext = policyContext != null ? policyContext          : Optional.empty();
            provenance    = provenance    != null ? provenance            : Optional.empty();
            traceContext  = traceContext  != null ? traceContext           : Optional.empty();
        }

        /**
         * Minimal factory retained for backward compatibility.
         * Prefer {@link Builder#source(String)} and explicit envelope fields.
         */
        @Deprecated(since = "2026.05", forRemoval = false)
        public static Event of(String type, Map<String, Object> payload) {
            return builder().type(type).payload(payload).build();
        }

        /**
         * DC-P0-003: Validate required envelope fields.
         *
         * @throws IllegalArgumentException when a required field is absent
         */
        public void validate() {
            if (type == null || type.isBlank()) {
                throw new IllegalArgumentException("event.type is required");
            }
            if (source.isEmpty() || source.get().isBlank()) {
                throw new IllegalArgumentException("event.source is required for production use");
            }
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String type;
            private Map<String, Object> payload = Map.of();
            private Map<String, String> headers = Map.of();
            private java.time.Instant timestamp = java.time.Instant.now();
            private Optional<String> source        = Optional.empty();
            private Optional<String> subjectType   = Optional.empty();
            private Optional<String> subjectId     = Optional.empty();
            private Optional<String> schemaVersion = Optional.empty();
            private Optional<String> correlationId = Optional.empty();
            private Optional<String> causationId   = Optional.empty();
            private Optional<String> actor         = Optional.empty();
            private Optional<String> classification = Optional.empty();
            private Optional<String> policyContext = Optional.empty();
            private Optional<String> provenance    = Optional.empty();
            private Optional<String> traceContext  = Optional.empty();

            public Builder type(String type)                     { this.type = type; return this; }
            public Builder payload(Map<String, Object> payload)  { this.payload = payload; return this; }
            public Builder headers(Map<String, String> headers)  { this.headers = headers; return this; }
            public Builder timestamp(java.time.Instant ts)       { this.timestamp = ts; return this; }
            public Builder source(String source)                 { this.source = Optional.ofNullable(source); return this; }
            public Builder subjectType(String subjectType)       { this.subjectType = Optional.ofNullable(subjectType); return this; }
            public Builder subjectId(String subjectId)           { this.subjectId = Optional.ofNullable(subjectId); return this; }
            public Builder schemaVersion(String schemaVersion)   { this.schemaVersion = Optional.ofNullable(schemaVersion); return this; }
            public Builder correlationId(String correlationId)   { this.correlationId = Optional.ofNullable(correlationId); return this; }
            public Builder causationId(String causationId)       { this.causationId = Optional.ofNullable(causationId); return this; }
            public Builder actor(String actor)                   { this.actor = Optional.ofNullable(actor); return this; }
            public Builder classification(String classification) { this.classification = Optional.ofNullable(classification); return this; }
            public Builder policyContext(String policyContext)   { this.policyContext = Optional.ofNullable(policyContext); return this; }
            public Builder provenance(String provenance)         { this.provenance = Optional.ofNullable(provenance); return this; }
            public Builder traceContext(String traceContext)      { this.traceContext = Optional.ofNullable(traceContext); return this; }

            public Event build() {
                return new Event(type, payload, headers, timestamp,
                    source, subjectType, subjectId, schemaVersion,
                    correlationId, causationId, actor, classification,
                    policyContext, provenance, traceContext);
            }
        }
    }

    /**
     * Event query specification.
     */
    record EventQuery(
        List<String> eventTypes,
        java.time.Instant startTime,
        java.time.Instant endTime,
        Offset fromOffset,
        int limit
    ) {
        public EventQuery {
            eventTypes = eventTypes != null ? List.copyOf(eventTypes) : List.of();
            fromOffset = fromOffset != null ? fromOffset : Offset.zero();
            if (limit <= 0) limit = 100;
        }

        public EventQuery(List<String> eventTypes, java.time.Instant startTime, java.time.Instant endTime, int limit) {
            this(eventTypes, startTime, endTime, Offset.zero(), limit);
        }

        public static EventQuery all() {
            return new EventQuery(List.of(), null, null, Offset.zero(), 100);
        }

        public static EventQuery byType(String... types) {
            return new EventQuery(List.of(types), null, null, Offset.zero(), 100);
        }

        public static EventQuery fromOffset(long offset) {
            return new EventQuery(List.of(), null, null, Offset.of(offset), 100);
        }
    }

    /**
     * Tail request.
     */
    record TailRequest(
        Offset fromOffset,
        List<String> eventTypes
    ) {
        public TailRequest {
            fromOffset = fromOffset != null ? fromOffset : Offset.zero();
            eventTypes = eventTypes != null ? List.copyOf(eventTypes) : List.of();
        }

        public static TailRequest fromLatest() {
            return new TailRequest(Offset.latest(), List.of());
        }

        public static TailRequest fromBeginning() {
            return new TailRequest(Offset.zero(), List.of());
        }
    }

    /**
     * Offset in event log.
     */
    record Offset(long value) {
        public static Offset zero() {
            return new Offset(0);
        }

        public static Offset of(long value) {
            return new Offset(value);
        }

        public static Offset latest() {
            return new Offset(-1); // Special value for latest
        }
    }

    /**
     * Subscription handle.
     */
    interface Subscription {
        void cancel();
        boolean isCancelled();
    }
}
