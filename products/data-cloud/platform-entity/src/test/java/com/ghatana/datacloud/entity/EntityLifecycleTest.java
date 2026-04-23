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
        void shouldCreateFieldWithBuilder() { // GH-90000
            UUID testId = UUID.randomUUID(); // GH-90000
            UUID collectionId = UUID.randomUUID(); // GH-90000
            
            MetaField field = MetaField.builder() // GH-90000
                .id(testId) // GH-90000
                .collectionId(collectionId) // GH-90000
                .name("price")
                .type(DataType.NUMBER) // GH-90000
                .label("Price")
                .required(true) // GH-90000
                .uniqueConstraint(false) // GH-90000
                .displayOrder(1) // GH-90000
                .active(true) // GH-90000
                .build(); // GH-90000

            assertThat(field.getName()).isEqualTo("price");
            assertThat(field.getType()).isEqualTo(DataType.NUMBER); // GH-90000
            assertThat(field.getLabel()).isEqualTo("Price");
            assertThat(field.getRequired()).isTrue(); // GH-90000
            assertThat(field.getUniqueConstraint()).isFalse(); // GH-90000
            assertThat(field.getDisplayOrder()).isEqualTo(1); // GH-90000
            assertThat(field.getActive()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should create field with reference")
        void shouldCreateFieldWithReference() { // GH-90000
            MetaField field = MetaField.builder() // GH-90000
                .name("categoryId")
                .type(DataType.REFERENCE) // GH-90000
                .referenceCollection("categories")
                .build(); // GH-90000

            assertThat(field.getType()).isEqualTo(DataType.REFERENCE); // GH-90000
            assertThat(field.getReferenceCollection()).isEqualTo("categories");
        }

        @Test
        @DisplayName("should handle default value")
        void shouldHandleDefaultValue() { // GH-90000
            MetaField field = MetaField.builder() // GH-90000
                .name("status")
                .type(DataType.STRING) // GH-90000
                .defaultValue("active")
                .build(); // GH-90000

            assertThat(field.getDefaultValue()).isEqualTo("active");
        }

        @Test
        @DisplayName("should handle display order")
        void shouldHandleDisplayOrder() { // GH-90000
            MetaField field1 = MetaField.builder().name("field1").displayOrder(1).build();
            MetaField field2 = MetaField.builder().name("field2").displayOrder(2).build();

            assertThat(field1.getDisplayOrder()).isLessThan(field2.getDisplayOrder()); // GH-90000
        }

        @Test
        @DisplayName("should handle required field flag")
        void shouldHandleRequiredFieldFlag() { // GH-90000
            MetaField requiredField = MetaField.builder() // GH-90000
                .name("email")
                .type(DataType.STRING) // GH-90000
                .required(true) // GH-90000
                .build(); // GH-90000

            MetaField optionalField = MetaField.builder() // GH-90000
                .name("nickname")
                .type(DataType.STRING) // GH-90000
                .required(false) // GH-90000
                .build(); // GH-90000

            assertThat(requiredField.getRequired()).isTrue(); // GH-90000
            assertThat(optionalField.getRequired()).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should handle unique constraint flag")
        void shouldHandleUniqueConstraintFlag() { // GH-90000
            MetaField uniqueField = MetaField.builder() // GH-90000
                .name("email")
                .type(DataType.STRING) // GH-90000
                .uniqueConstraint(true) // GH-90000
                .build(); // GH-90000

            MetaField nonUniqueField = MetaField.builder() // GH-90000
                .name("name")
                .type(DataType.STRING) // GH-90000
                .uniqueConstraint(false) // GH-90000
                .build(); // GH-90000

            assertThat(uniqueField.getUniqueConstraint()).isTrue(); // GH-90000
            assertThat(nonUniqueField.getUniqueConstraint()).isFalse(); // GH-90000
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
        void shouldSupportPrimitiveTypes() { // GH-90000
            assertThat(DataType.STRING).isNotNull(); // GH-90000
            assertThat(DataType.NUMBER).isNotNull(); // GH-90000
            assertThat(DataType.BOOLEAN).isNotNull(); // GH-90000
            assertThat(DataType.DATE).isNotNull(); // GH-90000
            assertThat(DataType.DATETIME).isNotNull(); // GH-90000
            assertThat(DataType.TIME).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should support complex types")
        void shouldSupportComplexTypes() { // GH-90000
            assertThat(DataType.ARRAY).isNotNull(); // GH-90000
            assertThat(DataType.EMBEDDED).isNotNull(); // GH-90000
            assertThat(DataType.JSON).isNotNull(); // GH-90000
            assertThat(DataType.REFERENCE).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should support specialized types")
        void shouldSupportSpecializedTypes() { // GH-90000
            assertThat(DataType.EMAIL).isNotNull(); // GH-90000
            assertThat(DataType.URL).isNotNull(); // GH-90000
            assertThat(DataType.PHONE).isNotNull(); // GH-90000
            assertThat(DataType.UUID).isNotNull(); // GH-90000
            assertThat(DataType.ENUM).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should support media types")
        void shouldSupportMediaTypes() { // GH-90000
            assertThat(DataType.IMAGE).isNotNull(); // GH-90000
            assertThat(DataType.FILE).isNotNull(); // GH-90000
            assertThat(DataType.RICHTEXT).isNotNull(); // GH-90000
            assertThat(DataType.MARKDOWN).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should support geographic and spatial types")
        void shouldSupportGeographicTypes() { // GH-90000
            assertThat(DataType.GEOLOCATION).isNotNull(); // GH-90000
            assertThat(DataType.COLOR).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("should support business types")
        void shouldSupportBusinessTypes() { // GH-90000
            assertThat(DataType.CURRENCY).isNotNull(); // GH-90000
            assertThat(DataType.PERCENTAGE).isNotNull(); // GH-90000
            assertThat(DataType.RATING).isNotNull(); // GH-90000
            assertThat(DataType.TAGS).isNotNull(); // GH-90000
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
        void shouldHandleNullInOptionalFields() { // GH-90000
            MetaField field = MetaField.builder() // GH-90000
                .name("optional")
                .type(DataType.STRING) // GH-90000
                .required(false) // GH-90000
                .label(null) // GH-90000
                .defaultValue(null) // GH-90000
                .build(); // GH-90000

            assertThat(field.getLabel()).isNull(); // GH-90000
            assertThat(field.getDefaultValue()).isNull(); // GH-90000
        }

        @Test
        @DisplayName("should handle zero display order")
        void shouldHandleZeroDisplayOrder() { // GH-90000
            MetaField field = MetaField.builder() // GH-90000
                .name("first")
                .displayOrder(0) // GH-90000
                .build(); // GH-90000

            assertThat(field.getDisplayOrder()).isEqualTo(0); // GH-90000
        }

        @Test
        @DisplayName("should handle negative display order")
        void shouldHandleNegativeDisplayOrder() { // GH-90000
            MetaField field = MetaField.builder() // GH-90000
                .name("negative")
                .displayOrder(-1) // GH-90000
                .build(); // GH-90000

            assertThat(field.getDisplayOrder()).isEqualTo(-1); // GH-90000
        }

        @Test
        @DisplayName("should handle field with all optional parameters")
        void shouldHandleFieldWithAllOptionals() { // GH-90000
            MetaField field = MetaField.builder() // GH-90000
                .name("simple")
                .type(DataType.STRING) // GH-90000
                .build(); // GH-90000

            assertThat(field.getName()).isEqualTo("simple");
            assertThat(field.getType()).isEqualTo(DataType.STRING); // GH-90000
            assertThat(field.getRequired()).isFalse(); // GH-90000
            assertThat(field.getActive()).isTrue(); // GH-90000
        }
    }
}
