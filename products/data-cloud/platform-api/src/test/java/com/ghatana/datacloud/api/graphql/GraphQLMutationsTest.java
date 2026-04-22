/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
 * All rights reserved.
 */
package com.ghatana.datacloud.api.graphql;

import com.ghatana.datacloud.application.CollectionService;
import com.ghatana.datacloud.application.EntityService;
import com.ghatana.datacloud.entity.CollectionRepository;
import com.ghatana.datacloud.entity.Entity;
import com.ghatana.datacloud.entity.MetaCollection;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for GraphQL mutation resolvers.
 *
 * <p>Tests mutation operations for entities and collections:
 * <ul>
 *   <li>Entity CRUD operations (create, update, delete)</li> // GH-90000
 *   <li>Collection CRUD operations (create, update, delete)</li> // GH-90000
 *   <li>Input validation</li>
 *   <li>UUID parsing and error handling</li>
 *   <li>Response mapping</li>
 * </ul>
 *
 * @doc.type test
 * @doc.purpose GraphQL mutation validation
 * @doc.layer api
 * @doc.pattern Test
 */
@ExtendWith(MockitoExtension.class) // GH-90000
@DisplayName("GraphQL Mutations Tests [GH-90000]")
class GraphQLMutationsTest extends EventloopTestBase {

    @Mock
    private EntityService entityService;

    @Mock
    private CollectionService collectionService;

    @Mock
    private CollectionRepository collectionRepository;

    // =========================================================================
    // ENTITY CRUD OPERATIONS
    // =========================================================================

    @Nested
    @DisplayName("Entity CRUD operations [GH-90000]")
    class EntityCrudOperations {

        @Test
        @DisplayName("should create entity successfully [GH-90000]")
        void shouldCreateEntitySuccessfully() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            Entity entity = Entity.builder() // GH-90000
                .id(UUID.randomUUID()) // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .data(Map.of("name", "Product 1", "price", 100)) // GH-90000
                .version(1) // GH-90000
                .build(); // GH-90000
            
            when(entityService.createEntity("tenant-1", "products", Map.of("name", "Product 1"), "user-1")) // GH-90000
                .thenReturn(Promise.of(entity)); // GH-90000
            
            Map<String, Object> result = runPromise(() ->  // GH-90000
                mutations.createEntity("tenant-1", "products", Map.of("name", "Product 1"), "user-1") // GH-90000
            );
            
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.get("tenantId [GH-90000]")).isEqualTo("tenant-1 [GH-90000]");
            assertThat(result.get("collectionName [GH-90000]")).isEqualTo("products [GH-90000]");
        }

        @Test
        @DisplayName("should update entity successfully [GH-90000]")
        void shouldUpdateEntitySuccessfully() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            UUID entityId = UUID.randomUUID(); // GH-90000
            Entity entity = Entity.builder() // GH-90000
                .id(entityId) // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .data(Map.of("name", "Updated Product", "price", 150)) // GH-90000
                .version(2) // GH-90000
                .build(); // GH-90000
            
            when(entityService.updateEntity("tenant-1", "products", entityId, Map.of("price", 150), "user-1")) // GH-90000
                .thenReturn(Promise.of(entity)); // GH-90000
            
            Map<String, Object> result = runPromise(() ->  // GH-90000
                mutations.updateEntity("tenant-1", "products", entityId.toString(), Map.of("price", 150), "user-1") // GH-90000
            );
            
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.get("id [GH-90000]")).isEqualTo(entityId.toString());
            assertThat(result.get("version [GH-90000]")).isEqualTo(2);
        }

        @Test
        @DisplayName("should delete entity successfully [GH-90000]")
        void shouldDeleteEntitySuccessfully() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            UUID entityId = UUID.randomUUID(); // GH-90000
            when(entityService.deleteEntity("tenant-1", "products", entityId, "user-1")) // GH-90000
                .thenReturn(Promise.complete()); // GH-90000
            
            Boolean result = runPromise(() ->  // GH-90000
                mutations.deleteEntity("tenant-1", "products", entityId.toString(), "user-1") // GH-90000
            );
            
            assertThat(result).isTrue(); // GH-90000
        }
    }

    // =========================================================================
    // COLLECTION CRUD OPERATIONS
    // =========================================================================

    @Nested
    @DisplayName("Collection CRUD operations [GH-90000]")
    class CollectionCrudOperations {

        @Test
        @DisplayName("should create collection successfully [GH-90000]")
        void shouldCreateCollectionSuccessfully() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            MetaCollection collection = MetaCollection.builder() // GH-90000
                .id(UUID.randomUUID()) // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("products [GH-90000]")
                .label("products [GH-90000]")
                .description("Product collection [GH-90000]")
                .fields(java.util.Collections.emptyList()) // GH-90000
                .permission(java.util.Collections.emptyMap()) // GH-90000
                .build(); // GH-90000
            
            when(collectionService.createCollection(eq("tenant-1 [GH-90000]"), any(MetaCollection.class), eq("user-1 [GH-90000]")))
                .thenReturn(Promise.of(collection)); // GH-90000
            
            Map<String, Object> result = runPromise(() ->  // GH-90000
                mutations.createCollection("tenant-1", "products", "Product collection", "user-1") // GH-90000
            );
            
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.get("tenantId [GH-90000]")).isEqualTo("tenant-1 [GH-90000]");
            assertThat(result.get("name [GH-90000]")).isEqualTo("products [GH-90000]");
        }

        @Test
        @DisplayName("should update collection successfully [GH-90000]")
        void shouldUpdateCollectionSuccessfully() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            MetaCollection existing = MetaCollection.builder() // GH-90000
                .id(UUID.randomUUID()) // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("products [GH-90000]")
                .label("products [GH-90000]")
                .description("Old description [GH-90000]")
                .fields(java.util.Collections.emptyList()) // GH-90000
                .permission(java.util.Collections.emptyMap()) // GH-90000
                .build(); // GH-90000
            
            when(collectionRepository.findByName("tenant-1", "products")) // GH-90000
                .thenReturn(Promise.of(java.util.Optional.of(existing))); // GH-90000
            
            when(collectionService.updateCollection(eq("tenant-1 [GH-90000]"), any(MetaCollection.class), eq("user-1 [GH-90000]")))
                .thenReturn(Promise.of(existing)); // GH-90000
            
            Map<String, Object> result = runPromise(() ->  // GH-90000
                mutations.updateCollection("tenant-1", "products", "New description", "user-1") // GH-90000
            );
            
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.get("name [GH-90000]")).isEqualTo("products [GH-90000]");
        }

        @Test
        @DisplayName("should delete collection successfully [GH-90000]")
        void shouldDeleteCollectionSuccessfully() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            MetaCollection existing = MetaCollection.builder() // GH-90000
                .id(UUID.randomUUID()) // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("products [GH-90000]")
                .label("products [GH-90000]")
                .description("Product collection [GH-90000]")
                .fields(java.util.Collections.emptyList()) // GH-90000
                .permission(java.util.Collections.emptyMap()) // GH-90000
                .build(); // GH-90000
            
            when(collectionRepository.findByName("tenant-1", "products")) // GH-90000
                .thenReturn(Promise.of(java.util.Optional.of(existing))); // GH-90000
            
            when(collectionService.deleteCollection("tenant-1", existing.getId(), "user-1")) // GH-90000
                .thenReturn(Promise.complete()); // GH-90000
            
            Boolean result = runPromise(() ->  // GH-90000
                mutations.deleteCollection("tenant-1", "products", "user-1") // GH-90000
            );
            
            assertThat(result).isTrue(); // GH-90000
        }
    }

    // =========================================================================
    // INPUT VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Input validation [GH-90000]")
    class InputValidation {

        @Test
        @DisplayName("should reject null tenantId in createEntity [GH-90000]")
        void shouldRejectNullTenantIdInCreateEntity() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.createEntity(null, "products", Map.of("name", "Product 1"), "user-1") // GH-90000
            )).isInstanceOf(NullPointerException.class) // GH-90000
              .hasMessageContaining("tenantId is required [GH-90000]");
        }

        @Test
        @DisplayName("should reject blank tenantId in createEntity [GH-90000]")
        void shouldRejectBlankTenantIdInCreateEntity() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.createEntity("", "products", Map.of("name", "Product 1"), "user-1") // GH-90000
            )).isInstanceOf(IllegalArgumentException.class) // GH-90000
              .hasMessageContaining("tenantId cannot be empty [GH-90000]");
        }

        @Test
        @DisplayName("should reject null collectionName in createEntity [GH-90000]")
        void shouldRejectNullCollectionNameInCreateEntity() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.createEntity("tenant-1", null, Map.of("name", "Product 1"), "user-1") // GH-90000
            )).isInstanceOf(NullPointerException.class) // GH-90000
              .hasMessageContaining("collectionName is required [GH-90000]");
        }

        @Test
        @DisplayName("should reject empty data in createEntity [GH-90000]")
        void shouldRejectEmptyDataInCreateEntity() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.createEntity("tenant-1", "products", Map.of(), "user-1") // GH-90000
            )).isInstanceOf(IllegalArgumentException.class) // GH-90000
              .hasMessageContaining("data cannot be empty [GH-90000]");
        }

        @Test
        @DisplayName("should reject null data in createEntity [GH-90000]")
        void shouldRejectNullDataInCreateEntity() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.createEntity("tenant-1", "products", null, "user-1") // GH-90000
            )).isInstanceOf(IllegalArgumentException.class) // GH-90000
              .hasMessageContaining("data cannot be empty [GH-90000]");
        }

        @Test
        @DisplayName("should reject null userId in createEntity [GH-90000]")
        void shouldRejectNullUserIdInCreateEntity() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.createEntity("tenant-1", "products", Map.of("name", "Product 1"), null) // GH-90000
            )).isInstanceOf(NullPointerException.class) // GH-90000
              .hasMessageContaining("userId is required [GH-90000]");
        }

        @Test
        @DisplayName("should reject blank name in createCollection [GH-90000]")
        void shouldRejectBlankNameInCreateCollection() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.createCollection("tenant-1", "", "Description", "user-1") // GH-90000
            )).isInstanceOf(IllegalArgumentException.class) // GH-90000
              .hasMessageContaining("name cannot be empty [GH-90000]");
        }

        @Test
        @DisplayName("should reject null name in createCollection [GH-90000]")
        void shouldRejectNullNameInCreateCollection() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.createCollection("tenant-1", null, "Description", "user-1") // GH-90000
            )).isInstanceOf(NullPointerException.class) // GH-90000
              .hasMessageContaining("name is required [GH-90000]");
        }
    }

    // =========================================================================
    // UUID PARSING AND ERROR HANDLING
    // =========================================================================

    @Nested
    @DisplayName("UUID parsing and error handling [GH-90000]")
    class UuidParsingAndErrorHandling {

        @Test
        @DisplayName("should reject invalid UUID format in updateEntity [GH-90000]")
        void shouldRejectInvalidUuidFormatInUpdateEntity() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.updateEntity("tenant-1", "products", "invalid-uuid", Map.of("price", 150), "user-1") // GH-90000
            )).isInstanceOf(IllegalArgumentException.class) // GH-90000
              .hasMessageContaining("Invalid entity ID format [GH-90000]");
        }

        @Test
        @DisplayName("should reject invalid UUID format in deleteEntity [GH-90000]")
        void shouldRejectInvalidUuidFormatInDeleteEntity() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.deleteEntity("tenant-1", "products", "invalid-uuid", "user-1") // GH-90000
            )).isInstanceOf(IllegalArgumentException.class) // GH-90000
              .hasMessageContaining("Invalid entity ID format [GH-90000]");
        }

        @Test
        @DisplayName("should handle collection not found in updateCollection [GH-90000]")
        void shouldHandleCollectionNotFoundInUpdateCollection() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            when(collectionRepository.findByName("tenant-1", "products")) // GH-90000
                .thenReturn(Promise.of(java.util.Optional.empty())); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.updateCollection("tenant-1", "products", "New description", "user-1") // GH-90000
            )).isInstanceOf(IllegalStateException.class) // GH-90000
              .hasMessageContaining("Collection not found [GH-90000]");
        }

        @Test
        @DisplayName("should handle collection not found in deleteCollection [GH-90000]")
        void shouldHandleCollectionNotFoundInDeleteCollection() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            when(collectionRepository.findByName("tenant-1", "products")) // GH-90000
                .thenReturn(Promise.of(java.util.Optional.empty())); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.deleteCollection("tenant-1", "products", "user-1") // GH-90000
            )).isInstanceOf(IllegalStateException.class) // GH-90000
              .hasMessageContaining("Collection not found [GH-90000]");
        }
    }

    // =========================================================================
    // RESPONSE MAPPING
    // =========================================================================

    @Nested
    @DisplayName("Response mapping [GH-90000]")
    class ResponseMapping {

        @Test
        @DisplayName("should map entity to response map correctly [GH-90000]")
        void shouldMapEntityToResponseMapCorrectly() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            UUID entityId = UUID.randomUUID(); // GH-90000
            Entity entity = Entity.builder() // GH-90000
                .id(entityId) // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .collectionName("products [GH-90000]")
                .data(Map.of("name", "Product 1", "price", 100)) // GH-90000
                .metadata(Map.of("source", "api")) // GH-90000
                .version(1) // GH-90000
                .build(); // GH-90000
            
            when(entityService.createEntity("tenant-1", "products", Map.of("name", "Product 1"), "user-1")) // GH-90000
                .thenReturn(Promise.of(entity)); // GH-90000
            
            Map<String, Object> result = runPromise(() ->  // GH-90000
                mutations.createEntity("tenant-1", "products", Map.of("name", "Product 1"), "user-1") // GH-90000
            );
            
            assertThat(result.get("id [GH-90000]")).isEqualTo(entityId.toString());
            assertThat(result.get("tenantId [GH-90000]")).isEqualTo("tenant-1 [GH-90000]");
            assertThat(result.get("collectionName [GH-90000]")).isEqualTo("products [GH-90000]");
            assertThat(result.get("data [GH-90000]")).isEqualTo(Map.of("name", "Product 1", "price", 100));
            assertThat(result.get("metadata [GH-90000]")).isEqualTo(Map.of("source", "api"));
            assertThat(result.get("version [GH-90000]")).isEqualTo(1);
        }

        @Test
        @DisplayName("should map collection to response map correctly [GH-90000]")
        void shouldMapCollectionToResponseMapCorrectly() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            UUID collectionId = UUID.randomUUID(); // GH-90000
            MetaCollection collection = MetaCollection.builder() // GH-90000
                .id(collectionId) // GH-90000
                .tenantId("tenant-1 [GH-90000]")
                .name("products [GH-90000]")
                .label("products [GH-90000]")
                .description("Product collection [GH-90000]")
                .fields(java.util.Collections.emptyList()) // GH-90000
                .permission(java.util.Collections.emptyMap()) // GH-90000
                .build(); // GH-90000
            
            when(collectionService.createCollection(eq("tenant-1 [GH-90000]"), any(MetaCollection.class), eq("user-1 [GH-90000]")))
                .thenReturn(Promise.of(collection)); // GH-90000
            
            Map<String, Object> result = runPromise(() ->  // GH-90000
                mutations.createCollection("tenant-1", "products", "Product collection", "user-1") // GH-90000
            );
            
            assertThat(result.get("id [GH-90000]")).isEqualTo(collectionId.toString());
            assertThat(result.get("tenantId [GH-90000]")).isEqualTo("tenant-1 [GH-90000]");
            assertThat(result.get("name [GH-90000]")).isEqualTo("products [GH-90000]");
            assertThat(result.get("description [GH-90000]")).isEqualTo("Product collection [GH-90000]");
        }

        @Test
        @DisplayName("should handle null entity in mapping [GH-90000]")
        void shouldHandleNullEntityInMapping() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            when(entityService.createEntity("tenant-1", "products", Map.of("name", "Product 1"), "user-1")) // GH-90000
                .thenReturn(Promise.of(null)); // GH-90000
            
            Map<String, Object> result = runPromise(() ->  // GH-90000
                mutations.createEntity("tenant-1", "products", Map.of("name", "Product 1"), "user-1") // GH-90000
            );
            
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result).isEmpty(); // GH-90000
        }
    }
}
