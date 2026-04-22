package com.ghatana.datacloud.entity;

import com.ghatana.datacloud.entity.validation.EntitySchemaValidator;
import com.ghatana.datacloud.entity.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive entity validation tests to increase coverage.
 * Tests entity validation, schema enforcement, type checking, and basic constraint validation.
 *
 * @doc.type test
 * @doc.purpose Comprehensive entity validation tests
 * @doc.layer domain
 * @doc.pattern Test
 */
@DisplayName("Entity Validation Tests")
class EntityValidationTest {

    private EntitySchemaValidator validator;

    @BeforeEach
    void setUp() {
        validator = EntitySchemaValidator.create(true); // strict mode
    }

    // =========================================================================
    // DATA TYPE VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Data type validation")
    class DataTypeValidation {

        @Test
        @DisplayName("should validate STRING type")
        void shouldValidateStringType() {
            validator.registerSchema("tenant-1", "users", List.of(
                MetaField.builder().name("username").type(DataType.STRING).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "users", Map.of("username", "john_doe"));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }

        @Test
        @DisplayName("should validate NUMBER type")
        void shouldValidateNumberType() {
            validator.registerSchema("tenant-1", "products", List.of(
                MetaField.builder().name("quantity").type(DataType.NUMBER).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "products", Map.of("quantity", 42));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }

        @Test
        @DisplayName("should validate CURRENCY type")
        void shouldValidateCurrencyType() {
            validator.registerSchema("tenant-1", "orders", List.of(
                MetaField.builder().name("total").type(DataType.CURRENCY).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "orders", Map.of("total", 99.99));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }

        @Test
        @DisplayName("should validate BOOLEAN type")
        void shouldValidateBooleanType() {
            validator.registerSchema("tenant-1", "users", List.of(
                MetaField.builder().name("active").type(DataType.BOOLEAN).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "users", Map.of("active", true));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }

        @Test
        @DisplayName("should validate DATETIME type")
        void shouldValidateDatetimeType() {
            validator.registerSchema("tenant-1", "events", List.of(
                MetaField.builder().name("occurredAt").type(DataType.DATETIME).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "events", Map.of("occurredAt", System.currentTimeMillis()));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }

        @Test
        @DisplayName("should validate JSON type")
        void shouldValidateJsonType() {
            validator.registerSchema("tenant-1", "configs", List.of(
                MetaField.builder().name("settings").type(DataType.JSON).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "configs", Map.of("settings", Map.of("key", "value")));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }

        @Test
        @DisplayName("should validate ARRAY type")
        void shouldValidateArrayType() {
            validator.registerSchema("tenant-1", "orders", List.of(
                MetaField.builder().name("items").type(DataType.ARRAY).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "orders", Map.of("items", List.of("item1", "item2")));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }

        @Test
        @DisplayName("should validate EMAIL type")
        void shouldValidateEmailType() {
            validator.registerSchema("tenant-1", "users", List.of(
                MetaField.builder().name("email").type(DataType.EMAIL).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "users", Map.of("email", "user@example.com"));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }

        @Test
        @DisplayName("should validate UUID type")
        void shouldValidateUuidType() {
            validator.registerSchema("tenant-1", "entities", List.of(
                MetaField.builder().name("entityId").type(DataType.UUID).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "entities", Map.of("entityId", "550e8400-e29b-41d4-a716-446655440000"));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }
    }

    // =========================================================================
    // REQUIRED FIELD VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Required field validation")
    class RequiredFieldValidation {

        @Test
        @DisplayName("should fail when required field is null")
        void shouldFailWhenRequiredFieldIsNull() {
            validator.registerSchema("tenant-1", "users", List.of(
                MetaField.builder().name("email").type(DataType.STRING).required(true).build()
            ));

            Map<String, Object> entity = new HashMap<>();
            entity.put("email", null);

            ValidationResult result = validator.validate("tenant-1", "users", entity);
            assertThat(result.state()).isEqualTo(ValidationResult.State.FAILURE);
            assertThat(result.violations()).anyMatch(v -> v.contains("email") && v.contains("required"));
        }

        @Test
        @DisplayName("should fail when required field is missing")
        void shouldFailWhenRequiredFieldIsMissing() {
            validator.registerSchema("tenant-1", "users", List.of(
                MetaField.builder().name("email").type(DataType.STRING).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "users", Map.of());
            assertThat(result.state()).isEqualTo(ValidationResult.State.FAILURE);
        }

        @Test
        @DisplayName("should succeed when optional field is missing")
        void shouldSucceedWhenOptionalFieldIsMissing() {
            validator.registerSchema("tenant-1", "users", List.of(
                MetaField.builder().name("nickname").type(DataType.STRING).required(false).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "users", Map.of());
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }

        @Test
        @DisplayName("should succeed when optional field is null")
        void shouldSucceedWhenOptionalFieldIsNull() {
            validator.registerSchema("tenant-1", "users", List.of(
                MetaField.builder().name("nickname").type(DataType.STRING).required(false).build()
            ));

            Map<String, Object> entity = new HashMap<>();
            entity.put("nickname", null);

            ValidationResult result = validator.validate("tenant-1", "users", entity);
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }
    }

    // =========================================================================
    // COMPLEX VALIDATION SCENARIOS
    // =========================================================================

    @Nested
    @DisplayName("Complex validation scenarios")
    class ComplexValidation {

        @Test
        @DisplayName("should validate multiple fields")
        void shouldValidateMultipleFields() {
            validator.registerSchema("tenant-1", "users", List.of(
                MetaField.builder().name("username").type(DataType.STRING).required(true).build(),
                MetaField.builder().name("email").type(DataType.EMAIL).required(true).build(),
                MetaField.builder().name("age").type(DataType.NUMBER).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "users", Map.of(
                "username", "john_doe",
                "email", "john@example.com",
                "age", 30
            ));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }

        @Test
        @DisplayName("should report all violations")
        void shouldReportAllViolations() {
            validator.registerSchema("tenant-1", "users", List.of(
                MetaField.builder().name("username").type(DataType.STRING).required(true).build(),
                MetaField.builder().name("email").type(DataType.EMAIL).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "users", Map.of());
            assertThat(result.state()).isEqualTo(ValidationResult.State.FAILURE);
            assertThat(result.violations()).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("should handle nested object validation")
        void shouldHandleNestedValidation() {
            validator.registerSchema("tenant-1", "orders", List.of(
                MetaField.builder().name("customer").type(DataType.EMBEDDED).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "orders", Map.of(
                "customer", Map.of("name", "John Doe", "id", "12345")
            ));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }

        @Test
        @DisplayName("should handle array field validation")
        void shouldHandleArrayValidation() {
            validator.registerSchema("tenant-1", "orders", List.of(
                MetaField.builder().name("items").type(DataType.ARRAY).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "orders", Map.of(
                "items", List.of("item1", "item2", "item3")
            ));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }

        @Test
        @DisplayName("should handle reference type")
        void shouldHandleReferenceType() {
            validator.registerSchema("tenant-1", "orders", List.of(
                MetaField.builder().name("customerId").type(DataType.REFERENCE).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "orders", Map.of(
                "customerId", "550e8400-e29b-41d4-a716-446655440000"
            ));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }
    }

    // =========================================================================
    // EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("should handle empty string for non-required field")
        void shouldHandleEmptyStringForOptional() {
            validator.registerSchema("tenant-1", "users", List.of(
                MetaField.builder().name("nickname").type(DataType.STRING).required(false).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "users", Map.of("nickname", ""));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }

        @Test
        @DisplayName("should handle zero value for numeric field")
        void shouldHandleZeroValue() {
            validator.registerSchema("tenant-1", "products", List.of(
                MetaField.builder().name("quantity").type(DataType.NUMBER).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "products", Map.of("quantity", 0));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }

        @Test
        @DisplayName("should handle false value for boolean field")
        void shouldHandleFalseValue() {
            validator.registerSchema("tenant-1", "users", List.of(
                MetaField.builder().name("active").type(DataType.BOOLEAN).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "users", Map.of("active", false));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }

        @Test
        @DisplayName("should handle empty array")
        void shouldHandleEmptyArray() {
            validator.registerSchema("tenant-1", "orders", List.of(
                MetaField.builder().name("items").type(DataType.ARRAY).required(false).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "orders", Map.of("items", List.of()));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }

        @Test
        @DisplayName("should handle empty object")
        void shouldHandleEmptyObject() {
            validator.registerSchema("tenant-1", "configs", List.of(
                MetaField.builder().name("metadata").type(DataType.JSON).required(false).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "configs", Map.of("metadata", Map.of()));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }

        @Test
        @DisplayName("should handle multiple tenants")
        void shouldHandleMultipleTenants() {
            validator.registerSchema("tenant-1", "users", List.of(
                MetaField.builder().name("username").type(DataType.STRING).required(true).build()
            ));
            validator.registerSchema("tenant-2", "users", List.of(
                MetaField.builder().name("username").type(DataType.STRING).required(true).build()
            ));

            ValidationResult result1 = validator.validate("tenant-1", "users", Map.of("username", "user1"));
            ValidationResult result2 = validator.validate("tenant-2", "users", Map.of("username", "user2"));

            assertThat(result1.state()).isEqualTo(ValidationResult.State.SUCCESS);
            assertThat(result2.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }

        @Test
        @DisplayName("should handle multiple collections per tenant")
        void shouldHandleMultipleCollections() {
            validator.registerSchema("tenant-1", "users", List.of(
                MetaField.builder().name("username").type(DataType.STRING).required(true).build()
            ));
            validator.registerSchema("tenant-1", "products", List.of(
                MetaField.builder().name("name").type(DataType.STRING).required(true).build()
            ));

            ValidationResult result1 = validator.validate("tenant-1", "users", Map.of("username", "john"));
            ValidationResult result2 = validator.validate("tenant-1", "products", Map.of("name", "widget"));

            assertThat(result1.state()).isEqualTo(ValidationResult.State.SUCCESS);
            assertThat(result2.state()).isEqualTo(ValidationResult.State.SUCCESS);
        }
    }
}
