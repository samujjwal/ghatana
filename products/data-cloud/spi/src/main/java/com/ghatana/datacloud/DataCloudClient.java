package com.ghatana.datacloud;

import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
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
     */
    record Query(
        List<Filter> filters,
        List<Sort> sorts,
        int offset,
        int limit
    ) {
        public Query {
            filters = filters != null ? List.copyOf(filters) : List.of();
            sorts = sorts != null ? List.copyOf(sorts) : List.of();
            if (offset < 0) offset = 0;
            if (limit <= 0) limit = 100;
        }

        public static Query all() {
            return new Query(List.of(), List.of(), 0, 100);
        }

        public static Query limit(int limit) {
            return new Query(List.of(), List.of(), 0, limit);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private List<Filter> filters = List.of();
            private List<Sort> sorts = List.of();
            private int offset = 0;
            private int limit = 100;

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

            public Query build() {
                return new Query(filters, sorts, offset, limit);
            }
        }
    }

    /**
     * Query filter.
     */
    record Filter(String field, String operator, Object value) {
        public static Filter eq(String field, Object value) {
            return new Filter(field, "eq", value);
        }

        public static Filter ne(String field, Object value) {
            return new Filter(field, "ne", value);
        }

        public static Filter gt(String field, Object value) {
            return new Filter(field, "gt", value);
        }

        public static Filter gte(String field, Object value) {
            return new Filter(field, "gte", value);
        }

        public static Filter lt(String field, Object value) {
            return new Filter(field, "lt", value);
        }

        public static Filter lte(String field, Object value) {
            return new Filter(field, "lte", value);
        }

        public static Filter like(String field, String pattern) {
            return new Filter(field, "like", pattern);
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
     * Event data structure.
     */
    record Event(
        String type,
        Map<String, Object> payload,
        Map<String, String> headers,
        java.time.Instant timestamp
    ) {
        public Event {
            java.util.Objects.requireNonNull(type, "type required");
            payload = payload != null ? Map.copyOf(payload) : Map.of();
            headers = headers != null ? Map.copyOf(headers) : Map.of();
            timestamp = timestamp != null ? timestamp : java.time.Instant.now();
        }

        public static Event of(String type, Map<String, Object> payload) {
            return new Event(type, payload, Map.of(), null);
        }
    }

    /**
     * Event query specification.
     */
    record EventQuery(
        List<String> eventTypes,
        java.time.Instant startTime,
        java.time.Instant endTime,
        int limit
    ) {
        public EventQuery {
            eventTypes = eventTypes != null ? List.copyOf(eventTypes) : List.of();
            if (limit <= 0) limit = 100;
        }

        public static EventQuery all() {
            return new EventQuery(List.of(), null, null, 100);
        }

        public static EventQuery byType(String... types) {
            return new EventQuery(List.of(types), null, null, 100);
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
