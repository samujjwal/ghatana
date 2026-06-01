package com.ghatana.datacloud;

import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.domain.eventstore.EventLogStore;
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
     * <p>P3-03: Offset semantics:
     * <ul>
     *   <li>fromOffset: inclusive - events at or after this offset are included</li>
     *   <li>toOffset: inclusive upper bound - events at or before this offset are included</li>
     *   <li>toOffset = -1: special value meaning "latest available offset"</li>
     * </ul>
     *
     * <p>P3-03: This method is deprecated in favor of {@link #replay(ReplayRequest)} which
     * provides richer replay semantics including event type filtering, replay modes,
     * and idempotency keys.
     *
     * @param tenantId   tenant identifier
     * @param fromOffset start offset (inclusive)
     * @param toOffset   end offset (inclusive upper bound, -1 for latest)
     * @return promise of events in that range
     * @deprecated Use {@link #replay(ReplayRequest)} for production-grade replay semantics
     */
    @Deprecated(since = "2026.05", forRemoval = true)
    default Promise<List<Event>> replayEvents(String tenantId, long fromOffset, long toOffset) {
        if (fromOffset < 0) {
            return Promise.ofException(new IllegalArgumentException("fromOffset must be >= 0"));
        }
        if (toOffset < -1) {
            return Promise.ofException(new IllegalArgumentException("toOffset must be >= 0 or -1 for latest"));
        }
        if (toOffset >= 0 && toOffset < fromOffset) {
            return Promise.ofException(new IllegalArgumentException("toOffset must be >= fromOffset"));
        }
        
        int limit = toOffset >= 0 ? (int) Math.min(toOffset - fromOffset + 1, 1000) : 1000;
        return queryEvents(tenantId, new EventQuery(List.of(), null, null, Offset.of(fromOffset), limit));
    }

    /**
     * Store a consumer checkpoint (commit offset) for a named stream.
     *
     * <p>P3-01: Checkpoint semantics ensure exactly-once processing guarantees.
     * Checkpoints are durably stored with the following properties:
     * <ul>
     *   <li>Atomic: Checkpoints are written atomically with processing completion</li>
     *   <li>Idempotent: Storing the same checkpoint multiple times is safe</li>
     *   <li>Durable: Checkpoints survive client restarts</li>
     *   <li>Scoped: Per-tenant, per-stream isolation</li>
     * </ul>
     *
     * <p>P3-03: This method is deprecated in favor of {@link #commitCheckpoint} which
     * provides idempotency guarantees and consumer group scoping.
     *
     * @param tenantId tenant identifier
     * @param stream   stream / event-type identifier
     * @param offset   offset to commit
     * @return promise of {@code true} when the checkpoint is stored
     * @deprecated Use {@link #commitCheckpoint(String, String, String, long, String)} for production-grade checkpoint management
     */
    @Deprecated(since = "2026.05", forRemoval = true)
    default Promise<Boolean> checkpoint(String tenantId, String stream, long offset) {
        return Promise.ofException(new UnsupportedOperationException(
            "checkpoint() is deprecated. Use commitCheckpoint() with consumer group and idempotency key for production-grade checkpoint management."));
    }

    /**
     * Retrieve the last stored checkpoint for a named stream.
     *
     * <p>P3-01: Enables consumers to resume processing from last committed offset
     * after restarts or failures.
     *
     * <p>P3-03: This method is deprecated in favor of {@link #readCheckpoint} which
     * provides consumer group scoping and structured checkpoint metadata.
     *
     * @param tenantId tenant identifier
     * @param stream   stream / event-type identifier
     * @return promise of optional offset (empty if no checkpoint exists)
     * @deprecated Use {@link #readCheckpoint(String, String, String)} for production-grade checkpoint reading
     */
    @Deprecated(since = "2026.05", forRemoval = true)
    default Promise<Optional<Offset>> getLastCheckpoint(String tenantId, String stream) {
        return Promise.ofException(new UnsupportedOperationException(
            "getLastCheckpoint() is deprecated. Use readCheckpoint() with consumer group for production-grade checkpoint management."));
    }

    /**
     * Delete a checkpoint for a named stream.
     *
     * <p>P3-01: Used when a consumer no longer needs to track position
     * or when resetting processing to the beginning.
     *
     * @param tenantId tenant identifier
     * @param stream   stream / event-type identifier
     * @return promise of {@code true} if checkpoint was deleted
     */
    default Promise<Boolean> deleteCheckpoint(String tenantId, String stream) {
        return Promise.ofException(new UnsupportedOperationException(
            "deleteCheckpoint() requires consumer group context. Use EventLogStore SPI methods for production-grade checkpoint management."));
    }

    /**
     * Replay events between two offsets with optional filtering.
     *
     * <p>P3-01: Enhanced replay semantics for:
     * <ul>
     *   <li>Recovery: Replay from last checkpoint after failure</li>
     *   <li>Debugging: Replay specific event ranges for troubleshooting</li>
     *   <li>Replication: Stream events to downstream systems</li>
     *   <li>Audit: Extract event history for compliance</li>
     * </ul>
     *
     * <p>P3-03: This method is deprecated in favor of {@link #replay(ReplayRequest)}.
     *
     * @param tenantId   tenant identifier
     * @param fromOffset start offset (inclusive)
     * @param toOffset   end offset (inclusive upper bound, -1 for latest)
     * @param filter     optional filter criteria for event types
     * @return promise of events in that range
     * @deprecated Use {@link #replay(ReplayRequest)} for production-grade replay semantics
     */
    @Deprecated(since = "2026.05", forRemoval = true)
    default Promise<List<Event>> replayEvents(String tenantId, long fromOffset, long toOffset, EventReplayFilter filter) {
        if (fromOffset < 0) {
            return Promise.ofException(new IllegalArgumentException("fromOffset must be >= 0"));
        }
        if (toOffset < -1) {
            return Promise.ofException(new IllegalArgumentException("toOffset must be >= 0 or -1 for latest"));
        }
        if (toOffset >= 0 && toOffset < fromOffset) {
            return Promise.ofException(new IllegalArgumentException("toOffset must be >= fromOffset"));
        }
        
        int limit = toOffset >= 0 ? (int) Math.min(toOffset - fromOffset + 1, 1000) : 1000;
        EventQuery query = new EventQuery(
            filter != null ? filter.eventTypes() : List.of(),
            filter != null ? filter.startTime() : null,
            filter != null ? filter.endTime() : null,
            Offset.of(fromOffset),
            limit
        );
        return queryEvents(tenantId, query);
    }

    /**
     * Replay events starting from a checkpoint with automatic progress tracking.
     *
     * <p>P3-01: Combines replay with automatic checkpoint management for
     * exactly-once processing semantics. The provided handler receives events
     * and must return true to advance the checkpoint.
     *
     * <p>P3-03: This method is deprecated in favor of {@link #replay(ReplayRequest)} and
     * {@link #commitCheckpoint}.
     *
     * @param tenantId tenant identifier
     * @param stream   stream identifier for checkpoint tracking
     * @param handler  event processor (returns true to checkpoint, false to retry)
     * @return promise of events processed
     * @deprecated Use {@link #replay(ReplayRequest)} with {@link #commitCheckpoint} for production-grade replay
     */
    @Deprecated(since = "2026.05", forRemoval = true)
    default Promise<ReplayResult> replayFromCheckpoint(String tenantId, String stream, EventProcessor handler) {
        return Promise.ofException(new UnsupportedOperationException(
            "replayFromCheckpoint() is deprecated. Use replay(ReplayRequest) with commitCheckpoint() for production-grade replay."));
    }

    /**
     * Replay events with comprehensive replay semantics.
     *
     * <p>P3-03: Production-grade replay supporting:
     * <ul>
     *   <li>Bounded offset ranges with inclusive semantics</li>
     *   <li>Event type filtering</li>
     *   <li>Replay mode selection (AT_LEAST_ONCE, EXACTLY_ONCE, AT_MOST_ONCE)</li>
     *   <li>Idempotency keys for safe retry</li>
     *   <li>Consumer group scoping</li>
     * </ul>
     *
     * @param tenantId tenant identifier
     * @param request  replay request with comprehensive parameters
     * @return promise of events in the specified range
     */
    default Promise<List<Event>> replay(String tenantId, ReplayRequest request) {
        return Promise.ofException(new UnsupportedOperationException(
            "replay() requires EventLogStore SPI implementation. Use eventLogStore().replay() for production-grade replay."));
    }

    /**
     * Read a checkpoint for a specific consumer group.
     *
     * <p>P3-03: Returns structured checkpoint metadata including offset,
     * timestamp, and consumer group information.
     *
     * @param tenantId     tenant identifier
     * @param stream       stream identifier
     * @param consumerGroup consumer group name
     * @return promise of checkpoint (empty if no checkpoint exists)
     */
    default Promise<Optional<Checkpoint>> readCheckpoint(String tenantId, String stream, String consumerGroup) {
        return Promise.ofException(new UnsupportedOperationException(
            "readCheckpoint() requires EventLogStore SPI implementation. Use eventLogStore().readCheckpoint() for production-grade checkpoint reading."));
    }

    /**
     * Commit a checkpoint with idempotency guarantees.
     *
     * <p>P3-03: Idempotent checkpoint commit that:
     * <ul>
     *   <li>Stores the offset for the tenant/stream/consumer-group tuple</li>
     *   <li>Uses idempotency key to prevent duplicate commits</li>
     *   <li>Returns the committed checkpoint metadata</li>
     * </ul>
     *
     * @param tenantId     tenant identifier
     * @param stream       stream identifier
     * @param consumerGroup consumer group name
     * @param offset       offset to commit
     * @param idempotencyKey idempotency key for safe retry
     * @return promise of committed checkpoint
     */
    default Promise<Checkpoint> commitCheckpoint(String tenantId, String stream, String consumerGroup, long offset, String idempotencyKey) {
        return Promise.ofException(new UnsupportedOperationException(
            "commitCheckpoint() requires EventLogStore SPI implementation. Use eventLogStore().commitCheckpoint() for production-grade checkpoint management."));
    }

    /**
     * Get checkpoint status for all streams in a tenant.
     *
     * <p>P3-01: Administrative API for monitoring consumer progress
     * and detecting lag or stalled consumers.
     *
     * @param tenantId tenant identifier
     * @return promise of map from stream name to checkpoint info
     */
    default Promise<Map<String, CheckpointInfo>> getAllCheckpoints(String tenantId) {
        return Promise.ofException(new UnsupportedOperationException(
            "getAllCheckpoints() requires EventLogStore SPI implementation. Use eventLogStore().getAllCheckpoints() for production-grade checkpoint monitoring."));
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

    // ==================== Checkpoint/Replay Types (P3-01) ====================

    /**
     * Replay request with comprehensive replay semantics.
     *
     * <p>P3-03: Enables production-grade event replay with:
     * <ul>
     *   <li>Bounded offset ranges (inclusive fromOffset, inclusive toOffset or -1 for latest)</li>
     *   <li>Event type filtering for selective replay</li>
     *   <li>Replay mode selection (AT_LEAST_ONCE, EXACTLY_ONCE, AT_MOST_ONCE)</li>
     *   <li>Idempotency keys for safe retry semantics</li>
     *   <li>Consumer group scoping for independent consumer tracking</li>
     * </ul>
     *
     * <p>Offset semantics:
     * <ul>
     *   <li>fromOffset: inclusive - events at or after this offset are included</li>
     *   <li>toOffset: inclusive upper bound - events at or before this offset are included</li>
     *   <li>toOffset = -1: special value meaning "latest available offset"</li>
     * </ul>
     */
    record ReplayRequest(
        long fromOffset,
        long toOffset,
        List<String> eventTypes,
        ReplayMode replayMode,
        String idempotencyKey,
        String consumerGroup
    ) {
        public ReplayRequest {
            if (fromOffset < 0) {
                throw new IllegalArgumentException("fromOffset must be >= 0");
            }
            if (toOffset < -1) {
                throw new IllegalArgumentException("toOffset must be >= 0 or -1 for latest");
            }
            if (toOffset >= 0 && toOffset < fromOffset) {
                throw new IllegalArgumentException("toOffset must be >= fromOffset");
            }
            eventTypes = eventTypes != null ? List.copyOf(eventTypes) : List.of();
            replayMode = replayMode != null ? replayMode : ReplayMode.AT_LEAST_ONCE;
            consumerGroup = consumerGroup != null && !consumerGroup.isBlank() ? consumerGroup : "default";
        }

        public static ReplayRequest fromOffset(long fromOffset) {
            return new ReplayRequest(fromOffset, -1, List.of(), ReplayMode.AT_LEAST_ONCE, null, "default");
        }

        public static ReplayRequest bounded(long fromOffset, long toOffset) {
            return new ReplayRequest(fromOffset, toOffset, List.of(), ReplayMode.AT_LEAST_ONCE, null, "default");
        }

        public static ReplayRequest filtered(long fromOffset, long toOffset, List<String> eventTypes) {
            return new ReplayRequest(fromOffset, toOffset, eventTypes, ReplayMode.AT_LEAST_ONCE, null, "default");
        }

        public static ReplayRequest withIdempotency(long fromOffset, long toOffset, String idempotencyKey) {
            return new ReplayRequest(fromOffset, toOffset, List.of(), ReplayMode.EXACTLY_ONCE, idempotencyKey, "default");
        }

        public static ReplayRequest forConsumerGroup(long fromOffset, long toOffset, String consumerGroup) {
            return new ReplayRequest(fromOffset, toOffset, List.of(), ReplayMode.AT_LEAST_ONCE, null, consumerGroup);
        }
    }

    /**
     * Replay mode semantics.
     */
    enum ReplayMode {
        /**
         * At-least-once semantics: events may be delivered multiple times on failure/retry.
         * Consumers must be idempotent.
         */
        AT_LEAST_ONCE,
        /**
         * Exactly-once semantics: events are delivered exactly once using idempotency keys.
         * Requires idempotencyKey to be set.
         */
        EXACTLY_ONCE,
        /**
         * At-most-once semantics: events may be lost on failure but never duplicated.
         */
        AT_MOST_ONCE
    }

    /**
     * Filter criteria for event replay operations.
     *
     * <p>P3-01: Enables selective replay by event type, time range, or both.
     */
    record EventReplayFilter(
        List<String> eventTypes,
        java.time.Instant startTime,
        java.time.Instant endTime
    ) {
        public EventReplayFilter {
            eventTypes = eventTypes != null ? List.copyOf(eventTypes) : List.of();
        }

        public static EventReplayFilter byType(String... types) {
            return new EventReplayFilter(List.of(types), null, null);
        }

        public static EventReplayFilter byTimeRange(java.time.Instant start, java.time.Instant end) {
            return new EventReplayFilter(List.of(), start, end);
        }

        public static EventReplayFilter all() {
            return new EventReplayFilter(List.of(), null, null);
        }
    }

    /**
     * Event processor for checkpoint-based replay.
     *
     * <p>P3-01: Functional interface for processing events during replay.
     * Implementations should return true to advance checkpoint, false to retry.
     */
    @FunctionalInterface
    interface EventProcessor {
        /**
         * Process a single event.
         *
         * @param event the event to process
         * @return true if processing succeeded and checkpoint should advance
         */
        boolean process(Event event);
    }

    /**
     * Result of a replay operation.
     *
     * <p>P3-01: Contains statistics about the replay operation.
     */
    record ReplayResult(
        int eventsProcessed,
        long lastOffset,
        boolean checkpointStored
    ) {
        /**
         * Check if any events were processed.
         */
        public boolean hasEvents() {
            return eventsProcessed > 0;
        }
    }

    /**
     * Checkpoint information for monitoring.
     *
     * <p>P3-01: Metadata about a consumer's checkpoint including
     * lag metrics for monitoring and alerting.
     */
    record CheckpointInfo(
        String stream,
        long checkpointOffset,
        long latestOffset,
        java.time.Instant lastCheckpointTime,
        String consumerId
    ) {
        /**
         * Calculate lag in number of events.
         */
        public long lag() {
            return latestOffset - checkpointOffset;
        }

        /**
         * Check if consumer is caught up (minimal lag).
         */
        public boolean isCaughtUp() {
            return lag() <= 1;
        }

        /**
         * Check if checkpoint is stale (older than threshold).
         */
        public boolean isStale(java.time.Duration threshold) {
            return lastCheckpointTime != null &&
                   lastCheckpointTime.plus(threshold).isBefore(java.time.Instant.now());
        }
    }

    /**
     * Structured checkpoint with metadata.
     *
     * <p>P3-03: Contains checkpoint offset, timestamp, consumer group,
     * and idempotency key for production-grade checkpoint management.
     */
    record Checkpoint(
        String stream,
        String consumerGroup,
        long offset,
        java.time.Instant timestamp,
        String idempotencyKey
    ) {
        public Checkpoint {
            if (stream == null || stream.isBlank()) {
                throw new IllegalArgumentException("stream is required");
            }
            if (consumerGroup == null || consumerGroup.isBlank()) {
                throw new IllegalArgumentException("consumerGroup is required");
            }
            if (offset < 0) {
                throw new IllegalArgumentException("offset must be >= 0");
            }
            timestamp = timestamp != null ? timestamp : java.time.Instant.now();
        }
    }
}
