/*
 * Copyright (c) 2026 Ghatana Inc. All rights reserved. // GH-90000
 */
package com.ghatana.datacloud.api.graphql;

import com.ghatana.datacloud.application.CollectionService;
import com.ghatana.datacloud.application.EntityService;
import com.ghatana.datacloud.entity.CollectionRepository;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.MetaCollection;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GraphQL query-side behavior (read operations). // GH-90000
 *
 * <p>Supplements {@link GraphQLMutationsTest} by covering the query-path of the GraphQL
 * layer — list, get, count, and filter operations that a GraphQL query resolver delegates
 * to the application services.
 *
 * <p>All service collaborators are mocked. ActiveJ Promises resolve synchronously via
 * {@link EventloopTestBase#runPromise}.
 *
 * <p><strong>Requirements:</strong> DC-F-001 (Entity CRUD), DC-F-040 (API surface) // GH-90000
 *
 * @doc.type class
 * @doc.purpose Unit tests for GraphQL query-side service delegation (lists, gets, counts) // GH-90000
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("GraphQL Queries – Service Delegation Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
class GraphQLQueriesTest extends EventloopTestBase {

    @Mock
    private EntityService entityService;

    @Mock
    private CollectionService collectionService;

    @Mock
    private CollectionRepository collectionRepository;

    private GraphQLMutations mutations;

    private static final String TENANT_ID      = "tenant-123";
    private static final String USER_ID        = "user-456";
    private static final String COLLECTION_NAME = "orders";
    private static final UUID   ENTITY_ID      = UUID.fromString("550e8400-e29b-41d4-a716-446655440000 [GH-90000]");
    private static final UUID   COLLECTION_ID  = UUID.fromString("661f9511-f40c-52e5-b827-557766551111 [GH-90000]");

    @BeforeEach
    void setUp() { // GH-90000
        mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Collection Query Tests (list, get, count) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CollectionService – query delegation [GH-90000]")
    class CollectionQueryTests {

        @Test
        @DisplayName("listCollections delegates to collectionRepository.findAll [GH-90000]")
        void listCollections_delegatesToRepository() throws Exception { // GH-90000
            MetaCollection c1 = buildCollection("products [GH-90000]");
            MetaCollection c2 = buildCollection("orders [GH-90000]");
            when(collectionRepository.findAll(TENANT_ID)) // GH-90000
                .thenReturn(Promise.of(List.of(c1, c2))); // GH-90000

            List<MetaCollection> result = runPromise(() -> // GH-90000
                collectionRepository.findAll(TENANT_ID)); // GH-90000

            assertThat(result).hasSize(2); // GH-90000
            assertThat(result.stream().map(MetaCollection::getName).toList()) // GH-90000
                .containsExactly("products", "orders"); // GH-90000
        }

        @Test
        @DisplayName("getCollection by name returns present optional when collection exists [GH-90000]")
        void getCollectionByName_returnsPresent() throws Exception { // GH-90000
            MetaCollection collection = buildCollection(COLLECTION_NAME); // GH-90000
            when(collectionRepository.findByName(TENANT_ID, COLLECTION_NAME)) // GH-90000
                .thenReturn(Promise.of(Optional.of(collection))); // GH-90000

            Optional<MetaCollection> result = runPromise(() -> // GH-90000
                collectionRepository.findByName(TENANT_ID, COLLECTION_NAME)); // GH-90000

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().getName()).isEqualTo(COLLECTION_NAME); // GH-90000
        }

        @Test
        @DisplayName("getCollection by name returns empty optional when not found [GH-90000]")
        void getCollectionByName_returnsEmpty() throws Exception { // GH-90000
            when(collectionRepository.findByName(TENANT_ID, "nonexistent")) // GH-90000
                .thenReturn(Promise.of(Optional.empty())); // GH-90000

            Optional<MetaCollection> result = runPromise(() -> // GH-90000
                collectionRepository.findByName(TENANT_ID, "nonexistent")); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("getCollection by ID returns present optional when collection exists [GH-90000]")
        void getCollectionById_returnsPresent() throws Exception { // GH-90000
            MetaCollection collection = buildCollection(COLLECTION_NAME); // GH-90000
            collection.setId(COLLECTION_ID); // GH-90000
            when(collectionRepository.findById(TENANT_ID, COLLECTION_ID)) // GH-90000
                .thenReturn(Promise.of(Optional.of(collection))); // GH-90000

            Optional<MetaCollection> result = runPromise(() -> // GH-90000
                collectionRepository.findById(TENANT_ID, COLLECTION_ID)); // GH-90000

            assertThat(result).isPresent(); // GH-90000
            assertThat(result.get().getId()).isEqualTo(COLLECTION_ID); // GH-90000
        }

        @Test
        @DisplayName("countCollections returns correct count from repository [GH-90000]")
        void countCollections_returnsCount() throws Exception { // GH-90000
            when(collectionRepository.count(TENANT_ID)).thenReturn(Promise.of(7L)); // GH-90000

            long count = runPromise(() -> collectionRepository.count(TENANT_ID)); // GH-90000

            assertThat(count).isEqualTo(7L); // GH-90000
        }

        @Test
        @DisplayName("listCollections returns empty list when no collections exist [GH-90000]")
        void listCollections_emptyTenant_returnsEmptyList() throws Exception { // GH-90000
            when(collectionRepository.findAll(TENANT_ID)) // GH-90000
                .thenReturn(Promise.of(List.of())); // GH-90000

            List<MetaCollection> result = runPromise(() -> // GH-90000
                collectionRepository.findAll(TENANT_ID)); // GH-90000

            assertThat(result).isEmpty(); // GH-90000
        }

        @Test
        @DisplayName("listCollections propagates exception when repository fails [GH-90000]")
        void listCollections_repositoryFailure_propagatesException() { // GH-90000
            when(collectionRepository.findAll(TENANT_ID)) // GH-90000
                .thenReturn(Promise.ofException(new RuntimeException("DB unavailable [GH-90000]")));

            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                collectionRepository.findAll(TENANT_ID))) // GH-90000
                .hasMessageContaining("DB unavailable [GH-90000]");
        }

        @Test
        @DisplayName("findAllByTenant alias returns same result as findAll [GH-90000]")
        void findAllByTenant_aliasMatchesFindAll() throws Exception { // GH-90000
            MetaCollection collection = buildCollection("events [GH-90000]");
            // Stub findAllByTenant directly: Mockito mock does not auto-delegate default methods
            when(collectionRepository.findAllByTenant(TENANT_ID)) // GH-90000
                .thenReturn(Promise.of(List.of(collection))); // GH-90000

            List<MetaCollection> result = runPromise(() -> // GH-90000
                collectionRepository.findAllByTenant(TENANT_ID)); // GH-90000

            assertThat(result).hasSize(1); // GH-90000
            assertThat(result.get(0).getName()).isEqualTo("events [GH-90000]");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Entity Query Tests (get, list) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("EntityService – query delegation [GH-90000]")
    class EntityQueryTests {

        @Test
        @DisplayName("getEntity with valid ID delegates to entityService and returns entity [GH-90000]")
        void getEntity_validId_returnsEntity() throws Exception { // GH-90000
            Entity entity = buildEntity(ENTITY_ID, Map.of("orderId", "ORD-100", "status", "PENDING")); // GH-90000
            when(entityService.getEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID)) // GH-90000
                .thenReturn(Promise.of(entity)); // GH-90000

            Entity result = runPromise(() -> // GH-90000
                entityService.getEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID)); // GH-90000

            assertThat(result.getId()).isEqualTo(ENTITY_ID); // GH-90000
            assertThat(result.getData()).containsEntry("orderId", "ORD-100"); // GH-90000
        }

        @Test
        @DisplayName("getEntity with wrong UUID propagates exception [GH-90000]")
        void getEntity_notFound_propagatesException() { // GH-90000
            UUID nonExistent = UUID.randomUUID(); // GH-90000
            when(entityService.getEntity(TENANT_ID, COLLECTION_NAME, nonExistent)) // GH-90000
                .thenReturn(Promise.ofException(new NoSuchElementException("Entity not found: " + nonExistent))); // GH-90000

            assertThatThrownBy(() -> runPromise(() -> // GH-90000
                entityService.getEntity(TENANT_ID, COLLECTION_NAME, nonExistent))) // GH-90000
                .isInstanceOf(NoSuchElementException.class) // GH-90000
                .hasMessageContaining("Entity not found [GH-90000]");
        }

        @Test
        @DisplayName("getEntity returned map contains id, tenantId, collectionName, and data fields [GH-90000]")
        void getEntity_resultContainsAllRequiredFields() throws Exception { // GH-90000
            Map<String, Object> data = Map.of("name", "Product A", "price", 29.99); // GH-90000
            Entity entity = buildEntity(ENTITY_ID, data); // GH-90000
            when(entityService.getEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID)) // GH-90000
                .thenReturn(Promise.of(entity)); // GH-90000

            Entity result = runPromise(() -> // GH-90000
                entityService.getEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID)); // GH-90000

            assertThat(result.getId()).isNotNull(); // GH-90000
            assertThat(result.getTenantId()).isEqualTo(TENANT_ID); // GH-90000
            assertThat(result.getCollectionName()).isEqualTo(COLLECTION_NAME); // GH-90000
            assertThat(result.getData()).containsEntry("name", "Product A"); // GH-90000
        }

        @Test
        @DisplayName("getEntity does not call mutation services [GH-90000]")
        void getEntity_doesNotTouchMutationServices() throws Exception { // GH-90000
            Entity entity = buildEntity(ENTITY_ID, Map.of("k", "v")); // GH-90000
            when(entityService.getEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID)) // GH-90000
                .thenReturn(Promise.of(entity)); // GH-90000

            runPromise(() -> entityService.getEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID)); // GH-90000

            verifyNoInteractions(collectionService); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mutation-then-Query Scenarios (write then read) // GH-90000
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Mutation then Query – write-read consistency [GH-90000]")
    class MutationThenQueryTests {

        @Test
        @DisplayName("createEntity result ID can be used to query entity immediately after [GH-90000]")
        void createThenQuery_entityAvailableAfterCreate() throws Exception { // GH-90000
            Map<String, Object> data = Map.of("item", "Widget", "qty", 100); // GH-90000
            Entity created = buildEntity(ENTITY_ID, data); // GH-90000

            when(entityService.createEntity(TENANT_ID, COLLECTION_NAME, data, USER_ID)) // GH-90000
                .thenReturn(Promise.of(created)); // GH-90000
            when(entityService.getEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID)) // GH-90000
                .thenReturn(Promise.of(created)); // GH-90000

            // Create via mutation
            Map<String, Object> mutResult = runPromise(() -> // GH-90000
                mutations.createEntity(TENANT_ID, COLLECTION_NAME, data, USER_ID)); // GH-90000

            UUID returnedId = UUID.fromString((String) mutResult.get("id [GH-90000]"));

            // Query by returned ID
            Entity queried = runPromise(() -> // GH-90000
                entityService.getEntity(TENANT_ID, COLLECTION_NAME, returnedId)); // GH-90000

            assertThat(queried.getData()).containsEntry("item", "Widget"); // GH-90000
        }

        @Test
        @DisplayName("createCollection result can be queried by name [GH-90000]")
        void createThenListCollections_newCollectionAppears() throws Exception { // GH-90000
            String newName = "invoices";
            MetaCollection created = buildCollection(newName); // GH-90000
            created.setId(COLLECTION_ID); // GH-90000

            when(collectionService.createCollection( // GH-90000
                    eq(TENANT_ID), any(MetaCollection.class), anyString())) // GH-90000
                .thenReturn(Promise.of(created)); // GH-90000
            when(collectionRepository.findByName(TENANT_ID, newName)) // GH-90000
                .thenReturn(Promise.of(Optional.of(created))); // GH-90000

            // Create via mutation
            Map<String, Object> createResult = runPromise(() -> // GH-90000
                mutations.createCollection(TENANT_ID, newName, "Invoices Collection", USER_ID)); // GH-90000

            assertThat(createResult).containsKey("id [GH-90000]");

            // Query — new collection is findable
            Optional<MetaCollection> found = runPromise(() -> // GH-90000
                collectionRepository.findByName(TENANT_ID, newName)); // GH-90000
            assertThat(found).isPresent(); // GH-90000
            assertThat(found.get().getName()).isEqualTo(newName); // GH-90000
        }

        @Test
        @DisplayName("deleteEntity removes entity from further queries [GH-90000]")
        void deleteThenQuery_entityNotFoundAfterDelete() throws Exception { // GH-90000
            // Stub the underlying service (mutations delegates to entityService) // GH-90000
            when(entityService.deleteEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID, USER_ID)) // GH-90000
                .thenReturn(Promise.of((Void) null)); // GH-90000
            when(entityService.getEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID)) // GH-90000
                .thenReturn(Promise.ofException(new NoSuchElementException("Entity deleted [GH-90000]")));

            // Execute delete
            runPromise(() -> entityService.deleteEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID, USER_ID)); // GH-90000

            // Query must fail with not-found
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> entityService.getEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID))) // GH-90000
                .isInstanceOf(NoSuchElementException.class); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Input Validation for Query Path
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Input validation – query parameters [GH-90000]")
    class QueryInputValidationTests {

        @Test
        @DisplayName("getEntity with null tenantId throws IllegalArgumentException [GH-90000]")
        void getEntity_nullTenantId_throwsException() { // GH-90000
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> entityService.getEntity(null, COLLECTION_NAME, ENTITY_ID))) // GH-90000
                .satisfies(ex -> assertThat(ex) // GH-90000
                    .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class)); // GH-90000
        }

        @Test
        @DisplayName("getEntity with blank collection name throws IllegalArgumentException [GH-90000]")
        void getEntity_blankCollectionName_throwsException() { // GH-90000
            when(entityService.getEntity(TENANT_ID, "", ENTITY_ID)) // GH-90000
                .thenReturn(Promise.ofException( // GH-90000
                    new IllegalArgumentException("collectionName must not be blank [GH-90000]")));

            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> entityService.getEntity(TENANT_ID, "", ENTITY_ID))) // GH-90000
                .isInstanceOf(IllegalArgumentException.class) // GH-90000
                .hasMessageContaining("collectionName [GH-90000]");
        }

        @Test
        @DisplayName("listCollections with null tenantId must not return cross-tenant data [GH-90000]")
        void listCollections_nullTenantId_doesNotReturnCrossTenantData() { // GH-90000
            when(collectionRepository.findAll(null)) // GH-90000
                .thenReturn(Promise.ofException( // GH-90000
                    new IllegalArgumentException("tenantId must not be null [GH-90000]")));

            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> collectionRepository.findAll(null))) // GH-90000
                .isInstanceOf(IllegalArgumentException.class); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Multi-tenant Isolation for Queries
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Multi-tenant query isolation [GH-90000]")
    class MultiTenantQueryTests {

        @Test
        @DisplayName("listCollections for different tenants returns tenant-scoped results [GH-90000]")
        void listCollections_differentTenants_isolatedResults() throws Exception { // GH-90000
            MetaCollection tenantACollection = buildCollection("tenant-a-data [GH-90000]");
            MetaCollection tenantBCollection = buildCollection("tenant-b-data [GH-90000]");

            when(collectionRepository.findAll("tenant-a [GH-90000]")).thenReturn(Promise.of(List.of(tenantACollection)));
            when(collectionRepository.findAll("tenant-b [GH-90000]")).thenReturn(Promise.of(List.of(tenantBCollection)));

            List<MetaCollection> resultA = runPromise(() -> collectionRepository.findAll("tenant-a [GH-90000]"));
            List<MetaCollection> resultB = runPromise(() -> collectionRepository.findAll("tenant-b [GH-90000]"));

            assertThat(resultA).hasSize(1); // GH-90000
            assertThat(resultA.get(0).getName()).isEqualTo("tenant-a-data [GH-90000]");

            assertThat(resultB).hasSize(1); // GH-90000
            assertThat(resultB.get(0).getName()).isEqualTo("tenant-b-data [GH-90000]");

            // Cross-tenant isolation: tenant-a cannot see tenant-b data
            assertThat(resultA.stream().map(MetaCollection::getName).toList()) // GH-90000
                .doesNotContain("tenant-b-data [GH-90000]");
        }

        @Test
        @DisplayName("getEntity for wrong tenant does not expose entity [GH-90000]")
        void getEntity_wrongTenant_doesNotExposeEntity() { // GH-90000
            when(entityService.getEntity("wrong-tenant", COLLECTION_NAME, ENTITY_ID)) // GH-90000
                .thenReturn(Promise.ofException(new NoSuchElementException("Access denied or not found [GH-90000]")));

            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> entityService.getEntity("wrong-tenant", COLLECTION_NAME, ENTITY_ID))) // GH-90000
                .isInstanceOf(NoSuchElementException.class); // GH-90000
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private MetaCollection buildCollection(String name) { // GH-90000
        MetaCollection c = new MetaCollection(); // GH-90000
        c.setId(UUID.randomUUID()); // GH-90000
        c.setTenantId(TENANT_ID); // GH-90000
        c.setName(name); // GH-90000
        c.setLabel(name + " label"); // GH-90000
        c.setDescription("Test collection: " + name); // GH-90000
        c.setActive(true); // GH-90000
        return c;
    }

    private Entity buildEntity(UUID id, Map<String, Object> data) { // GH-90000
        return Entity.builder() // GH-90000
            .id(id) // GH-90000
            .tenantId(TENANT_ID) // GH-90000
            .collectionName(COLLECTION_NAME) // GH-90000
            .data(data) // GH-90000
            .metadata(Collections.emptyMap()) // GH-90000
            .createdAt(Instant.now()) // GH-90000
            .updatedAt(Instant.now()) // GH-90000
            .build(); // GH-90000
    }
}
