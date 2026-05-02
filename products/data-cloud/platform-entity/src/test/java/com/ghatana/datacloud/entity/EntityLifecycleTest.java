package com.ghatana.datacloud.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for entity lifecycle operations including creation, updates, and state transitions.
 *
 * @doc.type test
 * @doc.purpose Entity lifecycle validation tests
 * @doc.layer domain
 * @doc.pattern Test
 */
@DisplayName("Entity Lifecycle Tests")
class EntityLifecycleTest {

    // =========================================================================
    // META FIELD LIFECYCLE
    // =========================================================================

    @Nested
    @DisplayName("MetaField lifecycle")
    class MetaFieldLifecycle {

        @Test
        @DisplayName("should create field with builder")
        void shouldCreateFieldWithBuilder() { 
            UUID testId = UUID.randomUUID(); 
            UUID collectionId = UUID.randomUUID(); 
            
            MetaField field = MetaField.builder() 
                .id(testId) 
                .collectionId(collectionId) 
                .name("price")
                .type(DataType.NUMBER) 
                .label("Price")
                .required(true) 
                .uniqueConstraint(false) 
                .displayOrder(1) 
                .active(true) 
                .build(); 

            assertThat(field.getName()).isEqualTo("price");
            assertThat(field.getType()).isEqualTo(DataType.NUMBER); 
            assertThat(field.getLabel()).isEqualTo("Price");
            assertThat(field.getRequired()).isTrue(); 
            assertThat(field.getUniqueConstraint()).isFalse(); 
            assertThat(field.getDisplayOrder()).isEqualTo(1); 
            assertThat(field.getActive()).isTrue(); 
        }

        @Test
        @DisplayName("should create field with reference")
        void shouldCreateFieldWithReference() { 
            MetaField field = MetaField.builder() 
                .name("categoryId")
                .type(DataType.REFERENCE) 
                .referenceCollection("categories")
                .build(); 

            assertThat(field.getType()).isEqualTo(DataType.REFERENCE); 
            assertThat(field.getReferenceCollection()).isEqualTo("categories");
        }

        @Test
        @DisplayName("should handle default value")
        void shouldHandleDefaultValue() { 
            MetaField field = MetaField.builder() 
                .name("status")
                .type(DataType.STRING) 
                .defaultValue("active")
                .build(); 

            assertThat(field.getDefaultValue()).isEqualTo("active");
        }

        @Test
        @DisplayName("should handle display order")
        void shouldHandleDisplayOrder() { 
            MetaField field1 = MetaField.builder().name("field1").displayOrder(1).build();
            MetaField field2 = MetaField.builder().name("field2").displayOrder(2).build();

            assertThat(field1.getDisplayOrder()).isLessThan(field2.getDisplayOrder()); 
        }

        @Test
        @DisplayName("should handle required field flag")
        void shouldHandleRequiredFieldFlag() { 
            MetaField requiredField = MetaField.builder() 
                .name("email")
                .type(DataType.STRING) 
                .required(true) 
                .build(); 

            MetaField optionalField = MetaField.builder() 
                .name("nickname")
                .type(DataType.STRING) 
                .required(false) 
                .build(); 

            assertThat(requiredField.getRequired()).isTrue(); 
            assertThat(optionalField.getRequired()).isFalse(); 
        }

        @Test
        @DisplayName("should handle unique constraint flag")
        void shouldHandleUniqueConstraintFlag() { 
            MetaField uniqueField = MetaField.builder() 
                .name("email")
                .type(DataType.STRING) 
                .uniqueConstraint(true) 
                .build(); 

            MetaField nonUniqueField = MetaField.builder() 
                .name("name")
                .type(DataType.STRING) 
                .uniqueConstraint(false) 
                .build(); 

            assertThat(uniqueField.getUniqueConstraint()).isTrue(); 
            assertThat(nonUniqueField.getUniqueConstraint()).isFalse(); 
        }
    }

    // =========================================================================
    // DATA TYPE COVERAGE
    // =========================================================================

    @Nested
    @DisplayName("DataType coverage")
    class DataTypeCoverage {

        @Test
        @DisplayName("should support all primitive types")
        void shouldSupportPrimitiveTypes() { 
            assertThat(DataType.STRING).isNotNull(); 
            assertThat(DataType.NUMBER).isNotNull(); 
            assertThat(DataType.BOOLEAN).isNotNull(); 
            assertThat(DataType.DATE).isNotNull(); 
            assertThat(DataType.DATETIME).isNotNull(); 
            assertThat(DataType.TIME).isNotNull(); 
        }

        @Test
        @DisplayName("should support complex types")
        void shouldSupportComplexTypes() { 
            assertThat(DataType.ARRAY).isNotNull(); 
            assertThat(DataType.EMBEDDED).isNotNull(); 
            assertThat(DataType.JSON).isNotNull(); 
            assertThat(DataType.REFERENCE).isNotNull(); 
        }

        @Test
        @DisplayName("should support specialized types")
        void shouldSupportSpecializedTypes() { 
            assertThat(DataType.EMAIL).isNotNull(); 
            assertThat(DataType.URL).isNotNull(); 
            assertThat(DataType.PHONE).isNotNull(); 
            assertThat(DataType.UUID).isNotNull(); 
            assertThat(DataType.ENUM).isNotNull(); 
        }

        @Test
        @DisplayName("should support media types")
        void shouldSupportMediaTypes() { 
            assertThat(DataType.IMAGE).isNotNull(); 
            assertThat(DataType.FILE).isNotNull(); 
            assertThat(DataType.RICHTEXT).isNotNull(); 
            assertThat(DataType.MARKDOWN).isNotNull(); 
        }

        @Test
        @DisplayName("should support geographic and spatial types")
        void shouldSupportGeographicTypes() { 
            assertThat(DataType.GEOLOCATION).isNotNull(); 
            assertThat(DataType.COLOR).isNotNull(); 
        }

        @Test
        @DisplayName("should support business types")
        void shouldSupportBusinessTypes() { 
            assertThat(DataType.CURRENCY).isNotNull(); 
            assertThat(DataType.PERCENTAGE).isNotNull(); 
            assertThat(DataType.RATING).isNotNull(); 
            assertThat(DataType.TAGS).isNotNull(); 
        }
    }

    // =========================================================================
    // EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle null values in optional fields")
        void shouldHandleNullInOptionalFields() { 
            MetaField field = MetaField.builder() 
                .name("optional")
                .type(DataType.STRING) 
                .required(false) 
                .label(null) 
                .defaultValue(null) 
                .build(); 

            assertThat(field.getLabel()).isNull(); 
            assertThat(field.getDefaultValue()).isNull(); 
        }

        @Test
        @DisplayName("should handle zero display order")
        void shouldHandleZeroDisplayOrder() { 
            MetaField field = MetaField.builder() 
                .name("first")
                .displayOrder(0) 
                .build(); 

            assertThat(field.getDisplayOrder()).isEqualTo(0); 
        }

        @Test
        @DisplayName("should handle negative display order")
        void shouldHandleNegativeDisplayOrder() { 
            MetaField field = MetaField.builder() 
                .name("negative")
                .displayOrder(-1) 
                .build(); 

            assertThat(field.getDisplayOrder()).isEqualTo(-1); 
        }

        @Test
        @DisplayName("should handle field with all optional parameters")
        void shouldHandleFieldWithAllOptionals() { 
            MetaField field = MetaField.builder() 
                .name("simple")
                .type(DataType.STRING) 
                .build(); 

            assertThat(field.getName()).isEqualTo("simple");
            assertThat(field.getType()).isEqualTo(DataType.STRING); 
            assertThat(field.getRequired()).isFalse(); 
            assertThat(field.getActive()).isTrue(); 
        }
    }
}
