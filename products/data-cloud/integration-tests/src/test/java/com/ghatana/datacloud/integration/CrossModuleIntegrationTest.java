/*
 * Copyright (c) 2026 Ghatana Inc. All rights reserved. // GH-90000
 */
package com.ghatana.datacloud.integration;

import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

/**
 * Cross-module integration tests for Data Cloud.
 *
 * <p>Exercises the full lifecycle flow across the conceptual boundaries of:
 * <ul>
 *   <li><em>Collection Management</em>: create collection → list → delete</li>
 *   <li><em>Entity Lifecycle</em>: create entity → read → update → delete within a collection</li>
 *   <li><em>Event Emission</em>: lifecycle operations emit observable domain events</li>
 *   <li><em>Cross-module consistency</em>: entity operations respect collection boundaries</li>
 *   <li><em>Multi-tenant isolation</em>: all stores scoped by tenantId</li>
 * </ul>
 *
 * <p>Uses lightweight in-memory implementations that satisfy the same contracts as the
 * real production adapters, keeping tests deterministic and fast while covering the
 * cross-module integration surface.
 *
 * <p>Tests are ordered so that state accumulated by earlier tests can be verified by later
 * ones (collection created with order 1 is still queryable by order 3). // GH-90000
 *
 * @doc.type class
 * @doc.purpose Cross-module integration tests for collection + entity + event lifecycle
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Cross-Module Integration – Collection, Entity, and Event Lifecycle")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // GH-90000
class CrossModuleIntegrationTest extends EventloopTestBase {

    // ─────────────────────────────────────────────────────────────────────────
    // Shared infrastructure (all tests share one set of stores) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    private static CollectionStore collectionStore;
    private static EntityStore     entityStore;
    private static AuditEventStore auditStore;
    private static CollectionService collectionService;
    private static EntityService     entityService;

    private static final String TENANT_A       = "cross-module-tenant-a";
    private static final String TENANT_B       = "cross-module-tenant-b";
    private static final String USER_ID        = "user-cross-mod-001";
    private static final String COLLECTION_NAME = "products";

    /** Persisted across ordered tests. */
    private static UUID createdCollectionId;
    private static UUID createdEntityId;

    @BeforeAll
    static void setUpAll() { // GH-90000
        auditStore        = new InMemoryAuditEventStore(); // GH-90000
        collectionStore   = new InMemoryCollectionStore(); // GH-90000
        entityStore       = new InMemoryEntityStore(); // GH-90000
        collectionService = new DefaultCollectionService(collectionStore, auditStore); // GH-90000
        entityService     = new DefaultEntityService(entityStore, collectionStore, auditStore); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Collection lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1) // GH-90000
    @DisplayName("Create collection — collection is stored and audit event is emitted")
    void step1_createCollection_storedAndAudited() throws Exception { // GH-90000
        CollectionRecord created = runPromise(() -> // GH-90000
            collectionService.createCollection(TENANT_A, COLLECTION_NAME, "Products", "Catalog data", USER_ID)); // GH-90000

        assertThat(created).isNotNull(); // GH-90000
        assertThat(created.id()).isNotNull(); // GH-90000
        assertThat(created.name()).isEqualTo(COLLECTION_NAME); // GH-90000
        assertThat(created.tenantId()).isEqualTo(TENANT_A); // GH-90000

        createdCollectionId = created.id(); // GH-90000

        // Audit event must have been emitted
        List<AuditEvent> audits = auditStore.getEvents(TENANT_A); // GH-90000
        assertThat(audits).hasSizeGreaterThanOrEqualTo(1); // GH-90000
        assertThat(audits.stream().anyMatch(e -> e.action().equals("COLLECTION_CREATED"))).isTrue();
    }

    @Test
    @Order(2) // GH-90000
    @DisplayName("List collections — new collection appears in tenant's list")
    void step2_listCollections_newCollectionVisible() throws Exception { // GH-90000
        List<CollectionRecord> collections = runPromise(() -> // GH-90000
            collectionService.listCollections(TENANT_A)); // GH-90000

        assertThat(collections).isNotEmpty(); // GH-90000
        assertThat(collections.stream().anyMatch(c -> c.id().equals(createdCollectionId))).isTrue(); // GH-90000
    }

    @Test
    @Order(3) // GH-90000
    @DisplayName("Create entity within collection — entity is stored with correct collection binding")
    void step3_createEntity_storedInCollection() throws Exception { // GH-90000
        Map<String, Object> data = Map.of("name", "Widget", "price", 9.99, "sku", "WGT-001"); // GH-90000

        EntityRecord entity = runPromise(() -> // GH-90000
            entityService.createEntity(TENANT_A, COLLECTION_NAME, data, USER_ID)); // GH-90000

        assertThat(entity).isNotNull(); // GH-90000
        assertThat(entity.id()).isNotNull(); // GH-90000
        assertThat(entity.tenantId()).isEqualTo(TENANT_A); // GH-90000
        assertThat(entity.collectionName()).isEqualTo(COLLECTION_NAME); // GH-90000
        assertThat(entity.data()).containsEntry("sku", "WGT-001"); // GH-90000

        createdEntityId = entity.id(); // GH-90000

        // Entity audit event emitted
        List<AuditEvent> audits = auditStore.getEvents(TENANT_A); // GH-90000
        assertThat(audits.stream().anyMatch(e -> e.action().equals("ENTITY_CREATED"))).isTrue();
    }

    @Test
    @Order(4) // GH-90000
    @DisplayName("Get entity by ID — returns entity matching the created one")
    void step4_getEntity_returnsCorrectEntity() throws Exception { // GH-90000
        EntityRecord fetched = runPromise(() -> // GH-90000
            entityService.getEntity(TENANT_A, COLLECTION_NAME, createdEntityId)); // GH-90000

        assertThat(fetched.id()).isEqualTo(createdEntityId); // GH-90000
        assertThat(fetched.data()).containsEntry("sku", "WGT-001"); // GH-90000
        assertThat(fetched.version()).isEqualTo(1L); // GH-90000
    }

    @Test
    @Order(5) // GH-90000
    @DisplayName("Update entity — version increments, data reflects update")
    void step5_updateEntity_versionIncrements() throws Exception { // GH-90000
        Map<String, Object> updatedData = Map.of("name", "Widget Pro", "price", 19.99, "sku", "WGT-001"); // GH-90000

        EntityRecord updated = runPromise(() -> // GH-90000
            entityService.updateEntity(TENANT_A, COLLECTION_NAME, createdEntityId, updatedData, USER_ID)); // GH-90000

        assertThat(updated.data()).containsEntry("name", "Widget Pro"); // GH-90000
        assertThat(updated.version()).isGreaterThan(1L); // GH-90000

        // Re-fetch and verify
        EntityRecord refetched = runPromise(() -> // GH-90000
            entityService.getEntity(TENANT_A, COLLECTION_NAME, createdEntityId)); // GH-90000
        assertThat(refetched.data()).containsEntry("price", 19.99); // GH-90000
    }

    @Test
    @Order(6) // GH-90000
    @DisplayName("Delete entity — entity not retrievable after deletion")
    void step6_deleteEntity_notRetrievableAfterDelete() throws Exception { // GH-90000
        runPromise(() -> entityService.deleteEntity(TENANT_A, COLLECTION_NAME, createdEntityId, USER_ID)); // GH-90000

        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> entityService.getEntity(TENANT_A, COLLECTION_NAME, createdEntityId))) // GH-90000
            .isInstanceOf(NoSuchElementException.class); // GH-90000

        // Delete audit event emitted
        List<AuditEvent> audits = auditStore.getEvents(TENANT_A); // GH-90000
        assertThat(audits.stream().anyMatch(e -> e.action().equals("ENTITY_DELETED"))).isTrue();
    }

    @Test
    @Order(7) // GH-90000
    @DisplayName("Delete collection — collection removed from list")
    void step7_deleteCollection_removedFromList() throws Exception { // GH-90000
        runPromise(() -> // GH-90000
            collectionService.deleteCollection(TENANT_A, createdCollectionId, USER_ID)); // GH-90000

        List<CollectionRecord> collections = runPromise(() -> // GH-90000
            collectionService.listCollections(TENANT_A)); // GH-90000
        assertThat(collections.stream().noneMatch(c -> c.id().equals(createdCollectionId))).isTrue(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cross-module consistency
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(8) // GH-90000
    @DisplayName("Create entity in non-existent collection — rejected with exception")
    void createEntityInNonExistentCollection_rejected() throws Exception { // GH-90000
        Map<String, Object> data = Map.of("orphan", true); // GH-90000

        assertThatThrownBy(() -> // GH-90000
            runPromise(() -> entityService.createEntity(TENANT_A, "no-such-collection", data, USER_ID))) // GH-90000
            .isInstanceOf(IllegalArgumentException.class) // GH-90000
            .hasMessageContaining("no-such-collection");
    }

    @Test
    @Order(9) // GH-90000
    @DisplayName("Multiple entities within same collection share collection metadata")
    void multipleEntities_sameCollectionName_allBoundToCollection() throws Exception { // GH-90000
        String colName = "shared-collection";
        runPromise(() -> collectionService.createCollection(TENANT_A, colName, "Shared", "", USER_ID)); // GH-90000

        for (int i = 0; i < 5; i++) { // GH-90000
            Map<String, Object> data = Map.of("index", i); // GH-90000
            EntityRecord entity = runPromise(() -> entityService.createEntity(TENANT_A, colName, data, USER_ID)); // GH-90000
            assertThat(entity.collectionName()).isEqualTo(colName); // GH-90000
        }

        List<EntityRecord> entities = runPromise(() -> entityService.listEntities(TENANT_A, colName)); // GH-90000
        assertThat(entities).hasSize(5); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Multi-tenant isolation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(10) // GH-90000
    @DisplayName("Tenant B collections do not appear in Tenant A's list")
    void tenantIsolation_collectionsNotCrossVisible() throws Exception { // GH-90000
        runPromise(() -> collectionService.createCollection(TENANT_B, "tenant-b-col", "TenantB", "", USER_ID)); // GH-90000

        List<CollectionRecord> tenantACollections = runPromise(() -> // GH-90000
            collectionService.listCollections(TENANT_A)); // GH-90000
        assertThat(tenantACollections.stream() // GH-90000
            .anyMatch(c -> c.tenantId().equals(TENANT_B))).isFalse(); // GH-90000
    }

    @Test
    @Order(11) // GH-90000
    @DisplayName("Audit trail is tenant-scoped — Tenant A cannot see Tenant B's audit events")
    void auditTrailIsolation_tenantScoped() throws Exception { // GH-90000
        // Trigger some tenant-B activity
        runPromise(() -> collectionService.createCollection(TENANT_B, "tenant-b-audit-col", "TB Audit", "", USER_ID)); // GH-90000

        List<AuditEvent> tenantAEvents = auditStore.getEvents(TENANT_A); // GH-90000
        assertThat(tenantAEvents.stream().allMatch(e -> e.tenantId().equals(TENANT_A))).isTrue(); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Audit event completeness
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(12) // GH-90000
    @DisplayName("Audit trail covers all lifecycle actions for Tenant A")
    void auditTrail_coversAllLifecycleActions() throws Exception { // GH-90000
        List<AuditEvent> audits = auditStore.getEvents(TENANT_A); // GH-90000
        Set<String> observedActions = new HashSet<>(); // GH-90000
        audits.forEach(e -> observedActions.add(e.action())); // GH-90000

        assertThat(observedActions).contains( // GH-90000
            "COLLECTION_CREATED",
            "ENTITY_CREATED",
            "ENTITY_UPDATED",
            "ENTITY_DELETED",
            "COLLECTION_DELETED"
        );
    }

    // =========================================================================
    // Domain types — local contracts used by this integration test
    // =========================================================================

    record CollectionRecord(UUID id, String tenantId, String name, String label, String description) {} // GH-90000

    record EntityRecord(UUID id, String tenantId, String collectionName, Map<String, Object> data, // GH-90000
                        Instant createdAt, Instant updatedAt, long version) {}

    record AuditEvent(String tenantId, String action, String entityId, Instant timestamp) {} // GH-90000

    // ─── Service interfaces ───

    interface CollectionService {
        Promise<CollectionRecord> createCollection( // GH-90000
            String tenantId, String name, String label, String description, String userId);
        Promise<List<CollectionRecord>> listCollections(String tenantId); // GH-90000
        Promise<Optional<CollectionRecord>> getCollection(String tenantId, String name); // GH-90000
        Promise<Void> deleteCollection(String tenantId, UUID collectionId, String userId); // GH-90000
    }

    interface EntityService {
        Promise<EntityRecord> createEntity( // GH-90000
            String tenantId, String collectionName, Map<String, Object> data, String userId);
        Promise<EntityRecord> getEntity(String tenantId, String collectionName, UUID entityId); // GH-90000
        Promise<List<EntityRecord>> listEntities(String tenantId, String collectionName); // GH-90000
        Promise<EntityRecord> updateEntity( // GH-90000
            String tenantId, String collectionName, UUID entityId, Map<String, Object> data, String userId);
        Promise<Void> deleteEntity( // GH-90000
            String tenantId, String collectionName, UUID entityId, String userId);
    }

    interface CollectionStore {
        void save(CollectionRecord record); // GH-90000
        Optional<CollectionRecord> findById(String tenantId, UUID id); // GH-90000
        Optional<CollectionRecord> findByName(String tenantId, String name); // GH-90000
        List<CollectionRecord> findAll(String tenantId); // GH-90000
        void delete(String tenantId, UUID id); // GH-90000
    }

    interface EntityStore {
        void save(EntityRecord record); // GH-90000
        Optional<EntityRecord> findById(String tenantId, String collectionName, UUID id); // GH-90000
        List<EntityRecord> findAll(String tenantId, String collectionName); // GH-90000
        void delete(String tenantId, String collectionName, UUID id); // GH-90000
    }

    interface AuditEventStore {
        void record(AuditEvent event); // GH-90000
        List<AuditEvent> getEvents(String tenantId); // GH-90000
    }

    // ─── In-memory implementations ───

    static class InMemoryCollectionStore implements CollectionStore {
        private final Map<String, Map<UUID, CollectionRecord>> store = new ConcurrentHashMap<>(); // GH-90000

        @Override
        public void save(CollectionRecord record) { // GH-90000
            store.computeIfAbsent(record.tenantId(), k -> new ConcurrentHashMap<>()) // GH-90000
                 .put(record.id(), record); // GH-90000
        }

        @Override
        public Optional<CollectionRecord> findById(String tenantId, UUID id) { // GH-90000
            return Optional.ofNullable(store.getOrDefault(tenantId, Map.of()).get(id)); // GH-90000
        }

        @Override
        public Optional<CollectionRecord> findByName(String tenantId, String name) { // GH-90000
            return store.getOrDefault(tenantId, Map.of()).values().stream() // GH-90000
                .filter(c -> c.name().equals(name)).findFirst(); // GH-90000
        }

        @Override
        public List<CollectionRecord> findAll(String tenantId) { // GH-90000
            return new ArrayList<>(store.getOrDefault(tenantId, Map.of()).values()); // GH-90000
        }

        @Override
        public void delete(String tenantId, UUID id) { // GH-90000
            store.getOrDefault(tenantId, Map.of()).remove(id); // GH-90000
        }
    }

    static class InMemoryEntityStore implements EntityStore {
        private final Map<String, Map<UUID, EntityRecord>> store = new ConcurrentHashMap<>(); // GH-90000

        private String key(String tenantId, String collectionName) { // GH-90000
            return tenantId + "|" + collectionName;
        }

        @Override
        public void save(EntityRecord record) { // GH-90000
            store.computeIfAbsent(key(record.tenantId(), record.collectionName()), k -> new ConcurrentHashMap<>()) // GH-90000
                 .put(record.id(), record); // GH-90000
        }

        @Override
        public Optional<EntityRecord> findById(String tenantId, String collectionName, UUID id) { // GH-90000
            return Optional.ofNullable(store.getOrDefault(key(tenantId, collectionName), Map.of()).get(id)); // GH-90000
        }

        @Override
        public List<EntityRecord> findAll(String tenantId, String collectionName) { // GH-90000
            return new ArrayList<>(store.getOrDefault(key(tenantId, collectionName), Map.of()).values()); // GH-90000
        }

        @Override
        public void delete(String tenantId, String collectionName, UUID id) { // GH-90000
            store.getOrDefault(key(tenantId, collectionName), Map.of()).remove(id); // GH-90000
        }
    }

    static class InMemoryAuditEventStore implements AuditEventStore {
        private final Map<String, List<AuditEvent>> events = new ConcurrentHashMap<>(); // GH-90000

        @Override
        public void record(AuditEvent event) { // GH-90000
            events.computeIfAbsent(event.tenantId(), k -> Collections.synchronizedList(new ArrayList<>())) // GH-90000
                  .add(event); // GH-90000
        }

        @Override
        public List<AuditEvent> getEvents(String tenantId) { // GH-90000
            return Collections.unmodifiableList(events.getOrDefault(tenantId, List.of())); // GH-90000
        }
    }

    static class DefaultCollectionService implements CollectionService {
        private final CollectionStore store;
        private final AuditEventStore audit;

        DefaultCollectionService(CollectionStore store, AuditEventStore audit) { // GH-90000
            this.store = store;
            this.audit = audit;
        }

        @Override
        public Promise<CollectionRecord> createCollection( // GH-90000
                String tenantId, String name, String label, String description, String userId) {
            return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> { // GH-90000
                CollectionRecord record = new CollectionRecord(UUID.randomUUID(), tenantId, name, label, description); // GH-90000
                store.save(record); // GH-90000
                audit.record(new AuditEvent(tenantId, "COLLECTION_CREATED", record.id().toString(), Instant.now())); // GH-90000
                return record;
            });
        }

        @Override
        public Promise<List<CollectionRecord>> listCollections(String tenantId) { // GH-90000
            return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> store.findAll(tenantId)); // GH-90000
        }

        @Override
        public Promise<Optional<CollectionRecord>> getCollection(String tenantId, String name) { // GH-90000
            return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> store.findByName(tenantId, name)); // GH-90000
        }

        @Override
        public Promise<Void> deleteCollection(String tenantId, UUID collectionId, String userId) { // GH-90000
            return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> { // GH-90000
                store.delete(tenantId, collectionId); // GH-90000
                audit.record(new AuditEvent(tenantId, "COLLECTION_DELETED", collectionId.toString(), Instant.now())); // GH-90000
                return null;
            });
        }
    }

    static class DefaultEntityService implements EntityService {
        private final EntityStore entityStore;
        private final CollectionStore collectionStore;
        private final AuditEventStore audit;
        private final AtomicLong versionSeq = new AtomicLong(0); // GH-90000

        DefaultEntityService(EntityStore entityStore, CollectionStore collectionStore, AuditEventStore audit) { // GH-90000
            this.entityStore    = entityStore;
            this.collectionStore = collectionStore;
            this.audit          = audit;
        }

        @Override
        public Promise<EntityRecord> createEntity( // GH-90000
                String tenantId, String collectionName, Map<String, Object> data, String userId) {
            return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> { // GH-90000
                Optional<CollectionRecord> col = collectionStore.findByName(tenantId, collectionName); // GH-90000
                if (col.isEmpty()) { // GH-90000
                    throw new IllegalArgumentException("Collection does not exist: " + collectionName); // GH-90000
                }
                Instant now = Instant.now(); // GH-90000
                EntityRecord entity = new EntityRecord( // GH-90000
                    UUID.randomUUID(), tenantId, collectionName, // GH-90000
                    new HashMap<>(data), now, now, 1L); // GH-90000
                entityStore.save(entity); // GH-90000
                audit.record(new AuditEvent(tenantId, "ENTITY_CREATED", entity.id().toString(), now)); // GH-90000
                return entity;
            });
        }

        @Override
        public Promise<EntityRecord> getEntity(String tenantId, String collectionName, UUID entityId) { // GH-90000
            return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> // GH-90000
                entityStore.findById(tenantId, collectionName, entityId) // GH-90000
                    .orElseThrow(() -> new NoSuchElementException( // GH-90000
                        "Entity not found: " + entityId + " in collection " + collectionName)));
        }

        @Override
        public Promise<List<EntityRecord>> listEntities(String tenantId, String collectionName) { // GH-90000
            return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> // GH-90000
                entityStore.findAll(tenantId, collectionName)); // GH-90000
        }

        @Override
        public Promise<EntityRecord> updateEntity( // GH-90000
                String tenantId, String collectionName, UUID entityId,
                Map<String, Object> data, String userId) {
            return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> { // GH-90000
                EntityRecord existing = entityStore.findById(tenantId, collectionName, entityId) // GH-90000
                    .orElseThrow(() -> new NoSuchElementException("Entity not found: " + entityId)); // GH-90000
                Instant now = Instant.now(); // GH-90000
                EntityRecord updated = new EntityRecord( // GH-90000
                    existing.id(), tenantId, collectionName, // GH-90000
                    new HashMap<>(data), existing.createdAt(), now, existing.version() + 1); // GH-90000
                entityStore.save(updated); // GH-90000
                audit.record(new AuditEvent(tenantId, "ENTITY_UPDATED", entityId.toString(), now)); // GH-90000
                return updated;
            });
        }

        @Override
        public Promise<Void> deleteEntity( // GH-90000
                String tenantId, String collectionName, UUID entityId, String userId) {
            return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> { // GH-90000
                entityStore.delete(tenantId, collectionName, entityId); // GH-90000
                audit.record(new AuditEvent(tenantId, "ENTITY_DELETED", entityId.toString(), Instant.now())); // GH-90000
                return null;
            });
        }
    }
}
