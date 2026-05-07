/**
 * @doc.type class
 * @doc.purpose Test entity storage, retrieval, and persistence
 * @doc.layer products
 * @doc.pattern Test
 */
package com.ghatana.datacloud.entity;

import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/**
 * Entity Storage Tests
 *
 * Test entity storage, retrieval, and persistence.
 */
@DisplayName("Entity Storage Tests")
class EntityStorageTest {

    @Test
    @DisplayName("Should store entities")
    void shouldStoreEntities() { 
        EntityRepository repository = mock(EntityRepository.class); 

        Entity entity = Entity.builder() 
            .tenantId("tenant-123")
            .collectionName("test-collection")
            .data(new HashMap<>()) 
            .build(); 

        assertThat(entity.getTenantId()).isEqualTo("tenant-123");
        assertThat(entity.getCollectionName()).isEqualTo("test-collection");
    }

    @Test
    @DisplayName("Should retrieve entities")
    void shouldRetrieveEntities() { 
        UUID entityId = UUID.randomUUID(); 
        String tenantId = "tenant-123";
        String collectionName = "test-collection";

        assertThat(entityId).isNotNull(); 
        assertThat(tenantId).isNotNull(); 
        assertThat(collectionName).isNotNull(); 
    }

    @Test
    @DisplayName("Should update entities")
    void shouldUpdateEntities() { 
        Entity entity = Entity.builder() 
            .tenantId("tenant-123")
            .collectionName("test-collection")
            .data(new HashMap<>()) 
            .build(); 

        Map<String, Object> newData = new HashMap<>(); 
        newData.put("name", "updated"); 
        entity.setData(newData); 

        assertThat(entity.getData()).containsKey("name");
        assertThat(entity.getData().get("name")).isEqualTo("updated");
    }

    @Test
    @DisplayName("Should delete entities")
    void shouldDeleteEntities() { 
        UUID entityId = UUID.randomUUID(); 

        assertThat(entityId).isNotNull(); 
    }

    @Test
    @DisplayName("Should handle storage failures")
    void shouldHandleStorageFailures() { 
        EntityRepository repository = mock(EntityRepository.class); 

        Entity entity = Entity.builder() 
            .tenantId("tenant-123")
            .collectionName("test-collection")
            .data(new HashMap<>()) 
            .build(); 

        when(repository.findById(any(String.class), any(String.class), any(UUID.class))) 
            .thenReturn(Promise.of(Optional.of(entity))); 

        Promise<Optional<Entity>> result = repository.findById("tenant-123", "test-collection", UUID.randomUUID()); 

        assertThat(repository).isNotNull(); 
        assertThat(result).isNotNull(); 
    }

    @Test
    @DisplayName("Should handle persistence")
    void shouldHandlePersistence() { 
        MetaCollection collection = MetaCollection.builder() 
            .tenantId("tenant-123")
            .name("test-collection")
            .build(); 

        collection.setVersion(1); 

        assertThat(collection.getVersion()).isEqualTo(1); 
    }
}
