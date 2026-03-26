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
 * Unit tests for GraphQLMutations.
 *
 * Tests validate:
 * - Input validation (null checks, empty strings, UUID parsing)
 * - Entity CRUD operations (create, update, delete)
 * - Collection CRUD operations (create, update, delete)
 * - Proper delegation to services
 * - Error handling for invalid inputs
 * - Map conversion for GraphQL responses
 *
 * CRITICAL: Extends EventloopTestBase for ActiveJ Promise testing.
 *
 * @see GraphQLMutations
 * @see EventloopTestBase
 * @doc.type class
 * @doc.purpose Unit tests for GraphQL mutations
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("GraphQLMutations Tests")
@ExtendWith(MockitoExtension.class)
class GraphQLMutationsTest extends EventloopTestBase {

    @Mock
    private EntityService entityService;

    @Mock
    private CollectionService collectionService;

    @Mock
    private CollectionRepository collectionRepository;

    private GraphQLMutations mutations;

    private static final String TENANT_ID = "tenant-123";
    private static final String USER_ID = "user-456";
    private static final String COLLECTION_NAME = "orders";
    private static final UUID ENTITY_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final UUID COLLECTION_ID = UUID.fromString("661f9511-f40c-52e5-b827-557766551111");

    @BeforeEach
    void setup() {
        mutations = new GraphQLMutations(entityService, collectionService, collectionRepository);
    }

    // ==================== Entity CRUD Tests ====================

    @Nested
    @DisplayName("createEntity Tests")
    class CreateEntityTests {

        @Test
        @DisplayName("should create entity with valid inputs")
        void shouldCreateEntity() {
            // GIVEN
            Map<String, Object> inputData = Map.of(
                "orderId", "ORD-001",
                "amount", 99.99,
                "status", "pending"
            );

            Entity mockEntity = Entity.builder()
                .id(ENTITY_ID)
                .tenantId(TENANT_ID)
                .collectionName(COLLECTION_NAME)
                .data(inputData)
                .metadata(Collections.emptyMap())
                .createdAt(Instant.now())
                .version(1)
                .build();

            when(entityService.createEntity(TENANT_ID, COLLECTION_NAME, inputData, USER_ID))
                .thenReturn(Promise.of(mockEntity));

            // WHEN
            Map<String, Object> result = runPromise(() -> 
                mutations.createEntity(TENANT_ID, COLLECTION_NAME, inputData, USER_ID));

            // THEN
            assertThat(result).containsEntry("id", ENTITY_ID.toString());
            assertThat(result).containsEntry("tenantId", TENANT_ID);
            assertThat(result).containsEntry("collectionName", COLLECTION_NAME);
            assertThat(result).containsEntry("data", inputData);
            assertThat(result).containsEntry("version", 1);

            verify(entityService).createEntity(TENANT_ID, COLLECTION_NAME, inputData, USER_ID);
        }

        @Test
        @DisplayName("should reject null tenantId")
        void shouldRejectNullTenantId() {
            // GIVEN
            Map<String, Object> data = Map.of("key", "value");

            // WHEN/THEN
            assertThatThrownBy(() -> 
                runPromise(() -> mutations.createEntity(null, COLLECTION_NAME, data, USER_ID))
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("tenantId is required");
        }

        @Test
        @DisplayName("should reject empty tenantId")
        void shouldRejectEmptyTenantId() {
            // GIVEN
            Map<String, Object> data = Map.of("key", "value");

            // WHEN/THEN
            assertThatThrownBy(() -> 
                runPromise(() -> mutations.createEntity("  ", COLLECTION_NAME, data, USER_ID))
            ).hasMessageContaining("tenantId cannot be empty");
        }

        @Test
        @DisplayName("should reject null collectionName")
        void shouldRejectNullCollectionName() {
            // GIVEN
            Map<String, Object> data = Map.of("key", "value");

            // WHEN/THEN
            assertThatThrownBy(() -> 
                runPromise(() -> mutations.createEntity(TENANT_ID, null, data, USER_ID))
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("collectionName is required");
        }

        @Test
        @DisplayName("should reject empty data")
        void shouldRejectEmptyData() {
            // WHEN/THEN
            assertThatThrownBy(() -> 
                runPromise(() -> mutations.createEntity(TENANT_ID, COLLECTION_NAME, Collections.emptyMap(), USER_ID))
            ).hasMessageContaining("data cannot be empty");
        }

        @Test
        @DisplayName("should reject null userId")
        void shouldRejectNullUserId() {
            // GIVEN
            Map<String, Object> data = Map.of("key", "value");

            // WHEN/THEN
            assertThatThrownBy(() -> 
                runPromise(() -> mutations.createEntity(TENANT_ID, COLLECTION_NAME, data, null))
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("userId is required");
        }
    }

    @Nested
    @DisplayName("updateEntity Tests")
    class UpdateEntityTests {

        @Test
        @DisplayName("should update entity with valid inputs")
        void shouldUpdateEntity() {
            // GIVEN
            Map<String, Object> updateData = Map.of("status", "completed", "completedAt", Instant.now().toString());

            Entity updatedEntity = Entity.builder()
                .id(ENTITY_ID)
                .tenantId(TENANT_ID)
                .collectionName(COLLECTION_NAME)
                .data(updateData)
                .metadata(Collections.emptyMap())
                .createdAt(Instant.now().minusSeconds(3600))
                .updatedAt(Instant.now())
                .version(2)
                .build();

            when(entityService.updateEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID, updateData, USER_ID))
                .thenReturn(Promise.of(updatedEntity));

            // WHEN
            Map<String, Object> result = runPromise(() -> 
                mutations.updateEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID.toString(), updateData, USER_ID));

            // THEN
            assertThat(result).containsEntry("id", ENTITY_ID.toString());
            assertThat(result).containsEntry("version", 2);

            verify(entityService).updateEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID, updateData, USER_ID);
        }

        @Test
        @DisplayName("should reject invalid UUID format")
        void shouldRejectInvalidUuid() {
            // GIVEN
            Map<String, Object> data = Map.of("key", "value");

            // WHEN/THEN
            assertThatThrownBy(() -> 
                runPromise(() -> mutations.updateEntity(TENANT_ID, COLLECTION_NAME, "invalid-uuid", data, USER_ID))
            ).hasMessageContaining("Invalid entity ID format");
        }
    }

    @Nested
    @DisplayName("deleteEntity Tests")
    class DeleteEntityTests {

        @Test
        @DisplayName("should delete entity with valid UUID")
        void shouldDeleteEntity() {
            // GIVEN
            when(entityService.deleteEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID, USER_ID))
                .thenReturn(Promise.of(null));

            // WHEN
            Boolean result = runPromise(() -> 
                mutations.deleteEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID.toString(), USER_ID));

            // THEN
            assertThat(result).isTrue();
            verify(entityService).deleteEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID, USER_ID);
        }

        @Test
        @DisplayName("should reject invalid UUID")
        void shouldRejectInvalidUuid() {
            // WHEN/THEN
            assertThatThrownBy(() -> 
                runPromise(() -> mutations.deleteEntity(TENANT_ID, COLLECTION_NAME, "bad-uuid", USER_ID))
            ).hasMessageContaining("Invalid entity ID format");
        }
    }

    // ==================== Collection CRUD Tests ====================

    @Nested
    @DisplayName("createCollection Tests")
    class CreateCollectionTests {

        @Test
        @DisplayName("should create collection with valid inputs")
        void shouldCreateCollection() {
            // GIVEN
            String name = "products";
            String description = "Product catalog";

            MetaCollection created = MetaCollection.builder()
                .id(COLLECTION_ID)
                .tenantId(TENANT_ID)
                .name(name)
                .label(name)
                .description(description)
                .fields(Collections.emptyList())
                .permission(Collections.emptyMap())
                .build();
            created.setCreatedAt(Instant.now());

            when(collectionService.createCollection(eq(TENANT_ID), any(MetaCollection.class), eq(USER_ID)))
                .thenReturn(Promise.of(created));

            // WHEN
            Map<String, Object> result = runPromise(() -> 
                mutations.createCollection(TENANT_ID, name, description, USER_ID));

            // THEN
            assertThat(result).containsEntry("id", COLLECTION_ID.toString());
            assertThat(result).containsEntry("name", name);
            assertThat(result).containsEntry("description", description);
            assertThat(result).containsEntry("tenantId", TENANT_ID);

            verify(collectionService).createCollection(eq(TENANT_ID), any(MetaCollection.class), eq(USER_ID));
        }

        @Test
        @DisplayName("should reject null name")
        void shouldRejectNullName() {
            // WHEN/THEN
            assertThatThrownBy(() -> 
                runPromise(() -> mutations.createCollection(TENANT_ID, null, "desc", USER_ID))
            ).isInstanceOf(NullPointerException.class)
             .hasMessageContaining("name is required");
        }

        @Test
        @DisplayName("should reject empty name")
        void shouldRejectEmptyName() {
            // WHEN/THEN
            assertThatThrownBy(() -> 
                runPromise(() -> mutations.createCollection(TENANT_ID, "  ", "desc", USER_ID))
            ).hasMessageContaining("name cannot be empty");
        }
    }

    @Nested
    @DisplayName("updateCollection Tests")
    class UpdateCollectionTests {

        @Test
        @DisplayName("should update collection description")
        void shouldUpdateCollection() {
            // GIVEN
            String name = "products";
            String newDescription = "Updated product catalog";

            MetaCollection existing = MetaCollection.builder()
                .id(COLLECTION_ID)
                .tenantId(TENANT_ID)
                .name(name)
                .description("Old description")
                .fields(Collections.emptyList())
                .build();

            MetaCollection updated = MetaCollection.builder()
                .id(COLLECTION_ID)
                .tenantId(TENANT_ID)
                .name(name)
                .description(newDescription)
                .fields(Collections.emptyList())
                .build();
            updated.setUpdatedAt(Instant.now());

            when(collectionRepository.findByName(TENANT_ID, name))
                .thenReturn(Promise.of(Optional.of(existing)));
            when(collectionService.updateCollection(eq(TENANT_ID), any(MetaCollection.class), eq(USER_ID)))
                .thenReturn(Promise.of(updated));

            // WHEN
            Map<String, Object> result = runPromise(() -> 
                mutations.updateCollection(TENANT_ID, name, newDescription, USER_ID));

            // THEN
            assertThat(result).containsEntry("description", newDescription);
            assertThat(result).containsEntry("name", name);

            verify(collectionRepository).findByName(TENANT_ID, name);
            verify(collectionService).updateCollection(eq(TENANT_ID), any(MetaCollection.class), eq(USER_ID));
        }

        @Test
        @DisplayName("should reject update for non-existent collection")
        void shouldRejectNonExistentCollection() {
            // GIVEN
            when(collectionRepository.findByName(TENANT_ID, "nonexistent"))
                .thenReturn(Promise.of(Optional.empty()));

            // WHEN/THEN
            assertThatThrownBy(() -> 
                runPromise(() -> mutations.updateCollection(TENANT_ID, "nonexistent", "desc", USER_ID))
            ).isInstanceOf(IllegalStateException.class)
             .hasMessageContaining("Collection not found");

            clearFatalError();
        }
    }

    @Nested
    @DisplayName("deleteCollection Tests")
    class DeleteCollectionTests {

        @Test
        @DisplayName("should delete collection by name")
        void shouldDeleteCollection() {
            // GIVEN
            String name = "old-collection";

            MetaCollection existing = MetaCollection.builder()
                .id(COLLECTION_ID)
                .tenantId(TENANT_ID)
                .name(name)
                .build();

            when(collectionRepository.findByName(TENANT_ID, name))
                .thenReturn(Promise.of(Optional.of(existing)));
            when(collectionService.deleteCollection(TENANT_ID, COLLECTION_ID, USER_ID))
                .thenReturn(Promise.of(null));

            // WHEN
            Boolean result = runPromise(() -> 
                mutations.deleteCollection(TENANT_ID, name, USER_ID));

            // THEN
            assertThat(result).isTrue();
            verify(collectionRepository).findByName(TENANT_ID, name);
            verify(collectionService).deleteCollection(TENANT_ID, COLLECTION_ID, USER_ID);
        }

        @Test
        @DisplayName("should reject delete for non-existent collection")
        void shouldRejectNonExistentCollection() {
            // GIVEN
            when(collectionRepository.findByName(TENANT_ID, "nonexistent"))
                .thenReturn(Promise.of(Optional.empty()));

            // WHEN/THEN
            assertThatThrownBy(() -> 
                runPromise(() -> mutations.deleteCollection(TENANT_ID, "nonexistent", USER_ID))
            ).isInstanceOf(IllegalStateException.class)
             .hasMessageContaining("Collection not found");

            clearFatalError();
        }
    }
}
