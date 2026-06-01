package com.ghatana.datacloud.plugins.validation;

import com.ghatana.datacloud.spi.EntityStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
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

    private static final TenantContext TENANT = TenantContext.of("tenant-a", "workspace-a"); 

    @Test
    @DisplayName("default processor stays Data-Cloud-local and never auto-enables AEP validation")
    @SuppressWarnings("deprecation")
    void defaultProcessorDoesNotEnableAepValidation() { 
        DataValidationProcessor processor = new DataValidationProcessor(); 

        assertThat(processor.isAepAvailable()).isFalse(); 
    }

    @Nested
    @DisplayName("Basic Validation")
    class BasicValidationTests {

        @Test
        @DisplayName("default processor rejects empty entity payloads")
        void defaultProcessorRejectsEmptyEntityPayloads() { 
            DataValidationProcessor processor = new DataValidationProcessor(); 
            EntityStore.Entity entity = EntityStore.Entity.builder() 
                    .collection("documents")
                    .data(Map.of()) 
                    .build(); 

            DataValidationProcessor.ValidationResult result =
                    runPromise(() -> processor.validate(TENANT, entity)); 

            assertThat(result.valid()).isFalse(); 
            assertThat(result.errors()).singleElement() 
                    .extracting(DataValidationProcessor.ValidationError::code) 
                    .isEqualTo("EMPTY_DATA");
        }

        @Test
        @DisplayName("default processor accepts non-empty entity payloads")
        void defaultProcessorAcceptsNonEmptyEntityPayloads() { 
            DataValidationProcessor processor = new DataValidationProcessor(); 
            EntityStore.Entity entity = EntityStore.Entity.builder() 
                    .collection("documents")
                    .data(Map.of("title", "Test Document")) 
                    .build(); 

            DataValidationProcessor.ValidationResult result =
                    runPromise(() -> processor.validate(TENANT, entity)); 

            assertThat(result.valid()).isTrue(); 
        }
    }

    @Nested
    @DisplayName("Rule-Based Validation")
    class RuleValidationTests {

        @Test
        @DisplayName("MIN_LENGTH rule rejects strings that are too short")
        void minLengthRuleRejectsShortStrings() { 
            DataValidationProcessor processor = new DataValidationProcessor(); 

            DataValidationProcessor.ValidationResult result = runPromise(() -> processor.validateWithRules( 
                    TENANT,
                    "documents",
                    Map.of("title", "x"), 
                    List.of(DataValidationProcessor.ValidationRule.minLength("title", 3)))); 

            assertThat(result.valid()).isFalse(); 
            assertThat(result.errors()).singleElement() 
                    .extracting(DataValidationProcessor.ValidationError::code) 
                    .isEqualTo("MIN_LENGTH");
        }

        @Test
        @DisplayName("MIN_LENGTH rule accepts strings that meet minimum")
        void minLengthRuleAcceptsValidStrings() { 
            DataValidationProcessor processor = new DataValidationProcessor(); 

            DataValidationProcessor.ValidationResult result = runPromise(() -> processor.validateWithRules( 
                    TENANT,
                    "documents",
                    Map.of("title", "Valid Title"), 
                    List.of(DataValidationProcessor.ValidationRule.minLength("title", 3)))); 

            assertThat(result.valid()).isTrue(); 
        }

        @Test
        @DisplayName("MAX_LENGTH rule rejects strings that are too long")
        void maxLengthRuleRejectsLongStrings() { 
            DataValidationProcessor processor = new DataValidationProcessor(); 

            DataValidationProcessor.ValidationResult result = runPromise(() -> processor.validateWithRules( 
                    TENANT,
                    "documents",
                    Map.of("title", "This is a very long title that exceeds the maximum"), 
                    List.of(DataValidationProcessor.ValidationRule.maxLength("title", 20)))); 

            assertThat(result.valid()).isFalse(); 
            assertThat(result.errors()).singleElement() 
                    .extracting(DataValidationProcessor.ValidationError::code) 
                    .isEqualTo("MAX_LENGTH");
        }

        @Test
        @DisplayName("MAX_LENGTH rule accepts strings that meet maximum")
        void maxLengthRuleAcceptsValidStrings() { 
            DataValidationProcessor processor = new DataValidationProcessor(); 

            DataValidationProcessor.ValidationResult result = runPromise(() -> processor.validateWithRules( 
                    TENANT,
                    "documents",
                    Map.of("title", "Short"), 
                    List.of(DataValidationProcessor.ValidationRule.maxLength("title", 20)))); 

            assertThat(result.valid()).isTrue(); 
        }

        @Test
        @DisplayName("REQUIRED rule rejects null values")
        void requiredRuleRejectsNullValues() { 
            DataValidationProcessor processor = new DataValidationProcessor(); 
            Map<String, Object> payload = new HashMap<>(); 
            payload.put("title", null); 

            DataValidationProcessor.ValidationResult result = runPromise(() -> processor.validateWithRules( 
                    TENANT,
                    "documents",
                    payload,
                    List.of(DataValidationProcessor.ValidationRule.required("title"))));

            assertThat(result.valid()).isFalse(); 
            assertThat(result.errors()).singleElement() 
                    .extracting(DataValidationProcessor.ValidationError::code) 
                    .isEqualTo("REQUIRED");
        }

        @Test
        @DisplayName("REQUIRED rule rejects blank strings")
        void requiredRuleRejectsBlankStrings() { 
            DataValidationProcessor processor = new DataValidationProcessor(); 

            DataValidationProcessor.ValidationResult result = runPromise(() -> processor.validateWithRules( 
                    TENANT,
                    "documents",
                    Map.of("title", "   "), 
                    List.of(DataValidationProcessor.ValidationRule.required("title"))));

            assertThat(result.valid()).isFalse(); 
            assertThat(result.errors()).singleElement() 
                    .extracting(DataValidationProcessor.ValidationError::code) 
                    .isEqualTo("REQUIRED");
        }

        @Test
        @DisplayName("PATTERN rule rejects strings that don't match")
        void patternRuleRejectsNonMatchingStrings() { 
            DataValidationProcessor processor = new DataValidationProcessor(); 

            DataValidationProcessor.ValidationResult result = runPromise(() -> processor.validateWithRules( 
                    TENANT,
                    "documents",
                    Map.of("email", "invalid-email"), 
                    List.of(DataValidationProcessor.ValidationRule.pattern("email", "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")))); 

            assertThat(result.valid()).isFalse(); 
            assertThat(result.errors()).singleElement() 
                    .extracting(DataValidationProcessor.ValidationError::code) 
                    .isEqualTo("PATTERN");
        }

        @Test
        @DisplayName("PATTERN rule accepts strings that match")
        void patternRuleAcceptsMatchingStrings() { 
            DataValidationProcessor processor = new DataValidationProcessor(); 

            DataValidationProcessor.ValidationResult result = runPromise(() -> processor.validateWithRules( 
                    TENANT,
                    "documents",
                    Map.of("email", "test@example.com"), 
                    List.of(DataValidationProcessor.ValidationRule.pattern("email", "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")))); 

            assertThat(result.valid()).isTrue(); 
        }

        @Test
        @DisplayName("MIN_VALUE rule rejects numbers below minimum")
        void minValueRuleRejectsNumbersBelowMinimum() { 
            DataValidationProcessor processor = new DataValidationProcessor(); 

            DataValidationProcessor.ValidationResult result = runPromise(() ->  
                processor.validateWithRules( 
                    TENANT,
                    "documents",
                    Map.of("age", 5), 
                    List.of(new DataValidationProcessor.ValidationRule( 
                        "age", 
                        DataValidationProcessor.RuleType.MIN_VALUE, 
                        10, 
                        "Age must be at least 10"
                    ))));

            assertThat(result.valid()).isFalse(); 
            assertThat(result.errors()).singleElement() 
                    .extracting(DataValidationProcessor.ValidationError::code) 
                    .isEqualTo("MIN_VALUE");
        }

        @Test
        @DisplayName("MIN_VALUE rule accepts numbers at or above minimum")
        void minValueRuleAcceptsValidNumbers() { 
            DataValidationProcessor processor = new DataValidationProcessor(); 

            DataValidationProcessor.ValidationResult result = runPromise(() ->  
                processor.validateWithRules( 
                    TENANT,
                    "documents",
                    Map.of("age", 15), 
                    List.of(new DataValidationProcessor.ValidationRule( 
                        "age", 
                        DataValidationProcessor.RuleType.MIN_VALUE, 
                        10, 
                        "Age must be at least 10"
                    ))));

            assertThat(result.valid()).isTrue(); 
        }

        @Test
        @DisplayName("MAX_VALUE rule rejects numbers above maximum")
        void maxValueRuleRejectsNumbersAboveMaximum() { 
            DataValidationProcessor processor = new DataValidationProcessor(); 

            DataValidationProcessor.ValidationResult result = runPromise(() ->  
                processor.validateWithRules( 
                    TENANT,
                    "documents",
                    Map.of("age", 150), 
                    List.of(new DataValidationProcessor.ValidationRule( 
                        "age", 
                        DataValidationProcessor.RuleType.MAX_VALUE, 
                        100, 
                        "Age must be at most 100"
                    ))));

            assertThat(result.valid()).isFalse(); 
            assertThat(result.errors()).singleElement() 
                    .extracting(DataValidationProcessor.ValidationError::code) 
                    .isEqualTo("MAX_VALUE");
        }

        @Test
        @DisplayName("MAX_VALUE rule accepts numbers at or below maximum")
        void maxValueRuleAcceptsValidNumbers() { 
            DataValidationProcessor processor = new DataValidationProcessor(); 

            DataValidationProcessor.ValidationResult result = runPromise(() ->  
                processor.validateWithRules( 
                    TENANT,
                    "documents",
                    Map.of("age", 50), 
                    List.of(new DataValidationProcessor.ValidationRule( 
                        "age", 
                        DataValidationProcessor.RuleType.MAX_VALUE, 
                        100, 
                        "Age must be at most 100"
                    ))));

            assertThat(result.valid()).isTrue(); 
        }

        @Test
        @DisplayName("multiple rules are validated together")
        void multipleRulesAreValidatedTogether() { 
            DataValidationProcessor processor = new DataValidationProcessor(); 

            DataValidationProcessor.ValidationResult result = runPromise(() ->  
                processor.validateWithRules( 
                    TENANT,
                    "documents",
                    Map.of("title", "x", "age", 150), 
                    List.of( 
                        DataValidationProcessor.ValidationRule.minLength("title", 3), 
                        new DataValidationProcessor.ValidationRule( 
                            "age", 
                            DataValidationProcessor.RuleType.MAX_VALUE, 
                            100, 
                            "Age must be at most 100"
                        ))));

            assertThat(result.valid()).isFalse(); 
            assertThat(result.errors()).hasSize(2); 
        }
    }

    @Nested
    @DisplayName("Pattern Detection")
    class PatternDetectionTests {

        @Test
        @DisplayName("detects email pattern in entity data")
        void detectsEmailPattern() { 
            DataValidationProcessor processor = new DataValidationProcessor(); 
            EntityStore.Entity entity = EntityStore.Entity.builder() 
                    .collection("contacts")
                    .data(Map.of("email", "test@example.com")) 
                    .build(); 

            List<DataValidationProcessor.DetectedPattern> patterns =
                    runPromise(() -> processor.detectPatterns(TENANT, entity)); 

            assertThat(patterns).isNotEmpty(); 
            assertThat(patterns).anyMatch(p -> p.patternId().equals("email"));
        }

        @Test
        @DisplayName("detects URL pattern in entity data")
        void detectsUrlPattern() { 
            DataValidationProcessor processor = new DataValidationProcessor(); 
            EntityStore.Entity entity = EntityStore.Entity.builder() 
                    .collection("resources")
                    .data(Map.of("url", "https://example.com")) 
                    .build(); 

            List<DataValidationProcessor.DetectedPattern> patterns =
                    runPromise(() -> processor.detectPatterns(TENANT, entity)); 

            assertThat(patterns).isNotEmpty(); 
            assertThat(patterns).anyMatch(p -> p.patternId().equals("url"));
        }

        @Test
        @DisplayName("detects UUID pattern in entity data")
        void detectsUuidPattern() { 
            DataValidationProcessor processor = new DataValidationProcessor(); 
            EntityStore.Entity entity = EntityStore.Entity.builder() 
                    .collection("entities")
                    .data(Map.of("id", "550e8400-e29b-41d4-a716-446655440000")) 
                    .build(); 

            List<DataValidationProcessor.DetectedPattern> patterns =
                    runPromise(() -> processor.detectPatterns(TENANT, entity)); 

            assertThat(patterns).isNotEmpty(); 
            assertThat(patterns).anyMatch(p -> p.patternId().equals("uuid"));
        }

        @Test
        @DisplayName("returns empty list when no patterns detected")
        void returnsEmptyListWhenNoPatterns() { 
            DataValidationProcessor processor = new DataValidationProcessor(); 
            EntityStore.Entity entity = EntityStore.Entity.builder() 
                    .collection("entities")
                    .data(Map.of("name", "John Doe")) 
                    .build(); 

            List<DataValidationProcessor.DetectedPattern> patterns =
                    runPromise(() -> processor.detectPatterns(TENANT, entity)); 

            assertThat(patterns).isEmpty(); 
        }
    }
}
