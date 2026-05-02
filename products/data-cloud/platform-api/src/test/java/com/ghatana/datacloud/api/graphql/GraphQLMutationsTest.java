/*
 * Copyright (c) 2026 Ghatana Inc. 
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
 *   <li>Entity CRUD operations (create, update, delete)</li> 
 *   <li>Collection CRUD operations (create, update, delete)</li> 
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
@ExtendWith(MockitoExtension.class) 
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
        void shouldCreateEntitySuccessfully() { 
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); 
            
            Entity entity = Entity.builder() 
                .id(UUID.randomUUID()) 
                .tenantId("tenant-1")
                .collectionName("products")
                .data(Map.of("name", "Product 1", "price", 100)) 
                .version(1) 
                .build(); 
            
            when(entityService.createEntity("tenant-1", "products", Map.of("name", "Product 1"), "user-1")) 
                .thenReturn(Promise.of(entity)); 
            
            Map<String, Object> result = runPromise(() ->  
                mutations.createEntity("tenant-1", "products", Map.of("name", "Product 1"), "user-1") 
            );
            
            assertThat(result).isNotNull(); 
            assertThat(result.get("tenantId")).isEqualTo("tenant-1");
            assertThat(result.get("collectionName")).isEqualTo("products");
        }

        @Test
        @DisplayName("should update entity successfully")
        void shouldUpdateEntitySuccessfully() { 
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); 
            
            UUID entityId = UUID.randomUUID(); 
            Entity entity = Entity.builder() 
                .id(entityId) 
                .tenantId("tenant-1")
                .collectionName("products")
                .data(Map.of("name", "Updated Product", "price", 150)) 
                .version(2) 
                .build(); 
            
            when(entityService.updateEntity("tenant-1", "products", entityId, Map.of("price", 150), "user-1")) 
                .thenReturn(Promise.of(entity)); 
            
            Map<String, Object> result = runPromise(() ->  
                mutations.updateEntity("tenant-1", "products", entityId.toString(), Map.of("price", 150), "user-1") 
            );
            
            assertThat(result).isNotNull(); 
            assertThat(result.get("id")).isEqualTo(entityId.toString());
            assertThat(result.get("version")).isEqualTo(2);
        }

        @Test
        @DisplayName("should delete entity successfully")
        void shouldDeleteEntitySuccessfully() { 
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); 
            
            UUID entityId = UUID.randomUUID(); 
            when(entityService.deleteEntity("tenant-1", "products", entityId, "user-1")) 
                .thenReturn(Promise.complete()); 
            
            Boolean result = runPromise(() ->  
                mutations.deleteEntity("tenant-1", "products", entityId.toString(), "user-1") 
            );
            
            assertThat(result).isTrue(); 
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
        void shouldCreateCollectionSuccessfully() { 
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); 
            
            MetaCollection collection = MetaCollection.builder() 
                .id(UUID.randomUUID()) 
                .tenantId("tenant-1")
                .name("products")
                .label("products")
                .description("Product collection")
                .fields(java.util.Collections.emptyList()) 
                .permission(java.util.Collections.emptyMap()) 
                .build(); 
            
            when(collectionService.createCollection(eq("tenant-1"), any(MetaCollection.class), eq("user-1")))
                .thenReturn(Promise.of(collection)); 
            
            Map<String, Object> result = runPromise(() ->  
                mutations.createCollection("tenant-1", "products", "Product collection", "user-1") 
            );
            
            assertThat(result).isNotNull(); 
            assertThat(result.get("tenantId")).isEqualTo("tenant-1");
            assertThat(result.get("name")).isEqualTo("products");
        }

        @Test
        @DisplayName("should update collection successfully")
        void shouldUpdateCollectionSuccessfully() { 
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); 
            
            MetaCollection existing = MetaCollection.builder() 
                .id(UUID.randomUUID()) 
                .tenantId("tenant-1")
                .name("products")
                .label("products")
                .description("Old description")
                .fields(java.util.Collections.emptyList()) 
                .permission(java.util.Collections.emptyMap()) 
                .build(); 
            
            when(collectionRepository.findByName("tenant-1", "products")) 
                .thenReturn(Promise.of(java.util.Optional.of(existing))); 
            
            when(collectionService.updateCollection(eq("tenant-1"), any(MetaCollection.class), eq("user-1")))
                .thenReturn(Promise.of(existing)); 
            
            Map<String, Object> result = runPromise(() ->  
                mutations.updateCollection("tenant-1", "products", "New description", "user-1") 
            );
            
            assertThat(result).isNotNull(); 
            assertThat(result.get("name")).isEqualTo("products");
        }

        @Test
        @DisplayName("should delete collection successfully")
        void shouldDeleteCollectionSuccessfully() { 
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); 
            
            MetaCollection existing = MetaCollection.builder() 
                .id(UUID.randomUUID()) 
                .tenantId("tenant-1")
                .name("products")
                .label("products")
                .description("Product collection")
                .fields(java.util.Collections.emptyList()) 
                .permission(java.util.Collections.emptyMap()) 
                .build(); 
            
            when(collectionRepository.findByName("tenant-1", "products")) 
                .thenReturn(Promise.of(java.util.Optional.of(existing))); 
            
            when(collectionService.deleteCollection("tenant-1", existing.getId(), "user-1")) 
                .thenReturn(Promise.complete()); 
            
            Boolean result = runPromise(() ->  
                mutations.deleteCollection("tenant-1", "products", "user-1") 
            );
            
            assertThat(result).isTrue(); 
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
        void shouldRejectNullTenantIdInCreateEntity() { 
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); 
            
            assertThatThrownBy(() -> runPromise(() ->  
                mutations.createEntity(null, "products", Map.of("name", "Product 1"), "user-1") 
            )).isInstanceOf(NullPointerException.class) 
              .hasMessageContaining("tenantId is required");
        }

        @Test
        @DisplayName("should reject blank tenantId in createEntity")
        void shouldRejectBlankTenantIdInCreateEntity() { 
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); 
            
            assertThatThrownBy(() -> runPromise(() ->  
                mutations.createEntity("", "products", Map.of("name", "Product 1"), "user-1") 
            )).isInstanceOf(IllegalArgumentException.class) 
              .hasMessageContaining("tenantId cannot be empty");
        }

        @Test
        @DisplayName("should reject null collectionName in createEntity")
        void shouldRejectNullCollectionNameInCreateEntity() { 
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); 
            
            assertThatThrownBy(() -> runPromise(() ->  
                mutations.createEntity("tenant-1", null, Map.of("name", "Product 1"), "user-1") 
            )).isInstanceOf(NullPointerException.class) 
              .hasMessageContaining("collectionName is required");
        }

        @Test
        @DisplayName("should reject empty data in createEntity")
        void shouldRejectEmptyDataInCreateEntity() { 
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); 
            
            assertThatThrownBy(() -> runPromise(() ->  
                mutations.createEntity("tenant-1", "products", Map.of(), "user-1") 
            )).isInstanceOf(IllegalArgumentException.class) 
              .hasMessageContaining("data cannot be empty");
        }

        @Test
        @DisplayName("should reject null data in createEntity")
        void shouldRejectNullDataInCreateEntity() { 
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); 
            
            assertThatThrownBy(() -> runPromise(() ->  
                mutations.createEntity("tenant-1", "products", null, "user-1") 
            )).isInstanceOf(IllegalArgumentException.class) 
              .hasMessageContaining("data cannot be empty");
        }

        @Test
        @DisplayName("should reject null userId in createEntity")
        void shouldRejectNullUserIdInCreateEntity() { 
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); 
            
            assertThatThrownBy(() -> runPromise(() ->  
                mutations.createEntity("tenant-1", "products", Map.of("name", "Product 1"), null) 
            )).isInstanceOf(NullPointerException.class) 
              .hasMessageContaining("userId is required");
        }

        @Test
        @DisplayName("should reject blank name in createCollection")
        void shouldRejectBlankNameInCreateCollection() { 
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); 
            
            assertThatThrownBy(() -> runPromise(() ->  
                mutations.createCollection("tenant-1", "", "Description", "user-1") 
            )).isInstanceOf(IllegalArgumentException.class) 
              .hasMessageContaining("name cannot be empty");
        }

        @Test
        @DisplayName("should reject null name in createCollection")
        void shouldRejectNullNameInCreateCollection() { 
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); 
            
            assertThatThrownBy(() -> runPromise(() ->  
                mutations.createCollection("tenant-1", null, "Description", "user-1") 
            )).isInstanceOf(NullPointerException.class) 
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
        void shouldRejectInvalidUuidFormatInUpdateEntity() { 
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); 
            
            assertThatThrownBy(() -> runPromise(() ->  
                mutations.updateEntity("tenant-1", "products", "invalid-uuid", Map.of("price", 150), "user-1") 
            )).isInstanceOf(IllegalArgumentException.class) 
              .hasMessageContaining("Invalid entity ID format");
        }

        @Test
        @DisplayName("should reject invalid UUID format in deleteEntity")
        void shouldRejectInvalidUuidFormatInDeleteEntity() { 
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); 
            
            assertThatThrownBy(() -> runPromise(() ->  
                mutations.deleteEntity("tenant-1", "products", "invalid-uuid", "user-1") 
            )).isInstanceOf(IllegalArgumentException.class) 
              .hasMessageContaining("Invalid entity ID format");
        }

        @Test
        @DisplayName("should handle collection not found in updateCollection")
        void shouldHandleCollectionNotFoundInUpdateCollection() { 
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); 
            
            when(collectionRepository.findByName("tenant-1", "products")) 
                .thenReturn(Promise.of(java.util.Optional.empty())); 
            
            assertThatThrownBy(() -> runPromise(() ->  
                mutations.updateCollection("tenant-1", "products", "New description", "user-1") 
            )).isInstanceOf(IllegalStateException.class) 
              .hasMessageContaining("Collection not found");
        }

        @Test
        @DisplayName("should handle collection not found in deleteCollection")
        void shouldHandleCollectionNotFoundInDeleteCollection() { 
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); 
            
            when(collectionRepository.findByName("tenant-1", "products")) 
                .thenReturn(Promise.of(java.util.Optional.empty())); 
            
            assertThatThrownBy(() -> runPromise(() ->  
                mutations.deleteCollection("tenant-1", "products", "user-1") 
            )).isInstanceOf(IllegalStateException.class) 
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
        void shouldMapEntityToResponseMapCorrectly() { 
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); 
            
            UUID entityId = UUID.randomUUID(); 
            Entity entity = Entity.builder() 
                .id(entityId) 
                .tenantId("tenant-1")
                .collectionName("products")
                .data(Map.of("name", "Product 1", "price", 100)) 
                .metadata(Map.of("source", "api")) 
                .version(1) 
                .build(); 
            
            when(entityService.createEntity("tenant-1", "products", Map.of("name", "Product 1"), "user-1")) 
                .thenReturn(Promise.of(entity)); 
            
            Map<String, Object> result = runPromise(() ->  
                mutations.createEntity("tenant-1", "products", Map.of("name", "Product 1"), "user-1") 
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
        void shouldMapCollectionToResponseMapCorrectly() { 
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); 
            
            UUID collectionId = UUID.randomUUID(); 
            MetaCollection collection = MetaCollection.builder() 
                .id(collectionId) 
                .tenantId("tenant-1")
                .name("products")
                .label("products")
                .description("Product collection")
                .fields(java.util.Collections.emptyList()) 
                .permission(java.util.Collections.emptyMap()) 
                .build(); 
            
            when(collectionService.createCollection(eq("tenant-1"), any(MetaCollection.class), eq("user-1")))
                .thenReturn(Promise.of(collection)); 
            
            Map<String, Object> result = runPromise(() ->  
                mutations.createCollection("tenant-1", "products", "Product collection", "user-1") 
            );
            
            assertThat(result.get("id")).isEqualTo(collectionId.toString());
            assertThat(result.get("tenantId")).isEqualTo("tenant-1");
            assertThat(result.get("name")).isEqualTo("products");
            assertThat(result.get("description")).isEqualTo("Product collection");
        }

        @Test
        @DisplayName("should handle null entity in mapping")
        void shouldHandleNullEntityInMapping() { 
            GraphQLMutations mutations = new GraphQLMutations(entityService, collectionService, collectionRepository); 
            
            when(entityService.createEntity("tenant-1", "products", Map.of("name", "Product 1"), "user-1")) 
                .thenReturn(Promise.of(null)); 
            
            Map<String, Object> result = runPromise(() ->  
                mutations.createEntity("tenant-1", "products", Map.of("name", "Product 1"), "user-1") 
            );
            
            assertThat(result).isNotNull(); 
            assertThat(result).isEmpty(); 
        }
    }
}
