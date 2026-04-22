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
@DisplayName("Collection entity [GH-90000]")
class CollectionTest {

    @Nested
    @DisplayName("equals() [GH-90000]")
    class EqualsTests {

        @Test
        @DisplayName("should equal itself (reflexive) [GH-90000]")
        void shouldEqualItself() { // GH-90000
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .name("col1 [GH-90000]")
                    .build(); // GH-90000
            assertThat(col).isEqualTo(col); // GH-90000
        }

        @Test
        @DisplayName("should equal another collection with the same UUID id [GH-90000]")
        void shouldEqualSameId() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Collection a = Collection.builder().tenantId("t1 [GH-90000]").name("col-a [GH-90000]").build();
            Collection b = Collection.builder().tenantId("t2 [GH-90000]").name("col-b [GH-90000]").build();
            // Set id via reflection to simulate JPA-assigned identity
            setId(a, id); // GH-90000
            setId(b, id); // GH-90000

            assertThat(a).isEqualTo(b); // GH-90000
        }

        @Test
        @DisplayName("should not equal a collection with a different UUID id [GH-90000]")
        void shouldNotEqualDifferentId() { // GH-90000
            Collection a = Collection.builder().tenantId("t1 [GH-90000]").name("col [GH-90000]").build();
            Collection b = Collection.builder().tenantId("t1 [GH-90000]").name("col [GH-90000]").build();
            setId(a, UUID.randomUUID()); // GH-90000
            setId(b, UUID.randomUUID()); // GH-90000

            assertThat(a).isNotEqualTo(b); // GH-90000
        }

        @Test
        @DisplayName("should not equal when both ids are null (new unsaved entities) [GH-90000]")
        void shouldNotEqualBothNullId() { // GH-90000
            Collection a = Collection.builder().tenantId("t1 [GH-90000]").name("col [GH-90000]").build();
            Collection b = Collection.builder().tenantId("t1 [GH-90000]").name("col [GH-90000]").build();
            // ids are null (not yet persisted) // GH-90000
            assertThat(a).isNotEqualTo(b); // GH-90000
        }

        @Test
        @DisplayName("should not equal when this id is null and other id is set [GH-90000]")
        void shouldNotEqualThisNullId() { // GH-90000
            Collection a = Collection.builder().tenantId("t1 [GH-90000]").name("col [GH-90000]").build();
            Collection b = Collection.builder().tenantId("t1 [GH-90000]").name("col [GH-90000]").build();
            setId(b, UUID.randomUUID()); // GH-90000

            assertThat(a).isNotEqualTo(b); // GH-90000
        }

        @Test
        @DisplayName("should not equal null [GH-90000]")
        void shouldNotEqualNull() { // GH-90000
            Collection col = Collection.builder().tenantId("t1 [GH-90000]").name("col [GH-90000]").build();
            assertThat(col).isNotEqualTo(null); // GH-90000
        }

        @Test
        @DisplayName("should not equal an object of a different type [GH-90000]")
        void shouldNotEqualDifferentType() { // GH-90000
            Collection col = Collection.builder().tenantId("t1 [GH-90000]").name("col [GH-90000]").build();
            assertThat(col).isNotEqualTo("not-a-collection [GH-90000]");
        }
    }

    @Nested
    @DisplayName("hashCode() [GH-90000]")
    class HashCodeTests {

        @Test
        @DisplayName("should return same hash for same UUID id [GH-90000]")
        void shouldReturnSameHashForSameId() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Collection a = Collection.builder().tenantId("t1 [GH-90000]").name("col [GH-90000]").build();
            Collection b = Collection.builder().tenantId("t2 [GH-90000]").name("other [GH-90000]").build();
            setId(a, id); // GH-90000
            setId(b, id); // GH-90000

            assertThat(a.hashCode()).isEqualTo(b.hashCode()); // GH-90000
        }

        @Test
        @DisplayName("should return 0 when id is null [GH-90000]")
        void shouldReturnZeroForNullId() { // GH-90000
            Collection col = Collection.builder().tenantId("t1 [GH-90000]").name("col [GH-90000]").build();
            assertThat(col.hashCode()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("should return UUID hashCode when id is set [GH-90000]")
        void shouldReturnIdHashCode() { // GH-90000
            UUID id = UUID.randomUUID(); // GH-90000
            Collection col = Collection.builder().tenantId("t1 [GH-90000]").name("col [GH-90000]").build();
            setId(col, id); // GH-90000

            assertThat(col.hashCode()).isEqualTo(id.hashCode()); // GH-90000
        }
    }

    @Nested
    @DisplayName("getField() [GH-90000]")
    class GetFieldTests {

        @Test
        @DisplayName("should return present Optional when field exists by name [GH-90000]")
        void shouldReturnFieldWhenFound() { // GH-90000
            FieldDefinition field = FieldDefinition.builder().name("email [GH-90000]").build();
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .name("col [GH-90000]")
                    .fields(List.of(field)) // GH-90000
                    .build(); // GH-90000

            assertThat(col.getField("email [GH-90000]")).isPresent();
            assertThat(col.getField("email [GH-90000]").get().getName()).isEqualTo("email [GH-90000]");
        }

        @Test
        @DisplayName("should return empty Optional when field name not found [GH-90000]")
        void shouldReturnEmptyWhenNotFound() { // GH-90000
            FieldDefinition field = FieldDefinition.builder().name("email [GH-90000]").build();
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .name("col [GH-90000]")
                    .fields(List.of(field)) // GH-90000
                    .build(); // GH-90000

            assertThat(col.getField("phone [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("should return empty Optional when fields list is null [GH-90000]")
        void shouldReturnEmptyWhenFieldsNull() { // GH-90000
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .name("col [GH-90000]")
                    .fields(null) // GH-90000
                    .build(); // GH-90000

            assertThat(col.getField("email [GH-90000]")).isEmpty();
        }

        @Test
        @DisplayName("should find correct field from multiple fields [GH-90000]")
        void shouldFindCorrectFieldAmongMany() { // GH-90000
            FieldDefinition name = FieldDefinition.builder().name("name [GH-90000]").build();
            FieldDefinition age = FieldDefinition.builder().name("age [GH-90000]").build();
            FieldDefinition email = FieldDefinition.builder().name("email [GH-90000]").build();
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .name("col [GH-90000]")
                    .fields(List.of(name, age, email)) // GH-90000
                    .build(); // GH-90000

            assertThat(col.getField("age [GH-90000]")).isPresent();
            assertThat(col.getField("age [GH-90000]").get().getName()).isEqualTo("age [GH-90000]");
        }
    }

    @Nested
    @DisplayName("addField() [GH-90000]")
    class AddFieldTests {

        @Test
        @DisplayName("should add a field and return this for chaining [GH-90000]")
        void shouldAddFieldAndReturnThis() { // GH-90000
            Collection col = Collection.builder().tenantId("t1 [GH-90000]").name("col [GH-90000]").build();
            FieldDefinition field = FieldDefinition.builder().name("score [GH-90000]").build();

            Collection result = col.addField(field); // GH-90000

            assertThat(result).isSameAs(col); // GH-90000
            assertThat(col.getField("score [GH-90000]")).isPresent();
        }

        @Test
        @DisplayName("should initialize fields list when null before adding [GH-90000]")
        void shouldInitializeNullFieldsBeforeAdding() { // GH-90000
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .name("col [GH-90000]")
                    .fields(null) // GH-90000
                    .build(); // GH-90000
            FieldDefinition field = FieldDefinition.builder().name("tag [GH-90000]").build();

            col.addField(field); // GH-90000

            assertThat(col.getField("tag [GH-90000]")).isPresent();
        }
    }

    @Nested
    @DisplayName("getPartitionCount() [GH-90000]")
    class GetPartitionCountTests {

        @Test
        @DisplayName("should return 1 when eventConfig is null [GH-90000]")
        void shouldReturnOneWhenEventConfigNull() { // GH-90000
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .name("col [GH-90000]")
                    .eventConfig(null) // GH-90000
                    .build(); // GH-90000

            assertThat(col.getPartitionCount()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("should return 1 when eventConfig has null partitionCount [GH-90000]")
        void shouldReturnOneWhenPartitionCountNull() { // GH-90000
            EventConfig config = EventConfig.builder().build(); // GH-90000
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .name("col [GH-90000]")
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
        @DisplayName("should return actual partition count from eventConfig [GH-90000]")
        void shouldReturnPartitionCountFromEventConfig() { // GH-90000
            EventConfig config = EventConfig.hashPartitioned("tenant-id", 8); // GH-90000
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .name("col [GH-90000]")
                    .eventConfig(config) // GH-90000
                    .build(); // GH-90000

            assertThat(col.getPartitionCount()).isEqualTo(8); // GH-90000
        }
    }

    @Nested
    @DisplayName("Builder defaults [GH-90000]")
    class BuilderDefaultTests {

        @Test
        @DisplayName("should set default recordType to ENTITY [GH-90000]")
        void shouldDefaultRecordTypeToEntity() { // GH-90000
            Collection col = Collection.builder().tenantId("t1 [GH-90000]").name("col [GH-90000]").build();
            assertThat(col.getRecordType()).isEqualTo(RecordType.ENTITY); // GH-90000
        }

        @Test
        @DisplayName("should set default storageProfile to 'default' [GH-90000]")
        void shouldDefaultStorageProfile() { // GH-90000
            Collection col = Collection.builder().tenantId("t1 [GH-90000]").name("col [GH-90000]").build();
            assertThat(col.getStorageProfile()).isEqualTo("default [GH-90000]");
        }

        @Test
        @DisplayName("should set default schemaVersion to '1.0.0' [GH-90000]")
        void shouldDefaultSchemaVersion() { // GH-90000
            Collection col = Collection.builder().tenantId("t1 [GH-90000]").name("col [GH-90000]").build();
            assertThat(col.getSchemaVersion()).isEqualTo("1.0.0 [GH-90000]");
        }

        @Test
        @DisplayName("should set default version to 1 [GH-90000]")
        void shouldDefaultVersionToOne() { // GH-90000
            Collection col = Collection.builder().tenantId("t1 [GH-90000]").name("col [GH-90000]").build();
            assertThat(col.getVersion()).isEqualTo(1); // GH-90000
        }

        @Test
        @DisplayName("should set default active to true [GH-90000]")
        void shouldDefaultActiveToTrue() { // GH-90000
            Collection col = Collection.builder().tenantId("t1 [GH-90000]").name("col [GH-90000]").build();
            assertThat(col.getActive()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("supportsOperation() [GH-90000]")
    class SupportsOperationTests {

        @Test
        @DisplayName("ENTITY collection should support CREATE and READ [GH-90000]")
        void entityShouldSupportCreateAndRead() { // GH-90000
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .name("col [GH-90000]")
                    .recordType(RecordType.ENTITY) // GH-90000
                    .build(); // GH-90000

            assertThat(col.supportsOperation(DataRecord.RecordOperation.CREATE)).isTrue(); // GH-90000
            assertThat(col.supportsOperation(DataRecord.RecordOperation.READ)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("ENTITY collection should support UPDATE and DELETE (mutable) [GH-90000]")
        void entityShouldSupportUpdateAndDelete() { // GH-90000
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .name("col [GH-90000]")
                    .recordType(RecordType.ENTITY) // GH-90000
                    .build(); // GH-90000

            assertThat(col.supportsOperation(DataRecord.RecordOperation.UPDATE)).isTrue(); // GH-90000
            assertThat(col.supportsOperation(DataRecord.RecordOperation.DELETE)).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("EVENT collection should support APPEND and SUBSCRIBE [GH-90000]")
        void eventShouldSupportAppendAndSubscribe() { // GH-90000
            Collection col = Collection.builder() // GH-90000
                    .tenantId("t1 [GH-90000]")
                    .name("col [GH-90000]")
                    .recordType(RecordType.EVENT) // GH-90000
                    .build(); // GH-90000

            assertThat(col.supportsOperation(DataRecord.RecordOperation.APPEND)).isTrue(); // GH-90000
            assertThat(col.supportsOperation(DataRecord.RecordOperation.SUBSCRIBE)).isTrue(); // GH-90000
        }
    }

    // ==================== Helpers ====================

    private static void setId(Collection col, UUID id) { // GH-90000
        try {
            var field = Collection.class.getDeclaredField("id [GH-90000]");
            field.setAccessible(true); // GH-90000
            field.set(col, id); // GH-90000
        } catch (NoSuchFieldException | IllegalAccessException e) { // GH-90000
            throw new RuntimeException("Failed to set Collection.id via reflection", e); // GH-90000
        }
    }
}
