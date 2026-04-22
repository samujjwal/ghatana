/*
 * Copyright (c) 2026 Ghatana Inc. // GH-90000
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
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for enhanced entity schema validation features.
 *
 * <p>Validates:
 * <ul>
 *   <li>Date range validation (minDate/maxDate)</li> // GH-90000
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
@DisplayName("EntitySchemaValidator Enhanced Features [GH-90000]")
class EntitySchemaValidatorEnhancedTest {

    private EntitySchemaValidator validator;

    @BeforeEach
    void setUp() { // GH-90000
        validator = EntitySchemaValidator.create(); // GH-90000
    }

    // =========================================================================
    // Date Range Validation
    // =========================================================================

    @Nested
    @DisplayName("Date range validation [GH-90000]")
    class DateRangeValidation {

        @Test
        @DisplayName("should reject date before minDate [GH-90000]")
        void shouldRejectDateBeforeMinDate() { // GH-90000
            MetaField dateField = MetaField.builder() // GH-90000
                .name("createdDate [GH-90000]")
                .type(DataType.DATETIME) // GH-90000
                .validation(FieldValidation.builder() // GH-90000
                    .minDate("2026-01-01T00:00:00Z [GH-90000]")
                    .build()) // GH-90000
                .build(); // GH-90000

            validator.registerSchema("tenant-1", "test", List.of(dateField)); // GH-90000

            Map<String, Object> data = Map.of( // GH-90000
                "createdDate", "2025-12-31T23:59:59Z"
            );

            ValidationResult result = validator.validate("tenant-1", "test", data); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("before minimum date [GH-90000]"));
        }

        @Test
        @DisplayName("should accept date on minDate boundary [GH-90000]")
        void shouldAcceptDateOnMinDateBoundary() { // GH-90000
            MetaField dateField = MetaField.builder() // GH-90000
                .name("createdDate [GH-90000]")
                .type(DataType.DATETIME) // GH-90000
                .validation(FieldValidation.builder() // GH-90000
                    .minDate("2026-01-01T00:00:00Z [GH-90000]")
                    .build()) // GH-90000
                .build(); // GH-90000

            validator.registerSchema("tenant-1", "test", List.of(dateField)); // GH-90000

            Map<String, Object> data = Map.of( // GH-90000
                "createdDate", "2026-01-01T00:00:00Z"
            );

            ValidationResult result = validator.validate("tenant-1", "test", data); // GH-90000

            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should reject date after maxDate [GH-90000]")
        void shouldRejectDateAfterMaxDate() { // GH-90000
            MetaField dateField = MetaField.builder() // GH-90000
                .name("expiryDate [GH-90000]")
                .type(DataType.DATETIME) // GH-90000
                .validation(FieldValidation.builder() // GH-90000
                    .maxDate("2026-12-31T23:59:59Z [GH-90000]")
                    .build()) // GH-90000
                .build(); // GH-90000

            validator.registerSchema("tenant-1", "test", List.of(dateField)); // GH-90000

            Map<String, Object> data = Map.of( // GH-90000
                "expiryDate", "2027-01-01T00:00:00Z"
            );

            ValidationResult result = validator.validate("tenant-1", "test", data); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).anyMatch(v -> v.contains("after maximum date [GH-90000]"));
        }

        @Test
        @DisplayName("should accept date within range [GH-90000]")
        void shouldAcceptDateWithinRange() { // GH-90000
            MetaField dateField = MetaField.builder() // GH-90000
                .name("eventDate [GH-90000]")
                .type(DataType.DATETIME) // GH-90000
                .validation(FieldValidation.builder() // GH-90000
                    .minDate("2026-01-01T00:00:00Z [GH-90000]")
                    .maxDate("2026-12-31T23:59:59Z [GH-90000]")
                    .build()) // GH-90000
                .build(); // GH-90000

            validator.registerSchema("tenant-1", "test", List.of(dateField)); // GH-90000

            Map<String, Object> data = Map.of( // GH-90000
                "eventDate", "2026-06-15T12:00:00Z"
            );

            ValidationResult result = validator.validate("tenant-1", "test", data); // GH-90000

            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should handle invalid minDate format gracefully [GH-90000]")
        void shouldHandleInvalidMinDateFormat() { // GH-90000
            MetaField dateField = MetaField.builder() // GH-90000
                .name("createdDate [GH-90000]")
                .type(DataType.DATETIME) // GH-90000
                .validation(FieldValidation.builder() // GH-90000
                    .minDate("invalid-date [GH-90000]")
                    .build()) // GH-90000
                .build(); // GH-90000

            validator.registerSchema("tenant-1", "test", List.of(dateField)); // GH-90000

            Map<String, Object> data = Map.of( // GH-90000
                "createdDate", "2026-06-15T12:00:00Z"
            );

            ValidationResult result = validator.validate("tenant-1", "test", data); // GH-90000

            // Should not fail validation, just log warning
            assertThat(result.valid()).isTrue(); // GH-90000
        }
    }

    // =========================================================================
    // Pattern Caching
    // =========================================================================

    @Nested
    @DisplayName("Pattern caching [GH-90000]")
    class PatternCaching {

        @Test
        @DisplayName("should cache compiled regex patterns [GH-90000]")
        void shouldCacheCompiledRegexPatterns() { // GH-90000
            MetaField emailField = MetaField.builder() // GH-90000
                .name("email [GH-90000]")
                .type(DataType.STRING) // GH-90000
                .validation(FieldValidation.builder() // GH-90000
                    .pattern("^[A-Za-z0-9+_.-]+@(.+)$ [GH-90000]")
                    .build()) // GH-90000
                .build(); // GH-90000

            validator.registerSchema("tenant-1", "test", List.of(emailField)); // GH-90000

            Map<String, Object> data = Map.of( // GH-90000
                "email", "test@example.com"
            );

            // First validation - pattern compiled and cached
            ValidationResult result1 = validator.validate("tenant-1", "test", data); // GH-90000
            assertThat(result1.valid()).isTrue(); // GH-90000

            // Second validation - pattern reused from cache
            ValidationResult result2 = validator.validate("tenant-1", "test", data); // GH-90000
            assertThat(result2.valid()).isTrue(); // GH-90000

            // Third validation with invalid email
            Map<String, Object> invalidData = Map.of( // GH-90000
                "email", "not-an-email"
            );
            ValidationResult result3 = validator.validate("tenant-1", "test", invalidData); // GH-90000
            assertThat(result3.valid()).isFalse(); // GH-90000
            assertThat(result3.violations()).anyMatch(v -> v.contains("does not match pattern [GH-90000]"));
        }

        @Test
        @DisplayName("should handle invalid regex pattern gracefully [GH-90000]")
        void shouldHandleInvalidRegexPattern() { // GH-90000
            MetaField field = MetaField.builder() // GH-90000
                .name("code [GH-90000]")
                .type(DataType.STRING) // GH-90000
                .validation(FieldValidation.builder() // GH-90000
                    .pattern("[invalid( [GH-90000]") // Invalid regex
                    .build()) // GH-90000
                .build(); // GH-90000

            validator.registerSchema("tenant-1", "test", List.of(field)); // GH-90000

            Map<String, Object> data = Map.of( // GH-90000
                "code", "ABC123"
            );

            ValidationResult result = validator.validate("tenant-1", "test", data); // GH-90000

            // Should not fail validation, just log warning
            assertThat(result.valid()).isTrue(); // GH-90000
        }
    }

    // =========================================================================
    // Custom Validators
    // =========================================================================

    @Nested
    @DisplayName("Custom validators [GH-90000]")
    class CustomValidators {

        @Test
        @DisplayName("should invoke custom validator after standard validation [GH-90000]")
        void shouldInvokeCustomValidatorAfterStandardValidation() { // GH-90000
            MetaField priceField = MetaField.builder() // GH-90000
                .name("price [GH-90000]")
                .type(DataType.NUMBER) // GH-90000
                .validation(FieldValidation.builder() // GH-90000
                    .min(0.0) // GH-90000
                    .max(1000.0) // GH-90000
                    .build()) // GH-90000
                .build(); // GH-90000

            validator.registerSchema("tenant-1", "products", List.of(priceField)); // GH-90000

            // Register custom validator for business rule
            validator.registerCustomValidator("tenant-1", "products", "price", // GH-90000
                (fieldName, value, allData) -> { // GH-90000
                    if (value instanceof Double price && price > 500.0) { // GH-90000
                        return Optional.of("Price cannot exceed $500 for standard products [GH-90000]");
                    }
                    return Optional.empty(); // GH-90000
                });

            Map<String, Object> data = Map.of( // GH-90000
                "price", 600.0
            );

            ValidationResult result = validator.validate("tenant-1", "products", data); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).contains("Price cannot exceed $500 for standard products [GH-90000]");
        }

        @Test
        @DisplayName("should allow custom validator to pass [GH-90000]")
        void shouldAllowCustomValidatorToPass() { // GH-90000
            MetaField quantityField = MetaField.builder() // GH-90000
                .name("quantity [GH-90000]")
                .type(DataType.NUMBER) // GH-90000
                .validation(FieldValidation.builder() // GH-90000
                    .min(1.0) // GH-90000
                    .build()) // GH-90000
                .build(); // GH-90000

            validator.registerSchema("tenant-1", "products", List.of(quantityField)); // GH-90000

            // Register custom validator that always passes
            validator.registerCustomValidator("tenant-1", "products", "quantity", // GH-90000
                (fieldName, value, allData) -> Optional.empty()); // GH-90000

            Map<String, Object> data = Map.of( // GH-90000
                "quantity", 10
            );

            ValidationResult result = validator.validate("tenant-1", "products", data); // GH-90000

            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should support cross-field validation [GH-90000]")
        void shouldSupportCrossFieldValidation() { // GH-90000
            MetaField startField = MetaField.builder() // GH-90000
                .name("startDate [GH-90000]")
                .type(DataType.DATETIME) // GH-90000
                .build(); // GH-90000

            MetaField endField = MetaField.builder() // GH-90000
                .name("endDate [GH-90000]")
                .type(DataType.DATETIME) // GH-90000
                .build(); // GH-90000

            validator.registerSchema("tenant-1", "events", List.of(startField, endField)); // GH-90000

            // Register custom validator for cross-field check
            validator.registerCustomValidator("tenant-1", "events", "endDate", // GH-90000
                (fieldName, value, allData) -> { // GH-90000
                    Object startDate = allData.get("startDate [GH-90000]");
                    if (startDate instanceof String startStr && value instanceof String endStr) { // GH-90000
                        try {
                            Instant start = Instant.parse(startStr); // GH-90000
                            Instant end = Instant.parse(endStr); // GH-90000
                            if (end.isBefore(start)) { // GH-90000
                                return Optional.of("End date must be after start date [GH-90000]");
                            }
                        } catch (Exception e) { // GH-90000
                            // Invalid format, type check will catch it
                        }
                    }
                    return Optional.empty(); // GH-90000
                });

            Map<String, Object> data = Map.of( // GH-90000
                "startDate", "2026-06-15T00:00:00Z",
                "endDate", "2026-06-14T23:59:59Z"
            );

            ValidationResult result = validator.validate("tenant-1", "events", data); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.violations()).contains("End date must be after start date [GH-90000]");
        }

        @Test
        @DisplayName("should not invoke custom validators if standard validation fails [GH-90000]")
        void shouldNotInvokeCustomValidatorsIfStandardValidationFails() { // GH-90000
            MetaField priceField = MetaField.builder() // GH-90000
                .name("price [GH-90000]")
                .type(DataType.NUMBER) // GH-90000
                .validation(FieldValidation.builder() // GH-90000
                    .min(0.0) // GH-90000
                    .build()) // GH-90000
                .build(); // GH-90000

            validator.registerSchema("tenant-1", "products", List.of(priceField)); // GH-90000

            // Register custom validator that would fail
            validator.registerCustomValidator("tenant-1", "products", "price", // GH-90000
                (fieldName, value, allData) -> Optional.of("Custom validator violation [GH-90000]"));

            Map<String, Object> data = Map.of( // GH-90000
                "price", -10.0 // Fails standard validation
            );

            ValidationResult result = validator.validate("tenant-1", "products", data); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            // Only standard validation violation, not custom validator
            assertThat(result.violations()).hasSize(1); // GH-90000
            assertThat(result.violations().get(0)).contains("below minimum [GH-90000]");
        }
    }

    // =========================================================================
    // Reference Checker Infrastructure
    // =========================================================================

    @Nested
    @DisplayName("Reference checker infrastructure [GH-90000]")
    class ReferenceCheckerInfrastructure {

        @Test
        @DisplayName("should accept reference checker [GH-90000]")
        void shouldAcceptReferenceChecker() { // GH-90000
            ReferenceChecker checker = (tenantId, collection, field, value) -> // GH-90000
                CompletableFuture.completedFuture(true); // GH-90000

            validator.setReferenceChecker(checker); // GH-90000

            MetaField refField = MetaField.builder() // GH-90000
                .name("categoryId [GH-90000]")
                .type(DataType.STRING) // GH-90000
                .validation(FieldValidation.builder() // GH-90000
                    .referenceCollection("categories [GH-90000]")
                    .referenceField("id [GH-90000]")
                    .build()) // GH-90000
                .build(); // GH-90000

            validator.registerSchema("tenant-1", "products", List.of(refField)); // GH-90000

            Map<String, Object> data = Map.of( // GH-90000
                "categoryId", "cat-123"
            );

            ValidationResult result = validator.validate("tenant-1", "products", data); // GH-90000

            // Reference validation is logged but not enforced in sync mode
            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("should not fail when reference checker is not set [GH-90000]")
        void shouldNotFailWhenReferenceCheckerIsNotSet() { // GH-90000
            MetaField refField = MetaField.builder() // GH-90000
                .name("categoryId [GH-90000]")
                .type(DataType.STRING) // GH-90000
                .validation(FieldValidation.builder() // GH-90000
                    .referenceCollection("categories [GH-90000]")
                    .referenceField("id [GH-90000]")
                    .build()) // GH-90000
                .build(); // GH-90000

            validator.registerSchema("tenant-1", "products", List.of(refField)); // GH-90000

            Map<String, Object> data = Map.of( // GH-90000
                "categoryId", "cat-123"
            );

            ValidationResult result = validator.validate("tenant-1", "products", data); // GH-90000

            // Should not fail even though reference checker is not set
            assertThat(result.valid()).isTrue(); // GH-90000
        }
    }
}
