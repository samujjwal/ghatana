/*
 * Copyright (c) 2026 Ghatana Inc. 
 * All rights reserved.
 */
package com.ghatana.datacloud.entity.validation;

import com.ghatana.datacloud.entity.DataType;
import com.ghatana.datacloud.entity.FieldValidation;
import com.ghatana.datacloud.entity.MetaField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import io.activej.promise.Promise;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for enhanced entity schema validation features.
 *
 * <p>Validates:
 * <ul>
 *   <li>Date range validation (minDate/maxDate)</li> 
 *   <li>Pattern caching for regex patterns</li>
 *   <li>Custom validator registration and invocation</li>
 *   <li>Reference validation infrastructure</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Enhanced entity validation feature tests
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("EntitySchemaValidator Enhanced Features")
class EntitySchemaValidatorEnhancedTest {

    private EntitySchemaValidator validator;

    @BeforeEach
    void setUp() { 
        validator = EntitySchemaValidator.create(); 
    }

    // =========================================================================
    // Date Range Validation
    // =========================================================================

    @Nested
    @DisplayName("Date range validation")
    class DateRangeValidation {

        @Test
        @DisplayName("should reject date before minDate")
        void shouldRejectDateBeforeMinDate() { 
            MetaField dateField = MetaField.builder() 
                .name("createdDate")
                .type(DataType.DATETIME) 
                .validation(FieldValidation.builder() 
                    .minDate("2026-01-01T00:00:00Z")
                    .build()) 
                .build(); 

            validator.registerSchema("tenant-1", "test", List.of(dateField)); 

            Map<String, Object> data = Map.of( 
                "createdDate", "2025-12-31T23:59:59Z"
            );

            ValidationResult result = validator.validate("tenant-1", "test", data); 

            assertThat(result.valid()).isFalse(); 
            assertThat(result.violations()).anyMatch(v -> v.contains("before minimum date"));
        }

        @Test
        @DisplayName("should accept date on minDate boundary")
        void shouldAcceptDateOnMinDateBoundary() { 
            MetaField dateField = MetaField.builder() 
                .name("createdDate")
                .type(DataType.DATETIME) 
                .validation(FieldValidation.builder() 
                    .minDate("2026-01-01T00:00:00Z")
                    .build()) 
                .build(); 

            validator.registerSchema("tenant-1", "test", List.of(dateField)); 

            Map<String, Object> data = Map.of( 
                "createdDate", "2026-01-01T00:00:00Z"
            );

            ValidationResult result = validator.validate("tenant-1", "test", data); 

            assertThat(result.valid()).isTrue(); 
        }

        @Test
        @DisplayName("should reject date after maxDate")
        void shouldRejectDateAfterMaxDate() { 
            MetaField dateField = MetaField.builder() 
                .name("expiryDate")
                .type(DataType.DATETIME) 
                .validation(FieldValidation.builder() 
                    .maxDate("2026-12-31T23:59:59Z")
                    .build()) 
                .build(); 

            validator.registerSchema("tenant-1", "test", List.of(dateField)); 

            Map<String, Object> data = Map.of( 
                "expiryDate", "2027-01-01T00:00:00Z"
            );

            ValidationResult result = validator.validate("tenant-1", "test", data); 

            assertThat(result.valid()).isFalse(); 
            assertThat(result.violations()).anyMatch(v -> v.contains("after maximum date"));
        }

        @Test
        @DisplayName("should accept date within range")
        void shouldAcceptDateWithinRange() { 
            MetaField dateField = MetaField.builder() 
                .name("eventDate")
                .type(DataType.DATETIME) 
                .validation(FieldValidation.builder() 
                    .minDate("2026-01-01T00:00:00Z")
                    .maxDate("2026-12-31T23:59:59Z")
                    .build()) 
                .build(); 

            validator.registerSchema("tenant-1", "test", List.of(dateField)); 

            Map<String, Object> data = Map.of( 
                "eventDate", "2026-06-15T12:00:00Z"
            );

            ValidationResult result = validator.validate("tenant-1", "test", data); 

            assertThat(result.valid()).isTrue(); 
        }

        @Test
        @DisplayName("should handle invalid minDate format gracefully")
        void shouldHandleInvalidMinDateFormat() { 
            MetaField dateField = MetaField.builder() 
                .name("createdDate")
                .type(DataType.DATETIME) 
                .validation(FieldValidation.builder() 
                    .minDate("invalid-date")
                    .build()) 
                .build(); 

            validator.registerSchema("tenant-1", "test", List.of(dateField)); 

            Map<String, Object> data = Map.of( 
                "createdDate", "2026-06-15T12:00:00Z"
            );

            ValidationResult result = validator.validate("tenant-1", "test", data); 

            // Should not fail validation, just log warning
            assertThat(result.valid()).isTrue(); 
        }
    }

    // =========================================================================
    // Pattern Caching
    // =========================================================================

    @Nested
    @DisplayName("Pattern caching")
    class PatternCaching {

        @Test
        @DisplayName("should cache compiled regex patterns")
        void shouldCacheCompiledRegexPatterns() { 
            MetaField emailField = MetaField.builder() 
                .name("email")
                .type(DataType.STRING) 
                .validation(FieldValidation.builder() 
                    .pattern("^[A-Za-z0-9+_.-]+@(.+)$")
                    .build()) 
                .build(); 

            validator.registerSchema("tenant-1", "test", List.of(emailField)); 

            Map<String, Object> data = Map.of( 
                "email", "test@example.com"
            );

            // First validation - pattern compiled and cached
            ValidationResult result1 = validator.validate("tenant-1", "test", data); 
            assertThat(result1.valid()).isTrue(); 

            // Second validation - pattern reused from cache
            ValidationResult result2 = validator.validate("tenant-1", "test", data); 
            assertThat(result2.valid()).isTrue(); 

            // Third validation with invalid email
            Map<String, Object> invalidData = Map.of( 
                "email", "not-an-email"
            );
            ValidationResult result3 = validator.validate("tenant-1", "test", invalidData); 
            assertThat(result3.valid()).isFalse(); 
            assertThat(result3.violations()).anyMatch(v -> v.contains("does not match pattern"));
        }

        @Test
        @DisplayName("should handle invalid regex pattern gracefully")
        void shouldHandleInvalidRegexPattern() { 
            MetaField field = MetaField.builder() 
                .name("code")
                .type(DataType.STRING) 
                .validation(FieldValidation.builder() 
                    .pattern("[invalid(") // Invalid regex
                    .build()) 
                .build(); 

            validator.registerSchema("tenant-1", "test", List.of(field)); 

            Map<String, Object> data = Map.of( 
                "code", "ABC123"
            );

            ValidationResult result = validator.validate("tenant-1", "test", data); 

            // Should not fail validation, just log warning
            assertThat(result.valid()).isTrue(); 
        }
    }

    // =========================================================================
    // Custom Validators
    // =========================================================================

    @Nested
    @DisplayName("Custom validators")
    class CustomValidators {

        @Test
        @DisplayName("should invoke custom validator after standard validation")
        void shouldInvokeCustomValidatorAfterStandardValidation() { 
            MetaField priceField = MetaField.builder() 
                .name("price")
                .type(DataType.NUMBER) 
                .validation(FieldValidation.builder() 
                    .min(0.0) 
                    .max(1000.0) 
                    .build()) 
                .build(); 

            validator.registerSchema("tenant-1", "products", List.of(priceField)); 

            // Register custom validator for business rule
            validator.registerCustomValidator("tenant-1", "products", "price", 
                (fieldName, value, allData) -> { 
                    if (value instanceof Double price && price > 500.0) { 
                        return Optional.of("Price cannot exceed $500 for standard products");
                    }
                    return Optional.empty(); 
                });

            Map<String, Object> data = Map.of( 
                "price", 600.0
            );

            ValidationResult result = validator.validate("tenant-1", "products", data); 

            assertThat(result.valid()).isFalse(); 
            assertThat(result.violations()).contains("Price cannot exceed $500 for standard products");
        }

        @Test
        @DisplayName("should allow custom validator to pass")
        void shouldAllowCustomValidatorToPass() { 
            MetaField quantityField = MetaField.builder() 
                .name("quantity")
                .type(DataType.NUMBER) 
                .validation(FieldValidation.builder() 
                    .min(1.0) 
                    .build()) 
                .build(); 

            validator.registerSchema("tenant-1", "products", List.of(quantityField)); 

            // Register custom validator that always passes
            validator.registerCustomValidator("tenant-1", "products", "quantity", 
                (fieldName, value, allData) -> Optional.empty()); 

            Map<String, Object> data = Map.of( 
                "quantity", 10
            );

            ValidationResult result = validator.validate("tenant-1", "products", data); 

            assertThat(result.valid()).isTrue(); 
        }

        @Test
        @DisplayName("should support cross-field validation")
        void shouldSupportCrossFieldValidation() { 
            MetaField startField = MetaField.builder() 
                .name("startDate")
                .type(DataType.DATETIME) 
                .build(); 

            MetaField endField = MetaField.builder() 
                .name("endDate")
                .type(DataType.DATETIME) 
                .build(); 

            validator.registerSchema("tenant-1", "events", List.of(startField, endField)); 

            // Register custom validator for cross-field check
            validator.registerCustomValidator("tenant-1", "events", "endDate", 
                (fieldName, value, allData) -> { 
                    Object startDate = allData.get("startDate");
                    if (startDate instanceof String startStr && value instanceof String endStr) { 
                        try {
                            Instant start = Instant.parse(startStr); 
                            Instant end = Instant.parse(endStr); 
                            if (end.isBefore(start)) { 
                                return Optional.of("End date must be after start date");
                            }
                        } catch (Exception e) { 
                            // Invalid format, type check will catch it
                        }
                    }
                    return Optional.empty(); 
                });

            Map<String, Object> data = Map.of( 
                "startDate", "2026-06-15T00:00:00Z",
                "endDate", "2026-06-14T23:59:59Z"
            );

            ValidationResult result = validator.validate("tenant-1", "events", data); 

            assertThat(result.valid()).isFalse(); 
            assertThat(result.violations()).contains("End date must be after start date");
        }

        @Test
        @DisplayName("should not invoke custom validators if standard validation fails")
        void shouldNotInvokeCustomValidatorsIfStandardValidationFails() { 
            MetaField priceField = MetaField.builder() 
                .name("price")
                .type(DataType.NUMBER) 
                .validation(FieldValidation.builder() 
                    .min(0.0) 
                    .build()) 
                .build(); 

            validator.registerSchema("tenant-1", "products", List.of(priceField)); 

            // Register custom validator that would fail
            validator.registerCustomValidator("tenant-1", "products", "price", 
                (fieldName, value, allData) -> Optional.of("Custom validator violation"));

            Map<String, Object> data = Map.of( 
                "price", -10.0 // Fails standard validation
            );

            ValidationResult result = validator.validate("tenant-1", "products", data); 

            assertThat(result.valid()).isFalse(); 
            // Only standard validation violation, not custom validator
            assertThat(result.violations()).hasSize(1); 
            assertThat(result.violations().get(0)).contains("below minimum");
        }
    }

    // =========================================================================
    // Reference Checker Infrastructure
    // =========================================================================

    @Nested
    @DisplayName("Reference checker infrastructure")
    class ReferenceCheckerInfrastructure {

        @Test
        @DisplayName("should accept reference checker")
        void shouldAcceptReferenceChecker() { 
            ReferenceChecker checker = (tenantId, collection, field, value) -> 
                Promise.of(true); 

            validator.setReferenceChecker(checker); 

            MetaField refField = MetaField.builder() 
                .name("categoryId")
                .type(DataType.STRING) 
                .validation(FieldValidation.builder() 
                    .referenceCollection("categories")
                    .referenceField("id")
                    .build()) 
                .build(); 

            validator.registerSchema("tenant-1", "products", List.of(refField)); 

            Map<String, Object> data = Map.of( 
                "categoryId", "cat-123"
            );

            ValidationResult result = validator.validate("tenant-1", "products", data); 

            // Reference validation is logged but not enforced in sync mode
            assertThat(result.valid()).isTrue(); 
        }

        @Test
        @DisplayName("should not fail when reference checker is not set")
        void shouldNotFailWhenReferenceCheckerIsNotSet() { 
            MetaField refField = MetaField.builder() 
                .name("categoryId")
                .type(DataType.STRING) 
                .validation(FieldValidation.builder() 
                    .referenceCollection("categories")
                    .referenceField("id")
                    .build()) 
                .build(); 

            validator.registerSchema("tenant-1", "products", List.of(refField)); 

            Map<String, Object> data = Map.of( 
                "categoryId", "cat-123"
            );

            ValidationResult result = validator.validate("tenant-1", "products", data); 

            // Should not fail even though reference checker is not set
            assertThat(result.valid()).isTrue(); 
        }
    }
}
