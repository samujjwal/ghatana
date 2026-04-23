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
@DisplayName("GraphQL Mutations Tests")
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
    @DisplayName("Entity CRUD operations")
    class EntityCrudOperations {

        @Test
        @DisplayName("should create entity successfully")
        void shouldCreateEntitySuccessfully() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            Entity entity = Entity.builder() // GH-90000
                .id(UUID.randomUUID()) // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .data(Map.of("name", "Product 1", "price", 100)) // GH-90000
                .version(1) // GH-90000
                .build(); // GH-90000
            
            when(entityService.createEntity("tenant-1", "products", Map.of("name", "Product 1"), "user-1")) // GH-90000
                .thenReturn(Promise.of(entity)); // GH-90000
            
            Map<String, Object> result = runPromise(() ->  // GH-90000
                mutations.createEntity("tenant-1", "products", Map.of("name", "Product 1"), "user-1") // GH-90000
            );
            
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.get("tenantId")).isEqualTo("tenant-1");
            assertThat(result.get("collectionName")).isEqualTo("products");
        }

        @Test
        @DisplayName("should update entity successfully")
        void shouldUpdateEntitySuccessfully() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            UUID entityId = UUID.randomUUID(); // GH-90000
            Entity entity = Entity.builder() // GH-90000
                .id(entityId) // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .data(Map.of("name", "Updated Product", "price", 150)) // GH-90000
                .version(2) // GH-90000
                .build(); // GH-90000
            
            when(entityService.updateEntity("tenant-1", "products", entityId, Map.of("price", 150), "user-1")) // GH-90000
                .thenReturn(Promise.of(entity)); // GH-90000
            
            Map<String, Object> result = runPromise(() ->  // GH-90000
                mutations.updateEntity("tenant-1", "products", entityId.toString(), Map.of("price", 150), "user-1") // GH-90000
            );
            
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.get("id")).isEqualTo(entityId.toString());
            assertThat(result.get("version")).isEqualTo(2);
        }

        @Test
        @DisplayName("should delete entity successfully")
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
    @DisplayName("Collection CRUD operations")
    class CollectionCrudOperations {

        @Test
        @DisplayName("should create collection successfully")
        void shouldCreateCollectionSuccessfully() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            MetaCollection collection = MetaCollection.builder() // GH-90000
                .id(UUID.randomUUID()) // GH-90000
                .tenantId("tenant-1")
                .name("products")
                .label("products")
                .description("Product collection")
                .fields(java.util.Collections.emptyList()) // GH-90000
                .permission(java.util.Collections.emptyMap()) // GH-90000
                .build(); // GH-90000
            
            when(collectionService.createCollection(eq("tenant-1"), any(MetaCollection.class), eq("user-1")))
                .thenReturn(Promise.of(collection)); // GH-90000
            
            Map<String, Object> result = runPromise(() ->  // GH-90000
                mutations.createCollection("tenant-1", "products", "Product collection", "user-1") // GH-90000
            );
            
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.get("tenantId")).isEqualTo("tenant-1");
            assertThat(result.get("name")).isEqualTo("products");
        }

        @Test
        @DisplayName("should update collection successfully")
        void shouldUpdateCollectionSuccessfully() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            MetaCollection existing = MetaCollection.builder() // GH-90000
                .id(UUID.randomUUID()) // GH-90000
                .tenantId("tenant-1")
                .name("products")
                .label("products")
                .description("Old description")
                .fields(java.util.Collections.emptyList()) // GH-90000
                .permission(java.util.Collections.emptyMap()) // GH-90000
                .build(); // GH-90000
            
            when(collectionRepository.findByName("tenant-1", "products")) // GH-90000
                .thenReturn(Promise.of(java.util.Optional.of(existing))); // GH-90000
            
            when(collectionService.updateCollection(eq("tenant-1"), any(MetaCollection.class), eq("user-1")))
                .thenReturn(Promise.of(existing)); // GH-90000
            
            Map<String, Object> result = runPromise(() ->  // GH-90000
                mutations.updateCollection("tenant-1", "products", "New description", "user-1") // GH-90000
            );
            
            assertThat(result).isNotNull(); // GH-90000
            assertThat(result.get("name")).isEqualTo("products");
        }

        @Test
        @DisplayName("should delete collection successfully")
        void shouldDeleteCollectionSuccessfully() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            MetaCollection existing = MetaCollection.builder() // GH-90000
                .id(UUID.randomUUID()) // GH-90000
                .tenantId("tenant-1")
                .name("products")
                .label("products")
                .description("Product collection")
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
    @DisplayName("Input validation")
    class InputValidation {

        @Test
        @DisplayName("should reject null tenantId in createEntity")
        void shouldRejectNullTenantIdInCreateEntity() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.createEntity(null, "products", Map.of("name", "Product 1"), "user-1") // GH-90000
            )).isInstanceOf(NullPointerException.class) // GH-90000
              .hasMessageContaining("tenantId is required");
        }

        @Test
        @DisplayName("should reject blank tenantId in createEntity")
        void shouldRejectBlankTenantIdInCreateEntity() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.createEntity("", "products", Map.of("name", "Product 1"), "user-1") // GH-90000
            )).isInstanceOf(IllegalArgumentException.class) // GH-90000
              .hasMessageContaining("tenantId cannot be empty");
        }

        @Test
        @DisplayName("should reject null collectionName in createEntity")
        void shouldRejectNullCollectionNameInCreateEntity() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.createEntity("tenant-1", null, Map.of("name", "Product 1"), "user-1") // GH-90000
            )).isInstanceOf(NullPointerException.class) // GH-90000
              .hasMessageContaining("collectionName is required");
        }

        @Test
        @DisplayName("should reject empty data in createEntity")
        void shouldRejectEmptyDataInCreateEntity() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.createEntity("tenant-1", "products", Map.of(), "user-1") // GH-90000
            )).isInstanceOf(IllegalArgumentException.class) // GH-90000
              .hasMessageContaining("data cannot be empty");
        }

        @Test
        @DisplayName("should reject null data in createEntity")
        void shouldRejectNullDataInCreateEntity() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.createEntity("tenant-1", "products", null, "user-1") // GH-90000
            )).isInstanceOf(IllegalArgumentException.class) // GH-90000
              .hasMessageContaining("data cannot be empty");
        }

        @Test
        @DisplayName("should reject null userId in createEntity")
        void shouldRejectNullUserIdInCreateEntity() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.createEntity("tenant-1", "products", Map.of("name", "Product 1"), null) // GH-90000
            )).isInstanceOf(NullPointerException.class) // GH-90000
              .hasMessageContaining("userId is required");
        }

        @Test
        @DisplayName("should reject blank name in createCollection")
        void shouldRejectBlankNameInCreateCollection() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.createCollection("tenant-1", "", "Description", "user-1") // GH-90000
            )).isInstanceOf(IllegalArgumentException.class) // GH-90000
              .hasMessageContaining("name cannot be empty");
        }

        @Test
        @DisplayName("should reject null name in createCollection")
        void shouldRejectNullNameInCreateCollection() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.createCollection("tenant-1", null, "Description", "user-1") // GH-90000
            )).isInstanceOf(NullPointerException.class) // GH-90000
              .hasMessageContaining("name is required");
        }
    }

    // =========================================================================
    // UUID PARSING AND ERROR HANDLING
    // =========================================================================

    @Nested
    @DisplayName("UUID parsing and error handling")
    class UuidParsingAndErrorHandling {

        @Test
        @DisplayName("should reject invalid UUID format in updateEntity")
        void shouldRejectInvalidUuidFormatInUpdateEntity() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.updateEntity("tenant-1", "products", "invalid-uuid", Map.of("price", 150), "user-1") // GH-90000
            )).isInstanceOf(IllegalArgumentException.class) // GH-90000
              .hasMessageContaining("Invalid entity ID format");
        }

        @Test
        @DisplayName("should reject invalid UUID format in deleteEntity")
        void shouldRejectInvalidUuidFormatInDeleteEntity() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.deleteEntity("tenant-1", "products", "invalid-uuid", "user-1") // GH-90000
            )).isInstanceOf(IllegalArgumentException.class) // GH-90000
              .hasMessageContaining("Invalid entity ID format");
        }

        @Test
        @DisplayName("should handle collection not found in updateCollection")
        void shouldHandleCollectionNotFoundInUpdateCollection() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            when(collectionRepository.findByName("tenant-1", "products")) // GH-90000
                .thenReturn(Promise.of(java.util.Optional.empty())); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.updateCollection("tenant-1", "products", "New description", "user-1") // GH-90000
            )).isInstanceOf(IllegalStateException.class) // GH-90000
              .hasMessageContaining("Collection not found");
        }

        @Test
        @DisplayName("should handle collection not found in deleteCollection")
        void shouldHandleCollectionNotFoundInDeleteCollection() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            when(collectionRepository.findByName("tenant-1", "products")) // GH-90000
                .thenReturn(Promise.of(java.util.Optional.empty())); // GH-90000
            
            assertThatThrownBy(() -> runPromise(() ->  // GH-90000
                mutations.deleteCollection("tenant-1", "products", "user-1") // GH-90000
            )).isInstanceOf(IllegalStateException.class) // GH-90000
              .hasMessageContaining("Collection not found");
        }
    }

    // =========================================================================
    // RESPONSE MAPPING
    // =========================================================================

    @Nested
    @DisplayName("Response mapping")
    class ResponseMapping {

        @Test
        @DisplayName("should map entity to response map correctly")
        void shouldMapEntityToResponseMapCorrectly() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            UUID entityId = UUID.randomUUID(); // GH-90000
            Entity entity = Entity.builder() // GH-90000
                .id(entityId) // GH-90000
                .tenantId("tenant-1")
                .collectionName("products")
                .data(Map.of("name", "Product 1", "price", 100)) // GH-90000
                .metadata(Map.of("source", "api")) // GH-90000
                .version(1) // GH-90000
                .build(); // GH-90000
            
            when(entityService.createEntity("tenant-1", "products", Map.of("name", "Product 1"), "user-1")) // GH-90000
                .thenReturn(Promise.of(entity)); // GH-90000
            
            Map<String, Object> result = runPromise(() ->  // GH-90000
                mutations.createEntity("tenant-1", "products", Map.of("name", "Product 1"), "user-1") // GH-90000
            );
            
            assertThat(result.get("id")).isEqualTo(entityId.toString());
            assertThat(result.get("tenantId")).isEqualTo("tenant-1");
            assertThat(result.get("collectionName")).isEqualTo("products");
            assertThat(result.get("data")).isEqualTo(Map.of("name", "Product 1", "price", 100));
            assertThat(result.get("metadata")).isEqualTo(Map.of("source", "api"));
            assertThat(result.get("version")).isEqualTo(1);
        }

        @Test
        @DisplayName("should map collection to response map correctly")
        void shouldMapCollectionToResponseMapCorrectly() { // GH-90000
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); // GH-90000
            
            UUID collectionId = UUID.randomUUID(); // GH-90000
            MetaCollection collection = MetaCollection.builder() // GH-90000
                .id(collectionId) // GH-90000
                .tenantId("tenant-1")
                .name("products")
                .label("products")
                .description("Product collection")
                .fields(java.util.Collections.emptyList()) // GH-90000
                .permission(java.util.Collections.emptyMap()) // GH-90000
                .build(); // GH-90000
            
            when(collectionService.createCollection(eq("tenant-1"), any(MetaCollection.class), eq("user-1")))
                .thenReturn(Promise.of(collection)); // GH-90000
            
            Map<String, Object> result = runPromise(() ->  // GH-90000
                mutations.createCollection("tenant-1", "products", "Product collection", "user-1") // GH-90000
            );
            
            assertThat(result.get("id")).isEqualTo(collectionId.toString());
            assertThat(result.get("tenantId")).isEqualTo("tenant-1");
            assertThat(result.get("name")).isEqualTo("products");
            assertThat(result.get("description")).isEqualTo("Product collection");
        }

        @Test
        @DisplayName("should handle null entity in mapping")
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
