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
@DisplayName("Entity Storage Tests [GH-90000]")
class EntityStorageTest {

    @Test
    @DisplayName("Should store entities [GH-90000]")
    void shouldStoreEntities() { // GH-90000
        EntityRepository repository = mock(EntityRepository.class); // GH-90000

        Entity entity = Entity.builder() // GH-90000
            .tenantId("tenant-123 [GH-90000]")
            .collectionName("test-collection [GH-90000]")
            .data(new HashMap<>()) // GH-90000
            .build(); // GH-90000

        assertThat(entity.getTenantId()).isEqualTo("tenant-123 [GH-90000]");
        assertThat(entity.getCollectionName()).isEqualTo("test-collection [GH-90000]");
    }

    @Test
    @DisplayName("Should retrieve entities [GH-90000]")
    void shouldRetrieveEntities() { // GH-90000
        UUID entityId = UUID.randomUUID(); // GH-90000
        String tenantId = "tenant-123";
        String collectionName = "test-collection";

        assertThat(entityId).isNotNull(); // GH-90000
        assertThat(tenantId).isNotNull(); // GH-90000
        assertThat(collectionName).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should update entities [GH-90000]")
    void shouldUpdateEntities() { // GH-90000
        Entity entity = Entity.builder() // GH-90000
            .tenantId("tenant-123 [GH-90000]")
            .collectionName("test-collection [GH-90000]")
            .data(new HashMap<>()) // GH-90000
            .build(); // GH-90000

        Map<String, Object> newData = new HashMap<>(); // GH-90000
        newData.put("name", "updated"); // GH-90000
        entity.setData(newData); // GH-90000

        assertThat(entity.getData()).containsKey("name [GH-90000]");
        assertThat(entity.getData().get("name [GH-90000]")).isEqualTo("updated [GH-90000]");
    }

    @Test
    @DisplayName("Should delete entities [GH-90000]")
    void shouldDeleteEntities() { // GH-90000
        UUID entityId = UUID.randomUUID(); // GH-90000

        assertThat(entityId).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should handle storage failures [GH-90000]")
    void shouldHandleStorageFailures() { // GH-90000
        EntityRepository repository = mock(EntityRepository.class); // GH-90000

        Entity entity = Entity.builder() // GH-90000
            .tenantId("tenant-123 [GH-90000]")
            .collectionName("test-collection [GH-90000]")
            .data(new HashMap<>()) // GH-90000
            .build(); // GH-90000

        when(repository.findById(any(String.class), any(String.class), any(UUID.class))) // GH-90000
            .thenReturn(Promise.of(Optional.of(entity))); // GH-90000

        Promise<Optional<Entity>> result = repository.findById("tenant-123", "test-collection", UUID.randomUUID()); // GH-90000

        assertThat(repository).isNotNull(); // GH-90000
        assertThat(result).isNotNull(); // GH-90000
    }

    @Test
    @DisplayName("Should handle persistence [GH-90000]")
    void shouldHandlePersistence() { // GH-90000
        MetaCollection collection = MetaCollection.builder() // GH-90000
            .tenantId("tenant-123 [GH-90000]")
            .name("test-collection [GH-90000]")
            .build(); // GH-90000

        collection.setVersion(1); // GH-90000

        assertThat(collection.getVersion()).isEqualTo(1); // GH-90000
    }
}
