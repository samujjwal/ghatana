package com.ghatana.datacloud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Collection}.
 */
@DisplayName("Collection")
class CollectionTest {

    @Test
    @DisplayName("builder creates collection with required fields")
    void builder_createsCollection() {
        Collection collection = Collection.builder()
                .tenantId("tenant-123")
                .name("customers")
                .recordType(RecordType.ENTITY)
                .build();

        assertThat(collection.getTenantId()).isEqualTo("tenant-123");
        assertThat(collection.getName()).isEqualTo("customers");
        assertThat(collection.getRecordType()).isEqualTo(RecordType.ENTITY);
    }

    @Test
    @DisplayName("supportsOperation returns true for CREATE on ENTITY type")
    void supportsOperation_createOnEntity() {
        Collection collection = Collection.builder()
                .tenantId("tenant-123")
                .name("customers")
                .recordType(RecordType.ENTITY)
                .build();

        assertThat(collection.supportsOperation(DataRecord.RecordOperation.CREATE)).isTrue();
    }

    @Test
    @DisplayName("supportsOperation returns true for UPDATE on ENTITY type")
    void supportsOperation_updateOnEntity() {
        Collection collection = Collection.builder()
                .tenantId("tenant-123")
                .name("customers")
                .recordType(RecordType.ENTITY)
                .build();

        assertThat(collection.supportsOperation(DataRecord.RecordOperation.UPDATE)).isTrue();
    }

    @Test
    @DisplayName("supportsOperation returns false for UPDATE on EVENT type")
    void supportsOperation_updateOnEvent() {
        Collection collection = Collection.builder()
                .tenantId("tenant-123")
                .name("events")
                .recordType(RecordType.EVENT)
                .build();

        assertThat(collection.supportsOperation(DataRecord.RecordOperation.UPDATE)).isFalse();
    }

    @Test
    @DisplayName("supportsOperation returns true for APPEND on EVENT type")
    void supportsOperation_appendOnEvent() {
        Collection collection = Collection.builder()
                .tenantId("tenant-123")
                .name("events")
                .recordType(RecordType.EVENT)
                .build();

        assertThat(collection.supportsOperation(DataRecord.RecordOperation.APPEND)).isTrue();
    }

    @Test
    @DisplayName("getField returns empty when fields is null")
    void getField_returnsEmptyWhenFieldsNull() {
        Collection collection = Collection.builder()
                .tenantId("tenant-123")
                .name("customers")
                .recordType(RecordType.ENTITY)
                .fields(null)
                .build();

        assertThat(collection.getField("name")).isEmpty();
    }

    @Test
    @DisplayName("getField returns field when found")
    void getField_returnsFieldWhenFound() {
        FieldDefinition field = FieldDefinition.builder()
                .name("email")
                .type(FieldDefinition.FieldType.STRING)
                .build();

        Collection collection = Collection.builder()
                .tenantId("tenant-123")
                .name("customers")
                .recordType(RecordType.ENTITY)
                .fields(List.of(field))
                .build();

        assertThat(collection.getField("email")).isPresent();
    }

    @Test
    @DisplayName("addField adds field to collection")
    void addField_addsFieldToCollection() {
        Collection collection = Collection.builder()
                .tenantId("tenant-123")
                .name("customers")
                .recordType(RecordType.ENTITY)
                .build();

        FieldDefinition field = FieldDefinition.builder()
                .name("email")
                .type(FieldDefinition.FieldType.STRING)
                .build();

        collection.addField(field);

        assertThat(collection.getFields()).hasSize(1);
        assertThat(collection.getFields().get(0).getName()).isEqualTo("email");
    }

    @Test
    @DisplayName("getPartitionCount returns 1 when eventConfig is null")
    void getPartitionCount_returnsOneWhenEventConfigNull() {
        Collection collection = Collection.builder()
                .tenantId("tenant-123")
                .name("customers")
                .recordType(RecordType.ENTITY)
                .build();

        assertThat(collection.getPartitionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("getPartitionCount returns configured partition count")
    void getPartitionCount_returnsConfiguredPartitionCount() {
        EventConfig eventConfig = EventConfig.builder()
                .partitionCount(8)
                .build();

        Collection collection = Collection.builder()
                .tenantId("tenant-123")
                .name("events")
                .recordType(RecordType.EVENT)
                .eventConfig(eventConfig)
                .build();

        assertThat(collection.getPartitionCount()).isEqualTo(8);
    }

    @Test
    @DisplayName("equals returns true for same ID")
    void equals_returnsTrueForSameId() {
        UUID id = UUID.randomUUID();
        Collection c1 = Collection.builder()
                .id(id)
                .tenantId("tenant-123")
                .name("customers")
                .build();

        Collection c2 = Collection.builder()
                .id(id)
                .tenantId("tenant-456")
                .name("orders")
                .build();

        assertThat(c1).isEqualTo(c2);
    }

    @Test
    @DisplayName("equals returns false for different IDs")
    void equals_returnsFalseForDifferentIds() {
        Collection c1 = Collection.builder()
                .tenantId("tenant-123")
                .name("customers")
                .build();

        Collection c2 = Collection.builder()
                .tenantId("tenant-456")
                .name("orders")
                .build();

        assertThat(c1).isNotEqualTo(c2);
    }

    @Test
    @DisplayName("hashCode returns same for same ID")
    void hashCode_returnsSameForSameId() {
        UUID id = UUID.randomUUID();
        Collection c1 = Collection.builder()
                .id(id)
                .tenantId("tenant-123")
                .name("customers")
                .build();

        Collection c2 = Collection.builder()
                .id(id)
                .tenantId("tenant-456")
                .name("orders")
                .build();

        assertThat(c1.hashCode()).isEqualTo(c2.hashCode());
    }

    @Test
    @DisplayName("toString contains key fields")
    void toString_containsKeyFields() {
        Collection collection = Collection.builder()
                .tenantId("tenant-123")
                .name("customers")
                .recordType(RecordType.ENTITY)
                .active(true)
                .build();

        String str = collection.toString();
        assertThat(str).contains("tenant-123");
        assertThat(str).contains("customers");
        assertThat(str).contains("ENTITY");
        assertThat(str).contains("active=true");
    }
}
