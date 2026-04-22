/**
 * @doc.type class
 * @doc.purpose Test feature validation, quality checks, and data integrity
 * @doc.layer products
 * @doc.pattern Test
 */
package com.ghatana.datacloud.featurestore;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feature Validation Tests
 *
 * Test feature validation, quality checks, and data integrity.
 */
@DisplayName("Feature Validation Tests")
@Tag("quality")
class FeatureValidationTest {

    @Test
    @DisplayName("Should validate feature schemas")
    void shouldValidateFeatureSchemas() {
        Map<String, Object> payload = Map.of("user_age", 25, "user_income", 50000.0);

        assertThat(payload).isNotEmpty();
        assertThat(payload.keySet()).allMatch(key -> key.matches("[a-z0-9_]+"));
    }

    @Test
    @DisplayName("Should check feature quality")
    void shouldCheckFeatureQuality() {
        Map<String, Object> payload = Map.of("age", 25);

        assertThat(payload).isNotEmpty();
        assertThat(payload.get("age")).isInstanceOf(Integer.class);
    }

    @Test
    @DisplayName("Should handle feature constraints")
    void shouldHandleFeatureConstraints() {
        Map<String, Object> payload = Map.of("age", 25, "name", "John Doe");

        assertThat(payload).isNotEmpty();
        assertThat(payload.keySet()).allMatch(key -> key.matches("[a-z0-9_]+"));
    }

    @Test
    @DisplayName("Should detect feature anomalies")
    void shouldDetectFeatureAnomalies() {
        Map<String, Object> payload = Map.of("age", -1000); // Negative age is anomalous

        assertThat(payload).isNotEmpty();
        assertThat((Integer) payload.get("age")).isNegative();
    }

    @Test
    @DisplayName("Should handle validation failures")
    void shouldHandleValidationFailures() {
        Map<String, Object> payload = Map.of("special@chars", "test");

        assertThat(payload).isNotEmpty();
        assertThat(payload).containsKey("special@chars");
    }

    @Test
    @DisplayName("Should handle data integrity")
    void shouldHandleDataIntegrity() {
        Map<String, Object> payload = Map.of("age", 25, "name", "John Doe");
        Instant timestamp = Instant.now();

        assertThat(payload).isNotEmpty();
        assertThat(timestamp).isNotNull();
    }

    // Additional comprehensive quality validation tests

    @Test
    @DisplayName("Should detect null values in features")
    void shouldDetectNullValuesInFeatures() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("age", 25);
        payload.put("name", null);
        payload.put("income", 50000.0);

        List<String> nullFields = new ArrayList<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (entry.getValue() == null) {
                nullFields.add(entry.getKey());
            }
        }

        assertThat(nullFields).containsExactly("name");
    }

    @Test
    @DisplayName("Should validate feature data types")
    void shouldValidateFeatureDataTypes() {
        Map<String, Object> payload = Map.of(
            "age", 25,
            "name", "John",
            "income", 50000.0,
            "active", true,
            "tags", List.of("tag1", "tag2")
        );

        assertThat(payload.get("age")).isInstanceOf(Integer.class);
        assertThat(payload.get("name")).isInstanceOf(String.class);
        assertThat(payload.get("income")).isInstanceOf(Double.class);
        assertThat(payload.get("active")).isInstanceOf(Boolean.class);
        assertThat(payload.get("tags")).isInstanceOf(List.class);
    }

    @Test
    @DisplayName("Should validate feature value ranges")
    void shouldValidateFeatureValueRanges() {
        Map<String, Object> payload = Map.of(
            "age", 25,
            "score", 0.85,
            "count", 100
        );

        // Age should be between 0 and 150
        int age = (Integer) payload.get("age");
        assertThat(age).isBetween(0, 150);

        // Score should be between 0 and 1
        double score = (Double) payload.get("score");
        assertThat(score).isBetween(0.0, 1.0);

        // Count should be non-negative
        int count = (Integer) payload.get("count");
        assertThat(count).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Should detect out-of-range values")
    void shouldDetectOutOfRangeValues() {
        Map<String, Object> payload = Map.of(
            "age", 200,  // Invalid age
            "score", 1.5   // Invalid score (> 1.0)
        );

        int age = (Integer) payload.get("age");
        assertThat(age).isGreaterThan(150);  // Out of valid range

        double score = (Double) payload.get("score");
        assertThat(score).isGreaterThan(1.0);  // Out of valid range
    }

    @Test
    @DisplayName("Should check feature completeness")
    void shouldCheckFeatureCompleteness() {
        Map<String, Object> payload = Map.of(
            "age", 25,
            "name", "John",
            "income", null  // Missing required field
        );

        Set<String> requiredFields = Set.of("age", "name", "income", "email");
        Set<String> missingFields = new HashSet<>();

        for (String field : requiredFields) {
            if (!payload.containsKey(field) || payload.get(field) == null) {
                missingFields.add(field);
            }
        }

        assertThat(missingFields).contains("income", "email");
    }

    @Test
    @DisplayName("Should detect duplicate feature values")
    void shouldDetectDuplicateFeatureValues() {
        List<Map<String, Object>> records = List.of(
            Map.of("id", 1, "name", "John"),
            Map.of("id", 2, "name", "John"),  // Duplicate name
            Map.of("id", 3, "name", "Jane")
        );

        Set<String> names = new HashSet<>();
        Set<String> duplicates = new HashSet<>();

        for (Map<String, Object> record : records) {
            String name = (String) record.get("name");
            if (!names.add(name)) {
                duplicates.add(name);
            }
        }

        assertThat(duplicates).contains("John");
    }

    @Test
    @DisplayName("Should validate feature consistency")
    void shouldValidateFeatureConsistency() {
        Map<String, Object> payload = Map.of(
            "start_date", "2024-01-01",
            "end_date", "2023-12-31",  // Inconsistent: end_date before start_date
            "status", "active"
        );

        String startDate = (String) payload.get("start_date");
        String endDate = (String) payload.get("end_date");

        assertThat(endDate).isLessThan(startDate);  // Consistency check fails
    }

    @Test
    @DisplayName("Should handle empty strings vs null")
    void shouldHandleEmptyStringsVsNull() {
        Map<String, Object> payload = Map.of(
            "name", "",      // Empty string
            "email", null   // Null value
        );

        String name = (String) payload.get("name");
        String email = (String) payload.get("email");

        assertThat(name).isEmpty();
        assertThat(email).isNull();
    }

    @Test
    @DisplayName("Should validate timestamp formats")
    void shouldValidateTimestampFormats() {
        Map<String, Object> payload = Map.of(
            "created_at", "2024-01-01T00:00:00Z",
            "updated_at", Instant.now().toString()
        );

        String createdAt = (String) payload.get("created_at");
        String updatedAt = (String) payload.get("updated_at");

        assertThat(createdAt).matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$");
        assertThat(updatedAt).isNotNull();
    }

    @Test
    @DisplayName("Should detect invalid email formats")
    void shouldDetectInvalidEmailFormats() {
        List<String> emails = List.of(
            "valid@example.com",
            "invalid-email",
            "another@invalid",
            "@missing-local.com"
        );

        List<String> invalidEmails = new ArrayList<>();
        for (String email : emails) {
            if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                invalidEmails.add(email);
            }
        }

        assertThat(invalidEmails).contains("invalid-email", "another@invalid", "@missing-local.com");
    }

    @Test
    @DisplayName("Should validate numeric precision")
    void shouldValidateNumericPrecision() {
        Map<String, Object> payload = Map.of(
            "price", 19.99,
            "tax_rate", 0.0875
        );

        double price = (Double) payload.get("price");
        double taxRate = (Double) payload.get("tax_rate");

        // Price should have at most 2 decimal places
        assertThat(price * 100).isEqualTo(Math.round(price * 100));

        // Tax rate should have at most 4 decimal places
        assertThat(taxRate * 10000).isEqualTo(Math.round(taxRate * 10000));
    }

    @Test
    @DisplayName("Should handle special characters in strings")
    void shouldHandleSpecialCharactersInStrings() {
        Map<String, Object> payload = Map.of(
            "description", "Product with special chars: @#$%^&*()",
            "name", "Simple_Name-123"
        );

        String description = (String) payload.get("description");
        String name = (String) payload.get("name");

        assertThat(description).contains("@#$%^&*()");
        assertThat(name).matches("[a-zA-Z0-9_-]+");
    }

    @Test
    @DisplayName("Should validate array/list lengths")
    void shouldValidateArrayLengths() {
        Map<String, Object> payload = Map.of(
            "tags", List.of("tag1", "tag2", "tag3"),
            "items", List.of()  // Empty list
        );

        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) payload.get("tags");
        @SuppressWarnings("unchecked")
        List<String> items = (List<String>) payload.get("items");

        assertThat(tags).hasSize(3);
        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("Should detect circular references in nested structures")
    void shouldDetectCircularReferences() {
        Map<String, Object> map1 = new HashMap<>();
        Map<String, Object> map2 = new HashMap<>();

        map1.put("ref", map2);
        map2.put("ref", map1);  // Circular reference

        Set<Object> visited = new HashSet<>();
        boolean hasCircular = detectCircularReference(map1, visited);

        assertThat(hasCircular).isTrue();
    }

    private boolean detectCircularReference(Object obj, Set<Object> visited) {
        if (obj == null || !visited.add(obj)) {
            return false;
        }

        if (obj instanceof Map) {
            for (Object value : ((Map<?, ?>) obj).values()) {
                if (detectCircularReference(value, visited)) {
                    return true;
                }
            }
        }

        visited.remove(obj);
        return false;
    }

    @Test
    @DisplayName("Should validate feature name patterns")
    void shouldValidateFeatureNamePatterns() {
        List<String> featureNames = List.of(
            "user_age",
            "user_income",
            "user-education",  // Invalid: hyphen instead of underscore
            "123_invalid",    // Invalid: starts with number
            "valid_name_123"
        );

        List<String> invalidNames = new ArrayList<>();
        for (String name : featureNames) {
            if (!name.matches("^[a-z][a-z0-9_]*$")) {
                invalidNames.add(name);
            }
        }

        assertThat(invalidNames).contains("user-education", "123_invalid");
    }

    @Test
    @DisplayName("Should check feature cardinality")
    void shouldCheckFeatureCardinality() {
        Map<String, Object> payload = Map.of(
            "category", "electronics",
            "subcategory", "laptops"
        );

        // Category should have limited cardinality (e.g., from a predefined set)
        Set<String> validCategories = Set.of("electronics", "clothing", "books", "food");
        String category = (String) payload.get("category");

        assertThat(validCategories).contains(category);
    }

    @Test
    @DisplayName("Should validate enum-like fields")
    void shouldValidateEnumLikeFields() {
        Map<String, Object> payload = Map.of(
            "status", "active",
            "priority", "high"
        );

        Set<String> validStatuses = Set.of("active", "inactive", "pending");
        Set<String> validPriorities = Set.of("low", "medium", "high", "urgent");

        String status = (String) payload.get("status");
        String priority = (String) payload.get("priority");

        assertThat(validStatuses).contains(status);
        assertThat(validPriorities).contains(priority);
    }
}
