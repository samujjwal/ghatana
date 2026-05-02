/*
 * Copyright (c) 2026 Ghatana Inc. All rights reserved. 
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
 * ones (collection created with order 1 is still queryable by order 3). 
 *
 * @doc.type class
 * @doc.purpose Cross-module integration tests for collection + entity + event lifecycle
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */
@DisplayName("Cross-Module Integration – Collection, Entity, and Event Lifecycle")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class) 
class CrossModuleIntegrationTest extends EventloopTestBase {

    // ─────────────────────────────────────────────────────────────────────────
    // Shared infrastructure (all tests share one set of stores) 
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
    static void setUpAll() { 
        auditStore        = new InMemoryAuditEventStore(); 
        collectionStore   = new InMemoryCollectionStore(); 
        entityStore       = new InMemoryEntityStore(); 
        collectionService = new DefaultCollectionService(collectionStore, auditStore); 
        entityService     = new DefaultEntityService(entityStore, collectionStore, auditStore); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Collection lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(1) 
    @DisplayName("Create collection — collection is stored and audit event is emitted")
    void step1_createCollection_storedAndAudited() throws Exception { 
        CollectionRecord created = runPromise(() -> 
            collectionService.createCollection(TENANT_A, COLLECTION_NAME, "Products", "Catalog data", USER_ID)); 

        assertThat(created).isNotNull(); 
        assertThat(created.id()).isNotNull(); 
        assertThat(created.name()).isEqualTo(COLLECTION_NAME); 
        assertThat(created.tenantId()).isEqualTo(TENANT_A); 

        createdCollectionId = created.id(); 

        // Audit event must have been emitted
        List<AuditEvent> audits = auditStore.getEvents(TENANT_A); 
        assertThat(audits).hasSizeGreaterThanOrEqualTo(1); 
        assertThat(audits.stream().anyMatch(e -> e.action().equals("COLLECTION_CREATED"))).isTrue();
    }

    @Test
    @Order(2) 
    @DisplayName("List collections — new collection appears in tenant's list")
    void step2_listCollections_newCollectionVisible() throws Exception { 
        List<CollectionRecord> collections = runPromise(() -> 
            collectionService.listCollections(TENANT_A)); 

        assertThat(collections).isNotEmpty(); 
        assertThat(collections.stream().anyMatch(c -> c.id().equals(createdCollectionId))).isTrue(); 
    }

    @Test
    @Order(3) 
    @DisplayName("Create entity within collection — entity is stored with correct collection binding")
    void step3_createEntity_storedInCollection() throws Exception { 
        Map<String, Object> data = Map.of("name", "Widget", "price", 9.99, "sku", "WGT-001"); 

        EntityRecord entity = runPromise(() -> 
            entityService.createEntity(TENANT_A, COLLECTION_NAME, data, USER_ID)); 

        assertThat(entity).isNotNull(); 
        assertThat(entity.id()).isNotNull(); 
        assertThat(entity.tenantId()).isEqualTo(TENANT_A); 
        assertThat(entity.collectionName()).isEqualTo(COLLECTION_NAME); 
        assertThat(entity.data()).containsEntry("sku", "WGT-001"); 

        createdEntityId = entity.id(); 

        // Entity audit event emitted
        List<AuditEvent> audits = auditStore.getEvents(TENANT_A); 
        assertThat(audits.stream().anyMatch(e -> e.action().equals("ENTITY_CREATED"))).isTrue();
    }

    @Test
    @Order(4) 
    @DisplayName("Get entity by ID — returns entity matching the created one")
    void step4_getEntity_returnsCorrectEntity() throws Exception { 
        EntityRecord fetched = runPromise(() -> 
            entityService.getEntity(TENANT_A, COLLECTION_NAME, createdEntityId)); 

        assertThat(fetched.id()).isEqualTo(createdEntityId); 
        assertThat(fetched.data()).containsEntry("sku", "WGT-001"); 
        assertThat(fetched.version()).isEqualTo(1L); 
    }

    @Test
    @Order(5) 
    @DisplayName("Update entity — version increments, data reflects update")
    void step5_updateEntity_versionIncrements() throws Exception { 
        Map<String, Object> updatedData = Map.of("name", "Widget Pro", "price", 19.99, "sku", "WGT-001"); 

        EntityRecord updated = runPromise(() -> 
            entityService.updateEntity(TENANT_A, COLLECTION_NAME, createdEntityId, updatedData, USER_ID)); 

        assertThat(updated.data()).containsEntry("name", "Widget Pro"); 
        assertThat(updated.version()).isGreaterThan(1L); 

        // Re-fetch and verify
        EntityRecord refetched = runPromise(() -> 
            entityService.getEntity(TENANT_A, COLLECTION_NAME, createdEntityId)); 
        assertThat(refetched.data()).containsEntry("price", 19.99); 
    }

    @Test
    @Order(6) 
    @DisplayName("Delete entity — entity not retrievable after deletion")
    void step6_deleteEntity_notRetrievableAfterDelete() throws Exception { 
        runPromise(() -> entityService.deleteEntity(TENANT_A, COLLECTION_NAME, createdEntityId, USER_ID)); 

        assertThatThrownBy(() -> 
            runPromise(() -> entityService.getEntity(TENANT_A, COLLECTION_NAME, createdEntityId))) 
            .isInstanceOf(NoSuchElementException.class); 

        // Delete audit event emitted
        List<AuditEvent> audits = auditStore.getEvents(TENANT_A); 
        assertThat(audits.stream().anyMatch(e -> e.action().equals("ENTITY_DELETED"))).isTrue();
    }

    @Test
    @Order(7) 
    @DisplayName("Delete collection — collection removed from list")
    void step7_deleteCollection_removedFromList() throws Exception { 
        runPromise(() -> 
            collectionService.deleteCollection(TENANT_A, createdCollectionId, USER_ID)); 

        List<CollectionRecord> collections = runPromise(() -> 
            collectionService.listCollections(TENANT_A)); 
        assertThat(collections.stream().noneMatch(c -> c.id().equals(createdCollectionId))).isTrue(); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cross-module consistency
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(8) 
    @DisplayName("Create entity in non-existent collection — rejected with exception")
    void createEntityInNonExistentCollection_rejected() throws Exception { 
        Map<String, Object> data = Map.of("orphan", true); 

        assertThatThrownBy(() -> 
            runPromise(() -> entityService.createEntity(TENANT_A, "no-such-collection", data, USER_ID))) 
            .isInstanceOf(IllegalArgumentException.class) 
            .hasMessageContaining("no-such-collection");
    }

    @Test
    @Order(9) 
    @DisplayName("Multiple entities within same collection share collection metadata")
    void multipleEntities_sameCollectionName_allBoundToCollection() throws Exception { 
        String colName = "shared-collection";
        runPromise(() -> collectionService.createCollection(TENANT_A, colName, "Shared", "", USER_ID)); 

        for (int i = 0; i < 5; i++) { 
            Map<String, Object> data = Map.of("index", i); 
            EntityRecord entity = runPromise(() -> entityService.createEntity(TENANT_A, colName, data, USER_ID)); 
            assertThat(entity.collectionName()).isEqualTo(colName); 
        }

        List<EntityRecord> entities = runPromise(() -> entityService.listEntities(TENANT_A, colName)); 
        assertThat(entities).hasSize(5); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Multi-tenant isolation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(10) 
    @DisplayName("Tenant B collections do not appear in Tenant A's list")
    void tenantIsolation_collectionsNotCrossVisible() throws Exception { 
        runPromise(() -> collectionService.createCollection(TENANT_B, "tenant-b-col", "TenantB", "", USER_ID)); 

        List<CollectionRecord> tenantACollections = runPromise(() -> 
            collectionService.listCollections(TENANT_A)); 
        assertThat(tenantACollections.stream() 
            .anyMatch(c -> c.tenantId().equals(TENANT_B))).isFalse(); 
    }

    @Test
    @Order(11) 
    @DisplayName("Audit trail is tenant-scoped — Tenant A cannot see Tenant B's audit events")
    void auditTrailIsolation_tenantScoped() throws Exception { 
        // Trigger some tenant-B activity
        runPromise(() -> collectionService.createCollection(TENANT_B, "tenant-b-audit-col", "TB Audit", "", USER_ID)); 

        List<AuditEvent> tenantAEvents = auditStore.getEvents(TENANT_A); 
        assertThat(tenantAEvents.stream().allMatch(e -> e.tenantId().equals(TENANT_A))).isTrue(); 
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Audit event completeness
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @Order(12) 
    @DisplayName("Audit trail covers all lifecycle actions for Tenant A")
    void auditTrail_coversAllLifecycleActions() throws Exception { 
        List<AuditEvent> audits = auditStore.getEvents(TENANT_A); 
        Set<String> observedActions = new HashSet<>(); 
        audits.forEach(e -> observedActions.add(e.action())); 

        assertThat(observedActions).contains( 
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

    record CollectionRecord(UUID id, String tenantId, String name, String label, String description) {} 

    record EntityRecord(UUID id, String tenantId, String collectionName, Map<String, Object> data, 
                        Instant createdAt, Instant updatedAt, long version) {}

    record AuditEvent(String tenantId, String action, String entityId, Instant timestamp) {} 

    // ─── Service interfaces ───

    interface CollectionService {
        Promise<CollectionRecord> createCollection( 
            String tenantId, String name, String label, String description, String userId);
        Promise<List<CollectionRecord>> listCollections(String tenantId); 
        Promise<Optional<CollectionRecord>> getCollection(String tenantId, String name); 
        Promise<Void> deleteCollection(String tenantId, UUID collectionId, String userId); 
    }

    interface EntityService {
        Promise<EntityRecord> createEntity( 
            String tenantId, String collectionName, Map<String, Object> data, String userId);
        Promise<EntityRecord> getEntity(String tenantId, String collectionName, UUID entityId); 
        Promise<List<EntityRecord>> listEntities(String tenantId, String collectionName); 
        Promise<EntityRecord> updateEntity( 
            String tenantId, String collectionName, UUID entityId, Map<String, Object> data, String userId);
        Promise<Void> deleteEntity( 
            String tenantId, String collectionName, UUID entityId, String userId);
    }

    interface CollectionStore {
        void save(CollectionRecord record); 
        Optional<CollectionRecord> findById(String tenantId, UUID id); 
        Optional<CollectionRecord> findByName(String tenantId, String name); 
        List<CollectionRecord> findAll(String tenantId); 
        void delete(String tenantId, UUID id); 
    }

    interface EntityStore {
        void save(EntityRecord record); 
        Optional<EntityRecord> findById(String tenantId, String collectionName, UUID id); 
        List<EntityRecord> findAll(String tenantId, String collectionName); 
        void delete(String tenantId, String collectionName, UUID id); 
    }

    interface AuditEventStore {
        void record(AuditEvent event); 
        List<AuditEvent> getEvents(String tenantId); 
    }

    // ─── In-memory implementations ───

    static class InMemoryCollectionStore implements CollectionStore {
        private final Map<String, Map<UUID, CollectionRecord>> store = new ConcurrentHashMap<>(); 

        @Override
        public void save(CollectionRecord record) { 
            store.computeIfAbsent(record.tenantId(), k -> new ConcurrentHashMap<>()) 
                 .put(record.id(), record); 
        }

        @Override
        public Optional<CollectionRecord> findById(String tenantId, UUID id) { 
            return Optional.ofNullable(store.getOrDefault(tenantId, Map.of()).get(id)); 
        }

        @Override
        public Optional<CollectionRecord> findByName(String tenantId, String name) { 
            return store.getOrDefault(tenantId, Map.of()).values().stream() 
                .filter(c -> c.name().equals(name)).findFirst(); 
        }

        @Override
        public List<CollectionRecord> findAll(String tenantId) { 
            return new ArrayList<>(store.getOrDefault(tenantId, Map.of()).values()); 
        }

        @Override
        public void delete(String tenantId, UUID id) { 
            store.getOrDefault(tenantId, Map.of()).remove(id); 
        }
    }

    static class InMemoryEntityStore implements EntityStore {
        private final Map<String, Map<UUID, EntityRecord>> store = new ConcurrentHashMap<>(); 

        private String key(String tenantId, String collectionName) { 
            return tenantId + "|" + collectionName;
        }

        @Override
        public void save(EntityRecord record) { 
            store.computeIfAbsent(key(record.tenantId(), record.collectionName()), k -> new ConcurrentHashMap<>()) 
                 .put(record.id(), record); 
        }

        @Override
        public Optional<EntityRecord> findById(String tenantId, String collectionName, UUID id) { 
            return Optional.ofNullable(store.getOrDefault(key(tenantId, collectionName), Map.of()).get(id)); 
        }

        @Override
        public List<EntityRecord> findAll(String tenantId, String collectionName) { 
            return new ArrayList<>(store.getOrDefault(key(tenantId, collectionName), Map.of()).values()); 
        }

        @Override
        public void delete(String tenantId, String collectionName, UUID id) { 
            store.getOrDefault(key(tenantId, collectionName), Map.of()).remove(id); 
        }
    }

    static class InMemoryAuditEventStore implements AuditEventStore {
        private final Map<String, List<AuditEvent>> events = new ConcurrentHashMap<>(); 

        @Override
        public void record(AuditEvent event) { 
            events.computeIfAbsent(event.tenantId(), k -> Collections.synchronizedList(new ArrayList<>())) 
                  .add(event); 
        }

        @Override
        public List<AuditEvent> getEvents(String tenantId) { 
            return Collections.unmodifiableList(events.getOrDefault(tenantId, List.of())); 
        }
    }

    static class DefaultCollectionService implements CollectionService {
        private final CollectionStore store;
        private final AuditEventStore audit;

        DefaultCollectionService(CollectionStore store, AuditEventStore audit) { 
            this.store = store;
            this.audit = audit;
        }

        @Override
        public Promise<CollectionRecord> createCollection( 
                String tenantId, String name, String label, String description, String userId) {
            return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> { 
                CollectionRecord record = new CollectionRecord(UUID.randomUUID(), tenantId, name, label, description); 
                store.save(record); 
                audit.record(new AuditEvent(tenantId, "COLLECTION_CREATED", record.id().toString(), Instant.now())); 
                return record;
            });
        }

        @Override
        public Promise<List<CollectionRecord>> listCollections(String tenantId) { 
            return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> store.findAll(tenantId)); 
        }

        @Override
        public Promise<Optional<CollectionRecord>> getCollection(String tenantId, String name) { 
            return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> store.findByName(tenantId, name)); 
        }

        @Override
        public Promise<Void> deleteCollection(String tenantId, UUID collectionId, String userId) { 
            return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> { 
                store.delete(tenantId, collectionId); 
                audit.record(new AuditEvent(tenantId, "COLLECTION_DELETED", collectionId.toString(), Instant.now())); 
                return null;
            });
        }
    }

    static class DefaultEntityService implements EntityService {
        private final EntityStore entityStore;
        private final CollectionStore collectionStore;
        private final AuditEventStore audit;
        private final AtomicLong versionSeq = new AtomicLong(0); 

        DefaultEntityService(EntityStore entityStore, CollectionStore collectionStore, AuditEventStore audit) { 
            this.entityStore    = entityStore;
            this.collectionStore = collectionStore;
            this.audit          = audit;
        }

        @Override
        public Promise<EntityRecord> createEntity( 
                String tenantId, String collectionName, Map<String, Object> data, String userId) {
            return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> { 
                Optional<CollectionRecord> col = collectionStore.findByName(tenantId, collectionName); 
                if (col.isEmpty()) { 
                    throw new IllegalArgumentException("Collection does not exist: " + collectionName); 
                }
                Instant now = Instant.now(); 
                EntityRecord entity = new EntityRecord( 
                    UUID.randomUUID(), tenantId, collectionName, 
                    new HashMap<>(data), now, now, 1L); 
                entityStore.save(entity); 
                audit.record(new AuditEvent(tenantId, "ENTITY_CREATED", entity.id().toString(), now)); 
                return entity;
            });
        }

        @Override
        public Promise<EntityRecord> getEntity(String tenantId, String collectionName, UUID entityId) { 
            return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> 
                entityStore.findById(tenantId, collectionName, entityId) 
                    .orElseThrow(() -> new NoSuchElementException( 
                        "Entity not found: " + entityId + " in collection " + collectionName)));
        }

        @Override
        public Promise<List<EntityRecord>> listEntities(String tenantId, String collectionName) { 
            return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> 
                entityStore.findAll(tenantId, collectionName)); 
        }

        @Override
        public Promise<EntityRecord> updateEntity( 
                String tenantId, String collectionName, UUID entityId,
                Map<String, Object> data, String userId) {
            return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> { 
                EntityRecord existing = entityStore.findById(tenantId, collectionName, entityId) 
                    .orElseThrow(() -> new NoSuchElementException("Entity not found: " + entityId)); 
                Instant now = Instant.now(); 
                EntityRecord updated = new EntityRecord( 
                    existing.id(), tenantId, collectionName, 
                    new HashMap<>(data), existing.createdAt(), now, existing.version() + 1); 
                entityStore.save(updated); 
                audit.record(new AuditEvent(tenantId, "ENTITY_UPDATED", entityId.toString(), now)); 
                return updated;
            });
        }

        @Override
        public Promise<Void> deleteEntity( 
                String tenantId, String collectionName, UUID entityId, String userId) {
            return Promise.ofBlocking(ForkJoinPool.commonPool(), () -> { 
                entityStore.delete(tenantId, collectionName, entityId); 
                audit.record(new AuditEvent(tenantId, "ENTITY_DELETED", entityId.toString(), Instant.now())); 
                return null;
            });
        }
    }
}
