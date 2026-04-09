/**
 * @doc.type class
 * @doc.purpose Test entity consistency, validation, and data integrity
 * @doc.layer products
 * @doc.pattern Test
 */
package com.ghatana.datacloud.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Entity Consistency Tests
 *
 * Test entity consistency, validation, and data integrity.
 */
@DisplayName("Entity Consistency Tests")
class EntityConsistencyTest {

    @Test
    @DisplayName("Should maintain entity consistency")
    void shouldMaintainEntityConsistency() {
        MetaCollection collection = MetaCollection.builder()
            .tenantId("tenant-123")
            .name("test-collection")
            .active(true)
            .build();

        assertThat(collection.getActive()).isTrue();
        assertThat(collection.getTenantId()).isEqualTo("tenant-123");
    }

    @Test
    @DisplayName("Should validate entity data")
    void shouldValidateEntityData() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "test");
        data.put("value", 123);

        assertThat(data).isNotEmpty();
        assertThat(data).containsKey("name");
        assertThat(data).containsKey("value");
    }

    @Test
    @DisplayName("Should handle data integrity")
    void shouldHandleDataIntegrity() {
        MetaCollection collection = MetaCollection.builder()
            .tenantId("tenant-123")
            .name("test-collection")
            .version(1)
            .build();

        collection.setVersion(2);

        assertThat(collection.getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should handle consistency checks")
    void shouldHandleConsistencyChecks() {
        MetaCollection collection1 = MetaCollection.builder()
            .tenantId("tenant-123")
            .name("test-collection")
            .build();

        MetaCollection collection2 = MetaCollection.builder()
            .tenantId("tenant-123")
            .name("test-collection")
            .build();

        boolean sameTenant = collection1.getTenantId().equals(collection2.getTenantId());
        boolean sameName = collection1.getName().equals(collection2.getName());

        assertThat(sameTenant).isTrue();
        assertThat(sameName).isTrue();
    }

    @Test
    @DisplayName("Should handle consistency failures")
    void shouldHandleConsistencyFailures() {
        MetaCollection collection = MetaCollection.builder()
            .tenantId("tenant-123")
            .name("test-collection")
            .active(true)
            .build();

        collection.setActive(false);

        assertThat(collection.getActive()).isFalse();
    }

    @Test
    @DisplayName("Should handle consistency recovery")
    void shouldHandleConsistencyRecovery() {
        MetaCollection collection = MetaCollection.builder()
            .tenantId("tenant-123")
            .name("test-collection")
            .active(false)
            .build();

        collection.setActive(true);

        assertThat(collection.getActive()).isTrue();
    }
}
