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
 * - Input validation (null checks, empty strings, UUID parsing) // GH-90000
 * - Entity CRUD operations (create, update, delete) // GH-90000
 * - Collection CRUD operations (create, update, delete) // GH-90000
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
@DisplayName("GraphQLMutations Tests [GH-90000]")
@ExtendWith(MockitoExtension.class) // GH-90000
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
    private static final UUID ENTITY_ID = UUID.fromString("550e8400-e29b-41d4-a716-446655440000 [GH-90000]");
    private static final UUID COLLECTION_ID = UUID.fromString("661f9511-f40c-52e5-b827-557766551111 [GH-90000]");

    @BeforeEach
    void setup() { // GH-90000
        mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
    }

    // ==================== Entity CRUD Tests ====================

    @Nested
    @DisplayName("createEntity Tests [GH-90000]")
    class CreateEntityTests {

        @Test
        @DisplayName("should create entity with valid inputs [GH-90000]")
        void shouldCreateEntity() { // GH-90000
            // GIVEN
            Map<String, Object> inputData = Map.of( // GH-90000
                "orderId", "ORD-001",
                "amount", 99.99,
                "status", "pending"
            );

            Entity mockEntity = Entity.builder() // GH-90000
                .id(ENTITY_ID) // GH-90000
                .tenantId(TENANT_ID) // GH-90000
                .collectionName(COLLECTION_NAME) // GH-90000
                .data(inputData) // GH-90000
                .metadata(Collections.emptyMap()) // GH-90000
                .createdAt(Instant.now()) // GH-90000
                .version(1) // GH-90000
                .build(); // GH-90000

            when(entityService.createEntity(TENANT_ID, COLLECTION_NAME, inputData, USER_ID)) // GH-90000
                .thenReturn(Promise.of(mockEntity)); // GH-90000

            // WHEN
            Map<String, Object> result = runPromise(() -> // GH-90000
                mutations.createEntity(TENANT_ID, COLLECTION_NAME, inputData, USER_ID)); // GH-90000

            // THEN
            assertThat(result).containsEntry("id", ENTITY_ID.toString()); // GH-90000
            assertThat(result).containsEntry("tenantId", TENANT_ID); // GH-90000
            assertThat(result).containsEntry("collectionName", COLLECTION_NAME); // GH-90000
            assertThat(result).containsEntry("data", inputData); // GH-90000
            assertThat(result).containsEntry("version", 1); // GH-90000

            verify(entityService).createEntity(TENANT_ID, COLLECTION_NAME, inputData, USER_ID); // GH-90000
        }

        @Test
        @DisplayName("should reject null tenantId [GH-90000]")
        void shouldRejectNullTenantId() { // GH-90000
            // GIVEN
            Map<String, Object> data = Map.of("key", "value"); // GH-90000

            // WHEN/THEN
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> mutations.createEntity(null, COLLECTION_NAME, data, USER_ID)) // GH-90000
            ).isInstanceOf(NullPointerException.class) // GH-90000
             .hasMessageContaining("tenantId is required [GH-90000]");
        }

        @Test
        @DisplayName("should reject empty tenantId [GH-90000]")
        void shouldRejectEmptyTenantId() { // GH-90000
            // GIVEN
            Map<String, Object> data = Map.of("key", "value"); // GH-90000

            // WHEN/THEN
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> mutations.createEntity("  ", COLLECTION_NAME, data, USER_ID)) // GH-90000
            ).hasMessageContaining("tenantId cannot be empty [GH-90000]");
        }

        @Test
        @DisplayName("should reject null collectionName [GH-90000]")
        void shouldRejectNullCollectionName() { // GH-90000
            // GIVEN
            Map<String, Object> data = Map.of("key", "value"); // GH-90000

            // WHEN/THEN
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> mutations.createEntity(TENANT_ID, null, data, USER_ID)) // GH-90000
            ).isInstanceOf(NullPointerException.class) // GH-90000
             .hasMessageContaining("collectionName is required [GH-90000]");
        }

        @Test
        @DisplayName("should reject empty data [GH-90000]")
        void shouldRejectEmptyData() { // GH-90000
            // WHEN/THEN
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> mutations.createEntity(TENANT_ID, COLLECTION_NAME, Collections.emptyMap(), USER_ID)) // GH-90000
            ).hasMessageContaining("data cannot be empty [GH-90000]");
        }

        @Test
        @DisplayName("should reject null userId [GH-90000]")
        void shouldRejectNullUserId() { // GH-90000
            // GIVEN
            Map<String, Object> data = Map.of("key", "value"); // GH-90000

            // WHEN/THEN
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> mutations.createEntity(TENANT_ID, COLLECTION_NAME, data, null)) // GH-90000
            ).isInstanceOf(NullPointerException.class) // GH-90000
             .hasMessageContaining("userId is required [GH-90000]");
        }
    }

    @Nested
    @DisplayName("updateEntity Tests [GH-90000]")
    class UpdateEntityTests {

        @Test
        @DisplayName("should update entity with valid inputs [GH-90000]")
        void shouldUpdateEntity() { // GH-90000
            // GIVEN
            Map<String, Object> updateData = Map.of("status", "completed", "completedAt", Instant.now().toString()); // GH-90000

            Entity updatedEntity = Entity.builder() // GH-90000
                .id(ENTITY_ID) // GH-90000
                .tenantId(TENANT_ID) // GH-90000
                .collectionName(COLLECTION_NAME) // GH-90000
                .data(updateData) // GH-90000
                .metadata(Collections.emptyMap()) // GH-90000
                .createdAt(Instant.now().minusSeconds(3600)) // GH-90000
                .updatedAt(Instant.now()) // GH-90000
                .version(2) // GH-90000
                .build(); // GH-90000

            when(entityService.updateEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID, updateData, USER_ID)) // GH-90000
                .thenReturn(Promise.of(updatedEntity)); // GH-90000

            // WHEN
            Map<String, Object> result = runPromise(() -> // GH-90000
                mutations.updateEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID.toString(), updateData, USER_ID)); // GH-90000

            // THEN
            assertThat(result).containsEntry("id", ENTITY_ID.toString()); // GH-90000
            assertThat(result).containsEntry("version", 2); // GH-90000

            verify(entityService).updateEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID, updateData, USER_ID); // GH-90000
        }

        @Test
        @DisplayName("should reject invalid UUID format [GH-90000]")
        void shouldRejectInvalidUuid() { // GH-90000
            // GIVEN
            Map<String, Object> data = Map.of("key", "value"); // GH-90000

            // WHEN/THEN
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> mutations.updateEntity(TENANT_ID, COLLECTION_NAME, "invalid-uuid", data, USER_ID)) // GH-90000
            ).hasMessageContaining("Invalid entity ID format [GH-90000]");
        }
    }

    @Nested
    @DisplayName("deleteEntity Tests [GH-90000]")
    class DeleteEntityTests {

        @Test
        @DisplayName("should delete entity with valid UUID [GH-90000]")
        void shouldDeleteEntity() { // GH-90000
            // GIVEN
            when(entityService.deleteEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID, USER_ID)) // GH-90000
                .thenReturn(Promise.of(null)); // GH-90000

            // WHEN
            Boolean result = runPromise(() -> // GH-90000
                mutations.deleteEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID.toString(), USER_ID)); // GH-90000

            // THEN
            assertThat(result).isTrue(); // GH-90000
            verify(entityService).deleteEntity(TENANT_ID, COLLECTION_NAME, ENTITY_ID, USER_ID); // GH-90000
        }

        @Test
        @DisplayName("should reject invalid UUID [GH-90000]")
        void shouldRejectInvalidUuid() { // GH-90000
            // WHEN/THEN
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> mutations.deleteEntity(TENANT_ID, COLLECTION_NAME, "bad-uuid", USER_ID)) // GH-90000
            ).hasMessageContaining("Invalid entity ID format [GH-90000]");
        }
    }

    // ==================== Collection CRUD Tests ====================

    @Nested
    @DisplayName("createCollection Tests [GH-90000]")
    class CreateCollectionTests {

        @Test
        @DisplayName("should create collection with valid inputs [GH-90000]")
        void shouldCreateCollection() { // GH-90000
            // GIVEN
            String name = "products";
            String description = "Product catalog";

            MetaCollection created = MetaCollection.builder() // GH-90000
                .id(COLLECTION_ID) // GH-90000
                .tenantId(TENANT_ID) // GH-90000
                .name(name) // GH-90000
                .label(name) // GH-90000
                .description(description) // GH-90000
                .fields(Collections.emptyList()) // GH-90000
                .permission(Collections.emptyMap()) // GH-90000
                .build(); // GH-90000
            created.setCreatedAt(Instant.now()); // GH-90000

            when(collectionService.createCollection(eq(TENANT_ID), any(MetaCollection.class), eq(USER_ID))) // GH-90000
                .thenReturn(Promise.of(created)); // GH-90000

            // WHEN
            Map<String, Object> result = runPromise(() -> // GH-90000
                mutations.createCollection(TENANT_ID, name, description, USER_ID)); // GH-90000

            // THEN
            assertThat(result).containsEntry("id", COLLECTION_ID.toString()); // GH-90000
            assertThat(result).containsEntry("name", name); // GH-90000
            assertThat(result).containsEntry("description", description); // GH-90000
            assertThat(result).containsEntry("tenantId", TENANT_ID); // GH-90000

            verify(collectionService).createCollection(eq(TENANT_ID), any(MetaCollection.class), eq(USER_ID)); // GH-90000
        }

        @Test
        @DisplayName("should reject null name [GH-90000]")
        void shouldRejectNullName() { // GH-90000
            // WHEN/THEN
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> mutations.createCollection(TENANT_ID, null, "desc", USER_ID)) // GH-90000
            ).isInstanceOf(NullPointerException.class) // GH-90000
             .hasMessageContaining("name is required [GH-90000]");
        }

        @Test
        @DisplayName("should reject empty name [GH-90000]")
        void shouldRejectEmptyName() { // GH-90000
            // WHEN/THEN
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> mutations.createCollection(TENANT_ID, "  ", "desc", USER_ID)) // GH-90000
            ).hasMessageContaining("name cannot be empty [GH-90000]");
        }
    }

    @Nested
    @DisplayName("updateCollection Tests [GH-90000]")
    class UpdateCollectionTests {

        @Test
        @DisplayName("should update collection description [GH-90000]")
        void shouldUpdateCollection() { // GH-90000
            // GIVEN
            String name = "products";
            String newDescription = "Updated product catalog";

            MetaCollection existing = MetaCollection.builder() // GH-90000
                .id(COLLECTION_ID) // GH-90000
                .tenantId(TENANT_ID) // GH-90000
                .name(name) // GH-90000
                .description("Old description [GH-90000]")
                .fields(Collections.emptyList()) // GH-90000
                .build(); // GH-90000

            MetaCollection updated = MetaCollection.builder() // GH-90000
                .id(COLLECTION_ID) // GH-90000
                .tenantId(TENANT_ID) // GH-90000
                .name(name) // GH-90000
                .description(newDescription) // GH-90000
                .fields(Collections.emptyList()) // GH-90000
                .build(); // GH-90000
            updated.setUpdatedAt(Instant.now()); // GH-90000

            when(collectionRepository.findByName(TENANT_ID, name)) // GH-90000
                .thenReturn(Promise.of(Optional.of(existing))); // GH-90000
            when(collectionService.updateCollection(eq(TENANT_ID), any(MetaCollection.class), eq(USER_ID))) // GH-90000
                .thenReturn(Promise.of(updated)); // GH-90000

            // WHEN
            Map<String, Object> result = runPromise(() -> // GH-90000
                mutations.updateCollection(TENANT_ID, name, newDescription, USER_ID)); // GH-90000

            // THEN
            assertThat(result).containsEntry("description", newDescription); // GH-90000
            assertThat(result).containsEntry("name", name); // GH-90000

            verify(collectionRepository).findByName(TENANT_ID, name); // GH-90000
            verify(collectionService).updateCollection(eq(TENANT_ID), any(MetaCollection.class), eq(USER_ID)); // GH-90000
        }

        @Test
        @DisplayName("should reject update for non-existent collection [GH-90000]")
        void shouldRejectNonExistentCollection() { // GH-90000
            // GIVEN
            when(collectionRepository.findByName(TENANT_ID, "nonexistent")) // GH-90000
                .thenReturn(Promise.of(Optional.empty())); // GH-90000

            // WHEN/THEN
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> mutations.updateCollection(TENANT_ID, "nonexistent", "desc", USER_ID)) // GH-90000
            ).isInstanceOf(IllegalStateException.class) // GH-90000
             .hasMessageContaining("Collection not found [GH-90000]");

            clearFatalError(); // GH-90000
        }
    }

    @Nested
    @DisplayName("deleteCollection Tests [GH-90000]")
    class DeleteCollectionTests {

        @Test
        @DisplayName("should delete collection by name [GH-90000]")
        void shouldDeleteCollection() { // GH-90000
            // GIVEN
            String name = "old-collection";

            MetaCollection existing = MetaCollection.builder() // GH-90000
                .id(COLLECTION_ID) // GH-90000
                .tenantId(TENANT_ID) // GH-90000
                .name(name) // GH-90000
                .build(); // GH-90000

            when(collectionRepository.findByName(TENANT_ID, name)) // GH-90000
                .thenReturn(Promise.of(Optional.of(existing))); // GH-90000
            when(collectionService.deleteCollection(TENANT_ID, COLLECTION_ID, USER_ID)) // GH-90000
                .thenReturn(Promise.of(null)); // GH-90000

            // WHEN
            Boolean result = runPromise(() -> // GH-90000
                mutations.deleteCollection(TENANT_ID, name, USER_ID)); // GH-90000

            // THEN
            assertThat(result).isTrue(); // GH-90000
            verify(collectionRepository).findByName(TENANT_ID, name); // GH-90000
            verify(collectionService).deleteCollection(TENANT_ID, COLLECTION_ID, USER_ID); // GH-90000
        }

        @Test
        @DisplayName("should reject delete for non-existent collection [GH-90000]")
        void shouldRejectNonExistentCollection() { // GH-90000
            // GIVEN
            when(collectionRepository.findByName(TENANT_ID, "nonexistent")) // GH-90000
                .thenReturn(Promise.of(Optional.empty())); // GH-90000

            // WHEN/THEN
            assertThatThrownBy(() -> // GH-90000
                runPromise(() -> mutations.deleteCollection(TENANT_ID, "nonexistent", USER_ID)) // GH-90000
            ).isInstanceOf(IllegalStateException.class) // GH-90000
             .hasMessageContaining("Collection not found [GH-90000]");

            clearFatalError(); // GH-90000
        }
    }
}
