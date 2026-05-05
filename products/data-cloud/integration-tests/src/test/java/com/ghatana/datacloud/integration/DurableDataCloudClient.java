/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.datacloud.integration;

import com.ghatana.datacloud.*;
import com.ghatana.datacloud.entity.EntityInterface;
import com.ghatana.datacloud.entity.storage.QuerySpecInterface;
import com.ghatana.datacloud.spi.*;
import com.ghatana.datacloud.storage.H2SovereignEntityStore;
import com.ghatana.datacloud.storage.H2SovereignEventLogStore;
import io.activej.promise.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Production-grade durable DataCloud client using H2 file-backed storage.
 *
 * <p>This client provides real persistence across process restarts by using
 * H2SovereignEntityStore and H2SovereignEventLogStore with file-based storage.
 * Suitable for integration tests that need to verify restart durability.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>File-backed H2 database for entity storage</li>
 *   <li>File-backed H2 database for event log storage</li>
 *   <li>Persistence across process restarts</li>
 *   <li>Tenant-scoped isolation</li>
 *   <li>Full DataCloudClient interface implementation</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Durable DataCloud client with H2 file-backed storage for integration tests
 * @doc.layer product
 * @doc.pattern Client, Adapter
 */
public class DurableDataCloudClient implements DataCloudClient, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(DurableDataCloudClient.class);

    private final Path storageDirectory;
    private final H2SovereignEntityStore entityStore;
    private final H2SovereignEventLogStore eventLogStore;
    private final AtomicReference<Boolean> isOpen = new AtomicReference<>(false);
    private final Instant startTime;

    /**
     * Creates a new durable client with temporary storage directory.
     *
     * @throws Exception if storage initialization fails
     */
    public DurableDataCloudClient() throws Exception {
        this(Files.createTempDirectory("datacloud-durable-test-"));
    }

    /**
     * Creates a new durable client with specified storage directory.
     *
     * @param storageDirectory directory for H2 database files
     * @throws Exception if storage initialization fails
     */
    public DurableDataCloudClient(Path storageDirectory) throws Exception {
        this.storageDirectory = Objects.requireNonNull(storageDirectory, "storageDirectory required");
        this.entityStore = new H2SovereignEntityStore(storageDirectory);
        this.eventLogStore = new H2SovereignEventLogStore(storageDirectory);
        this.startTime = Instant.now();
        this.isOpen.set(true);
        log.info("DurableDataCloudClient initialized with storage: {}", storageDirectory);
    }

    @Override
    public Promise<Void> open() {
        if (isOpen.get()) {
            log.debug("Client already open");
            return Promise.of(null);
        }
        isOpen.set(true);
        log.info("DurableDataCloudClient opened");
        return Promise.of(null);
    }

    @Override
    public Promise<Void> close() {
        if (!isOpen.get()) {
            return Promise.of(null);
        }
        isOpen.set(false);
        try {
            entityStore.close();
            eventLogStore.close();
            log.info("DurableDataCloudClient closed");
            return Promise.of(null);
        } catch (Exception e) {
            log.error("Error closing durable client", e);
            return Promise.ofException(e);
        }
    }

    @Override
    public boolean isOpen() {
        return isOpen.get();
    }

    @Override
    public Instant startTime() {
        return startTime;
    }

    @Override
    public Promise<EntityInterface> createEntity(String tenantId, String collectionName, Map<String, Object> data) {
        requireOpen();
        validateTenantId(tenantId);
        validateCollectionName(collectionName);
        Objects.requireNonNull(data, "data is required");

        TenantContext tenant = TenantContext.of(tenantId);
        UUID entityId = UUID.randomUUID();
        Instant now = Instant.now();

        Entity entity = new Entity(
            new EntityId(entityId),
            tenantId,
            collectionName,
            new HashMap<>(data),
            Map.of(),
            now,
            now,
            1
        );

        return entityStore.save(tenant, entity)
            .then(savedEntity -> {
                log.debug("Created entity {} in {}/{}", entityId, tenantId, collectionName);
                return Promise.of(toEntityInterface(savedEntity));
            });
    }

    @Override
    public Promise<EntityInterface> getEntity(String tenantId, String collectionName, String entityId) {
        requireOpen();
        validateTenantId(tenantId);
        validateCollectionName(collectionName);
        Objects.requireNonNull(entityId, "entityId is required");

        TenantContext tenant = TenantContext.of(tenantId);
        return entityStore.findById(tenant, new EntityId(UUID.fromString(entityId)))
            .then(opt -> {
                if (opt.isEmpty()) {
                    log.debug("Entity not found: {} in {}/{}", entityId, tenantId, collectionName);
                    return Promise.of(null);
                }
                return Promise.of(toEntityInterface(opt.get()));
            });
    }

    @Override
    public Promise<List<EntityInterface>> queryEntities(String tenantId, String collectionName, QuerySpecInterface query) {
        requireOpen();
        validateTenantId(tenantId);
        validateCollectionName(collectionName);
        Objects.requireNonNull(query, "query is required");

        TenantContext tenant = TenantContext.of(tenantId);
        QuerySpec spec = toQuerySpec(query);

        return entityStore.query(tenant, collectionName, spec)
            .then(result -> {
                List<EntityInterface> entities = new ArrayList<>();
                for (Entity entity : result.entities()) {
                    entities.add(toEntityInterface(entity));
                }
                log.debug("Query returned {} entities from {}/{}", entities.size(), tenantId, collectionName);
                return Promise.of(entities);
            });
    }

    @Override
    public Promise<EntityInterface> updateEntity(String tenantId, String collectionName, String entityId, Map<String, Object> data) {
        requireOpen();
        validateTenantId(tenantId);
        validateCollectionName(collectionName);
        Objects.requireNonNull(entityId, "entityId is required");
        Objects.requireNonNull(data, "data is required");

        TenantContext tenant = TenantContext.of(tenantId);
        return entityStore.findById(tenant, new EntityId(UUID.fromString(entityId)))
            .then(opt -> {
                if (opt.isEmpty()) {
                    log.debug("Entity not found for update: {} in {}/{}", entityId, tenantId, collectionName);
                    return Promise.of((EntityInterface) null);
                }
                Entity existing = opt.get();
                Entity updated = new Entity(
                    existing.id(),
                    existing.tenantId(),
                    existing.collectionName(),
                    new HashMap<>(data),
                    Map.of(),
                    existing.createdAt(),
                    Instant.now(),
                    existing.version() + 1
                );
                return entityStore.save(tenant, updated)
                    .then(saved -> {
                        log.debug("Updated entity {} in {}/{}", entityId, tenantId, collectionName);
                        return Promise.of(toEntityInterface(saved));
                    });
            });
    }

    @Override
    public Promise<Void> deleteEntity(String tenantId, String collectionName, String entityId) {
        requireOpen();
        validateTenantId(tenantId);
        validateCollectionName(collectionName);
        Objects.requireNonNull(entityId, "entityId is required");

        TenantContext tenant = TenantContext.of(tenantId);
        return entityStore.delete(tenant, new EntityId(UUID.fromString(entityId)))
            .then(deleted -> {
                log.debug("Deleted entity {} from {}/{}", entityId, tenantId, collectionName);
                return Promise.of(null);
            });
    }

    @Override
    public Promise<String> appendEvent(String tenantId, String streamName, Map<String, Object> eventData) {
        requireOpen();
        validateTenantId(tenantId);
        Objects.requireNonNull(streamName, "streamName is required");
        Objects.requireNonNull(eventData, "eventData is required");

        TenantContext tenant = TenantContext.of(tenantId);
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        EventEntry entry = new EventEntry(
            eventId,
            streamName,
            "1.0",
            now,
            serializeData(eventData),
            "application/json",
            Map.of(),
            Optional.empty()
        );

        return eventLogStore.append(tenant, entry)
            .then(offset -> {
                log.debug("Appended event {} to stream {} (tenant: {})", eventId, streamName, tenantId);
                return Promise.of(offset.value());
            });
    }

    @Override
    public Promise<List<Map<String, Object>>> readEvents(String tenantId, String streamName, int limit) {
        requireOpen();
        validateTenantId(tenantId);
        Objects.requireNonNull(streamName, "streamName is required");

        TenantContext tenant = TenantContext.of(tenantId);
        return eventLogStore.read(tenant, com.ghatana.platform.types.identity.Offset.zero(), limit)
            .then(entries -> {
                List<Map<String, Object>> events = new ArrayList<>();
                for (EventEntry entry : entries) {
                    events.add(deserializeData(entry.payload()));
                }
                log.debug("Read {} events from stream {} (tenant: {})", events.size(), streamName, tenantId);
                return Promise.of(events);
            });
    }

    // ==================== Helper Methods ====================

    private void requireOpen() {
        if (!isOpen.get()) {
            throw new IllegalStateException("Client is not open. Call open() first.");
        }
    }

    private void validateTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId cannot be null or blank");
        }
    }

    private void validateCollectionName(String collectionName) {
        if (collectionName == null || collectionName.isBlank()) {
            throw new IllegalArgumentException("collectionName cannot be null or blank");
        }
    }

    private EntityInterface toEntityInterface(Entity entity) {
        return new SimpleEntityAdapter(entity);
    }

    private QuerySpec toQuerySpec(QuerySpecInterface query) {
        return QuerySpec.builder()
            .filter(query.filter())
            .limit(query.limit())
            .offset(query.offset())
            .build();
    }

    private java.nio.ByteBuffer serializeData(Map<String, Object> data) {
        try {
            byte[] bytes = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(data);
            return java.nio.ByteBuffer.wrap(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event data", e);
        }
    }

    private Map<String, Object> deserializeData(java.nio.ByteBuffer buffer) {
        try {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(bytes, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize event data", e);
        }
    }

    /**
     * Adapter for Entity to EntityInterface.
     */
    private static class SimpleEntityAdapter implements EntityInterface {
        private final Entity entity;

        SimpleEntityAdapter(Entity entity) {
            this.entity = entity;
        }

        @Override
        public String id() {
            return entity.id().value().toString();
        }

        @Override
        public String tenantId() {
            return entity.tenantId();
        }

        @Override
        public String collectionName() {
            return entity.collectionName();
        }

        @Override
        public Map<String, Object> data() {
            return entity.data();
        }

        @Override
        public Map<String, Object> metadata() {
            return entity.metadata();
        }

        @Override
        public Instant createdAt() {
            return entity.createdAt();
        }

        @Override
        public Instant updatedAt() {
            return entity.updatedAt();
        }

        @Override
        public long version() {
            return entity.version();
        }
    }
}
