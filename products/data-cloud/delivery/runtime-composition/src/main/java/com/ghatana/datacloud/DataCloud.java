package com.ghatana.datacloud;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.datacloud.entity.storage.FilterCriteria;
import com.ghatana.datacloud.spi.BatchResult;
import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.datacloud.storage.H2SovereignEntityStore;
import com.ghatana.datacloud.storage.H2SovereignEventLogStore;
import com.ghatana.datacloud.spi.EventLogStore;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
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

    static EntityStore discoverEntityStore(DataCloudConfig config) {
        return discoverEntityStore(config, ServiceLoader.load(EntityStore.class).findFirst());
    }

    static EntityStore discoverEntityStore(DataCloudConfig config, Optional<EntityStore> discoveredStore) {
        Objects.requireNonNull(config, "config required");
        Objects.requireNonNull(discoveredStore, "discoveredStore required");

        if (config.profile() == DataCloudConfig.DataCloudProfile.SOVEREIGN) {
            return new H2SovereignEntityStore(resolveSovereignDataDirectory(config));
        }

        if (config.profile() == DataCloudConfig.DataCloudProfile.LOCAL) {
            return new InMemoryEntityStore();
        }

        return discoveredStore.orElseThrow(() -> new IllegalStateException(
            "No durable EntityStore provider found. Register an EntityStore implementation via META-INF/services."
        ));
    }

    static EventLogStore discoverEventLogStore(DataCloudConfig config) {
        Objects.requireNonNull(config, "config required");

        if (config.profile() == DataCloudConfig.DataCloudProfile.SOVEREIGN) {
            return new H2SovereignEventLogStore(
                resolveSovereignDataDirectory(config),
                Executors.newVirtualThreadPerTaskExecutor(),
                resolveTailPollingConfig(config));
        }

        if (config.profile() == DataCloudConfig.DataCloudProfile.LOCAL) {
            return new InMemoryEventLogStore();
        }

        return discoverEventLogStore(
            config,
            ServiceLoader.load(EventLogStore.class).findFirst(),
            ServiceLoader.load(com.ghatana.datacloud.spi.EventLogStore.class).findFirst()
        );
    }

    static EventLogStore discoverEventLogStore(DataCloudConfig config, Optional<EventLogStore> discoveredStore) {
        return discoverEventLogStore(config, discoveredStore, Optional.empty());
    }

    static EventLogStore discoverEventLogStore(
        DataCloudConfig config,
        Optional<EventLogStore> discoveredStore,
        Optional<com.ghatana.datacloud.spi.EventLogStore> legacyDiscoveredStore
    ) {
        Objects.requireNonNull(config, "config required");
        Objects.requireNonNull(discoveredStore, "discoveredStore required");
        Objects.requireNonNull(legacyDiscoveredStore, "legacyDiscoveredStore required");

        if (config.profile() == DataCloudConfig.DataCloudProfile.SOVEREIGN) {
            return new H2SovereignEventLogStore(
                resolveSovereignDataDirectory(config),
                Executors.newVirtualThreadPerTaskExecutor(),
                resolveTailPollingConfig(config));
        }

        if (config.profile() == DataCloudConfig.DataCloudProfile.LOCAL) {
            return new InMemoryEventLogStore();
        }

        if (discoveredStore.isPresent()) {
            return discoveredStore.orElseThrow();
        }

        if (legacyDiscoveredStore.isPresent()) {
            return legacyDiscoveredStore.orElseThrow();
        }

        return discoveredStore.orElseThrow(() -> new IllegalStateException(
            "No durable EventLogStore provider found. Register an EventLogStore implementation via META-INF/services."
        ));
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
        return (int) Math.min(offsetValue, entryCount);
    }

    private static Path resolveSovereignDataDirectory(DataCloudConfig config) {
        Object configuredDirectory = config.customConfig().get("sovereign.dataDir");
        if (configuredDirectory instanceof String directory && !directory.isBlank()) {
            return Paths.get(directory);
        }
        return Paths.get(System.getProperty("user.home"), ".ghatana", "datacloud", "sovereign");
    }

    private static H2SovereignEventLogStore.TailPollingConfig resolveTailPollingConfig(DataCloudConfig config) {
        Map<String, Object> customConfig = config.customConfig();
        long pollIntervalMs = readLongConfig(customConfig, "sovereign.tail.pollIntervalMs", 250L);
        int maxSubscribers = readIntConfig(customConfig, "sovereign.tail.maxSubscribers", 1024);
        int maxBatchSize = readIntConfig(customConfig, "sovereign.tail.maxBatchSize", 100);
        long maxBackoffMs = readLongConfig(customConfig, "sovereign.tail.maxBackoffMs", 30_000L);
        return new H2SovereignEventLogStore.TailPollingConfig(
            pollIntervalMs,
            maxSubscribers,
            maxBatchSize,
            maxBackoffMs);
    }

    private static long readLongConfig(Map<String, Object> config, String key, long defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static int readIntConfig(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    // ==================== Configuration ====================

    /**
     * Data-Cloud configuration.
     *
     * @doc.type record
     * @doc.purpose Captures factory options and deployment profile for Data-Cloud clients.
     * @doc.layer api
     * @doc.pattern ValueObject
     */
    public record DataCloudConfig(
        String instanceId,
        int maxConnectionsPerTenant,
        boolean enableCaching,
        boolean enableMetrics,
        DataCloudProfile profile,
        Map<String, Object> customConfig
    ) {
        /**
         * Trace exporter backend configuration for P3.7 observability.
         */
        public record TraceExporterConfig(
            String backend,
            String endpoint,
            double samplingRate,
            Map<String, String> headers
        ) {
            public TraceExporterConfig {
                backend = backend != null ? backend : "otlp";
                endpoint = endpoint != null ? endpoint : "http://localhost:4317";
                if (samplingRate < 0.0) samplingRate = 0.0;
                if (samplingRate > 1.0) samplingRate = 1.0;
                headers = headers != null ? Map.copyOf(headers) : Map.of();
            }

            public static TraceExporterConfig defaults() {
                return new TraceExporterConfig("otlp", "http://localhost:4317", 1.0, Map.of());
            }

            public boolean isEnabled() {
                return !"disabled".equalsIgnoreCase(backend);
            }
        }
        public DataCloudConfig {
            instanceId = instanceId != null ? instanceId : UUID.randomUUID().toString();
            if (maxConnectionsPerTenant <= 0) maxConnectionsPerTenant = 10;
            profile = profile != null ? profile : DataCloudProfile.LOCAL;
            customConfig = customConfig != null ? Map.copyOf(customConfig) : Map.of();
        }

        public static DataCloudConfig defaults() {
            return new DataCloudConfig(null, 10, true, true, DataCloudProfile.LOCAL, Map.of());
        }

        public static DataCloudConfig forTesting() {
            return new DataCloudConfig("test-instance", 1, false, false, DataCloudProfile.LOCAL, Map.of());
        }

        public static Builder builder() {
            return new Builder();
        }

        /**
         * Fluent builder for {@link DataCloudConfig}.
         *
         * @doc.type class
         * @doc.purpose Builds Data-Cloud configuration instances with explicit settings.
         * @doc.layer api
         * @doc.pattern Builder
         */
        public static class Builder {
            private String instanceIdValue;
            private int maxConnectionsPerTenantValue = 10;
            private boolean enableCachingValue = true;
            private boolean enableMetricsValue = true;
            private DataCloudProfile profileValue = DataCloudProfile.LOCAL;
            private Map<String, Object> customConfig = Map.of();

            public Builder instanceId(String instanceId) {
                this.instanceIdValue = instanceId;
                return this;
            }

            public Builder maxConnectionsPerTenant(int max) {
                this.maxConnectionsPerTenantValue = max;
                return this;
            }

            public Builder enableCaching(boolean enable) {
                this.enableCachingValue = enable;
                return this;
            }

            public Builder enableMetrics(boolean enable) {
                this.enableMetricsValue = enable;
                return this;
            }

            public Builder profile(DataCloudProfile profile) {
                this.profileValue = profile;
                return this;
            }

            public Builder customConfig(Map<String, Object> config) {
                this.customConfig = config;
                return this;
            }

            public DataCloudConfig build() {
                return new DataCloudConfig(instanceIdValue, maxConnectionsPerTenantValue,
                    enableCachingValue, enableMetricsValue, profileValue, customConfig);
            }
        }

        /**
         * Data-Cloud deployment profile.
         *
         * @doc.type enum
         * @doc.purpose Declares the deployment profile used to gate durable store requirements.
         * @doc.layer api
         * @doc.pattern ValueObject
         */
        public enum DataCloudProfile {
            LOCAL,
            SOVEREIGN,
            STAGING,
            PRODUCTION
        }
    }

    // ==================== Default Implementation ====================

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static class DefaultDataCloudClient implements DataCloudClient {
        private final EntityStore entityStore;
        private final EventLogStore eventLogStore;
        private final DataCloudConfig.DataCloudProfile profile;
        private volatile boolean closed = false;

        DefaultDataCloudClient(EntityStore entityStore, EventLogStore eventLogStore, DataCloudConfig config) {
            this.entityStore = Objects.requireNonNull(entityStore, "entityStore required");
            this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore required");
            this.profile = Objects.requireNonNull(config, "config required").profile();
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
            // DC-P0-001: use collection-scoped findByRef so same entity ID in different
            // collections under the same tenant does not collide.
            return entityStore.findByRef(tenant, EntityStore.EntityRef.of(collection, id))
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

            EntityStore.QuerySpec.Builder specBuilder = EntityStore.QuerySpec.builder()
                .collection(collection)
                .offset(query.offset())
                .limit(query.limit());

            if (!query.filters().isEmpty()) {
                List<EntityStore.Filter> storeFilters = new ArrayList<>();
                for (DataCloudClient.Filter f : query.filters()) {
                    storeFilters.add(toStoreFilter(f));
                }
                specBuilder.filters(storeFilters);
            }

            if (!query.sorts().isEmpty()) {
                List<EntityStore.Sort> storeSorts = new ArrayList<>();
                for (DataCloudClient.Sort s : query.sorts()) {
                    storeSorts.add(s.ascending()
                        ? EntityStore.Sort.asc(s.field())
                        : EntityStore.Sort.desc(s.field()));
                }
                specBuilder.sorts(storeSorts);
            }

            return entityStore.query(tenant, specBuilder.build())
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

        private EntityStore.Filter toStoreFilter(DataCloudClient.Filter filter) {
            return switch (filter.operator()) {
                case EQ -> EntityStore.Filter.eq(filter.field(), filter.value());
                case NE -> EntityStore.Filter.ne(filter.field(), filter.value());
                case GT -> EntityStore.Filter.gt(filter.field(), filter.value());
                case GTE -> EntityStore.Filter.gte(filter.field(), filter.value());
                case LT -> EntityStore.Filter.lt(filter.field(), filter.value());
                case LTE -> EntityStore.Filter.lte(filter.field(), filter.value());
                case LIKE -> EntityStore.Filter.like(filter.field(), (String) filter.value());
                default -> EntityStore.Filter.eq(filter.field(), filter.value());
            };
        }

        @Override
        public Promise<Void> delete(String tenantId, String collection, String id) {
            checkNotClosed();
            TenantContext tenant = TenantContext.of(tenantId);
            // DC-P0-001: use collection-scoped deleteByRef
            return entityStore.deleteByRef(tenant, EntityStore.EntityRef.of(collection, id));
        }

        @Override
        public Promise<DataCloudClient.Offset> appendEvent(String tenantId, Event event) {
            checkNotClosed();
            TenantContext tenant = TenantContext.of(tenantId);

            if (profile != DataCloudConfig.DataCloudProfile.LOCAL) {
                try {
                    event.validate();
                } catch (IllegalArgumentException exception) {
                    return Promise.ofException(exception);
                }
            }

            String payloadJson;
            try {
                payloadJson = MAPPER.writeValueAsString(event.payload());
            } catch (Exception ex) {
                return Promise.ofException(new IllegalArgumentException("Failed to serialize event payload", ex));
            }

            // DC-P0-003: propagate canonical envelope fields into headers for storage/round-trip.
            Map<String, String> enrichedHeaders = new java.util.LinkedHashMap<>(event.headers());
            event.source().ifPresent(v        -> enrichedHeaders.put("x-dc-source", v));
            event.subjectType().ifPresent(v   -> enrichedHeaders.put("x-dc-subject-type", v));
            event.subjectId().ifPresent(v     -> enrichedHeaders.put("x-dc-subject-id", v));
            event.schemaVersion().ifPresent(v -> enrichedHeaders.put("x-dc-schema-version", v));
            event.correlationId().ifPresent(v -> enrichedHeaders.put("x-dc-correlation-id", v));
            event.causationId().ifPresent(v   -> enrichedHeaders.put("x-dc-causation-id", v));
            event.actor().ifPresent(v         -> enrichedHeaders.put("x-dc-actor", v));
            event.classification().ifPresent(v -> enrichedHeaders.put("x-dc-classification", v));
            event.policyContext().ifPresent(v -> enrichedHeaders.put("x-dc-policy-context", v));
            event.provenance().ifPresent(v    -> enrichedHeaders.put("x-dc-provenance", v));
            event.traceContext().ifPresent(v  -> enrichedHeaders.put("x-dc-trace-context", v));

            EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
                .eventType(event.type())
                .payload(payloadJson)
                .timestamp(event.timestamp())
                .headers(enrichedHeaders)
                .build();

            return eventLogStore.append(tenant, entry)
                .map(offset -> DataCloudClient.Offset.of(numericOffsetValue(offset)));
        }

        @Override
        public Promise<List<Event>> queryEvents(String tenantId, EventQuery query) {
            checkNotClosed();
            TenantContext tenant = TenantContext.of(tenantId);
            com.ghatana.platform.types.identity.Offset fromOffset = com.ghatana.platform.types.identity.Offset.of(query.fromOffset().value());
            Optional<String> singleType = query.eventTypes().size() == 1
                ? Optional.of(query.eventTypes().getFirst())
                : Optional.empty();

            if (query.startTime() != null && query.endTime() != null) {
                if (singleType.isPresent()) {
                    return eventLogStore.readByType(
                            tenant,
                            singleType.get(),
                            fromOffset,
                            query.limit())
                        .map(entries -> entries.stream()
                            .filter(e -> !e.timestamp().isBefore(query.startTime()) && e.timestamp().isBefore(query.endTime()))
                            .map(this::toEvent)
                            .toList());
                }
                if (!query.eventTypes().isEmpty()) {
                    List<Promise<List<EventLogStore.EventEntry>>> readsByType = query.eventTypes().stream()
                        .map(type -> eventLogStore.readByType(tenant, type, fromOffset, query.limit()))
                        .toList();

                    return io.activej.promise.Promises.toList(readsByType)
                        .map(results -> results.stream()
                            .flatMap(List::stream)
                            .filter(e -> !e.timestamp().isBefore(query.startTime()) && e.timestamp().isBefore(query.endTime()))
                            .sorted(Comparator
                                .comparing(EventLogStore.EventEntry::timestamp)
                                .thenComparing(EventLogStore.EventEntry::eventId))
                            .limit(query.limit())
                            .map(this::toEvent)
                            .toList());
                }
                return eventLogStore.readByTimeRange(
                    tenant,
                    query.startTime(), query.endTime(), query.limit())
                    .map(entries -> entries.stream()
                        .filter(e -> query.eventTypes().isEmpty() || query.eventTypes().contains(e.eventType()))
                        .map(this::toEvent)
                        .toList());
            }

            if (singleType.isPresent()) {
                return eventLogStore.readByType(
                        tenant,
                        singleType.get(),
                        fromOffset,
                        query.limit())
                    .map(entries -> entries.stream()
                        .map(this::toEvent)
                        .toList());
            }

            return eventLogStore.read(
                    tenant,
                    fromOffset, query.limit())
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

            eventLogStore.tail(
                tenant,
                fromOffset, entry -> {
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
            closeIfPossible(entityStore);
            closeIfPossible(eventLogStore);
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

        private void closeIfPossible(Object candidate) {
            if (candidate instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception exception) {
                    // DC-BE-004: Log close failures so operators can diagnose resource leaks.
                    System.getLogger(DataCloud.class.getName())
                        .log(System.Logger.Level.WARNING,
                            "Failed to close {0}: {1}",
                            new Object[]{candidate.getClass().getSimpleName(), exception.getMessage()});
                }
            }
        }

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
            Map<String, String> headers = entry.headers();
            return Event.builder()
                .type(entry.eventType())
                .payload(payload)
                .headers(headers)
                .timestamp(entry.timestamp())
                .source(headers.get("x-dc-source"))
                .correlationId(headers.get("x-dc-correlation-id"))
                .causationId(headers.get("x-dc-causation-id"))
                .traceContext(headers.get("x-dc-trace-context"))
                .build();
        }
    }

    // ==================== In-Memory Implementations ====================

    private static class InMemoryEntityStore implements EntityStore {
        /**
         * Outer key: tenantId. Inner key: "collection/entityId" (collection-scoped).
         * This ensures the same entity ID can exist in multiple collections under one tenant
         * without collision (DC-P0-001).
         */
        private static final Logger log = LoggerFactory.getLogger(InMemoryEntityStore.class);
        /** DC-PERF-002: soft limit — warn when any single tenant exceeds this. */
        private static final int SOFT_LIMIT_PER_TENANT = 10_000;
        private final Map<String, Map<String, Entity>> store = new ConcurrentHashMap<>();

        private static String scopedKey(String collection, String entityId) {
            return collection + "/" + entityId;
        }

        @Override
        public Promise<Entity> save(TenantContext tenant, Entity entity) {
            Map<String, Entity> tenantStore = store.computeIfAbsent(tenant.tenantId(), k -> new ConcurrentHashMap<>());
            tenantStore.put(scopedKey(entity.collection(), entity.id().value()), entity);
            int size = tenantStore.size();
            if (size >= SOFT_LIMIT_PER_TENANT) {
                log.warn("DC-PERF-002: InMemoryEntityStore tenant={} has reached {} entities " +
                    "(soft limit {}). Switch to H2SovereignEntityStore for durable storage.",
                    tenant.tenantId(), size, SOFT_LIMIT_PER_TENANT);
            }
            return Promise.of(entity);
        }

        @Override
        public Promise<BatchResult<String>> saveBatch(TenantContext tenant, List<Entity> entities) {
            for (Entity entity : entities) {
                save(tenant, entity);
            }
            return Promise.of(BatchResult.success(entities.size()));
        }

        @Override
        public Promise<Optional<Entity>> findById(TenantContext tenant, EntityId id) {
            // Without collection context the key is ambiguous; return empty to signal that
            // callers must use findByRef for collection-scoped lookup.
            return Promise.of(Optional.empty());
        }

        @Override
        public Promise<Optional<Entity>> findByRef(TenantContext tenant, EntityRef ref) {
            Map<String, Entity> tenantStore = store.get(tenant.tenantId());
            if (tenantStore == null) {
                return Promise.of(Optional.empty());
            }
            return Promise.of(Optional.ofNullable(tenantStore.get(scopedKey(ref.collection(), ref.entityId().value()))));
        }

        @Override
        public Promise<List<Entity>> findByIds(TenantContext tenant, List<EntityId> ids) {
            // Without collection context we cannot do a scoped look-up; return empty.
            return Promise.of(List.of());
        }

        @Override
        public Promise<List<Entity>> findByRefs(TenantContext tenant, List<EntityRef> refs) {
            Map<String, Entity> tenantStore = store.getOrDefault(tenant.tenantId(), Map.of());
            List<Entity> results = refs.stream()
                .map(ref -> tenantStore.get(scopedKey(ref.collection(), ref.entityId().value())))
                .filter(Objects::nonNull)
                .toList();
            return Promise.of(results);
        }

        @Override
        public Promise<QueryResult> query(TenantContext tenant, QuerySpec query) {
            Map<String, Entity> tenantStore = store.getOrDefault(tenant.tenantId(), Map.of());
            List<Entity> filtered = tenantStore.values().stream()
                .filter(e -> e.collection().equals(query.collection()))
                .filter(e -> matchesFilters(e, query.filters()))
                .sorted(buildComparator(query.sorts()))
                .toList();
            long totalCount = filtered.size();
            List<Entity> page = filtered.stream()
                .skip(query.offset())
                .limit(query.limit())
                .toList();
            return Promise.of(QueryResult.of(page, totalCount));
        }

        private boolean matchesFilters(Entity entity, List<Filter> filters) {
            if (filters.isEmpty()) {
                return true;
            }
            for (Filter filter : filters) {
                Object actual = resolveField(entity, filter.field());
                Object expected = filter.value();
                boolean match = switch (filter.operator()) {
                    case EQ -> Objects.equals(actual, expected);
                    case NE -> !Objects.equals(actual, expected);
                    case GT -> compareValues(actual, expected) > 0;
                    case GTE -> compareValues(actual, expected) >= 0;
                    case LT -> compareValues(actual, expected) < 0;
                    case LTE -> compareValues(actual, expected) <= 0;
                    case LIKE -> actual instanceof String s && s.contains(String.valueOf(expected));
                    default -> Objects.equals(actual, expected);
                };
                if (!match) {
                    return false;
                }
            }
            return true;
        }

        @SuppressWarnings("unchecked")
        private int compareValues(Object a, Object b) {
            if (a == null && b == null) return 0;
            if (a == null) return -1;
            if (b == null) return 1;
            if (a instanceof Comparable<?> ca && b instanceof Comparable<?> cb && a.getClass().equals(b.getClass())) {
                return ((Comparable<Object>) ca).compareTo(cb);
            }
            return String.valueOf(a).compareTo(String.valueOf(b));
        }

        private Object resolveField(Entity entity, String field) {
            return switch (field) {
                case "id" -> entity.id().value();
                case "collection" -> entity.collection();
                case "version" -> entity.metadata().version();
                case "createdAt" -> entity.metadata().createdAt();
                case "updatedAt" -> entity.metadata().updatedAt();
                default -> entity.data().get(field);
            };
        }

        private java.util.Comparator<Entity> buildComparator(List<Sort> sorts) {
            if (sorts.isEmpty()) {
                return java.util.Comparator.comparing((Entity e) -> e.metadata().updatedAt(), java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                    .thenComparing(e -> e.id().value());
            }
            java.util.Comparator<Entity> comparator = null;
            for (Sort sort : sorts) {
                java.util.Comparator<Entity> next =
                    (left, right) -> compareValues(resolveField(left, sort.field()), resolveField(right, sort.field()));
                if (sort.direction() == Direction.DESC) {
                    next = next.reversed();
                }
                comparator = comparator == null ? next : comparator.thenComparing(next);
            }
            return comparator != null ? comparator : java.util.Comparator.comparing(e -> e.id().value());
        }

        @Override
        public Promise<Void> delete(TenantContext tenant, EntityId id) {
            // Without collection context we cannot safely delete; no-op.
            return Promise.of(null);
        }

        @Override
        public Promise<Void> deleteByRef(TenantContext tenant, EntityRef ref) {
            Map<String, Entity> tenantStore = store.get(tenant.tenantId());
            if (tenantStore != null) {
                tenantStore.remove(scopedKey(ref.collection(), ref.entityId().value()));
            }
            return Promise.of(null);
        }

        @Override
        public Promise<BatchResult<String>> deleteBatch(TenantContext tenant, List<EntityId> ids) {
            // Without collection context, cannot do scoped deletes; return zero affected.
            return Promise.of(new BatchResult<String>(ids.size(), 0, ids.size(),
                java.util.stream.IntStream.range(0, ids.size())
                    .mapToObj(i -> new com.ghatana.datacloud.spi.BatchError<String>(i, ids.get(i).value(), "COLLECTION_REQUIRED", "collection context required for scoped delete"))
                    .toList()));
        }

        @Override
        public Promise<BatchResult<String>> deleteByRefs(TenantContext tenant, List<EntityRef> refs) {
            Map<String, Entity> tenantStore = store.get(tenant.tenantId());
            int deleted = 0;
            if (tenantStore != null) {
                for (EntityRef ref : refs) {
                    if (tenantStore.remove(scopedKey(ref.collection(), ref.entityId().value())) != null) {
                        deleted++;
                    }
                }
            }
            return Promise.of(new BatchResult<>(refs.size(), deleted, refs.size() - deleted, List.of()));
        }

        @Override
        public Promise<Long> count(TenantContext tenant, QuerySpec query) {
            Map<String, Entity> tenantStore = store.getOrDefault(tenant.tenantId(), Map.of());
            long count = tenantStore.values().stream()
                .filter(e -> e.collection().equals(query.collection()))
                .filter(e -> matchesFilters(e, query.filters()))
                .count();
            return Promise.of(count);
        }

        @Override
        public Promise<Boolean> exists(TenantContext tenant, EntityId id) {
            // Without collection context this is unreliable; return false.
            return Promise.of(false);
        }

        @Override
        public Promise<Boolean> existsByRef(TenantContext tenant, EntityRef ref) {
            Map<String, Entity> tenantStore = store.get(tenant.tenantId());
            return Promise.of(tenantStore != null && tenantStore.containsKey(scopedKey(ref.collection(), ref.entityId().value())));
        }

        @Override
        public Promise<List<String>> listCollections(TenantContext tenant) {
            Map<String, Entity> tenantStore = store.getOrDefault(tenant.tenantId(), Map.of());
            List<String> collections = tenantStore.values().stream()
                .map(Entity::collection)
                .distinct()
                .sorted()
                .toList();
            return Promise.of(collections);
        }
    }

    private static class InMemoryEventLogStore implements EventLogStore {
        private final Map<String, List<EventEntry>> store = new ConcurrentHashMap<>();
        private final Map<String, Long> offsets = new ConcurrentHashMap<>();
        private final Map<String, List<Consumer<EventEntry>>> tailListeners = new ConcurrentHashMap<>();

        @Override
        public Promise<Offset> append(com.ghatana.datacloud.spi.TenantContext tenant, EventEntry entry) {
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
        public Promise<List<Offset>> appendBatch(com.ghatana.datacloud.spi.TenantContext tenant, List<EventEntry> entries) {
            List<Offset> results = new ArrayList<>();
            for (EventEntry entry : entries) {
                results.add(append(tenant, entry).getResult());
            }
            return Promise.of(results);
        }

        @Override
        public Promise<List<EventEntry>> read(com.ghatana.datacloud.spi.TenantContext tenant, Offset from, int limit) {
            List<EventEntry> entries = store.getOrDefault(tenant.tenantId(), List.of());
            long startOffset = normalizedReadOffset(from);
            List<EventEntry> results = entries.stream()
                .skip(startOffset)
                .limit(limit)
                .toList();
            return Promise.of(results);
        }

        @Override
        public Promise<List<EventEntry>> readByTimeRange(com.ghatana.datacloud.spi.TenantContext tenant, java.time.Instant startTime, java.time.Instant endTime, int limit) {
            List<EventEntry> entries = store.getOrDefault(tenant.tenantId(), List.of());
            List<EventEntry> results = entries.stream()
                .filter(e -> !e.timestamp().isBefore(startTime) && e.timestamp().isBefore(endTime))
                .limit(limit)
                .toList();
            return Promise.of(results);
        }

        @Override
        public Promise<List<EventEntry>> readByType(com.ghatana.datacloud.spi.TenantContext tenant, String eventType, Offset from, int limit) {
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
        public Promise<Offset> getLatestOffset(com.ghatana.datacloud.spi.TenantContext tenant) {
            Long offset = offsets.get(tenant.tenantId());
            return Promise.of(offset != null ? Offset.of(offset) : Offset.zero());
        }

        @Override
        public Promise<Offset> getEarliestOffset(com.ghatana.datacloud.spi.TenantContext tenant) {
            return Promise.of(Offset.zero());
        }

        @Override
        public Promise<Subscription> tail(com.ghatana.datacloud.spi.TenantContext tenant, Offset from, Consumer<EventEntry> handler) {
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
