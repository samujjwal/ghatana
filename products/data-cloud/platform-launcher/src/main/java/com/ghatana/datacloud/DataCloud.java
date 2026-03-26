package com.ghatana.datacloud;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Data-Cloud Entry Point - ONLY public class users need for factory methods.
 *
 * <p>This is the primary entry point for Data-Cloud. It provides 3 factory methods:
 * <ul>
 *   <li>{@link #create(DataCloudConfig)} - Create with configuration</li>
 *   <li>{@link #embedded()} - Create embedded instance</li>
 *   <li>{@link #forTesting()} - Create for testing</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Data-Cloud factory entry point
 * @doc.layer api
 * @doc.pattern Factory
 * @since 1.0.0
 */
public final class DataCloud {

    private DataCloud() {
        // Utility class - no instantiation
    }

    // ==================== Factory Methods (3) ====================

    /**
     * Create a Data-Cloud client with the given configuration.
     *
     * @param config Data-Cloud configuration
     * @return configured Data-Cloud client
     */
    public static DataCloudClient create(DataCloudConfig config) {
        Objects.requireNonNull(config, "config required");
        
        // Discover stores via ServiceLoader or use in-memory
        EntityStore entityStore = discoverEntityStore(config);
        EventLogStore eventLogStore = discoverEventLogStore(config);
        
        return new DefaultDataCloudClient(entityStore, eventLogStore, config);
    }

    /**
     * Create an embedded Data-Cloud client with default configuration.
     *
     * @return embedded Data-Cloud client
     */
    public static DataCloudClient embedded() {
        return create(DataCloudConfig.defaults());
    }

    /**
     * Create a Data-Cloud client for testing with in-memory storage.
     *
     * @return testing Data-Cloud client
     */
    public static DataCloudClient forTesting() {
        return new DefaultDataCloudClient(
            new InMemoryEntityStore(),
            new InMemoryEventLogStore(),
            DataCloudConfig.forTesting()
        );
    }

    private static EntityStore discoverEntityStore(DataCloudConfig config) {
        ServiceLoader<EntityStore> loader = ServiceLoader.load(EntityStore.class);
        return loader.findFirst().orElse(new InMemoryEntityStore());
    }

    private static EventLogStore discoverEventLogStore(DataCloudConfig config) {
        ServiceLoader<EventLogStore> loader = ServiceLoader.load(EventLogStore.class);
        return loader.findFirst().orElse(new InMemoryEventLogStore());
    }

    private static long numericOffsetValue(Offset offset) {
        Objects.requireNonNull(offset, "offset required");
        try {
            return Long.parseLong(offset.value());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                "Offset must be numeric for DataCloud operations: '" + offset.value() + "'",
                e
            );
        }
    }

    private static long normalizedReadOffset(Offset offset) {
        return Math.max(0L, numericOffsetValue(offset));
    }

    private static int tailStartIndex(Offset from, int entryCount) {
        long offsetValue = numericOffsetValue(from);
        if (offsetValue < 0) {
            return entryCount;
        }
        return (int) Math.min(offsetValue, (long) entryCount);
    }

    // ==================== Configuration ====================

    /**
     * Data-Cloud configuration.
     */
    public record DataCloudConfig(
        String instanceId,
        int maxConnectionsPerTenant,
        boolean enableCaching,
        boolean enableMetrics,
        Map<String, Object> customConfig
    ) {
        public DataCloudConfig {
            instanceId = instanceId != null ? instanceId : UUID.randomUUID().toString();
            if (maxConnectionsPerTenant <= 0) maxConnectionsPerTenant = 10;
            customConfig = customConfig != null ? Map.copyOf(customConfig) : Map.of();
        }

        public static DataCloudConfig defaults() {
            return new DataCloudConfig(null, 10, true, true, Map.of());
        }

        public static DataCloudConfig forTesting() {
            return new DataCloudConfig("test-instance", 1, false, false, Map.of());
        }

        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String instanceId;
            private int maxConnectionsPerTenant = 10;
            private boolean enableCaching = true;
            private boolean enableMetrics = true;
            private Map<String, Object> customConfig = Map.of();

            public Builder instanceId(String instanceId) {
                this.instanceId = instanceId;
                return this;
            }

            public Builder maxConnectionsPerTenant(int max) {
                this.maxConnectionsPerTenant = max;
                return this;
            }

            public Builder enableCaching(boolean enable) {
                this.enableCaching = enable;
                return this;
            }

            public Builder enableMetrics(boolean enable) {
                this.enableMetrics = enable;
                return this;
            }

            public Builder customConfig(Map<String, Object> config) {
                this.customConfig = config;
                return this;
            }

            public DataCloudConfig build() {
                return new DataCloudConfig(instanceId, maxConnectionsPerTenant, 
                    enableCaching, enableMetrics, customConfig);
            }
        }
    }

    // ==================== Default Implementation ====================

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static class DefaultDataCloudClient implements DataCloudClient {
        private final EntityStore entityStore;
        private final EventLogStore eventLogStore;
        private final DataCloudConfig config;
        private volatile boolean closed = false;

        DefaultDataCloudClient(EntityStore entityStore, EventLogStore eventLogStore, DataCloudConfig config) {
            this.entityStore = Objects.requireNonNull(entityStore, "entityStore required");
            this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore required");
            this.config = Objects.requireNonNull(config, "config required");
        }

        @Override
        public Promise<Entity> save(String tenantId, String collection, Map<String, Object> data) {
            checkNotClosed();
            TenantContext tenant = TenantContext.of(tenantId);
            
            String id = data.containsKey("id") ? data.get("id").toString() : UUID.randomUUID().toString();
            EntityStore.Entity entity = EntityStore.Entity.builder()
                .id(id)
                .collection(collection)
                .data(data)
                .build();
            
            return entityStore.save(tenant, entity)
                .map(saved -> new Entity(
                    saved.id().value(),
                    saved.collection(),
                    saved.data(),
                    saved.metadata().createdAt(),
                    saved.metadata().updatedAt(),
                    saved.metadata().version()
                ));
        }

        @Override
        public Promise<Optional<Entity>> findById(String tenantId, String collection, String id) {
            checkNotClosed();
            TenantContext tenant = TenantContext.of(tenantId);
            
            return entityStore.findById(tenant, EntityStore.EntityId.of(id))
                .map(opt -> opt.map(e -> new Entity(
                    e.id().value(),
                    e.collection(),
                    e.data(),
                    e.metadata().createdAt(),
                    e.metadata().updatedAt(),
                    e.metadata().version()
                )));
        }

        @Override
        public Promise<List<Entity>> query(String tenantId, String collection, Query query) {
            checkNotClosed();
            TenantContext tenant = TenantContext.of(tenantId);
            
            EntityStore.QuerySpec spec = EntityStore.QuerySpec.builder()
                .collection(collection)
                .offset(query.offset())
                .limit(query.limit())
                .build();
            
            return entityStore.query(tenant, spec)
                .map(result -> result.entities().stream()
                    .map(e -> new Entity(
                        e.id().value(),
                        e.collection(),
                        e.data(),
                        e.metadata().createdAt(),
                        e.metadata().updatedAt(),
                        e.metadata().version()
                    ))
                    .toList());
        }

        @Override
        public Promise<Void> delete(String tenantId, String collection, String id) {
            checkNotClosed();
            TenantContext tenant = TenantContext.of(tenantId);
            return entityStore.delete(tenant, EntityStore.EntityId.of(id));
        }

        @Override
        public Promise<DataCloudClient.Offset> appendEvent(String tenantId, Event event) {
            checkNotClosed();
            TenantContext tenant = TenantContext.of(tenantId);

            String payloadJson;
            try {
                payloadJson = MAPPER.writeValueAsString(event.payload());
            } catch (Exception ex) {
                return Promise.ofException(new IllegalArgumentException("Failed to serialize event payload", ex));
            }

            EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
                .eventType(event.type())
                .payload(payloadJson)
                .timestamp(event.timestamp())
                .headers(event.headers())
                .build();
            
            return eventLogStore.append(tenant, entry)
                .map(offset -> DataCloudClient.Offset.of(numericOffsetValue(offset)));
        }

        @Override
        public Promise<List<Event>> queryEvents(String tenantId, EventQuery query) {
            checkNotClosed();
            TenantContext tenant = TenantContext.of(tenantId);
            
            if (query.startTime() != null && query.endTime() != null) {
                return eventLogStore.readByTimeRange(tenant, query.startTime(), query.endTime(), query.limit())
                    .map(entries -> entries.stream()
                        .filter(e -> query.eventTypes().isEmpty() || query.eventTypes().contains(e.eventType()))
                        .map(this::toEvent)
                        .toList());
            }
            
            return eventLogStore.read(tenant, com.ghatana.platform.types.identity.Offset.zero(), query.limit())
                .map(entries -> entries.stream()
                    .filter(e -> query.eventTypes().isEmpty() || query.eventTypes().contains(e.eventType()))
                    .map(this::toEvent)
                    .toList());
        }

        @Override
        public Subscription tailEvents(String tenantId, TailRequest request, Consumer<Event> handler) {
            checkNotClosed();
            TenantContext tenant = TenantContext.of(tenantId);
            com.ghatana.platform.types.identity.Offset fromOffset = com.ghatana.platform.types.identity.Offset.of(request.fromOffset().value());
            
            final boolean[] cancelled = {false};
            
            eventLogStore.tail(tenant, fromOffset, entry -> {
                if (!cancelled[0]) {
                    if (request.eventTypes().isEmpty() || request.eventTypes().contains(entry.eventType())) {
                        handler.accept(toEvent(entry));
                    }
                }
            });
            
            return new Subscription() {
                @Override
                public void cancel() {
                    cancelled[0] = true;
                }

                @Override
                public boolean isCancelled() {
                    return cancelled[0];
                }
            };
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public EntityStore entityStore() {
            return entityStore;
        }

        @Override
        public EventLogStore eventLogStore() {
            return eventLogStore;
        }

        private void checkNotClosed() {
            if (closed) {
                throw new IllegalStateException("DataCloudClient is closed");
            }
        }

        @SuppressWarnings("unchecked")
        private Event toEvent(EventLogStore.EventEntry entry) {
            ByteBuffer buf = entry.payload();
            String payloadJson = new String(buf.array(), buf.position(), buf.limit() - buf.position(),
                    java.nio.charset.StandardCharsets.UTF_8);
            Map<String, Object> payload;
            try {
                payload = MAPPER.readValue(payloadJson, new TypeReference<>() {});
            } catch (Exception ex) {
                payload = Map.of("raw", payloadJson);
            }
            return new Event(
                entry.eventType(),
                payload,
                entry.headers(),
                entry.timestamp()
            );
        }
    }

    // ==================== In-Memory Implementations ====================

    private static class InMemoryEntityStore implements EntityStore {
        private final Map<String, Map<String, Entity>> store = new ConcurrentHashMap<>();

        @Override
        public Promise<Entity> save(TenantContext tenant, Entity entity) {
            store.computeIfAbsent(tenant.tenantId(), k -> new ConcurrentHashMap<>())
                .put(entity.id().value(), entity);
            return Promise.of(entity);
        }

        @Override
        public Promise<BatchResult> saveBatch(TenantContext tenant, List<Entity> entities) {
            for (Entity entity : entities) {
                save(tenant, entity);
            }
            return Promise.of(BatchResult.success(entities.size()));
        }

        @Override
        public Promise<Optional<Entity>> findById(TenantContext tenant, EntityId id) {
            Map<String, Entity> tenantStore = store.get(tenant.tenantId());
            if (tenantStore == null) {
                return Promise.of(Optional.empty());
            }
            return Promise.of(Optional.ofNullable(tenantStore.get(id.value())));
        }

        @Override
        public Promise<List<Entity>> findByIds(TenantContext tenant, List<EntityId> ids) {
            Map<String, Entity> tenantStore = store.getOrDefault(tenant.tenantId(), Map.of());
            List<Entity> results = ids.stream()
                .map(id -> tenantStore.get(id.value()))
                .filter(Objects::nonNull)
                .toList();
            return Promise.of(results);
        }

        @Override
        public Promise<QueryResult> query(TenantContext tenant, QuerySpec query) {
            Map<String, Entity> tenantStore = store.getOrDefault(tenant.tenantId(), Map.of());
            List<Entity> results = tenantStore.values().stream()
                .filter(e -> e.collection().equals(query.collection()))
                .skip(query.offset())
                .limit(query.limit())
                .toList();
            return Promise.of(QueryResult.of(results));
        }

        @Override
        public Promise<Void> delete(TenantContext tenant, EntityId id) {
            Map<String, Entity> tenantStore = store.get(tenant.tenantId());
            if (tenantStore != null) {
                tenantStore.remove(id.value());
            }
            return Promise.of(null);
        }

        @Override
        public Promise<BatchResult> deleteBatch(TenantContext tenant, List<EntityId> ids) {
            for (EntityId id : ids) {
                delete(tenant, id);
            }
            return Promise.of(BatchResult.success(ids.size()));
        }

        @Override
        public Promise<Long> count(TenantContext tenant, QuerySpec query) {
            Map<String, Entity> tenantStore = store.getOrDefault(tenant.tenantId(), Map.of());
            long count = tenantStore.values().stream()
                .filter(e -> e.collection().equals(query.collection()))
                .count();
            return Promise.of(count);
        }

        @Override
        public Promise<Boolean> exists(TenantContext tenant, EntityId id) {
            Map<String, Entity> tenantStore = store.get(tenant.tenantId());
            return Promise.of(tenantStore != null && tenantStore.containsKey(id.value()));
        }
    }

    private static class InMemoryEventLogStore implements EventLogStore {
        private final Map<String, List<EventEntry>> store = new ConcurrentHashMap<>();
        private final Map<String, Long> offsets = new ConcurrentHashMap<>();
        private final Map<String, List<Consumer<EventEntry>>> tailListeners = new ConcurrentHashMap<>();

        @Override
        public Promise<Offset> append(TenantContext tenant, EventEntry entry) {
            List<EventEntry> entries = store.computeIfAbsent(tenant.tenantId(), k -> new ArrayList<>());
            long offset;
            synchronized (entries) {
                entries.add(entry);
                offset = offsets.compute(tenant.tenantId(), (k, v) -> v == null ? 1 : v + 1);
            }
            // Notify all tail listeners for this tenant
            List<Consumer<EventEntry>> listeners = tailListeners.get(tenant.tenantId());
            if (listeners != null) {
                for (Consumer<EventEntry> listener : listeners) {
                    listener.accept(entry);
                }
            }
            return Promise.of(Offset.of(offset));
        }

        @Override
        public Promise<List<Offset>> appendBatch(TenantContext tenant, List<EventEntry> entries) {
            List<Offset> results = new ArrayList<>();
            for (EventEntry entry : entries) {
                results.add(append(tenant, entry).getResult());
            }
            return Promise.of(results);
        }

        @Override
        public Promise<List<EventEntry>> read(TenantContext tenant, Offset from, int limit) {
            List<EventEntry> entries = store.getOrDefault(tenant.tenantId(), List.of());
            long startOffset = normalizedReadOffset(from);
            List<EventEntry> results = entries.stream()
                .skip(startOffset)
                .limit(limit)
                .toList();
            return Promise.of(results);
        }

        @Override
        public Promise<List<EventEntry>> readByTimeRange(TenantContext tenant, java.time.Instant startTime, java.time.Instant endTime, int limit) {
            List<EventEntry> entries = store.getOrDefault(tenant.tenantId(), List.of());
            List<EventEntry> results = entries.stream()
                .filter(e -> !e.timestamp().isBefore(startTime) && e.timestamp().isBefore(endTime))
                .limit(limit)
                .toList();
            return Promise.of(results);
        }

        @Override
        public Promise<List<EventEntry>> readByType(TenantContext tenant, String eventType, Offset from, int limit) {
            List<EventEntry> entries = store.getOrDefault(tenant.tenantId(), List.of());
            long startOffset = normalizedReadOffset(from);
            List<EventEntry> results = entries.stream()
                .skip(startOffset)
                .filter(e -> e.eventType().equals(eventType))
                .limit(limit)
                .toList();
            return Promise.of(results);
        }

        @Override
        public Promise<Offset> getLatestOffset(TenantContext tenant) {
            Long offset = offsets.get(tenant.tenantId());
            return Promise.of(offset != null ? Offset.of(offset) : Offset.zero());
        }

        @Override
        public Promise<Offset> getEarliestOffset(TenantContext tenant) {
            return Promise.of(Offset.zero());
        }

        @Override
        public Promise<Subscription> tail(TenantContext tenant, Offset from, Consumer<EventEntry> handler) {
            // Replay any existing entries from the given offset
            List<EventEntry> existing = store.getOrDefault(tenant.tenantId(), List.of());
            int startIndex = tailStartIndex(from, existing.size());
            for (int i = startIndex; i < existing.size(); i++) {
                handler.accept(existing.get(i));
            }

            // Register handler for all future appends to this tenant
            final boolean[] cancelled = {false};
            Consumer<EventEntry> guardedHandler = entry -> {
                if (!cancelled[0]) handler.accept(entry);
            };
            tailListeners
                .computeIfAbsent(tenant.tenantId(), k -> new CopyOnWriteArrayList<>())
                .add(guardedHandler);

            return Promise.of(new Subscription() {
                @Override
                public void cancel() {
                    cancelled[0] = true;
                    List<Consumer<EventEntry>> list = tailListeners.get(tenant.tenantId());
                    if (list != null) list.remove(guardedHandler);
                }

                @Override
                public boolean isCancelled() {
                    return cancelled[0];
                }
            });
        }
    }
}
