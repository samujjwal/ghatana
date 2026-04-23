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
    void setUp() { // GH-90000
        validator = EntitySchemaValidator.create(true); // strict mode // GH-90000
    }

    // =========================================================================
    // DATA TYPE VALIDATION
    // =========================================================================

    @Nested
    @DisplayName("Data type validation")
    class DataTypeValidation {

        @Test
        @DisplayName("should validate STRING type")
        void shouldValidateStringType() { // GH-90000
            validator.registerSchema("tenant-1", "users", List.of( // GH-90000
                MetaField.builder().name("username").type(DataType.STRING).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "users", Map.of("username", "john_doe")); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("should validate NUMBER type")
        void shouldValidateNumberType() { // GH-90000
            validator.registerSchema("tenant-1", "products", List.of( // GH-90000
                MetaField.builder().name("quantity").type(DataType.NUMBER).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "products", Map.of("quantity", 42)); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("should validate CURRENCY type")
        void shouldValidateCurrencyType() { // GH-90000
            validator.registerSchema("tenant-1", "orders", List.of( // GH-90000
                MetaField.builder().name("total").type(DataType.CURRENCY).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "orders", Map.of("total", 99.99)); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("should validate BOOLEAN type")
        void shouldValidateBooleanType() { // GH-90000
            validator.registerSchema("tenant-1", "users", List.of( // GH-90000
                MetaField.builder().name("active").type(DataType.BOOLEAN).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "users", Map.of("active", true)); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("should validate DATETIME type")
        void shouldValidateDatetimeType() { // GH-90000
            validator.registerSchema("tenant-1", "events", List.of( // GH-90000
                MetaField.builder().name("occurredAt").type(DataType.DATETIME).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "events", Map.of("occurredAt", System.currentTimeMillis())); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("should validate JSON type")
        void shouldValidateJsonType() { // GH-90000
            validator.registerSchema("tenant-1", "configs", List.of( // GH-90000
                MetaField.builder().name("settings").type(DataType.JSON).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "configs", Map.of("settings", Map.of("key", "value"))); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("should validate ARRAY type")
        void shouldValidateArrayType() { // GH-90000
            validator.registerSchema("tenant-1", "orders", List.of( // GH-90000
                MetaField.builder().name("items").type(DataType.ARRAY).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "orders", Map.of("items", List.of("item1", "item2"))); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("should validate EMAIL type")
        void shouldValidateEmailType() { // GH-90000
            validator.registerSchema("tenant-1", "users", List.of( // GH-90000
                MetaField.builder().name("email").type(DataType.EMAIL).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "users", Map.of("email", "user@example.com")); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("should validate UUID type")
        void shouldValidateUuidType() { // GH-90000
            validator.registerSchema("tenant-1", "entities", List.of( // GH-90000
                MetaField.builder().name("entityId").type(DataType.UUID).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "entities", Map.of("entityId", "550e8400-e29b-41d4-a716-446655440000")); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
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
        void shouldFailWhenRequiredFieldIsNull() { // GH-90000
            validator.registerSchema("tenant-1", "users", List.of( // GH-90000
                MetaField.builder().name("email").type(DataType.STRING).required(true).build()
            ));

            Map<String, Object> entity = new HashMap<>(); // GH-90000
            entity.put("email", null); // GH-90000

            ValidationResult result = validator.validate("tenant-1", "users", entity); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.FAILURE); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("email") && v.contains("required"));
        }

        @Test
        @DisplayName("should fail when required field is missing")
        void shouldFailWhenRequiredFieldIsMissing() { // GH-90000
            validator.registerSchema("tenant-1", "users", List.of( // GH-90000
                MetaField.builder().name("email").type(DataType.STRING).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "users", Map.of()); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.FAILURE); // GH-90000
        }

        @Test
        @DisplayName("should succeed when optional field is missing")
        void shouldSucceedWhenOptionalFieldIsMissing() { // GH-90000
            validator.registerSchema("tenant-1", "users", List.of( // GH-90000
                MetaField.builder().name("nickname").type(DataType.STRING).required(false).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "users", Map.of()); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("should succeed when optional field is null")
        void shouldSucceedWhenOptionalFieldIsNull() { // GH-90000
            validator.registerSchema("tenant-1", "users", List.of( // GH-90000
                MetaField.builder().name("nickname").type(DataType.STRING).required(false).build()
            ));

            Map<String, Object> entity = new HashMap<>(); // GH-90000
            entity.put("nickname", null); // GH-90000

            ValidationResult result = validator.validate("tenant-1", "users", entity); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
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
        void shouldValidateMultipleFields() { // GH-90000
            validator.registerSchema("tenant-1", "users", List.of( // GH-90000
                MetaField.builder().name("username").type(DataType.STRING).required(true).build(),
                MetaField.builder().name("email").type(DataType.EMAIL).required(true).build(),
                MetaField.builder().name("age").type(DataType.NUMBER).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "users", Map.of( // GH-90000
                "username", "john_doe",
                "email", "john@example.com",
                "age", 30
            ));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("should report all violations")
        void shouldReportAllViolations() { // GH-90000
            validator.registerSchema("tenant-1", "users", List.of( // GH-90000
                MetaField.builder().name("username").type(DataType.STRING).required(true).build(),
                MetaField.builder().name("email").type(DataType.EMAIL).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "users", Map.of()); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.FAILURE); // GH-90000
            assertThat(result.violations()).hasSizeGreaterThanOrEqualTo(2); // GH-90000
        }

        @Test
        @DisplayName("should handle nested object validation")
        void shouldHandleNestedValidation() { // GH-90000
            validator.registerSchema("tenant-1", "orders", List.of( // GH-90000
                MetaField.builder().name("customer").type(DataType.EMBEDDED).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "orders", Map.of( // GH-90000
                "customer", Map.of("name", "John Doe", "id", "12345") // GH-90000
            ));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("should handle array field validation")
        void shouldHandleArrayValidation() { // GH-90000
            validator.registerSchema("tenant-1", "orders", List.of( // GH-90000
                MetaField.builder().name("items").type(DataType.ARRAY).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "orders", Map.of( // GH-90000
                "items", List.of("item1", "item2", "item3") // GH-90000
            ));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("should handle reference type")
        void shouldHandleReferenceType() { // GH-90000
            validator.registerSchema("tenant-1", "orders", List.of( // GH-90000
                MetaField.builder().name("customerId").type(DataType.REFERENCE).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "orders", Map.of( // GH-90000
                "customerId", "550e8400-e29b-41d4-a716-446655440000"
            ));
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
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
        void shouldHandleEmptyStringForOptional() { // GH-90000
            validator.registerSchema("tenant-1", "users", List.of( // GH-90000
                MetaField.builder().name("nickname").type(DataType.STRING).required(false).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "users", Map.of("nickname", "")); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("should handle zero value for numeric field")
        void shouldHandleZeroValue() { // GH-90000
            validator.registerSchema("tenant-1", "products", List.of( // GH-90000
                MetaField.builder().name("quantity").type(DataType.NUMBER).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "products", Map.of("quantity", 0)); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("should handle false value for boolean field")
        void shouldHandleFalseValue() { // GH-90000
            validator.registerSchema("tenant-1", "users", List.of( // GH-90000
                MetaField.builder().name("active").type(DataType.BOOLEAN).required(true).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "users", Map.of("active", false)); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("should handle empty array")
        void shouldHandleEmptyArray() { // GH-90000
            validator.registerSchema("tenant-1", "orders", List.of( // GH-90000
                MetaField.builder().name("items").type(DataType.ARRAY).required(false).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "orders", Map.of("items", List.of())); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("should handle empty object")
        void shouldHandleEmptyObject() { // GH-90000
            validator.registerSchema("tenant-1", "configs", List.of( // GH-90000
                MetaField.builder().name("metadata").type(DataType.JSON).required(false).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "configs", Map.of("metadata", Map.of())); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("should handle multiple tenants")
        void shouldHandleMultipleTenants() { // GH-90000
            validator.registerSchema("tenant-1", "users", List.of( // GH-90000
                MetaField.builder().name("username").type(DataType.STRING).required(true).build()
            ));
            validator.registerSchema("tenant-2", "users", List.of( // GH-90000
                MetaField.builder().name("username").type(DataType.STRING).required(true).build()
            ));

            ValidationResult result1 = validator.validate("tenant-1", "users", Map.of("username", "user1")); // GH-90000
            ValidationResult result2 = validator.validate("tenant-2", "users", Map.of("username", "user2")); // GH-90000

            assertThat(result1.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
            assertThat(result2.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("should handle multiple collections per tenant")
        void shouldHandleMultipleCollections() { // GH-90000
            validator.registerSchema("tenant-1", "users", List.of( // GH-90000
                MetaField.builder().name("username").type(DataType.STRING).required(true).build()
            ));
            validator.registerSchema("tenant-1", "products", List.of( // GH-90000
                MetaField.builder().name("name").type(DataType.STRING).required(true).build()
            ));

            ValidationResult result1 = validator.validate("tenant-1", "users", Map.of("username", "john")); // GH-90000
            ValidationResult result2 = validator.validate("tenant-1", "products", Map.of("name", "widget")); // GH-90000

            assertThat(result1.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
            assertThat(result2.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
        }
    }
}
