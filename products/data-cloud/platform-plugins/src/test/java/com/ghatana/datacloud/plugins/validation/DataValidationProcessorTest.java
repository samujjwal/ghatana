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

@DisplayName("DataValidationProcessor Tests [GH-90000]")
class DataValidationProcessorTest extends EventloopTestBase {

    private static final TenantContext TENANT = TenantContext.of("tenant-a", "workspace-a"); // GH-90000

    @Test
    @DisplayName("default processor stays Data-Cloud-local and never auto-enables AEP validation [GH-90000]")
    @SuppressWarnings("deprecation [GH-90000]")
    void defaultProcessorDoesNotEnableAepValidation() { // GH-90000
        DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000

        assertThat(processor.isAepAvailable()).isFalse(); // GH-90000
    }

    @Nested
    @DisplayName("Basic Validation [GH-90000]")
    class BasicValidationTests {

        @Test
        @DisplayName("default processor rejects empty entity payloads [GH-90000]")
        void defaultProcessorRejectsEmptyEntityPayloads() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000
            EntityStore.Entity entity = EntityStore.Entity.builder() // GH-90000
                    .collection("documents [GH-90000]")
                    .data(Map.of()) // GH-90000
                    .build(); // GH-90000

            DataValidationProcessor.ValidationResult result =
                    runPromise(() -> processor.validate(TENANT, entity)); // GH-90000

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.errors()).singleElement() // GH-90000
                    .extracting(DataValidationProcessor.ValidationError::code) // GH-90000
                    .isEqualTo("EMPTY_DATA [GH-90000]");
        }

        @Test
        @DisplayName("default processor accepts non-empty entity payloads [GH-90000]")
        void defaultProcessorAcceptsNonEmptyEntityPayloads() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000
            EntityStore.Entity entity = EntityStore.Entity.builder() // GH-90000
                    .collection("documents [GH-90000]")
                    .data(Map.of("title", "Test Document")) // GH-90000
                    .build(); // GH-90000

            DataValidationProcessor.ValidationResult result =
                    runPromise(() -> processor.validate(TENANT, entity)); // GH-90000

            assertThat(result.valid()).isTrue(); // GH-90000
        }
    }

    @Nested
    @DisplayName("Rule-Based Validation [GH-90000]")
    class RuleValidationTests {

        @Test
        @DisplayName("MIN_LENGTH rule rejects strings that are too short [GH-90000]")
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
                    .isEqualTo("MIN_LENGTH [GH-90000]");
        }

        @Test
        @DisplayName("MIN_LENGTH rule accepts strings that meet minimum [GH-90000]")
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
        @DisplayName("MAX_LENGTH rule rejects strings that are too long [GH-90000]")
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
                    .isEqualTo("MAX_LENGTH [GH-90000]");
        }

        @Test
        @DisplayName("MAX_LENGTH rule accepts strings that meet maximum [GH-90000]")
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
        @DisplayName("REQUIRED rule rejects null values [GH-90000]")
        void requiredRuleRejectsNullValues() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000
            Map<String, Object> payload = new HashMap<>(); // GH-90000
            payload.put("title", null); // GH-90000

            DataValidationProcessor.ValidationResult result = runPromise(() -> processor.validateWithRules( // GH-90000
                    TENANT,
                    "documents",
                    payload,
                    List.of(DataValidationProcessor.ValidationRule.required("title [GH-90000]"))));

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.errors()).singleElement() // GH-90000
                    .extracting(DataValidationProcessor.ValidationError::code) // GH-90000
                    .isEqualTo("REQUIRED [GH-90000]");
        }

        @Test
        @DisplayName("REQUIRED rule rejects blank strings [GH-90000]")
        void requiredRuleRejectsBlankStrings() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000

            DataValidationProcessor.ValidationResult result = runPromise(() -> processor.validateWithRules( // GH-90000
                    TENANT,
                    "documents",
                    Map.of("title", "   "), // GH-90000
                    List.of(DataValidationProcessor.ValidationRule.required("title [GH-90000]"))));

            assertThat(result.valid()).isFalse(); // GH-90000
            assertThat(result.errors()).singleElement() // GH-90000
                    .extracting(DataValidationProcessor.ValidationError::code) // GH-90000
                    .isEqualTo("REQUIRED [GH-90000]");
        }

        @Test
        @DisplayName("PATTERN rule rejects strings that don't match [GH-90000]")
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
                    .isEqualTo("PATTERN [GH-90000]");
        }

        @Test
        @DisplayName("PATTERN rule accepts strings that match [GH-90000]")
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
        @DisplayName("MIN_VALUE rule rejects numbers below minimum [GH-90000]")
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
                    .isEqualTo("MIN_VALUE [GH-90000]");
        }

        @Test
        @DisplayName("MIN_VALUE rule accepts numbers at or above minimum [GH-90000]")
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
        @DisplayName("MAX_VALUE rule rejects numbers above maximum [GH-90000]")
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
                    .isEqualTo("MAX_VALUE [GH-90000]");
        }

        @Test
        @DisplayName("MAX_VALUE rule accepts numbers at or below maximum [GH-90000]")
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
        @DisplayName("multiple rules are validated together [GH-90000]")
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
    @DisplayName("Pattern Detection [GH-90000]")
    class PatternDetectionTests {

        @Test
        @DisplayName("detects email pattern in entity data [GH-90000]")
        void detectsEmailPattern() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000
            EntityStore.Entity entity = EntityStore.Entity.builder() // GH-90000
                    .collection("contacts [GH-90000]")
                    .data(Map.of("email", "test@example.com")) // GH-90000
                    .build(); // GH-90000

            List<DataValidationProcessor.DetectedPattern> patterns =
                    runPromise(() -> processor.detectPatterns(TENANT, entity)); // GH-90000

            assertThat(patterns).isNotEmpty(); // GH-90000
            assertThat(patterns).anyMatch(p -> p.patternId().equals("email [GH-90000]"));
        }

        @Test
        @DisplayName("detects URL pattern in entity data [GH-90000]")
        void detectsUrlPattern() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000
            EntityStore.Entity entity = EntityStore.Entity.builder() // GH-90000
                    .collection("resources [GH-90000]")
                    .data(Map.of("url", "https://example.com")) // GH-90000
                    .build(); // GH-90000

            List<DataValidationProcessor.DetectedPattern> patterns =
                    runPromise(() -> processor.detectPatterns(TENANT, entity)); // GH-90000

            assertThat(patterns).isNotEmpty(); // GH-90000
            assertThat(patterns).anyMatch(p -> p.patternId().equals("url [GH-90000]"));
        }

        @Test
        @DisplayName("detects UUID pattern in entity data [GH-90000]")
        void detectsUuidPattern() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000
            EntityStore.Entity entity = EntityStore.Entity.builder() // GH-90000
                    .collection("entities [GH-90000]")
                    .data(Map.of("id", "550e8400-e29b-41d4-a716-446655440000")) // GH-90000
                    .build(); // GH-90000

            List<DataValidationProcessor.DetectedPattern> patterns =
                    runPromise(() -> processor.detectPatterns(TENANT, entity)); // GH-90000

            assertThat(patterns).isNotEmpty(); // GH-90000
            assertThat(patterns).anyMatch(p -> p.patternId().equals("uuid [GH-90000]"));
        }

        @Test
        @DisplayName("returns empty list when no patterns detected [GH-90000]")
        void returnsEmptyListWhenNoPatterns() { // GH-90000
            DataValidationProcessor processor = new DataValidationProcessor(); // GH-90000
            EntityStore.Entity entity = EntityStore.Entity.builder() // GH-90000
                    .collection("entities [GH-90000]")
                    .data(Map.of("name", "John Doe")) // GH-90000
                    .build(); // GH-90000

            List<DataValidationProcessor.DetectedPattern> patterns =
                    runPromise(() -> processor.detectPatterns(TENANT, entity)); // GH-90000

            assertThat(patterns).isEmpty(); // GH-90000
        }
    }
}
