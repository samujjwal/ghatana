package com.ghatana.datacloud;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Collection} entity.
 *
 * @doc.type class
 * @doc.purpose Tests for Collection entity business methods, equals/hashCode, and builder defaults
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("Collection entity")
class CollectionTest {

    @Nested
    @DisplayName("equals()")
    class EqualsTests {

        @Test
        @DisplayName("should equal itself (reflexive)")
        void shouldEqualItself() { // GH-90000
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1")
                    .name("col1")
                    .build(); // GH-90000
            assertThat(col).isEqualTo(col); // GH-90000
        }

        @Test
        @DisplayName("should equal another collection with the same UUID id")
        void shouldEqualSameId() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Collection a = Collection.builder().tenantId("t1").name("col-a").build();
            Collection b = Collection.builder().tenantId("t2").name("col-b").build();
            // Set id via reflection to simulate JPA-assigned identity
            setId(a, id); // GH-90000
            setId(b, id); // GH-90000

            assertThat(a).isEqualTo(b); // GH-90000
        }

        @Test
        @DisplayName("should not equal a collection with a different UUID id")
        void shouldNotEqualDifferentId() { // GH-90000
            Collection a = Collection.builder().tenantId("t1").name("col").build();
            Collection b = Collection.builder().tenantId("t1").name("col").build();
            setId(a, UUID.randomUUID()); // GH-90000
            setId(b, UUID.randomUUID()); // GH-90000

            assertThat(a).isNotEqualTo(b); // GH-90000
        }

        @Test
        @DisplayName("should not equal when both ids are null (new unsaved entities)")
        void shouldNotEqualBothNullId() { // GH-90000
            Collection a = Collection.builder().tenantId("t1").name("col").build();
            Collection b = Collection.builder().tenantId("t1").name("col").build();
            // ids are null (not yet persisted) // GH-90000
            assertThat(a).isNotEqualTo(b); // GH-90000
        }

        @Test
        @DisplayName("should not equal when this id is null and other id is set")
        void shouldNotEqualThisNullId() { // GH-90000
            Collection a = Collection.builder().tenantId("t1").name("col").build();
            Collection b = Collection.builder().tenantId("t1").name("col").build();
            setId(b, UUID.randomUUID()); // GH-90000

            assertThat(a).isNotEqualTo(b); // GH-90000
        }

        @Test
        @DisplayName("should not equal null")
        void shouldNotEqualNull() { // GH-90000
            Collection col = Collection.builder().tenantId("t1").name("col").build();
            assertThat(col).isNotEqualTo(null); // GH-90000
        }

        @Test
        @DisplayName("should not equal an object of a different type")
        void shouldNotEqualDifferentType() { // GH-90000
            Collection col = Collection.builder().tenantId("t1").name("col").build();
            assertThat(col).isNotEqualTo("not-a-collection");
        }
    }

    @Nested
    @DisplayName("hashCode()")
    class HashCodeTests {

        @Test
        @DisplayName("should return same hash for same UUID id")
        void shouldReturnSameHashForSameId() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Collection a = Collection.builder().tenantId("t1").name("col").build();
            Collection b = Collection.builder().tenantId("t2").name("other").build();
            setId(a, id); // GH-90000
            setId(b, id); // GH-90000

            assertThat(a.hashCode()).isEqualTo(b.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("should return 0 when id is null")
        void shouldReturnZeroForNullId() { // GH-90000
            Collection col = Collection.builder().tenantId("t1").name("col").build();
            assertThat(col.hashCode()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("should return UUID hashCode when id is set")
        void shouldReturnIdHashCode() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Collection col = Collection.builder().tenantId("t1").name("col").build();
            setId(col, id); // GH-90000

            assertThat(col.hashCode()).isEqualTo(id.hashCode()); // GH-90000
        }
    }

    @Nested
    @DisplayName("getField()")
    class GetFieldTests {

        @Test
        @DisplayName("should return present Optional when field exists by name")
        void shouldReturnFieldWhenFound() { // GH-90000
            FieldDefinition field = FieldDefinition.builder().name("email").build();
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1")
                    .name("col")
                    .fields(List.of(field)) // GH-90000
                    .build(); // GH-90000

            assertThat(col.getField("email")).isPresent();
            assertThat(col.getField("email").get().getName()).isEqualTo("email");
        }

        @Test
        @DisplayName("should return empty Optional when field name not found")
        void shouldReturnEmptyWhenNotFound() { // GH-90000
            FieldDefinition field = FieldDefinition.builder().name("email").build();
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1")
                    .name("col")
                    .fields(List.of(field)) // GH-90000
                    .build(); // GH-90000

            assertThat(col.getField("phone")).isEmpty();
        }

        @Test
        @DisplayName("should return empty Optional when fields list is null")
        void shouldReturnEmptyWhenFieldsNull() { // GH-90000
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1")
                    .name("col")
                    .fields(null) // GH-90000
                    .build(); // GH-90000

            assertThat(col.getField("email")).isEmpty();
        }

        @Test
        @DisplayName("should find correct field from multiple fields")
        void shouldFindCorrectFieldAmongMany() { // GH-90000
            FieldDefinition name = FieldDefinition.builder().name("name").build();
            FieldDefinition age = FieldDefinition.builder().name("age").build();
            FieldDefinition email = FieldDefinition.builder().name("email").build();
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1")
                    .name("col")
                    .fields(List.of(name, age, email)) // GH-90000
                    .build(); // GH-90000

            assertThat(col.getField("age")).isPresent();
            assertThat(col.getField("age").get().getName()).isEqualTo("age");
        }
    }

    @Nested
    @DisplayName("addField()")
    class AddFieldTests {

        @Test
        @DisplayName("should add a field and return this for chaining")
        void shouldAddFieldAndReturnThis() { // GH-90000
            Collection col = Collection.builder().tenantId("t1").name("col").build();
            FieldDefinition field = FieldDefinition.builder().name("score").build();

            Collection result = col.addField(field); // GH-90000

            assertThat(result).isSameAs(col); // GH-90000
            assertThat(col.getField("score")).isPresent();
        }

        @Test
        @DisplayName("should initialize fields list when null before adding")
        void shouldInitializeNullFieldsBeforeAdding() { // GH-90000
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1")
                    .name("col")
                    .fields(null) // GH-90000
                    .build(); // GH-90000
            FieldDefinition field = FieldDefinition.builder().name("tag").build();

            col.addField(field); // GH-90000

            assertThat(col.getField("tag")).isPresent();
        }
    }

    @Nested
    @DisplayName("getPartitionCount()")
    class GetPartitionCountTests {

        @Test
        @DisplayName("should return 1 when eventConfig is null")
        void shouldReturnOneWhenEventConfigNull() { // GH-90000
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1")
                    .name("col")
                    .eventConfig(null) // GH-90000
                    .build(); // GH-90000

            assertThat(col.getPartitionCount()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("should return 1 when eventConfig has null partitionCount")
        void shouldReturnOneWhenPartitionCountNull() { // GH-90000
            EventConfig config = EventConfig.builder().build(); // GH-90000
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1")
                    .name("col")
                    .eventConfig(config) // GH-90000
                    .build(); // GH-90000

            // Default EventConfig.partitionCount may be null if not set as @Builder.Default
            // If it provides a default, test that; if null, result is 1
            if (config.getPartitionCount() == null) { // GH-90000
                assertThat(col.getPartitionCount()).isEqualTo(1); // GH-90000
            } else {
                assertThat(col.getPartitionCount()).isEqualTo(config.getPartitionCount()); // GH-90000
            }
        }

        @Test
        @DisplayName("should return actual partition count from eventConfig")
        void shouldReturnPartitionCountFromEventConfig() { // GH-90000
            EventConfig config = EventConfig.hashPartitioned("tenant-id", 8); // GH-90000
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1")
                    .name("col")
                    .eventConfig(config) // GH-90000
                    .build(); // GH-90000

            assertThat(col.getPartitionCount()).isEqualTo(8); // GH-90000
        }
    }

    @Nested
    @DisplayName("Builder defaults")
    class BuilderDefaultTests {

        @Test
        @DisplayName("should set default recordType to ENTITY")
        void shouldDefaultRecordTypeToEntity() { // GH-90000
            Collection col = Collection.builder().tenantId("t1").name("col").build();
            assertThat(col.getRecordType()).isEqualTo(RecordType.ENTITY); // GH-90000
        }

        @Test
        @DisplayName("should set default storageProfile to 'default'")
        void shouldDefaultStorageProfile() { // GH-90000
            Collection col = Collection.builder().tenantId("t1").name("col").build();
            assertThat(col.getStorageProfile()).isEqualTo("default");
        }

        @Test
        @DisplayName("should set default schemaVersion to '1.0.0'")
        void shouldDefaultSchemaVersion() { // GH-90000
            Collection col = Collection.builder().tenantId("t1").name("col").build();
            assertThat(col.getSchemaVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("should set default version to 1")
        void shouldDefaultVersionToOne() { // GH-90000
            Collection col = Collection.builder().tenantId("t1").name("col").build();
            assertThat(col.getVersion()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("should set default active to true")
        void shouldDefaultActiveToTrue() { // GH-90000
            Collection col = Collection.builder().tenantId("t1").name("col").build();
            assertThat(col.getActive()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("supportsOperation()")
    class SupportsOperationTests {

        @Test
        @DisplayName("ENTITY collection should support CREATE and READ")
        void entityShouldSupportCreateAndRead() { // GH-90000
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1")
                    .name("col")
                    .recordType(RecordType.ENTITY) // GH-90000
                    .build(); // GH-90000

            assertThat(col.supportsOperation(DataRecord.RecordOperation.CREATE)).isTrue(); // GH-90000
            assertThat(col.supportsOperation(DataRecord.RecordOperation.READ)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("ENTITY collection should support UPDATE and DELETE (mutable)")
        void entityShouldSupportUpdateAndDelete() { // GH-90000
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1")
                    .name("col")
                    .recordType(RecordType.ENTITY) // GH-90000
                    .build(); // GH-90000

            assertThat(col.supportsOperation(DataRecord.RecordOperation.UPDATE)).isTrue(); // GH-90000
            assertThat(col.supportsOperation(DataRecord.RecordOperation.DELETE)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("EVENT collection should support APPEND and SUBSCRIBE")
        void eventShouldSupportAppendAndSubscribe() { // GH-90000
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1")
                    .name("col")
                    .recordType(RecordType.EVENT) // GH-90000
                    .build(); // GH-90000

            assertThat(col.supportsOperation(DataRecord.RecordOperation.APPEND)).isTrue(); // GH-90000
            assertThat(col.supportsOperation(DataRecord.RecordOperation.SUBSCRIBE)).isTrue(); // GH-90000
        }
    }

    // ==================== Helpers ====================

    private static void setId(Collection col, UUID id) { // GH-90000
        try {
            var field = Collection.class.getDeclaredField("id");
            field.setAccessible(true); // GH-90000
            field.set(col, id); // GH-90000
        } catch (NoSuchFieldException | IllegalAccessException e) { // GH-90000
            throw new RuntimeException("Failed to set Collection.id via reflection", e); // GH-90000
        }
    }
}
