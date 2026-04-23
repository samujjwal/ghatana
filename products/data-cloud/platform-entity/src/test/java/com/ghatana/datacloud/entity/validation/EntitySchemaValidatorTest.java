package com.ghatana.datacloud.entity.validation;

import com.ghatana.datacloud.entity.DataType;
import com.ghatana.datacloud.entity.MetaField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link EntitySchemaValidator}.
 *
 * @doc.type test
 * @doc.purpose Validate schema registration, eviction, presence checks, and validation rules
 * @doc.layer domain
 */
@DisplayName("EntitySchemaValidator Tests")
class EntitySchemaValidatorTest {

    private EntitySchemaValidator validator;

    @BeforeEach
    void setUp() { // GH-90000
        validator = EntitySchemaValidator.create(); // GH-90000
    }

    // =========================================================================
    // FACTORY METHODS
    // =========================================================================

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("create() should return non-null validator in non-strict mode")
        void createShouldReturnNonNull() { // GH-90000
            assertThat(EntitySchemaValidator.create()).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("create(true) should return non-null strict validator")
        void createStrictShouldReturnNonNull() { // GH-90000
            assertThat(EntitySchemaValidator.create(true)).isNotNull(); // GH-90000
        }

        @Test
        @DisplayName("create(false) should return non-null lenient validator")
        void createLenientShouldReturnNonNull() { // GH-90000
            assertThat(EntitySchemaValidator.create(false)).isNotNull(); // GH-90000
        }
    }

    // =========================================================================
    // SCHEMA REGISTRATION
    // =========================================================================

    @Nested
    @DisplayName("registerSchema")
    class RegisterSchema {

        @Test
        @DisplayName("should register schema so hasSchema returns true")
        void shouldRegisterSchema() { // GH-90000
            validator.registerSchema("tenant-1", "products", List.of( // GH-90000
                    MetaField.builder().name("name").type(DataType.STRING).build()
            ));
            assertThat(validator.hasSchema("tenant-1", "products")).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should not affect other tenants when registering schema")
        void shouldIsolateTenants() { // GH-90000
            validator.registerSchema("tenant-1", "products", List.of( // GH-90000
                    MetaField.builder().name("name").type(DataType.STRING).build()
            ));
            assertThat(validator.hasSchema("tenant-2", "products")).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should not affect other collections for same tenant")
        void shouldIsolateCollections() { // GH-90000
            validator.registerSchema("tenant-1", "products", List.of( // GH-90000
                    MetaField.builder().name("name").type(DataType.STRING).build()
            ));
            assertThat(validator.hasSchema("tenant-1", "orders")).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // HAS SCHEMA
    // =========================================================================

    @Nested
    @DisplayName("hasSchema")
    class HasSchema {

        @Test
        @DisplayName("should return false before any schema registered")
        void shouldReturnFalseInitially() { // GH-90000
            assertThat(validator.hasSchema("tenant-1", "products")).isFalse(); // GH-90000
        }
    }

    // =========================================================================
    // EVICT SCHEMA
    // =========================================================================

    @Nested
    @DisplayName("evictSchema")
    class EvictSchema {

        @Test
        @DisplayName("should evict schema so hasSchema returns false")
        void shouldEvictSchema() { // GH-90000
            validator.registerSchema("tenant-1", "products", List.of( // GH-90000
                    MetaField.builder().name("id").type(DataType.STRING).build()
            ));
            assertThat(validator.hasSchema("tenant-1", "products")).isTrue(); // GH-90000

            validator.evictSchema("tenant-1", "products"); // GH-90000
            assertThat(validator.hasSchema("tenant-1", "products")).isFalse(); // GH-90000
        }

        @Test
        @DisplayName("should not throw when evicting non-existent schema")
        void shouldNotThrowOnMissingEvict() { // GH-90000
            assertThatCode(() -> validator.evictSchema("unknown", "missing")) // GH-90000
                    .doesNotThrowAnyException(); // GH-90000
        }
    }

    // =========================================================================
    // VALIDATE
    // =========================================================================

    @Nested
    @DisplayName("validate")
    class Validate {

        @Test
        @DisplayName("should return UNREGISTERED when no schema registered")
        void shouldReturnUnregisteredWhenSchemaAbsent() { // GH-90000
            ValidationResult result = validator.validate("tenant-1", "products", Map.of("name", "test")); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.UNREGISTERED); // GH-90000
        }

        @Test
        @DisplayName("should return SUCCESS when data matches schema")
        void shouldReturnSuccessForValidData() { // GH-90000
            validator.registerSchema("tenant-1", "products", List.of( // GH-90000
                    MetaField.builder().name("name").type(DataType.STRING).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "products", Map.of("name", "Widget")); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); // GH-90000
        }

        @Test
        @DisplayName("should return FAILURE when required field is missing")
        void shouldReturnFailureForMissingRequiredField() { // GH-90000
            MetaField requiredField = MetaField.builder() // GH-90000
                    .name("name")
                    .type(DataType.STRING) // GH-90000
                    .required(true) // GH-90000
                    .build(); // GH-90000
            validator.registerSchema("tenant-1", "products", List.of(requiredField)); // GH-90000

            ValidationResult result = validator.validate("tenant-1", "products", Map.of()); // GH-90000
            assertThat(result.state()).isEqualTo(ValidationResult.State.FAILURE); // GH-90000
            assertThat(result.violations()).isNotEmpty(); // GH-90000
        }
    }
}
