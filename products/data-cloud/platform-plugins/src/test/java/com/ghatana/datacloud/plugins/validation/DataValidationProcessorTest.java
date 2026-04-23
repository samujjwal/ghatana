package com.ghatana.datacloud.plugins.validation;

import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.datacloud.spi.TenantContext;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DataValidationProcessor Tests")
class DataValidationProcessorTest extends EventloopTestBase {

    private static final TenantContext TENANT = TenantContext.of("tenant-a", "workspace-a"); // GH-90000

    @Test
    @DisplayName("default processor stays Data-Cloud-local and never auto-enables AEP validation")
    @SuppressWarnings("deprecation")
    void defaultProcessorDoesNotEnableAepValidation() { // GH-90000
        DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000

        assertThat(processor.isAepAvailable()).isFalse(); // GH-90000
    }

    @Nested
    @DisplayName("Basic Validation")
    class BasicValidationTests {

        @Test
        @DisplayName("default processor rejects empty entity payloads")
        void defaultProcessorRejectsEmptyEntityPayloads() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000
            EntityStore.Entity entity = EntityStore.Entity.builder() // GH-90000
                    .collection("documents")
                    .data(Map.of()) // GH-90000
                    .build(); // GH-90000

            DataValidationProcessor.ValidationResult result =
                    runPromise(() -> processor.validate(TENANT, entity)); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.errors()).singleElement() // GH-90000
                    .extracting(DataValidationProcessor.ValidationError::code) // GH-90000
                    .isEqualTo("EMPTY_DATA");
        }

        @Test
        @DisplayName("default processor accepts non-empty entity payloads")
        void defaultProcessorAcceptsNonEmptyEntityPayloads() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000
            EntityStore.Entity entity = EntityStore.Entity.builder() // GH-90000
                    .collection("documents")
                    .data(Map.of("title", "Test Document")) // GH-90000
                    .build(); // GH-90000

            DataValidationProcessor.ValidationResult result =
                    runPromise(() -> processor.validate(TENANT, entity)); // GH-90000

            assertThat(result.valid()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Rule-Based Validation")
    class RuleValidationTests {

        @Test
        @DisplayName("MIN_LENGTH rule rejects strings that are too short")
        void minLengthRuleRejectsShortStrings() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000

            DataValidationProcessor.ValidationResult result = runPromise(() -> processor.validateWithRules( // GH-90000
                    TENANT,
                    "documents",
                    Map.of("title", "x"), // GH-90000
                    List.of(DataValidationProcessor.ValidationRule.minLength("title", 3)))); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.errors()).singleElement() // GH-90000
                    .extracting(DataValidationProcessor.ValidationError::code) // GH-90000
                    .isEqualTo("MIN_LENGTH");
        }

        @Test
        @DisplayName("MIN_LENGTH rule accepts strings that meet minimum")
        void minLengthRuleAcceptsValidStrings() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000

            DataValidationProcessor.ValidationResult result = runPromise(() -> processor.validateWithRules( // GH-90000
                    TENANT,
                    "documents",
                    Map.of("title", "Valid Title"), // GH-90000
                    List.of(DataValidationProcessor.ValidationRule.minLength("title", 3)))); // GH-90000

            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("MAX_LENGTH rule rejects strings that are too long")
        void maxLengthRuleRejectsLongStrings() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000

            DataValidationProcessor.ValidationResult result = runPromise(() -> processor.validateWithRules( // GH-90000
                    TENANT,
                    "documents",
                    Map.of("title", "This is a very long title that exceeds the maximum"), // GH-90000
                    List.of(DataValidationProcessor.ValidationRule.maxLength("title", 20)))); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.errors()).singleElement() // GH-90000
                    .extracting(DataValidationProcessor.ValidationError::code) // GH-90000
                    .isEqualTo("MAX_LENGTH");
        }

        @Test
        @DisplayName("MAX_LENGTH rule accepts strings that meet maximum")
        void maxLengthRuleAcceptsValidStrings() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000

            DataValidationProcessor.ValidationResult result = runPromise(() -> processor.validateWithRules( // GH-90000
                    TENANT,
                    "documents",
                    Map.of("title", "Short"), // GH-90000
                    List.of(DataValidationProcessor.ValidationRule.maxLength("title", 20)))); // GH-90000

            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("REQUIRED rule rejects null values")
        void requiredRuleRejectsNullValues() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000
            Map<String, Object> payload = new HashMap<>(); // GH-90000
            payload.put("title", null); // GH-90000

            DataValidationProcessor.ValidationResult result = runPromise(() -> processor.validateWithRules( // GH-90000
                    TENANT,
                    "documents",
                    payload,
                    List.of(DataValidationProcessor.ValidationRule.required("title"))));

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.errors()).singleElement() // GH-90000
                    .extracting(DataValidationProcessor.ValidationError::code) // GH-90000
                    .isEqualTo("REQUIRED");
        }

        @Test
        @DisplayName("REQUIRED rule rejects blank strings")
        void requiredRuleRejectsBlankStrings() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000

            DataValidationProcessor.ValidationResult result = runPromise(() -> processor.validateWithRules( // GH-90000
                    TENANT,
                    "documents",
                    Map.of("title", "   "), // GH-90000
                    List.of(DataValidationProcessor.ValidationRule.required("title"))));

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.errors()).singleElement() // GH-90000
                    .extracting(DataValidationProcessor.ValidationError::code) // GH-90000
                    .isEqualTo("REQUIRED");
        }

        @Test
        @DisplayName("PATTERN rule rejects strings that don't match")
        void patternRuleRejectsNonMatchingStrings() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000

            DataValidationProcessor.ValidationResult result = runPromise(() -> processor.validateWithRules( // GH-90000
                    TENANT,
                    "documents",
                    Map.of("email", "invalid-email"), // GH-90000
                    List.of(DataValidationProcessor.ValidationRule.pattern("email", "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")))); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.errors()).singleElement() // GH-90000
                    .extracting(DataValidationProcessor.ValidationError::code) // GH-90000
                    .isEqualTo("PATTERN");
        }

        @Test
        @DisplayName("PATTERN rule accepts strings that match")
        void patternRuleAcceptsMatchingStrings() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000

            DataValidationProcessor.ValidationResult result = runPromise(() -> processor.validateWithRules( // GH-90000
                    TENANT,
                    "documents",
                    Map.of("email", "test@example.com"), // GH-90000
                    List.of(DataValidationProcessor.ValidationRule.pattern("email", "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")))); // GH-90000

            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("MIN_VALUE rule rejects numbers below minimum")
        void minValueRuleRejectsNumbersBelowMinimum() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000

            DataValidationProcessor.ValidationResult result = runPromise(() ->  // GH-90000
                processor.validateWithRules( // GH-90000
                    TENANT,
                    "documents",
                    Map.of("age", 5), // GH-90000
                    List.of(new DataValidationProcessor.ValidationRule( // GH-90000
                        "age", 
                        DataValidationProcessor.RuleType.MIN_VALUE, 
                        10, 
                        "Age must be at least 10"
                    ))));

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.errors()).singleElement() // GH-90000
                    .extracting(DataValidationProcessor.ValidationError::code) // GH-90000
                    .isEqualTo("MIN_VALUE");
        }

        @Test
        @DisplayName("MIN_VALUE rule accepts numbers at or above minimum")
        void minValueRuleAcceptsValidNumbers() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000

            DataValidationProcessor.ValidationResult result = runPromise(() ->  // GH-90000
                processor.validateWithRules( // GH-90000
                    TENANT,
                    "documents",
                    Map.of("age", 15), // GH-90000
                    List.of(new DataValidationProcessor.ValidationRule( // GH-90000
                        "age", 
                        DataValidationProcessor.RuleType.MIN_VALUE, 
                        10, 
                        "Age must be at least 10"
                    ))));

            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("MAX_VALUE rule rejects numbers above maximum")
        void maxValueRuleRejectsNumbersAboveMaximum() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000

            DataValidationProcessor.ValidationResult result = runPromise(() ->  // GH-90000
                processor.validateWithRules( // GH-90000
                    TENANT,
                    "documents",
                    Map.of("age", 150), // GH-90000
                    List.of(new DataValidationProcessor.ValidationRule( // GH-90000
                        "age", 
                        DataValidationProcessor.RuleType.MAX_VALUE, 
                        100, 
                        "Age must be at most 100"
                    ))));

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.errors()).singleElement() // GH-90000
                    .extracting(DataValidationProcessor.ValidationError::code) // GH-90000
                    .isEqualTo("MAX_VALUE");
        }

        @Test
        @DisplayName("MAX_VALUE rule accepts numbers at or below maximum")
        void maxValueRuleAcceptsValidNumbers() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000

            DataValidationProcessor.ValidationResult result = runPromise(() ->  // GH-90000
                processor.validateWithRules( // GH-90000
                    TENANT,
                    "documents",
                    Map.of("age", 50), // GH-90000
                    List.of(new DataValidationProcessor.ValidationRule( // GH-90000
                        "age", 
                        DataValidationProcessor.RuleType.MAX_VALUE, 
                        100, 
                        "Age must be at most 100"
                    ))));

            assertThat(result.valid()).isTrue(); // GH-90000
        }

        @Test
        @DisplayName("multiple rules are validated together")
        void multipleRulesAreValidatedTogether() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000

            DataValidationProcessor.ValidationResult result = runPromise(() ->  // GH-90000
                processor.validateWithRules( // GH-90000
                    TENANT,
                    "documents",
                    Map.of("title", "x", "age", 150), // GH-90000
                    List.of( // GH-90000
                        DataValidationProcessor.ValidationRule.minLength("title", 3), // GH-90000
                        new DataValidationProcessor.ValidationRule( // GH-90000
                            "age", 
                            DataValidationProcessor.RuleType.MAX_VALUE, 
                            100, 
                            "Age must be at most 100"
                        ))));

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.errors()).hasSize(2); // GH-90000
        }
    }

    @Nested
    @DisplayName("Pattern Detection")
    class PatternDetectionTests {

        @Test
        @DisplayName("detects email pattern in entity data")
        void detectsEmailPattern() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000
            EntityStore.Entity entity = EntityStore.Entity.builder() // GH-90000
                    .collection("contacts")
                    .data(Map.of("email", "test@example.com")) // GH-90000
                    .build(); // GH-90000

            List<DataValidationProcessor.DetectedPattern> patterns =
                    runPromise(() -> processor.detectPatterns(TENANT, entity)); // GH-90000

            assertThat(patterns).isNotEmpty(); // GH-90000
            assertThat(patterns).anyMatch(p -> p.patternId().equals("email"));
        }

        @Test
        @DisplayName("detects URL pattern in entity data")
        void detectsUrlPattern() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000
            EntityStore.Entity entity = EntityStore.Entity.builder() // GH-90000
                    .collection("resources")
                    .data(Map.of("url", "https://example.com")) // GH-90000
                    .build(); // GH-90000

            List<DataValidationProcessor.DetectedPattern> patterns =
                    runPromise(() -> processor.detectPatterns(TENANT, entity)); // GH-90000

            assertThat(patterns).isNotEmpty(); // GH-90000
            assertThat(patterns).anyMatch(p -> p.patternId().equals("url"));
        }

        @Test
        @DisplayName("detects UUID pattern in entity data")
        void detectsUuidPattern() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000
            EntityStore.Entity entity = EntityStore.Entity.builder() // GH-90000
                    .collection("entities")
                    .data(Map.of("id", "550e8400-e29b-41d4-a716-446655440000")) // GH-90000
                    .build(); // GH-90000

            List<DataValidationProcessor.DetectedPattern> patterns =
                    runPromise(() -> processor.detectPatterns(TENANT, entity)); // GH-90000

            assertThat(patterns).isNotEmpty(); // GH-90000
            assertThat(patterns).anyMatch(p -> p.patternId().equals("uuid"));
        }

        @Test
        @DisplayName("returns empty list when no patterns detected")
        void returnsEmptyListWhenNoPatterns() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000
            EntityStore.Entity entity = EntityStore.Entity.builder() // GH-90000
                    .collection("entities")
                    .data(Map.of("name", "John Doe")) // GH-90000
                    .build(); // GH-90000

            List<DataValidationProcessor.DetectedPattern> patterns =
                    runPromise(() -> processor.detectPatterns(TENANT, entity)); // GH-90000

            assertThat(patterns).isEmpty(); // GH-90000
        }
    }
}
