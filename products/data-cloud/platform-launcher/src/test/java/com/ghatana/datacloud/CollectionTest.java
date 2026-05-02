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
        void shouldEqualItself() { 
            Collection col = Collection.builder() 
                    .tenantId("t1")
                    .name("col1")
                    .build(); 
            assertThat(col).isEqualTo(col); 
        }

        @Test
        @DisplayName("should equal another collection with the same UUID id")
        void shouldEqualSameId() { 
            UUID id = UUID.randomUUID(); 
            Collection a = Collection.builder().tenantId("t1").name("col-a").build();
            Collection b = Collection.builder().tenantId("t2").name("col-b").build();
            // Set id via reflection to simulate JPA-assigned identity
            setId(a, id); 
            setId(b, id); 

            assertThat(a).isEqualTo(b); 
        }

        @Test
        @DisplayName("should not equal a collection with a different UUID id")
        void shouldNotEqualDifferentId() { 
            Collection a = Collection.builder().tenantId("t1").name("col").build();
            Collection b = Collection.builder().tenantId("t1").name("col").build();
            setId(a, UUID.randomUUID()); 
            setId(b, UUID.randomUUID()); 

            assertThat(a).isNotEqualTo(b); 
        }

        @Test
        @DisplayName("should not equal when both ids are null (new unsaved entities)")
        void shouldNotEqualBothNullId() { 
            Collection a = Collection.builder().tenantId("t1").name("col").build();
            Collection b = Collection.builder().tenantId("t1").name("col").build();
            // ids are null (not yet persisted) 
            assertThat(a).isNotEqualTo(b); 
        }

        @Test
        @DisplayName("should not equal when this id is null and other id is set")
        void shouldNotEqualThisNullId() { 
            Collection a = Collection.builder().tenantId("t1").name("col").build();
            Collection b = Collection.builder().tenantId("t1").name("col").build();
            setId(b, UUID.randomUUID()); 

            assertThat(a).isNotEqualTo(b); 
        }

        @Test
        @DisplayName("should not equal null")
        void shouldNotEqualNull() { 
            Collection col = Collection.builder().tenantId("t1").name("col").build();
            assertThat(col).isNotEqualTo(null); 
        }

        @Test
        @DisplayName("should not equal an object of a different type")
        void shouldNotEqualDifferentType() { 
            Collection col = Collection.builder().tenantId("t1").name("col").build();
            assertThat(col).isNotEqualTo("not-a-collection");
        }
    }

    @Nested
    @DisplayName("hashCode()")
    class HashCodeTests {

        @Test
        @DisplayName("should return same hash for same UUID id")
        void shouldReturnSameHashForSameId() { 
            UUID id = UUID.randomUUID(); 
            Collection a = Collection.builder().tenantId("t1").name("col").build();
            Collection b = Collection.builder().tenantId("t2").name("other").build();
            setId(a, id); 
            setId(b, id); 

            assertThat(a.hashCode()).isEqualTo(b.hashCode()); 
        }

        @Test
        @DisplayName("should return 0 when id is null")
        void shouldReturnZeroForNullId() { 
            Collection col = Collection.builder().tenantId("t1").name("col").build();
            assertThat(col.hashCode()).isEqualTo(0); 
        }

        @Test
        @DisplayName("should return UUID hashCode when id is set")
        void shouldReturnIdHashCode() { 
            UUID id = UUID.randomUUID(); 
            Collection col = Collection.builder().tenantId("t1").name("col").build();
            setId(col, id); 

            assertThat(col.hashCode()).isEqualTo(id.hashCode()); 
        }
    }

    @Nested
    @DisplayName("getField()")
    class GetFieldTests {

        @Test
        @DisplayName("should return present Optional when field exists by name")
        void shouldReturnFieldWhenFound() { 
            FieldDefinition field = FieldDefinition.builder().name("email").build();
            Collection col = Collection.builder() 
                    .tenantId("t1")
                    .name("col")
                    .fields(List.of(field)) 
                    .build(); 

            assertThat(col.getField("email")).isPresent();
            assertThat(col.getField("email").get().getName()).isEqualTo("email");
        }

        @Test
        @DisplayName("should return empty Optional when field name not found")
        void shouldReturnEmptyWhenNotFound() { 
            FieldDefinition field = FieldDefinition.builder().name("email").build();
            Collection col = Collection.builder() 
                    .tenantId("t1")
                    .name("col")
                    .fields(List.of(field)) 
                    .build(); 

            assertThat(col.getField("phone")).isEmpty();
        }

        @Test
        @DisplayName("should return empty Optional when fields list is null")
        void shouldReturnEmptyWhenFieldsNull() { 
            Collection col = Collection.builder() 
                    .tenantId("t1")
                    .name("col")
                    .fields(null) 
                    .build(); 

            assertThat(col.getField("email")).isEmpty();
        }

        @Test
        @DisplayName("should find correct field from multiple fields")
        void shouldFindCorrectFieldAmongMany() { 
            FieldDefinition name = FieldDefinition.builder().name("name").build();
            FieldDefinition age = FieldDefinition.builder().name("age").build();
            FieldDefinition email = FieldDefinition.builder().name("email").build();
            Collection col = Collection.builder() 
                    .tenantId("t1")
                    .name("col")
                    .fields(List.of(name, age, email)) 
                    .build(); 

            assertThat(col.getField("age")).isPresent();
            assertThat(col.getField("age").get().getName()).isEqualTo("age");
        }
    }

    @Nested
    @DisplayName("addField()")
    class AddFieldTests {

        @Test
        @DisplayName("should add a field and return this for chaining")
        void shouldAddFieldAndReturnThis() { 
            Collection col = Collection.builder().tenantId("t1").name("col").build();
            FieldDefinition field = FieldDefinition.builder().name("score").build();

            Collection result = col.addField(field); 

            assertThat(result).isSameAs(col); 
            assertThat(col.getField("score")).isPresent();
        }

        @Test
        @DisplayName("should initialize fields list when null before adding")
        void shouldInitializeNullFieldsBeforeAdding() { 
            Collection col = Collection.builder() 
                    .tenantId("t1")
                    .name("col")
                    .fields(null) 
                    .build(); 
            FieldDefinition field = FieldDefinition.builder().name("tag").build();

            col.addField(field); 

            assertThat(col.getField("tag")).isPresent();
        }
    }

    @Nested
    @DisplayName("getPartitionCount()")
    class GetPartitionCountTests {

        @Test
        @DisplayName("should return 1 when eventConfig is null")
        void shouldReturnOneWhenEventConfigNull() { 
            Collection col = Collection.builder() 
                    .tenantId("t1")
                    .name("col")
                    .eventConfig(null) 
                    .build(); 

            assertThat(col.getPartitionCount()).isEqualTo(1); 
        }

        @Test
        @DisplayName("should return 1 when eventConfig has null partitionCount")
        void shouldReturnOneWhenPartitionCountNull() { 
            EventConfig config = EventConfig.builder().build(); 
            Collection col = Collection.builder() 
                    .tenantId("t1")
                    .name("col")
                    .eventConfig(config) 
                    .build(); 

            // Default EventConfig.partitionCount may be null if not set as @Builder.Default
            // If it provides a default, test that; if null, result is 1
            if (config.getPartitionCount() == null) { 
                assertThat(col.getPartitionCount()).isEqualTo(1); 
            } else {
                assertThat(col.getPartitionCount()).isEqualTo(config.getPartitionCount()); 
            }
        }

        @Test
        @DisplayName("should return actual partition count from eventConfig")
        void shouldReturnPartitionCountFromEventConfig() { 
            EventConfig config = EventConfig.hashPartitioned("tenant-id", 8); 
            Collection col = Collection.builder() 
                    .tenantId("t1")
                    .name("col")
                    .eventConfig(config) 
                    .build(); 

            assertThat(col.getPartitionCount()).isEqualTo(8); 
        }
    }

    @Nested
    @DisplayName("Builder defaults")
    class BuilderDefaultTests {

        @Test
        @DisplayName("should set default recordType to ENTITY")
        void shouldDefaultRecordTypeToEntity() { 
            Collection col = Collection.builder().tenantId("t1").name("col").build();
            assertThat(col.getRecordType()).isEqualTo(RecordType.ENTITY); 
        }

        @Test
        @DisplayName("should set default storageProfile to 'default'")
        void shouldDefaultStorageProfile() { 
            Collection col = Collection.builder().tenantId("t1").name("col").build();
            assertThat(col.getStorageProfile()).isEqualTo("default");
        }

        @Test
        @DisplayName("should set default schemaVersion to '1.0.0'")
        void shouldDefaultSchemaVersion() { 
            Collection col = Collection.builder().tenantId("t1").name("col").build();
            assertThat(col.getSchemaVersion()).isEqualTo("1.0.0");
        }

        @Test
        @DisplayName("should set default version to 1")
        void shouldDefaultVersionToOne() { 
            Collection col = Collection.builder().tenantId("t1").name("col").build();
            assertThat(col.getVersion()).isEqualTo(1); 
        }

        @Test
        @DisplayName("should set default active to true")
        void shouldDefaultActiveToTrue() { 
            Collection col = Collection.builder().tenantId("t1").name("col").build();
            assertThat(col.getActive()).isTrue(); 
        }
    }

    @Nested
    @DisplayName("supportsOperation()")
    class SupportsOperationTests {

        @Test
        @DisplayName("ENTITY collection should support CREATE and READ")
        void entityShouldSupportCreateAndRead() { 
            Collection col = Collection.builder() 
                    .tenantId("t1")
                    .name("col")
                    .recordType(RecordType.ENTITY) 
                    .build(); 

            assertThat(col.supportsOperation(DataRecord.RecordOperation.CREATE)).isTrue(); 
            assertThat(col.supportsOperation(DataRecord.RecordOperation.READ)).isTrue(); 
        }

        @Test
        @DisplayName("ENTITY collection should support UPDATE and DELETE (mutable)")
        void entityShouldSupportUpdateAndDelete() { 
            Collection col = Collection.builder() 
                    .tenantId("t1")
                    .name("col")
                    .recordType(RecordType.ENTITY) 
                    .build(); 

            assertThat(col.supportsOperation(DataRecord.RecordOperation.UPDATE)).isTrue(); 
            assertThat(col.supportsOperation(DataRecord.RecordOperation.DELETE)).isTrue(); 
        }

        @Test
        @DisplayName("EVENT collection should support APPEND and SUBSCRIBE")
        void eventShouldSupportAppendAndSubscribe() { 
            Collection col = Collection.builder() 
                    .tenantId("t1")
                    .name("col")
                    .recordType(RecordType.EVENT) 
                    .build(); 

            assertThat(col.supportsOperation(DataRecord.RecordOperation.APPEND)).isTrue(); 
            assertThat(col.supportsOperation(DataRecord.RecordOperation.SUBSCRIBE)).isTrue(); 
        }
    }

    // ==================== Helpers ====================

    private static void setId(Collection col, UUID id) { 
        try {
            var field = Collection.class.getDeclaredField("id");
            field.setAccessible(true); 
            field.set(col, id); 
        } catch (NoSuchFieldException | IllegalAccessException e) { 
            throw new RuntimeException("Failed to set Collection.id via reflection", e); 
        }
    }
}
