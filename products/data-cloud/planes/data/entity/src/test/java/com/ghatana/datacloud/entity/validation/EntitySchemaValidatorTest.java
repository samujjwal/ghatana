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
    void setUp() { 
        validator = EntitySchemaValidator.create(); 
    }

    // =========================================================================
    // FACTORY METHODS
    // =========================================================================

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("create() should return non-null validator in non-strict mode")
        void createShouldReturnNonNull() { 
            assertThat(EntitySchemaValidator.create()).isNotNull(); 
        }

        @Test
        @DisplayName("create(true) should return non-null strict validator")
        void createStrictShouldReturnNonNull() { 
            assertThat(EntitySchemaValidator.create(true)).isNotNull(); 
        }

        @Test
        @DisplayName("create(false) should return non-null lenient validator")
        void createLenientShouldReturnNonNull() { 
            assertThat(EntitySchemaValidator.create(false)).isNotNull(); 
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
        void shouldRegisterSchema() { 
            validator.registerSchema("tenant-1", "products", List.of( 
                    MetaField.builder().name("name").type(DataType.STRING).build()
            ));
            assertThat(validator.hasSchema("tenant-1", "products")).isTrue(); 
        }

        @Test
        @DisplayName("should not affect other tenants when registering schema")
        void shouldIsolateTenants() { 
            validator.registerSchema("tenant-1", "products", List.of( 
                    MetaField.builder().name("name").type(DataType.STRING).build()
            ));
            assertThat(validator.hasSchema("tenant-2", "products")).isFalse(); 
        }

        @Test
        @DisplayName("should not affect other collections for same tenant")
        void shouldIsolateCollections() { 
            validator.registerSchema("tenant-1", "products", List.of( 
                    MetaField.builder().name("name").type(DataType.STRING).build()
            ));
            assertThat(validator.hasSchema("tenant-1", "orders")).isFalse(); 
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
        void shouldReturnFalseInitially() { 
            assertThat(validator.hasSchema("tenant-1", "products")).isFalse(); 
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
        void shouldEvictSchema() { 
            validator.registerSchema("tenant-1", "products", List.of( 
                    MetaField.builder().name("id").type(DataType.STRING).build()
            ));
            assertThat(validator.hasSchema("tenant-1", "products")).isTrue(); 

            validator.evictSchema("tenant-1", "products"); 
            assertThat(validator.hasSchema("tenant-1", "products")).isFalse(); 
        }

        @Test
        @DisplayName("should not throw when evicting non-existent schema")
        void shouldNotThrowOnMissingEvict() { 
            assertThatCode(() -> validator.evictSchema("unknown", "missing")) 
                    .doesNotThrowAnyException(); 
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
        void shouldReturnUnregisteredWhenSchemaAbsent() { 
            ValidationResult result = validator.validate("tenant-1", "products", Map.of("name", "test")); 
            assertThat(result.state()).isEqualTo(ValidationResult.State.UNREGISTERED); 
        }

        @Test
        @DisplayName("should return SUCCESS when data matches schema")
        void shouldReturnSuccessForValidData() { 
            validator.registerSchema("tenant-1", "products", List.of( 
                    MetaField.builder().name("name").type(DataType.STRING).build()
            ));

            ValidationResult result = validator.validate("tenant-1", "products", Map.of("name", "Widget")); 
            assertThat(result.state()).isEqualTo(ValidationResult.State.SUCCESS); 
        }

        @Test
        @DisplayName("should return FAILURE when required field is missing")
        void shouldReturnFailureForMissingRequiredField() { 
            MetaField requiredField = MetaField.builder() 
                    .name("name")
                    .type(DataType.STRING) 
                    .required(true) 
                    .build(); 
            validator.registerSchema("tenant-1", "products", List.of(requiredField)); 

            ValidationResult result = validator.validate("tenant-1", "products", Map.of()); 
            assertThat(result.state()).isEqualTo(ValidationResult.State.FAILURE); 
            assertThat(result.violations()).isNotEmpty(); 
        }
    }
}
